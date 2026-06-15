package org.lovelycheck.core.config;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public interface ConfigNode {

    @Nullable
    ConfigNode getTable(String path);

    boolean isTable(String path);

    boolean isList(String path);

    @Nullable
    String getString(String path);

    @Nullable
    Boolean getBoolean(String path);

    @Nullable
    Long getLong(String path);

    @Nullable
    List<?> getList(String path);

    Set<String> keySet();
}
