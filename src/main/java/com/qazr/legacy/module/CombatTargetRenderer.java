package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelChicken;
import net.minecraft.client.model.ModelHorse;
import net.minecraft.client.model.ModelQuadruped;
import net.minecraft.client.model.ModelSpider;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderLivingBase;
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
        GlStateManager.glLineWidth(0.5F);
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
        if (ModConfig.targetSkeleton) drawSkeleton(target, x, y, z, partialTicks, red, green, blue);
        if (ModConfig.targetRays) drawLine(0.0, 0.0, 0.0, x, y + target.height * 0.5, z, red, green, blue);
    }

    private void drawSkeleton(EntityLivingBase target, double x, double y, double z, float partialTicks,
            float red, float green, float blue) {
        float bodyYaw = interpolateAngle(target.prevRenderYawOffset, target.renderYawOffset, partialTicks);
        float headYaw = interpolateAngle(target.prevRotationYawHead, target.rotationYawHead, partialTicks);
        SkeletonType type = skeletonType(mainModel(target));
        float facingYaw = facingYaw(type, bodyYaw, headYaw);
        SkeletonBasis basis = new SkeletonBasis(x, y, z, Math.max(0.5, target.height),
            Math.max(0.25, target.width), facingYaw, headYaw, red, green, blue);
        switch (skeletonType(mainModel(target))) {
            case HUMANOID: drawHumanoidSkeleton(basis); break;
            case QUADRUPED: drawQuadrupedSkeleton(basis, false); break;
            case HORSE: drawQuadrupedSkeleton(basis, true); break;
            case SPIDER: drawSpiderSkeleton(basis); break;
            case BIRD: drawBirdSkeleton(basis); break;
            case CREEPER: drawCreeperSkeleton(basis); break;
            case SEGMENTED: drawSegmentedSkeleton(basis); break;
            case AQUATIC: drawAquaticSkeleton(basis); break;
            default: drawGenericSkeleton(basis);
        }
    }

    private ModelBase mainModel(EntityLivingBase target) {
        Render<?> renderer = mc.getRenderManager().getEntityRenderObject(target);
        return renderer instanceof RenderLivingBase ? ((RenderLivingBase<?>) renderer).getMainModel() : null;
    }

    static float facingYaw(SkeletonType type, float bodyYaw, float headYaw) {
        if (type == SkeletonType.QUADRUPED || type == SkeletonType.HORSE) return headYaw;
        return bodyYaw;
    }

    static SkeletonType skeletonType(ModelBase model) {
        if (model == null) return SkeletonType.GENERIC;
        if (model instanceof ModelHorse) return SkeletonType.HORSE;
        if (model instanceof ModelSpider) return SkeletonType.SPIDER;
        if (model instanceof ModelChicken) return SkeletonType.BIRD;
        if (model instanceof ModelBiped) return SkeletonType.HUMANOID;
        if (model instanceof ModelQuadruped) return SkeletonType.QUADRUPED;
        String name = model.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        if (name.contains("horse")) return SkeletonType.HORSE;
        if (name.contains("spider")) return SkeletonType.SPIDER;
        if (name.contains("chicken") || name.contains("parrot")) return SkeletonType.BIRD;
        if (name.contains("cow") || name.contains("sheep") || name.contains("pig")
                || name.contains("wolf") || name.contains("ocelot") || name.contains("rabbit")
                || name.contains("bear") || name.contains("llama")) return SkeletonType.QUADRUPED;
        if (name.contains("villager") || name.contains("witch") || name.contains("illager")
                || name.contains("golem") || name.contains("snowman")) return SkeletonType.HUMANOID;
        if (name.contains("creeper")) return SkeletonType.CREEPER;
        if (name.contains("silverfish") || name.contains("endermite")) return SkeletonType.SEGMENTED;
        if (name.contains("squid") || name.contains("guardian")) return SkeletonType.AQUATIC;
        return SkeletonType.GENERIC;
    }

    private void drawHumanoidSkeleton(SkeletonBasis b) {
        localLine(b, 0, 0.30, 0, 0, 0.72, 0);
        localLine(b, 0, 0.72, 0, 0, 0.96, 0);
        localLine(b, -0.42, 0.72, 0, 0.42, 0.72, 0);
        localLine(b, -0.42, 0.72, 0, -0.58, 0.38, 0);
        localLine(b, 0.42, 0.72, 0, 0.58, 0.38, 0);
        localLine(b, 0, 0.30, 0, -0.30, 0, 0);
        localLine(b, 0, 0.30, 0, 0.30, 0, 0);
    }

    private void drawQuadrupedSkeleton(SkeletonBasis b, boolean horse) {
        double back = horse ? -0.72 : -0.58;
        double front = horse ? 0.66 : 0.58;
        double bodyY = horse ? 0.58 : 0.60;
        localLine(b, 0, bodyY, back, 0, bodyY, front);
        localHeadLine(b, 0, bodyY, front, 0, horse ? 0.88 : 0.78, horse ? 0.78 : 0.70);
        headLine(b, 0, horse ? 0.88 : 0.78, horse ? 0.78 : 0.70, 0, horse ? 0.91 : 0.80, horse ? 1.00 : 0.88);
        double[] ends = {back + 0.10, front - 0.10};
        for (double longitudinal : ends) {
            for (double side : new double[] {-0.32, 0.32}) {
                localLine(b, side, bodyY, longitudinal, side * 1.12, 0.30, longitudinal);
                localLine(b, side * 1.12, 0.30, longitudinal, side, 0, longitudinal + (horse ? 0.06 : 0));
            }
        }
    }

    private void drawSpiderSkeleton(SkeletonBasis b) {
        localLine(b, 0, 0.48, -0.45, 0, 0.48, 0.48);
        double[] positions = {-0.34, -0.12, 0.12, 0.34};
        for (int i = 0; i < positions.length; i++) {
            double forward = positions[i];
            double sweep = (i - 1.5) * 0.13;
            for (double side : new double[] {-1.0, 1.0}) {
                localLine(b, side * 0.16, 0.46, forward, side * 0.45, 0.38, forward + sweep);
                localLine(b, side * 0.45, 0.38, forward + sweep, side * 0.66, 0.12, forward + sweep * 1.6);
            }
        }
    }

    private void drawBirdSkeleton(SkeletonBasis b) {
        localLine(b, 0, 0.35, -0.18, 0, 0.66, 0.18);
        localLine(b, 0, 0.66, 0.18, 0, 0.88, 0.28);
        localLine(b, -0.12, 0.54, 0, -0.58, 0.40, -0.06);
        localLine(b, 0.12, 0.54, 0, 0.58, 0.40, -0.06);
        localLine(b, -0.16, 0.34, -0.08, -0.16, 0, 0.02);
        localLine(b, 0.16, 0.34, -0.08, 0.16, 0, 0.02);
    }

    private void drawCreeperSkeleton(SkeletonBasis b) {
        localLine(b, 0, 0.20, 0, 0, 0.88, 0);
        for (double side : new double[] {-0.28, 0.28}) {
            localLine(b, 0, 0.24, -0.12, side, 0, -0.28);
            localLine(b, 0, 0.24, 0.12, side, 0, 0.28);
        }
    }

    private void drawSegmentedSkeleton(SkeletonBasis b) {
        localLine(b, 0, 0.42, -0.62, 0, 0.42, 0.62);
        for (double forward : new double[] {-0.48, -0.24, 0, 0.24, 0.48}) {
            localLine(b, -0.35, 0.38, forward, 0.35, 0.38, forward);
        }
    }

    private void drawAquaticSkeleton(SkeletonBasis b) {
        localLine(b, 0, 0.22, 0, 0, 0.88, 0);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0 * i / 8.0;
            localLine(b, 0, 0.28, 0, Math.cos(angle) * 0.42, 0, Math.sin(angle) * 0.42);
        }
    }

    private void drawGenericSkeleton(SkeletonBasis b) {
        localLine(b, 0, 0.08, 0, 0, 0.92, 0);
        localLine(b, -0.46, 0.50, 0, 0.46, 0.50, 0);
        localLine(b, 0, 0.50, -0.46, 0, 0.50, 0.46);
    }

    private void localLine(SkeletonBasis b, double side1, double height1, double forward1,
            double side2, double height2, double forward2) {
        drawLine(b.x(side1, forward1), b.y(height1), b.z(side1, forward1),
            b.x(side2, forward2), b.y(height2), b.z(side2, forward2), b.red, b.green, b.blue);
    }

    private void localHeadLine(SkeletonBasis b, double side1, double height1, double forward1,
            double side2, double height2, double forward2) {
        drawLine(b.x(side1, forward1), b.y(height1), b.z(side1, forward1),
            b.headX(side2, forward2), b.y(height2), b.headZ(side2, forward2), b.red, b.green, b.blue);
    }

    private void headLine(SkeletonBasis b, double side1, double height1, double forward1,
            double side2, double height2, double forward2) {
        drawLine(b.headX(side1, forward1), b.y(height1), b.headZ(side1, forward1),
            b.headX(side2, forward2), b.y(height2), b.headZ(side2, forward2), b.red, b.green, b.blue);
    }

    static float interpolateAngle(float previous, float current, float partialTicks) {
        return previous + net.minecraft.util.math.MathHelper.wrapDegrees(current - previous) * partialTicks;
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

    enum SkeletonType {
        HUMANOID, QUADRUPED, HORSE, SPIDER, BIRD, CREEPER, SEGMENTED, AQUATIC, GENERIC
    }

    private static final class SkeletonBasis {
        private final double baseX;
        private final double baseY;
        private final double baseZ;
        private final double height;
        private final double width;
        private final double sideX;
        private final double sideZ;
        private final double forwardX;
        private final double forwardZ;
        private final double headSideX;
        private final double headSideZ;
        private final double headForwardX;
        private final double headForwardZ;
        private final float red;
        private final float green;
        private final float blue;

        private SkeletonBasis(double x, double y, double z, double height, double width, float yaw, float headYaw,
                float red, float green, float blue) {
            double angle = Math.toRadians(yaw);
            double headAngle = Math.toRadians(headYaw);
            this.baseX = x;
            this.baseY = y;
            this.baseZ = z;
            this.height = height;
            this.width = width;
            this.sideX = Math.cos(angle);
            this.sideZ = -Math.sin(angle);
            this.forwardX = Math.sin(angle);
            this.forwardZ = Math.cos(angle);
            this.headSideX = Math.cos(headAngle);
            this.headSideZ = -Math.sin(headAngle);
            this.headForwardX = Math.sin(headAngle);
            this.headForwardZ = Math.cos(headAngle);
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        private double x(double side, double forward) {
            return baseX + sideX * side * width + forwardX * forward * width;
        }

        private double y(double normalized) {
            return baseY + normalized * height;
        }

        private double z(double side, double forward) {
            return baseZ + sideZ * side * width + forwardZ * forward * width;
        }

        private double headX(double side, double forward) {
            return baseX + headSideX * side * width + headForwardX * forward * width;
        }

        private double headZ(double side, double forward) {
            return baseZ + headSideZ * side * width + headForwardZ * forward * width;
        }
    }
}
