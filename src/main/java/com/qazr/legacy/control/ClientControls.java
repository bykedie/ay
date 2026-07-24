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
    private static final String CATEGORY = "Qazr Legacy 功能设置";
    private final ModuleManager modules;
    private final KeyBinding menu = new KeyBinding("打开控制面板", Keyboard.KEY_RSHIFT, CATEGORY);
    private final KeyBinding melee = new KeyBinding("切换自动近战", Keyboard.KEY_NONE, CATEGORY);
    private final KeyBinding blink = new KeyBinding("切换闪现攻击", Keyboard.KEY_NONE, CATEGORY);
    private final KeyBinding criticals = new KeyBinding("切换自动暴击", Keyboard.KEY_NONE, CATEGORY);
    private final KeyBinding mine = new KeyBinding("切换自动挖矿", Keyboard.KEY_NONE, CATEGORY);

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
        if (menu.isPressed()) {
            Minecraft.getMinecraft().displayGuiScreen(new ModuleControlScreen(modules, menu.getKeyCode()));
        }
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
