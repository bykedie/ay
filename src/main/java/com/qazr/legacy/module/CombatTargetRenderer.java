package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

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
        if (meleeTargets.isEmpty() && blinkTargets.isEmpty()) return;

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
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
        }
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
}
