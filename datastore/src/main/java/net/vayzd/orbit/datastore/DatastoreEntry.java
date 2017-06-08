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

import java.sql.*;
import java.util.*;

import static com.google.common.base.Preconditions.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;

public interface DatastoreEntry {

    void readFrom(ResultSet set) throws SQLException;

    default TreeSet<String> getSetFromString(ResultSet set, int columnIndex) throws SQLException {
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