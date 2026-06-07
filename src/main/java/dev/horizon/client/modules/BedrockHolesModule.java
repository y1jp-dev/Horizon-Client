package dev.horizon.client.modules;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class BedrockHolesModule extends Module {

    public final Setting<Boolean> outline   = addSetting(new Setting<>("Outline",    "Render box outline",    true));
    public final Setting<Boolean> tracers   = addSetting(new Setting<>("Tracers",    "Draw tracers to holes", false));
    public final Setting<Double>  fillAlpha = addSetting(new Setting<>("Fill Alpha", "Fill opacity", 3.2, 0.0, 20.0));

    private static final float FR = 1.0f, FG = 1.0f, FB = 1.0f;

    private static final int SCAN_RADIUS       = 4;
    private static final int FLOOR_SCAN_HEIGHT = 6;

    private final ConcurrentHashMap<Long, List<BlockPos>> chunkEntries  = new ConcurrentHashMap<>();
    private final Set<Long>                               scannedChunks = Collections.synchronizedSet(new HashSet<>());
    private final ConcurrentLinkedQueue<ChunkPos>         chunkQueue    = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean                           workerRunning = new AtomicBoolean(false);

    private ExecutorService executor;

    public BedrockHolesModule() {
        super("Bedrock Holes", "Render bedrock holes");
    }

    @Override
    protected void onEnable() {
        chunkEntries.clear();
        scannedChunks.clear();
        chunkQueue.clear();
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "horizon-bedrockholes");
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

        if (!workerRunning.get() && !chunkQueue.isEmpty()) submitWorker(mc);
    }

    public void onChunkUnload(ChunkPos pos) {
        long key = ChunkPos.toLong(pos.x, pos.z);
        chunkEntries.remove(key);
        scannedChunks.remove(key);
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

        int bottomY  = mc.world.getBottomY();
        int topY     = mc.world.getBottomY() + mc.world.getHeight();
        int startX   = cp.getStartX();
        int startZ   = cp.getStartZ();
        boolean isNether = mc.world.getRegistryKey().equals(World.NETHER);

        List<int[]> ranges = new ArrayList<>();
        ranges.add(new int[]{ bottomY, bottomY + FLOOR_SCAN_HEIGHT });
        if (isNether) ranges.add(new int[]{ 119, 127 });

        Set<BlockPos> candidates = new HashSet<>();
        for (int[] range : ranges) {
            for (int x = startX; x < startX + 16; x++) {
                for (int z = startZ; z < startZ + 16; z++) {
                    for (int y = range[0]; y <= range[1]; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (mc.world.getBlockState(pos).getBlock() != Blocks.BEDROCK) {
                            candidates.add(pos);
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            chunkEntries.put(ChunkPos.toLong(cp.x, cp.z), Collections.emptyList());
            return;
        }

        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> holes  = new ArrayList<>();

        for (BlockPos start : candidates) {
            if (visited.contains(start)) continue;

            List<BlockPos> component = new ArrayList<>();
            Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            visited.add(start);

            while (!queue.isEmpty()) {
                BlockPos cur = queue.poll();
                component.add(cur);
                for (Direction dir : Direction.values()) {
                    BlockPos nb = cur.offset(dir);
                    if (!visited.contains(nb) && candidates.contains(nb)) {
                        visited.add(nb);
                        queue.add(nb);
                    }
                }
            }

            Set<BlockPos> compSet = new HashSet<>(component);
            boolean enclosed = true;

            outer:
            for (BlockPos bp : component) {
                for (Direction dir : Direction.values()) {
                    BlockPos nb = bp.offset(dir);
                    if (compSet.contains(nb)) continue;
                    int ny = nb.getY();
                    if (ny < bottomY || ny >= topY) continue;
                    if (mc.world.getBlockState(nb).getBlock() != Blocks.BEDROCK) {
                        enclosed = false;
                        break outer;
                    }
                }
            }

            if (enclosed) holes.addAll(component);
        }

        chunkEntries.put(ChunkPos.toLong(cp.x, cp.z), holes);
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
            for (List<BlockPos> list : chunkEntries.values()) {
                for (BlockPos p : list) {
                    fillBox(buf, mat,
                            p.getX(), p.getY(), p.getZ(),
                            p.getX() + 1, p.getY() + 1, p.getZ() + 1,
                            FR, FG, FB, fa);
                }
            }
            BuiltBuffer built = buf.endNullable();
            if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        }

        if (outline.getValue()) {
            RenderSystem.lineWidth(1.5f);
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (List<BlockPos> list : chunkEntries.values()) {
                for (BlockPos p : list) {
                    outlineBox(buf, mat,
                               p.getX(), p.getY(), p.getZ(),
                               p.getX() + 1, p.getY() + 1, p.getZ() + 1,
                               FR, FG, FB, 1f);
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
            for (List<BlockPos> list : chunkEntries.values()) {
                for (BlockPos p : list) {
                    v(buf, mat, ox, oy, oz, FR, FG, FB, 0.7f);
                    v(buf, mat, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, FR, FG, FB, 0.7f);
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
}
