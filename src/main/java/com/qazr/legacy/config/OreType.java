package com.qazr.legacy.config;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

public enum OreType {
    COAL("coal", "煤矿", 0x555555, "minecraft:coal_ore"),
    IRON("iron", "铁矿", 0xD8AF93, "minecraft:iron_ore"),
    GOLD("gold", "金矿", 0xFFD84A, "minecraft:gold_ore"),
    REDSTONE("redstone", "红石矿", 0xFF3030, "minecraft:redstone_ore", "minecraft:lit_redstone_ore"),
    LAPIS("lapis", "青金石矿", 0x386BFF, "minecraft:lapis_ore"),
    DIAMOND("diamond", "钻石矿", 0x4DE7E7, "minecraft:diamond_ore"),
    EMERALD("emerald", "绿宝石矿", 0x45E06F, "minecraft:emerald_ore"),
    QUARTZ("quartz", "下界石英矿", 0xF2E4D2, "minecraft:quartz_ore");

    private static final Map<ResourceLocation, OreType> BY_REGISTRY_NAME = new HashMap<>();
    private static final Map<Block, OreType> BY_BLOCK = new IdentityHashMap<>();
    private static final Set<Block> NON_ORE_BLOCKS =
        java.util.Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());

    static {
        for (OreType type : values()) {
            for (ResourceLocation name : type.registryNames) BY_REGISTRY_NAME.put(name, type);
        }
    }

    private final String key;
    private final String displayName;
    private final int defaultColor;
    private final ResourceLocation[] registryNames;

    OreType(String key, String displayName, int defaultColor, String... registryNames) {
        this.key = key;
        this.displayName = displayName;
        this.defaultColor = defaultColor;
        this.registryNames = new ResourceLocation[registryNames.length];
        for (int i = 0; i < registryNames.length; i++) this.registryNames[i] = new ResourceLocation(registryNames[i]);
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public int defaultColor() {
        return defaultColor;
    }

    public boolean matchesRegistryName(String name) {
        try {
            ResourceLocation target = new ResourceLocation(name);
            for (ResourceLocation registryName : registryNames) {
                if (registryName.equals(target)) return true;
            }
            return false;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static OreType fromBlock(Block block) {
        if (block == null) return null;
        OreType cached = BY_BLOCK.get(block);
        if (cached != null || NON_ORE_BLOCKS.contains(block)) return cached;
        ResourceLocation registryName = block.getRegistryName();
        if (registryName == null) return null;
        OreType resolved = BY_REGISTRY_NAME.get(registryName);
        if (resolved == null) NON_ORE_BLOCKS.add(block);
        else BY_BLOCK.put(block, resolved);
        return resolved;
    }
}
