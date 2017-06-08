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
package net.vayzd.orbit.spigot;

import net.vayzd.orbit.backend.*;
import net.vayzd.orbit.backend.entries.*;
import net.vayzd.orbit.backend.model.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.*;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

import static java.util.Arrays.*;

public class OrbitPlugin extends JavaPlugin {

    private DataStore dataStore;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("Connecting to database...");
        try {
            dataStore = OrbitDataStore.newDataStore(getLogger(),
                    Type.valueOf(getConfig().getString("database.type")),
                    getConfig().getString("database.host"),
                    getConfig().getInt("database.port"),
                    getConfig().getString("database.username"),
                    getConfig().getString("database.password"),
                    getConfig().getString("database.database"),
                    getConfig().getInt("pool-size")
            );
            dataStore.connect();
            getLogger().info("Successfully connected!");
        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "Failed to connect to database!", ex);
            getLogger().info("Shutting down...");
            getServer().shutdown();
        }
        dataStore.fetchAndCacheGroups();
        getServer().getPluginManager().registerEvents(new Listener() {

            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
                final UUID UUID = event.getPlayer().getUniqueId();
                final String name = event.getPlayer().getName();
                dataStore.getPlayer(UUID, found -> {
                    DatabasePlayer result = found.orElseGet(() -> {
                        DatabasePlayer player = new DatabasePlayer();
                        player.setUUID(UUID.toString());
                        player.setName(name);
                        player.setGroup("default");
                        player.setPermissions(asList("test.perm.1", "test.perm.2"));
                        dataStore.insertPlayer(player);
                        return player;
                    });
                    if (!name.equals(result.getName())) {
                        result.setName(name);
                        dataStore.updatePlayer(result);
                    }
                    System.out.println("result.getCombinedPermissions() = " + result.getCombinedPermissions());
                    System.out.println("result.hasPermission(\"test1\") = " + result.hasPermission("test1"));
                    System.out.println("result.hasPermission(\"test8\") = " + result.hasPermission("test8"));
                });
                AtomicReference<Long> reference = new AtomicReference<>(System.currentTimeMillis());
                Player player = event.getPlayer();
                dataStore.getPlayerList(result -> {
                    TreeSet<String> set = new TreeSet<>();
                    result.forEach(entry -> set.add(entry.getName()));
                    set.forEach(player::sendMessage);
                    player.sendMessage("§aTotal of '" + set.size() + "' unique names!");
                    player.sendMessage("§bTime taken: §e" + (System.currentTimeMillis() - reference.get()) + "ms");
                });
            }
        }, this);
    }
}
