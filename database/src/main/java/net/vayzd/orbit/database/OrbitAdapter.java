package net.vayzd.orbit.database;

import lombok.*;
import net.vayzd.orbit.database.model.*;

import java.sql.*;
import java.util.logging.*;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class OrbitAdapter {

    private final Database database;

    public OrbitAdapter(Logger logger, Type type, String host, int port, String username, String password, String database, int pool) {
        try {
            this.database = new OrbitDatabase(logger, type, host, port, username, password, database, pool);
        } catch (SQLException ex) {
            throw new RuntimeException("Unable to establish database connectivity!", ex);
        }
    }
}
