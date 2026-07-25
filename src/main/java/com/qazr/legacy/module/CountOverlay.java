package com.qazr.legacy.module;

import com.qazr.legacy.config.HudPosition;
import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class CountOverlay {
    private static final int PADDING = 6;
    private static final int ITEM_HEIGHT = 9;
    private static final int ITEM_GAP = 6;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private final OreVisualizer oreVisualizer;

    public CountOverlay(ModuleManager modules, OreVisualizer oreVisualizer) {
        this.modules = modules;
        this.oreVisualizer = oreVisualizer;
    }

    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Text event) {
        if (mc.player == null || mc.world == null) return;
        boolean showTargets = modules.isEnabled(ModuleId.TARGET_VISUALIZER) && ModConfig.targetCountHud;
        boolean showOres = modules.isEnabled(ModuleId.ORE_VISUALIZER) && ModConfig.oreCountHud;
        if (!showTargets && !showOres) return;

        String targetLine = showTargets ? "生物 " + targetCount() : null;
        String oreLine = showOres ? "矿物 " + oreVisualizer.countVisibleOres() : null;
        int width = itemWidth(targetLine) + itemWidth(oreLine);
        if (targetLine != null && oreLine != null) width += ITEM_GAP;
        int height = ITEM_HEIGHT + 4;
        ScaledResolution scaled = new ScaledResolution(mc);
        HudPosition position = ModConfig.countHudPosition;
        int x = position.right() ? scaled.getScaledWidth() - width - PADDING : PADDING;
        int y = position.bottom() ? scaled.getScaledHeight() - height - PADDING : PADDING;

        Gui.drawRect(x, y, x + width, y + height, 0x8C111318);
        int itemX = x + 5;
        if (targetLine != null) {
            drawItem(targetLine, itemX, y + 3, 0xFF77DD77);
            itemX += itemWidth(targetLine) + ITEM_GAP;
        }
        if (oreLine != null) drawItem(oreLine, itemX, y + 3, 0xFF4DE7E7);
    }

    private int targetCount() {
        return CombatSupport.countVisualizationTargets(mc, ModConfig.targetVisualizerRange);
    }

    private int textWidth(String value) {
        return value == null ? 0 : mc.fontRenderer.getStringWidth(value);
    }

    private int itemWidth(String value) {
        return value == null ? 0 : textWidth(value) + 12;
    }

    private void drawItem(String text, int x, int y, int color) {
        Gui.drawRect(x, y + 3, x + 4, y + 7, color);
        mc.fontRenderer.drawStringWithShadow(text, x + 7, y + 1, 0xFFFFFFFF);
    }
}
