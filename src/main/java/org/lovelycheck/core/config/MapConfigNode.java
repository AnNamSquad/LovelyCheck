package org.lovelycheck.core.config;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MapConfigNode implements ConfigNode {

    private final Map<String, Object> values;

    public MapConfigNode(Map<?, ?> values) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (entry.getKey() != null) {
                normalized.put(entry.getKey().toString(), entry.getValue());
            }
        }
        this.values = Collections.unmodifiableMap(normalized);
    }

    @Override
    public @Nullable ConfigNode getTable(String path) {
        Object value = getValue(path);
        if (value instanceof Map<?, ?> map) {
            return new MapConfigNode(map);
        }
        return null;
    }

    @Override
    public boolean isTable(String path) {
        return getValue(path) instanceof Map<?, ?>;
    }

    @Override
    public boolean isList(String path) {
        return getValue(path) instanceof List<?>;
    }

    @Override
    public @Nullable String getString(String path) {
        Object value = getValue(path);
        return value instanceof String string ? string : null;
    }

    @Override
    public @Nullable Boolean getBoolean(String path) {
        Object value = getValue(path);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            if ("true".equalsIgnoreCase(string)) {
                return true;
            }
            if ("false".equalsIgnoreCase(string)) {
                return false;
            }
        }
        return null;
    }

    @Override
    public @Nullable Long getLong(String path) {
        Object value = getValue(path);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @Override
    public @Nullable List<?> getList(String path) {
        Object value = getValue(path);
        return value instanceof List<?> list ? list : null;
    }

    @Override
    public Set<String> keySet() {
        return values.keySet();
    }

    private @Nullable Object getValue(String path) {
        Object current = values;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = getMapValue(map, segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private @Nullable Object getMapValue(Map<?, ?> map, String key) {
        if (map.containsKey(key)) {
            return map.get(key);
        }

        String hyphen = key.replace('_', '-');
        if (map.containsKey(hyphen)) {
            return map.get(hyphen);
        }

        String underscore = key.replace('-', '_');
        if (map.containsKey(underscore)) {
            return map.get(underscore);
        }

        return null;
    }
}
