package net.vayzd.orbit.command;

@FunctionalInterface
interface OrbitCommandExecutor {

    boolean onCommand(OrbitCommandSender sender, String[] args);
}
