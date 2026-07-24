package com.qazr.legacy.config;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public enum ModuleSetting {
    GG_MIN_DELAY(ModuleId.AUTO_GG, "最小延迟", Type.NUMBER, 0.0, 200.0, 1.0, " tick"),
    GG_MAX_DELAY(ModuleId.AUTO_GG, "最大延迟", Type.NUMBER, 0.0, 200.0, 1.0, " tick"),
    REPLY_COOLDOWN(ModuleId.AUTO_REPLY, "回复冷却", Type.NUMBER, 20.0, 1200.0, 20.0, " tick"),
    MINE_RADIUS(ModuleId.AUTO_MINE, "搜索半径", Type.NUMBER, 1.0, 6.0, 1.0, " 格"),
    MINE_DELAY(ModuleId.AUTO_MINE, "挖掘延迟", Type.NUMBER, 0.0, 40.0, 1.0, " tick"),
    MELEE_RANGE(ModuleId.MELEE_AURA, "攻击距离", Type.NUMBER, 1.0, 6.0, 0.1, " 格"),
    MELEE_DELAY(ModuleId.MELEE_AURA, "攻击延迟", Type.NUMBER, 0.0, 20.0, 1.0, " tick"),
    MELEE_PLAYERS(ModuleId.MELEE_AURA, "攻击玩家", Type.TOGGLE),
    MELEE_HOSTILES(ModuleId.MELEE_AURA, "攻击敌对生物", Type.TOGGLE),
    MELEE_ANIMALS(ModuleId.MELEE_AURA, "攻击动物", Type.TOGGLE),
    MELEE_AUTO_WEAPON(ModuleId.MELEE_AURA, "自动选择武器", Type.TOGGLE),
    MELEE_PRIORITY(ModuleId.MELEE_AURA, "目标优先级", Type.CHOICE),
    BLINK_RANGE(ModuleId.BLINK_STRIKE, "搜索距离", Type.NUMBER, 3.0, 200.0, 1.0, " 格"),
    BLINK_STEP(ModuleId.BLINK_STRIKE, "分段步长", Type.NUMBER, 1.0, 9.5, 0.1, " 格"),
    BLINK_ATTACK_DISTANCE(ModuleId.BLINK_STRIKE, "攻击位置距离", Type.NUMBER, 1.0, 4.0, 0.1, " 格"),
    BLINK_PREDICT(ModuleId.BLINK_STRIKE, "目标预测", Type.NUMBER, 0.0, 5.0, 1.0, " tick"),
    BLINK_DELAY(ModuleId.BLINK_STRIKE, "攻击间隔", Type.NUMBER, 1.0, 40.0, 1.0, " tick");

    public enum Type {
        NUMBER, TOGGLE, CHOICE
    }

    private final ModuleId module;
    private final String label;
    private final Type type;
    private final double min;
    private final double max;
    private final double step;
    private final String suffix;
    private static final Map<ModuleId, ModuleSetting[]> BY_MODULE = new EnumMap<>(ModuleId.class);

    static {
        for (ModuleId module : ModuleId.values()) {
            List<ModuleSetting> settings = new ArrayList<>();
            for (ModuleSetting setting : values()) {
                if (setting.module == module) settings.add(setting);
            }
            BY_MODULE.put(module, settings.toArray(new ModuleSetting[0]));
        }
    }

    ModuleSetting(ModuleId module, String label, Type type) {
        this(module, label, type, 0.0, 0.0, 0.0, "");
    }

    ModuleSetting(ModuleId module, String label, Type type, double min, double max, double step, String suffix) {
        this.module = module;
        this.label = label;
        this.type = type;
        this.min = min;
        this.max = max;
        this.step = step;
        this.suffix = suffix;
    }

    public ModuleId module() {
        return module;
    }

    public String label() {
        return label;
    }

    public Type type() {
        return type;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double step() {
        return step;
    }

    public String suffix() {
        return suffix;
    }

    public static ModuleSetting[] forModule(ModuleId module) {
        return BY_MODULE.get(module);
    }
}
