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

import net.vayzd.orbit.datastore.*;
import net.vayzd.orbit.datastore.group.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.*;

import java.util.*;
import java.util.logging.*;

public class OrbitPlugin extends JavaPlugin implements Listener {

    private Datastore datastore;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("Connecting to database...");
        try {
            datastore = OrbitDatastore.createDatastore(
                    getLogger(),
                    new DatastoreCredentials(
                            getConfig().getString("hostname"),
                            getConfig().getInt("port"),
                            getConfig().getString("username"),
                            getConfig().getString("password"),
                            getConfig().getString("defaultDatabase")
                    ),
                    Thread.currentThread(),
                    getConfig().getInt("pool-size")
            );
            datastore.connect((result, error) -> {
                if (error == null) {
                    getLogger().info("Successfully connected!");
                    //datastore.fetchAndCacheGroups();
                } else {
                    shutdownDueToDatastoreFailure(error);
                }
            });
        } catch (Exception error) {
            shutdownDueToDatastoreFailure(error);
        }
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        Optional<DatastoreGroup> found = datastore.getGroup("default");
        if (found.isPresent()) {
            getLogger().info("Group with name 'default' is present in database!");
            getLogger().info(found.get().toString());
        } else {
            getLogger().warning("Unable to find group with name 'default'!");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        datastore.getGroup("default", (found, error) -> {
            if (error == null) {
                if (found.isPresent()) {
                    getLogger().info("Group with name 'default' is present in database!");
                    getLogger().info(found.get().toString());
                } else {
                    getLogger().warning("Unable to find group with name 'default'!");
                }
            } else {
                getLogger().warning("Unable to find group with name 'default'!");
            }
        });
    }

    private void shutdownDueToDatastoreFailure(Throwable error) {
        getLogger().log(Level.WARNING, "Unable to construct datastore instance", error);
        getLogger().warning("Shutting down...");
        getServer().shutdown();
    }
}
