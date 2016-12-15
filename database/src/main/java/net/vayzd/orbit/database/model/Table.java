package net.vayzd.orbit.database.model;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
public @interface Table {

    String name();
}
