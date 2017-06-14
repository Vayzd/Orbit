package net.vayzd.orbit.command;

import net.md_5.bungee.api.chat.*;

import java.util.*;

public interface OrbitCommandSender {

    void sendMessage(String message);

    void sendMessage(BaseComponent[] components);

    UUID getUniqueId();

    String getName();

    boolean isConsole();

    boolean isPlayer();

    boolean isOperator();

    boolean hasPermission();
}
