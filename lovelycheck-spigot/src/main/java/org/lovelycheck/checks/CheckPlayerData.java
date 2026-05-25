package org.lovelycheck.checks;

import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private BlockState originalState;
    private BukkitTask signTimeoutTask;
    private boolean currentBatchFakePacket;
    private boolean forceRealSignForCurrentBatch;
    private boolean translationMaskingDetected;
    private final Set<String> translationMaskedHackIds = new HashSet<>();

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
    public Map<String, HackResult> getResults()        { return results; }
    public boolean isAutoCheck()                       { return autoCheck; }
    public String getReason()                          { return reason; }
    public boolean hasMoreBatches()                    { return currentBatch < batches.size(); }
    public List<HackDefinition> getCurrentBatchHacks() { return batches.get(currentBatch); }
    public long getScanId()                            { return scanId; }
    public void setScanId(long id)                     { this.scanId = id; }

    public Location getSignLocation()            { return signLocation; }
    public void setSignLocation(Location l)      { this.signLocation = l; }
    public BlockState getOriginalState()         { return originalState; }
    public void setOriginalState(BlockState s)   { this.originalState = s; }
    public BukkitTask getSignTimeoutTask()       { return signTimeoutTask; }
    public void setSignTimeoutTask(BukkitTask t) { this.signTimeoutTask = t; }
    public boolean isCurrentBatchFakePacket()    { return currentBatchFakePacket; }
    public void setCurrentBatchFakePacket(boolean currentBatchFakePacket) {
        this.currentBatchFakePacket = currentBatchFakePacket;
    }
    public boolean isForceRealSignForCurrentBatch() { return forceRealSignForCurrentBatch; }
    public void setForceRealSignForCurrentBatch(boolean forceRealSignForCurrentBatch) {
        this.forceRealSignForCurrentBatch = forceRealSignForCurrentBatch;
    }
    public boolean isTranslationMaskingDetected() { return translationMaskingDetected; }
    public void setTranslationMaskingDetected(boolean translationMaskingDetected) {
        this.translationMaskingDetected = translationMaskingDetected;
    }
    public void addTranslationMaskedHackId(String id) { translationMaskedHackIds.add(id); }
    public boolean isTranslationMaskedHackId(String id) { return translationMaskedHackIds.contains(id); }
    public Set<String> getTranslationMaskedHackIds() { return Set.copyOf(translationMaskedHackIds); }
}
