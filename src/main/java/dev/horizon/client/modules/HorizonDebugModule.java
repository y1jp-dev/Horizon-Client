package dev.horizon.client.modules;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class HorizonDebugModule extends Module {

    public final Setting<Double>  scanRadius   = addSetting(new Setting<>("Scan Radius",  "Chunk radius",         4.0, 1.0, 8.0));
    public final Setting<Double>  minBlocks    = addSetting(new Setting<>("Min Blocks",   "Min blocks to flag",   3.0, 1.0, 20.0));
    public final Setting<Boolean> soundDetect  = addSetting(new Setting<>("Sound Detect", "Detect from sounds",   true));
    public final Setting<Boolean> blockDetect  = addSetting(new Setting<>("Block Detect", "Detect from blocks",   true));
    public final Setting<Boolean> entityDetect = addSetting(new Setting<>("Entity Detect","Spawner mob patterns", true));
    public final Setting<Boolean> lightDetect  = addSetting(new Setting<>("Light Detect", "Dark cavity scan",     true));
    public final Setting<Boolean> tracers      = addSetting(new Setting<>("Tracers",      "Draw tracers",         true));
    public final Setting<Double>  fillAlpha    = addSetting(new Setting<>("Alpha",        "Marker opacity",       10.0, 0.0, 20.0));

    private static final float WR = 1f, WG = 1f, WB = 1f;
    private static final float RENDER_Y = 63.0f;
    private static final int   DEEPSLATE_MAX_Y = 0;
    private static final long  SCAN_INTERVAL  = 200L;
    private static final long  MARKER_TIMEOUT = 72000L;

    private final ConcurrentHashMap<Long, Detection> flaggedChunks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<BlockPos, Long>  soundMarkers  = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, Map<String, AtomicInteger>> entitySpawnTracker = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> entityTrackStart = new ConcurrentHashMap<>();
    private static final long  ENTITY_WINDOW   = 200L;
    private static final int   ENTITY_THRESHOLD = 3;

    private final ConcurrentLinkedQueue<ChunkScanResult> pendingResults = new ConcurrentLinkedQueue<>();
    private final Set<Long>                  inFlight    = Collections.synchronizedSet(new HashSet<>());
    private final ConcurrentHashMap<Long, Long> lastScanned = new ConcurrentHashMap<>();

    private long tick = 0;
    private ExecutorService executor;
    private static HorizonDebugModule INSTANCE;

    private static final Set<Block> BASE_BLOCKS = Set.of(
        Blocks.SPAWNER, Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL,
        Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
        Blocks.HOPPER, Blocks.DISPENSER, Blocks.DROPPER,
        Blocks.BREWING_STAND, Blocks.ENCHANTING_TABLE,
        Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL,
        Blocks.CRAFTING_TABLE, Blocks.LADDER, Blocks.IRON_DOOR,
        Blocks.IRON_TRAPDOOR, Blocks.NOTE_BLOCK, Blocks.JUKEBOX,
        Blocks.BEACON, Blocks.LODESTONE
    );

    private static final Set<String> BASE_SOUNDS = Set.of(
        "minecraft:block.chest.open", "minecraft:block.chest.close",
        "minecraft:block.ender_chest.open", "minecraft:block.ender_chest.close",
        "minecraft:block.furnace.fire_crackle",
        "minecraft:block.iron_door.open", "minecraft:block.iron_door.close",
        "minecraft:block.note_block.harp",
        "minecraft:block.anvil.use", "minecraft:block.anvil.land",
        "minecraft:entity.experience_orb.pickup",
        "minecraft:block.enchantment_table.use",
        "minecraft:block.barrel.open", "minecraft:block.barrel.close",
        "minecraft:block.smoker.smoke", "minecraft:block.blast_furnace.fire_crackle",
        "minecraft:entity.mob_spawner.ambient"
    );

    private static boolean isSpawnerMob(Entity e) {
        return e instanceof ZombieEntity
            || e instanceof SkeletonEntity
            || e instanceof SpiderEntity
            || e instanceof CaveSpiderEntity
            || e instanceof CreeperEntity
            || e instanceof BlazeEntity
            || e instanceof SilverfishEntity
            || e instanceof MagmaCubeEntity
            || e instanceof ZombifiedPiglinEntity;
    }

    public HorizonDebugModule() {
        super("Horizon Debug", "Detects base blocks and sounds");
        INSTANCE = this;
    }

    public static HorizonDebugModule get() { return INSTANCE; }

    @Override
    protected void onEnable() {
        flaggedChunks.clear(); soundMarkers.clear();
        pendingResults.clear(); inFlight.clear(); lastScanned.clear();
        entitySpawnTracker.clear(); entityTrackStart.clear();
        tick = 0;
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "horizon-debug-scanner");
            t.setDaemon(true); t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }

    @Override
    protected void onDisable() {
        if (executor != null) { executor.shutdownNow(); executor = null; }
        flaggedChunks.clear(); soundMarkers.clear();
        pendingResults.clear(); inFlight.clear(); lastScanned.clear();
        entitySpawnTracker.clear(); entityTrackStart.clear();
    }

    public void onSoundPlayedById(String id, Vec3d pos) {
        if (!isEnabled() || !soundDetect.getValue()) return;
        if (id == null || pos == null || !BASE_SOUNDS.contains(id)) return;
        if (pos.y > DEEPSLATE_MAX_Y) return;
        BlockPos bp = BlockPos.ofFloored(pos);
        soundMarkers.put(bp, tick);
        flag(new ChunkPos(bp).toLong(), "sound");
    }

    public void onEntitySpawned(Entity entity) {
        if (!isEnabled() || !entityDetect.getValue()) return;
        if (!isSpawnerMob(entity)) return;

        if (entity.getY() > DEEPSLATE_MAX_Y) return;

        ChunkPos cp  = new ChunkPos(entity.getBlockPos());
        long     key = cp.toLong();
        String   cls = entity.getClass().getSimpleName();

        Long start = entityTrackStart.get(key);
        if (start == null || tick - start > ENTITY_WINDOW) {
            entitySpawnTracker.remove(key);
            entityTrackStart.put(key, tick);
        }

        Map<String, AtomicInteger> tracker = entitySpawnTracker.computeIfAbsent(key,
                k -> new ConcurrentHashMap<>());
        int count = tracker.computeIfAbsent(cls, c -> new AtomicInteger(0)).incrementAndGet();

        if (count >= ENTITY_THRESHOLD) {
            flag(key, "entity");
        }
    }

    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || executor == null) return;

        tick++;

        long cutoff = tick - MARKER_TIMEOUT;
        soundMarkers.values().removeIf(t -> t < cutoff);
        flaggedChunks.values().removeIf(d -> d.tickAdded < cutoff);

        ChunkScanResult r;
        while ((r = pendingResults.poll()) != null) {
            inFlight.remove(r.chunkKey);
            if (r.spawnerFound) {

                flag(r.chunkKey, "spawner");
            } else if (r.baseCount >= (int) Math.round(minBlocks.getValue())) {
                flag(r.chunkKey, "block");
            }
            if (r.lightAnomaly) {

                flag(r.chunkKey, "light");
            }
        }

        if (entityDetect.getValue()) {
            scanNearbyEntities(mc);
        }

        if (!blockDetect.getValue() && !lightDetect.getValue()) return;

        int range = (int) Math.round(scanRadius.getValue());
        ChunkPos pc = new ChunkPos(mc.player.getBlockPos());

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                int cx = pc.x + dx, cz = pc.z + dz;
                if (!mc.world.isChunkLoaded(cx, cz)) continue;
                long key = ChunkPos.toLong(cx, cz);
                if (inFlight.contains(key)) continue;
                Long last = lastScanned.get(key);
                if (last != null && tick - last < SCAN_INTERVAL) continue;

                WorldChunk chunk = mc.world.getChunk(cx, cz);
                ChunkSection[] sections = chunk.getSectionArray().clone();
                int bottomY = mc.world.getBottomY();
                boolean doBlock = blockDetect.getValue();
                boolean doLight = lightDetect.getValue();

                int preCount = 0;
                boolean spawnerEntity = false;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be.getPos().getY() > DEEPSLATE_MAX_Y) continue;
                    if (be instanceof MobSpawnerBlockEntity) { spawnerEntity = true; break; }
                    if (isBaseBlockEntity(be)) preCount++;
                }

                final int pre = preCount;
                final boolean spawnEnt = spawnerEntity;
                inFlight.add(key);
                lastScanned.put(key, tick);

                executor.submit(() -> {
                    int baseCount = doBlock ? (pre + scanForBaseBlocks(sections, bottomY)) : pre;
                    boolean spawnerBlock = spawnEnt || (doBlock && scanForSpawner(sections, bottomY));
                    boolean lightAnom   = doLight && scanForDarkCavity(sections, bottomY);
                    pendingResults.add(new ChunkScanResult(key, baseCount, spawnerBlock, lightAnom));
                });
            }
        }
    }

    private void scanNearbyEntities(MinecraftClient mc) {
        if (mc.world == null) return;

        Map<Long, Map<String, Integer>> counts = new HashMap<>();
        for (Entity e : mc.world.getEntities()) {
            if (!isSpawnerMob(e) || e.getY() > DEEPSLATE_MAX_Y) continue;
            long key = new ChunkPos(e.getBlockPos()).toLong();
            String cls = e.getClass().getSimpleName();
            counts.computeIfAbsent(key, k -> new HashMap<>())
                  .merge(cls, 1, Integer::sum);
        }
        for (Map.Entry<Long, Map<String, Integer>> e : counts.entrySet()) {
            for (int cnt : e.getValue().values()) {
                if (cnt >= ENTITY_THRESHOLD) {
                    flag(e.getKey(), "entity");
                    break;
                }
            }
        }
    }

    private static boolean scanForSpawner(ChunkSection[] sections, int bottomY) {
        for (int si = 0; si < sections.length; si++) {
            ChunkSection sec = sections[si];
            if (sec == null || sec.isEmpty()) continue;
            int baseY = bottomY + si * 16;
            if (baseY > DEEPSLATE_MAX_Y) continue;
            for (int i = 0; i < 4096; i++) {
                int lx = (i >> 8) & 0xF, ly = (i >> 4) & 0xF, lz = i & 0xF;
                if (baseY + ly > DEEPSLATE_MAX_Y) continue;
                if (sec.getBlockState(lx, ly, lz).getBlock() == Blocks.SPAWNER) return true;
            }
        }
        return false;
    }

    private static int scanForBaseBlocks(ChunkSection[] sections, int bottomY) {
        int count = 0;
        for (int si = 0; si < sections.length; si++) {
            ChunkSection sec = sections[si];
            if (sec == null || sec.isEmpty()) continue;
            int baseY = bottomY + si * 16;
            if (baseY > DEEPSLATE_MAX_Y) continue;
            for (int i = 0; i < 4096; i++) {
                int lx = (i >> 8) & 0xF, ly = (i >> 4) & 0xF, lz = i & 0xF;
                if (baseY + ly > DEEPSLATE_MAX_Y) continue;
                if (BASE_BLOCKS.contains(sec.getBlockState(lx, ly, lz).getBlock())) count++;
            }
        }
        return count;
    }

    private static boolean scanForDarkCavity(ChunkSection[] sections, int bottomY) {
        for (int si = 0; si < sections.length; si++) {
            ChunkSection sec = sections[si];
            if (sec == null || sec.isEmpty()) continue;
            int sectionBaseY = bottomY + si * 16;

            if (sectionBaseY > DEEPSLATE_MAX_Y) continue;

            int transitions = 0;
            for (int lx = 1; lx < 15; lx++) {
                for (int lz = 1; lz < 15; lz++) {
                    for (int ly = 1; ly < 15; ly++) {
                        boolean cur = sec.getBlockState(lx, ly, lz).isAir();
                        if (!cur) continue;

                        if (!sec.getBlockState(lx-1, ly, lz).isAir()) transitions++;
                        if (!sec.getBlockState(lx+1, ly, lz).isAir()) transitions++;
                        if (!sec.getBlockState(lx, ly-1, lz).isAir()) transitions++;
                        if (!sec.getBlockState(lx, ly+1, lz).isAir()) transitions++;
                        if (!sec.getBlockState(lx, ly, lz-1).isAir()) transitions++;
                        if (!sec.getBlockState(lx, ly, lz+1).isAir()) transitions++;
                    }
                }
            }

            if (transitions > 80 && transitions < 800) return true;
        }
        return false;
    }

    private static boolean isBaseBlockEntity(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof TrappedChestBlockEntity
            || be instanceof BarrelBlockEntity || be instanceof FurnaceBlockEntity
            || be instanceof BlastFurnaceBlockEntity || be instanceof SmokerBlockEntity
            || be instanceof HopperBlockEntity || be instanceof DispenserBlockEntity
            || be instanceof DropperBlockEntity || be instanceof BrewingStandBlockEntity
            || be instanceof EnchantingTableBlockEntity;
    }

    private void flag(long chunkKey, String reason) {
        flaggedChunks.merge(chunkKey, new Detection(tick, reason),
                (old, neu) -> new Detection(
                        Math.max(old.tickAdded, neu.tickAdded),
                        old.confidence() + 1 > 3 ? "confirmed" : old.reason + "+" + neu.reason));
    }

    public void render(WorldRenderContext context) {
        if (flaggedChunks.isEmpty() && soundMarkers.isEmpty()) return;

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

        if (alpha > 0f && !flaggedChunks.isEmpty()) {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (Map.Entry<Long, Detection> e : flaggedChunks.entrySet()) {
                int cx = ChunkPos.getPackedX(e.getKey());
                int cz = ChunkPos.getPackedZ(e.getKey());
                float age   = (float)(tick - e.getValue().tickAdded);
                float pulse = 0.55f + 0.45f * (float) Math.sin(age * 0.12f);
                double x1 = cx * 16.0, z1 = cz * 16.0, x2 = x1+16, z2 = z1+16;
                fillBox(buf, mat, x1, yB, z1, x2, yT, z2, WR, WG, WB, alpha * pulse);
            }
            BuiltBuffer fb = buf.endNullable();
            if (fb != null) BufferRenderer.drawWithGlobalProgram(fb);

            BufferBuilder ob = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (Map.Entry<Long, Detection> e : flaggedChunks.entrySet()) {
                int cx = ChunkPos.getPackedX(e.getKey());
                int cz = ChunkPos.getPackedZ(e.getKey());
                float age   = (float)(tick - e.getValue().tickAdded);
                float pulse = 0.55f + 0.45f * (float) Math.sin(age * 0.12f);
                double x1 = cx * 16.0, z1 = cz * 16.0, x2 = x1+16, z2 = z1+16;
                outlineBox(ob, mat, x1, yB, z1, x2, yT, z2, WR, WG, WB, pulse);
            }
            BuiltBuffer ol = ob.endNullable();
            if (ol != null) BufferRenderer.drawWithGlobalProgram(ol);
        }

        if (!soundMarkers.isEmpty()) {
            RenderSystem.lineWidth(1.5f);
            BufferBuilder rb = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (Map.Entry<BlockPos, Long> e : soundMarkers.entrySet()) {
                BlockPos p = e.getKey();
                float age   = (float)(tick - e.getValue());
                float pulse = 0.5f + 0.5f * (float) Math.sin(age * 0.15f);
                float size  = 0.4f + pulse * 0.5f;
                drawHRing(rb, mat, p.getX()+.5f, p.getY()+.5f, p.getZ()+.5f, size, WR, WG, WB, pulse);
            }
            BuiltBuffer rfb = rb.endNullable();
            if (rfb != null) BufferRenderer.drawWithGlobalProgram(rfb);
            RenderSystem.lineWidth(1f);
        }

        if (tracers.getValue() && !flaggedChunks.isEmpty()) {
            Vec3d look = Vec3d.fromPolar(context.camera().getPitch(), context.camera().getYaw());
            float ox = (float)(cam.x + look.x * 10);
            float oy = (float)(cam.y + look.y * 10);
            float oz = (float)(cam.z + look.z * 10);
            BufferBuilder tb = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (long key : flaggedChunks.keySet()) {
                int cx = ChunkPos.getPackedX(key), cz = ChunkPos.getPackedZ(key);
                v(tb, mat, ox, oy, oz, WR, WG, WB, 0.5f);
                v(tb, mat, cx*16f+8f, RENDER_Y, cz*16f+8f, WR, WG, WB, 0.5f);
            }
            BuiltBuffer tfb = tb.endNullable();
            if (tfb != null) BufferRenderer.drawWithGlobalProgram(tfb);
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        matrices.pop();
    }

    private static void drawHRing(BufferBuilder b, Matrix4f m, float cx, float cy, float cz, float r, float red, float g, float blue, float a) {
        for (int i = 0; i < 24; i++) {
            double a1 = 2*Math.PI*i/24, a2 = 2*Math.PI*(i+1)/24;
            v(b,m, cx+(float)(Math.cos(a1)*r), cy, cz+(float)(Math.sin(a1)*r), red,g,blue,a);
            v(b,m, cx+(float)(Math.cos(a2)*r), cy, cz+(float)(Math.sin(a2)*r), red,g,blue,a);
        }
    }

    private static void fillBox(BufferBuilder b, Matrix4f m, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float bv, float a) {
        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x2,y1,z1,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a); v(b,m,x1,y1,z2,r,g,bv,a);
        v(b,m,x1,y2,z1,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a);
        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x1,y2,z1,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a); v(b,m,x2,y1,z1,r,g,bv,a);
        v(b,m,x1,y1,z2,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a);
        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x1,y1,z2,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a); v(b,m,x1,y2,z1,r,g,bv,a);
        v(b,m,x2,y1,z1,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a);
    }

    private static void outlineBox(BufferBuilder b, Matrix4f m, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float bv, float a) {
        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x2,y1,z1,r,g,bv,a); v(b,m,x2,y1,z1,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a);
        v(b,m,x2,y1,z2,r,g,bv,a); v(b,m,x1,y1,z2,r,g,bv,a); v(b,m,x1,y1,z2,r,g,bv,a); v(b,m,x1,y1,z1,r,g,bv,a);
        v(b,m,x1,y2,z1,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a);
        v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a); v(b,m,x1,y2,z1,r,g,bv,a);
        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x1,y2,z1,r,g,bv,a); v(b,m,x2,y1,z1,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a);
        v(b,m,x2,y1,z2,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x1,y1,z2,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a);
    }

    private static void v(BufferBuilder b, Matrix4f m, double x, double y, double z, float r, float g, float bv, float a) { b.vertex(m,(float)x,(float)y,(float)z).color(r,g,bv,a); }
    private static void v(BufferBuilder b, Matrix4f m, float x,  float y,  float z,  float r, float g, float bv, float a) { b.vertex(m,x,y,z).color(r,g,bv,a); }

    private record Detection(long tickAdded, String reason) {
        int confidence() { return (int) reason.chars().filter(c -> c == '+').count() + 1; }
    }
    private record ChunkScanResult(long chunkKey, int baseCount, boolean spawnerFound, boolean lightAnomaly) {}
}
