package com.qazr.legacy.gui;

import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.module.ModuleManager;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

public final class ModuleControlScreen extends GuiScreen {
    private static final int MODULE_BUTTON_BASE = 100;
    private final ModuleManager modules;

    public ModuleControlScreen(ModuleManager modules) {
        this.modules = modules;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int buttonWidth = Math.min(220, width - 40);
        int totalHeight = (ModuleId.values().length + 1) * 24;
        int startY = Math.max(30, (height - totalHeight) / 2);
        for (ModuleId id : ModuleId.values()) {
            buttonList.add(new GuiButton(MODULE_BUTTON_BASE + id.ordinal(), (width - buttonWidth) / 2,
                startY + id.ordinal() * 24, buttonWidth, 20, label(id)));
        }
        buttonList.add(new GuiButton(0, (width - buttonWidth) / 2,
            startY + ModuleId.values().length * 24, buttonWidth, 20, I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            mc.displayGuiScreen(null);
            return;
        }
        int ordinal = button.id - MODULE_BUTTON_BASE;
        if (ordinal < 0 || ordinal >= ModuleId.values().length) return;
        modules.toggle(ModuleId.values()[ordinal]);
        refreshLabels();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, I18n.format("gui.qazr.title"), width / 2,
            Math.max(10, buttonList.get(0).y - 20), 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void refreshLabels() {
        for (ModuleId id : ModuleId.values()) {
            buttonList.get(id.ordinal()).displayString = label(id);
        }
    }

    private String label(ModuleId id) {
        String state = I18n.format(modules.isEnabled(id) ? "gui.qazr.enabled" : "gui.qazr.disabled");
        return I18n.format(id.translationKey()) + "：" + state;
    }
}
