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

import net.vayzd.orbit.datastore.group.*;

import java.sql.*;
import java.util.*;

public interface Datastore extends AutoCloseable {

    void connect(DataCallback<Boolean> uponSchemaCompletion);

    void disconnect(DataCallback<Boolean> uponCompletion);

    Connection getConnection() throws SQLException;

    Connection getCaughtConnection();

    void fetchAndCacheGroups();

    Optional<DatastoreGroup> getGroup(String name);

    void getGroup(String name, DataCallback<Optional<DatastoreGroup>> callback);

    boolean hasGroup(String name);

    void hasGroup(String name, DataCallback<Boolean> callback);

    Optional<DatastoreGroup> getDefaultGroup();

    boolean hasDefaultGroup();

    boolean insertGroup(DatastoreGroup group);

    void insertGroup(DatastoreGroup group, DataCallback<Boolean> uponCompletion);

    boolean updateGroup(DatastoreGroup group);

    void updateGroup(DatastoreGroup group, DataCallback<Boolean> uponCompletion);

    boolean deleteGroup(DatastoreGroup group);

    void deleteGroup(DatastoreGroup group, DataCallback<Boolean> uponCompletion);

    Optional<DatastoreSubject> getSubject(UUID uniqueId);

    void getSubject(UUID uniqueId, DataCallback<Optional<DatastoreSubject>> callback);

    boolean hasSubject(UUID uniqueId);

    void hasSubject(UUID uniqueId, DataCallback<Boolean> callback);

    List<DatastoreSubject> getSubjectListByGroup(String name);

    void getSubjectListByGroup(String name, DataCallback<List<DatastoreSubject>> callback);

    boolean insertSubject(DatastoreSubject subject);

    void insertSubject(DatastoreSubject subject, DataCallback<Boolean> uponCompletion);

    boolean updateSubject(DatastoreSubject subject);

    void updateSubject(DatastoreSubject subject, DataCallback<Boolean> uponCompletion);

    boolean deleteSubject(DatastoreSubject subject);

    void deleteSubject(DatastoreSubject subject, DataCallback<Boolean> uponCompletion);
}