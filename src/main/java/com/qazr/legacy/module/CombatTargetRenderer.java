package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

public final class CombatTargetRenderer {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;

    public CombatTargetRenderer(ModuleManager modules) {
        this.modules = modules;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (mc.player == null || mc.world == null) return;
        Set<EntityLivingBase> meleeTargets = selectedTargets(ModuleId.MELEE_AURA,
            modules.isEnabled(ModuleId.MELEE_AURA) && ModConfig.meleeVisualize, ModConfig.meleeMultiTarget, ModConfig.meleeMaxTargets);
        Set<EntityLivingBase> blinkTargets = selectedTargets(ModuleId.BLINK_STRIKE,
            modules.isEnabled(ModuleId.BLINK_STRIKE) && ModConfig.blinkVisualize, ModConfig.blinkMultiTarget, ModConfig.blinkMaxTargets);
        List<EntityLivingBase> visualTargets = visualizationTargets();
        if (meleeTargets.isEmpty() && blinkTargets.isEmpty() && visualTargets.isEmpty()) return;

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(2.0F);
        try {
            for (EntityLivingBase target : meleeTargets) drawTarget(target, event.getPartialTicks(), 0.25F, 1.0F, 0.35F);
            for (EntityLivingBase target : blinkTargets) drawTarget(target, event.getPartialTicks(), 1.0F, 0.25F, 0.3F);
            for (EntityLivingBase target : visualTargets) drawAdvancedTarget(target, event.getPartialTicks());
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
        }
    }

    private List<EntityLivingBase> visualizationTargets() {
        if (!modules.isEnabled(ModuleId.TARGET_VISUALIZER)) return java.util.Collections.emptyList();
        return CombatSupport.findVisualizationTargets(mc, ModConfig.targetVisualizerRange, 50);
    }

    private Set<EntityLivingBase> selectedTargets(ModuleId module, boolean enabled, boolean multi, int maxTargets) {
        if (!enabled) return java.util.Collections.emptySet();
        int limit = multi ? maxTargets : 1;
        List<EntityLivingBase> targets = CombatSupport.findTargets(mc, module, limit);
        return new LinkedHashSet<>(targets);
    }

    private void drawTarget(EntityLivingBase target, float partialTicks, float red, float green, float blue) {
        RenderManager render = mc.getRenderManager();
        double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * partialTicks - render.viewerPosX;
        double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * partialTicks - render.viewerPosY;
        double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partialTicks - render.viewerPosZ;
        AxisAlignedBB box = target.getEntityBoundingBox().offset(x - target.posX, y - target.posY, z - target.posZ).grow(0.04);
        RenderGlobal.renderFilledBox(box, red, green, blue, 0.12F);
        RenderGlobal.drawSelectionBoundingBox(box, red, green, blue, 0.95F);
    }

    private void drawAdvancedTarget(EntityLivingBase target, float partialTicks) {
        boolean visible = mc.player.canEntityBeSeen(target);
        float red = visible ? 0.2F : 1.0F;
        float green = visible ? 1.0F : 0.2F;
        float blue = visible ? 0.3F : 0.15F;
        RenderManager render = mc.getRenderManager();
        double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * partialTicks - render.viewerPosX;
        double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * partialTicks - render.viewerPosY;
        double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partialTicks - render.viewerPosZ;
        AxisAlignedBB box = target.getEntityBoundingBox().offset(x - target.posX, y - target.posY, z - target.posZ).grow(0.04);
        if (ModConfig.targetBox) RenderGlobal.drawSelectionBoundingBox(box, red, green, blue, 0.95F);
        if (ModConfig.targetSkeleton) drawSkeleton(target, x, y, z, red, green, blue);
        if (ModConfig.targetRays) drawLine(0.0, 0.0, 0.0, x, y + target.height * 0.5, z, red, green, blue);
    }

    private void drawSkeleton(EntityLivingBase target, double x, double y, double z,
            float red, float green, float blue) {
        double h = Math.max(0.5, target.height);
        double w = Math.max(0.25, target.width);
        double shoulder = h * 0.72;
        double hip = h * 0.30;
        double yaw = Math.toRadians(target.renderYawOffset);
        double sideX = Math.cos(yaw);
        double sideZ = -Math.sin(yaw);
        drawLine(x, y + hip, z, x, y + shoulder, z, red, green, blue);
        drawLine(x, y + shoulder, z, x, y + h * 0.96, z, red, green, blue);
        drawLine(x - sideX * w * 0.42, y + shoulder, z - sideZ * w * 0.42,
            x + sideX * w * 0.42, y + shoulder, z + sideZ * w * 0.42, red, green, blue);
        drawLine(x - sideX * w * 0.42, y + shoulder, z - sideZ * w * 0.42,
            x - sideX * w * 0.58, y + h * 0.38, z - sideZ * w * 0.58, red, green, blue);
        drawLine(x + sideX * w * 0.42, y + shoulder, z + sideZ * w * 0.42,
            x + sideX * w * 0.58, y + h * 0.38, z + sideZ * w * 0.58, red, green, blue);
        drawLine(x, y + hip, z, x - sideX * w * 0.30, y, z - sideZ * w * 0.30, red, green, blue);
        drawLine(x, y + hip, z, x + sideX * w * 0.30, y, z + sideZ * w * 0.30, red, green, blue);
    }

    private void drawLine(double x1, double y1, double z1, double x2, double y2, double z2,
            float red, float green, float blue) {
        GlStateManager.color(red, green, blue, 0.95F);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
        buffer.pos(x1, y1, z1).endVertex();
        buffer.pos(x2, y2, z2).endVertex();
        Tessellator.getInstance().draw();
    }
}
