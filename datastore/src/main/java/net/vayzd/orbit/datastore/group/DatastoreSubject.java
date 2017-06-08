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
package net.vayzd.orbit.datastore.group;

import lombok.*;
import net.vayzd.orbit.datastore.*;

import java.sql.*;
import java.util.*;

import static com.google.common.base.Preconditions.*;

@DatastoreTable(name = "subjects")
@Getter
@Setter
@ToString
public class DatastoreSubject implements DatastoreEntry {

    private final PermissionMatcher matcher;
    private UUID uniqueId = null;
    private String name = null;
    @Deprecated
    private String groupName = null;
    private DatastoreGroup group = null;
    private long lastSeen = 0L;
    @Setter(AccessLevel.PRIVATE)
    private TreeSet<String> permissionSet = new TreeSet<>();

    public DatastoreSubject() {
        this.matcher = new PermissionMatcher(permissionSet, false);
    }

    public void updatePermissionSet(@NonNull Set<String> updatedSet) {
        checkNotNull(updatedSet, "Updated permission set can't be null");
        permissionSet.clear();
        permissionSet.addAll(updatedSet);
        matcher.updatePermissionSet(updatedSet);
    }

    public boolean hasPermission(@NonNull String permission) {
        checkNotNull(permission, "Permission to check for can't be null");
        return matcher.hasPermission(permission);
    }

    public Set<String> getCombinedPermissionSet() {
        Set<String> combined = new TreeSet<>();
        combined.addAll(permissionSet);
        combined.addAll(group != null ? group.getPermissionSet() : new TreeSet<>());
        return Collections.unmodifiableSet(combined);
    }

    @Override
    public void readFrom(ResultSet set) throws SQLException {
        setUniqueId(UUID.fromString(set.getString(1)));
        setName(set.getString(2));
        setGroupName(set.getString(3));
        setLastSeen(set.getLong(4));
        setPermissionSet(getSetFromString(set, 5));
    }
}
