package com.qazr.legacy.util;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;

public final class CreativeItems {
    private CreativeItems() {
    }

    public static String give(String itemName, int count, int meta) {
        Item item = Item.REGISTRY.getObject(new ResourceLocation(itemName));
        if (item == null) return "Unknown item: " + itemName;
        return giveStack(new ItemStack(item, count, meta));
    }

    public static String givePotion(String effectName, int level, int seconds, boolean splash) {
        Potion effect = Potion.REGISTRY.getObject(new ResourceLocation(effectName));
        if (effect == null) return "Unknown potion effect: " + effectName;
        ItemStack stack = new ItemStack(splash ? Items.SPLASH_POTION : Items.POTIONITEM);
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList effects = new NBTTagList();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte("Id", (byte) Potion.getIdFromPotion(effect));
        tag.setByte("Amplifier", (byte) (level - 1));
        tag.setInteger("Duration", seconds * 20);
        tag.setBoolean("ShowParticles", true);
        effects.appendTag(tag);
        root.setTag("CustomPotionEffects", effects);
        stack.setTagCompound(root);
        stack.setStackDisplayName("Qazr " + effectName + " " + level);
        return giveStack(stack);
    }

    private static String giveStack(ItemStack stack) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.playerController == null) return "Not in a world.";
        if (!mc.player.capabilities.isCreativeMode) return "Creative mode is required.";
        int slot = mc.player.inventory.getFirstEmptyStack();
        if (slot < 0) return "Inventory is full.";
        mc.player.inventory.setInventorySlotContents(slot, stack);
        int containerSlot = slot < 9 ? slot + 36 : slot;
        mc.playerController.sendSlotPacket(stack, containerSlot);
        return "Created " + stack.getDisplayName() + " x" + stack.getCount();
    }
}
