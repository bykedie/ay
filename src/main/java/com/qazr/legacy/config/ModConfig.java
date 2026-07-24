package com.qazr.legacy.config;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraftforge.common.config.Configuration;

public final class ModConfig {
    private static Configuration configuration;

    public static boolean autoGg;
    public static boolean autoReply;
    public static boolean autoMine;
    public static boolean creativeTools;
    public static boolean meleeAura;
    public static boolean blinkStrike;
    public static boolean criticals;
    public static String[] ggMessages;
    public static int ggMinDelayTicks;
    public static int ggMaxDelayTicks;
    public static String replyTarget;
    public static String[] replyMessages;
    public static int replyCooldownTicks;
    public static int mineRadius;
    public static int mineDelayTicks;
    public static String[] mineBlocks;
    public static double meleeRange;
    public static int meleeDelayTicks;
    public static boolean meleePlayers;
    public static boolean meleeHostiles;
    public static boolean meleeAnimals;
    public static boolean meleeAutoWeapon;
    public static boolean meleeModded;
    public static boolean meleeRotate;
    public static boolean meleeMultiTarget;
    public static int meleeMaxTargets;
    public static boolean meleeVisualize;
    public static String meleePriority;
    private static final Set<String> meleeExcludedModEntities = new LinkedHashSet<>();
    public static double blinkRange;
    public static double blinkStep;
    public static double blinkAttackDistance;
    public static int blinkPredictTicks;
    public static int blinkDelayTicks;
    public static boolean blinkPlayers;
    public static boolean blinkHostiles;
    public static boolean blinkAnimals;
    public static boolean blinkModded;
    public static boolean blinkAutoWeapon;
    public static boolean blinkRotate;
    public static boolean blinkMultiTarget;
    public static int blinkMaxTargets;
    public static boolean blinkVisualize;
    public static String blinkPriority;
    private static final Set<String> blinkExcludedModEntities = new LinkedHashSet<>();

    private ModConfig() {
    }

    public static void load(File file) {
        configuration = new Configuration(file);
        sync();
    }

    public static void sync() {
        autoGg = configuration.getBoolean("autoGg", "modules", true, "Send a configurable message after a detected kill.");
        autoReply = configuration.getBoolean("autoReply", "modules", false, "Reply to messages from the selected player.");
        autoMine = configuration.getBoolean("autoMine", "modules", false, "Mine configured nearby ore blocks.");
        creativeTools = configuration.getBoolean("creativeTools", "modules", true, "Enable creative item and potion commands.");
        meleeAura = configuration.getBoolean("meleeAura", "modules", false, "Automatically attack nearby entities with swords or axes.");
        blinkStrike = configuration.getBoolean("blinkStrike", "modules", false, "Attack through a temporary packet-position excursion and return.");
        criticals = configuration.getBoolean("criticals", "modules", false, "Send a critical movement sequence before melee attacks.");
        ggMessages = configuration.getStringList("messages", "autoGg", new String[] {"gg {player}", "good fight {player}"}, "Messages sent after a detected kill.");
        ggMinDelayTicks = configuration.getInt("minDelayTicks", "autoGg", 4, 0, 200, "Minimum delay before sending.");
        ggMaxDelayTicks = configuration.getInt("maxDelayTicks", "autoGg", 24, 0, 200, "Maximum delay before sending.");
        replyTarget = configuration.getString("target", "autoReply", "", "Only messages from this player trigger a reply. Empty accepts anyone.");
        replyMessages = configuration.getStringList("messages", "autoReply", new String[] {"Hi {player}", "I saw that, {player}."}, "Random automatic replies.");
        replyCooldownTicks = configuration.getInt("cooldownTicks", "autoReply", 100, 20, 1200, "Minimum delay between replies.");
        mineRadius = configuration.getInt("radius", "autoMine", 4, 1, 6, "Block scan radius. Only reachable blocks are mined.");
        mineDelayTicks = configuration.getInt("delayTicks", "autoMine", 2, 0, 40, "Delay between mining actions.");
        mineBlocks = configuration.getStringList("blocks", "autoMine", new String[] {
            "minecraft:coal_ore", "minecraft:iron_ore", "minecraft:gold_ore",
            "minecraft:redstone_ore", "minecraft:lapis_ore", "minecraft:diamond_ore", "minecraft:emerald_ore"
        }, "Registry names of blocks to mine.");
        meleeRange = configuration.getFloat("range", "meleeAura", 3.0F, 1.0F, 6.0F, "Distance from the player eyes to the nearest point of the target hitbox.");
        meleeDelayTicks = configuration.getInt("delayTicks", "meleeAura", 1, 0, 20, "Extra ticks between attacks after cooldown is ready.");
        meleePlayers = configuration.getBoolean("players", "meleeAura", true, "Target players.");
        meleeHostiles = configuration.getBoolean("hostiles", "meleeAura", true, "Target hostile mobs.");
        meleeAnimals = configuration.getBoolean("animals", "meleeAura", false, "Target animals.");
        meleeAutoWeapon = configuration.getBoolean("autoWeapon", "meleeAura", true, "Select the strongest sword or axe in the hotbar.");
        meleeModded = configuration.getBoolean("moddedEntities", "meleeAura", false, "Target living entities registered by other mods.");
        meleeRotate = configuration.getBoolean("rotateView", "meleeAura", false, "Turn the local camera toward the current target.");
        meleeMultiTarget = configuration.getBoolean("multiTarget", "meleeAura", false, "Attack multiple sorted targets per cycle.");
        meleeMaxTargets = configuration.getInt("maxTargets", "meleeAura", 3, 1, 5, "Maximum targets attacked per cycle when multi-target is enabled.");
        meleeVisualize = configuration.getBoolean("visualizeTargets", "meleeAura", false, "Draw boxes around selected targets.");
        meleePriority = configuration.getString("priority", "meleeAura", "distance", "Target priority: distance or health.");
        meleeExcludedModEntities.clear();
        meleeExcludedModEntities.addAll(Arrays.asList(configuration.getStringList("excludedModEntities", "meleeAura",
            new String[0], "Mod entity registry names excluded from melee aura.")));
        blinkRange = configuration.getFloat("range", "blinkStrike", 12.0F, 3.0F, 200.0F, "Maximum target acquisition distance. Twelve blocks fits the vanilla movement and attack checks with default settings.");
        blinkStep = configuration.getFloat("step", "blinkStrike", 4.0F, 1.0F, 9.5F, "Maximum distance represented by each position packet. Vanilla 1.12 rejects movement above roughly 10 blocks per packet.");
        blinkAttackDistance = configuration.getFloat("attackDistance", "blinkStrike", 2.5F, 1.0F, 4.0F, "Spoofed distance from the target when the attack packet is sent.");
        blinkPredictTicks = configuration.getInt("predictTicks", "blinkStrike", 1, 0, 5, "Horizontal target-motion prediction.");
        blinkDelayTicks = configuration.getInt("delayTicks", "blinkStrike", 8, 1, 40, "Additional delay after each attempted strike.");
        blinkPlayers = configuration.getBoolean("players", "blinkStrike", true, "Target players independently from melee aura.");
        blinkHostiles = configuration.getBoolean("hostiles", "blinkStrike", true, "Target hostile mobs independently from melee aura.");
        blinkAnimals = configuration.getBoolean("animals", "blinkStrike", false, "Target animals independently from melee aura.");
        blinkModded = configuration.getBoolean("moddedEntities", "blinkStrike", false, "Target living entities registered by other mods.");
        blinkAutoWeapon = configuration.getBoolean("autoWeapon", "blinkStrike", true, "Select the strongest sword or axe in the hotbar.");
        blinkRotate = configuration.getBoolean("rotateView", "blinkStrike", false, "Turn the local camera toward the current target.");
        blinkMultiTarget = configuration.getBoolean("multiTarget", "blinkStrike", false, "Attack multiple sorted targets per cycle.");
        blinkMaxTargets = configuration.getInt("maxTargets", "blinkStrike", 3, 1, 5, "Maximum targets attacked per cycle when multi-target is enabled.");
        blinkVisualize = configuration.getBoolean("visualizeTargets", "blinkStrike", false, "Draw boxes around selected targets.");
        blinkPriority = configuration.getString("priority", "blinkStrike", "distance", "Target priority: distance or health.");
        blinkExcludedModEntities.clear();
        blinkExcludedModEntities.addAll(Arrays.asList(configuration.getStringList("excludedModEntities", "blinkStrike",
            new String[0], "Mod entity registry names excluded from blink strike.")));
        if (configuration.hasChanged()) configuration.save();
    }

    public static void reload() {
        configuration.load();
        sync();
    }

    public static void saveModule(ModuleId id, boolean enabled) {
        configuration.get("modules", id.key(), enabled).set(enabled);
        configuration.save();
    }

    public static double saveRange(ModuleId id, double value) {
        if (id == ModuleId.MELEE_AURA) {
            requireRange(id, value, 1.0, 6.0);
            meleeRange = value;
            configuration.get("meleeAura", "range", value).set(value);
        } else if (id == ModuleId.BLINK_STRIKE) {
            requireRange(id, value, 3.0, 200.0);
            blinkRange = value;
            configuration.get("blinkStrike", "range", value).set(value);
        } else {
            throw new IllegalArgumentException("Module has no attack range: " + id.key());
        }
        configuration.save();
        return value;
    }

    public static double getRange(ModuleId id) {
        if (id == ModuleId.MELEE_AURA) return meleeRange;
        if (id == ModuleId.BLINK_STRIKE) return blinkRange;
        throw new IllegalArgumentException("Module has no attack range: " + id.key());
    }

    public static double getNumber(ModuleSetting setting) {
        switch (setting) {
            case GG_MIN_DELAY: return ggMinDelayTicks;
            case GG_MAX_DELAY: return ggMaxDelayTicks;
            case REPLY_COOLDOWN: return replyCooldownTicks;
            case MINE_RADIUS: return mineRadius;
            case MINE_DELAY: return mineDelayTicks;
            case MELEE_RANGE: return meleeRange;
            case MELEE_DELAY: return meleeDelayTicks;
            case MELEE_MAX_TARGETS: return meleeMaxTargets;
            case BLINK_RANGE: return blinkRange;
            case BLINK_STEP: return blinkStep;
            case BLINK_ATTACK_DISTANCE: return blinkAttackDistance;
            case BLINK_PREDICT: return blinkPredictTicks;
            case BLINK_DELAY: return blinkDelayTicks;
            case BLINK_MAX_TARGETS: return blinkMaxTargets;
            default: throw new IllegalArgumentException("Setting is not numeric: " + setting);
        }
    }

    public static double saveNumber(ModuleSetting setting, double value) {
        if (setting.type() != ModuleSetting.Type.NUMBER) {
            throw new IllegalArgumentException("Setting is not numeric: " + setting);
        }
        double clamped = Math.max(setting.min(), Math.min(setting.max(), value));
        double rounded = Math.round(clamped / setting.step()) * setting.step();
        rounded = Math.max(setting.min(), Math.min(setting.max(), rounded));
        switch (setting) {
            case GG_MIN_DELAY:
                ggMinDelayTicks = (int) rounded;
                if (ggMaxDelayTicks < ggMinDelayTicks) ggMaxDelayTicks = ggMinDelayTicks;
                saveInt("autoGg", "minDelayTicks", ggMinDelayTicks);
                saveInt("autoGg", "maxDelayTicks", ggMaxDelayTicks);
                break;
            case GG_MAX_DELAY:
                ggMaxDelayTicks = (int) rounded;
                if (ggMinDelayTicks > ggMaxDelayTicks) ggMinDelayTicks = ggMaxDelayTicks;
                saveInt("autoGg", "maxDelayTicks", ggMaxDelayTicks);
                saveInt("autoGg", "minDelayTicks", ggMinDelayTicks);
                break;
            case REPLY_COOLDOWN: replyCooldownTicks = (int) rounded; saveInt("autoReply", "cooldownTicks", replyCooldownTicks); break;
            case MINE_RADIUS: mineRadius = (int) rounded; saveInt("autoMine", "radius", mineRadius); break;
            case MINE_DELAY: mineDelayTicks = (int) rounded; saveInt("autoMine", "delayTicks", mineDelayTicks); break;
            case MELEE_RANGE: meleeRange = rounded; saveDouble("meleeAura", "range", rounded); break;
            case MELEE_DELAY: meleeDelayTicks = (int) rounded; saveInt("meleeAura", "delayTicks", meleeDelayTicks); break;
            case MELEE_MAX_TARGETS: meleeMaxTargets = (int) rounded; saveInt("meleeAura", "maxTargets", meleeMaxTargets); break;
            case BLINK_RANGE: blinkRange = rounded; saveDouble("blinkStrike", "range", rounded); break;
            case BLINK_STEP: blinkStep = rounded; saveDouble("blinkStrike", "step", rounded); break;
            case BLINK_ATTACK_DISTANCE: blinkAttackDistance = rounded; saveDouble("blinkStrike", "attackDistance", rounded); break;
            case BLINK_PREDICT: blinkPredictTicks = (int) rounded; saveInt("blinkStrike", "predictTicks", blinkPredictTicks); break;
            case BLINK_DELAY: blinkDelayTicks = (int) rounded; saveInt("blinkStrike", "delayTicks", blinkDelayTicks); break;
            case BLINK_MAX_TARGETS: blinkMaxTargets = (int) rounded; saveInt("blinkStrike", "maxTargets", blinkMaxTargets); break;
            default: throw new IllegalArgumentException("Setting is not numeric: " + setting);
        }
        configuration.save();
        return rounded;
    }

    public static boolean getToggle(ModuleSetting setting) {
        switch (setting) {
            case MELEE_PLAYERS: return meleePlayers;
            case MELEE_HOSTILES: return meleeHostiles;
            case MELEE_ANIMALS: return meleeAnimals;
            case MELEE_AUTO_WEAPON: return meleeAutoWeapon;
            case MELEE_MODDED: return meleeModded;
            case MELEE_ROTATE: return meleeRotate;
            case MELEE_MULTI: return meleeMultiTarget;
            case MELEE_VISUALIZE: return meleeVisualize;
            case BLINK_PLAYERS: return blinkPlayers;
            case BLINK_HOSTILES: return blinkHostiles;
            case BLINK_ANIMALS: return blinkAnimals;
            case BLINK_MODDED: return blinkModded;
            case BLINK_AUTO_WEAPON: return blinkAutoWeapon;
            case BLINK_ROTATE: return blinkRotate;
            case BLINK_MULTI: return blinkMultiTarget;
            case BLINK_VISUALIZE: return blinkVisualize;
            default: throw new IllegalArgumentException("Setting is not a toggle: " + setting);
        }
    }

    public static boolean toggle(ModuleSetting setting) {
        boolean value = !getToggle(setting);
        switch (setting) {
            case MELEE_PLAYERS: meleePlayers = value; saveBoolean("meleeAura", "players", value); break;
            case MELEE_HOSTILES: meleeHostiles = value; saveBoolean("meleeAura", "hostiles", value); break;
            case MELEE_ANIMALS: meleeAnimals = value; saveBoolean("meleeAura", "animals", value); break;
            case MELEE_AUTO_WEAPON: meleeAutoWeapon = value; saveBoolean("meleeAura", "autoWeapon", value); break;
            case MELEE_MODDED: meleeModded = value; saveBoolean("meleeAura", "moddedEntities", value); break;
            case MELEE_ROTATE: meleeRotate = value; saveBoolean("meleeAura", "rotateView", value); break;
            case MELEE_MULTI: meleeMultiTarget = value; saveBoolean("meleeAura", "multiTarget", value); break;
            case MELEE_VISUALIZE: meleeVisualize = value; saveBoolean("meleeAura", "visualizeTargets", value); break;
            case BLINK_PLAYERS: blinkPlayers = value; saveBoolean("blinkStrike", "players", value); break;
            case BLINK_HOSTILES: blinkHostiles = value; saveBoolean("blinkStrike", "hostiles", value); break;
            case BLINK_ANIMALS: blinkAnimals = value; saveBoolean("blinkStrike", "animals", value); break;
            case BLINK_MODDED: blinkModded = value; saveBoolean("blinkStrike", "moddedEntities", value); break;
            case BLINK_AUTO_WEAPON: blinkAutoWeapon = value; saveBoolean("blinkStrike", "autoWeapon", value); break;
            case BLINK_ROTATE: blinkRotate = value; saveBoolean("blinkStrike", "rotateView", value); break;
            case BLINK_MULTI: blinkMultiTarget = value; saveBoolean("blinkStrike", "multiTarget", value); break;
            case BLINK_VISUALIZE: blinkVisualize = value; saveBoolean("blinkStrike", "visualizeTargets", value); break;
            default: throw new IllegalArgumentException("Setting is not a toggle: " + setting);
        }
        configuration.save();
        return value;
    }

    public static String getChoice(ModuleSetting setting) {
        if (setting == ModuleSetting.MELEE_PRIORITY) return "health".equalsIgnoreCase(meleePriority) ? "血量" : "距离";
        if (setting == ModuleSetting.BLINK_PRIORITY) return "health".equalsIgnoreCase(blinkPriority) ? "血量" : "距离";
        throw new IllegalArgumentException("Setting is not a choice: " + setting);
    }

    public static String cycleChoice(ModuleSetting setting) {
        if (setting == ModuleSetting.MELEE_PRIORITY) {
            meleePriority = "health".equalsIgnoreCase(meleePriority) ? "distance" : "health";
            configuration.get("meleeAura", "priority", meleePriority).set(meleePriority);
        } else if (setting == ModuleSetting.BLINK_PRIORITY) {
            blinkPriority = "health".equalsIgnoreCase(blinkPriority) ? "distance" : "health";
            configuration.get("blinkStrike", "priority", blinkPriority).set(blinkPriority);
        } else {
            throw new IllegalArgumentException("Setting is not a choice: " + setting);
        }
        configuration.save();
        return getChoice(setting);
    }

    public static boolean isModEntityEnabled(ModuleId module, String registryName) {
        return !excludedModEntities(module).contains(registryName);
    }

    public static boolean toggleModEntity(ModuleId module, String registryName) {
        Set<String> excluded = excludedModEntities(module);
        boolean enabled;
        if (excluded.remove(registryName)) {
            enabled = true;
        } else {
            excluded.add(registryName);
            enabled = false;
        }
        String category = module == ModuleId.MELEE_AURA ? "meleeAura" : "blinkStrike";
        configuration.get(category, "excludedModEntities", new String[0])
            .set(excluded.toArray(new String[0]));
        configuration.save();
        return enabled;
    }

    private static Set<String> excludedModEntities(ModuleId module) {
        if (module == ModuleId.MELEE_AURA) return meleeExcludedModEntities;
        if (module == ModuleId.BLINK_STRIKE) return blinkExcludedModEntities;
        throw new IllegalArgumentException("Module has no entity targets: " + module);
    }

    private static void saveInt(String category, String key, int value) {
        configuration.get(category, key, value).set(value);
    }

    private static void saveDouble(String category, String key, double value) {
        configuration.get(category, key, value).set(value);
    }

    private static void saveBoolean(String category, String key, boolean value) {
        configuration.get(category, key, value).set(value);
    }

    private static void requireRange(ModuleId id, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(id.key() + " range must be between " + min + " and " + max);
        }
    }
}
