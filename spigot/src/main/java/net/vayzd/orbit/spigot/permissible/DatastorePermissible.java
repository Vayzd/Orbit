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
package net.vayzd.orbit.spigot.permissible;

import net.vayzd.orbit.datastore.group.*;
import org.bukkit.entity.*;
import org.bukkit.permissions.*;

import java.util.*;

public class DatastorePermissible extends PermissibleBase {

    private final DatastoreSubject subject;

    public DatastorePermissible(final Player player, final DatastoreSubject subject) {
        super(player);
        this.subject = subject;
    }

    @Override
    public boolean hasPermission(String permission) {
        return subject != null && subject.hasPermission(permission);
    }

    @Override
    public boolean hasPermission(Permission permission) {
        return subject != null && subject.hasPermission(permission.getName());
    }

    @Override
    public boolean isPermissionSet(String permission) {
        return subject != null && subject.getCombinedPermissionSet().contains(permission);
    }

    @Override
    public void recalculatePermissions() {
    }

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        TreeSet<PermissionAttachmentInfo> permissionSet = new TreeSet<>();
        if (subject == null) {
            return permissionSet;
        }
        subject.getCombinedPermissionSet().forEach(permission -> {
            if (permission != null) {
                permissionSet.add(new PermissionAttachmentInfo(
                        this,
                        permission,
                        null,
                        !permission.startsWith("-")
                ));
            }
        });
        return permissionSet;
    }
}
