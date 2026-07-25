package com.qazr.legacy.module;

import com.qazr.legacy.config.ModConfig;
import com.qazr.legacy.config.ModuleId;
import com.qazr.legacy.config.OreType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
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
    private static final int SECTIONS_PER_TICK = 12;
    private static final int VALIDATION_CHUNKS_PER_TICK = 2;
    private static final double BOX_INSET = 0.002;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private final Map<Long, List<OreMarker>> markersByChunk = new HashMap<>();
    private final Map<OreType, Set<Long>> markerSetsByType = new EnumMap<>(OreType.class);
    private final Set<Long> scannedChunks = new HashSet<>();
    private final Deque<ScanTask> scanQueue = new ArrayDeque<>();
    private int validationIndex;
    private int validationDelay;
    private World seededWorld;
    private int seededRadiusChunks;
    private int seededCenterChunkX = Integer.MIN_VALUE;
    private int seededCenterChunkZ = Integer.MIN_VALUE;
    private boolean cacheActive;

    public OreVisualizer(ModuleManager modules) {
        this.modules = modules;
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!event.getWorld().isRemote) return;
        Chunk chunk = event.getChunk();
        long key = ChunkPos.asLong(chunk.x, chunk.z);
        scannedChunks.remove(key);
        removeQueued(key);
        removeChunkMarkers(key);
        if (!cacheNeeded()) return;
        int centerSection = mc.player == null ? 4
            : MathHelper.clamp(MathHelper.floor(mc.player.posY) >> 4, 0, 15);
        scanQueue.addFirst(new ScanTask(event.getWorld(), chunk, centerSection));
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (!event.getWorld().isRemote) return;
        long key = ChunkPos.asLong(event.getChunk().x, event.getChunk().z);
        scannedChunks.remove(key);
        removeChunkMarkers(key);
        removeQueued(key);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) return;
        markersByChunk.clear();
        markerSetsByType.clear();
        scannedChunks.clear();
        scanQueue.clear();
        validationIndex = 0;
        validationDelay = 0;
        seededWorld = null;
        seededRadiusChunks = 0;
        seededCenterChunkX = Integer.MIN_VALUE;
        seededCenterChunkZ = Integer.MIN_VALUE;
        cacheActive = false;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || mc.world == null) return;
        if (!cacheNeeded()) {
            cacheActive = false;
            return;
        }
        if (!cacheActive) {
            cacheActive = true;
            seededWorld = null;
        }
        seedLoadedChunks();
        int remaining = SECTIONS_PER_TICK;
        while (remaining-- > 0 && !scanQueue.isEmpty()) {
            ScanTask task = scanQueue.removeFirst();
            if (task.world != mc.world || !task.chunk.isLoaded()) {
                continue;
            }
            appendChunkMarkers(task.key, task.scanNextSection());
            if (task.isComplete()) {
                scannedChunks.add(task.key);
            } else {
                scanQueue.addLast(task);
            }
        }
        if (validationDelay-- <= 0) {
            validateCachedMarkers(VALIDATION_CHUNKS_PER_TICK);
            validationDelay = 4;
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
        GlStateManager.glLineWidth(0.5F);
        try {
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            for (Map.Entry<Long, List<OreMarker>> entry : markersByChunk.entrySet()) {
                if (!chunkPossiblyInRange(entry.getKey(), mc.player.posX, mc.player.posZ, ModConfig.oreVisualizerRange)) continue;
                List<OreMarker> markers = entry.getValue();
                for (OreMarker marker : markers) {
                    if (!ModConfig.isOreEnabled(marker.type)) continue;
                    if (distanceSq(marker.pos) > rangeSq) continue;
                    Set<Long> sameType = markerSetsByType.get(marker.type);
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

    public int countVisibleOres() {
        if (mc.player == null || mc.world == null) return 0;
        double rangeSq = ModConfig.oreVisualizerRange * ModConfig.oreVisualizerRange;
        int count = 0;
        for (Map.Entry<Long, List<OreMarker>> entry : markersByChunk.entrySet()) {
            if (!chunkPossiblyInRange(entry.getKey(), mc.player.posX, mc.player.posZ, ModConfig.oreVisualizerRange)) continue;
            List<OreMarker> markers = entry.getValue();
            for (OreMarker marker : markers) {
                if (ModConfig.isOreEnabled(marker.type) && distanceSq(marker.pos) <= rangeSq) count++;
            }
        }
        return count;
    }

    public List<CachedOre> cachedMineOres(double range) {
        if (mc.player == null || mc.world == null) return Collections.emptyList();
        double rangeSq = range * range;
        List<CachedOre> result = new ArrayList<>();
        for (Map.Entry<Long, List<OreMarker>> entry : markersByChunk.entrySet()) {
            if (!chunkPossiblyInRange(entry.getKey(), mc.player.posX, mc.player.posZ, range)) continue;
            List<OreMarker> markers = entry.getValue();
            for (OreMarker marker : markers) {
                if (!ModConfig.isMineOreEnabled(marker.type)) continue;
                double distanceSq = distanceSq(marker.pos);
                if (distanceSq <= rangeSq) result.add(new CachedOre(marker.pos, marker.type, distanceSq));
            }
        }
        result.sort(Comparator.comparingDouble(CachedOre::distanceSq));
        return result;
    }

    public void removeMarker(BlockPos pos) {
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        List<OreMarker> markers = markersByChunk.get(key);
        if (markers == null) return;
        Iterator<OreMarker> iterator = markers.iterator();
        while (iterator.hasNext()) {
            OreMarker marker = iterator.next();
            if (!marker.pos.equals(pos)) continue;
            iterator.remove();
            removeTypeMarker(marker);
        }
        if (markers.isEmpty()) markersByChunk.remove(key);
    }

    private boolean cacheNeeded() {
        return modules.isEnabled(ModuleId.ORE_VISUALIZER) || modules.isEnabled(ModuleId.AUTO_MINE);
    }

    private void seedLoadedChunks() {
        if (mc.player == null || mc.world == null) return;
        if (!(mc.world.getChunkProvider() instanceof ChunkProviderClient)) return;
        double cacheRange = effectiveCacheRange(modules.isEnabled(ModuleId.ORE_VISUALIZER),
            ModConfig.oreVisualizerRange, modules.isEnabled(ModuleId.AUTO_MINE), ModConfig.minePathRange);
        int radiusChunks = Math.max(1, (int) Math.ceil(cacheRange / 16.0D));
        ChunkProviderClient provider = (ChunkProviderClient) mc.world.getChunkProvider();
        int centerChunkX = MathHelper.floor(mc.player.posX) >> 4;
        int centerChunkZ = MathHelper.floor(mc.player.posZ) >> 4;
        pruneQueue(centerChunkX, centerChunkZ, radiusChunks);
        if (seededWorld == mc.world && radiusChunks == seededRadiusChunks
                && centerChunkX == seededCenterChunkX && centerChunkZ == seededCenterChunkZ) return;
        List<SeededChunk> chunks = new ArrayList<>();
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                int distanceSq = dx * dx + dz * dz;
                if (distanceSq > radiusChunks * radiusChunks) continue;
                Chunk chunk = provider.getLoadedChunk(centerChunkX + dx, centerChunkZ + dz);
                if (chunk != null) chunks.add(new SeededChunk(chunk, distanceSq));
            }
        }
        chunks.sort(Comparator.comparingInt(chunk -> chunk.distanceSq));
        int centerSection = MathHelper.clamp(MathHelper.floor(mc.player.posY) >> 4, 0, 15);
        for (SeededChunk chunk : chunks) queueCachedChunk(mc.world, chunk.chunk, centerSection);
        prioritizeQueue(centerChunkX, centerChunkZ);
        seededWorld = mc.world;
        seededRadiusChunks = radiusChunks;
        seededCenterChunkX = centerChunkX;
        seededCenterChunkZ = centerChunkZ;
        validationDelay = 0;
    }

    static double effectiveCacheRange(boolean oreEnabled, double oreRange, boolean mineEnabled, double mineRange) {
        return Math.max(oreEnabled ? oreRange : 0.0, mineEnabled ? mineRange : 0.0);
    }

    static int[] sectionOrder(int sectionCount, int centerSection) {
        if (sectionCount <= 0) return new int[0];
        int center = MathHelper.clamp(centerSection, 0, sectionCount - 1);
        int[] order = new int[sectionCount];
        int index = 0;
        order[index++] = center;
        for (int offset = 1; index < sectionCount; offset++) {
            if (center - offset >= 0) order[index++] = center - offset;
            if (index < sectionCount && center + offset < sectionCount) order[index++] = center + offset;
        }
        return order;
    }

    private void prioritizeQueue(int centerChunkX, int centerChunkZ) {
        List<ScanTask> tasks = new ArrayList<>(scanQueue);
        tasks.sort(Comparator.comparingInt(task -> task.distanceSq(centerChunkX, centerChunkZ)));
        scanQueue.clear();
        scanQueue.addAll(tasks);
    }

    private void pruneQueue(int centerChunkX, int centerChunkZ, int radiusChunks) {
        int maxDistanceSq = radiusChunks * radiusChunks;
        Iterator<ScanTask> iterator = scanQueue.iterator();
        while (iterator.hasNext()) {
            ScanTask task = iterator.next();
            if (task.distanceSq(centerChunkX, centerChunkZ) <= maxDistanceSq) continue;
            removeChunkMarkers(task.key);
            scannedChunks.remove(task.key);
            iterator.remove();
        }
    }

    private void queueCachedChunk(World world, Chunk chunk, int centerSection) {
        long key = ChunkPos.asLong(chunk.x, chunk.z);
        if (scannedChunks.contains(key) || isQueued(key)) return;
        scanQueue.addLast(new ScanTask(world, chunk, centerSection));
    }

    private boolean isQueued(long key) {
        for (ScanTask task : scanQueue) if (task.key == key) return true;
        return false;
    }

    private double distanceSq(BlockPos pos) {
        double dx = pos.getX() + 0.5 - mc.player.posX;
        double dy = pos.getY() + 0.5 - mc.player.posY;
        double dz = pos.getZ() + 0.5 - mc.player.posZ;
        return dx * dx + dy * dy + dz * dz;
    }

    static boolean chunkPossiblyInRange(long key, double playerX, double playerZ, double range) {
        int chunkX = (int) key;
        int chunkZ = (int) (key >> 32);
        double minX = chunkX * 16.0;
        double maxX = minX + 16.0;
        double minZ = chunkZ * 16.0;
        double maxZ = minZ + 16.0;
        double dx = playerX < minX ? minX - playerX : playerX > maxX ? playerX - maxX : 0.0;
        double dz = playerZ < minZ ? minZ - playerZ : playerZ > maxZ ? playerZ - maxZ : 0.0;
        double padded = range + 1.5;
        return dx * dx + dz * dz <= padded * padded;
    }

    private void appendChunkMarkers(long key, List<OreMarker> markers) {
        if (markers.isEmpty()) return;
        List<OreMarker> stored = markersByChunk.computeIfAbsent(key, ignored -> new ArrayList<>());
        stored.addAll(markers);
        for (OreMarker marker : markers) addTypeMarker(marker);
    }

    private void removeChunkMarkers(long key) {
        List<OreMarker> markers = markersByChunk.remove(key);
        if (markers == null) return;
        for (OreMarker marker : markers) removeTypeMarker(marker);
    }

    private void addTypeMarker(OreMarker marker) {
        markerSetsByType.computeIfAbsent(marker.type, ignored -> new HashSet<>()).add(marker.pos.toLong());
    }

    private void removeTypeMarker(OreMarker marker) {
        Set<Long> markers = markerSetsByType.get(marker.type);
        if (markers == null) return;
        markers.remove(marker.pos.toLong());
        if (markers.isEmpty()) markerSetsByType.remove(marker.type);
    }

    private void validateCachedMarkers(int chunkBudget) {
        if (markersByChunk.isEmpty()) return;
        List<Long> keys = new ArrayList<>(markersByChunk.keySet());
        for (int i = 0; i < chunkBudget && !keys.isEmpty(); i++) {
            if (validationIndex >= keys.size()) validationIndex = 0;
            validateChunk(keys.get(validationIndex++));
        }
    }

    private void validateChunk(long key) {
        List<OreMarker> markers = markersByChunk.get(key);
        if (markers == null) return;
        Iterator<OreMarker> iterator = markers.iterator();
        while (iterator.hasNext()) {
            OreMarker marker = iterator.next();
            IBlockState state = mc.world.getBlockState(marker.pos);
            if (OreType.fromBlock(state.getBlock()) == marker.type) continue;
            iterator.remove();
            removeTypeMarker(marker);
        }
        if (markers.isEmpty()) markersByChunk.remove(key);
    }

    private void removeQueued(long key) {
        Iterator<ScanTask> iterator = scanQueue.iterator();
        while (iterator.hasNext()) if (iterator.next().key == key) iterator.remove();
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

    public static final class CachedOre {
        private final BlockPos pos;
        private final OreType type;
        private final double distanceSq;

        private CachedOre(BlockPos pos, OreType type, double distanceSq) {
            this.pos = pos;
            this.type = type;
            this.distanceSq = distanceSq;
        }

        public BlockPos pos() {
            return pos;
        }

        public OreType type() {
            return type;
        }

        public double distanceSq() {
            return distanceSq;
        }
    }

    private static final class ScanTask {
        private final World world;
        private final Chunk chunk;
        private final long key;
        private final List<OreMarker> sectionMarkers = new ArrayList<>();
        private final int[] sectionOrder;
        private int sectionCursor;

        private ScanTask(World world, Chunk chunk, int centerSection) {
            this.world = world;
            this.chunk = chunk;
            this.key = ChunkPos.asLong(chunk.x, chunk.z);
            this.sectionOrder = OreVisualizer.sectionOrder(chunk.getBlockStorageArray().length, centerSection);
        }

        private List<OreMarker> scanNextSection() {
            sectionMarkers.clear();
            ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
            while (sectionCursor < sectionOrder.length) {
                ExtendedBlockStorage section = sections[sectionOrder[sectionCursor++]];
                if (section == null || section.isEmpty()) continue;
                int baseX = chunk.x << 4;
                int baseY = section.getYLocation();
                int baseZ = chunk.z << 4;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            OreType type = OreType.fromBlock(section.get(x, y, z).getBlock());
                            if (type != null) sectionMarkers.add(new OreMarker(new BlockPos(baseX + x, baseY + y, baseZ + z), type));
                        }
                    }
                }
                return sectionMarkers;
            }
            return sectionMarkers;
        }

        private boolean isComplete() {
            return sectionCursor >= sectionOrder.length;
        }

        private int distanceSq(int centerChunkX, int centerChunkZ) {
            int dx = chunk.x - centerChunkX;
            int dz = chunk.z - centerChunkZ;
            return dx * dx + dz * dz;
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

    private static final class SeededChunk {
        private final Chunk chunk;
        private final int distanceSq;

        private SeededChunk(Chunk chunk, int distanceSq) {
            this.chunk = chunk;
            this.distanceSq = distanceSq;
        }
    }
}
