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
    private final AtomicReference<DatastoreGroup> defaultGroup = new AtomicReference<>(null);
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
    public void connect(DataCallback<Boolean> uponSchemaCompletion) {
        checkNotNull(uponSchemaCompletion);
        setDataSource(new HikariDataSource(config));
        submitTask(() -> {
            try (Connection connection = getConnection()) {
                Statement statement = connection.createStatement();
                for (String tableSchema : TABLE_SCHEMA) {
                    statement.executeUpdate(tableSchema);
                }
                statement.close();
                uponSchemaCompletion.complete(true, null);
            } catch (SQLException error) {
                uponSchemaCompletion.complete(false, error);
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
                    cache.putIfAbsent(group.getName(), group);
                }
                set.close();
                statement.close();
            } catch (SQLException error) {
                logger.log(Level.WARNING, "Unable to fetch and locally cache groups!", error);
            }
            if (cache.isEmpty()) {
                return;
            }
            cache.values().forEach(group -> {
                if (group != null) {
                    group.updatePermissionSet(calculatorOf(group).computePermissionSet());
                }
            });
            for (DatastoreGroup group : cache.values()) {
                if (group.isDefaultGroup()) {
                    defaultGroup.set(group);
                    break;
                }
            }
            logger.info(format("Successfully fetched and locally cached %s group%s!",
                    cache.size(),
                    cache.size() > 1 ? "s" : ""
            ));
        });
    }

    @Override
    public Optional<DatastoreGroup> getGroup(String name) {
        checkNotNull(name);
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
                    cache.putIfAbsent(group.getName(), group);
                    reference.set(group);
                }
                set.close();
                statement.close();
            } catch (SQLException error) {
                logger.log(Level.WARNING, format("Unable to get group with name='%s'!", name), error);
            }
            DatastoreGroup group = reference.get();
            if (group != null) {
                group.updatePermissionSet(calculatorOf(group).computePermissionSet());
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
    public Optional<DatastoreGroup> getDefaultGroup() {
        return Optional.ofNullable(defaultGroup.get());
    }

    @Override
    public boolean hasDefaultGroup() {
        return getDefaultGroup().isPresent();
    }

    @Override
    public boolean insertGroup(DatastoreGroup group) {
        checkNotNull(group);
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement(format(
                    "INSERT INTO %s(name, parents, default_group, display_name, prefix, suffix, show_tab, show_tag, show_chat, " +
                            "color_char, tab_order, permissions) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    table(DatastoreGroup.class)
            ));
            statement.setString(1, group.getName());
            statement.setString(2, convertSetToString(group.getParentSet()));
            statement.setBoolean(3, group.isDefaultGroup());
            statement.setString(4, group.getDisplayName());
            statement.setString(5, group.getPrefix());
            statement.setString(6, group.getSuffix());
            statement.setBoolean(7, group.isShowTab());
            statement.setBoolean(8, group.isShowTag());
            statement.setBoolean(9, group.isShowChat());
            statement.setString(10, String.valueOf(group.getColorChar()));
            statement.setInt(11, group.getTabOrder());
            statement.setString(12, convertSetToString(group.getPermissionSet()));
            statement.executeUpdate();
            statement.close();
            return true;
        } catch (SQLException error) {
            logger.log(Level.WARNING, format("Unable to insert group with name='%s'!", group.getName()), error);
            return false;
        }
    }

    @Override
    public void insertGroup(DatastoreGroup group, DataCallback<Boolean> uponCompletion) {
        fulfill(uponCompletion, insertGroup(group));
    }

    @Override
    public boolean updateGroup(DatastoreGroup group) {
        checkNotNull(group);
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement(format(
                    "UPDATE %s SET parents=?, default_group=?, display_name=?, prefix=?, suffix=?, show_tab=?, show_tag=?, " +
                            "show_chat=?, color=?, order=?, permissions=? WHERE name=?",
                    table(DatastoreGroup.class)
            ));
            statement.setString(1, convertSetToString(group.getParentSet()));
            statement.setBoolean(2, group.isDefaultGroup());
            statement.setString(3, group.getDisplayName());
            statement.setString(4, group.getPrefix());
            statement.setString(5, group.getSuffix());
            statement.setBoolean(6, group.isShowTab());
            statement.setBoolean(7, group.isShowTag());
            statement.setBoolean(8, group.isShowChat());
            statement.setString(9, String.valueOf(group.getColorChar()));
            statement.setInt(10, group.getTabOrder());
            statement.setString(11, convertSetToString(group.getPermissionSet()));
            statement.setString(12, group.getName());
            statement.executeUpdate();
            statement.close();
            return true;
        } catch (SQLException error) {
            logger.log(Level.WARNING, format("Unable to update group with name='%s'!", group.getName()), error);
            return false;
        }
    }

    @Override
    public void updateGroup(DatastoreGroup group, DataCallback<Boolean> uponCompletion) {
        fulfill(uponCompletion, updateGroup(group));
    }

    @Override
    public boolean deleteGroup(DatastoreGroup group) {
        checkNotNull(group);
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
            logger.log(Level.WARNING, format("Unable to delete group with name='%s'!", group.getName()), error);
            return false;
        }
    }

    @Override
    public void deleteGroup(DatastoreGroup group, DataCallback<Boolean> uponCompletion) {
        fulfill(uponCompletion, deleteGroup(group));
    }

    @Override
    public Optional<DatastoreSubject> getSubject(UUID uniqueId) {
        checkNotNull(uniqueId);
        AtomicReference<DatastoreSubject> reference = new AtomicReference<>(null);
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement(format(
                    "SELECT * FROM %s WHERE uniqueId=?",
                    table(DatastoreSubject.class)
            ));
            statement.setString(1, uniqueId.toString());
            ResultSet set = statement.executeQuery();
            if (!set.isClosed() && set.next()) {
                DatastoreSubject subject = new DatastoreSubject();
                subject.readFrom(set);
                reference.set(subject);
            }
            set.close();
            statement.close();
        } catch (SQLException error) {
            logger.log(Level.WARNING, format("Unable to get subject with UUID='%s'!", uniqueId.toString()), error);
        }
        DatastoreSubject subject = reference.get();
        if (subject != null) {
            getGroup(subject.getGroupName()).ifPresent(subject::setGroup);
        }
        return Optional.ofNullable(reference.get());
    }

    @Override
    public void getSubject(UUID uniqueId, DataCallback<Optional<DatastoreSubject>> callback) {
        fulfill(callback, getSubject(uniqueId));
    }

    @Override
    public boolean hasSubject(UUID uniqueId) {
        return getSubject(uniqueId).isPresent();
    }

    @Override
    public void hasSubject(UUID uniqueId, DataCallback<Boolean> callback) {
        fulfill(callback, hasSubject(uniqueId));
    }

    @Override
    public List<DatastoreSubject> getSubjectListByGroup(String name) {
        checkNotNull(name);
        AtomicReference<List<DatastoreSubject>> reference = new AtomicReference<>(new ArrayList<>());
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement(format(
                    "SELECT * FROM %s WHERE group_name=?",
                    table(DatastoreSubject.class)
            ));
            statement.setString(1, name);
            ResultSet set = statement.executeQuery();
            while (!set.isClosed() && set.next()) {
                DatastoreSubject subject = new DatastoreSubject();
                subject.readFrom(set);
                reference.get().add(subject);
            }
            set.close();
            statement.close();
        } catch (SQLException error) {
            logger.log(Level.WARNING, format("Unable to get subject list by group with name='%s'!", name), error);
        }
        if (reference.get().isEmpty()) {
            return reference.get();
        }
        reference.get().forEach(subject -> {
            if (subject != null) {
                getGroup(subject.getGroupName()).ifPresent(subject::setGroup);
            }
        });
        return reference.get();
    }

    @Override
    public void getSubjectListByGroup(String name, DataCallback<List<DatastoreSubject>> callback) {
        fulfill(callback, getSubjectListByGroup(name));
    }

    @Override
    public boolean insertSubject(DatastoreSubject subject) {
        checkNotNull(subject);
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement(format(
                    "INSERT INTO %s(uniqueId, group_name, permissions) VALUES (?, ?, ?)",
                    table(DatastoreSubject.class)
            ));
            statement.setString(1, subject.getUniqueId().toString());
            statement.setString(2, subject.getGroup() == null
                    ? subject.getGroupName()
                    : subject.getGroup().getName()
            );
            statement.setString(3, convertSetToString(subject.getPermissionSet()));
            statement.executeUpdate();
            statement.close();
            return true;
        } catch (SQLException error) {
            logger.log(Level.WARNING, format("Unable to insert player with UUID='%s'!",
                    subject.getUniqueId().toString()),
                    error);
            return false;
        }
    }

    @Override
    public void insertSubject(DatastoreSubject subject, DataCallback<Boolean> uponCompletion) {
        fulfill(uponCompletion, insertSubject(subject));
    }

    @Override
    public boolean updateSubject(DatastoreSubject subject) {
        checkNotNull(subject);
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement(format(
                    "UPDATE %s SET group_name=?, permissions=? WHERE uniqueId=?",
                    table(DatastoreSubject.class)
            ));
            statement.setString(1, subject.getGroup() == null
                    ? subject.getGroupName()
                    : subject.getGroup().getName()
            );
            statement.setString(2, convertSetToString(subject.getPermissionSet()));
            statement.setString(3, subject.getUniqueId().toString());
            statement.executeUpdate();
            statement.close();
            return true;
        } catch (SQLException error) {
            logger.log(Level.WARNING, format("Unable to update player with UUID='%s'!",
                    subject.getUniqueId().toString()),
                    error);
            return false;
        }
    }

    @Override
    public void updateSubject(DatastoreSubject subject, DataCallback<Boolean> uponCompletion) {
        fulfill(uponCompletion, updateSubject(subject));
    }

    @Override
    public boolean deleteSubject(DatastoreSubject subject) {
        checkNotNull(subject);
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement(format(
                    "DELETE FROM %s WHERE uniqueId=?",
                    table(DatastoreSubject.class)
            ));
            statement.setString(1, subject.getUniqueId().toString());
            statement.executeUpdate();
            statement.close();
            return true;
        } catch (SQLException error) {
            logger.log(Level.WARNING, format("Unable to delete player with UUID='%s'!",
                    subject.getUniqueId().toString()),
                    error);
            return false;
        }
    }

    @Override
    public void deleteSubject(DatastoreSubject subject, DataCallback<Boolean> uponCompletion) {
        fulfill(uponCompletion, deleteSubject(subject));
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

    private <T> String convertSetToString(final Set<T> set) {
        checkNotNull(set);
        checkArgument(!set.isEmpty());
        AtomicInteger remaining = new AtomicInteger(set.size());
        StringBuilder builder = new StringBuilder();
        set.forEach(nextEntry -> {
            builder.append(nextEntry);
            if (remaining.decrementAndGet() != 0) {
                builder.append(";");
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
                    "`name` VARCHAR(16) NOT NULL, " +
                    "`parents` TEXT NOT NULL, " +
                    "`default_group` BOOLEAN NOT NULL, " +
                    "`display_name` VARCHAR(16) NOT NULL, " +
                    "`prefix` VARCHAR(16) NOT NULL, " +
                    "`suffix` VARCHAR(16) NOT NULL, " +
                    "`show_tab` BOOLEAN NOT NULL, " +
                    "`show_tag` BOOLEAN NOT NULL, " +
                    "`show_chat` BOOLEAN NOT NULL, " +
                    "`color_char` CHAR(1) NOT NULL, " +
                    "`tab_order` SMALLINT NOT NULL, " +
                    "`permissions` TEXT NOT NULL, " +
                    "PRIMARY KEY(`name`), UNIQUE(`tab_order`)" +
                    ") DEFAULT CHARSET=utf8;", table(DatastoreGroup.class)),

            format("CREATE TABLE IF NOT EXISTS `%s`(" +
                    "`uniqueId` VARCHAR(36) NOT NULL, " +
                    "`group_name` VARCHAR(16) NOT NULL, " +
                    "`permissions` TEXT NOT NULL, " +
                    "PRIMARY KEY(`uniqueId`), INDEX(`group_name`)" +
                    ") DEFAULT CHARSET=utf8;", table(DatastoreSubject.class))
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