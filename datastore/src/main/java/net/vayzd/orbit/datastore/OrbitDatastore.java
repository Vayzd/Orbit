/*
 * This file is part of Orbit, licenced under the MIT Licence (MIT)
 *
 * Copyright (c) Vayzd Network <https://www.vayzd.net/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.vayzd.orbit.datastore;

import com.zaxxer.hikari.*;
import lombok.*;
import net.vayzd.orbit.datastore.group.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

import static com.google.common.base.Preconditions.*;
import static java.lang.String.*;
import static java.util.Arrays.*;

public class OrbitDatastore implements Datastore {

    private final HikariConfig config;
    @Setter(AccessLevel.PRIVATE)
    private HikariDataSource dataSource;
    private final Logger logger;
    private final ExecutorService queue;
    private final Set<Thread> threadSet = new HashSet<>();
    private final AtomicLong threadCount = new AtomicLong(0);
    private final AtomicReference<Thread> primaryThread = new AtomicReference<>();
    private final ConcurrentMap<String, DatastoreGroup> cache = new ConcurrentHashMap<>();

    private OrbitDatastore(Logger logger, DatastoreCredentials credentials, Thread primary,
                           int poolSize) throws Exception {
        checkNotNull(logger, "Datastore logger can't be null");
        checkNotNull(credentials, "Credentials can't be null");
        checkArgument(poolSize >= 0, "Pool size must be greater than or equal to 0");
        this.logger = logger;
        {
            AtomicReference<ThreadFactory> factoryReference = new AtomicReference<>(
                    runnable -> {
                        Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                        thread.setName("Datastore Thread #" + threadCount.incrementAndGet());
                        thread.setDaemon(true);
                        threadSet.add(thread);
                        return thread;
                    }
            );
            if (poolSize <= 1) {
                this.queue = Executors.newSingleThreadExecutor(factoryReference.get());
            } else if (poolSize < Runtime.getRuntime().availableProcessors()) {
                this.queue = Executors.newFixedThreadPool(poolSize, factoryReference.get());
            } else {
                this.queue = Executors.newWorkStealingPool();
            }
            this.primaryThread.set(checkNotNull(primary, "Primary thread can't be null"));
        }
        Logger.getLogger("com.zaxxer.hikari").setLevel(Level.OFF);
        HikariConfig config = new HikariConfig();
        {
            checkNotNull(credentials.getHostname(), "Hostname can't be null");
            checkArgument(credentials.getPort() > 0, "Port must be greater than 0");
            checkNotNull(credentials.getUsername(), "Username can't be null");
            checkNotNull(credentials.getPassword(), "Username can't be null");
            checkNotNull(credentials.getDefaultDatabase(), "Default ");
            config.setJdbcUrl(String.format("jdbc:mysql://%s:%s/%s?autoReconnect=true&characterEncoding=UTF-8",
                    credentials.getHostname(),
                    credentials.getPort(),
                    credentials.getDefaultDatabase()
            ));
            config.setDriverClassName("com.mysql.jdbc.Driver");
            config.setUsername(credentials.getUsername());
            config.setPassword(credentials.getPassword());
            // multiply the async thread count by 2 so there are still connections
            // available which can be used by spigot async events or netty IO threads for instance
            config.setMaximumPoolSize(poolSize >= Runtime.getRuntime().availableProcessors()
                    ? (Runtime.getRuntime().availableProcessors() * 2) + 1
                    : poolSize * 2
            );
            config.addDataSourceProperty("useConfigs", "maxPerformance");
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        }
        this.config = config;
    }

    @Override
    public void connect(DataCallback<Boolean> uponCompletion) {
        checkNotNull(uponCompletion);
        submitTask(() -> {
            setDataSource(new HikariDataSource(config));
            try (Connection connection = getConnection()) {
                Statement statement = connection.createStatement();
                for (String tableSchema : TABLE_SCHEMA) {
                    statement.executeUpdate(tableSchema);
                }
                statement.close();
                uponCompletion.complete(true, null);
            } catch (SQLException error) {
                uponCompletion.complete(false, error);
                logger.log(Level.WARNING, "Unable to ensure default table schema!", error);
            }
        });
    }

    @Override
    public void disconnect(DataCallback<Boolean> uponCompletion) {
        checkNotNull(uponCompletion);
        try {
            close();
            uponCompletion.complete(true, null);
        } catch (Exception error) {
            uponCompletion.complete(false, error);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public Connection getCaughtConnection() {
        try (Connection connection = getConnection()) {
            return connection;
        } catch (SQLException error) {
            error.printStackTrace();
            return null;
        }
    }

    @Override
    public void fetchAndCacheGroups() {
        submitTask(() -> {
            cache.clear();
            try (Connection connection = getConnection()) {
                PreparedStatement statement = connection.prepareStatement(format(
                        "SELECT * FROM %s",
                        table(DatastoreGroup.class)
                ));
                ResultSet set = statement.executeQuery();
                while (!set.isClosed() && set.next()) {
                    DatastoreGroup group = new DatastoreGroup();
                    group.readFrom(set);
                    group.updatePermissionSet(calculatorOf(group).computeEffectivePermissions());
                    cache.putIfAbsent(group.getName(), group);
                }
                set.close();
                statement.close();
            } catch (SQLException error) {
                logger.log(Level.WARNING, "Unable to fetch and locally cache groups!", error);
            }
            logger.info(format("Successfully fetched and locally cached %s groups!", cache.size()));
        });
    }

    @Override
    public Optional<DatastoreGroup> getGroup(String name) {
        AtomicReference<DatastoreGroup> reference = new AtomicReference<>(null);
        if (cache.containsKey(name)) {
            reference.set(cache.getOrDefault(name, null));
        } else {
            try (Connection connection = getConnection()) {
                PreparedStatement statement = connection.prepareStatement(format(
                        "SELECT * FROM %s WHERE name=?",
                        table(DatastoreGroup.class)
                ));
                statement.setString(1, name);
                ResultSet set = statement.executeQuery();
                if (!set.isClosed() && set.next()) {
                    DatastoreGroup group = new DatastoreGroup();
                    group.readFrom(set);
                    group.updatePermissionSet(calculatorOf(group).computeEffectivePermissions());
                    cache.putIfAbsent(group.getName(), group);
                    reference.set(group);
                }
                set.close();
                statement.close();
            } catch (SQLException error) {
                logger.log(Level.WARNING, format("Unable to get group with name '%s'!", name), error);
            }
        }
        return Optional.ofNullable(reference.get());
    }

    @Override
    public void getGroup(String name, DataCallback<Optional<DatastoreGroup>> callback) {
        fulfill(callback, getGroup(name));
    }

    @Override
    public boolean hasGroup(String name) {
        return getGroup(name).isPresent();
    }

    @Override
    public void hasGroup(String name, DataCallback<Boolean> callback) {
        fulfill(callback, hasGroup(name));
    }

    @Override
    public boolean insertGroup(DatastoreGroup group) {
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement(format(
                    "INSERT INTO %s(name, parentSet, prefix, suffix, tabColor, tabOrder, permissionSet) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    table(DatastoreGroup.class)
            ));
            statement.setString(1, group.getName());
            statement.setString(2, convertSetToString(group.getParentSet(), ";"));
            statement.setString(3, group.getPrefix());
            statement.setString(4, group.getSuffix());
            statement.setString(5, String.valueOf(group.getTabColor()));
            statement.setInt(6, group.getTabOrder());
            statement.setString(7, convertSetToString(group.getPermissionSet(), ";"));
            statement.executeUpdate();
            statement.close();
            return true;
        } catch (SQLException error) {
            logger.log(Level.WARNING, format("Unable to insert group with name '%s'!", group.getName()), error);
            return false;
        }
    }

    @Override
    public void insertGroup(DatastoreGroup group, DataCallback<Boolean> uponCompletion) {
        fulfill(uponCompletion, insertGroup(group));
    }

    @Override
    public boolean updateGroup(DatastoreGroup group) {
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement(format(
                    "UPDATE %s SET parentSet=?, prefix=?, suffix=?, color=?, order=?, permissionSet=? WHERE name=?",
                    table(DatastoreGroup.class)
            ));
            statement.setString(1, convertSetToString(group.getParentSet(), ";"));
            statement.setString(2, group.getPrefix());
            statement.setString(3, group.getSuffix());
            statement.setString(4, String.valueOf(group.getTabColor()));
            statement.setInt(5, group.getTabOrder());
            statement.setString(6, convertSetToString(group.getPermissionSet(), ";"));
            statement.setString(7, group.getName());
            statement.executeUpdate();
            statement.close();
            return true;
        } catch (SQLException error) {
            logger.log(Level.WARNING, format("Unable to update group with name '%s'!", group.getName()), error);
            return false;
        }
    }

    @Override
    public void updateGroup(DatastoreGroup group, DataCallback<Boolean> uponCompletion) {
        fulfill(uponCompletion, updateGroup(group));
    }

    @Override
    public boolean deleteGroup(DatastoreGroup group) {
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement(format(
                    "DELETE FROM %s WHERE name=?",
                    table(DatastoreGroup.class)
            ));
            statement.setString(1, group.getName());
            statement.executeUpdate();
            statement.close();
            return true;
        } catch (SQLException error) {
            logger.log(Level.WARNING, format("Unable to delete group with name '%s'!", group.getName()), error);
            return false;
        }
    }

    @Override
    public void deleteGroup(DatastoreGroup group, DataCallback<Boolean> uponCompletion) {
        fulfill(uponCompletion, deleteGroup(group));
    }

    @Override
    public void close() throws Exception {
        if (dataSource.isClosed()) {
            throw new RuntimeException("Connection is already closed");
        }
        dataSource.close();
    }

    private String table(Class<? extends DatastoreEntry> from) {
        return checkNotNull(from, "DataStoreEntry can't be null")
                .getAnnotation(DatastoreTable.class).name();
    }

    private PermissionCalculator calculatorOf(DatastoreGroup group) {
        checkNotNull(group);
        return PermissionCalculator.of(group, this);
    }

    private <T> String convertSetToString(final Set<T> set, final String separator) {
        checkNotNull(set);
        checkArgument(!set.isEmpty());
        checkNotNull(separator);
        checkArgument(!separator.isEmpty());
        AtomicInteger remaining = new AtomicInteger(set.size());
        StringBuilder builder = new StringBuilder();
        set.forEach(nextEntry -> {
            builder.append(nextEntry);
            if (remaining.decrementAndGet() != 0) {
                builder.append(separator);
            }
        });
        return builder.toString();
    }

    private void submitTask(final Runnable task) {
        checkNotNull(task, "Database task (Runnable) can't be null");
        Thread current = Thread.currentThread();
        if (threadSet.contains(current) || !primaryThread.get().equals(current)) {
            task.run();
        } else {
            queue.execute(task);
        }
    }

    private <T> void fulfill(final DataCallback<T> callback, final T result) {
        submitTask(() -> {
            try {
                checkNotNull(callback, "DataCallback<T> (async result) can't be null");
                callback.complete(result, null);
            } catch (NullPointerException error) {
                callback.complete(null, error);
                error.printStackTrace();
            }
        });
    }

    private final List<String> TABLE_SCHEMA = new LinkedList<>(asList(
            format("CREATE TABLE IF NOT EXISTS `%s`(" +
                    "`name` VARCHAR(32) NOT NULL, " +
                    "`parentSet` TEXT NOT NULL, " +
                    "`prefix` VARCHAR(16) NOT NULL, " +
                    "`suffix` VARCHAR(16) NOT NULL, " +
                    "`tabColor` CHAR(1) NOT NULL, " +
                    "`tabOrder` SMALLINT NOT NULL, " +
                    "`permissionSet` TEXT, " +
                    "PRIMARY(`name`), UNIQUE(`tabOrder`)" +
                    ") DEFAULT CHARSET=utf8;", table(DatastoreGroup.class))
    ));

    private static volatile Datastore datastore = null;

    public static Datastore createDatastore(Logger logger, DatastoreCredentials credentials, Thread primary,
                                         int poolSize) throws Exception {
        if (datastore == null) {
            datastore = new OrbitDatastore(logger, credentials, primary, poolSize);
        }
        return datastore;
    }
}