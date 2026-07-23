package com.qazr.legacy.control;

import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public final class ClientControls {
    private final ModuleManager modules;
    private final KeyBinding melee = new KeyBinding("key.qazr.melee", Keyboard.KEY_R, "key.categories.qazr");
    private final KeyBinding criticals = new KeyBinding("key.qazr.criticals", Keyboard.KEY_C, "key.categories.qazr");
    private final KeyBinding mine = new KeyBinding("key.qazr.mine", Keyboard.KEY_M, "key.categories.qazr");

    public ClientControls(ModuleManager modules) {
        this.modules = modules;
    }

    public void register() {
        ClientRegistry.registerKeyBinding(melee);
        ClientRegistry.registerKeyBinding(criticals);
        ClientRegistry.registerKeyBinding(mine);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (melee.isPressed()) toggle(ModuleId.MELEE_AURA);
        if (criticals.isPressed()) toggle(ModuleId.CRITICALS);
        if (mine.isPressed()) toggle(ModuleId.AUTO_MINE);
    }

    private void toggle(ModuleId id) {
        boolean enabled = modules.toggle(id);
        if (Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(
                "[Qazr] " + id.key() + " = " + (enabled ? "on" : "off")));
        }
    }
}
