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
package net.vayzd.orbit.backend.entries;

import lombok.*;
import net.vayzd.orbit.backend.model.*;

import java.sql.*;
import java.util.*;

import static java.util.Arrays.*;

@Table(name = "players")
@Getter
@Setter
public class DatabasePlayer extends DatabaseEntry {

    private String UUID = null,
            name = null,
            group = null;
    private List<String> permissions = new LinkedList<>();
    private DatabaseGroup databaseGroup = null;
    private final PermissionMatcher matcher = new PermissionMatcher(permissions, false);

    public void setPermissions(List<String> update) {
        permissions = update;
        matcher.setPermissions(update);
    }

    public boolean hasPermission(String permission) {
        return databaseGroup.hasPermission(permission) || matcher.hasPermission(permission);
    }

    public List<String> getCombinedPermissions() {
        List<String> combined = new LinkedList<>();
        combined.addAll(permissions);
        combined.addAll(databaseGroup != null ? databaseGroup.getPermissions() : new LinkedList<>());
        return Collections.unmodifiableList(combined);
    }

    @Override
    public void fetch(ResultSet set) throws SQLException {
        setUUID(set.getString("uuid"));
        setName(set.getString("name"));
        setGroup(set.getString("group"));
        setPermissions(new LinkedList<>(asList(set.getString("permissions").split(";"))));
    }
}
