package net.vayzd.orbit.spigot;

import net.vayzd.orbit.database.*;
import net.vayzd.orbit.database.entries.*;
import net.vayzd.orbit.database.model.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.*;

import java.util.*;
import java.util.logging.*;

import static java.util.Arrays.*;

public class OrbitPlugin extends JavaPlugin {

    private Database database;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("Connecting to database...");
        try {
            OrbitAdapter adapter = new OrbitAdapter(getLogger(),
                    Type.valueOf(getConfig().getString("database.type")),
                    getConfig().getString("database.host"),
                    getConfig().getInt("database.port"),
                    getConfig().getString("database.username"),
                    getConfig().getString("database.password"),
                    getConfig().getString("database.database"),
                    getConfig().getInt("pool-size")
            );
            database = adapter.getDatabase();
            database.connect();
            getLogger().info("Successfully connected!");
        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "Failed to connect to database!", ex);
            getLogger().info("Shutting down...");
            getServer().shutdown();
        }
        getServer().getPluginManager().registerEvents(new Listener() {

            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
                final UUID UUID = event.getPlayer().getUniqueId();
                final String name = event.getPlayer().getName();
                database.getPlayer(UUID, result -> {
                    if (result == null) {
                        result = new DatabasePlayer();
                        result.setUUID(UUID.toString());
                        result.setName(name);
                        result.setGroupId(0);
                        result.setPermissions(asList("test.perm.1", "test.perm.2"));
                        database.insertPlayer(result);
                    } else if (!result.getName().equals(name)) {
                        result.setName(name);
                        database.updatePlayer(result);
                    }
                });
            }
        }, this);
    }
}
