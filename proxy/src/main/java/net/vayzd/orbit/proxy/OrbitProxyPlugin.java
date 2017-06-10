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
package net.vayzd.orbit.proxy;

import com.google.common.io.*;
import lombok.*;
import net.md_5.bungee.api.plugin.*;
import net.md_5.bungee.config.*;
import net.vayzd.orbit.datastore.*;
import net.vayzd.orbit.datastore.group.*;

import java.io.*;
import java.util.logging.*;

public class OrbitProxyPlugin extends Plugin {

    @Getter
    private Datastore datastore;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("Connecting to database...");
        try {
            Configuration configuration = YamlConfiguration.getProvider(ConfigurationProvider.class).load(
                    new File(getDataFolder(), "config.yml")
            );
            datastore = OrbitDatastore.createDatastore(
                    getLogger(),
                    new DatastoreCredentials(
                            configuration.getString("database.hostname"),
                            configuration.getInt("database.port"),
                            configuration.getString("database.username"),
                            configuration.getString("database.password"),
                            configuration.getString("database.defaultDatabase")
                    ),
                    Thread.currentThread(),
                    configuration.getInt("pool-size")
            );
            datastore.connect((result, error) -> {
                if (error == null) {
                    datastore.fetchAndCacheGroups();
                    if (!datastore.hasDefaultGroup()) {
                        datastore.insertGroup(newDefaultGroup(), (success, failure) -> {
                            if (success && failure == null) {
                                datastore.fetchAndCacheGroups(); // cache again.
                            }
                        });
                    }
                } else {
                    shutdownDueToDatastoreFailure(error);
                }
            });
            getLogger().info("Successfully connected!");
        } catch (Exception error) {
            shutdownDueToDatastoreFailure(error);
        }
    }

    @Override
    public void onDisable() {
        datastore.disconnect((success, error) -> {
        });
    }

    private void saveDefaultConfig() {
        if (!getDataFolder().exists()) {
            if (getDataFolder().mkdir()) {
                File defaultFile = new File(getDataFolder(), "config.yml");
                if (!defaultFile.exists()) {
                    try (InputStream input = getResourceAsStream("config.yml");
                         OutputStream output = new FileOutputStream(defaultFile)) {
                        ByteStreams.copy(input, output);
                    } catch (IOException error) {
                        error.printStackTrace();
                    }
                }
            }
        }
    }

    private DatastoreGroup newDefaultGroup() {
        DatastoreGroup defaultGroup = new DatastoreGroup();
        defaultGroup.setName("default");
        defaultGroup.getParentSet().add(""); // no parents
        defaultGroup.setDefaultGroup(true);
        defaultGroup.setDisplayName("Default");
        defaultGroup.setPrefix("");
        defaultGroup.setSuffix("");
        defaultGroup.setTabOrder(32767); // max "SMALLINT" value
        defaultGroup.getPermissionSet().add(""); // no permissions
        return defaultGroup;
    }

    private void shutdownDueToDatastoreFailure(Throwable error) {
        getLogger().log(Level.WARNING, "Unable to construct datastore instance", error);
        getLogger().warning("Shutting down...");
        getProxy().stop();
    }
}
