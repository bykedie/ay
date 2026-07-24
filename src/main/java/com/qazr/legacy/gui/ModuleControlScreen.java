package com.qazr.legacy.gui;

import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleSetting;
import com.qazr.legacy.module.ModuleManager;
import java.io.IOException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

public final class ModuleControlScreen extends GuiScreen {
    private static final int MARGIN = 10;
    private static final int GAP = 8;
    private static final int TITLE_HEIGHT = 28;
    private static final int HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 21;
    private static final int SETTING_HEIGHT = 24;
    private static final int COLOR_OVERLAY = 0x52000000;
    private static final int COLOR_PANEL = 0xD91B1D21;
    private static final int COLOR_HEADER = 0xE0A7232B;
    private static final int COLOR_ROW_HOVER = 0xCC34373D;
    private static final int COLOR_ROW_ENABLED = 0xCC6E171D;
    private static final int COLOR_BORDER = 0xFFCB333C;
    private static final int COLOR_ENABLED = 0xFF77DD77;
    private static final int COLOR_DISABLED = 0xFFB7B7B7;
    private static final int COLOR_SETTING = 0xE026292E;
    private static final int COLOR_SLIDER = 0xFF555A62;
    private static final int COLOR_SLIDER_FILL = 0xFFCF3C45;

    private static final Category[] CATEGORIES = {
        new Category("战斗",
            ModuleId.MELEE_AURA, ModuleId.BLINK_STRIKE, ModuleId.CRITICALS),
        new Category("自动化",
            ModuleId.AUTO_GG, ModuleId.AUTO_REPLY, ModuleId.AUTO_MINE),
        new Category("工具", ModuleId.CREATIVE_TOOLS)
    };

    private final ModuleManager modules;
    private final int menuKeyCode;
    private final EnumSet<ModuleId> expanded = EnumSet.noneOf(ModuleId.class);
    private final Map<ModuleSetting, Double> sliderPreview = new EnumMap<>(ModuleSetting.class);
    private ModuleSetting dragging;

    public ModuleControlScreen(ModuleManager modules, int menuKeyCode) {
        this.modules = modules;
        this.menuKeyCode = menuKeyCode;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, COLOR_OVERLAY);
        drawRect(0, 0, width, TITLE_HEIGHT, 0xD916181C);
        drawRect(0, TITLE_HEIGHT - 1, width, TITLE_HEIGHT, COLOR_BORDER);
        fontRenderer.drawStringWithShadow("Qazr Legacy 控制面板", MARGIN, 10, 0xFFFFFF);

        for (int i = 0; i < CATEGORIES.length; i++) drawCategory(CATEGORIES[i], i, mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        for (int i = 0; i < CATEGORIES.length; i++) {
            ModuleId selected = moduleAt(CATEGORIES[i], i, mouseX, mouseY);
            if (selected != null) {
                if (mouseButton == 0) {
                    modules.toggle(selected);
                } else if (mouseButton == 1) {
                    if (!expanded.remove(selected)) expanded.add(selected);
                }
                return;
            }
            ModuleSetting setting = settingAt(CATEGORIES[i], i, mouseX, mouseY);
            if (setting != null && mouseButton == 0) {
                if (setting.type() == ModuleSetting.Type.NUMBER) {
                    dragging = setting;
                    updateSlider(setting, CATEGORIES[i], i, mouseX);
                } else if (setting.type() == ModuleSetting.Type.TOGGLE) {
                    ModConfig.toggle(setting);
                } else {
                    ModConfig.cycleChoice(setting);
                }
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (dragging != null && clickedMouseButton == 0) {
            for (int i = 0; i < CATEGORIES.length; i++) {
                if (dragging.module() == null || contains(CATEGORIES[i].modules, dragging.module())) {
                    updateSlider(dragging, CATEGORIES[i], i, mouseX);
                    break;
                }
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        commitSlider();
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void onGuiClosed() {
        commitSlider();
        super.onGuiClosed();
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
        fontRenderer.drawStringWithShadow(category.name, bounds.x + 6, bounds.y + 7, 0xFFFFFF);

        int rowY = bounds.y + HEADER_HEIGHT;
        for (ModuleId id : category.modules) {
            boolean hovered = inside(mouseX, mouseY, bounds.x, rowY, bounds.width, ROW_HEIGHT);
            boolean enabled = modules.isEnabled(id);
            if (enabled) drawRect(bounds.x + 1, rowY, bounds.x + bounds.width, rowY + ROW_HEIGHT, COLOR_ROW_ENABLED);
            else if (hovered) drawRect(bounds.x + 1, rowY, bounds.x + bounds.width, rowY + ROW_HEIGHT, COLOR_ROW_HOVER);

            int textY = rowY + (ROW_HEIGHT - fontRenderer.FONT_HEIGHT) / 2;
            fontRenderer.drawStringWithShadow(id.displayName(), bounds.x + 6, textY, 0xFFFFFF);
            String state = enabled ? "开" : "关";
            int stateColor = enabled ? COLOR_ENABLED : COLOR_DISABLED;
            fontRenderer.drawStringWithShadow(state, bounds.x + bounds.width - 7 - fontRenderer.getStringWidth(state),
                textY, stateColor);
            fontRenderer.drawStringWithShadow(expanded.contains(id) ? "-" : "+",
                bounds.x + bounds.width - 28, textY, COLOR_DISABLED);
            rowY += ROW_HEIGHT;
            if (expanded.contains(id)) rowY = drawSettings(id, bounds, rowY, mouseX, mouseY);
        }
    }

    private int drawSettings(ModuleId id, Bounds bounds, int startY, int mouseX, int mouseY) {
        ModuleSetting[] settings = ModuleSetting.forModule(id);
        if (settings.length == 0) {
            drawRect(bounds.x + 1, startY, bounds.x + bounds.width, startY + SETTING_HEIGHT, COLOR_SETTING);
            fontRenderer.drawStringWithShadow("暂无可调参数", bounds.x + 14, startY + 8, COLOR_DISABLED);
            return startY + SETTING_HEIGHT;
        }
        int y = startY;
        for (ModuleSetting setting : settings) {
            boolean hovered = inside(mouseX, mouseY, bounds.x, y, bounds.width, SETTING_HEIGHT);
            drawRect(bounds.x + 1, y, bounds.x + bounds.width, y + SETTING_HEIGHT,
                hovered ? COLOR_ROW_HOVER : COLOR_SETTING);
            int textY = y + 4;
            fontRenderer.drawStringWithShadow(setting.label(), bounds.x + 14, textY, 0xFFE2E2E2);
            String value = settingValue(setting);
            fontRenderer.drawStringWithShadow(value, bounds.x + bounds.width - 7 - fontRenderer.getStringWidth(value),
                textY, setting.type() == ModuleSetting.Type.TOGGLE && ModConfig.getToggle(setting)
                    ? COLOR_ENABLED : 0xFFFFFFFF);
            if (setting.type() == ModuleSetting.Type.NUMBER) drawSlider(setting, bounds, y);
            y += SETTING_HEIGHT;
        }
        return y;
    }

    private void drawSlider(ModuleSetting setting, Bounds bounds, int y) {
        int left = bounds.x + 14;
        int right = bounds.x + bounds.width - 8;
        int sliderY = y + SETTING_HEIGHT - 5;
        double progress = (displayNumber(setting) - setting.min()) / (setting.max() - setting.min());
        int filled = left + (int) Math.round((right - left) * progress);
        drawRect(left, sliderY, right, sliderY + 2, COLOR_SLIDER);
        drawRect(left, sliderY, filled, sliderY + 2, COLOR_SLIDER_FILL);
        drawRect(filled - 1, sliderY - 2, filled + 2, sliderY + 4, 0xFFFFFFFF);
    }

    private ModuleId moduleAt(Category category, int index, int mouseX, int mouseY) {
        Bounds bounds = boundsFor(category, index);
        int y = bounds.y + HEADER_HEIGHT;
        for (ModuleId id : category.modules) {
            if (inside(mouseX, mouseY, bounds.x, y, bounds.width, ROW_HEIGHT)) return id;
            y += ROW_HEIGHT + expandedHeight(id);
        }
        return null;
    }

    private ModuleSetting settingAt(Category category, int index, int mouseX, int mouseY) {
        Bounds bounds = boundsFor(category, index);
        int y = bounds.y + HEADER_HEIGHT;
        for (ModuleId id : category.modules) {
            y += ROW_HEIGHT;
            if (expanded.contains(id)) {
                for (ModuleSetting setting : ModuleSetting.forModule(id)) {
                    if (inside(mouseX, mouseY, bounds.x, y, bounds.width, SETTING_HEIGHT)) return setting;
                    y += SETTING_HEIGHT;
                }
                if (ModuleSetting.forModule(id).length == 0) y += SETTING_HEIGHT;
            }
        }
        return null;
    }

    private Bounds boundsFor(Category category, int index) {
        int availableWidth = width - MARGIN * 2;
        boolean columns = availableWidth >= 390;
        int panelWidth = columns ? (availableWidth - GAP * (CATEGORIES.length - 1)) / CATEGORIES.length : availableWidth;
        int x = columns ? MARGIN + index * (panelWidth + GAP) : MARGIN;
        int y = columns ? TITLE_HEIGHT + GAP : TITLE_HEIGHT + GAP + stackedOffset(index);
        int height = HEADER_HEIGHT + category.modules.length * ROW_HEIGHT;
        for (ModuleId id : category.modules) height += expandedHeight(id);
        return new Bounds(x, y, panelWidth, height);
    }

    private int stackedOffset(int categoryIndex) {
        int offset = 0;
        for (int i = 0; i < categoryIndex; i++) {
            offset += HEADER_HEIGHT + CATEGORIES[i].modules.length * ROW_HEIGHT + GAP;
            for (ModuleId id : CATEGORIES[i].modules) offset += expandedHeight(id);
        }
        return offset;
    }

    private int expandedHeight(ModuleId id) {
        if (!expanded.contains(id)) return 0;
        return Math.max(1, ModuleSetting.forModule(id).length) * SETTING_HEIGHT;
    }

    private void updateSlider(ModuleSetting setting, Category category, int index, int mouseX) {
        Bounds bounds = boundsFor(category, index);
        int left = bounds.x + 14;
        int right = bounds.x + bounds.width - 8;
        double progress = Math.max(0.0, Math.min(1.0, (double) (mouseX - left) / (right - left)));
        double value = setting.min() + (setting.max() - setting.min()) * progress;
        double rounded = Math.round(value / setting.step()) * setting.step();
        sliderPreview.put(setting, Math.max(setting.min(), Math.min(setting.max(), rounded)));
    }

    private String settingValue(ModuleSetting setting) {
        if (setting.type() == ModuleSetting.Type.TOGGLE) return ModConfig.getToggle(setting) ? "开" : "关";
        if (setting.type() == ModuleSetting.Type.CHOICE) return ModConfig.getChoice(setting);
        double value = displayNumber(setting);
        String number = setting.step() >= 1.0 ? Integer.toString((int) Math.round(value))
            : String.format(java.util.Locale.ROOT, "%.1f", value);
        return number + setting.suffix();
    }

    private double displayNumber(ModuleSetting setting) {
        Double preview = sliderPreview.get(setting);
        return preview == null ? ModConfig.getNumber(setting) : preview;
    }

    private void commitSlider() {
        if (dragging == null) return;
        Double value = sliderPreview.remove(dragging);
        if (value != null) ModConfig.saveNumber(dragging, value);
        dragging = null;
    }

    private static boolean contains(ModuleId[] modules, ModuleId target) {
        for (ModuleId module : modules) if (module == target) return true;
        return false;
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static final class Category {
        private final String name;
        private final ModuleId[] modules;

        private Category(String name, ModuleId... modules) {
            this.name = name;
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
