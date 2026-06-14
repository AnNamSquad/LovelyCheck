package org.lovelycheck.core.forge;

import org.lovelycheck.core.LovelyCheckPlayer;

public final class ForgeSpoofingDetector {

    public static final String CHECK_ID = "spoofed_brand";
    public static final String CHECK_NAME = "Spoofed Brand (Fabric)";

    private ForgeSpoofingDetector() {
    }

    public static boolean detect(LovelyCheckPlayer playerData) {
        if (!ForgeConfig.isSpoofingDetectionEnabled()
                || playerData.hasGenericCheck(CHECK_ID)
                || !ForgeChannelParser.isVanillaBrand(playerData.getBrand())
                || !playerData.hasFabricChannelsDetected()) {
            return false;
        }

        playerData.addGenericCheck(CHECK_ID);
        return true;
    }
}
