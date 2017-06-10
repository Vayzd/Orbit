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
package net.vayzd.orbit.spigot.listener;

import lombok.*;
import net.vayzd.orbit.datastore.group.*;
import net.vayzd.orbit.spigot.*;
import net.vayzd.orbit.spigot.permissible.*;
import net.vayzd.orbit.spigot.reflect.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

@RequiredArgsConstructor
public class SubjectListener implements Listener {

    private final ConcurrentMap<UUID, DatastoreSubject> subjectMap = new ConcurrentHashMap<>();
    private final OrbitPlugin plugin;

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult().equals(AsyncPlayerPreLoginEvent.Result.ALLOWED)) {
            final UUID uniqueId = event.getUniqueId();
            DatastoreSubject result = plugin.getDatastore().getSubject(uniqueId).orElseGet(() -> {
                DatastoreSubject subject = new DatastoreSubject();
                subject.setUniqueId(uniqueId);
                Optional<DatastoreGroup> defaultGroup = plugin.getDatastore().getDefaultGroup();
                if (defaultGroup.isPresent()) {
                    DatastoreGroup group = defaultGroup.get();
                    subject.setGroup(group);
                    subject.setGroupName(group.getName());
                } else {
                    subject.setGroupName("default");
                }
                plugin.getDatastore().insertSubject(subject);
                return subject;
            });
            subjectMap.putIfAbsent(uniqueId, result);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult().equals(PlayerLoginEvent.Result.ALLOWED)) {
            final Player player = event.getPlayer();
            final UUID uniqueId = player.getUniqueId();
            DatastoreSubject subject = subjectMap.remove(uniqueId);
            if (subject == null) {
                event.disallow(
                        PlayerLoginEvent.Result.KICK_OTHER,
                        "§cAn unexpected error occured. Please try to reconnect!"
                );
                return;
            }
            injectPermissions(player, subject);
        }
    }

    private void injectPermissions(final Player player, final DatastoreSubject subject) {
        try {
            Class<?> entity = ReflectionUtil.getClassFromOBC("entity.CraftHumanEntity");
            ReflectionUtil.getAccessibleField(entity, "perm")
                    .set(player, new DatastorePermissible(player, subject));
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException error) {
            plugin.getLogger().log(Level.WARNING, "Unable to inject permissions!", error);
        }
    }
}
