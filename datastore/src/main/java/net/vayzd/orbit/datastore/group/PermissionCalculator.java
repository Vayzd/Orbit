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

import com.google.common.collect.*;
import lombok.*;
import net.vayzd.orbit.datastore.*;

import java.util.*;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter(AccessLevel.PRIVATE)
public class PermissionCalculator {

    @NonNull
    private final DatastoreGroup group;
    @NonNull
    private final Datastore datastore;

    public static PermissionCalculator of(DatastoreGroup group, Datastore datastore) {
        return new PermissionCalculator(group, datastore);
    }

    public ImmutableSet<String> computePermissionSet() {
        TreeSet<String> permissionSet = new TreeSet<>();
        for (String ancestor : calculateGroupTree()) {
            for (String permission : getGroupFor(ancestor).getPermissionSet()) {
                if (permissionSet.contains(permission)) {
                    continue;
                }
                permissionSet.add(permission);
            }
        }
        return squash(permissionSet);
    }

    private ImmutableSet<String> calculateGroupTree() {
        List<String> tree = new ArrayList<>();
        tree.add(group.getName());
        for (String next : group.getParentSet()) {
            if (next.equals(group.getName())) {
                continue;
            }
            for (String trunk : calculateBackwardGroupTree(next)) {
                tree.add(0, trunk);
            }
        }
        return squash(tree);
    }

    private ImmutableSet<String> calculateBackwardGroupTree(String group) {
        TreeSet<String> tree = new TreeSet<>();
        tree.add(group);
        for (String next : getGroupFor(group).getParentSet()) {
            try {
                if (next.equals(group)) {
                    continue;
                }
                if (getGroupFor(next).getParentSet().contains(group)) {
                    continue;
                }
                tree.addAll(new ArrayList<>(calculateBackwardGroupTree(next)));
            } catch (StackOverflowError error) {
                return squash(getGroupFor(group).getParentSet());
            }
        }
        return squash(tree);
    }

    private DatastoreGroup getGroupFor(String name) {
        Optional<DatastoreGroup> group = datastore.getGroup(name);
        return group.orElseGet(this::getGroup);
    }

    private <T> ImmutableSet<T> squash(Collection<T> list) {
        return ImmutableSet.copyOf(list);
    }
}