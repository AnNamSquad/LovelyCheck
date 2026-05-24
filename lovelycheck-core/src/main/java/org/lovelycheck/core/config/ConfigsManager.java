package org.lovelycheck.core.config;

import org.lovelycheck.core.LovelyCheckRegistry;
import org.lovelycheck.core.exceptions.ParsingException;
import org.lovelycheck.core.forge.ForgeConfig;
import org.lovelycheck.core.probing.ProbingConfig;
import org.jetbrains.annotations.NotNull;
import org.tomlj.Toml;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ConfigsManager {

    private static final ClassLoader classLoader = ConfigsManager.class.getClassLoader();
    private static final String UNIFIED_CONFIG = "lovelycheck.toml";

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
            File unifiedFile = new File(folder, UNIFIED_CONFIG);
            if (unifiedFile.exists() || !hasLegacyConfig(folder)) {
                loadUnifiedConfig(getConfig(UNIFIED_CONFIG, unifiedFile));
            } else {
                loadLegacyConfigs(folder);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadUnifiedConfig(TomlParseResult unified) {
        if (unified == null) {
            return;
        }

        TomlTable coreConfig = tableOrRoot(unified, "config");
        Config.setParseResult(coreConfig);

        String langFile = Config.LANG_FILE.getStringOrDefault("english");
        Message.setFallbackParseResult(loadFallbackMessages(langFile));
        TomlTable messages = tableOrEnglish(unified, "messages", langFile);
        Message.setParseResult(messages);

        loadActions(unified.getTable("actions"));
        loadGenericChecks(unified.getTable("generic"));
        LunarConfig.load(unified.getTable("lunar"));
        ForgeConfig.load(unified.getTable("forge"));
        BedrockConfig.load(unified.getTable("bedrock"));
        ProbingConfig.load(unified.getTable("probing"));
    }

    private static void loadLegacyConfigs(File folder) throws IOException {
        TomlParseResult defaults = getResourceConfig(UNIFIED_CONFIG);

        TomlParseResult legacyConfig = getExistingConfig(new File(folder, "config.toml"));
        Config.setParseResult(legacyConfig != null ? legacyConfig : tableOrNull(defaults, "config"));

        String langFile = Config.LANG_FILE.getStringOrDefault("english");
        Message.setFallbackParseResult(loadFallbackMessages(langFile));
        TomlParseResult legacyMessages = getExistingConfig(
                new File(new File(folder, "languages"), langFile + ".toml"));
        Message.setParseResult(legacyMessages != null
                ? legacyMessages
                : tableOrEnglish(defaults, "messages", langFile));

        loadActions(tableOrDefault(getExistingConfig(new File(folder, "actions.toml")), defaults, "actions"));
        loadGenericChecks(tableOrDefault(getExistingConfig(new File(folder, "generic.toml")), defaults, "generic"));
        LunarConfig.load(tableOrDefault(getExistingConfig(new File(folder, "lunar.toml")), defaults, "lunar"));
        ForgeConfig.load(tableOrDefault(getExistingConfig(new File(folder, "forge.toml")), defaults, "forge"));
        BedrockConfig.load(tableOrDefault(getExistingConfig(new File(folder, "bedrock.toml")), defaults, "bedrock"));
        ProbingConfig.load(tableOrDefault(getExistingConfig(new File(folder, "probing.toml")), defaults, "probing"));
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

    public static TomlParseResult getConfig(@NotNull String name, File target) throws IOException {
        try {
            if (!target.exists()) {
                target.getParentFile().mkdirs();
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
            TomlParseResult result = Toml.parse(Path.of(target.toURI()));
            for (TomlParseError error : result.errors())
                throw new ParsingException(error.toString());
            return result;
        } catch (IOException | ParsingException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    private static TomlParseResult getExistingConfig(File target) {
        if (!target.exists()) {
            return null;
        }
        try {
            TomlParseResult result = Toml.parse(Path.of(target.toURI()));
            for (TomlParseError error : result.errors())
                throw new ParsingException(error.toString());
            return result;
        } catch (IOException | ParsingException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    private static TomlTable loadFallbackMessages(String langFile) {
        if (langFile == null || langFile.isBlank()) {
            langFile = "english";
        }
        TomlParseResult bundled = getResourceConfig(UNIFIED_CONFIG);
        if (bundled == null) {
            return null;
        }
        TomlTable fallback = tableOrEnglish(bundled, "messages", langFile);
        if (fallback == null && !"english".equalsIgnoreCase(langFile)) {
            fallback = bundled.getTable("messages.english");
        }
        return fallback;
    }

    private static TomlParseResult getResourceConfig(@NotNull String name) {
        try (InputStream input = getResource(name)) {
            if (input == null)
                return null;
            TomlParseResult result = Toml.parse(input);
            for (TomlParseError error : result.errors())
                throw new ParsingException(error.toString());
            return result;
        } catch (IOException | ParsingException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadActions(TomlTable result) {
        if (result == null) {
            return;
        }
        for (String key : result.keySet()) {
            TomlTable table = result.getTable(key);
            assert table != null;
            Action action = new Action(key);
            if (table.isString("send_alert"))
                action.setAlert(table.getString("send_alert"));
            if (table.isLong("delay_ticks"))
                action.setDelayTicks(table.getLong("delay_ticks"));
            if (table.isTable("commands")) {
                TomlTable commandsTable = table.getTable("commands");
                assert commandsTable != null;
                if (commandsTable.isArray("console"))
                    action.setConsoleCommands(
                            (List<String>) (Object) Objects.requireNonNull(commandsTable.getArray("console")).toList());
                if (commandsTable.isArray("player"))
                    action.setPlayerCommands(
                            (List<String>) (Object) Objects.requireNonNull(commandsTable.getArray("player")).toList());
                if (commandsTable.isArray("opped_player"))
                    action.setOppedPlayerCommands((List<String>) (Object) Objects
                            .requireNonNull(commandsTable.getArray("opped_player")).toList());
            }
            LovelyCheckRegistry.registerAction(action);
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadGenericChecks(TomlTable result) {
        if (result == null) {
            return;
        }
        if (!Boolean.TRUE.equals(result.getBoolean("enabled")))
            return;

        for (String key : result.keySet()) {
            if (!result.isTable(key))
                continue;
            TomlTable table = result.getTable(key);
            assert table != null;
            List<Action> actions = new ArrayList<>();
            for (Object actionName : Objects.requireNonNull(
                    Objects.requireNonNull(table.getArray("actions")).toList())) {
                Action action = LovelyCheckRegistry.getAction((String) actionName);
                if (action != null)
                    actions.add(action);
            }
            LovelyCheckRegistry.registerCheck(new GenericCheck(key,
                    table.getString("name"),
                    (List<String>) (Object) table.getArray("channels").toList(),
                    table.getString("message_has"),
                    table.getString("message_not_has"),
                    table.getString("category"),
                    actions));
        }
    }

    private static TomlTable tableOrRoot(TomlTable root, String path) {
        TomlTable table = root.getTable(path);
        return table != null ? table : root;
    }

    private static TomlTable tableOrDefault(TomlTable table, TomlTable root, String path) {
        return table != null ? table : tableOrNull(root, path);
    }

    private static TomlTable tableOrNull(TomlTable root, String path) {
        return root != null ? root.getTable(path) : null;
    }

    private static TomlTable tableOrEnglish(TomlTable root, String parent, String langFile) {
        if (root == null) {
            return null;
        }
        TomlTable table = root.getTable(parent + "." + langFile);
        if (table == null && !"english".equalsIgnoreCase(langFile)) {
            table = root.getTable(parent + ".english");
        }
        return table;
    }

}
