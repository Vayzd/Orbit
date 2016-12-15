package net.vayzd.orbit.database.model;

import java.util.*;

public final class QuerySet {

    private final Map<String, Object> base = new LinkedHashMap<>();

    public QuerySet append(String key, Object value) {
        base.put(key, value);
        return this;
    }

    public LinkedHashMap<String, Object> get() {
        return new LinkedHashMap<>(base);
    }
}
