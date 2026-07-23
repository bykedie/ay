package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import java.util.EnumMap;
import java.util.Map;

public final class ModuleManager {
    private final Map<ModuleId, Boolean> states = new EnumMap<>(ModuleId.class);

    public ModuleManager() {
        states.put(ModuleId.AUTO_GG, ModConfig.autoGg);
        states.put(ModuleId.AUTO_REPLY, ModConfig.autoReply);
        states.put(ModuleId.AUTO_MINE, ModConfig.autoMine);
        states.put(ModuleId.CREATIVE_TOOLS, ModConfig.creativeTools);
        states.put(ModuleId.MELEE_AURA, ModConfig.meleeAura);
        states.put(ModuleId.CRITICALS, ModConfig.criticals);
    }

    public boolean isEnabled(ModuleId id) {
        return Boolean.TRUE.equals(states.get(id));
    }

    public boolean toggle(ModuleId id) {
        return setEnabled(id, !isEnabled(id));
    }

    public boolean setEnabled(ModuleId id, boolean enabled) {
        states.put(id, enabled);
        ModConfig.saveModule(id, enabled);
        return enabled;
    }

    public String statusLine() {
        StringBuilder text = new StringBuilder();
        for (ModuleId id : ModuleId.values()) {
            if (text.length() > 0) text.append(" | ");
            text.append(id.key()).append('=').append(isEnabled(id) ? "on" : "off");
        }
        return text.toString();
    }
}
