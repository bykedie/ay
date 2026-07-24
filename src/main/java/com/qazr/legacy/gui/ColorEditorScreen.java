package com.qazr.legacy.gui;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleSetting;
import com.qazr.legacy.config.OreType;
import java.io.IOException;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

public final class ColorEditorScreen extends GuiScreen {
    private final GuiScreen parent;
    private final ModuleSetting setting;
    private GuiTextField colorField;
    private String error = "";

    public ColorEditorScreen(GuiScreen parent, ModuleSetting setting) {
        this.parent = parent;
        this.setting = setting;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        int fieldWidth = Math.min(220, width - 40);
        int left = (width - fieldWidth) / 2;
        colorField = new GuiTextField(0, fontRenderer, left, 68, fieldWidth, 20);
        colorField.setMaxStringLength(7);
        colorField.setText(String.format(Locale.ROOT, "#%06X", ModConfig.getOreColor(setting.oreType())));
        colorField.setFocused(true);
        buttonList.add(new GuiButton(0, width / 2 - 104, 102, 100, 20, "保存"));
        buttonList.add(new GuiButton(1, width / 2 + 4, 102, 100, 20, "取消"));
    }

    @Override
    public void updateScreen() {
        colorField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        OreType ore = setting.oreType();
        drawCenteredString(fontRenderer, ore.displayName() + "方框颜色", width / 2, 14, 0xFFFFFFFF);
        drawCenteredString(fontRenderer, "输入 6 位十六进制 RGB 颜色", width / 2, 32, 0xFFB7B7B7);
        int preview = previewColor();
        drawRect(width / 2 - 24, 45, width / 2 + 24, 61, 0xFFFFFFFF);
        drawRect(width / 2 - 23, 46, width / 2 + 23, 60, 0xFF000000 | preview);
        colorField.drawTextBox();
        if (!error.isEmpty()) drawCenteredString(fontRenderer, error, width / 2, 91, 0xFFFF6666);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        colorField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            save();
            return;
        }
        if (colorField.textboxKeyTyped(typedChar, keyCode)) error = "";
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) save();
        else mc.displayGuiScreen(parent);
    }

    private void save() {
        Integer color = parseColor(colorField.getText());
        if (color == null) {
            error = "颜色格式无效，例如 #4DE7E7";
            return;
        }
        ModConfig.saveOreColor(setting.oreType(), color);
        mc.displayGuiScreen(parent);
    }

    private int previewColor() {
        Integer color = parseColor(colorField.getText());
        return color == null ? ModConfig.getOreColor(setting.oreType()) : color;
    }

    static Integer parseColor(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.startsWith("#")) raw = raw.substring(1);
        if (!raw.matches("[0-9a-fA-F]{6}")) return null;
        return Integer.parseInt(raw, 16);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }
}
