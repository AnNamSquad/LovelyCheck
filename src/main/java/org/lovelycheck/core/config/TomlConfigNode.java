package org.lovelycheck.core.config;

import org.jetbrains.annotations.Nullable;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

import java.util.List;
import java.util.Set;

public final class TomlConfigNode implements ConfigNode {

    private final TomlTable table;

    public TomlConfigNode(TomlTable table) {
        this.table = table;
    }

    @Override
    public @Nullable ConfigNode getTable(String path) {
        TomlTable nested = table.getTable(path);
        if (nested == null) {
            nested = table.getTable(alternatePath(path));
        }
        return nested != null ? new TomlConfigNode(nested) : null;
    }

    @Override
    public boolean isTable(String path) {
        return table.isTable(path) || table.isTable(alternatePath(path));
    }

    @Override
    public boolean isList(String path) {
        return table.isArray(path) || table.isArray(alternatePath(path));
    }

    @Override
    public @Nullable String getString(String path) {
        String value = table.getString(path);
        return value != null ? value : table.getString(alternatePath(path));
    }

    @Override
    public @Nullable Boolean getBoolean(String path) {
        Boolean value = table.getBoolean(path);
        return value != null ? value : table.getBoolean(alternatePath(path));
    }

    @Override
    public @Nullable Long getLong(String path) {
        Long value = table.getLong(path);
        return value != null ? value : table.getLong(alternatePath(path));
    }

    @Override
    public @Nullable List<?> getList(String path) {
        TomlArray array = table.getArray(path);
        if (array == null) {
            array = table.getArray(alternatePath(path));
        }
        return array != null ? array.toList() : null;
    }

    @Override
    public Set<String> keySet() {
        return table.keySet();
    }

    private String alternatePath(String path) {
        return path.indexOf('_') >= 0 ? path.replace('_', '-') : path.replace('-', '_');
    }
}
