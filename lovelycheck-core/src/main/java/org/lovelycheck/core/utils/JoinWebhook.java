package org.lovelycheck.core.utils;

import org.lovelycheck.core.LovelyCheckPlayer;
import org.lovelycheck.core.LovelyCheckRegistry;
import org.lovelycheck.core.config.Config;
import org.lovelycheck.core.forge.ForgeClientType;
import org.lovelycheck.core.forge.ForgeModInfo;
import org.lovelycheck.core.lunar.LunarModInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JoinWebhook {

    public static void send(String playerName, UUID playerUuid) {
        if (!Config.JOIN_WEBHOOK_ENABLED.toBool()) {
            return;
        }

        // Look up the player once to avoid redundant map lookups across placeholder calls.
        // Use getPlayerIfPresent to avoid recreating a removed player record — the webhook
        // runs on a delay, so the player may have disconnected by now.
        LovelyCheckPlayer playerData = playerUuid != null ? LovelyCheckRegistry.getPlayerIfPresent(playerUuid) : null;

        String url = Config.JOIN_WEBHOOK_URL.getStringOrDefault("");
        String content = replacePlaceholders(Config.JOIN_WEBHOOK_CONTENT.getStringOrDefault(""), playerName, playerUuid, playerData);
        String title = replacePlaceholders(Config.JOIN_WEBHOOK_EMBED_TITLE.getStringOrDefault("Player Joined"), playerName, playerUuid, playerData);
        String description = replacePlaceholders(Config.JOIN_WEBHOOK_EMBED_DESCRIPTION.getStringOrDefault("Player {player} has joined the server."), playerName, playerUuid, playerData);
        int color = (int) Config.JOIN_WEBHOOK_EMBED_COLOR.toLong(65280);
        String footer = replacePlaceholders(Config.JOIN_WEBHOOK_EMBED_FOOTER.getStringOrDefault("lovelycheck"), playerName, playerUuid, playerData);

        DiscordWebhook.send(url, content, title, description, color, footer);
    }

    private static String replacePlaceholders(String input, String playerName, UUID playerUuid, LovelyCheckPlayer playerData) {
        if (input == null) {
            return "";
        }
        String safeName = playerName != null ? playerName : "Unknown";
        String safeUuid = playerUuid != null ? playerUuid.toString() : "Unknown";
        String client = getClientName(playerData);
        String modlist = getModList(playerData);
        return input.replace("{player}", safeName)
                .replace("{uuid}", safeUuid)
                .replace("{client}", client)
                .replace("{modlist}", modlist);
    }

    private static String getClientName(LovelyCheckPlayer playerData) {
        if (playerData == null) {
            return "Unknown";
        }
        if (playerData.isBedrockDetected()) {
            return "Bedrock";
        }
        if (playerData.hasLunarModsData()) {
            return "Lunar Client";
        }

        ForgeClientType forgeClientType = playerData.getForgeClientType();
        if (forgeClientType != null) {
            return forgeClientType.getDisplayName();
        }

        return "Unknown";
    }

    private static String getModList(LovelyCheckPlayer playerData) {
        if (playerData == null) {
            return "Unknown";
        }

        List<String> mods = new ArrayList<>();
        for (LunarModInfo mod : playerData.getLunarMods()) {
            String displayName = mod.getDisplayName() != null && !mod.getDisplayName().isBlank()
                    ? mod.getDisplayName()
                    : mod.getId();
            if (displayName == null || displayName.isBlank()) {
                continue;
            }
            if (mod.getVersion() != null && !mod.getVersion().isBlank()) {
                mods.add(displayName + " (" + mod.getVersion() + ")");
            } else {
                mods.add(displayName);
            }
        }
        for (ForgeModInfo mod : playerData.getForgeMods()) {
            mods.add(mod.toString());
        }

        if (!mods.isEmpty()) {
            return String.join(", ", mods);
        }

        if (playerData.hasLunarModsData() || playerData.hasForgeModsData()) {
            return "None";
        }

        return "Unknown";
    }
}
