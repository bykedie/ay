package com.qazr.legacy.config;

import java.io.File;
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
    public static String meleePriority;
    public static double blinkRange;
    public static double blinkStep;
    public static double blinkAttackDistance;
    public static int blinkPredictTicks;
    public static int blinkDelayTicks;

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
        meleePriority = configuration.getString("priority", "meleeAura", "distance", "Target priority: distance or health.");
        blinkRange = configuration.getFloat("range", "blinkStrike", 12.0F, 3.0F, 200.0F, "Maximum target acquisition distance. Twelve blocks fits the vanilla movement and attack checks with default settings.");
        blinkStep = configuration.getFloat("step", "blinkStrike", 4.0F, 1.0F, 9.5F, "Maximum distance represented by each position packet. Vanilla 1.12 rejects movement above roughly 10 blocks per packet.");
        blinkAttackDistance = configuration.getFloat("attackDistance", "blinkStrike", 2.5F, 1.0F, 4.0F, "Spoofed distance from the target when the attack packet is sent.");
        blinkPredictTicks = configuration.getInt("predictTicks", "blinkStrike", 1, 0, 5, "Horizontal target-motion prediction.");
        blinkDelayTicks = configuration.getInt("delayTicks", "blinkStrike", 8, 1, 40, "Additional delay after each attempted strike.");
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

    private static void requireRange(ModuleId id, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(id.key() + " range must be between " + min + " and " + max);
        }
    }
}
