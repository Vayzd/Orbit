package net.vayzd.orbit.database.model;

import java.sql.*;

public abstract class DatabaseEntry {

    public abstract void create(QuerySet set);

    public abstract void fetch(ResultSet set);
}
