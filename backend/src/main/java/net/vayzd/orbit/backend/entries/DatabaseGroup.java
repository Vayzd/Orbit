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

@Table(name = "groups")
@Getter
@Setter
public class DatabaseGroup extends DatabaseEntry {

    private final PermissionMatcher matcher;
    private String name = null;
    private String prefix = null,
            suffix = null,
            color = null;
    private int order = 0;
    private List<String> parents = new LinkedList<>();
    private List<String> permissions = new LinkedList<>();

    public DatabaseGroup() {
        this.matcher = new PermissionMatcher(permissions, true);
    }

    public DatabaseGroup(String name) {
        this();
        this.name = name;
    }

    public void setPermissions(List<String> update) {
        permissions = update;
        matcher.setPermissions(update);
    }

    boolean hasPermission(String permission) {
        return matcher.hasPermission(permission);
    }

    @Override
    public void fetch(ResultSet set) throws SQLException {
        setName(set.getString("name"));
        setParents(new LinkedList<>(asList(set.getString("parents").split(";"))));
        setPrefix(set.getString("prefix"));
        setSuffix(set.getString("suffix"));
        setColor(set.getString("color"));
        setOrder(set.getInt("order"));
        setPermissions(new LinkedList<>(asList(set.getString("permissions").split(";"))));
    }
}
