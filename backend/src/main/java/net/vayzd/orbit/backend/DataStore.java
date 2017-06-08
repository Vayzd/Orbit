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

import net.vayzd.orbit.backend.entries.*;

import java.sql.*;
import java.util.*;

public interface DataStore {

    Connection getConnection() throws SQLException;

    void connect();

    void disconnect();

    void fetchAndCacheGroups();

    Optional<DatabaseGroup> getGroup(String name);

    void getGroup(String name, Completable<Optional<DatabaseGroup>> completable);

    boolean hasGroup(String name);

    void hasGroup(String name, Completable<Boolean> completable);

    void insertGroup(DatabaseGroup name);

    void updateGroup(DatabaseGroup name);

    void deleteGroup(DatabaseGroup name);

    Optional<DatabasePlayer> getPlayer(UUID UUID);

    void getPlayer(UUID UUID, Completable<Optional<DatabasePlayer>> completable);

    Optional<DatabasePlayer> getPlayer(String name);

    void getPlayer(String name, Completable<Optional<DatabasePlayer>> completable);

    boolean hasPlayer(UUID UUID);

    void hasPlayer(UUID UUID, Completable<Boolean> completable);

    boolean hasPlayer(String name);

    void hasPlayer(String name, Completable<Boolean> completable);

    List<DatabasePlayer> getPlayerList();

    void getPlayerList(Completable<List<DatabasePlayer>> completable);

    List<DatabasePlayer> getPlayerListByGroup(String name);

    void getPlayerListByGroup(String name, Completable<List<DatabasePlayer>> completable);

    void insertPlayer(DatabasePlayer player);

    void updatePlayer(DatabasePlayer player);

    void deletePlayer(DatabasePlayer player);
}
