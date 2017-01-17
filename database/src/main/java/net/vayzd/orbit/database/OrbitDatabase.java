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
package net.vayzd.orbit.database;

import com.zaxxer.hikari.*;
import lombok.*;
import net.vayzd.orbit.database.entries.*;
import net.vayzd.orbit.database.model.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

final class OrbitDatabase implements Database {

    private final LinkedList<String> SCHEMA_DESIGN = new LinkedList<>(Arrays.asList(

            "CREATE TABLE IF NOT EXISTS `aliases`(" +
                    "`id` MEDIUMINT NOT NULL AUTO_INCREMENT, " +
                    "`uuid` VARCHAR(36) NOT NULL, " +
                    "`name` VARCHAR(16) NOT NULL, " +
                    "`names` TEXT, " +
                    "PRIMARY KEY(`id`), UNIQUE(`uuid`)" +
                    ") DEFAULT CHARSET=utf8;",

            "CREATE TABLE IF NOT EXISTS `groups`(" +
                    "`id` MEDIUMINT NOT NULL AUTO_INCREMENT, " +
                    "`name` VARCHAR(32) NOT NULL, " +
                    "`prefix` VARCHAR(16) NOT NULL, " +
                    "`childId` MEDIUMINT NOT NULL, " +
                    "`permissions` TEXT, " +
                    "PRIMARY KEY(`id`), UNIQUE(`name`)" +
                    ") DEFAULT CHARSET=utf8;",

            "CREATE TABLE IF NOT EXISTS `players`(" +
                    "`id` MEDIUMINT NOT NULL AUTO_INCREMENT, " +
                    "`uuid` VARCHAR(36) NOT NULL, " +
                    "`name` VARCHAR(16) NOT NULL, " +
                    "`groupId` MEDIUMINT NOT NULL, " +
                    "`permissions` TEXT, " +
                    "PRIMARY KEY(`id`), UNIQUE(`uuid`)" +
                    ") DEFAULT CHARSET=utf8;"
    ));
    private final HikariConfig config;
    @Setter(AccessLevel.PRIVATE)
    private HikariDataSource source;
    private final AtomicLong count = new AtomicLong(0);
    private final ExecutorService queue;
    private final List<Thread> threadList = new ArrayList<>();

    public OrbitDatabase(Type type, String host, int port, String username, String password, String database, int pool) throws SQLException {
        Logger.getLogger("com.zaxxer.hikari").setLevel(Level.OFF);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(format("jdbc:%s://%s:%s/%s?autoReconnect=true", type.getName(), host, port, database));
        config.setDriverClassName(type.getDriverClassName());
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(pool);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        if (pool == 1 && !(pool <= 0)) {
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
    }

    @Override
    public Connection getConnection() throws SQLException {
        return source.getConnection();
    }

    @Override
    public void connect() {
        setSource(new HikariDataSource(config));
        submit(() -> {
            try (Statement statement = getConnection().createStatement()) {
                for (String design : SCHEMA_DESIGN) {
                    statement.executeUpdate(design);
                }
            } catch (SQLException ex) {
                throw new RuntimeException("Unable to insert default database schema design!", ex);
            }
            // TODO: 16.12.16 fetch and cache database groups.
        });
    }

    @Override
    public void disconnect() {
        source.close();
    }

    @Override
    public void getPlayer(UUID UUID, Completable<DatabasePlayer> completable) {
        submit(() -> {
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
                    reference.set(player);
                }
                set.close();
                statement.close();
            } catch (SQLException ex) {
                throw new RuntimeException(
                        format("Unable to get player with UUID '%s' from database!", UUID.toString()),
                        ex);
            }
            complete(completable, reference.get());
        });
    }

    @Override
    public void getPlayer(String name, Completable<DatabasePlayer> completable) {
        submit(() -> {
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
                    reference.set(player);
                }
                set.close();
                statement.close();
            } catch (SQLException ex) {
                throw new RuntimeException(
                        format("Unable to get player with name '%s' from database!", name),
                        ex);
            }
            complete(completable, reference.get());
        });
    }

    @Override
    public void getPlayerList(Completable<List<DatabasePlayer>> completable) {
        submit(() -> {
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
                    reference.get().add(player);
                }
                set.close();
                statement.close();
            } catch (SQLException ex) {
                throw new RuntimeException("Unable to get player list from database!", ex);
            }
            complete(completable, reference.get());
        });
    }

    @Override
    public void getPlayerListByGroup(int groupId, Completable<List<DatabasePlayer>> completable) {
        submit(() -> {
            AtomicReference<List<DatabasePlayer>> reference = new AtomicReference<>(new ArrayList<>());
            try (Connection connection = getConnection()) {
                PreparedStatement statement = connection.prepareStatement(format(
                        "SELECT * FROM %s WHERE groupId=?",
                        table(DatabasePlayer.class)
                ));
                statement.setInt(1, groupId);
                ResultSet set = statement.executeQuery();
                while (!set.isClosed() && set.next()) {
                    DatabasePlayer player = new DatabasePlayer();
                    player.fetch(set);
                    reference.get().add(player);
                }
                set.close();
                statement.close();
            } catch (SQLException ex) {
                throw new RuntimeException("Unable to get player list from database!", ex);
            }
            complete(completable, reference.get());
        });
    }

    @Override
    public void insertPlayer(DatabasePlayer player) {
        submit(() -> {
            try (Connection connection = getConnection()) {
                PreparedStatement statement = connection.prepareStatement(format(
                        "INSERT INTO %s(uuid, name, groupId, permissions) VALUES (?, ?, ?, ?)",
                        table(DatabasePlayer.class)
                ));
                statement.setString(1, player.getUUID());
                statement.setString(2, player.getName());
                statement.setInt(3, player.getGroupId());
                statement.setString(4, convertPermissions(player.getPermissions()));
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
                        "UPDATE %s SET name=?, groupId=?, permissions=? WHERE uuid=?",
                        table(DatabasePlayer.class)
                ));
                statement.setString(1, player.getName());
                statement.setInt(2, player.getGroupId());
                statement.setString(3, convertPermissions(player.getPermissions()));
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

    private String convertPermissions(List<String> permissions) {
        CountDownLatch latch = new CountDownLatch(permissions.size());
        StringBuilder builder = new StringBuilder();
        permissions.forEach(permission -> {
            latch.countDown();
            builder.append(permission);
            if (latch.getCount() != 0) {
                builder.append("/-/");
            }
        });
        return builder.toString();
    }

    private void submit(final Runnable action) {
        check(action, "Database Action (Runnable) can't be null");
        if (isDatabaseThread()) {
            action.run();
        } else {
            queue.execute(action);
        }
    }

    private <T> void complete(final Completable<T> completable, final T result) {
        check(completable, "Completable<T> (Async Future) can't be null");
        completable.complete(result);
    }

    private boolean isDatabaseThread() {
        return threadList.contains(Thread.currentThread());
    }

    private <T> T check(final T object, final String error) {
        if (object != null) {
            return object;
        } else {
            throw new RuntimeException(error);
        }
    }
}
