package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import java.util.EnumMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {
    private final Map<ModuleId, Boolean> states = new EnumMap<>(ModuleId.class);
    private final List<Runnable> reloadListeners = new ArrayList<>();

    public ModuleManager() {
        reloadStates();
    }

    private void reloadStates() {
        states.put(ModuleId.AUTO_GG, ModConfig.autoGg);
        states.put(ModuleId.AUTO_REPLY, ModConfig.autoReply);
        states.put(ModuleId.AUTO_MINE, ModConfig.autoMine);
        states.put(ModuleId.AUTO_BRIDGE, ModConfig.autoBridge);
        states.put(ModuleId.ORE_VISUALIZER, ModConfig.oreVisualizer);
        states.put(ModuleId.CREATIVE_TOOLS, ModConfig.creativeTools);
        states.put(ModuleId.MELEE_AURA, ModConfig.meleeAura);
        states.put(ModuleId.BLINK_STRIKE, ModConfig.blinkStrike);
        states.put(ModuleId.FLIGHT, ModConfig.flight);
        states.put(ModuleId.CRITICALS, ModConfig.criticals);
        states.put(ModuleId.TARGET_VISUALIZER, ModConfig.targetVisualizer);
        if (isEnabled(ModuleId.MELEE_AURA) && isEnabled(ModuleId.BLINK_STRIKE)) {
            states.put(ModuleId.BLINK_STRIKE, false);
            ModConfig.saveModule(ModuleId.BLINK_STRIKE, false);
        }
    }

    public void reloadConfig() {
        ModConfig.reload();
        reloadStates();
        for (Runnable listener : reloadListeners) listener.run();
    }

    public void addReloadListener(Runnable listener) {
        reloadListeners.add(listener);
    }

    public boolean isEnabled(ModuleId id) {
        return Boolean.TRUE.equals(states.get(id));
    }

    public boolean toggle(ModuleId id) {
        return setEnabled(id, !isEnabled(id));
    }

    public boolean setEnabled(ModuleId id, boolean enabled) {
        if (enabled && id == ModuleId.MELEE_AURA) disableCombatPeer(ModuleId.BLINK_STRIKE);
        if (enabled && id == ModuleId.BLINK_STRIKE) disableCombatPeer(ModuleId.MELEE_AURA);
        states.put(id, enabled);
        ModConfig.saveModule(id, enabled);
        return enabled;
    }

    private void disableCombatPeer(ModuleId id) {
        if (!isEnabled(id)) return;
        states.put(id, false);
        ModConfig.saveModule(id, false);
    }

    public String statusLine() {
        StringBuilder text = new StringBuilder();
        for (ModuleId id : ModuleId.values()) {
            if (text.length() > 0) text.append(" | ");
            text.append(id.displayName()).append('=').append(isEnabled(id) ? "已开启" : "已关闭");
        }
        return text.toString();
    }
}
