package org.lovelycheck.core.config;

import org.lovelycheck.core.LovelyCheckRegistry;
import org.lovelycheck.core.exceptions.ParsingException;
import org.lovelycheck.core.forge.ForgeConfig;
import org.lovelycheck.core.probing.ProbingConfig;
import org.jetbrains.annotations.NotNull;
import org.tomlj.Toml;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ConfigsManager {

    private static final ClassLoader classLoader = ConfigsManager.class.getClassLoader();
    private static final String ADVANCED_CONFIG = "advanced.yml";
    private static final String LEGACY_UNIFIED_CONFIG = "lovelycheck.toml";

    public static void init(File folder) {
        loadConfigs(folder);
    }

    public static void reload(File folder) {
        LovelyCheckRegistry.clear();
        loadConfigs(folder);
    }

    private static void loadConfigs(File folder) {
        folder.mkdirs();
        try {
            File advancedFile = new File(folder, ADVANCED_CONFIG);
            File legacyUnifiedFile = new File(folder, LEGACY_UNIFIED_CONFIG);
            if (advancedFile.exists() || (!legacyUnifiedFile.exists() && !hasLegacyConfig(folder))) {
                loadUnifiedConfig(getConfig(ADVANCED_CONFIG, advancedFile));
            } else if (legacyUnifiedFile.exists()) {
                loadUnifiedConfig(getExistingTomlConfig(legacyUnifiedFile));
            } else {
                loadLegacyConfigs(folder);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadUnifiedConfig(ConfigNode unified) {
        if (unified == null) {
            return;
        }

        Config.setParseResult(tableOrRoot(unified, "config"));

        String langFile = Config.LANG_FILE.getStringOrDefault("english");
        Message.setFallbackParseResult(loadFallbackMessages(langFile));
        Message.setParseResult(tableOrEnglish(unified, "messages", langFile));

        loadActions(unified.getTable("actions"));
        loadGenericChecks(unified.getTable("generic"));
        LunarConfig.load(unified.getTable("lunar"));
        ForgeConfig.load(unified.getTable("forge"));
        BedrockConfig.load(unified.getTable("bedrock"));
        ProbingConfig.load(unified.getTable("probing"));
    }

    private static void loadLegacyConfigs(File folder) throws IOException {
        ConfigNode defaults = getResourceConfig(ADVANCED_CONFIG);

        ConfigNode legacyConfig = getExistingTomlConfig(new File(folder, "config.toml"));
        Config.setParseResult(legacyConfig != null ? legacyConfig : tableOrRoot(defaults, "config"));

        String langFile = Config.LANG_FILE.getStringOrDefault("english");
        Message.setFallbackParseResult(loadFallbackMessages(langFile));
        ConfigNode legacyMessages = getExistingTomlConfig(
                new File(new File(folder, "languages"), langFile + ".toml"));
        Message.setParseResult(legacyMessages != null
                ? legacyMessages
                : tableOrEnglish(defaults, "messages", langFile));

        loadActions(tableOrDefault(getExistingTomlConfig(new File(folder, "actions.toml")), defaults, "actions"));
        loadGenericChecks(tableOrDefault(getExistingTomlConfig(new File(folder, "generic.toml")), defaults, "generic"));
        LunarConfig.load(tableOrDefault(getExistingTomlConfig(new File(folder, "lunar.toml")), defaults, "lunar"));
        ForgeConfig.load(tableOrDefault(getExistingTomlConfig(new File(folder, "forge.toml")), defaults, "forge"));
        BedrockConfig.load(tableOrDefault(getExistingTomlConfig(new File(folder, "bedrock.toml")), defaults, "bedrock"));
        ProbingConfig.load(tableOrDefault(getExistingTomlConfig(new File(folder, "probing.toml")), defaults, "probing"));
    }

    private static boolean hasLegacyConfig(File folder) {
        return new File(folder, "config.toml").exists()
                || new File(folder, "actions.toml").exists()
                || new File(folder, "generic.toml").exists()
                || new File(folder, "lunar.toml").exists()
                || new File(folder, "forge.toml").exists()
                || new File(folder, "bedrock.toml").exists()
                || new File(folder, "probing.toml").exists()
                || new File(new File(folder, "languages"), "english.toml").exists();
    }

    public static InputStream getResource(@NotNull String name) throws IOException {
        URL url = classLoader.getResource(name);
        if (url == null)
            return null;
        URLConnection connection = url.openConnection();
        connection.setUseCaches(false);
        return connection.getInputStream();
    }

    public static ConfigNode getConfig(@NotNull String name, File target) throws IOException {
        try {
            if (!target.exists()) {
                target.getParentFile().mkdirs();
                copyResource(name, target);
            }
            return parseYaml(new FileInputStream(target));
        } catch (IOException | RuntimeException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    private static ConfigNode getExistingTomlConfig(File target) {
        if (!target.exists()) {
            return null;
        }
        try {
            TomlParseResult result = Toml.parse(Path.of(target.toURI()));
            for (TomlParseError error : result.errors())
                throw new ParsingException(error.toString());
            return new TomlConfigNode(result);
        } catch (IOException | ParsingException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    private static ConfigNode loadFallbackMessages(String langFile) {
        if (langFile == null || langFile.isBlank()) {
            langFile = "english";
        }
        ConfigNode bundled = getResourceConfig(ADVANCED_CONFIG);
        if (bundled == null) {
            return null;
        }
        ConfigNode fallback = tableOrEnglish(bundled, "messages", langFile);
        if (fallback == null && !"english".equalsIgnoreCase(langFile)) {
            fallback = bundled.getTable("messages.english");
        }
        return fallback;
    }

    private static ConfigNode getResourceConfig(@NotNull String name) {
        try (InputStream input = getResource(name)) {
            if (input == null)
                return null;
            return parseYaml(input);
        } catch (IOException | RuntimeException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    private static void loadActions(ConfigNode result) {
        if (result == null) {
            return;
        }
        for (String key : result.keySet()) {
            ConfigNode table = result.getTable(key);
            assert table != null;
            Action action = new Action(key);
            String alert = table.getString("send_alert");
            if (alert != null)
                action.setAlert(alert);
            Long delayTicks = table.getLong("delay_ticks");
            if (delayTicks != null)
                action.setDelayTicks(delayTicks);
            if (table.isTable("commands")) {
                ConfigNode commandsTable = table.getTable("commands");
                assert commandsTable != null;
                if (commandsTable.isList("console"))
                    action.setConsoleCommands(toStringList(commandsTable.getList("console")));
                if (commandsTable.isList("player"))
                    action.setPlayerCommands(toStringList(commandsTable.getList("player")));
                if (commandsTable.isList("opped_player"))
                    action.setOppedPlayerCommands(toStringList(commandsTable.getList("opped_player")));
            }
            LovelyCheckRegistry.registerAction(action);
        }
    }

    private static void loadGenericChecks(ConfigNode result) {
        if (result == null) {
            return;
        }
        if (!Boolean.TRUE.equals(result.getBoolean("enabled")))
            return;

        for (String key : result.keySet()) {
            if (!result.isTable(key))
                continue;
            ConfigNode table = result.getTable(key);
            assert table != null;
            List<Action> actions = new ArrayList<>();
            for (String actionName : toStringList(table.getList("actions"))) {
                Action action = LovelyCheckRegistry.getAction(actionName);
                if (action != null)
                    actions.add(action);
            }
            List<String> channels = toStringList(table.getList("channels"));
            if (channels.isEmpty()) {
                continue;
            }
            LovelyCheckRegistry.registerCheck(new GenericCheck(key,
                    table.getString("name"),
                    channels,
                    table.getString("message_has"),
                    table.getString("message_not_has"),
                    table.getString("category"),
                    actions));
        }
    }

    private static ConfigNode tableOrRoot(ConfigNode root, String path) {
        if (root == null) {
            return null;
        }
        ConfigNode table = root.getTable(path);
        return table != null ? table : root;
    }

    private static ConfigNode tableOrDefault(ConfigNode table, ConfigNode root, String path) {
        return table != null ? table : tableOrNull(root, path);
    }

    private static ConfigNode tableOrNull(ConfigNode root, String path) {
        return root != null ? root.getTable(path) : null;
    }

    private static ConfigNode tableOrEnglish(ConfigNode root, String parent, String langFile) {
        if (root == null) {
            return null;
        }
        ConfigNode table = root.getTable(parent + "." + langFile);
        if (table == null && !"english".equalsIgnoreCase(langFile)) {
            table = root.getTable(parent + ".english");
        }
        return table;
    }

    private static void copyResource(String name, File target) throws IOException {
        try (InputStream resource = getResource(name)) {
            if (resource == null) {
                throw new FileNotFoundException("Bundled config resource not found: " + name);
            }
            java.nio.file.Files.copy(
                    resource,
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static ConfigNode parseYaml(InputStream input) throws IOException {
        try (input) {
            Object loaded = new Yaml().load(input);
            if (loaded == null) {
                return new MapConfigNode(Collections.emptyMap());
            }
            if (!(loaded instanceof Map<?, ?> map)) {
                throw new IOException("YAML root must be a map");
            }
            return new MapConfigNode(map);
        }
    }

    private static List<String> toStringList(List<?> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        List<String> strings = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof String string) {
                strings.add(string);
            }
        }
        return strings;
    }

}
