package com.qazr.legacy.config;

import java.io.File;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraftforge.common.config.Configuration;

public final class ModConfig {
    private static Configuration configuration;

    public static boolean autoGg;
    public static boolean autoReply;
    public static boolean autoMine;
    public static boolean autoBridge;
    public static boolean oreVisualizer;
    public static boolean creativeTools;
    public static boolean meleeAura;
    public static boolean blinkStrike;
    public static boolean flight;
    public static boolean criticals;
    public static boolean targetVisualizer;
    public static boolean panelGraveKeyMigrated;
    public static String[] ggMessages;
    public static int ggMinDelayTicks;
    public static int ggMaxDelayTicks;
    public static String replyTarget;
    public static String[] replyMessages;
    public static int replyCooldownTicks;
    public static int mineDelayTicks;
    public static String[] mineBlocks;
    public static int minePathRange;
    public static int mineManualPauseTicks;
    public static boolean mineVisualizePath;
    public static boolean mineScaffoldAssist;
    private static final EnumMap<OreType, Boolean> mineOreEnabled = new EnumMap<>(OreType.class);
    private static final EnumMap<OreType, Integer> mineTargetCounts = new EnumMap<>(OreType.class);
    private static long autoMineSelectionRevision;
    public static double bridgeLookahead;
    public static int bridgeDownScan;
    public static int bridgeDelayTicks;
    public static boolean bridgeAvoidFeet;
    public static double oreVisualizerRange;
    public static double oreVisualizerBrightness;
    public static boolean oreCountHud;
    private static final EnumMap<OreType, Boolean> oreEnabled = new EnumMap<>(OreType.class);
    private static final EnumMap<OreType, Integer> oreColors = new EnumMap<>(OreType.class);
    public static double meleeRange;
    public static int meleeDelayTicks;
    public static boolean meleePlayers;
    public static boolean meleeHostiles;
    public static boolean meleeAnimals;
    public static boolean meleePeaceful;
    public static boolean meleeAutoWeapon;
    public static boolean meleeModded;
    public static boolean meleeRotate;
    public static boolean meleeMultiTarget;
    public static int meleeMaxTargets;
    public static boolean meleeVisualize;
    public static String meleePriority;
    public static AttackPoint meleeAttackPoint;
    private static final Set<String> meleeExcludedModEntities = new LinkedHashSet<>();
    public static double blinkRange;
    public static double blinkStep;
    public static double blinkAttackDistance;
    public static int blinkPredictTicks;
    public static int blinkDelayTicks;
    public static boolean blinkPlayers;
    public static boolean blinkHostiles;
    public static boolean blinkAnimals;
    public static boolean blinkPeaceful;
    public static boolean blinkModded;
    public static boolean blinkAutoWeapon;
    public static boolean blinkRotate;
    public static boolean blinkMultiTarget;
    public static int blinkMaxTargets;
    public static boolean blinkVisualize;
    public static String blinkPriority;
    public static AttackPoint blinkAttackPoint;
    private static final Set<String> blinkExcludedModEntities = new LinkedHashSet<>();
    public static FlightMode flightMode;
    public static double flightSpeed;
    public static double flightDescentSpeed;
    public static boolean targetSkeleton;
    public static boolean targetBox;
    public static boolean targetRays;
    public static double targetVisualizerRange;
    public static boolean targetCountHud;
    public static HudPosition countHudPosition;

    private static final String[] DEFAULT_GG_MESSAGES = {
        "gg {player}", "good fight {player}", "well played {player}",
        "nice fight {player}", "gg wp {player}"
    };
    private static final String[] DEFAULT_REPLY_MESSAGES = {
        "Hi {player}", "I saw that, {player}.", "Hello {player}",
        "Got it, {player}.", "Thanks, {player}."
    };

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
        autoBridge = configuration.getBoolean("autoBridge", "modules", false, "Place a block before walking off edges.");
        oreVisualizer = configuration.getBoolean("oreVisualizer", "modules", false, "Draw cached ore block outlines.");
        creativeTools = configuration.getBoolean("creativeTools", "modules", true, "Enable creative item and potion commands.");
        meleeAura = configuration.getBoolean("meleeAura", "modules", false, "Automatically attack nearby entities with swords or axes.");
        blinkStrike = configuration.getBoolean("blinkStrike", "modules", false, "Attack through a temporary packet-position excursion and return.");
        flight = configuration.getBoolean("flight", "modules", false, "Packet-assisted survival movement modes.");
        criticals = configuration.getBoolean("criticals", "modules", false, "Send a critical movement sequence before melee attacks.");
        targetVisualizer = configuration.getBoolean("targetVisualizer", "modules", false, "Draw configurable target overlays.");
        panelGraveKeyMigrated = configuration.getBoolean("panelGraveKeyMigrated", "ui", false,
            "One-time migration from the old Right Shift panel key to the grave key.");
        ggMessages = fiveMessages(configuration.getStringList("messages", "autoGg", DEFAULT_GG_MESSAGES, "Messages sent after a detected kill."), DEFAULT_GG_MESSAGES);
        ggMinDelayTicks = configuration.getInt("minDelayTicks", "autoGg", 4, 0, 200, "Minimum delay before sending.");
        ggMaxDelayTicks = configuration.getInt("maxDelayTicks", "autoGg", 24, 0, 200, "Maximum delay before sending.");
        replyTarget = configuration.getString("target", "autoReply", "", "Only messages from this player trigger a reply. Empty accepts anyone.");
        replyMessages = fiveMessages(configuration.getStringList("messages", "autoReply", DEFAULT_REPLY_MESSAGES, "Random automatic replies."), DEFAULT_REPLY_MESSAGES);
        replyCooldownTicks = configuration.getInt("cooldownTicks", "autoReply", 100, 20, 1200, "Minimum delay between replies.");
        mineDelayTicks = configuration.getInt("delayTicks", "autoMine", 2, 0, 40, "Delay after a mined block is confirmed complete.");
        mineBlocks = configuration.getStringList("blocks", "autoMine", new String[] {
            "minecraft:coal_ore", "minecraft:iron_ore", "minecraft:gold_ore",
            "minecraft:redstone_ore", "minecraft:lapis_ore", "minecraft:diamond_ore", "minecraft:emerald_ore"
        }, "Registry names of blocks to mine.");
        minePathRange = configuration.getInt("pathRange", "autoMine", 32, 6, 96, "Maximum range for walking to selected ores.");
        mineManualPauseTicks = configuration.getInt("manualPauseTicks", "autoMine", 30, 0, 100, "Ticks to pause pathing after manual movement input.");
        mineVisualizePath = configuration.getBoolean("visualizePath", "autoMine", true, "Draw the current mining target and planned route.");
        mineScaffoldAssist = configuration.getBoolean("scaffoldAssist", "autoMine", false,
            "Place one stable block under the player when it safely brings an overhead ore into reach.");
        int legacyTargetCount = configuration.getInt("targetCount", "autoMine", 0, 0, 999,
            "Legacy global target count. New configs use per-ore target counts.");
        mineOreEnabled.clear();
        mineTargetCounts.clear();
        for (OreType type : OreType.values()) {
            mineOreEnabled.put(type, configuration.getBoolean(type.key() + "Mine", "autoMine",
                defaultMineOreEnabled(type), "Allow auto mine to target " + type.displayName() + "."));
            mineTargetCounts.put(type, configuration.getInt(type.key() + "TargetCount", "autoMine", legacyTargetCount,
                0, 999, "Number of " + type.displayName() + " blocks to mine before skipping that ore. Zero means unlimited."));
        }
        bridgeLookahead = configuration.getFloat("lookAhead", "autoBridge", 0.95F, 0.30F, 1.60F,
            "Distance ahead of player movement to check for bridge placement.");
        bridgeDownScan = configuration.getInt("downScan", "autoBridge", 2, 1, 4, "How far below the feet auto bridge can place while jumping or falling.");
        bridgeDelayTicks = configuration.getInt("delayTicks", "autoBridge", 1, 0, 10, "Delay between bridge placements.");
        bridgeAvoidFeet = configuration.getBoolean("avoidFeetCollision", "autoBridge", true, "Skip placements that may intersect the player's feet.");
        oreVisualizerRange = configuration.getFloat("range", "oreVisualizer", 150.0F, 16.0F, 500.0F,
            "Maximum ore visualization distance. Only client-loaded chunks can be scanned.");
        oreVisualizerBrightness = configuration.getFloat("brightness", "oreVisualizer", 1.0F, 0.0F, 1.0F,
            "RGB brightness multiplier for ore outlines.");
        oreCountHud = configuration.getBoolean("countHud", "oreVisualizer", false, "Show nearby cached ore count on the HUD.");
        oreEnabled.clear();
        oreColors.clear();
        for (OreType type : OreType.values()) {
            oreEnabled.put(type, configuration.getBoolean(type.key() + "Enabled", "oreVisualizer", true,
                "Draw " + type.displayName() + " blocks."));
            oreColors.put(type, configuration.getInt(type.key() + "Color", "oreVisualizer", type.defaultColor(),
                0, 0xFFFFFF, "RGB outline color for " + type.displayName() + "."));
        }
        meleeRange = configuration.getFloat("range", "meleeAura", 3.0F, 1.0F, 6.0F, "Distance from the player eyes to the nearest point of the target hitbox.");
        meleeDelayTicks = configuration.getInt("delayTicks", "meleeAura", 1, 0, 20, "Extra ticks between attacks after cooldown is ready.");
        meleePlayers = configuration.getBoolean("players", "meleeAura", true, "Target players.");
        meleeHostiles = configuration.getBoolean("hostiles", "meleeAura", true, "Target hostile mobs.");
        meleeAnimals = configuration.getBoolean("animals", "meleeAura", false, "Target animals.");
        meleePeaceful = configuration.getBoolean("peaceful", "meleeAura", false, "Target other peaceful living entities such as villagers and golems.");
        meleeAutoWeapon = configuration.getBoolean("autoWeapon", "meleeAura", true, "Select the strongest sword or axe in the hotbar.");
        meleeModded = configuration.getBoolean("moddedEntities", "meleeAura", false, "Target living entities registered by other mods.");
        meleeRotate = configuration.getBoolean("rotateView", "meleeAura", false, "Turn the local camera toward the current target.");
        meleeMultiTarget = configuration.getBoolean("multiTarget", "meleeAura", false, "Attack multiple sorted targets per cycle.");
        meleeMaxTargets = configuration.getInt("maxTargets", "meleeAura", 3, 1, 50, "Maximum targets attacked per cycle when multi-target is enabled.");
        meleeVisualize = configuration.getBoolean("visualizeTargets", "meleeAura", false, "Draw boxes around selected targets.");
        meleePriority = configuration.getString("priority", "meleeAura", "distance", "Target priority: distance or health.");
        meleeAttackPoint = AttackPoint.fromKey(configuration.getString("attackPoint", "meleeAura", AttackPoint.CHEST.key(), "Target body point: head, chest, legs or feet."));
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
        blinkPeaceful = configuration.getBoolean("peaceful", "blinkStrike", false, "Target other peaceful living entities independently from melee aura.");
        blinkModded = configuration.getBoolean("moddedEntities", "blinkStrike", false, "Target living entities registered by other mods.");
        blinkAutoWeapon = configuration.getBoolean("autoWeapon", "blinkStrike", true, "Select the strongest sword or axe in the hotbar.");
        blinkRotate = configuration.getBoolean("rotateView", "blinkStrike", false, "Turn the local camera toward the current target.");
        blinkMultiTarget = configuration.getBoolean("multiTarget", "blinkStrike", false, "Attack multiple sorted targets per cycle.");
        blinkMaxTargets = configuration.getInt("maxTargets", "blinkStrike", 3, 1, 50, "Maximum targets attacked per cycle when multi-target is enabled.");
        blinkVisualize = configuration.getBoolean("visualizeTargets", "blinkStrike", false, "Draw boxes around selected targets.");
        blinkPriority = configuration.getString("priority", "blinkStrike", "distance", "Target priority: distance or health.");
        blinkAttackPoint = AttackPoint.fromKey(configuration.getString("attackPoint", "blinkStrike", AttackPoint.CHEST.key(), "Target body point: head, chest, legs or feet."));
        blinkExcludedModEntities.clear();
        blinkExcludedModEntities.addAll(Arrays.asList(configuration.getStringList("excludedModEntities", "blinkStrike",
            new String[0], "Mod entity registry names excluded from blink strike.")));
        boolean legacyFlight = configuration.getCategory("flight").containsKey("elytraPackets")
            || configuration.getCategory("flight").containsKey("boatPackets")
            || configuration.getCategory("flight").containsKey("verticalSpeed");
        configuration.getCategory("flight").remove("elytraPackets");
        configuration.getCategory("flight").remove("boatPackets");
        configuration.getCategory("flight").remove("verticalSpeed");
        if (legacyFlight) configuration.getCategory("flight").remove("speed");
        String configuredFlightMode = configuration.getString("mode", "flight", FlightMode.STATIC.key(),
            "WWE Flight mode: static or vanilla.");
        flightMode = FlightMode.fromKey(configuredFlightMode);
        if (!flightMode.key().equalsIgnoreCase(configuredFlightMode)) {
            configuration.get("flight", "mode", FlightMode.STATIC.key()).set(flightMode.key());
        }
        flightSpeed = configuration.getFloat("speed", "flight", 1.0F, 0.0F, 10.0F, "WWE Flight speed.");
        flightDescentSpeed = configuration.getFloat("descentSpeed", "flight", 0.35F, 0.0F, 1.0F,
            "Independent controlled descent speed.");
        targetSkeleton = configuration.getBoolean("skeleton", "targetVisualizer", true, "Draw stick-figure skeletons.");
        targetBox = configuration.getBoolean("box", "targetVisualizer", true, "Draw target bounding boxes.");
        targetRays = configuration.getBoolean("rays", "targetVisualizer", false, "Draw lines from the camera to targets.");
        targetVisualizerRange = configuration.getFloat("range", "targetVisualizer", 150.0F, 3.0F, 500.0F, "Maximum visualization distance.");
        targetCountHud = configuration.getBoolean("countHud", "targetVisualizer", false, "Show nearby living entity count on the HUD.");
        countHudPosition = HudPosition.fromKey(configuration.getString("countHudPosition", "hud", HudPosition.TOP_LEFT.key(), "Shared count HUD position."));
        configuration.get("autoGg", "messages", DEFAULT_GG_MESSAGES).set(ggMessages);
        configuration.get("autoReply", "messages", DEFAULT_REPLY_MESSAGES).set(replyMessages);
        if (configuration.hasChanged()) configuration.save();
        autoMineSelectionRevision++;
    }

    public static void reload() {
        configuration.load();
        sync();
    }

    public static void saveModule(ModuleId id, boolean enabled) {
        configuration.get("modules", id.key(), enabled).set(enabled);
        configuration.save();
    }

    public static void markPanelGraveKeyMigrated() {
        panelGraveKeyMigrated = true;
        saveBoolean("ui", "panelGraveKeyMigrated", true);
        configuration.save();
    }

    public static void saveGgMessages(String[] messages) {
        ggMessages = fiveMessages(messages, DEFAULT_GG_MESSAGES);
        configuration.get("autoGg", "messages", DEFAULT_GG_MESSAGES).set(ggMessages);
        configuration.save();
    }

    public static void saveReplySettings(String target, String[] messages) {
        replyTarget = target == null ? "" : target.trim();
        replyMessages = fiveMessages(messages, DEFAULT_REPLY_MESSAGES);
        configuration.get("autoReply", "target", "").set(replyTarget);
        configuration.get("autoReply", "messages", DEFAULT_REPLY_MESSAGES).set(replyMessages);
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
            throw new IllegalArgumentException(id.displayName() + "没有可调整的攻击距离。");
        }
        configuration.save();
        return value;
    }

    public static double getRange(ModuleId id) {
        if (id == ModuleId.MELEE_AURA) return meleeRange;
        if (id == ModuleId.BLINK_STRIKE) return blinkRange;
        throw new IllegalArgumentException(id.displayName() + "没有可调整的攻击距离。");
    }

    public static double getNumber(ModuleSetting setting) {
        OreType ore = setting.oreType();
        if (ore != null && setting.module() == ModuleId.AUTO_MINE && setting.type() == ModuleSetting.Type.NUMBER) {
            return getMineTargetCount(ore);
        }
        switch (setting) {
            case GG_MIN_DELAY: return ggMinDelayTicks;
            case GG_MAX_DELAY: return ggMaxDelayTicks;
            case REPLY_COOLDOWN: return replyCooldownTicks;
            case MINE_DELAY: return mineDelayTicks;
            case MINE_PATH_RANGE: return minePathRange;
            case MINE_MANUAL_PAUSE: return mineManualPauseTicks;
            case BRIDGE_LOOKAHEAD: return bridgeLookahead;
            case BRIDGE_DOWN_SCAN: return bridgeDownScan;
            case BRIDGE_DELAY: return bridgeDelayTicks;
            case ORE_RANGE: return oreVisualizerRange;
            case ORE_BRIGHTNESS: return oreVisualizerBrightness;
            case MELEE_RANGE: return meleeRange;
            case MELEE_DELAY: return meleeDelayTicks;
            case MELEE_MAX_TARGETS: return meleeMaxTargets;
            case BLINK_RANGE: return blinkRange;
            case BLINK_STEP: return blinkStep;
            case BLINK_ATTACK_DISTANCE: return blinkAttackDistance;
            case BLINK_PREDICT: return blinkPredictTicks;
            case BLINK_DELAY: return blinkDelayTicks;
            case BLINK_MAX_TARGETS: return blinkMaxTargets;
            case FLIGHT_SPEED: return flightSpeed;
            case FLIGHT_DESCENT_SPEED: return flightDescentSpeed;
            case TARGET_RANGE: return targetVisualizerRange;
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
        OreType ore = setting.oreType();
        if (ore != null && setting.module() == ModuleId.AUTO_MINE && setting.type() == ModuleSetting.Type.NUMBER) {
            int count = (int) rounded;
            boolean changed = getMineTargetCount(ore) != count;
            mineTargetCounts.put(ore, count);
            saveInt("autoMine", ore.key() + "TargetCount", count);
            configuration.save();
            if (changed) autoMineSelectionRevision++;
            return count;
        }
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
            case MINE_DELAY: mineDelayTicks = (int) rounded; saveInt("autoMine", "delayTicks", mineDelayTicks); break;
            case MINE_PATH_RANGE:
                int pathRange = (int) rounded;
                if (minePathRange != pathRange) autoMineSelectionRevision++;
                minePathRange = pathRange;
                saveInt("autoMine", "pathRange", minePathRange);
                break;
            case MINE_MANUAL_PAUSE: mineManualPauseTicks = (int) rounded; saveInt("autoMine", "manualPauseTicks", mineManualPauseTicks); break;
            case BRIDGE_LOOKAHEAD: bridgeLookahead = rounded; saveDouble("autoBridge", "lookAhead", rounded); break;
            case BRIDGE_DOWN_SCAN: bridgeDownScan = (int) rounded; saveInt("autoBridge", "downScan", bridgeDownScan); break;
            case BRIDGE_DELAY: bridgeDelayTicks = (int) rounded; saveInt("autoBridge", "delayTicks", bridgeDelayTicks); break;
            case ORE_RANGE: oreVisualizerRange = rounded; saveDouble("oreVisualizer", "range", rounded); break;
            case ORE_BRIGHTNESS: oreVisualizerBrightness = rounded; saveDouble("oreVisualizer", "brightness", rounded); break;
            case MELEE_RANGE: meleeRange = rounded; saveDouble("meleeAura", "range", rounded); break;
            case MELEE_DELAY: meleeDelayTicks = (int) rounded; saveInt("meleeAura", "delayTicks", meleeDelayTicks); break;
            case MELEE_MAX_TARGETS: meleeMaxTargets = (int) rounded; saveInt("meleeAura", "maxTargets", meleeMaxTargets); break;
            case BLINK_RANGE: blinkRange = rounded; saveDouble("blinkStrike", "range", rounded); break;
            case BLINK_STEP: blinkStep = rounded; saveDouble("blinkStrike", "step", rounded); break;
            case BLINK_ATTACK_DISTANCE: blinkAttackDistance = rounded; saveDouble("blinkStrike", "attackDistance", rounded); break;
            case BLINK_PREDICT: blinkPredictTicks = (int) rounded; saveInt("blinkStrike", "predictTicks", blinkPredictTicks); break;
            case BLINK_DELAY: blinkDelayTicks = (int) rounded; saveInt("blinkStrike", "delayTicks", blinkDelayTicks); break;
            case BLINK_MAX_TARGETS: blinkMaxTargets = (int) rounded; saveInt("blinkStrike", "maxTargets", blinkMaxTargets); break;
            case FLIGHT_SPEED: flightSpeed = rounded; saveDouble("flight", "speed", rounded); break;
            case FLIGHT_DESCENT_SPEED: flightDescentSpeed = rounded; saveDouble("flight", "descentSpeed", rounded); break;
            case TARGET_RANGE: targetVisualizerRange = rounded; saveDouble("targetVisualizer", "range", rounded); break;
            default: throw new IllegalArgumentException("Setting is not numeric: " + setting);
        }
        configuration.save();
        return rounded;
    }

    public static boolean getToggle(ModuleSetting setting) {
        OreType ore = setting.oreType();
        if (ore != null && setting.module() == ModuleId.AUTO_MINE) return isMineOreEnabled(ore);
        if (ore != null && setting.type() == ModuleSetting.Type.TOGGLE) return isOreEnabled(ore);
        switch (setting) {
            case MELEE_PLAYERS: return meleePlayers;
            case MELEE_HOSTILES: return meleeHostiles;
            case MELEE_ANIMALS: return meleeAnimals;
            case MELEE_PEACEFUL: return meleePeaceful;
            case MELEE_AUTO_WEAPON: return meleeAutoWeapon;
            case MELEE_MODDED: return meleeModded;
            case MELEE_ROTATE: return meleeRotate;
            case MELEE_MULTI: return meleeMultiTarget;
            case MELEE_VISUALIZE: return meleeVisualize;
            case BLINK_PLAYERS: return blinkPlayers;
            case BLINK_HOSTILES: return blinkHostiles;
            case BLINK_ANIMALS: return blinkAnimals;
            case BLINK_PEACEFUL: return blinkPeaceful;
            case BLINK_MODDED: return blinkModded;
            case BLINK_AUTO_WEAPON: return blinkAutoWeapon;
            case BLINK_ROTATE: return blinkRotate;
            case BLINK_MULTI: return blinkMultiTarget;
            case BLINK_VISUALIZE: return blinkVisualize;
            case MINE_VISUALIZE_PATH: return mineVisualizePath;
            case MINE_SCAFFOLD_ASSIST: return mineScaffoldAssist;
            case BRIDGE_AVOID_FEET: return bridgeAvoidFeet;
            case TARGET_SKELETON: return targetSkeleton;
            case TARGET_BOX: return targetBox;
            case TARGET_RAYS: return targetRays;
            case TARGET_COUNT_HUD: return targetCountHud;
            case ORE_COUNT_HUD: return oreCountHud;
            default: throw new IllegalArgumentException("Setting is not a toggle: " + setting);
        }
    }

    public static boolean toggle(ModuleSetting setting) {
        boolean value = !getToggle(setting);
        OreType ore = setting.oreType();
        if (ore != null && setting.module() == ModuleId.AUTO_MINE) {
            mineOreEnabled.put(ore, value);
            saveBoolean("autoMine", ore.key() + "Mine", value);
            configuration.save();
            autoMineSelectionRevision++;
            return value;
        }
        if (ore != null && setting.type() == ModuleSetting.Type.TOGGLE) {
            oreEnabled.put(ore, value);
            saveBoolean("oreVisualizer", ore.key() + "Enabled", value);
            configuration.save();
            return value;
        }
        switch (setting) {
            case MELEE_PLAYERS: meleePlayers = value; saveBoolean("meleeAura", "players", value); break;
            case MELEE_HOSTILES: meleeHostiles = value; saveBoolean("meleeAura", "hostiles", value); break;
            case MELEE_ANIMALS: meleeAnimals = value; saveBoolean("meleeAura", "animals", value); break;
            case MELEE_PEACEFUL: meleePeaceful = value; saveBoolean("meleeAura", "peaceful", value); break;
            case MELEE_AUTO_WEAPON: meleeAutoWeapon = value; saveBoolean("meleeAura", "autoWeapon", value); break;
            case MELEE_MODDED: meleeModded = value; saveBoolean("meleeAura", "moddedEntities", value); break;
            case MELEE_ROTATE: meleeRotate = value; saveBoolean("meleeAura", "rotateView", value); break;
            case MELEE_MULTI: meleeMultiTarget = value; saveBoolean("meleeAura", "multiTarget", value); break;
            case MELEE_VISUALIZE: meleeVisualize = value; saveBoolean("meleeAura", "visualizeTargets", value); break;
            case BLINK_PLAYERS: blinkPlayers = value; saveBoolean("blinkStrike", "players", value); break;
            case BLINK_HOSTILES: blinkHostiles = value; saveBoolean("blinkStrike", "hostiles", value); break;
            case BLINK_ANIMALS: blinkAnimals = value; saveBoolean("blinkStrike", "animals", value); break;
            case BLINK_PEACEFUL: blinkPeaceful = value; saveBoolean("blinkStrike", "peaceful", value); break;
            case BLINK_MODDED: blinkModded = value; saveBoolean("blinkStrike", "moddedEntities", value); break;
            case BLINK_AUTO_WEAPON: blinkAutoWeapon = value; saveBoolean("blinkStrike", "autoWeapon", value); break;
            case BLINK_ROTATE: blinkRotate = value; saveBoolean("blinkStrike", "rotateView", value); break;
            case BLINK_MULTI: blinkMultiTarget = value; saveBoolean("blinkStrike", "multiTarget", value); break;
            case BLINK_VISUALIZE: blinkVisualize = value; saveBoolean("blinkStrike", "visualizeTargets", value); break;
            case MINE_VISUALIZE_PATH: mineVisualizePath = value; saveBoolean("autoMine", "visualizePath", value); break;
            case MINE_SCAFFOLD_ASSIST: mineScaffoldAssist = value; saveBoolean("autoMine", "scaffoldAssist", value); break;
            case BRIDGE_AVOID_FEET: bridgeAvoidFeet = value; saveBoolean("autoBridge", "avoidFeetCollision", value); break;
            case TARGET_SKELETON: targetSkeleton = value; saveBoolean("targetVisualizer", "skeleton", value); break;
            case TARGET_BOX: targetBox = value; saveBoolean("targetVisualizer", "box", value); break;
            case TARGET_RAYS: targetRays = value; saveBoolean("targetVisualizer", "rays", value); break;
            case TARGET_COUNT_HUD: targetCountHud = value; saveBoolean("targetVisualizer", "countHud", value); break;
            case ORE_COUNT_HUD: oreCountHud = value; saveBoolean("oreVisualizer", "countHud", value); break;
            default: throw new IllegalArgumentException("Setting is not a toggle: " + setting);
        }
        configuration.save();
        return value;
    }

    public static boolean isOreEnabled(OreType type) {
        Boolean enabled = oreEnabled.get(type);
        return enabled == null || enabled;
    }

    public static boolean isMineOreEnabled(OreType type) {
        Boolean enabled = mineOreEnabled.get(type);
        return enabled == null || enabled;
    }

    public static boolean hasEnabledMineOre() {
        for (OreType type : OreType.values()) {
            if (isMineOreEnabled(type)) return true;
        }
        return false;
    }

    public static int getMineTargetCount(OreType type) {
        Integer count = mineTargetCounts.get(type);
        return count == null ? 0 : count;
    }

    public static long autoMineSelectionRevision() {
        return autoMineSelectionRevision;
    }

    public static int getOreColor(OreType type) {
        Integer color = oreColors.get(type);
        return color == null ? type.defaultColor() : color;
    }

    public static int saveOreColor(OreType type, int color) {
        if (type == null || color < 0 || color > 0xFFFFFF) {
            throw new IllegalArgumentException("Color must be a 6-digit RGB value");
        }
        oreColors.put(type, color);
        configuration.get("oreVisualizer", type.key() + "Color", type.defaultColor()).set(color);
        configuration.save();
        return color;
    }

    public static String getChoice(ModuleSetting setting) {
        if (setting == ModuleSetting.MELEE_PRIORITY) return "health".equalsIgnoreCase(meleePriority) ? "血量" : "距离";
        if (setting == ModuleSetting.BLINK_PRIORITY) return "health".equalsIgnoreCase(blinkPriority) ? "血量" : "距离";
        if (setting == ModuleSetting.MELEE_ATTACK_POINT) return meleeAttackPoint.displayName();
        if (setting == ModuleSetting.BLINK_ATTACK_POINT) return blinkAttackPoint.displayName();
        if (setting == ModuleSetting.TARGET_COUNT_POSITION || setting == ModuleSetting.ORE_COUNT_POSITION) return countHudPosition.displayName();
        if (setting == ModuleSetting.FLIGHT_MODE) return flightMode.displayName();
        throw new IllegalArgumentException("Setting is not a choice: " + setting);
    }

    public static String cycleChoice(ModuleSetting setting) {
        if (setting == ModuleSetting.MELEE_PRIORITY) {
            meleePriority = "health".equalsIgnoreCase(meleePriority) ? "distance" : "health";
            configuration.get("meleeAura", "priority", meleePriority).set(meleePriority);
        } else if (setting == ModuleSetting.BLINK_PRIORITY) {
            blinkPriority = "health".equalsIgnoreCase(blinkPriority) ? "distance" : "health";
            configuration.get("blinkStrike", "priority", blinkPriority).set(blinkPriority);
        } else if (setting == ModuleSetting.MELEE_ATTACK_POINT) {
            meleeAttackPoint = AttackPoint.next(meleeAttackPoint);
            configuration.get("meleeAura", "attackPoint", meleeAttackPoint.key()).set(meleeAttackPoint.key());
        } else if (setting == ModuleSetting.BLINK_ATTACK_POINT) {
            blinkAttackPoint = AttackPoint.next(blinkAttackPoint);
            configuration.get("blinkStrike", "attackPoint", blinkAttackPoint.key()).set(blinkAttackPoint.key());
        } else if (setting == ModuleSetting.TARGET_COUNT_POSITION || setting == ModuleSetting.ORE_COUNT_POSITION) {
            countHudPosition = HudPosition.next(countHudPosition);
            configuration.get("hud", "countHudPosition", countHudPosition.key()).set(countHudPosition.key());
        } else if (setting == ModuleSetting.FLIGHT_MODE) {
            flightMode = FlightMode.next(flightMode);
            configuration.get("flight", "mode", flightMode.key()).set(flightMode.key());
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
        throw new IllegalArgumentException(module.displayName() + "没有实体目标列表。");
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

    private static boolean defaultMineOreEnabled(OreType type) {
        for (String name : mineBlocks) {
            if (type.matchesRegistryName(name)) return true;
        }
        return false;
    }

    private static String[] fiveMessages(String[] messages, String[] defaults) {
        String[] result = new String[5];
        for (int i = 0; i < result.length; i++) {
            String value = messages != null && i < messages.length ? messages[i] : defaults[i];
            result[i] = value == null ? "" : value.trim();
        }
        return result;
    }

    private static void requireRange(ModuleId id, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(id.key() + " range must be between " + min + " and " + max);
        }
    }
}
