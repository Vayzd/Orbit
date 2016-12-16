package net.vayzd.orbit.database.model;

import lombok.*;

@RequiredArgsConstructor
@Getter
public enum Type {

    MARIADB("mariadb", "org.mariadb.jdbc.Driver"),
    MYSQL("mysql", "com.mysql.jdbc.Driver");

    private final String name;
    private final String driverClassName;
}
