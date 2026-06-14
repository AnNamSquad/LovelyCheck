package org.lovelycheck.spigot.checks;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import java.util.concurrent.atomic.AtomicBoolean;

public class CheckPlayerData {

    private final UUID targetUUID;
    private final UUID initiatorUUID;
    private final List<List<HackDefinition>> batches;
    private int currentBatch;
    private final Map<String, HackResult> results;
    private final boolean autoCheck;
    private final String reason;
    private long scanId = -1;

    private Location signLocation;
    private BukkitTask signTimeoutTask;
    private boolean translationMaskingDetected;
    private final Set<String> translationMaskedHackIds = new HashSet<>();

    private boolean confirmationScan = false;
    private final Map<String, HackResult> firstScanResults = new LinkedHashMap<>();
    private boolean localeProbe = false;
    private final AtomicBoolean responded = new AtomicBoolean(false);

    public CheckPlayerData(UUID targetUUID, UUID initiatorUUID,
                           List<List<HackDefinition>> batches,
                           boolean autoCheck, String reason) {
        this.targetUUID    = targetUUID;
        this.initiatorUUID = initiatorUUID;
        this.batches       = batches;
        this.currentBatch  = 0;
        this.results       = new LinkedHashMap<>();
        this.autoCheck     = autoCheck;
        this.reason        = reason;
    }

    public UUID getTargetUUID()                        { return targetUUID; }
    public UUID getInitiatorUUID()                     { return initiatorUUID; }
    public List<List<HackDefinition>> getBatches()     { return batches; }
    public int getCurrentBatch()                       { return currentBatch; }
    public void incrementBatch()                       { currentBatch++; }
    public void setCurrentBatch(int currentBatch)       { this.currentBatch = currentBatch; }
    public void resetBatch()                           { this.currentBatch = 0; }
    public Map<String, HackResult> getResults()        { return results; }
    public boolean isAutoCheck()                       { return autoCheck; }
    public String getReason()                          { return reason; }
    public boolean hasMoreBatches()                    { return currentBatch < batches.size(); }
    public List<HackDefinition> getCurrentBatchHacks() { return batches.get(currentBatch); }
    public long getScanId()                            { return scanId; }
    public void setScanId(long id)                     { this.scanId = id; }

    public Location getSignLocation()            { return signLocation; }
    public void setSignLocation(Location l)      { this.signLocation = l; }
    public BukkitTask getSignTimeoutTask()       { return signTimeoutTask; }
    public void setSignTimeoutTask(BukkitTask t) { this.signTimeoutTask = t; }
    public boolean isTranslationMaskingDetected() { return translationMaskingDetected; }
    public void setTranslationMaskingDetected(boolean translationMaskingDetected) {
        this.translationMaskingDetected = translationMaskingDetected;
    }
    public void addTranslationMaskedHackId(String id) { translationMaskedHackIds.add(id); }
    public boolean isTranslationMaskedHackId(String id) { return translationMaskedHackIds.contains(id); }
    public Set<String> getTranslationMaskedHackIds() { return Set.copyOf(translationMaskedHackIds); }

    public boolean isConfirmationScan() { return confirmationScan; }
    public void setConfirmationScan(boolean confirmationScan) { this.confirmationScan = confirmationScan; }
    public Map<String, HackResult> getFirstScanResults() { return firstScanResults; }

    public boolean isLocaleProbe() { return localeProbe; }
    public void setLocaleProbe(boolean localeProbe) { this.localeProbe = localeProbe; }
    public AtomicBoolean getResponded() { return responded; }
}
