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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;
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
    private static final int VISUALIZER_SECTIONS_PER_TICK = 12;
    private static final int AUTO_MINE_SECTIONS_PER_TICK = 2;
    private static final int SECTION_BLOCK_COUNT = 16 * 16 * 16;
    private static final int VISUALIZER_BLOCKS_PER_TICK = SECTION_BLOCK_COUNT * 4;
    private static final int AUTO_MINE_BLOCKS_PER_TICK = SECTION_BLOCK_COUNT;
    private static final int VALIDATION_MARKERS_PER_PASS = 128;
    private static final double BOX_INSET = 0.002;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager modules;
    private final Map<Long, List<OreMarker>> markersByChunk = new HashMap<>();
    private final Map<OreType, Set<Long>> markerSetsByType = new EnumMap<>(OreType.class);
    private final Set<Long> scannedChunks = new HashSet<>();
    private final Deque<ScanTask> scanQueue = new ArrayDeque<>();
    private final Set<Long> queuedChunks = new HashSet<>();
    private final Map<Long, ValidationTask> validationTasks = new LinkedHashMap<>();
    private int validationDelay;
    private World seededWorld;
    private int seededRadiusChunks;
    private double seededRange = -1.0;
    private int seededCenterChunkX = Integer.MIN_VALUE;
    private int seededCenterChunkZ = Integer.MIN_VALUE;
    private boolean cacheActive;
    private int cachedVisibleOreCount;
    private int cachedVisibleOreCountTick = Integer.MIN_VALUE;
    private long markerRevision;

    public OreVisualizer(ModuleManager modules) {
        this.modules = modules;
    }

    public void reloadCache() {
        if (hasCacheState()) clearCache();
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
        ScanTask task = new ScanTask(event.getWorld(), chunk, centerSection);
        if (mc.player == null) {
            scanQueue.addFirst(task);
            queuedChunks.add(key);
        } else {
            int centerChunkX = MathHelper.floor(mc.player.posX) >> 4;
            int centerChunkZ = MathHelper.floor(mc.player.posZ) >> 4;
            double cacheRange = effectiveCacheRange(
                modules.isEnabled(ModuleId.ORE_VISUALIZER), ModConfig.oreVisualizerRange,
                modules.isEnabled(ModuleId.AUTO_MINE), ModConfig.minePathRange);
            if (!chunkCouldEnterRange(key, centerChunkX, centerChunkZ, cacheRange)) return;
            requeueScanTask(task, MathHelper.floor(mc.player.posX) >> 4,
                MathHelper.floor(mc.player.posZ) >> 4);
        }
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
        queuedChunks.clear();
        validationTasks.clear();
        validationDelay = 0;
        seededWorld = null;
        seededRadiusChunks = 0;
        seededRange = -1.0;
        seededCenterChunkX = Integer.MIN_VALUE;
        seededCenterChunkZ = Integer.MIN_VALUE;
        cacheActive = false;
        invalidateVisibleOreCount();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || mc.world == null) return;
        if (!cacheNeeded()) {
            if (hasCacheState()) clearCache();
            return;
        }
        if (!cacheActive) {
            cacheActive = true;
            seededWorld = null;
        }
        seedLoadedChunks();
        boolean autoMineEnabled = modules.isEnabled(ModuleId.AUTO_MINE);
        int remainingTasks = scanBudget(autoMineEnabled);
        int remainingBlocks = scanBlockBudget(autoMineEnabled);
        while (remainingTasks-- > 0 && remainingBlocks > 0 && !scanQueue.isEmpty()) {
            ScanTask task = scanQueue.removeFirst();
            queuedChunks.remove(task.key);
            if (task.world != mc.world || !task.chunk.isLoaded()) {
                continue;
            }
            appendChunkMarkers(task.key, task.scanNextBlocks(remainingBlocks));
            remainingBlocks -= task.lastScanChecks;
            if (task.isComplete()) {
                scannedChunks.add(task.key);
            } else {
                requeueScanTask(task, seededCenterChunkX, seededCenterChunkZ);
            }
        }
        if (validationDelay-- <= 0) {
            validateCachedMarkers(VALIDATION_MARKERS_PER_PASS);
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
        int currentTick = mc.player.ticksExisted;
        if (reuseVisibleOreCount(cachedVisibleOreCountTick, currentTick)) {
            return cachedVisibleOreCount;
        }
        double rangeSq = ModConfig.oreVisualizerRange * ModConfig.oreVisualizerRange;
        int count = 0;
        for (Map.Entry<Long, List<OreMarker>> entry : markersByChunk.entrySet()) {
            if (!chunkPossiblyInRange(entry.getKey(), mc.player.posX, mc.player.posZ, ModConfig.oreVisualizerRange)) continue;
            List<OreMarker> markers = entry.getValue();
            for (OreMarker marker : markers) {
                if (ModConfig.isOreEnabled(marker.type) && distanceSq(marker.pos) <= rangeSq) count++;
            }
        }
        cachedVisibleOreCount = count;
        cachedVisibleOreCountTick = currentTick;
        return cachedVisibleOreCount;
    }

    public long markerRevision() {
        return markerRevision;
    }

    static boolean reuseVisibleOreCount(int cachedTick, int currentTick) {
        return cachedTick == currentTick;
    }

    public List<CachedOre> cachedMineOres(double range) {
        return cachedMineOres(range, Integer.MAX_VALUE);
    }

    public List<CachedOre> cachedMineOres(double range, int limit) {
        return cachedMineOres(range, limit, type -> true);
    }

    public List<CachedOre> cachedMineOres(double range, int limit, Predicate<OreType> typeFilter) {
        return cachedMineOres(range, limit, typeFilter, pos -> true);
    }

    public List<CachedOre> cachedMineOres(double range, int limit, Predicate<OreType> typeFilter,
            Predicate<BlockPos> positionFilter) {
        if (mc.player == null || mc.world == null) return Collections.emptyList();
        if (limit <= 0) return Collections.emptyList();
        double rangeSq = range * range;
        EnumMap<OreType, Boolean> eligibleTypes = new EnumMap<>(OreType.class);
        for (OreType type : OreType.values()) {
            eligibleTypes.put(type, mineTypeEligible(ModConfig.isMineOreEnabled(type),
                typeFilter == null || typeFilter.test(type)));
        }
        PriorityQueue<CachedOre> nearest = new PriorityQueue<>((left, right) ->
            compareCachedOres(right, left));
        for (Map.Entry<Long, List<OreMarker>> entry : markersByChunk.entrySet()) {
            double chunkDistanceSq = chunkHorizontalDistanceSq(
                entry.getKey(), mc.player.posX, mc.player.posZ);
            double farthestDistanceSq = nearest.isEmpty()
                ? Double.POSITIVE_INFINITY : nearest.peek().distanceSq();
            if (chunkCannotImproveNearest(chunkDistanceSq, rangeSq, nearest.size(), limit,
                    farthestDistanceSq)) continue;
            List<OreMarker> markers = entry.getValue();
            for (OreMarker marker : markers) {
                if (!eligibleTypes.getOrDefault(marker.type, false)) continue;
                double distanceSq = distanceSq(marker.pos);
                if (distanceSq > rangeSq) continue;
                double farthestCandidateDistanceSq = nearest.isEmpty()
                    ? Double.POSITIVE_INFINITY : nearest.peek().distanceSq();
                if (distanceCannotImproveNearest(distanceSq, nearest.size(), limit,
                        farthestCandidateDistanceSq)) continue;
                if (positionFilter != null && !positionFilter.test(marker.pos)) continue;
                if (nearest.size() < limit) {
                    nearest.add(new CachedOre(marker.pos, marker.type, distanceSq));
                } else if (candidatePrecedesFarthest(distanceSq, marker.pos, nearest.peek())) {
                    nearest.remove();
                    nearest.add(new CachedOre(marker.pos, marker.type, distanceSq));
                }
            }
        }
        List<CachedOre> result = new ArrayList<>(nearest);
        result.sort(OreVisualizer::compareCachedOres);
        return result;
    }

    static boolean mineTypeEligible(boolean configured, boolean quotaAvailable) {
        return configured && quotaAvailable;
    }

    static int compareCachedOres(CachedOre left, CachedOre right) {
        int distance = Double.compare(left.distanceSq(), right.distanceSq());
        return distance != 0 ? distance : Long.compare(left.pos().toLong(), right.pos().toLong());
    }

    static boolean candidatePrecedesFarthest(double distanceSq, BlockPos pos, CachedOre farthest) {
        int distance = Double.compare(distanceSq, farthest.distanceSq());
        return distance != 0
            ? distance < 0 : Long.compare(pos.toLong(), farthest.pos().toLong()) < 0;
    }

    public void removeMarker(BlockPos pos) {
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        List<OreMarker> markers = markersByChunk.get(key);
        if (markers == null) return;
        Iterator<OreMarker> iterator = markers.iterator();
        boolean removed = false;
        while (iterator.hasNext()) {
            OreMarker marker = iterator.next();
            if (!marker.pos.equals(pos)) continue;
            iterator.remove();
            removeTypeMarker(marker);
            removed = true;
        }
        if (removed) invalidateVisibleOreCount();
        if (markers.isEmpty()) {
            markersByChunk.remove(key);
            validationTasks.remove(key);
        }
    }

    public void restoreMarker(BlockPos pos, OreType type) {
        if (pos == null || type == null) return;
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        if (!markerCacheOwnsChunk(key)) return;
        List<OreMarker> markers = markersByChunk.computeIfAbsent(key, ignored -> new ArrayList<>());
        if (!mergeMarker(markers, new OreMarker(pos.toImmutable(), type))) return;
        queueValidation(key);
        invalidateVisibleOreCount();
    }

    public void reconcileMarker(BlockPos pos, OreType actualType) {
        if (actualType == null) {
            removeMarker(pos);
        } else {
            restoreMarker(pos, actualType);
        }
    }

    static boolean markerRestoreNeeded(OreType expected, int matchingMarkers, int markersAtPosition) {
        return expected != null && (matchingMarkers != 1 || markersAtPosition != 1);
    }

    private boolean markerCacheOwnsChunk(long key) {
        return markerCacheOwnsChunk(scannedChunks.contains(key), queuedChunks.contains(key),
            markersByChunk.containsKey(key));
    }

    static boolean markerCacheOwnsChunk(boolean scanned, boolean queued, boolean hasMarkers) {
        return scanned || queued || hasMarkers;
    }

    private boolean cacheNeeded() {
        return modules.isEnabled(ModuleId.ORE_VISUALIZER) || modules.isEnabled(ModuleId.AUTO_MINE);
    }

    private void seedLoadedChunks() {
        if (mc.player == null || mc.world == null) return;
        if (!(mc.world.getChunkProvider() instanceof ChunkProviderClient)) return;
        double cacheRange = effectiveCacheRange(modules.isEnabled(ModuleId.ORE_VISUALIZER),
            ModConfig.oreVisualizerRange, modules.isEnabled(ModuleId.AUTO_MINE), ModConfig.minePathRange);
        int radiusChunks = chunkSearchRadius(cacheRange);
        ChunkProviderClient provider = (ChunkProviderClient) mc.world.getChunkProvider();
        int centerChunkX = MathHelper.floor(mc.player.posX) >> 4;
        int centerChunkZ = MathHelper.floor(mc.player.posZ) >> 4;
        if (sameSeedState(seededWorld == mc.world, seededRadiusChunks, seededRange,
                seededCenterChunkX, seededCenterChunkZ, radiusChunks, cacheRange,
                centerChunkX, centerChunkZ)) return;
        pruneQueue(centerChunkX, centerChunkZ, cacheRange);
        pruneScannedChunks(centerChunkX, centerChunkZ, cacheRange);
        int centerSection = MathHelper.clamp(MathHelper.floor(mc.player.posY) >> 4, 0, 15);
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                long key = ChunkPos.asLong(centerChunkX + dx, centerChunkZ + dz);
                if (!chunkCouldEnterRange(key, centerChunkX, centerChunkZ, cacheRange)) continue;
                Chunk chunk = provider.getLoadedChunk(centerChunkX + dx, centerChunkZ + dz);
                if (chunk != null) queueCachedChunk(mc.world, chunk, centerSection);
            }
        }
        prioritizeQueue(centerChunkX, centerChunkZ);
        seededWorld = mc.world;
        seededRadiusChunks = radiusChunks;
        seededRange = cacheRange;
        seededCenterChunkX = centerChunkX;
        seededCenterChunkZ = centerChunkZ;
        validationDelay = 0;
    }

    static double effectiveCacheRange(boolean oreEnabled, double oreRange, boolean mineEnabled, double mineRange) {
        return Math.max(oreEnabled ? oreRange : 0.0, mineEnabled ? mineRange : 0.0);
    }

    static int scanBudget(boolean autoMineEnabled) {
        return autoMineEnabled ? AUTO_MINE_SECTIONS_PER_TICK : VISUALIZER_SECTIONS_PER_TICK;
    }

    static int scanBlockBudget(boolean autoMineEnabled) {
        return autoMineEnabled ? AUTO_MINE_BLOCKS_PER_TICK : VISUALIZER_BLOCKS_PER_TICK;
    }

    static int scanSliceChecks(int blockCursor, int blockCount, int budget) {
        int start = MathHelper.clamp(blockCursor, 0, Math.max(0, blockCount));
        return Math.min(Math.max(0, budget), Math.max(0, blockCount - start));
    }

    static boolean sameSeedState(boolean sameWorld, int previousRadius, double previousRange,
            int previousChunkX, int previousChunkZ, int radius, double range,
            int chunkX, int chunkZ) {
        return sameWorld && previousRadius == radius && Double.compare(previousRange, range) == 0
            && previousChunkX == chunkX && previousChunkZ == chunkZ;
    }

    static int chunkSearchRadius(double range) {
        return Math.max(1, (int) Math.ceil(Math.max(0.0, range) / 16.0D) + 1);
    }

    static boolean chunkCouldEnterRange(long key, int centerChunkX, int centerChunkZ, double range) {
        int chunkX = (int) key;
        int chunkZ = (int) (key >> 32);
        double dx = Math.max(0, Math.abs(chunkX - centerChunkX) - 1) * 16.0D;
        double dz = Math.max(0, Math.abs(chunkZ - centerChunkZ) - 1) * 16.0D;
        double padded = Math.max(0.0, range) + 1.5D;
        return dx * dx + dz * dz <= padded * padded;
    }

    private void clearCache() {
        markersByChunk.clear();
        markerSetsByType.clear();
        scannedChunks.clear();
        scanQueue.clear();
        queuedChunks.clear();
        validationTasks.clear();
        validationDelay = 0;
        seededWorld = null;
        seededRadiusChunks = 0;
        seededRange = -1.0;
        seededCenterChunkX = Integer.MIN_VALUE;
        seededCenterChunkZ = Integer.MIN_VALUE;
        cacheActive = false;
        invalidateVisibleOreCount();
    }

    private boolean hasCacheState() {
        return cacheActive || !markersByChunk.isEmpty() || !markerSetsByType.isEmpty()
            || !scannedChunks.isEmpty() || !scanQueue.isEmpty() || !queuedChunks.isEmpty();
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

    private void requeueScanTask(ScanTask task, int centerChunkX, int centerChunkZ) {
        if (!queuedChunks.add(task.key)) return;
        int taskDistanceSq = task.distanceSq(centerChunkX, centerChunkZ);
        int queued = scanQueue.size();
        boolean inserted = false;
        for (int i = 0; i < queued; i++) {
            ScanTask next = scanQueue.removeFirst();
            if (!inserted && !scanTaskPrecedesResumed(
                    next.distanceSq(centerChunkX, centerChunkZ), taskDistanceSq)) {
                scanQueue.addLast(task);
                inserted = true;
            }
            scanQueue.addLast(next);
        }
        if (!inserted) scanQueue.addLast(task);
    }

    static boolean scanTaskPrecedesResumed(int queuedDistanceSq, int resumedDistanceSq) {
        return queuedDistanceSq <= resumedDistanceSq;
    }

    private void pruneQueue(int centerChunkX, int centerChunkZ, double range) {
        Iterator<ScanTask> iterator = scanQueue.iterator();
        while (iterator.hasNext()) {
            ScanTask task = iterator.next();
            if (chunkCouldEnterRange(task.key, centerChunkX, centerChunkZ, range)) continue;
            removeChunkMarkers(task.key);
            scannedChunks.remove(task.key);
            queuedChunks.remove(task.key);
            iterator.remove();
        }
    }

    private void pruneScannedChunks(int centerChunkX, int centerChunkZ, double range) {
        Iterator<Long> iterator = scannedChunks.iterator();
        while (iterator.hasNext()) {
            long key = iterator.next();
            if (chunkCouldEnterRange(key, centerChunkX, centerChunkZ, range)) continue;
            iterator.remove();
            removeChunkMarkers(key);
        }
    }

    private void queueCachedChunk(World world, Chunk chunk, int centerSection) {
        long key = ChunkPos.asLong(chunk.x, chunk.z);
        if (scannedChunks.contains(key) || !queuedChunks.add(key)) return;
        scanQueue.addLast(new ScanTask(world, chunk, centerSection));
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

    static double chunkHorizontalDistanceSq(long key, double playerX, double playerZ) {
        int chunkX = (int) key;
        int chunkZ = (int) (key >> 32);
        double minX = chunkX * 16.0 + 0.5;
        double maxX = minX + 15.0;
        double minZ = chunkZ * 16.0 + 0.5;
        double maxZ = minZ + 15.0;
        double dx = playerX < minX ? minX - playerX : playerX > maxX ? playerX - maxX : 0.0;
        double dz = playerZ < minZ ? minZ - playerZ : playerZ > maxZ ? playerZ - maxZ : 0.0;
        return dx * dx + dz * dz;
    }

    static boolean chunkCannotImproveNearest(double chunkDistanceSq, double rangeSq,
            int candidateCount, int limit, double farthestDistanceSq) {
        return chunkDistanceSq > rangeSq
            || distanceCannotImproveNearest(
                chunkDistanceSq, candidateCount, limit, farthestDistanceSq);
    }

    static boolean distanceCannotImproveNearest(double distanceSq, int candidateCount,
            int limit, double farthestDistanceSq) {
        return candidateCount >= limit && distanceSq > farthestDistanceSq;
    }

    private void appendChunkMarkers(long key, List<OreMarker> markers) {
        if (markers.isEmpty()) return;
        List<OreMarker> stored = markersByChunk.computeIfAbsent(key, ignored -> new ArrayList<>());
        if (!mergeScannedMarkers(stored, markers)) return;
        queueValidation(key);
        invalidateVisibleOreCount();
    }

    private boolean mergeScannedMarkers(List<OreMarker> stored, List<OreMarker> scanned) {
        Map<Long, OreMarker> merged = new LinkedHashMap<>();
        Map<Long, OreType> storedTypes = new LinkedHashMap<>();
        for (OreMarker marker : stored) {
            merged.put(marker.pos.toLong(), marker);
            storedTypes.put(marker.pos.toLong(), marker.type);
        }
        for (OreMarker marker : scanned) {
            merged.put(marker.pos.toLong(), marker);
        }
        Map<Long, OreType> mergedTypes = new LinkedHashMap<>();
        for (Map.Entry<Long, OreMarker> entry : merged.entrySet()) {
            mergedTypes.put(entry.getKey(), entry.getValue().type);
        }
        if (!markerMergeChanged(stored.size(), storedTypes, mergedTypes)) return false;
        for (OreMarker marker : stored) removeTypeMarker(marker);
        stored.clear();
        stored.addAll(merged.values());
        for (OreMarker marker : stored) addTypeMarker(marker);
        return true;
    }

    static boolean markerMergeChanged(int storedCount, Map<Long, OreType> storedTypes,
            Map<Long, OreType> mergedTypes) {
        return storedTypes == null || mergedTypes == null
            || storedCount != mergedTypes.size() || !storedTypes.equals(mergedTypes);
    }

    private boolean mergeMarker(List<OreMarker> stored, OreMarker desired) {
        int markersAtPosition = 0;
        int matchingMarkers = 0;
        for (OreMarker marker : stored) {
            if (!marker.pos.equals(desired.pos)) continue;
            markersAtPosition++;
            if (marker.type == desired.type) matchingMarkers++;
        }
        if (!markerRestoreNeeded(desired.type, matchingMarkers, markersAtPosition)) return false;
        Iterator<OreMarker> iterator = stored.iterator();
        while (iterator.hasNext()) {
            OreMarker marker = iterator.next();
            if (!marker.pos.equals(desired.pos)) continue;
            iterator.remove();
            removeTypeMarker(marker);
        }
        stored.add(desired);
        addTypeMarker(desired);
        return true;
    }

    private void removeChunkMarkers(long key) {
        List<OreMarker> markers = markersByChunk.remove(key);
        validationTasks.remove(key);
        if (markers == null) return;
        for (OreMarker marker : markers) removeTypeMarker(marker);
        invalidateVisibleOreCount();
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

    private void queueValidation(long key) {
        if (validationTasks.containsKey(key)) return;
        validationTasks.put(key, new ValidationTask(key));
    }

    private void validateCachedMarkers(int markerBudget) {
        int tasksRemaining = validationTaskVisitLimit(validationTasks.size(), markerBudget);
        while (markerBudget > 0 && tasksRemaining-- > 0 && !validationTasks.isEmpty()) {
            Iterator<Map.Entry<Long, ValidationTask>> iterator =
                validationTasks.entrySet().iterator();
            Map.Entry<Long, ValidationTask> entry = iterator.next();
            ValidationTask task = entry.getValue();
            iterator.remove();
            List<OreMarker> markers = markersByChunk.get(task.key);
            if (markers == null || markers.isEmpty()) continue;
            if (task.markerIndex >= markers.size()) task.markerIndex = 0;
            int checks = validationChecksForSlice(markers.size(), task.markerIndex, markerBudget);
            int checked = 0;
            boolean removed = false;
            while (checked < checks && task.markerIndex < markers.size()) {
                OreMarker marker = markers.get(task.markerIndex);
                IBlockState state = mc.world.getBlockState(marker.pos);
                OreType actual = OreType.fromBlock(state.getBlock());
                if (actual == marker.type) {
                    task.markerIndex++;
                } else if (actual != null) {
                    removeTypeMarker(marker);
                    OreMarker replacement = new OreMarker(marker.pos, actual);
                    markers.set(task.markerIndex, replacement);
                    addTypeMarker(replacement);
                    task.markerIndex++;
                    removed = true;
                } else {
                    markers.remove(task.markerIndex);
                    removeTypeMarker(marker);
                    removed = true;
                }
                checked++;
            }
            markerBudget -= checked;
            if (removed) invalidateVisibleOreCount();
            if (markers.isEmpty()) {
                markersByChunk.remove(task.key);
            } else {
                if (task.markerIndex >= markers.size()) task.markerIndex = 0;
                validationTasks.put(task.key, task);
            }
        }
    }

    private void invalidateVisibleOreCount() {
        cachedVisibleOreCountTick = Integer.MIN_VALUE;
        markerRevision++;
    }

    static int validationChecksForSlice(int markerCount, int markerIndex, int budget) {
        if (markerCount <= 0 || budget <= 0) return 0;
        int start = MathHelper.clamp(markerIndex, 0, markerCount);
        return Math.min(budget, markerCount - start);
    }

    static int validationTaskVisitLimit(int queueSize, int markerBudget) {
        return Math.min(Math.max(0, queueSize), Math.max(0, markerBudget));
    }

    private void removeQueued(long key) {
        Iterator<ScanTask> iterator = scanQueue.iterator();
        while (iterator.hasNext()) if (iterator.next().key == key) iterator.remove();
        queuedChunks.remove(key);
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

        CachedOre(BlockPos pos, OreType type, double distanceSq) {
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
        private int blockCursor;
        private int lastScanChecks;

        private ScanTask(World world, Chunk chunk, int centerSection) {
            this.world = world;
            this.chunk = chunk;
            this.key = ChunkPos.asLong(chunk.x, chunk.z);
            this.sectionOrder = OreVisualizer.sectionOrder(chunk.getBlockStorageArray().length, centerSection);
        }

        private List<OreMarker> scanNextBlocks(int budget) {
            sectionMarkers.clear();
            lastScanChecks = 0;
            ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
            while (sectionCursor < sectionOrder.length) {
                ExtendedBlockStorage section = sections[sectionOrder[sectionCursor]];
                if (section == null || section.isEmpty()) {
                    sectionCursor++;
                    blockCursor = 0;
                    continue;
                }
                int baseX = chunk.x << 4;
                int baseY = section.getYLocation();
                int baseZ = chunk.z << 4;
                int checks = scanSliceChecks(blockCursor, SECTION_BLOCK_COUNT, budget);
                int end = blockCursor + checks;
                for (; blockCursor < end; blockCursor++) {
                    int x = blockCursor & 15;
                    int z = blockCursor >> 4 & 15;
                    int y = blockCursor >> 8 & 15;
                    OreType type = OreType.fromBlock(section.get(x, y, z).getBlock());
                    if (type != null) sectionMarkers.add(new OreMarker(
                        new BlockPos(baseX + x, baseY + y, baseZ + z), type));
                }
                lastScanChecks = checks;
                if (blockCursor >= SECTION_BLOCK_COUNT) {
                    sectionCursor++;
                    blockCursor = 0;
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

    private static final class ValidationTask {
        private final long key;
        private int markerIndex;

        private ValidationTask(long key) {
            this.key = key;
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
