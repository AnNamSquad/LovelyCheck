package org.lovelycheck.spigot.checks.managers;

import org.lovelycheck.spigot.checks.*;
import org.lovelycheck.spigot.checks.utils.SignUtil;
import org.lovelycheck.spigot.checks.utils.WebhookUtil;
import org.lovelycheck.spigot.LovelyCheckPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.lovelycheck.core.LovelyCheckPlayer;
import org.lovelycheck.core.LovelyCheckRegistry;
import org.lovelycheck.core.config.GenericCheck;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CheckManager {

    private static final String CTRL_KEYBIND = "key.forward";
    private static final int SIGN_LINES = 4;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TRANSLATION_MASKING_RESULT_ID = "translation-masking-bypass";

    private static final Map<String, String> LOCALE_MAP = Map.ofEntries(
            Map.entry("space", "en"),
            Map.entry("espace", "fr"),
            Map.entry("leertaste", "de"),
            Map.entry("espacio", "es"),
            Map.entry("espaço", "pt"),
            Map.entry("spazio", "it"),
            Map.entry("пробел", "ru"),
            Map.entry("spacja", "pl"),
            Map.entry("boşluk", "tr"),
            Map.entry("ara tuşu", "tr"),
            Map.entry("dấu cách", "vi"),
            Map.entry("スペース", "ja"),
            Map.entry("스페이스", "ko"),
            Map.entry("空格", "zh"),
            Map.entry("mezerník", "cs"),
            Map.entry("mellanslag", "sv"),
            Map.entry("mellomrom", "no"),
            Map.entry("spatie", "nl"),
            Map.entry("mezera", "cs"));

    private final LovelyCheckPlugin plugin;
    private final Map<UUID, CheckPlayerData> activeChecks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAutoCheck = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> latestDetectedHacks = new ConcurrentHashMap<>();

    public CheckManager(LovelyCheckPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isChecking(UUID uuid) {
        return activeChecks.containsKey(uuid);
    }

    public List<String> getLatestDetectedHacks(UUID uuid) {
        return latestDetectedHacks.getOrDefault(uuid, List.of());
    }

    public boolean hasLatestDetectedHacks(UUID uuid) {
        return !getLatestDetectedHacks(uuid).isEmpty();
    }

    public boolean canAutoCheck(UUID uuid) {
        long cooldownMs = plugin.getConfigManager().getFlagCooldownHours() * 3_600_000L;
        return System.currentTimeMillis() - lastAutoCheck.getOrDefault(uuid, 0L) >= cooldownMs;
    }

    public void startCheck(Player target, Player initiator,
            List<HackDefinition> hacks, boolean autoCheck, String reason) {
        UUID uuid = target.getUniqueId();

        if (activeChecks.containsKey(uuid)) {
            if (initiator != null)
                initiator.sendMessage(plugin.getMessageManager().get("already-checking",
                        Map.of("player", target.getName())));
            return;
        }

        if (target.isOp()
                || target.hasPermission("lovelycheck.bypass")
                || target.hasPermission("lovelychecker.bypass")
                || target.hasPermission("zenithdetector.bypass")) {
            if (initiator != null)
                initiator.sendMessage(MM.deserialize(plugin.getConfigManager().getPrefix()
                        + "<red>Player " + target.getName() + " bypasses anticheat checks."));
            return;
        }

        if (isBedrockPlayer(target)) {
            Component msg = plugin.getMessageManager().get("bedrock-skip",
                    Map.of("player", target.getName()));
            if (initiator != null)
                initiator.sendMessage(msg);
            else
                plugin.getMessageManager().broadcastAlerts(msg);
            return;
        }

        if (autoCheck)
            lastAutoCheck.put(uuid, System.currentTimeMillis());

        List<List<HackDefinition>> batches = buildBatches(hacks);
        if (batches.isEmpty())
            return;

        CheckPlayerData data = new CheckPlayerData(uuid,
                initiator != null ? initiator.getUniqueId() : null,
                batches, autoCheck, reason);
        activeChecks.put(uuid, data);

        if (initiator != null && !plugin.getConfigManager().isSilentCheck())
            initiator.sendMessage(plugin.getMessageManager().get("check-started",
                    Map.of("player", target.getName())));

        processBatch(target, data);
    }

    public void startLocaleProbe(Player target, Player initiator) {
        UUID uuid = target.getUniqueId();

        if (activeChecks.containsKey(uuid)) {
            if (initiator != null)
                initiator.sendMessage(plugin.getMessageManager().get("already-checking",
                        Map.of("player", target.getName())));
            return;
        }

        if (target.isOp()
                || target.hasPermission("lovelycheck.bypass")
                || target.hasPermission("lovelychecker.bypass")
                || target.hasPermission("zenithdetector.bypass")) {
            if (initiator != null)
                initiator.sendMessage(MM.deserialize(plugin.getConfigManager().getPrefix()
                        + "<red>Player " + target.getName() + " bypasses anticheat checks."));
            return;
        }

        if (isBedrockPlayer(target)) {
            Component msg = plugin.getMessageManager().get("bedrock-skip",
                    Map.of("player", target.getName()));
            if (initiator != null)
                initiator.sendMessage(msg);
            else
                plugin.getMessageManager().broadcastAlerts(msg);
            return;
        }

        List<List<HackDefinition>> batches = List.of(List.of(
                new HackDefinition("locale-probe", "Locale Probe", "key.jump", DetectionMode.KEYBIND)));

        CheckPlayerData data = new CheckPlayerData(uuid,
                initiator != null ? initiator.getUniqueId() : null,
                batches, false, "Locale Probe Command");
        data.setLocaleProbe(true);
        activeChecks.put(uuid, data);

        if (initiator != null)
            initiator.sendMessage(MM.deserialize(plugin.getConfigManager().getPrefix()
                    + "<white>Starting locale probe on <yellow>" + target.getName() + "<white>..."));

        processBatch(target, data);
    }

    private List<List<HackDefinition>> buildBatches(List<HackDefinition> hacks) {
        List<List<HackDefinition>> batches = new ArrayList<>();
        if (hacks.isEmpty())
            return batches;

        int firstBatchSize = Math.min(getMaxHacksForBatch(0), hacks.size());
        batches.add(new ArrayList<>(hacks.subList(0, firstBatchSize)));

        for (int i = firstBatchSize; i < hacks.size(); i += getMaxHacksForBatch(1)) {
            batches.add(new ArrayList<>(hacks.subList(i, Math.min(i + getMaxHacksForBatch(1), hacks.size()))));
        }
        return batches;
    }

    private int getMaxHacksForBatch(int batchIndex) {
        return shouldUseControlLine(batchIndex) ? SIGN_LINES - 1 : SIGN_LINES;
    }

    private boolean shouldUseControlLine(int batchIndex) {
        return batchIndex == 0;
    }

    private boolean isBedrockPlayer(Player target) {
        if (!plugin.getConfigManager().isBedrockEnabled()) {
            return false;
        }

        if (plugin.getClientDataManager().isBedrock(target.getUniqueId())) {
            return true;
        }

        LovelyCheckPlayer playerData = LovelyCheckRegistry.getPlayer(target.getUniqueId());
        if (playerData.isBedrockDetected()) {
            return true;
        }

        for (String prefix : plugin.getConfigManager().getBedrockPrefixes()) {
            if (!prefix.isEmpty() && target.getName().startsWith(prefix)) {
                playerData.markBedrockDetected("Prefix");
                plugin.getClientDataManager().setClientType(target.getUniqueId(), ClientType.BEDROCK);
                return true;
            }
        }

        return false;
    }

    private void processBatch(Player target, CheckPlayerData data) {
        UUID uuid = target.getUniqueId();
        List<HackDefinition> batch = data.getCurrentBatchHacks();

        restoreCurrentSign(data);

        if (plugin.getConfigManager().isFakeSignPacketsEnabled()
                && !data.isForceRealSignForCurrentBatch()
                && processFakePacketBatch(target, data, batch, uuid)) {
            return;
        }
        data.setForceRealSignForCurrentBatch(false);
        data.setCurrentBatchFakePacket(false);

        Location signLoc = SignUtil.findAirBlock(target);
        if (signLoc == null) {
            finishCheck(uuid);
            return;
        }

        Block block = signLoc.getBlock();
        BlockState originalState = block.getState();

        block.setType(Material.OAK_SIGN, false);
        BlockState freshState = block.getState();
        if (!(freshState instanceof Sign sign)) {
            originalState.update(true, false);
            finishCheck(uuid);
            return;
        }

        var front = sign.getSide(Side.FRONT);
        if (data.isLocaleProbe()) {
            front.line(0, Component.keybind("key.jump"));
            front.line(1, Component.empty());
            front.line(2, Component.empty());
            front.line(3, Component.keybind(CTRL_KEYBIND));
        } else {
            int maxHacks = getMaxHacksForBatch(data.getCurrentBatch());
            for (int i = 0; i < SIGN_LINES; i++) {
                if (i < maxHacks) {
                    front.line(i, i < batch.size() ? buildComponent(batch.get(i)) : Component.empty());
                } else if (i == SIGN_LINES - 1 && shouldUseControlLine(data.getCurrentBatch())) {
                    front.line(i, Component.keybind(CTRL_KEYBIND));
                } else {
                    front.line(i, Component.empty());
                }
            }
        }
        sign.update(true, false);

        data.setSignLocation(signLoc);
        data.setOriginalState(originalState);

        SignUtil.setAllowedEditor(signLoc, uuid, plugin);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!activeChecks.containsKey(uuid))
                return;
            SignUtil.sendBlockEntityPacket(target, signLoc, plugin);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!activeChecks.containsKey(uuid))
                    return;
                SignUtil.sendOpenSignPacket(target, signLoc, plugin);
                target.sendBlockChange(signLoc, originalState.getBlockData());
            }, 1L);
        });

        data.setSignTimeoutTask(scheduleBatchTimeout(uuid, batch));
    }

    private boolean processFakePacketBatch(Player target, CheckPlayerData data,
            List<HackDefinition> batch, UUID uuid) {
        Location signLoc = SignUtil.findHiddenFakeSignLocation(target, data.getCurrentBatch());
        if (signLoc == null) {
            return false;
        }

        data.setSignLocation(signLoc);
        data.setOriginalState(null);
        boolean includeControlLine = (shouldUseControlLine(data.getCurrentBatch()) || data.isLocaleProbe());
        if (!plugin.openFakeSignCheck(target, signLoc, batch, includeControlLine)) {
            data.setSignLocation(null);
            data.setCurrentBatchFakePacket(false);
            return false;
        }

        data.setCurrentBatchFakePacket(true);
        data.setSignTimeoutTask(scheduleBatchTimeout(uuid, batch));
        return true;
    }

    private BukkitTask scheduleBatchTimeout(UUID uuid, List<HackDefinition> batch) {
        long ticks = plugin.getConfigManager().getTimeoutTicks();
        CheckPlayerData d = activeChecks.get(uuid);
        if (d != null && d.getCurrentBatch() == 0) {
            int configShieldTicks = plugin.getConfigManager().getShieldTimeoutTicks();
            int buffer = plugin.getConfigManager().getShieldTimeoutBufferTicks();
            Player targetPlayer = Bukkit.getPlayer(uuid);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                long pingTicks = targetPlayer.getPing() / 50;
                ticks = Math.max(configShieldTicks, pingTicks + buffer);
            } else {
                ticks = configShieldTicks;
            }
        }
        final long timeoutTicks = ticks;
        return Bukkit.getScheduler().runTaskLater(plugin, () -> {
            CheckPlayerData data = activeChecks.get(uuid);
            if (data == null)
                return;
            if (data.isCurrentBatchFakePacket()) {
                restoreCurrentSign(data);
                retryCurrentBatchWithRealSign(uuid, data, "fake sign timed out");
                return;
            }
            restoreCurrentSign(data);

            if (data.getCurrentBatch() == 0) {
                if (data.isLocaleProbe()) {
                    finishLocaleProbe(uuid, "Unknown (Timeout)");
                    return;
                }

                plugin.getLogger().info("[lovelycheck] Target " + uuid
                        + " did not respond to the first probe. Client is PROTECTED (translation shield). Skipping remaining checks.");

                List<HackDefinition> allHacks = data.getBatches().stream().flatMap(List::stream).toList();
                for (HackDefinition h : allHacks) {
                    data.getResults().put(h.getId(), HackResult.PROTECTED);
                }
                data.setCurrentBatch(data.getBatches().size());
                scheduleNextOrFinish(uuid);
            } else {
                for (HackDefinition h : batch)
                    data.getResults().put(h.getId(), HackResult.PROTECTED);
                data.incrementBatch();
                scheduleNextOrFinish(uuid);
            }
        }, timeoutTicks);
    }

    private Component buildComponent(HackDefinition hack) {
        return switch (hack.getMode()) {
            case METEOR, TRANSLATE -> Component.translatable(hack.getKey(), hack.getFallback());
            case KEYBIND -> Component.keybind(hack.getKey());
        };
    }

    private String detectLocale(String line0) {
        if (line0 == null || line0.isBlank())
            return "en (English)";
        String clean = line0.trim().toLowerCase(Locale.ROOT);

        for (Map.Entry<String, String> entry : LOCALE_MAP.entrySet()) {
            if (clean.contains(entry.getKey())) {
                String code = entry.getValue();
                return switch (code) {
                    case "en" -> "en (English)";
                    case "fr" -> "fr (French)";
                    case "de" -> "de (German)";
                    case "es" -> "es (Spanish)";
                    case "pt" -> "pt (Portuguese)";
                    case "it" -> "it (Italian)";
                    case "ru" -> "ru (Russian)";
                    case "pl" -> "pl (Polish)";
                    case "tr" -> "tr (Turkish)";
                    case "vi" -> "vi (Vietnamese)";
                    case "ja" -> "ja (Japanese)";
                    case "ko" -> "ko (Korean)";
                    case "zh" -> "zh (Chinese)";
                    case "cs" -> "cs (Czech)";
                    case "sv" -> "sv (Swedish)";
                    case "no" -> "no (Norwegian)";
                    case "nl" -> "nl (Dutch)";
                    default -> code;
                };
            }
        }
        return "Unknown (" + line0 + ")";
    }

    private void finishLocaleProbe(UUID uuid, String detectedLocale) {
        CheckPlayerData data = activeChecks.remove(uuid);
        if (data == null)
            return;

        Player targetPlayer = Bukkit.getPlayer(uuid);
        if (targetPlayer == null)
            return;

        String targetName = targetPlayer.getName();
        String checkerName = data.getInitiatorUUID() != null
                ? Optional.ofNullable(Bukkit.getPlayer(data.getInitiatorUUID()))
                        .map(Player::getName).orElse("Console")
                : "AutoCheck";

        Component localeMsg = MM.deserialize(plugin.getConfigManager().getPrefix())
                .append(Component.text("Detected Locale: ", NamedTextColor.GRAY))
                .append(Component.text(detectedLocale, NamedTextColor.GREEN))
                .append(Component.text(" for ", NamedTextColor.GRAY))
                .append(Component.text(targetName, NamedTextColor.WHITE));

        Player initiator = data.getInitiatorUUID() != null ? Bukkit.getPlayer(data.getInitiatorUUID()) : null;
        if (initiator != null) {
            initiator.sendMessage(localeMsg);
        } else {
            plugin.getMessageManager().broadcastAlerts(localeMsg);
        }

        plugin.getLogger().info("[lovelycheck] Locale for " + targetName + ": " + detectedLocale);

        ConfigManager cfg = plugin.getConfigManager();
        if (cfg.isDiscordEnabled()) {
            String webhookUrl = cfg.getLocaleWebhookUrl();
            if (webhookUrl == null || webhookUrl.isBlank() || webhookUrl.contains("CHANGE_ME")) {
                webhookUrl = cfg.getWebhookUrl();
            }
            int color = cfg.getLocaleEmbedColor();
            WebhookUtil.sendLocaleReport(webhookUrl, color, targetName, checkerName, detectedLocale);
        }
    }

    public void handleBatchResponse(Player target, String[] lines) {
        UUID uuid = target.getUniqueId();
        CheckPlayerData data = activeChecks.get(uuid);
        if (data == null)
            return;

        if (data.getSignTimeoutTask() != null)
            data.getSignTimeoutTask().cancel();
        boolean fakePacketResponse = data.isCurrentBatchFakePacket();
        restoreCurrentSign(data);

        List<HackDefinition> batch = data.getCurrentBatchHacks();

        if (data.isLocaleProbe()) {
            data.setCurrentBatchFakePacket(false);
            data.setForceRealSignForCurrentBatch(false);
            String line0 = lines.length > 0 ? lines[0].strip() : "";
            String detectedLocale = detectLocale(line0);
            finishLocaleProbe(uuid, detectedLocale);
            return;
        }

        // Only truly-unsafe responses (raw JSON components) warrant a real-sign retry.
        // Key-echo (OpSec returning the raw key name) is a valid PROTECTED response.
        if (fakePacketResponse && hasUnsafeFakePacketResponse(batch, lines)) {
            retryCurrentBatchWithRealSign(uuid, data, "raw component response");
            return;
        }
        data.setCurrentBatchFakePacket(false);
        data.setForceRealSignForCurrentBatch(false);

        boolean controlLineExpected = shouldUseControlLine(data.getCurrentBatch());
        String ctrlResp = controlLineExpected && lines.length > 3 ? lines[3].strip() : "";

        boolean exploitPreventer = controlLineExpected && ctrlResp.equalsIgnoreCase(CTRL_KEYBIND);

        if (plugin.getConfigManager().isSilentCheck()) {
            plugin.getLogger().fine("[lovelycheck] Batch " + data.getCurrentBatch()
                    + " from " + target.getName() + " CTRL='" + ctrlResp + "'"
                    + (exploitPreventer ? " [ExploitPreventer]" : ""));
        } else {
            plugin.getLogger().info("[lovelycheck] Batch " + data.getCurrentBatch()
                    + " from " + target.getName() + " CTRL='" + ctrlResp + "'"
                    + (exploitPreventer ? " [ExploitPreventer DETECTED]" : ""));
        }

        if (exploitPreventer) {
            Component epMsg = MM.deserialize(plugin.getConfigManager().getPrefix())
                    .append(Component.text("ExploitPreventer-style sign protection detected for ",
                            NamedTextColor.YELLOW))
                    .append(Component.text(target.getName(), NamedTextColor.WHITE))
                    .append(Component.text(".", NamedTextColor.YELLOW));
            if (!plugin.getConfigManager().isSilentCheck()) {
                plugin.getMessageManager().broadcastAlerts(epMsg);
                notifyInitiator(data, epMsg);
            }
        }

        for (int i = 0; i < batch.size(); i++) {
            HackDefinition hack = batch.get(i);
            String resp = i < lines.length ? lines[i].strip() : "";
            HackResult result = evaluateResponse(hack, resp, exploitPreventer);
            data.getResults().put(hack.getId(), result);
            // Only log non-trivial results to reduce spam
            if (result != HackResult.NOT_DETECTED || !plugin.getConfigManager().isSilentCheck()) {
                plugin.getLogger().info("[lovelycheck] " + hack.getDisplayName()
                        + " -> " + result + " (resp='" + resp + "')");
            }
        }

        data.incrementBatch();
        scheduleNextOrFinish(uuid);
    }

    private void evaluateTranslationMasking(UUID uuid, String targetName, CheckPlayerData data) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isDetectTranslationMaskingEnabled()
                || data.isTranslationMaskingDetected()) {
            return;
        }

        MaskingStats stats = collectMaskingStats(uuid, data);
        if (stats.notDetectedEvidenceMatches() < cfg.getTranslationMaskingMinimumChecks()) {
            return;
        }

        data.setTranslationMaskingDetected(true);
        plugin.getLogger().warning("[lovelycheck] Translation masking detected for "
                + targetName + ": " + stats.notDetectedEvidenceMatches()
                + " configured probes returned vanilla-safe values despite matching connection evidence.");
    }

    private MaskingStats collectMaskingStats(UUID uuid, CheckPlayerData data) {
        LovelyCheckPlayer playerData = LovelyCheckRegistry.getPlayerIfPresent(uuid);
        if (playerData == null || playerData.isBedrockDetected()) {
            return new MaskingStats(0);
        }

        int notDetectedEvidenceMatches = 0;
        for (List<HackDefinition> batch : data.getBatches()) {
            for (HackDefinition hack : batch) {
                HackResult result = data.getResults().get(hack.getId());
                if (result != HackResult.NOT_DETECTED || !hasMatchingConnectionEvidence(playerData, hack)) {
                    continue;
                }
                data.addTranslationMaskedHackId(hack.getId());
                notDetectedEvidenceMatches++;
            }
        }

        return new MaskingStats(notDetectedEvidenceMatches);
    }

    private boolean hasMatchingConnectionEvidence(LovelyCheckPlayer playerData, HackDefinition hack) {
        for (String checkId : playerData.getGenericChecks()) {
            if (hack.matchesModId(checkId)) {
                return true;
            }
            GenericCheck check = LovelyCheckRegistry.getCheck(checkId);
            if (check != null && hack.matchesModId(check.getName())) {
                return true;
            }
        }
        for (var mod : playerData.getForgeMods()) {
            if (hack.matchesModId(mod.getModId())) {
                return true;
            }
        }
        for (var mod : playerData.getLunarMods()) {
            if (hack.matchesModId(mod.getId()) || hack.matchesModId(mod.getDisplayName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true only when the client sent back a raw JSON component string,
     * which means the fake-packet NBT was never rendered by the client
     * (e.g. a proxy / reflection hack returning serialized component JSON).
     *
     * Key-echo responses (where the response equals the probe key verbatim,
     * e.g. OpSec returning "key.meteor-client.open-gui") are intentionally
     * NOT considered unsafe — they are classifiable as PROTECTED by evaluateResponse.
     */
    private boolean hasUnsafeFakePacketResponse(List<HackDefinition> batch, String[] lines) {
        for (int i = 0; i < batch.size() && i < lines.length; i++) {
            String response = lines[i] != null ? lines[i].strip() : "";
            if (looksLikeRawComponent(response)) {
                return true;
            }
        }
        String control = lines.length > 3 && lines[3] != null ? lines[3].strip() : "";
        return looksLikeRawComponent(control);
    }

    private boolean looksLikeRawComponent(String response) {
        if (response.isEmpty()) {
            return false;
        }
        String lower = response.toLowerCase(Locale.ROOT);
        return (lower.startsWith("{") || lower.startsWith("["))
                && (lower.contains("\"translate\"")
                        || lower.contains("\"keybind\"")
                        || lower.contains("\"fallback\"")
                        || lower.contains("\"text\""));
    }

    private void retryCurrentBatchWithRealSign(UUID uuid, CheckPlayerData data, String reason) {
        data.setCurrentBatchFakePacket(false);
        data.setForceRealSignForCurrentBatch(true);
        plugin.getLogger().info("[lovelycheck] Fake sign-check packet flow for "
                + uuid + " returned an unsafe result (" + reason + "); retrying with real-sign fallback.");
        Bukkit.getScheduler().runTask(plugin, () -> {
            CheckPlayerData current = activeChecks.get(uuid);
            Player target = Bukkit.getPlayer(uuid);
            if (current != data) {
                return;
            }
            if (target == null || !target.isOnline()) {
                finishCheck(uuid);
                return;
            }
            processBatch(target, data);
        });
    }

    private void scheduleNextOrFinish(UUID uuid) {
        CheckPlayerData data = activeChecks.get(uuid);
        if (data == null)
            return;
        if (data.hasMoreBatches()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player t = Bukkit.getPlayer(uuid);
                if (t != null && t.isOnline())
                    processBatch(t, data);
                else
                    finishCheck(uuid);
            }, plugin.getConfigManager().getBetweenSignTicks());
        } else {
            finishCheck(uuid);
        }
    }

    private HackResult evaluateResponse(HackDefinition hack, String resp, boolean exploitPreventer) {
        if (resp.isEmpty())
            return HackResult.NOT_DETECTED;
        String normalizedResp = resp.toLowerCase(Locale.ROOT);
        String normalizedFallback = hack.getFallback().toLowerCase(Locale.ROOT);

        return switch (hack.getMode()) {
            case METEOR -> {
                if (resp.equalsIgnoreCase(hack.getKey()))
                    yield HackResult.DETECTED;
                if (normalizedResp.startsWith(normalizedFallback))
                    yield HackResult.NOT_DETECTED;
                yield HackResult.DETECTED;
            }
            case TRANSLATE -> {
                if (normalizedResp.startsWith(normalizedFallback))
                    yield HackResult.NOT_DETECTED;
                if (resp.equalsIgnoreCase(hack.getKey()))
                    yield HackResult.NOT_DETECTED;
                yield HackResult.DETECTED;
            }
            case KEYBIND -> {
                // ExploitPreventer echoes the raw key name back — mark NOT_DETECTED
                if (exploitPreventer && resp.equalsIgnoreCase(hack.getKey()))
                    yield HackResult.NOT_DETECTED;
                // OpSec masks mod keybinds by echoing the raw key name back.
                // A vanilla client with the mod not installed also returns the raw key.
                // Both cases are NOT_DETECTED unless matching connection evidence is present.
                if (resp.equalsIgnoreCase(hack.getKey()))
                    yield HackResult.NOT_DETECTED;
                // Any other non-empty response means the keybind resolved → mod present
                yield HackResult.DETECTED;
            }
        };
    }

    private void finishCheck(UUID uuid) {
        CheckPlayerData data = activeChecks.get(uuid);
        if (data == null)
            return;

        Player targetPlayer = Bukkit.getPlayer(uuid);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            activeChecks.remove(uuid);
            return;
        }

        ConfigManager cfg = plugin.getConfigManager();
        boolean hasDetections = data.getResults().values().stream().anyMatch(r -> r == HackResult.DETECTED);

        if (!data.isConfirmationScan() && cfg.isConfirmationEnabled() && hasDetections) {
            List<HackDefinition> flaggedHacks = new ArrayList<>();
            for (Map.Entry<String, HackResult> entry : data.getResults().entrySet()) {
                if (entry.getValue() == HackResult.DETECTED) {
                    HackDefinition hack = cfg.getHack(entry.getKey());
                    if (hack != null) {
                        flaggedHacks.add(hack);
                    }
                }
            }

            if (!flaggedHacks.isEmpty()) {
                plugin.getLogger().info("[lovelycheck] Target " + targetPlayer.getName() + " flagged "
                        + flaggedHacks.size() + " modules. Starting double confirmation scan...");

                data.getFirstScanResults().putAll(data.getResults());

                List<List<HackDefinition>> newBatches = buildBatches(flaggedHacks);
                data.getBatches().clear();
                data.getBatches().addAll(newBatches);
                data.resetBatch();
                data.getResults().clear();
                data.setConfirmationScan(true);

                processBatch(targetPlayer, data);
                return;
            }
        }

        activeChecks.remove(uuid);

        if (data.isConfirmationScan()) {
            Map<String, HackResult> finalResults = new LinkedHashMap<>();
            for (Map.Entry<String, HackResult> entry : data.getFirstScanResults().entrySet()) {
                String hackId = entry.getKey();
                HackResult firstResult = entry.getValue();

                if (firstResult == HackResult.DETECTED) {
                    HackResult secondResult = data.getResults().get(hackId);
                    if (secondResult == HackResult.DETECTED) {
                        finalResults.put(hackId, HackResult.DETECTED);
                    } else {
                        plugin.getLogger()
                                .info("[lovelycheck] Double confirmation failed for " + hackId + " on "
                                        + targetPlayer.getName() + " (Second scan: " + secondResult
                                        + "). Downgrading to NOT_DETECTED.");
                        finalResults.put(hackId, HackResult.NOT_DETECTED);
                    }
                } else {
                    finalResults.put(hackId, firstResult);
                }
            }
            data.getResults().clear();
            data.getResults().putAll(finalResults);
        }

        String targetName = targetPlayer.getName();
        String targetUUID = uuid.toString();
        String checkerName = data.getInitiatorUUID() != null
                ? Optional.ofNullable(Bukkit.getPlayer(data.getInitiatorUUID()))
                        .map(Player::getName).orElse("Console")
                : (data.isAutoCheck() ? "AutoCheck" : "Console");

        evaluateTranslationMasking(uuid, targetName, data);

        List<HackDefinition> allHacks;
        if (data.isConfirmationScan()) {
            allHacks = new ArrayList<>();
            for (String hackId : data.getFirstScanResults().keySet()) {
                HackDefinition hack = cfg.getHack(hackId);
                if (hack != null)
                    allHacks.add(hack);
            }
        } else {
            allHacks = data.getBatches().stream().flatMap(List::stream).toList();
        }

        Map<HackResult, List<String>> resultGroups = new EnumMap<>(HackResult.class);
        for (HackResult result : HackResult.values()) {
            resultGroups.put(result, new ArrayList<>());
        }
        StringBuilder resultText = new StringBuilder();

        for (HackDefinition hack : allHacks) {
            HackResult r = data.getResults().getOrDefault(hack.getId(), HackResult.SKIPPED);
            if (data.isTranslationMaskedHackId(hack.getId())) {
                resultGroups.get(HackResult.PROTECTED).add(hack.getDisplayName());
            } else {
                resultGroups.get(r).add(hack.getDisplayName());
            }
            resultText.append(hack.getDisplayName()).append(": ").append(r.name()).append("\n");
        }

        List<String> detected = resultGroups.get(HackResult.DETECTED);
        List<String> protectedResults = resultGroups.get(HackResult.PROTECTED);
        List<String> skipped = resultGroups.get(HackResult.SKIPPED);
        int cleanCount = resultGroups.get(HackResult.NOT_DETECTED).size();
        boolean anyDetected = !detected.isEmpty();
        boolean anyProtected = !protectedResults.isEmpty();
        boolean maskingDetected = data.isTranslationMaskingDetected();
        boolean maskingPunishable = maskingDetected && cfg.isTranslationMaskingPunishable();
        boolean allClean = !anyDetected && !anyProtected && !maskingDetected && skipped.isEmpty();
        List<String> violations = buildViolations(detected, maskingPunishable);
        boolean punishable = anyDetected || maskingPunishable;
        String maskingDisplayName = cfg.getTranslationMaskingDisplayName();

        if (!violations.isEmpty()) {
            latestDetectedHacks.put(uuid, violations);
        } else {
            latestDetectedHacks.remove(uuid);
        }

        sendResultLine(data, Component.text("Sign check complete for ", NamedTextColor.WHITE)
                .append(Component.text(targetName, NamedTextColor.YELLOW))
                .append(Component.text(" by ", NamedTextColor.GRAY))
                .append(Component.text(checkerName, NamedTextColor.WHITE))
                .append(Component.text(".", NamedTextColor.GRAY)));
        sendResultLine(data, Component.text("Detected ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(detected.size()),
                        anyDetected ? NamedTextColor.RED : NamedTextColor.GREEN))
                .append(Component.text(" | Protected ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(protectedResults.size()),
                        anyProtected ? NamedTextColor.YELLOW : NamedTextColor.GREEN))
                .append(Component.text(" | Clean ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(cleanCount), NamedTextColor.GREEN))
                .append(Component.text(" | Skipped ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(skipped.size()),
                        skipped.isEmpty() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
        if (anyDetected) {
            sendResultLine(data, resultNamesLine("Detected", NamedTextColor.RED, detected));
        }
        if (anyProtected) {
            sendResultLine(data, resultNamesLine("Protected", NamedTextColor.YELLOW, protectedResults));
        }
        if (maskingDetected) {
            sendResultLine(data, resultNamesLine("Bypass", NamedTextColor.RED, List.of(maskingDisplayName)));
        }
        if (!skipped.isEmpty()) {
            sendResultLine(data, resultNamesLine("Skipped", NamedTextColor.GRAY, skipped));
        }
        if (allClean) {
            sendResultLine(data, Component.text("No configured hacks were detected.", NamedTextColor.GREEN));
        }

        long scanId = plugin.getDatabaseManager().saveScan(
                "hack", targetName, targetUUID, checkerName, data.getReason(), punishable);
        for (HackDefinition hack : allHacks) {
            HackResult r = data.getResults().getOrDefault(hack.getId(), HackResult.SKIPPED);
            HackResult storedResult = data.isTranslationMaskedHackId(hack.getId())
                    ? HackResult.PROTECTED
                    : r;
            plugin.getDatabaseManager().saveHackResult(scanId, hack.getId(),
                    hack.getDisplayName(), storedResult.name());
        }
        if (maskingDetected) {
            plugin.getDatabaseManager().saveHackResult(scanId, TRANSLATION_MASKING_RESULT_ID,
                    maskingDisplayName, HackResult.DETECTED.name());
        }

        if (cfg.isDiscordEnabled() && punishable) {
            WebhookUtil.sendDetectionReport(cfg.getWebhookUrl(), cfg.getEmbedColor(),
                    targetName, checkerName, data.getReason(), violations);
        }

        final String tn = targetName;
        if (punishable && cfg.isPunishmentEnabled()) {
            executePunishment(data, targetName, targetUUID, violations, cfg);
        } else if (anyDetected && cfg.isCommandIfPositiveEnabled()) {
            String cmd = cfg.getPositiveCommand().replace("%player%", tn);
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
        } else if (anyProtected && !anyDetected && cfg.isCommandIfProtectedEnabled()) {
            String cmd = cfg.getProtectedCommand().replace("%player%", tn);
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
        }
        if (allClean && cfg.isCommandIfCleanEnabled()) {
            String cmd = cfg.getCleanCommand().replace("%player%", tn);
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
        }
    }

    private void notifyInitiator(CheckPlayerData data, Component msg) {
        if (data.getInitiatorUUID() == null)
            return;
        Player ini = Bukkit.getPlayer(data.getInitiatorUUID());
        if (ini == null || !ini.isOnline())
            return;
        boolean gets = (ini.hasPermission("lovelycheck.alerts") || ini.hasPermission("lovelychecker.alerts"))
                && plugin.hasAlertsEnabled(ini.getUniqueId());
        if (!gets)
            ini.sendMessage(msg);
    }

    private void sendResultLine(CheckPlayerData data, Component body) {
        if (plugin.getConfigManager().isSilentCheck())
            return;
        Component message = MM.deserialize(plugin.getConfigManager().getPrefix()).append(body);
        plugin.getMessageManager().broadcastAlerts(message);
        notifyInitiator(data, message);
    }

    private Component resultNamesLine(String label, NamedTextColor labelColor, List<String> names) {
        return Component.text(label + ": ", labelColor)
                .append(Component.text(compactNames(names), NamedTextColor.WHITE));
    }

    private record MaskingStats(int notDetectedEvidenceMatches) {
    }

    private List<String> buildViolations(List<String> detected, boolean includeMaskingBypass) {
        LinkedHashSet<String> names = new LinkedHashSet<>(detected);
        if (includeMaskingBypass) {
            names.add(plugin.getConfigManager().getTranslationMaskingDisplayName());
        }
        return List.copyOf(names);
    }

    private void executePunishment(CheckPlayerData data, String targetName, String targetUUID,
            List<String> violations, ConfigManager cfg) {
        int offense = plugin.getDatabaseManager().getNextPunishmentOffense(targetUUID);
        boolean kickOnly = cfg.isPunishmentKickFirst() && offense == 1;
        int banOffense = cfg.isPunishmentKickFirst() ? offense - 1 : offense;
        String duration = kickOnly ? "kick" : getPunishmentDuration(banOffense, cfg.getPunishmentDurations());
        String detections = String.join(", ", violations);
        String reason = applyPunishmentPlaceholders(cfg.getPunishmentReason(),
                targetName, targetUUID, offense, duration, detections);
        String commandTemplate = kickOnly ? cfg.getPunishmentKickCommand() : cfg.getPunishmentCommand();
        String command = applyPunishmentPlaceholders(commandTemplate,
                targetName, targetUUID, offense, duration, detections)
                .replace("%reason%", reason);

        plugin.getDatabaseManager().savePunishment(targetName, targetUUID, offense, duration, reason);

        if (command.isBlank()) {
            plugin.getLogger().warning("[lovelycheck] Punishment command is blank; saved offense #"
                    + offense + " for " + targetName + " but did not dispatch a punishment command.");
        } else {
            Bukkit.getScheduler().runTask(plugin,
                    () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
        }

        String action = kickOnly ? "kick" : duration;
        sendResultLine(data, Component.text("Punishment: offense #", NamedTextColor.YELLOW)
                .append(Component.text(String.valueOf(offense), NamedTextColor.WHITE))
                .append(Component.text(" for ", NamedTextColor.YELLOW))
                .append(Component.text(action, NamedTextColor.WHITE))
                .append(Component.text(" - ", NamedTextColor.GRAY))
                .append(Component.text(compactNames(violations), NamedTextColor.RED)));
    }

    private String getPunishmentDuration(int offense, List<String> durations) {
        if (durations.isEmpty()) {
            return "30d";
        }
        int index = Math.min(Math.max(offense, 1) - 1, durations.size() - 1);
        return durations.get(index);
    }

    private String applyPunishmentPlaceholders(String value, String targetName, String targetUUID,
            int offense, String duration, String detections) {
        return value
                .replace("%player%", targetName)
                .replace("%uuid%", targetUUID)
                .replace("%offense%", String.valueOf(offense))
                .replace("%duration%", duration)
                .replace("%detections%", detections);
    }

    private String compactNames(List<String> names) {
        int maxShown = 8;
        if (names.size() <= maxShown) {
            return String.join(", ", names);
        }
        return String.join(", ", names.subList(0, maxShown))
                + " +" + (names.size() - maxShown) + " more";
    }

    private void restoreCurrentSign(CheckPlayerData data) {
        Location loc = data.getSignLocation();
        if (loc == null)
            return;
        BlockState originalState = data.getOriginalState();
        data.setSignLocation(null);
        data.setOriginalState(null);
        if (originalState == null) {
            plugin.restoreFakeSignCheck(data.getTargetUUID(), loc);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (originalState != null)
                    originalState.update(true, false);
            } catch (Exception e) {
                plugin.getLogger().warning("[lovelycheck] Restore: " + e.getMessage());
            }
        });
    }

    public void cleanup() {
        for (CheckPlayerData d : activeChecks.values()) {
            if (d.getSignTimeoutTask() != null)
                d.getSignTimeoutTask().cancel();
            restoreCurrentSign(d);
        }
        activeChecks.clear();
    }
}
