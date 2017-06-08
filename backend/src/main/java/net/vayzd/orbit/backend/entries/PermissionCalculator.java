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

import com.google.common.collect.*;
import lombok.*;
import net.vayzd.orbit.backend.*;

import java.util.*;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter(AccessLevel.PRIVATE)
public class PermissionCalculator {

    private final @NonNull DatabaseGroup group;
    private final @NonNull DataStore dataStore;

    public static PermissionCalculator of(DatabaseGroup group, DataStore dataStore) {
        return new PermissionCalculator(group, dataStore);
    }

    public ImmutableList<String> computeEffectivePermissions() {
        List<String> permissions = new LinkedList<>();
        for (String ancestor : calculateGroupTree()) {
            for (String permission : getGroupFor(ancestor).getPermissions()) {
                if (permissions.contains(permission)) {
                    continue;
                }
                permissions.add(permission);
            }
        }
        return squash(permissions);
    }

    private ImmutableList<String> calculateGroupTree() {
        List<String> tree = new ArrayList<>();
        for (String next : group.getParents()) {
            if (next.equals(group.getName())) {
                continue;
            }
            for (String trunk : calculateBackwardGroupTree(next)) {
                tree.add(0, trunk);
            }
        }
        return squash(tree);
    }

    private ImmutableList<String> calculateBackwardGroupTree(String group) {
        List<String> tree = new ArrayList<>();
        tree.add(group);
        for (String next : getGroupFor(group).getParents()) {
            try {
                if (next.equals(group)) {
                    continue;
                }
                if (getGroupFor(next).getParents().contains(group)) {
                    continue;
                }
                tree.addAll(new ArrayList<>(calculateBackwardGroupTree(next)));
            } catch (StackOverflowError error) {
                return squash(getGroupFor(group).getParents());
            }
        }
        return squash(tree);
    }

    private DatabaseGroup getGroupFor(String name) {
        Optional<DatabaseGroup> group = dataStore.getGroup(name);
        return group.orElseGet(this::getGroup);
    }

    private <T> ImmutableList<T> squash(List<T> list) {
        return ImmutableSet.copyOf(list).asList();
    }
}
