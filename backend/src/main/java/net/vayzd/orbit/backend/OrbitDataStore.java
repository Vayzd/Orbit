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
package net.vayzd.orbit.backend;

import com.zaxxer.hikari.*;
import lombok.*;
import net.vayzd.orbit.backend.entries.*;
import net.vayzd.orbit.backend.model.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

import static java.util.Arrays.*;

public final class OrbitDataStore implements DataStore {

    private final LinkedList<String> SCHEMA_DESIGN = new LinkedList<>(asList(

            "CREATE TABLE IF NOT EXISTS `aliases`(" +
                    "`id` MEDIUMINT NOT NULL AUTO_INCREMENT, " +
                    "`uuid` VARCHAR(36) NOT NULL, " +
                    "`name` VARCHAR(16) NOT NULL, " +
                    "`names` TEXT, " +
                    "PRIMARY KEY(`id`), UNIQUE(`uuid`), INDEX(`name`)" +
                    ") DEFAULT CHARSET=utf8;",

            "CREATE TABLE IF NOT EXISTS `groups`(" +
                    "`name` VARCHAR(32) NOT NULL, " +
                    "`parents` TEXT NOT NULL, " +
                    "`prefix` VARCHAR(16) NOT NULL, " +
                    "`suffix` VARCHAR(16) NOT NULL, " +
                    "`color` VARCHAR(16) NOT NULL, " +
                    "`order` MEDIUMINT NOT NULL, " +
                    "`permissions` TEXT, " +
                    "UNIQUE(`name`)" +
                    ") DEFAULT CHARSET=utf8;",

            "CREATE TABLE IF NOT EXISTS `players`(" +
                    "`id` MEDIUMINT NOT NULL AUTO_INCREMENT, " +
                    "`uuid` VARCHAR(36) NOT NULL, " +
                    "`name` VARCHAR(16) NOT NULL, " +
                    "`group` VARCHAR(32) NOT NULL, " +
                    "`permissions` TEXT, " +
                    "PRIMARY KEY(`id`), UNIQUE(`uuid`), INDEX(`name`), INDEX(`group`)" +
                    ") DEFAULT CHARSET=utf8;"
    ));
    private final HikariConfig config;
    @Setter(AccessLevel.PRIVATE)
    private HikariDataSource source;
    private final AtomicLong count = new AtomicLong(0);
    private final Logger logger;
    private final ExecutorService queue;
    private final List<Thread> threadList = new ArrayList<>();
    private final ConcurrentMap<String, DatabaseGroup> cache = new ConcurrentHashMap<>();

    private OrbitDataStore(Logger logger, Type type, String host, int port, String username, String password,
                          String database, int pool) throws SQLException {
        Logger.getLogger("com.zaxxer.hikari").setLevel(Level.OFF);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(format("jdbc:%s://%s:%s/%s?autoReconnect=true", type.getName(), host, port, database));
        config.setDriverClassName(type.getDriverClassName());
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(pool);
        config.addDataSourceProperty("useConfigs", "maxPerformance");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        if (pool <= 1) {
            queue = Executors.newSingleThreadExecutor(task -> {
                Thread thread = Executors.defaultThreadFactory().newThread(task);
                thread.setName("Database Queue");
                thread.setDaemon(true);
                threadList.add(thread);
                return thread;
            });
        } else {
            queue = Executors.newFixedThreadPool(pool, task -> {
                Thread thread = Executors.defaultThreadFactory().newThread(task);
                thread.setName("Database Thread #" + count.incrementAndGet());
                thread.setDaemon(true);
                threadList.add(thread);
                return thread;
            });
        }
        this.config = config;
        this.logger = logger;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return source.getConnection();
    }

    @Override
    public void connect() {
        setSource(new HikariDataSource(config));
        submit(() -> {
            try (Connection connection = getConnection()) {
                for (String design : SCHEMA_DESIGN) {
                    PreparedStatement statement = connection.prepareStatement(design);
                    statement.executeUpdate();
                    statement.close();
                }
            } catch (SQLException ex) {
                throw new RuntimeException("Unable to insert default database schema design!", ex);
            }
        });
    }

    @Override
    public void disconnect() {
        source.close();
    }

    @Override
    public void fetchAndCacheGroups() {
        submit(() -> {
            cache.clear();
            try (Connection connection = getConnection()) {
                PreparedStatement statement = connection.prepareStatement(format(
                        "SELECT * FROM %s",
                        table(DatabaseGroup.class)
                ));
                ResultSet set = statement.executeQuery();
                while (!set.isClosed() && set.next()) {
                    DatabaseGroup group = new DatabaseGroup();
                    group.fetch(set);
                    group.setPermissions(calculator(group).computeEffectivePermissions());
                    cache.putIfAbsent(group.getName(), group);
                }
                set.close();
                statement.close();
            } catch (SQLException ex) {
                throw new RuntimeException("Unable to fetch and cache groups from database!", ex);
            }
            logger.info(format("Successfully fetched and locally cached %s groups!", cache.size()));
        });
    }

    @Override
    public Optional<DatabaseGroup> getGroup(String name) {
        AtomicReference<DatabaseGroup> reference = new AtomicReference<>(null);
        if (cache.containsKey(name)) {
            reference.set(cache.get(name));
        } else {
            try (Connection connection = getConnection()) {
                PreparedStatement statement = connection.prepareStatement(format(
                        "SELECT * FROM %s WHERE name=?",
                        table(DatabaseGroup.class)
                ));
                statement.setString(1, name);
                ResultSet set = statement.executeQuery();
                if (!set.isClosed() && set.next()) {
                    DatabaseGroup group = new DatabaseGroup();
                    group.fetch(set);
                    group.setPermissions(calculator(group).computeEffectivePermissions());
                    reference.set(group);
                    cache.putIfAbsent(group.getName(), group);
                }
                set.close();
                statement.close();
            } catch (SQLException ex) {
                throw new RuntimeException(format("Unable to get group with name '%s' from database!", name), ex);
            }
        }
        return Optional.ofNullable(reference.get());
    }

    @Override
    public void getGroup(String name, Completable<Optional<DatabaseGroup>> completable) {
        fulfill(completable, getGroup(name));
    }

    @Override
    public boolean hasGroup(String name) {
        return getGroup(name).isPresent();
    }

    @Override
    public void hasGroup(String name, Completable<Boolean> completable) {
        fulfill(completable, hasGroup(name));
    }

    @Override
    public void insertGroup(DatabaseGroup group) {
        submit(() -> {
            try (Connection connection = getConnection()) {
                PreparedStatement statement = connection.prepareStatement(format(
                        "INSERT INTO %s(name, parents, prefix, suffix, color, order, permissions) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                        table(DatabasePlayer.class)
                ));
                statement.setString(1, group.getName());
                statement.setString(2, convertListToString(group.getParents(), ";"));
                statement.setString(3, group.getPrefix());
                statement.setString(4, group.getSuffix());
                statement.setString(5, group.getColor());
                statement.setInt(6, group.getOrder());
                statement.setString(7, convertListToString(group.getPermissions(), ";"));
                statement.executeUpdate();
                statement.close();
            } catch (SQLException ex) {
                throw new RuntimeException(
                        format("Unable to insert group with name '%s' to database!", group.getName()),
                        ex);
            }
        });
    }

    @Override
    public void updateGroup(DatabaseGroup group) {
        submit(() -> {
            try (Connection connection = getConnection()) {
                PreparedStatement statement = connection.prepareStatement(format(
                        "UPDATE %s SET parents=?, prefix=?, suffix=?, color=?, order=?, permissions=? WHERE name=?",
                        table(DatabaseGroup.class)
                ));
                statement.setString(1, convertListToString(group.getParents(), ";"));
                statement.setString(2, group.getPrefix());
                statement.setString(3, group.getSuffix());
                statement.setString(4, group.getColor());
                statement.setInt(5, group.getOrder());
                statement.setString(6, convertListToString(group.getPermissions(), ";"));
                statement.setString(7, group.getName());
                statement.executeUpdate();
                statement.close();
            } catch (SQLException ex) {
                throw new RuntimeException(
                        format("Unable to update group with name '%s' on database!", group.getName()),
                        ex);
            }
        });
    }

    @Override
    public void deleteGroup(DatabaseGroup group) {
        submit(() -> {
            try (Connection connection = getConnection()) {
                PreparedStatement statement = connection.prepareStatement(format(
                        "DELETE FROM %s WHERE name=?",
                        table(DatabaseGroup.class)
                ));
                statement.setString(1, group.getName());
                statement.executeUpdate();
                statement.close();
            } catch (SQLException ex) {
                throw new RuntimeException(
                        format("Unable to delete group with name '%s' from database!", group.getName()),
                        ex);
            }
        });
    }

    @Override
    public Optional<DatabasePlayer> getPlayer(UUID UUID) {
        AtomicReference<DatabasePlayer> reference = new AtomicReference<>(null);
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement(format(
                    "SELECT * FROM %s WHERE uuid=?",
                    table(DatabasePlayer.class)
            ));
            statement.setString(1, UUID.toString());
            ResultSet set = statement.executeQuery();
            if (!set.isClosed() && set.next()) {
                DatabasePlayer player = new DatabasePlayer();
                player.fetch(set);
                getGroup(player.getGroup()).ifPresent(player::setDatabaseGroup);
                reference.set(player);
            }
            set.close();
            statement.close();
        } catch (SQLException ex) {
            throw new RuntimeException(
                    format("Unable to get player with UUID '%s' from database!", UUID.toString()),
                    ex);
        }
        return Optional.ofNullable(reference.get());
    }

    @Override
    public void getPlayer(UUID UUID, Completable<Optional<DatabasePlayer>> completable) {
        fulfill(completable, getPlayer(UUID));
    }

    @Override
    public Optional<DatabasePlayer> getPlayer(String name) {
        AtomicReference<DatabasePlayer> reference = new AtomicReference<>(null);
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement(format(
                    "SELECT * FROM %s WHERE name=?",
                    table(DatabasePlayer.class)
            ));
            statement.setString(1, name);
            ResultSet set = statement.executeQuery();
            if (!set.isClosed() && set.next()) {
                DatabasePlayer player = new DatabasePlayer();
                player.fetch(set);
                getGroup(player.getGroup()).ifPresent(player::setDatabaseGroup);
                reference.set(player);
            }
            set.close();
            statement.close();
        } catch (SQLException ex) {
            throw new RuntimeException(format("Unable to get player with name '%s' from database!", name), ex);
        }
        return Optional.ofNullable(reference.get());
    }

    @Override
    public void getPlayer(String name, Completable<Optional<DatabasePlayer>> completable) {
        fulfill(completable, getPlayer(name));
    }

    @Override
    public boolean hasPlayer(UUID UUID) {
        return getPlayer(UUID).isPresent();
    }

    @Override
    public void hasPlayer(UUID UUID, Completable<Boolean> completable) {
        fulfill(completable, hasPlayer(UUID));
    }

    @Override
    public boolean hasPlayer(String name) {
        return getPlayer(name).isPresent();
    }

    @Override
    public void hasPlayer(String name, Completable<Boolean> completable) {
        fulfill(completable, hasPlayer(name));
    }

    @Override
    public List<DatabasePlayer> getPlayerList() {
        AtomicReference<List<DatabasePlayer>> reference = new AtomicReference<>(new ArrayList<>());
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement(format(
                    "SELECT * FROM %s",
                    table(DatabasePlayer.class)
            ));
            ResultSet set = statement.executeQuery();
            while (!set.isClosed() && set.next()) {
                DatabasePlayer player = new DatabasePlayer();
                player.fetch(set);
                getGroup(player.getGroup()).ifPresent(player::setDatabaseGroup);
                reference.get().add(player);
            }
            set.close();
            statement.close();
        } catch (SQLException ex) {
            throw new RuntimeException("Unable to get player list from database!", ex);
        }
        return reference.get();
    }

    @Override
    public void getPlayerList(Completable<List<DatabasePlayer>> completable) {
        fulfill(completable, getPlayerList());
    }

    @Override
    public List<DatabasePlayer> getPlayerListByGroup(String name) {
        AtomicReference<List<DatabasePlayer>> reference = new AtomicReference<>(new ArrayList<>());
        try (Connection connection = getConnection()) {
            PreparedStatement statement = connection.prepareStatement(format(
                    "SELECT * FROM %s WHERE group=?",
                    table(DatabasePlayer.class)
            ));
            statement.setString(1, name);
            ResultSet set = statement.executeQuery();
            while (!set.isClosed() && set.next()) {
                DatabasePlayer player = new DatabasePlayer();
                player.fetch(set);
                getGroup(player.getGroup()).ifPresent(player::setDatabaseGroup);
                reference.get().add(player);
            }
            set.close();
            statement.close();
        } catch (SQLException ex) {
            throw new RuntimeException(format("Unable to get player list by group '%s' from database!", name), ex);
        }
        return reference.get();
    }

    @Override
    public void getPlayerListByGroup(String name, Completable<List<DatabasePlayer>> completable) {
        fulfill(completable, getPlayerListByGroup(name));
    }

    @Override
    public void insertPlayer(DatabasePlayer player) {
        submit(() -> {
            try (Connection connection = getConnection()) {
                PreparedStatement statement = connection.prepareStatement(format(
                        "INSERT INTO %s(uuid, name, group, permissions) VALUES (?, ?, ?, ?)",
                        table(DatabasePlayer.class)
                ));
                statement.setString(1, player.getUUID());
                statement.setString(2, player.getName());
                statement.setString(3, player.getGroup());
                statement.setString(4, convertListToString(player.getPermissions(), ";"));
                statement.executeUpdate();
                statement.close();
            } catch (SQLException ex) {
                throw new RuntimeException(format("Unable to insert player '%s' to database!", player.getName()), ex);
            }
        });
    }

    @Override
    public void updatePlayer(DatabasePlayer player) {
        submit(() -> {
            try (Connection connection = getConnection()) {
                PreparedStatement statement = connection.prepareStatement(format(
                        "UPDATE %s SET name=?, group=?, permissions=? WHERE uuid=?",
                        table(DatabasePlayer.class)
                ));
                statement.setString(1, player.getName());
                statement.setString(2, player.getGroup());
                statement.setString(3, convertListToString(player.getPermissions(), ";"));
                statement.setString(4, player.getUUID());
                statement.executeUpdate();
                statement.close();
            } catch (SQLException ex) {
                throw new RuntimeException(format("Unable to update player '%s' on database!", player.getName()), ex);
            }
        });
    }

    @Override
    public void deletePlayer(DatabasePlayer player) {
        submit(() -> {
            try (Connection connection = getConnection()) {
                PreparedStatement statement = connection.prepareStatement(format(
                        "DELETE FROM %s WHERE uuid=?",
                        table(DatabasePlayer.class)
                ));
                statement.setString(1, player.getUUID());
                statement.executeUpdate();
                statement.close();
            } catch (SQLException ex) {
                throw new RuntimeException(format("Unable to delete player '%s' from database!", player.getName()), ex);
            }
        });
    }

    @SafeVarargs
    public final <O> String format(String message, O... objects) {
        return String.format(message, objects);
    }

    private String table(Class<? extends DatabaseEntry> from) {
        check(from, "DatabaseEntry instance can't be null");
        return from.getAnnotation(Table.class).name();
    }

    private PermissionCalculator calculator(DatabaseGroup group) {
        return PermissionCalculator.of(group, this);
    }

    private <T> String convertListToString(List<T> list, String separator) {
        CountDownLatch latch = new CountDownLatch(list.size());
        StringBuilder builder = new StringBuilder();
        list.forEach(next -> {
            latch.countDown();
            builder.append(next);
            if (latch.getCount() != 0) {
                builder.append(separator);
            }
        });
        return builder.toString();
    }

    private void submit(final Runnable action) {
        check(action, "Database Action (Runnable) can't be null");
        if (threadList.contains(Thread.currentThread())) {
            action.run();
        } else {
            queue.execute(action);
        }
    }

    private <T> void fulfill(final Completable<T> completable, final T result) {
        submit(() -> {
            check(completable, "Completable<T> (Async Future) can't be null");
            completable.complete(result);
        });
    }

    private <T> T check(final T object, final String error) {
        if (object != null) {
            return object;
        } else {
            throw new RuntimeException(error);
        }
    }

    private static volatile DataStore dataStore = null;

    public static DataStore newDataStore(Logger logger, Type type, String host, int port, String username,
                                         String password, String database, int pool) throws SQLException {
        if (dataStore == null) {
            dataStore = new OrbitDataStore(logger, type, host, port, username, password, database, pool);
        }
        return dataStore;
    }
}
