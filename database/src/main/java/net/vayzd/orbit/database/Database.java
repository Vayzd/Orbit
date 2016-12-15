package net.vayzd.orbit.database;

public interface Database {

    void connect(String host, int port, String username, String password, String database);
}
