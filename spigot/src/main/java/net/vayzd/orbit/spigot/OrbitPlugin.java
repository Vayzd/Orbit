package net.vayzd.orbit.spigot;

import net.vayzd.orbit.database.*;
import net.vayzd.orbit.database.entries.*;
import net.vayzd.orbit.database.model.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.*;

import java.util.*;
import java.util.logging.*;

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
            public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
                database.getPlayer(event.getUniqueId(), result -> {
                    System.out.println("hello1");
                    if (result == null) {
                        result = new DatabasePlayer();
                        result.setUUID(event.getUniqueId().toString());
                        result.setName(event.getName());
                        result.setGroupId(4);
                        result.setPermissions(Arrays.asList("test.perm.1", "test.perm.2"));
                        System.out.println("hello2");
                        database.insertPlayer(result);
                    } else {
                        System.out.println("hello3");
                        System.out.println("result.getUUID() = " + result.getUUID());
                        System.out.println("result.getName() = " + result.getName());
                        System.out.println("result.getGroupId() = " + result.getGroupId());
                        System.out.println("result.getPermissions() = " + result.getPermissions());
                    }
                    System.out.println("hello4");
                });
            }
        }, this);
    }
}
