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
package net.vayzd.orbit.database.entries;

import lombok.*;
import net.vayzd.orbit.database.model.*;

import java.sql.*;

@Table(name = "players")
@Getter
@Setter
public class DatabasePlayer extends DatabaseEntry {

    private String UUID = null,
            name = null;
    private int groupId = 1,
            extraId = 0;

    @Override
    public void create(QuerySet set) {
        set.append("uuid", UUID).append("name", name).append("groupId", groupId).append("extraId", extraId);
    }

    @Override
    public void fetch(ResultSet set) throws SQLException {
        setUUID(set.getString("uuid"));
        setName(set.getString("name"));
        setGroupId(set.getInt("groupId"));
        setExtraId(set.getInt("extraId"));
    }
}
