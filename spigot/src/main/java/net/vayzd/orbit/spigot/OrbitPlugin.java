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

import lombok.*;
import net.vayzd.orbit.datastore.*;
import net.vayzd.orbit.datastore.group.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.*;

import java.util.*;
import java.util.logging.*;

import static java.util.Arrays.*;

public class OrbitPlugin extends JavaPlugin implements Listener {

    @Getter
    private Datastore datastore;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("Connecting to database...");
        try {
            datastore = OrbitDatastore.createDatastore(
                    getLogger(),
                    new DatastoreCredentials(
                            getConfig().getString("database.hostname"),
                            getConfig().getInt("database.port"),
                            getConfig().getString("database.username"),
                            getConfig().getString("database.password"),
                            getConfig().getString("database.defaultDatabase")
                    ),
                    Thread.currentThread(),
                    getConfig().getInt("pool-size")
            );
            datastore.connect((result, error) -> {
                if (error == null) {
                    getLogger().info("Successfully inserted default table schema!");
                    datastore.fetchAndCacheGroups();
                } else {
                    shutdownDueToDatastoreFailure(error);
                }
            });
            getLogger().info("Successfully connected!");
        } catch (Exception error) {
            shutdownDueToDatastoreFailure(error);
        }
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        /*Optional<DatastoreGroup> found = datastore.getGroup("default");
        if (found.isPresent()) {
            getLogger().info("Group with name 'default' is present in database!");
            getLogger().info(found.get().toString());
        } else {
            getLogger().warning("Unable to find group with name 'default'!");
        }*/
        Optional<DatastoreSubject> result = datastore.getSubject(event.getUniqueId());
        if (result.isPresent()) {
            getLogger().info("");
            getLogger().info("Found subject by UUID '" + event.getUniqueId().toString() + "'");
            getLogger().info("");
            getLogger().info(result.get().toString());
            getLogger().info("");
        } else {
            getLogger().warning("");
            getLogger().warning("Unable to find subject by UUID '" + event.getUniqueId().toString() + "'");
            getLogger().warning("Inserting it...");
            getLogger().warning("");
            datastore.insertSubject(result.orElseGet(() -> {
                DatastoreSubject subject = new DatastoreSubject();
                subject.setUniqueId(event.getUniqueId());
                subject.setGroupName("default");
                subject.updatePermissionSet(new TreeSet<>(asList("test.1", "test.2")));
                return subject;
            }), (success, error) -> {
                if (error == null && success) {
                    getLogger().info("");
                    getLogger().info("Successfully inserted!");
                    getLogger().info("");
                } else {
                    getLogger().warning("");
                    getLogger().log(Level.WARNING, "Unable to insert subject!", error);
                    getLogger().warning("");
                }
            });
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        /*datastore.getGroup("brutal", (found, error) -> {
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
        });*/
        datastore.getSubject(event.getPlayer().getUniqueId(), (result, error) -> {
            if (error == null) {
                if (result.isPresent()) {
                    getLogger().info("");
                    getLogger().info("Found subject by UUID '" + event.getPlayer().getUniqueId().toString() + "'");
                    getLogger().info("");
                    getLogger().info(result.get().toString());
                    getLogger().info("");
                } else {
                    getLogger().warning("Unable to find subject!");
                }
            } else {
                getLogger().warning("Unable to find subject!");
            }
        });
    }

    private void shutdownDueToDatastoreFailure(Throwable error) {
        getLogger().log(Level.WARNING, "Unable to construct datastore instance", error);
        getLogger().warning("Shutting down...");
        getServer().shutdown();
    }
}
