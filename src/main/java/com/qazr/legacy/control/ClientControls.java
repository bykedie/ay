package com.qazr.legacy.control;

import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.gui.ModuleControlScreen;
import com.qazr.legacy.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public final class ClientControls {
    private final ModuleManager modules;
    private final KeyBinding menu = new KeyBinding("key.qazr.menu", Keyboard.KEY_RSHIFT, "key.categories.qazr");
    private final KeyBinding melee = new KeyBinding("key.qazr.module.melee", Keyboard.KEY_NONE, "key.categories.qazr");
    private final KeyBinding blink = new KeyBinding("key.qazr.module.blink", Keyboard.KEY_NONE, "key.categories.qazr");
    private final KeyBinding criticals = new KeyBinding("key.qazr.module.criticals", Keyboard.KEY_NONE, "key.categories.qazr");
    private final KeyBinding mine = new KeyBinding("key.qazr.module.mine", Keyboard.KEY_NONE, "key.categories.qazr");

    public ClientControls(ModuleManager modules) {
        this.modules = modules;
    }

    public void register() {
        ClientRegistry.registerKeyBinding(menu);
        ClientRegistry.registerKeyBinding(melee);
        ClientRegistry.registerKeyBinding(blink);
        ClientRegistry.registerKeyBinding(criticals);
        ClientRegistry.registerKeyBinding(mine);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (menu.isPressed()) Minecraft.getMinecraft().displayGuiScreen(new ModuleControlScreen(modules));
        if (melee.isPressed()) toggle(ModuleId.MELEE_AURA);
        if (blink.isPressed()) toggle(ModuleId.BLINK_STRIKE);
        if (criticals.isPressed()) toggle(ModuleId.CRITICALS);
        if (mine.isPressed()) toggle(ModuleId.AUTO_MINE);
    }

    private void toggle(ModuleId id) {
        boolean enabled = modules.toggle(id);
        if (Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(
                "[Qazr] " + I18n.format(id.translationKey()) + " = "
                    + I18n.format(enabled ? "gui.qazr.enabled" : "gui.qazr.disabled")));
        }
    }
}
