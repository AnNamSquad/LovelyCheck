package org.lovelycheck.core.config;

import org.lovelycheck.core.LovelyCheckRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BedrockConfig {

    private static boolean enabled = false;
    private static String label = "Bedrock";
    private static List<Action> bedrockActions = Collections.emptyList();

    private BedrockConfig() {
    }

    public static void load(@Nullable ConfigNode result) {
        enabled = false;
        label = "Bedrock";
        bedrockActions = Collections.emptyList();

        if (result == null) {
            return;
        }

        Boolean enabledValue = result.getBoolean("enabled");
        if (enabledValue != null) {
            enabled = enabledValue;
        }

        String labelValue = result.getString("label");
        if (labelValue != null && !labelValue.isBlank()) {
            label = labelValue;
        }

        List<?> actionIds = result.getList("actions");
        if (actionIds != null) {
            bedrockActions = resolveActions(actionIds);
        }
    }

    private static List<Action> resolveActions(@Nullable List<?> actionIds) {
        if (actionIds == null) {
            return Collections.emptyList();
        }
        List<Action> actions = new ArrayList<>();
        for (Object value : actionIds) {
            if (!(value instanceof String actionName)) {
                continue;
            }
            Action action = LovelyCheckRegistry.getAction(actionName);
            if (action != null) {
                actions.add(action);
            }
        }
        return Collections.unmodifiableList(actions);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static String getLabel() {
        return label;
    }

    public static List<Action> getBedrockActions() {
        return bedrockActions;
    }
}
