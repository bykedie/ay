package com.qazr.legacy.gui;

import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleSetting;
import com.qazr.legacy.control.ClientControls;
import com.qazr.legacy.module.ModuleManager;
import java.io.IOException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

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
            ModuleId.MELEE_AURA, ModuleId.BLINK_STRIKE, ModuleId.CRITICALS, ModuleId.TARGET_VISUALIZER),
        new Category("自动化",
            ModuleId.AUTO_GG, ModuleId.AUTO_REPLY, ModuleId.AUTO_MINE, ModuleId.AUTO_BRIDGE, ModuleId.ORE_VISUALIZER),
        new Category("工具", ModuleId.CREATIVE_TOOLS)
    };

    private final ModuleManager modules;
    private final ClientControls controls;
    private final int menuKeyCode;
    private final EnumSet<ModuleId> expanded = EnumSet.noneOf(ModuleId.class);
    private final Map<ModuleSetting, Double> sliderPreview = new EnumMap<>(ModuleSetting.class);
    private final Map<ModuleId, Integer> settingScroll = new EnumMap<>(ModuleId.class);
    private ModuleSetting dragging;
    private ModuleId awaitingKey;
    private ModuleId entitySelector;
    private int entityScroll;
    private final List<ResourceLocation> modEntityTypes = new ArrayList<>();
    private final List<HelpArea> helpAreas = new ArrayList<>();
    private String hoveredHelp;

    public ModuleControlScreen(ModuleManager modules, ClientControls controls, int menuKeyCode) {
        this.modules = modules;
        this.controls = controls;
        this.menuKeyCode = menuKeyCode;
        for (ResourceLocation key : EntityList.getEntityNameList()) {
            Class<? extends net.minecraft.entity.Entity> type = EntityList.getClass(key);
            if (!"minecraft".equals(key.getNamespace()) && type != null
                    && EntityLivingBase.class.isAssignableFrom(type)) modEntityTypes.add(key);
        }
        modEntityTypes.sort(Comparator.comparing(ResourceLocation::toString));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        hoveredHelp = null;
        helpAreas.clear();
        drawRect(0, 0, width, height, COLOR_OVERLAY);
        drawRect(0, 0, width, TITLE_HEIGHT, 0xD916181C);
        drawRect(0, TITLE_HEIGHT - 1, width, TITLE_HEIGHT, COLOR_BORDER);
        fontRenderer.drawStringWithShadow("Voris Hub 控制面板", MARGIN, 10, 0xFFFFFF);

        for (int i = 0; i < CATEGORIES.length; i++) drawCategory(CATEGORIES[i], i, mouseX, mouseY);
        if (entitySelector != null) drawEntitySelector(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (entitySelector == null && hoveredHelp != null) {
            drawHoveringText(fontRenderer.listFormattedStringToWidth(hoveredHelp, Math.min(280, width - 24)), mouseX, mouseY);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (entitySelector != null) {
            clickEntitySelector(mouseX, mouseY, mouseButton);
            return;
        }
        for (HelpArea area : helpAreas) {
            if (inside(mouseX, mouseY, area.x, area.y, area.width, area.height)) return;
        }
        for (int i = 0; i < CATEGORIES.length; i++) {
            ModuleId selected = moduleAt(CATEGORIES[i], i, mouseX, mouseY);
            if (selected != null) {
                if (mouseButton == 0) {
                    modules.toggle(selected);
                } else if (mouseButton == 1) {
                    if (!expanded.remove(selected)) {
                        expanded.clear();
                        expanded.add(selected);
                        settingScroll.put(selected, 0);
                    }
                }
                return;
            }
            ModuleSetting setting = settingAt(CATEGORIES[i], i, mouseX, mouseY);
            if (setting != null) {
                if (mouseButton == 1 && isModEntitySetting(setting)) {
                    entitySelector = setting.module();
                    entityScroll = 0;
                } else if (mouseButton == 0 && setting.type() == ModuleSetting.Type.NUMBER) {
                    dragging = setting;
                    updateSlider(setting, CATEGORIES[i], i, mouseX);
                } else if (mouseButton == 0 && setting.type() == ModuleSetting.Type.TOGGLE) {
                    ModConfig.toggle(setting);
                } else if (mouseButton == 0 && setting.type() == ModuleSetting.Type.TEXT) {
                    mc.displayGuiScreen(new MessageEditorScreen(this, setting.module()));
                } else if (mouseButton == 0 && setting.type() == ModuleSetting.Type.COLOR) {
                    mc.displayGuiScreen(new ColorEditorScreen(this, setting));
                } else if (mouseButton == 0) {
                    ModConfig.cycleChoice(setting);
                }
                return;
            }
            ModuleId keyModule = keyBindingAt(CATEGORIES[i], i, mouseX, mouseY);
            if (keyModule != null && mouseButton == 0) {
                awaitingKey = keyModule;
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
        if (entitySelector != null) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == menuKeyCode) entitySelector = null;
            return;
        }
        if (awaitingKey != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                awaitingKey = null;
            } else {
                controls.setModuleKey(awaitingKey,
                    keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_BACK ? Keyboard.KEY_NONE : keyCode);
                awaitingKey = null;
            }
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == menuKeyCode) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        if (entitySelector == null) {
            int mouseX = Mouse.getEventX() * width / mc.displayWidth;
            int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            scrollSettingsAt(mouseX, mouseY, wheel < 0 ? 1 : -1);
            return;
        }
        int visible = entitySelectorBounds().visibleRows;
        int max = Math.max(0, modEntityTypes.size() - visible);
        entityScroll = Math.max(0, Math.min(max, entityScroll + (wheel < 0 ? 1 : -1)));
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
        int y = drawKeyBinding(id, bounds, startY, mouseX, mouseY);
        if (settings.length == 0) {
            drawRect(bounds.x + 1, y, bounds.x + bounds.width, y + SETTING_HEIGHT, COLOR_SETTING);
            fontRenderer.drawStringWithShadow("暂无其他参数", bounds.x + 14, y + 8, COLOR_DISABLED);
            return y + SETTING_HEIGHT;
        }
        int visible = visibleSettingRows(id);
        int scroll = settingScroll(id, settings.length, visible);
        for (int index = scroll; index < settings.length && index < scroll + visible; index++) {
            ModuleSetting setting = settings[index];
            boolean hovered = inside(mouseX, mouseY, bounds.x, y, bounds.width, SETTING_HEIGHT);
            drawRect(bounds.x + 1, y, bounds.x + bounds.width, y + SETTING_HEIGHT,
                hovered ? COLOR_ROW_HOVER : COLOR_SETTING);
            int textY = y + 4;
            String value = settingValue(setting);
            if (isModEntitySetting(setting)) value += " 右键选择";
            int valueX = bounds.x + bounds.width - 7 - fontRenderer.getStringWidth(value);
            if (setting.type() == ModuleSetting.Type.COLOR) {
                int color = ModConfig.getOreColor(setting.oreType());
                drawRect(valueX - 14, textY - 1, valueX - 2, textY + 10, 0xFFFFFFFF);
                drawRect(valueX - 13, textY, valueX - 3, textY + 9, 0xFF000000 | color);
                valueX -= 16;
            }
            drawSettingLabel(setting, bounds.x + 14, textY, valueX - 8, mouseX, mouseY);
            fontRenderer.drawStringWithShadow(value, bounds.x + bounds.width - 7 - fontRenderer.getStringWidth(value),
                textY, setting.type() == ModuleSetting.Type.TOGGLE && ModConfig.getToggle(setting)
                    ? COLOR_ENABLED : 0xFFFFFFFF);
            if (setting.type() == ModuleSetting.Type.NUMBER) drawSlider(setting, bounds, y);
            y += SETTING_HEIGHT;
        }
        return y;
    }

    private int drawKeyBinding(ModuleId id, Bounds bounds, int y, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, bounds.x, y, bounds.width, SETTING_HEIGHT);
        drawRect(bounds.x + 1, y, bounds.x + bounds.width, y + SETTING_HEIGHT,
            hovered ? COLOR_ROW_HOVER : COLOR_SETTING);
        KeyBinding binding = controls.getModuleBinding(id);
        String value = awaitingKey == id ? "请按键..."
            : binding.getKeyCode() == Keyboard.KEY_NONE ? "未绑定" : Keyboard.getKeyName(binding.getKeyCode());
        int valueX = bounds.x + bounds.width - 7 - fontRenderer.getStringWidth(value);
        drawLabelWithHelp("绑定按键", "点击后按下需要绑定的按键；Delete 或 Backspace 清除，Esc 取消。",
            bounds.x + 14, y + 8, valueX - 8, mouseX, mouseY);
        fontRenderer.drawStringWithShadow(value, valueX,
            y + 8, awaitingKey == id ? COLOR_ENABLED : 0xFFFFFFFF);
        return y + SETTING_HEIGHT;
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

    private void drawEntitySelector(int mouseX, int mouseY) {
        SelectorBounds bounds = entitySelectorBounds();
        drawRect(0, 0, width, height, 0x78000000);
        drawRect(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, 0xF0181A1F);
        drawRect(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + HEADER_HEIGHT, COLOR_HEADER);
        String title = entitySelector.displayName() + "：模组实体类型";
        fontRenderer.drawStringWithShadow(title, bounds.x + 8, bounds.y + 7, 0xFFFFFFFF);
        fontRenderer.drawStringWithShadow("关闭", bounds.x + bounds.width - 8 - fontRenderer.getStringWidth("关闭"),
            bounds.y + 7, COLOR_DISABLED);
        if (modEntityTypes.isEmpty()) {
            fontRenderer.drawStringWithShadow("当前客户端没有注册第三方活体实体", bounds.x + 10,
                bounds.y + HEADER_HEIGHT + 12, COLOR_DISABLED);
            return;
        }
        for (int row = 0; row < bounds.visibleRows && entityScroll + row < modEntityTypes.size(); row++) {
            ResourceLocation key = modEntityTypes.get(entityScroll + row);
            int y = bounds.y + HEADER_HEIGHT + row * ROW_HEIGHT;
            boolean hovered = inside(mouseX, mouseY, bounds.x, y, bounds.width, ROW_HEIGHT);
            boolean enabled = ModConfig.isModEntityEnabled(entitySelector, key.toString());
            drawRect(bounds.x + 1, y, bounds.x + bounds.width - 1, y + ROW_HEIGHT,
                hovered ? COLOR_ROW_HOVER : COLOR_SETTING);
            String name = trimToWidth(key.toString(), bounds.width - 55);
            fontRenderer.drawStringWithShadow(name, bounds.x + 8, y + 6, 0xFFFFFFFF);
            String state = enabled ? "选中" : "忽略";
            fontRenderer.drawStringWithShadow(state, bounds.x + bounds.width - 8 - fontRenderer.getStringWidth(state),
                y + 6, enabled ? COLOR_ENABLED : COLOR_DISABLED);
        }
        String page = Math.min(modEntityTypes.size(), entityScroll + 1) + "-"
            + Math.min(modEntityTypes.size(), entityScroll + bounds.visibleRows) + " / " + modEntityTypes.size();
        fontRenderer.drawStringWithShadow(page, bounds.x + 8, bounds.y + bounds.height - 14, COLOR_DISABLED);
    }

    private void clickEntitySelector(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) return;
        SelectorBounds bounds = entitySelectorBounds();
        if (!inside(mouseX, mouseY, bounds.x, bounds.y, bounds.width, bounds.height)) {
            entitySelector = null;
            return;
        }
        if (inside(mouseX, mouseY, bounds.x, bounds.y, bounds.width, HEADER_HEIGHT)) {
            if (mouseX >= bounds.x + bounds.width - 55) entitySelector = null;
            return;
        }
        int row = (mouseY - bounds.y - HEADER_HEIGHT) / ROW_HEIGHT;
        if (row >= 0 && row < bounds.visibleRows && entityScroll + row < modEntityTypes.size()) {
            ModConfig.toggleModEntity(entitySelector, modEntityTypes.get(entityScroll + row).toString());
        }
    }

    private SelectorBounds entitySelectorBounds() {
        int panelWidth = Math.min(430, width - 40);
        int visibleRows = Math.max(1, Math.min(12, (height - 62) / ROW_HEIGHT));
        int panelHeight = HEADER_HEIGHT + visibleRows * ROW_HEIGHT + 20;
        return new SelectorBounds((width - panelWidth) / 2, Math.max(20, (height - panelHeight) / 2),
            panelWidth, panelHeight, visibleRows);
    }

    private String trimToWidth(String value, int maxWidth) {
        if (fontRenderer.getStringWidth(value) <= maxWidth) return value;
        return fontRenderer.trimStringToWidth(value, Math.max(0, maxWidth - fontRenderer.getStringWidth("..."))) + "...";
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
                y += SETTING_HEIGHT;
                ModuleSetting[] settings = ModuleSetting.forModule(id);
                int visible = visibleSettingRows(id);
                int scroll = settingScroll(id, settings.length, visible);
                for (int settingIndex = scroll; settingIndex < settings.length && settingIndex < scroll + visible; settingIndex++) {
                    ModuleSetting setting = settings[settingIndex];
                    if (inside(mouseX, mouseY, bounds.x, y, bounds.width, SETTING_HEIGHT)) return setting;
                    y += SETTING_HEIGHT;
                }
                if (ModuleSetting.forModule(id).length == 0) y += SETTING_HEIGHT;
            }
        }
        return null;
    }

    private ModuleId keyBindingAt(Category category, int index, int mouseX, int mouseY) {
        Bounds bounds = boundsFor(category, index);
        int y = bounds.y + HEADER_HEIGHT;
        for (ModuleId id : category.modules) {
            y += ROW_HEIGHT;
            if (expanded.contains(id)) {
                if (inside(mouseX, mouseY, bounds.x, y, bounds.width, SETTING_HEIGHT)) return id;
                y += SETTING_HEIGHT + Math.max(1, visibleSettingRows(id)) * SETTING_HEIGHT;
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
        return (1 + Math.max(1, visibleSettingRows(id))) * SETTING_HEIGHT;
    }

    private int visibleSettingRows(ModuleId id) {
        int total = ModuleSetting.forModule(id).length;
        if (total == 0) return 1;
        Category category = categoryFor(id);
        int baseHeight = HEADER_HEIGHT + category.modules.length * ROW_HEIGHT + SETTING_HEIGHT;
        int available = Math.max(SETTING_HEIGHT, height - MARGIN - categoryTop(category) - baseHeight);
        return Math.max(1, Math.min(total, available / SETTING_HEIGHT));
    }

    private int categoryTop(Category target) {
        if (width - MARGIN * 2 >= 390) return TITLE_HEIGHT + GAP;
        int y = TITLE_HEIGHT + GAP;
        for (Category category : CATEGORIES) {
            if (category == target) return y;
            y += HEADER_HEIGHT + category.modules.length * ROW_HEIGHT + GAP;
        }
        return y;
    }

    private int settingScroll(ModuleId id, int total, int visible) {
        int max = Math.max(0, total - visible);
        int value = Math.max(0, Math.min(max, settingScroll.getOrDefault(id, 0)));
        settingScroll.put(id, value);
        return value;
    }

    private void scrollSettingsAt(int mouseX, int mouseY, int direction) {
        for (int i = 0; i < CATEGORIES.length; i++) {
            Bounds bounds = boundsFor(CATEGORIES[i], i);
            if (!inside(mouseX, mouseY, bounds.x, bounds.y, bounds.width, bounds.height)) continue;
            for (ModuleId id : CATEGORIES[i].modules) {
                if (!expanded.contains(id)) continue;
                int total = ModuleSetting.forModule(id).length;
                int visible = visibleSettingRows(id);
                int current = settingScroll(id, total, visible);
                settingScroll.put(id, Math.max(0, Math.min(Math.max(0, total - visible), current + direction)));
                return;
            }
        }
    }

    private static Category categoryFor(ModuleId id) {
        for (Category category : CATEGORIES) {
            if (contains(category.modules, id)) return category;
        }
        throw new IllegalArgumentException("Unknown module category: " + id);
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
        if (setting.type() == ModuleSetting.Type.TEXT) return "点击编辑";
        if (setting.type() == ModuleSetting.Type.COLOR) {
            return String.format(Locale.ROOT, "#%06X", ModConfig.getOreColor(setting.oreType()));
        }
        double value = displayNumber(setting);
        String number = setting.step() >= 1.0 ? Integer.toString((int) Math.round(value))
            : String.format(java.util.Locale.ROOT, "%.1f", value);
        return number + setting.suffix();
    }

    private void drawSettingLabel(ModuleSetting setting, int x, int y, int right, int mouseX, int mouseY) {
        drawLabelWithHelp(setting.label(), setting.description(), x, y, right, mouseX, mouseY);
    }

    private void drawLabelWithHelp(String label, String help, int x, int y, int right, int mouseX, int mouseY) {
        int maxWidth = Math.max(8, right - x - 14);
        String shown = label;
        if (fontRenderer.getStringWidth(shown) > maxWidth) {
            shown = fontRenderer.trimStringToWidth(shown, Math.max(1, maxWidth - fontRenderer.getStringWidth("..."))) + "...";
        }
        fontRenderer.drawStringWithShadow(shown, x, y, 0xFFE2E2E2);
        int helpX = x + fontRenderer.getStringWidth(shown) + 3;
        int helpY = y - 1;
        drawRect(helpX + 2, helpY, helpX + 8, helpY + 1, COLOR_DISABLED);
        drawRect(helpX, helpY + 2, helpX + 1, helpY + 8, COLOR_DISABLED);
        drawRect(helpX + 9, helpY + 2, helpX + 10, helpY + 8, COLOR_DISABLED);
        drawRect(helpX + 2, helpY + 9, helpX + 8, helpY + 10, COLOR_DISABLED);
        fontRenderer.drawStringWithShadow("?", helpX + 2, helpY + 1, 0xFFFFFFFF);
        HelpArea area = new HelpArea(helpX, helpY, 10, 10, help);
        helpAreas.add(area);
        if (inside(mouseX, mouseY, area.x, area.y, area.width, area.height)) hoveredHelp = help;
    }

    private static boolean isModEntitySetting(ModuleSetting setting) {
        return setting == ModuleSetting.MELEE_MODDED || setting == ModuleSetting.BLINK_MODDED;
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

    private static final class SelectorBounds {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int visibleRows;

        private SelectorBounds(int x, int y, int width, int height, int visibleRows) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.visibleRows = visibleRows;
        }
    }

    private static final class HelpArea {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final String help;

        private HelpArea(int x, int y, int width, int height, String help) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.help = help;
        }
    }
}
