package dev.horizon.client.modules;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
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

public class OreESPModule extends Module {

    public final Setting<Boolean> showCoal     = addSetting(new Setting<>("Coal",      "Coal",      true));
    public final Setting<Boolean> showIron     = addSetting(new Setting<>("Iron",      "Iron",      true));
    public final Setting<Boolean> showGold     = addSetting(new Setting<>("Gold",      "Gold",      true));
    public final Setting<Boolean> showRedstone = addSetting(new Setting<>("Redstone",  "Redstone",  true));
    public final Setting<Boolean> showLapis    = addSetting(new Setting<>("Lapis",     "Lapis",     true));
    public final Setting<Boolean> showDiamond  = addSetting(new Setting<>("Diamond",   "Diamond",   true));
    public final Setting<Boolean> showEmerald  = addSetting(new Setting<>("Emerald",   "Emerald",   true));
    public final Setting<Boolean> showDebris   = addSetting(new Setting<>("Debris",    "Debris",    true));
    public final Setting<Boolean> showGilded   = addSetting(new Setting<>("Gilded",    "Gilded",    true));
    public final Setting<Boolean> tracers      = addSetting(new Setting<>("Tracers",   "Tracers",   false));
    public final Setting<Boolean> outline      = addSetting(new Setting<>("Outline",   "Outline",   true));
    public final Setting<Double>  fillAlpha    = addSetting(new Setting<>("Fill Alpha","Fill opacity", 3.2, 0.0, 20.0));

    private static final int T_COAL = 0, T_IRON = 1, T_GOLD = 2, T_REDSTONE = 3,
                             T_LAPIS = 4, T_DIAMOND = 5, T_EMERALD = 6,
                             T_DEBRIS = 7, T_GILDED = 8;

    private static final int[] FILL_COLORS = {
        0x28404040, 0x28C0C0C0, 0x28FFD700, 0x28CC0000,
        0x280033CC, 0x2800FFFF, 0x2800CC44, 0x2844CCFF, 0x28FF8C00,
    };
    private static final int[] LINE_COLORS = {
        0xFF606060, 0xFFDCDCDC, 0xFFFFD700, 0xFFFF2222,
        0xFF2255FF, 0xFF00FFFF, 0xFF00FF55, 0xFF44CCFF, 0xFFFF8C00,
    };

    private static final int SCAN_RADIUS = 4;

    private final ConcurrentHashMap<Long, List<Entry>> chunkEntries  = new ConcurrentHashMap<>();
    private final Set<Long>                            scannedChunks = Collections.synchronizedSet(new HashSet<>());
    private final ConcurrentLinkedQueue<ChunkPos>      chunkQueue    = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean                        workerRunning = new AtomicBoolean(false);

    private ExecutorService executor;

    public OreESPModule() {
        super("Ore ESP", "Highlights deepslate ores");
    }

    @Override
    protected void onEnable() {
        chunkEntries.clear();
        scannedChunks.clear();
        chunkQueue.clear();
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "horizon-oreesp");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }

    @Override
    protected void onDisable() {
        if (executor != null) { executor.shutdownNow(); executor = null; }
        chunkEntries.clear();
        scannedChunks.clear();
        chunkQueue.clear();
    }

    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || executor == null) return;

        scannedChunks.removeIf(key -> {
            int cx = ChunkPos.getPackedX(key), cz = ChunkPos.getPackedZ(key);
            boolean loaded = mc.world.getChunkManager().isChunkLoaded(cx, cz);
            if (!loaded) chunkEntries.remove(key);
            return !loaded;
        });

        ChunkPos pc = mc.player.getChunkPos();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
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

    public void onChunkUnload(ChunkPos pos) {
        long key = ChunkPos.toLong(pos.x, pos.z);
        chunkEntries.remove(key);
        scannedChunks.remove(key);
    }

    public void onBlockUpdate(BlockPos pos) {
        long key = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        List<Entry> entries = chunkEntries.get(key);
        if (entries != null) {
            entries.removeIf(e -> e.pos().equals(pos));
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

        int startX  = cp.getStartX();
        int startZ  = cp.getStartZ();
        int bottomY = mc.world.getBottomY();
        ChunkSection[] sections = chunk.getSectionArray();

        List<Entry> found = new ArrayList<>();
        for (int si = 0; si < sections.length; si++) {
            ChunkSection sec = sections[si];
            if (sec == null || sec.isEmpty()) continue;
            int baseY = bottomY + si * 16;
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int ly = 0; ly < 16; ly++) {
                        Block blk = sec.getBlockState(lx, ly, lz).getBlock();
                        int t = typeOf(blk);
                        if (t >= 0) found.add(new Entry(
                            new BlockPos(startX + lx, baseY + ly, startZ + lz), t));
                    }
                }
            }
        }

        chunkEntries.put(ChunkPos.toLong(cp.x, cp.z), found);
    }

    public void render(WorldRenderContext context) {
        if (chunkEntries.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        Vec3d camPos = context.camera().getPos();
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Tessellator tess = Tessellator.getInstance();
        float fa = fillAlpha.getValue().floatValue() / 20f;

        if (fa > 0f) {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (List<Entry> entries : chunkEntries.values()) {
                for (Entry e : entries) {
                    int argb = FILL_COLORS[e.type];
                    float r = ((argb >> 16) & 0xFF) / 255f;
                    float g = ((argb >>  8) & 0xFF) / 255f;
                    float b = ( argb        & 0xFF) / 255f;
                    fillBox(buf, mat, e.pos.getX(), e.pos.getY(), e.pos.getZ(),
                            e.pos.getX()+1, e.pos.getY()+1, e.pos.getZ()+1, r, g, b, fa);
                }
            }
            BuiltBuffer built = buf.endNullable();
            if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        }

        if (outline.getValue()) {
            RenderSystem.lineWidth(1.5f);
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (List<Entry> entries : chunkEntries.values()) {
                for (Entry e : entries) {
                    int argb = LINE_COLORS[e.type];
                    float r = ((argb >> 16) & 0xFF) / 255f;
                    float g = ((argb >>  8) & 0xFF) / 255f;
                    float b = ( argb        & 0xFF) / 255f;
                    outlineBox(buf, mat, e.pos.getX(), e.pos.getY(), e.pos.getZ(),
                               e.pos.getX()+1, e.pos.getY()+1, e.pos.getZ()+1, r, g, b, 1f);
                }
            }
            BuiltBuffer built = buf.endNullable();
            if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        }

        if (tracers.getValue()) {
            Vec3d look = Vec3d.fromPolar(context.camera().getPitch(), context.camera().getYaw());
            double ox = camPos.x + look.x * 10;
            double oy = camPos.y + look.y * 10;
            double oz = camPos.z + look.z * 10;
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (List<Entry> entries : chunkEntries.values()) {
                for (Entry e : entries) {
                    int argb = LINE_COLORS[e.type];
                    float r = ((argb >> 16) & 0xFF) / 255f;
                    float g = ((argb >>  8) & 0xFF) / 255f;
                    float b = ( argb        & 0xFF) / 255f;
                    v(buf, mat, ox, oy, oz, r, g, b, 0.7f);
                    v(buf, mat, e.pos.getX()+0.5, e.pos.getY()+0.5, e.pos.getZ()+0.5, r, g, b, 0.7f);
                }
            }
            BuiltBuffer built = buf.endNullable();
            if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        }

        RenderSystem.lineWidth(1f);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        matrices.pop();
    }

    private int typeOf(Block b) {
        if (b == Blocks.DEEPSLATE_COAL_ORE     && showCoal.getValue())     return T_COAL;
        if (b == Blocks.DEEPSLATE_IRON_ORE     && showIron.getValue())     return T_IRON;
        if (b == Blocks.DEEPSLATE_GOLD_ORE     && showGold.getValue())     return T_GOLD;
        if (b == Blocks.DEEPSLATE_REDSTONE_ORE && showRedstone.getValue()) return T_REDSTONE;
        if (b == Blocks.DEEPSLATE_LAPIS_ORE    && showLapis.getValue())    return T_LAPIS;
        if (b == Blocks.DEEPSLATE_DIAMOND_ORE  && showDiamond.getValue())  return T_DIAMOND;
        if (b == Blocks.DEEPSLATE_EMERALD_ORE  && showEmerald.getValue())  return T_EMERALD;
        if (b == Blocks.ANCIENT_DEBRIS         && showDebris.getValue())   return T_DEBRIS;
        if (b == Blocks.GILDED_BLACKSTONE      && showGilded.getValue())   return T_GILDED;
        return -1;
    }

    private static void fillBox(BufferBuilder b, Matrix4f m,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 float r, float g, float bv, float a) {
        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x2,y1,z1,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a); v(b,m,x1,y1,z2,r,g,bv,a);
        v(b,m,x1,y2,z1,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a);
        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x1,y2,z1,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a); v(b,m,x2,y1,z1,r,g,bv,a);
        v(b,m,x1,y1,z2,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a);
        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x1,y1,z2,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a); v(b,m,x1,y2,z1,r,g,bv,a);
        v(b,m,x2,y1,z1,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a);
    }

    private static void outlineBox(BufferBuilder b, Matrix4f m,
                                    double x1, double y1, double z1,
                                    double x2, double y2, double z2,
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
                           double x, double y, double z,
                           float r, float g, float bv, float a) {
        b.vertex(m, (float) x, (float) y, (float) z).color(r, g, bv, a);
    }

    private record Entry(BlockPos pos, int type) {}
}
