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

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import static java.util.regex.Pattern.*;

final class PermissionMatcher {

    private ConcurrentMap<String, Boolean> cache = null;
    private Set<Pattern> patternSet;
    @Getter
    private final TreeSet<String> permissionSet;

    PermissionMatcher(TreeSet<String> permissionSet, boolean caching) {
        this.patternSet = new HashSet<>();
        this.permissionSet = permissionSet;
        deliver();
        if (caching) {
            cache = new ConcurrentHashMap<>();
        }
    }

    void updatePermissionSet(TreeSet<String> updatedSet) {
        permissionSet.clear();
        permissionSet.addAll(updatedSet);
        deliver();
        purgeCache();
    }

    boolean hasPermission(String permission) {
        if (cache != null && cache.containsKey(permission)) {
            return cache.get(permission);
        }
        for (Pattern pattern : patternSet) {
            if (pattern.matcher(permission).matches()) {
                if (cache != null) {
                    cache.put(permission, true);
                }
                return true;
            }
        }
        if (cache != null) {
            cache.put(permission, false);
        }
        return false;
    }

    private void purgeCache() {
        if (cache != null) {
            cache.clear();
        }
    }

    private void deliver() {
        patternSet.clear();
        permissionSet.forEach(permission -> patternSet.add(create(permission)));
    }

    private Pattern create(String permission) {
        try {
            return compile(prepare(permission), Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException ex) {
            return compile(quote(permission), Pattern.CASE_INSENSITIVE);
        }
    }

    private String prepare(String permission) {
        if (permission.startsWith("$")) {
            return permission.substring(1);
        }
        return permission.replace(".", "\\.").replace("*", "(.*)");
    }
}