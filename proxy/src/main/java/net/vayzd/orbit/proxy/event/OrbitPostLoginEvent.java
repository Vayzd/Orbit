package net.vayzd.orbit.proxy.event;

import lombok.*;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.*;
import net.md_5.bungee.api.plugin.*;

/**
 * Event called after a {@link ProxiedPlayer} has received
 * its permission data.
 * <p>
 * From now on permission data of this palyer can be checked
 * via {@link CommandSender#hasPermission(String)}!
 */
@EqualsAndHashCode(callSuper = false)
@ToString
@Data
public class OrbitPostLoginEvent extends Event {

    private final ProxiedPlayer player;
}
