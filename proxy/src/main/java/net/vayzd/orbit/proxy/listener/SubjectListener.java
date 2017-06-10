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
package net.vayzd.orbit.proxy.listener;

import lombok.*;
import net.md_5.bungee.api.*;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.connection.*;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.*;
import net.md_5.bungee.event.*;
import net.vayzd.orbit.datastore.group.*;
import net.vayzd.orbit.proxy.*;
import net.vayzd.orbit.proxy.event.*;

import java.util.*;
import java.util.concurrent.*;

@RequiredArgsConstructor
public class SubjectListener implements Listener {

    private final BaseComponent[] UNEXPECTED_ERROR = new ComponentBuilder("")
            .append("An unexpected error occured whilst logging in.")
            .color(ChatColor.RED)
            .append("\n\nPlease try to reconnect!")
            .color(ChatColor.GREEN)
            .create();
    private final ConcurrentMap<UUID, DatastoreSubject> subjectMap = new ConcurrentHashMap<>();
    private final OrbitProxyPlugin plugin;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(final LoginEvent event) {
        if (event.isCancelled()) {
            return;
        }
        event.registerIntent(plugin);
        plugin.getProxy().getScheduler().runAsync(plugin, () -> {
            if (event.isCancelled()) {
                event.completeIntent(plugin);
                return;
            }
            final UUID uniqueId = event.getConnection().getUniqueId();
            DatastoreSubject subject = plugin.getDatastore().getSubject(uniqueId).orElseGet(() -> {
                DatastoreSubject preset = new DatastoreSubject();
                preset.setUniqueId(uniqueId);
                plugin.getDatastore().getDefaultGroup().ifPresent(defaultGroup -> {
                    preset.setGroup(defaultGroup);
                    preset.setGroupName(defaultGroup.getName());
                });
                return preset;
            });
            Callback<OrbitLoginEvent> callback = (result, error) -> {
                try {
                    if (result.isCancelled()) {
                        event.setCancelReason(result.getCancelReason());
                        event.setCancelled(result.isCancelled());
                        return;
                    }
                    subjectMap.putIfAbsent(uniqueId, subject);
                } finally {
                    event.completeIntent(plugin);
                }
            };
            plugin.getProxy().getPluginManager().callEvent(new OrbitLoginEvent(event, subject, callback));
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPostLogin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        UUID uniqueId = player.getUniqueId();
        DatastoreSubject subject = subjectMap.remove(uniqueId);
        if (subject == null) {
            player.disconnect(UNEXPECTED_ERROR);
            return;
        }
        for (String permission : subject.getCombinedPermissionSet()) {
            if (!permission.startsWith("-")) {
                player.setPermission(permission, true);
            } else {
                player.setPermission(permission, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        UUID uniqueId = event.getPlayer().getUniqueId();
        if (subjectMap.containsKey(uniqueId)) {
            subjectMap.remove(uniqueId);
        }
    }
}
