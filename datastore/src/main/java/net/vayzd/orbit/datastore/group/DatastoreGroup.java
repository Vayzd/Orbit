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
import static java.util.Arrays.*;
import static java.util.Collections.*;

@DatastoreTable(name = "groups")
@Getter
@Setter
public class DatastoreGroup implements DatastoreEntry {

    private final PermissionMatcher matcher;
    private String name = null;
    private TreeSet<String> parentSet = new TreeSet<>();
    private String prefix = null,
            suffix = null,
            tabColor = null;
    private int tabOrder = 0;
    @Getter(AccessLevel.PACKAGE)
    @Setter(AccessLevel.PRIVATE)
    private TreeSet<String> permissionSet = new TreeSet<>();

    public DatastoreGroup() {
        this.matcher = new PermissionMatcher(permissionSet, true);
    }

    public DatastoreGroup(@NonNull String name) {
        this();
        checkNotNull(name, "Name can't be null");
        this.name = name;
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

    @Override
    public void readFrom(ResultSet set) throws SQLException {
        setName(set.getString(1));
        setParentSet(getSetFromString(set, 2));
        setPrefix(set.getString(3));
        setSuffix(set.getString(4));
        setTabColor(set.getString(5));
        setTabOrder(set.getInt(6));
        setPermissionSet(getSetFromString(set, 7));
    }

    private TreeSet<String> getSetFromString(ResultSet set, int columnIndex) throws SQLException {
        String value = set.getString(columnIndex);
        try {
            checkNotNull(value);
            checkArgument(!value.isEmpty());
            checkArgument(value.contains(";"));
            return new TreeSet<>(asList(value.split(";")));
        } catch (NullPointerException | IllegalArgumentException ignored) {
            return new TreeSet<>(singletonList("default"));
        }
    }
}
