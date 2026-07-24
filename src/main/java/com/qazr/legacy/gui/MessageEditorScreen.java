package com.qazr.legacy.gui;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

public final class MessageEditorScreen extends GuiScreen {
    private final GuiScreen parent;
    private final ModuleId module;
    private final List<GuiTextField> messageFields = new ArrayList<>();
    private GuiTextField targetField;

    public MessageEditorScreen(GuiScreen parent, ModuleId module) {
        this.parent = parent;
        this.module = module;
    }

    @Override
    public void initGui() {
        KeyboardHelper.enableRepeatEvents(true);
        buttonList.clear();
        messageFields.clear();
        int panelWidth = Math.min(500, width - 40);
        int left = (width - panelWidth) / 2;
        int y = module == ModuleId.AUTO_REPLY ? 58 : 36;
        if (module == ModuleId.AUTO_REPLY) {
            targetField = textField(100, left, 34, panelWidth);
            targetField.setText(ModConfig.replyTarget);
        }
        String[] values = module == ModuleId.AUTO_GG ? ModConfig.ggMessages : ModConfig.replyMessages;
        for (int i = 0; i < 5; i++) {
            GuiTextField field = textField(i, left, y + i * 25, panelWidth);
            field.setText(i < values.length ? values[i] : "");
            messageFields.add(field);
        }
        int buttonY = y + 5 * 25 + 6;
        buttonList.add(new GuiButton(0, width / 2 - 104, buttonY, 100, 20, "保存"));
        buttonList.add(new GuiButton(1, width / 2 + 4, buttonY, 100, 20, "取消"));
    }

    private GuiTextField textField(int id, int x, int y, int fieldWidth) {
        GuiTextField field = new GuiTextField(id, fontRenderer, x, y, fieldWidth, 20);
        field.setMaxStringLength(160);
        return field;
    }

    @Override
    public void updateScreen() {
        if (targetField != null) targetField.updateCursorCounter();
        for (GuiTextField field : messageFields) field.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        String title = module == ModuleId.AUTO_GG ? "自动发送 GG：随机消息" : "自动回复：随机消息";
        drawCenteredString(fontRenderer, title, width / 2, 8, 0xFFFFFF);
        if (targetField != null) {
            fontRenderer.drawStringWithShadow("指定玩家（留空代表所有玩家）", targetField.x, 22, 0xFFCFCFCF);
            targetField.drawTextBox();
        }
        for (int i = 0; i < messageFields.size(); i++) {
            GuiTextField field = messageFields.get(i);
            fontRenderer.drawStringWithShadow(Integer.toString(i + 1), field.x - 14, field.y + 6, 0xFFCFCFCF);
            field.drawTextBox();
        }
        fontRenderer.drawStringWithShadow("{player} 会替换成玩家名；空白项不会参与随机发送",
            (width - Math.min(500, width - 40)) / 2, height - 14, 0xFFAAAAAA);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (targetField != null) targetField.mouseClicked(mouseX, mouseY, mouseButton);
        for (GuiTextField field : messageFields) field.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (targetField != null && targetField.textboxKeyTyped(typedChar, keyCode)) return;
        for (GuiTextField field : messageFields) {
            if (field.textboxKeyTyped(typedChar, keyCode)) return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            String[] messages = new String[5];
            for (int i = 0; i < messages.length; i++) messages[i] = messageFields.get(i).getText();
            if (module == ModuleId.AUTO_GG) ModConfig.saveGgMessages(messages);
            else ModConfig.saveReplySettings(targetField.getText(), messages);
        }
        mc.displayGuiScreen(parent);
    }

    @Override
    public void onGuiClosed() {
        KeyboardHelper.enableRepeatEvents(false);
    }

    private static final class KeyboardHelper {
        private static void enableRepeatEvents(boolean enabled) {
            org.lwjgl.input.Keyboard.enableRepeatEvents(enabled);
        }
    }
}
