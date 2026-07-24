package com.qazr.legacy.gui;

import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.module.ModuleManager;
import java.io.IOException;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

public final class ModuleControlScreen extends GuiScreen {
    private static final int MARGIN = 10;
    private static final int GAP = 8;
    private static final int TITLE_HEIGHT = 28;
    private static final int HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 21;
    private static final int COLOR_OVERLAY = 0x52000000;
    private static final int COLOR_PANEL = 0xD91B1D21;
    private static final int COLOR_HEADER = 0xE0A7232B;
    private static final int COLOR_ROW_HOVER = 0xCC34373D;
    private static final int COLOR_ROW_ENABLED = 0xCC6E171D;
    private static final int COLOR_BORDER = 0xFFCB333C;
    private static final int COLOR_ENABLED = 0xFF77DD77;
    private static final int COLOR_DISABLED = 0xFFB7B7B7;

    private static final Category[] CATEGORIES = {
        new Category("gui.qazr.category.combat",
            ModuleId.MELEE_AURA, ModuleId.BLINK_STRIKE, ModuleId.CRITICALS),
        new Category("gui.qazr.category.automation",
            ModuleId.AUTO_GG, ModuleId.AUTO_REPLY, ModuleId.AUTO_MINE),
        new Category("gui.qazr.category.tools", ModuleId.CREATIVE_TOOLS)
    };

    private final ModuleManager modules;
    private final int menuKeyCode;

    public ModuleControlScreen(ModuleManager modules, int menuKeyCode) {
        this.modules = modules;
        this.menuKeyCode = menuKeyCode;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, COLOR_OVERLAY);
        drawRect(0, 0, width, TITLE_HEIGHT, 0xD916181C);
        drawRect(0, TITLE_HEIGHT - 1, width, TITLE_HEIGHT, COLOR_BORDER);
        fontRenderer.drawStringWithShadow(I18n.format("gui.qazr.title"), MARGIN, 10, 0xFFFFFF);

        for (int i = 0; i < CATEGORIES.length; i++) drawCategory(CATEGORIES[i], i, mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            for (int i = 0; i < CATEGORIES.length; i++) {
                ModuleId selected = moduleAt(CATEGORIES[i], i, mouseX, mouseY);
                if (selected != null) {
                    modules.toggle(selected);
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == menuKeyCode) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void drawCategory(Category category, int index, int mouseX, int mouseY) {
        Bounds bounds = boundsFor(category, index);
        drawRect(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, COLOR_PANEL);
        drawRect(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + HEADER_HEIGHT, COLOR_HEADER);
        drawHorizontalLine(bounds.x, bounds.x + bounds.width - 1, bounds.y, COLOR_BORDER);
        drawVerticalLine(bounds.x, bounds.y, bounds.y + bounds.height - 1, COLOR_BORDER);
        fontRenderer.drawStringWithShadow(I18n.format(category.nameKey), bounds.x + 6, bounds.y + 7, 0xFFFFFF);

        for (int row = 0; row < category.modules.length; row++) {
            ModuleId id = category.modules[row];
            int rowY = bounds.y + HEADER_HEIGHT + row * ROW_HEIGHT;
            boolean hovered = inside(mouseX, mouseY, bounds.x, rowY, bounds.width, ROW_HEIGHT);
            boolean enabled = modules.isEnabled(id);
            if (enabled) drawRect(bounds.x + 1, rowY, bounds.x + bounds.width, rowY + ROW_HEIGHT, COLOR_ROW_ENABLED);
            else if (hovered) drawRect(bounds.x + 1, rowY, bounds.x + bounds.width, rowY + ROW_HEIGHT, COLOR_ROW_HOVER);

            int textY = rowY + (ROW_HEIGHT - fontRenderer.FONT_HEIGHT) / 2;
            fontRenderer.drawStringWithShadow(I18n.format(id.translationKey()), bounds.x + 6, textY, 0xFFFFFF);
            String state = I18n.format(enabled ? "gui.qazr.on" : "gui.qazr.off");
            int stateColor = enabled ? COLOR_ENABLED : COLOR_DISABLED;
            fontRenderer.drawStringWithShadow(state, bounds.x + bounds.width - 7 - fontRenderer.getStringWidth(state),
                textY, stateColor);
        }
    }

    private ModuleId moduleAt(Category category, int index, int mouseX, int mouseY) {
        Bounds bounds = boundsFor(category, index);
        if (!inside(mouseX, mouseY, bounds.x, bounds.y + HEADER_HEIGHT, bounds.width,
                category.modules.length * ROW_HEIGHT)) return null;
        int row = (mouseY - bounds.y - HEADER_HEIGHT) / ROW_HEIGHT;
        return category.modules[row];
    }

    private Bounds boundsFor(Category category, int index) {
        int availableWidth = width - MARGIN * 2;
        boolean columns = availableWidth >= 390;
        int panelWidth = columns ? (availableWidth - GAP * (CATEGORIES.length - 1)) / CATEGORIES.length : availableWidth;
        int x = columns ? MARGIN + index * (panelWidth + GAP) : MARGIN;
        int y = columns ? TITLE_HEIGHT + GAP : TITLE_HEIGHT + GAP + stackedOffset(index);
        int height = HEADER_HEIGHT + category.modules.length * ROW_HEIGHT;
        return new Bounds(x, y, panelWidth, height);
    }

    private int stackedOffset(int categoryIndex) {
        int offset = 0;
        for (int i = 0; i < categoryIndex; i++) {
            offset += HEADER_HEIGHT + CATEGORIES[i].modules.length * ROW_HEIGHT + GAP;
        }
        return offset;
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static final class Category {
        private final String nameKey;
        private final ModuleId[] modules;

        private Category(String nameKey, ModuleId... modules) {
            this.nameKey = nameKey;
            this.modules = modules;
        }
    }

    private static final class Bounds {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        private Bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
