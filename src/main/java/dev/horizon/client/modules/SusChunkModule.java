package dev.horizon.client.modules;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SusChunkModule extends Module {

    public final Setting<Double>  sensitivity = addSetting(new Setting<>("Sensitivity", "Min blocks to flag", 2.0, 1.0, 50.0));
    public final Setting<Double>  fillAlpha   = addSetting(new Setting<>("Alpha",       "Fill opacity",        7.0, 0.0, 20.0));
    public final Setting<Boolean> clustersESP = addSetting(new Setting<>("Clusters ESP","Show geode stones",   false));

    private static final int SIM_DISTANCE     = 16;
    private static final int HIGHLIGHT_RADIUS = 2;

    private static final float CR = 1.0f, CG = 0.15f, CB = 0.15f;

    private static final float PR = 0.6f, PG = 0.0f, PB = 1.0f;
    private static final float RENDER_Y = 63.0f;

    private final ConcurrentHashMap<Long, Integer> rawChunkCounts = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, Integer> flaggedChunks = new ConcurrentHashMap<>();

    private final Set<Long> markedBlocks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Set<Long>                        scannedChunks  = Collections.synchronizedSet(new HashSet<>());
    private final ConcurrentLinkedQueue<ChunkPos>  chunkQueue     = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ScanResult>pendingResults = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean workerRunning = new AtomicBoolean(false);

    private volatile boolean rawCountsDirty = false;

    private ExecutorService executor;

    private static SusChunkModule INSTANCE;
    public static SusChunkModule get() { return INSTANCE; }

    public SusChunkModule() {
        super("Sus Chunk Finder", "Flags suspicious chunks");
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        rawChunkCounts.clear();
        flaggedChunks.clear();
        markedBlocks.clear();
        scannedChunks.clear();
        chunkQueue.clear();
        pendingResults.clear();
        rawCountsDirty = false;
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "horizon-suschunk");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }

    @Override
    protected void onDisable() {
        if (executor != null) { executor.shutdownNow(); executor = null; }
        rawChunkCounts.clear();
        flaggedChunks.clear();
        markedBlocks.clear();
        scannedChunks.clear();
        chunkQueue.clear();
        pendingResults.clear();
        rawCountsDirty = false;
    }

    public void onChunkUnload(ChunkPos pos) {
        long key = ChunkPos.toLong(pos.x, pos.z);
        rawChunkCounts.remove(key);
        flaggedChunks.remove(key);
        scannedChunks.remove(key);

        markedBlocks.removeIf(l -> {
            BlockPos bp = BlockPos.fromLong(l);
            return (bp.getX() >> 4) == pos.x && (bp.getZ() >> 4) == pos.z;
        });

        rawCountsDirty = true;
    }

    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || executor == null) return;

        int threshold = (int) Math.round(sensitivity.getValue());
        int range     = SIM_DISTANCE;

        ScanResult r;
        while ((r = pendingResults.poll()) != null) {
            long key = ChunkPos.toLong(r.pos.x, r.pos.z);

            rawChunkCounts.put(key, r.count);
            rawCountsDirty = true;

            for (long b : r.blocks) markedBlocks.add(b);
        }

        if (rawCountsDirty) {
            rawCountsDirty = false;
            recomputeFlaggedChunks(threshold);
        }

        markedBlocks.removeIf(l -> {
            BlockPos bp = BlockPos.fromLong(l);
            int cx = bp.getX() >> 4, cz = bp.getZ() >> 4;
            boolean loaded = mc.world.getChunkManager().isChunkLoaded(cx, cz);
            if (!loaded) {
                long key = ChunkPos.toLong(cx, cz);
                boolean changed = rawChunkCounts.remove(key) != null;
                flaggedChunks.remove(key);
                scannedChunks.remove(key);
                if (changed) rawCountsDirty = true;
            }
            return !loaded;
        });

        ChunkPos pc = mc.player.getChunkPos();
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                int cx = pc.x + dx, cz = pc.z + dz;
                if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                long key = ChunkPos.toLong(cx, cz);
                if (!scannedChunks.contains(key)) {
                    scannedChunks.add(key);
                    chunkQueue.add(new ChunkPos(cx, cz));
                }
            }
        }

        if (!workerRunning.get() && !chunkQueue.isEmpty()) {
            submitWorker(mc);
        }
    }

    private void recomputeFlaggedChunks(int threshold) {
        flaggedChunks.clear();
        for (long key : rawChunkCounts.keySet()) {
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            int neighbourhoodSum = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long nKey = ChunkPos.toLong(cx + dx, cz + dz);
                    neighbourhoodSum += rawChunkCounts.getOrDefault(nKey, 0);
                }
            }
            if (neighbourhoodSum >= threshold) {
                flaggedChunks.put(key, neighbourhoodSum);
            }
        }
    }

    private void submitWorker(MinecraftClient mc) {
        if (executor == null || executor.isShutdown()) return;
        workerRunning.set(true);
        executor.submit(() -> {
            try {
                ChunkPos cp;
                while ((cp = chunkQueue.poll()) != null) {
                    if (mc.world == null) break;
                    scanChunk(mc, cp);
                }
            } finally {
                workerRunning.set(false);
            }
        });
    }

    private void scanChunk(MinecraftClient mc, ChunkPos cp) {
        if (mc.world == null) return;

        WorldChunk chunk = mc.world.getChunk(cp.x, cp.z);
        if (chunk == null || chunk.isEmpty()) return;

        WorldChunk chunkN = mc.world.getChunk(cp.x,     cp.z - 1);
        WorldChunk chunkS = mc.world.getChunk(cp.x,     cp.z + 1);
        WorldChunk chunkW = mc.world.getChunk(cp.x - 1, cp.z);
        WorldChunk chunkE = mc.world.getChunk(cp.x + 1, cp.z);

        int startX  = cp.getStartX();
        int startZ  = cp.getStartZ();
        int bottomY = mc.world.getBottomY();
        ChunkSection[] sections = chunk.getSectionArray();

        int count = 0;
        List<Long> blocks = new ArrayList<>();

        for (int si = 0; si < sections.length; si++) {
            ChunkSection sec = sections[si];
            if (sec == null || sec.isEmpty()) continue;
            int baseY = bottomY + si * 16;

            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int ly = 0; ly < 16; ly++) {
                        Block blk = sec.getBlockState(lx, ly, lz).getBlock();
                        int worldY = baseY + ly;

                        if (blk == Blocks.AMETHYST_CLUSTER) {
                            count++;
                            blocks.add(new BlockPos(startX + lx, worldY, startZ + lz).asLong());
                            continue;
                        }

                        if (blk != Blocks.STONE) continue;

                        if (!hasAmethystNeighbour(sections, chunkN, chunkS, chunkW, chunkE,
                                lx, ly, lz, si, worldY, bottomY)) continue;

                        if (!hasExposedFace(sections, chunkN, chunkS, chunkW, chunkE,
                                lx, ly, lz, si, worldY, bottomY)) continue;

                        count++;
                        blocks.add(new BlockPos(startX + lx, worldY, startZ + lz).asLong());
                    }
                }
            }
        }

        pendingResults.add(new ScanResult(cp, count, blocks));
    }

    private boolean hasAmethystNeighbour(ChunkSection[] sec,
                                          WorldChunk N, WorldChunk S, WorldChunk W, WorldChunk E,
                                          int lx, int ly, int lz, int si, int worldY, int bottomY) {
        if (ly > 0) { if (isAmethyst(sec[si].getBlockState(lx, ly-1, lz).getBlock())) return true; }
        else if (si > 0 && sec[si-1] != null) { if (isAmethyst(sec[si-1].getBlockState(lx, 15, lz).getBlock())) return true; }

        if (ly < 15) { if (isAmethyst(sec[si].getBlockState(lx, ly+1, lz).getBlock())) return true; }
        else if (si < sec.length-1 && sec[si+1] != null) { if (isAmethyst(sec[si+1].getBlockState(lx, 0, lz).getBlock())) return true; }

        if (lz > 0) { if (isAmethyst(sec[si].getBlockState(lx, ly, lz-1).getBlock())) return true; }
        else if (N != null) { if (isAmethyst(blockIn(N, lx, worldY, 15, bottomY))) return true; }

        if (lz < 15) { if (isAmethyst(sec[si].getBlockState(lx, ly, lz+1).getBlock())) return true; }
        else if (S != null) { if (isAmethyst(blockIn(S, lx, worldY, 0, bottomY))) return true; }

        if (lx > 0) { if (isAmethyst(sec[si].getBlockState(lx-1, ly, lz).getBlock())) return true; }
        else if (W != null) { if (isAmethyst(blockIn(W, 15, worldY, lz, bottomY))) return true; }

        if (lx < 15) { if (isAmethyst(sec[si].getBlockState(lx+1, ly, lz).getBlock())) return true; }
        else if (E != null) { if (isAmethyst(blockIn(E, 0, worldY, lz, bottomY))) return true; }

        return false;
    }

    private boolean hasExposedFace(ChunkSection[] sec,
                                    WorldChunk N, WorldChunk S, WorldChunk W, WorldChunk E,
                                    int lx, int ly, int lz, int si, int worldY, int bottomY) {
        if (ly > 0) { if (!isSolid(sec[si].getBlockState(lx, ly-1, lz))) return true; }
        else if (si > 0 && sec[si-1] != null) { if (!isSolid(sec[si-1].getBlockState(lx, 15, lz))) return true; }
        else return true;

        if (ly < 15) { if (!isSolid(sec[si].getBlockState(lx, ly+1, lz))) return true; }
        else if (si < sec.length-1 && sec[si+1] != null) { if (!isSolid(sec[si+1].getBlockState(lx, 0, lz))) return true; }
        else return true;

        if (lz > 0) { if (!isSolid(sec[si].getBlockState(lx, ly, lz-1))) return true; }
        else if (N != null) { if (!isSolid(stateIn(N, lx, worldY, 15, bottomY))) return true; }
        else return true;

        if (lz < 15) { if (!isSolid(sec[si].getBlockState(lx, ly, lz+1))) return true; }
        else if (S != null) { if (!isSolid(stateIn(S, lx, worldY, 0, bottomY))) return true; }
        else return true;

        if (lx > 0) { if (!isSolid(sec[si].getBlockState(lx-1, ly, lz))) return true; }
        else if (W != null) { if (!isSolid(stateIn(W, 15, worldY, lz, bottomY))) return true; }
        else return true;

        if (lx < 15) { if (!isSolid(sec[si].getBlockState(lx+1, ly, lz))) return true; }
        else if (E != null) { if (!isSolid(stateIn(E, 0, worldY, lz, bottomY))) return true; }
        else return true;

        return false;
    }

    private Block blockIn(WorldChunk c, int lx, int worldY, int lz, int bottomY) {
        return stateIn(c, lx, worldY, lz, bottomY).getBlock();
    }

    private BlockState stateIn(WorldChunk c, int lx, int worldY, int lz, int bottomY) {
        int si = (worldY - bottomY) >> 4;
        int ly = (worldY - bottomY) & 15;
        ChunkSection[] s = c.getSectionArray();
        if (si < 0 || si >= s.length || s[si] == null) return Blocks.AIR.getDefaultState();
        return s[si].getBlockState(lx, ly, lz);
    }

    private boolean isAmethyst(Block b) {
        return b == Blocks.AMETHYST_BLOCK || b == Blocks.BUDDING_AMETHYST;
    }

    private boolean isSolid(BlockState s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        return s.isOpaque() && s.isFullCube(mc.world, BlockPos.ORIGIN);
    }

    public void render(WorldRenderContext context) {
        boolean hasChunks  = !flaggedChunks.isEmpty();
        boolean hasBlocks  = clustersESP.getValue() && !markedBlocks.isEmpty();
        if (!hasChunks && !hasBlocks) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        Vec3d cam   = context.camera().getPos();
        float alpha = fillAlpha.getValue().floatValue() / 20f;

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Tessellator tess = Tessellator.getInstance();
        float yB = RENDER_Y, yT = RENDER_Y + 0.25f;

        if (alpha > 0f && hasChunks) {

            Set<Long> sourceChunks = new HashSet<>();
            for (long key : flaggedChunks.keySet()) {
                int cx = ChunkPos.getPackedX(key);
                int cz = ChunkPos.getPackedZ(key);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        long nKey = ChunkPos.toLong(cx + dx, cz + dz);
                        if (rawChunkCounts.getOrDefault(nKey, 0) > 0) {
                            sourceChunks.add(nKey);
                        }
                    }
                }
            }

            Set<Long> toRender = new HashSet<>(sourceChunks);
            for (long key : sourceChunks) {
                int cx = ChunkPos.getPackedX(key);
                int cz = ChunkPos.getPackedZ(key);
                for (int dx = -HIGHLIGHT_RADIUS; dx <= HIGHLIGHT_RADIUS; dx++) {
                    for (int dz = -HIGHLIGHT_RADIUS; dz <= HIGHLIGHT_RADIUS; dz++) {
                        toRender.add(ChunkPos.toLong(cx + dx, cz + dz));
                    }
                }
            }

            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (long key : toRender) {
                int cx = ChunkPos.getPackedX(key), cz = ChunkPos.getPackedZ(key);
                double x1 = cx * 16, z1 = cz * 16, x2 = x1 + 16, z2 = z1 + 16;
                fillBoxExterior(buf, mat, x1, yB, z1, x2, yT, z2, CR, CG, CB, alpha, toRender, cx, cz);
            }
            BuiltBuffer fb = buf.endNullable();
            if (fb != null) BufferRenderer.drawWithGlobalProgram(fb);
        }

        if (hasBlocks) {
            BufferBuilder cf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (long l : markedBlocks) {
                BlockPos p = BlockPos.fromLong(l);
                fillBox(cf, mat, p.getX(), p.getY(), p.getZ(),
                        p.getX()+1, p.getY()+1, p.getZ()+1, PR, PG, PB, 0.25f);
            }
            BuiltBuffer cfb = cf.endNullable();
            if (cfb != null) BufferRenderer.drawWithGlobalProgram(cfb);
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        matrices.pop();
    }

    private static void fillBox(BufferBuilder b, Matrix4f m,
                                 double x1, double y1, double z1, double x2, double y2, double z2,
                                 float r, float g, float bv, float a) {
        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x2,y1,z1,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a); v(b,m,x1,y1,z2,r,g,bv,a);
        v(b,m,x1,y2,z1,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a);
        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x1,y2,z1,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a); v(b,m,x2,y1,z1,r,g,bv,a);
        v(b,m,x1,y1,z2,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a);
        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x1,y1,z2,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a); v(b,m,x1,y2,z1,r,g,bv,a);
        v(b,m,x2,y1,z1,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a);
    }

    private static void fillBoxExterior(BufferBuilder b, Matrix4f m,
                                         double x1, double y1, double z1,
                                         double x2, double y2, double z2,
                                         float r, float g, float bv, float a,
                                         Set<Long> rendered, int cx, int cz) {

        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x2,y1,z1,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a); v(b,m,x1,y1,z2,r,g,bv,a);

        v(b,m,x1,y2,z1,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a);

        if (!rendered.contains(ChunkPos.toLong(cx, cz - 1)))
        { v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x1,y2,z1,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a); v(b,m,x2,y1,z1,r,g,bv,a); }

        if (!rendered.contains(ChunkPos.toLong(cx, cz + 1)))
        { v(b,m,x1,y1,z2,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a); }

        if (!rendered.contains(ChunkPos.toLong(cx - 1, cz)))
        { v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x1,y1,z2,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a); v(b,m,x1,y2,z1,r,g,bv,a); }

        if (!rendered.contains(ChunkPos.toLong(cx + 1, cz)))
        { v(b,m,x2,y1,z1,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a); }
    }

    private static void outlineBox(BufferBuilder b, Matrix4f m,
                                    double x1, double y1, double z1, double x2, double y2, double z2,
                                    float r, float g, float bv, float a) {
        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x2,y1,z1,r,g,bv,a);
        v(b,m,x2,y1,z1,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a);
        v(b,m,x2,y1,z2,r,g,bv,a); v(b,m,x1,y1,z2,r,g,bv,a);
        v(b,m,x1,y1,z2,r,g,bv,a); v(b,m,x1,y1,z1,r,g,bv,a);
        v(b,m,x1,y2,z1,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a);
        v(b,m,x2,y2,z1,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a);
        v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a);
        v(b,m,x1,y2,z2,r,g,bv,a); v(b,m,x1,y2,z1,r,g,bv,a);
        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x1,y2,z1,r,g,bv,a);
        v(b,m,x2,y1,z1,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a);
        v(b,m,x2,y1,z2,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a);
        v(b,m,x1,y1,z2,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a);
    }

    private static void v(BufferBuilder b, Matrix4f m,
                           double x, double y, double z, float r, float g, float bv, float a) {
        b.vertex(m, (float)x, (float)y, (float)z).color(r, g, bv, a);
    }

    private record ScanResult(ChunkPos pos, int count, List<Long> blocks) {}
}
