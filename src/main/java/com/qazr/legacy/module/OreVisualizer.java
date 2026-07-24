package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.config.OreType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

public final class OreVisualizer {
    private static final int SECTIONS_PER_TICK = 2;
    private static final double BOX_INSET = 0.002;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private final Map<Long, List<OreMarker>> markersByChunk = new HashMap<>();
    private final Deque<ScanTask> scanQueue = new ArrayDeque<>();

    public OreVisualizer(ModuleManager modules) {
        this.modules = modules;
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!event.getWorld().isRemote) return;
        Chunk chunk = event.getChunk();
        long key = ChunkPos.asLong(chunk.x, chunk.z);
        removeQueued(key);
        markersByChunk.remove(key);
        scanQueue.addLast(new ScanTask(event.getWorld(), chunk));
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (!event.getWorld().isRemote) return;
        long key = ChunkPos.asLong(event.getChunk().x, event.getChunk().z);
        markersByChunk.remove(key);
        removeQueued(key);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) return;
        markersByChunk.clear();
        scanQueue.clear();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || mc.world == null) return;
        int remaining = SECTIONS_PER_TICK;
        while (remaining-- > 0 && !scanQueue.isEmpty()) {
            ScanTask task = scanQueue.removeFirst();
            if (task.world != mc.world || !task.chunk.isLoaded()) continue;
            if (task.scanNextSection()) markersByChunk.put(task.key, task.markers);
            else scanQueue.addLast(task);
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!modules.isEnabled(ModuleId.ORE_VISUALIZER) || mc.player == null || mc.world == null) return;
        double rangeSq = ModConfig.oreVisualizerRange * ModConfig.oreVisualizerRange;
        double viewerX = mc.getRenderManager().viewerPosX;
        double viewerY = mc.getRenderManager().viewerPosY;
        double viewerZ = mc.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(1.0F);
        try {
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            Map<OreType, Set<Long>> markerSets = markerSetsByType();
            for (List<OreMarker> markers : markersByChunk.values()) {
                for (OreMarker marker : markers) {
                    if (!ModConfig.isOreEnabled(marker.type)) continue;
                    double dx = marker.pos.getX() + 0.5 - mc.player.posX;
                    double dy = marker.pos.getY() + 0.5 - mc.player.posY;
                    double dz = marker.pos.getZ() + 0.5 - mc.player.posZ;
                    if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
                    Set<Long> sameType = markerSets.get(marker.type);
                    if (sameType != null) addBoundaryBox(buffer, marker.pos, sameType, viewerX, viewerY, viewerZ,
                        ModConfig.getOreColor(marker.type));
                }
            }
            Tessellator.getInstance().draw();
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

    private void removeQueued(long key) {
        Iterator<ScanTask> iterator = scanQueue.iterator();
        while (iterator.hasNext()) if (iterator.next().key == key) iterator.remove();
    }

    private Map<OreType, Set<Long>> markerSetsByType() {
        Map<OreType, Set<Long>> result = new EnumMap<>(OreType.class);
        Iterator<Map.Entry<Long, List<OreMarker>>> chunkIterator = markersByChunk.entrySet().iterator();
        while (chunkIterator.hasNext()) {
            List<OreMarker> markers = chunkIterator.next().getValue();
            Iterator<OreMarker> markerIterator = markers.iterator();
            while (markerIterator.hasNext()) {
                OreMarker marker = markerIterator.next();
                IBlockState state = mc.world.getBlockState(marker.pos);
                OreType current = OreType.fromBlock(state.getBlock());
                if (current != marker.type) {
                    markerIterator.remove();
                    continue;
                }
                result.computeIfAbsent(marker.type, ignored -> new HashSet<>()).add(marker.pos.toLong());
            }
            if (markers.isEmpty()) chunkIterator.remove();
        }
        return result;
    }

    private static void addBoundaryBox(BufferBuilder buffer, BlockPos pos, Set<Long> sameType,
            double viewerX, double viewerY, double viewerZ, int color) {
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        addBoundaryBox(new BufferSink(buffer), pos, sameType, viewerX, viewerY, viewerZ, red, green, blue);
    }

    static int boundaryLineCount(Set<BlockPos> positions, BlockPos pos) {
        Set<Long> encoded = new HashSet<>();
        for (BlockPos block : positions) encoded.add(block.toLong());
        CountingBuffer buffer = new CountingBuffer();
        addBoundaryBox(buffer, pos, encoded, 0.0, 0.0, 0.0, 1.0F, 1.0F, 1.0F);
        return buffer.count;
    }

    private static void addBoundaryBox(LineSink sink, BlockPos pos, Set<Long> sameType,
            double viewerX, double viewerY, double viewerZ, float red, float green, float blue) {
        double x1 = pos.getX() - viewerX + BOX_INSET;
        double y1 = pos.getY() - viewerY + BOX_INSET;
        double z1 = pos.getZ() - viewerZ + BOX_INSET;
        double x2 = pos.getX() - viewerX + 1.0 - BOX_INSET;
        double y2 = pos.getY() - viewerY + 1.0 - BOX_INSET;
        double z2 = pos.getZ() - viewerZ + 1.0 - BOX_INSET;
        boolean west = boundary(sameType, pos.add(-1, 0, 0));
        boolean east = boundary(sameType, pos.add(1, 0, 0));
        boolean down = boundary(sameType, pos.add(0, -1, 0));
        boolean up = boundary(sameType, pos.add(0, 1, 0));
        boolean north = boundary(sameType, pos.add(0, 0, -1));
        boolean south = boundary(sameType, pos.add(0, 0, 1));
        if (down && north) line(sink, x1, y1, z1, x2, y1, z1, red, green, blue);
        if (down && south) line(sink, x1, y1, z2, x2, y1, z2, red, green, blue);
        if (up && north) line(sink, x1, y2, z1, x2, y2, z1, red, green, blue);
        if (up && south) line(sink, x1, y2, z2, x2, y2, z2, red, green, blue);
        if (down && west) line(sink, x1, y1, z1, x1, y1, z2, red, green, blue);
        if (down && east) line(sink, x2, y1, z1, x2, y1, z2, red, green, blue);
        if (up && west) line(sink, x1, y2, z1, x1, y2, z2, red, green, blue);
        if (up && east) line(sink, x2, y2, z1, x2, y2, z2, red, green, blue);
        if (west && north) line(sink, x1, y1, z1, x1, y2, z1, red, green, blue);
        if (east && north) line(sink, x2, y1, z1, x2, y2, z1, red, green, blue);
        if (west && south) line(sink, x1, y1, z2, x1, y2, z2, red, green, blue);
        if (east && south) line(sink, x2, y1, z2, x2, y2, z2, red, green, blue);
    }

    private static boolean boundary(Set<Long> sameType, BlockPos neighbor) {
        return !sameType.contains(neighbor.toLong());
    }

    private static void line(LineSink sink, double x1, double y1, double z1,
            double x2, double y2, double z2, float red, float green, float blue) {
        sink.line(x1, y1, z1, x2, y2, z2, red, green, blue);
    }

    private interface LineSink {
        void line(double x1, double y1, double z1, double x2, double y2, double z2,
            float red, float green, float blue);
    }

    private static final class CountingBuffer implements LineSink {
        private int count;

        @Override
        public void line(double x1, double y1, double z1, double x2, double y2, double z2,
                float red, float green, float blue) {
            count++;
        }
    }

    private static final class BufferSink implements LineSink {
        private final BufferBuilder buffer;

        private BufferSink(BufferBuilder buffer) {
            this.buffer = buffer;
        }

        @Override
        public void line(double x1, double y1, double z1, double x2, double y2, double z2,
                float red, float green, float blue) {
            buffer.pos(x1, y1, z1).color(red, green, blue, 0.9F).endVertex();
            buffer.pos(x2, y2, z2).color(red, green, blue, 0.9F).endVertex();
        }
    }

    private static final class ScanTask {
        private final World world;
        private final Chunk chunk;
        private final long key;
        private final List<OreMarker> markers = new ArrayList<>();
        private int sectionIndex;

        private ScanTask(World world, Chunk chunk) {
            this.world = world;
            this.chunk = chunk;
            this.key = ChunkPos.asLong(chunk.x, chunk.z);
        }

        private boolean scanNextSection() {
            ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
            while (sectionIndex < sections.length) {
                ExtendedBlockStorage section = sections[sectionIndex++];
                if (section == null || section.isEmpty()) continue;
                int baseX = chunk.x << 4;
                int baseY = section.getYLocation();
                int baseZ = chunk.z << 4;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            OreType type = OreType.fromBlock(section.get(x, y, z).getBlock());
                            if (type != null) markers.add(new OreMarker(new BlockPos(baseX + x, baseY + y, baseZ + z), type));
                        }
                    }
                }
                return sectionIndex >= sections.length;
            }
            return true;
        }
    }

    private static final class OreMarker {
        private final BlockPos pos;
        private final OreType type;

        private OreMarker(BlockPos pos, OreType type) {
            this.pos = pos;
            this.type = type;
        }
    }
}
