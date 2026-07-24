package com.qazr.legacy.control;

import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.gui.ModuleControlScreen;
import com.qazr.legacy.module.ModuleManager;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
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
    private final Map<ModuleId, KeyBinding> moduleKeys = new EnumMap<>(ModuleId.class);

    public ClientControls(ModuleManager modules) {
        this.modules = modules;
        for (ModuleId id : ModuleId.values()) {
            moduleKeys.put(id, new KeyBinding("切换" + id.displayName(), Keyboard.KEY_NONE, CATEGORY));
        }
    }

    public void register() {
        ClientRegistry.registerKeyBinding(menu);
        for (KeyBinding binding : moduleKeys.values()) ClientRegistry.registerKeyBinding(binding);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (menu.isPressed()) {
            Minecraft.getMinecraft().displayGuiScreen(new ModuleControlScreen(modules, this, menu.getKeyCode()));
        }
        for (Map.Entry<ModuleId, KeyBinding> entry : moduleKeys.entrySet()) {
            if (entry.getValue().isPressed()) toggle(entry.getKey());
        }
    }

    public KeyBinding getModuleBinding(ModuleId id) {
        return moduleKeys.get(id);
    }

    public void setModuleKey(ModuleId id, int keyCode) {
        KeyBinding binding = moduleKeys.get(id);
        if (binding == null) return;
        binding.setKeyCode(keyCode);
        KeyBinding.resetKeyBindingArrayAndHash();
        Minecraft.getMinecraft().gameSettings.saveOptions();
    }

    private void toggle(ModuleId id) {
        boolean enabled = modules.toggle(id);
        if (Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(
                "[Qazr] " + id.displayName() + " = " + (enabled ? "已开启" : "已关闭")));
        }
    }
}
