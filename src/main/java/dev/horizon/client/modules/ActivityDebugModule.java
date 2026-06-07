package dev.horizon.client.modules;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ActivityDebugModule extends Module {

    private static ActivityDebugModule INSTANCE;

    public final Setting<Double> radius  = addSetting(new Setting<>("Radius",   "Chunks around player to render",  8.0,   2.0,  24.0));
    public final Setting<Double> yOffset = addSetting(new Setting<>("Y Offset", "Vertical offset for the overlay", 0.0, -64.0, 256.0));

    private static final int    Y_LIMIT          = 4;

    private static final int    SIGNAL_THRESHOLD = 5;

    private static final long   ENTITY_COOLDOWN  = 10_000L;

    private static final long   CLEAR_INTERVAL   = 30_000L;

    private static final double BASE_RENDER_Y    = -7.0;

    private static final int    MAX_WEIGHT       = 50;

    private static final int W_BLOCK_UPDATE = 3;
    private static final int W_SOUND        = 2;
    private static final int W_ENTITY       = 5;
    private static final int W_WORLD_EVENT  = 2;
    private static final int W_BLOCK_ENTITY = 4;
    private static final int W_DIVERSITY    = 5;

    private final ConcurrentHashMap<ChunkPos, SignalData> chunkSignals    = new ConcurrentHashMap<>();
    private final Set<ChunkPos>                           flaggedChunks   = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos>                           notifiedChunks  = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Integer, Long>        entityCooldowns = new ConcurrentHashMap<>();
    private long lastClearTime = 0L;

    public ActivityDebugModule() {
        super("Activity Debug", "Heat-map of underground chunk activity.");
        INSTANCE = this;
    }

    public static ActivityDebugModule get() { return INSTANCE; }

    @Override
    protected void onEnable() {
        chunkSignals.clear();
        flaggedChunks.clear();
        notifiedChunks.clear();
        entityCooldowns.clear();
        lastClearTime = System.currentTimeMillis();
    }

    @Override
    protected void onDisable() {
        chunkSignals.clear();
        flaggedChunks.clear();
        notifiedChunks.clear();
        entityCooldowns.clear();
    }

    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        scanEntities(mc);
        long now = System.currentTimeMillis();
        if (now - lastClearTime > CLEAR_INTERVAL) {
            clearDistantChunks(mc);
            lastClearTime = now;
        }
    }

    private void scanEntities(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == null || entity == mc.player) continue;
            if (entity.getY() >= Y_LIMIT) continue;
            int id = entity.getId();
            Long last = entityCooldowns.get(id);
            if (last != null && now - last < ENTITY_COOLDOWN) continue;
            entityCooldowns.put(id, now);
            addSignal(entity.getBlockPos(), SignalType.ENTITY, W_ENTITY, mc);
        }
        entityCooldowns.entrySet().removeIf(e -> now - e.getValue() > 60_000L);
    }

    public void onBlockUpdate(BlockPos pos) {
        if (pos.getY() >= Y_LIMIT) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        addSignal(pos, SignalType.BLOCK_UPDATE, W_BLOCK_UPDATE, mc);
    }

    public void onChunkDeltaUpdate(BlockPos pos) {
        if (pos.getY() >= Y_LIMIT) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        addSignal(pos, SignalType.BLOCK_UPDATE, W_BLOCK_UPDATE, mc);
    }

    public void onBlockEntityUpdate(BlockPos pos) {
        if (pos.getY() >= Y_LIMIT) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        addSignal(pos, SignalType.BLOCK_ENTITY, W_BLOCK_ENTITY, mc);
    }

    public void onWorldEvent(int eventId, BlockPos pos) {
        if (pos == null || pos.getY() >= Y_LIMIT) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        switch (eventId) {
            case 1001, 1010, 1011, 1012, 1013, 1014, 1015, 1018 ->
                addSignal(pos, SignalType.WORLD_EVENT, W_WORLD_EVENT, mc);
        }
    }

    public void onSoundPlayed(String soundId, double x, double y, double z) {
        if (y >= Y_LIMIT) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        if (!isTrackedSound(soundId)) return;
        addSignal(new BlockPos((int) x, (int) y, (int) z), SignalType.SOUND, W_SOUND, mc);
    }

    private boolean isTrackedSound(String id) {
        return id.contains("furnace")       || id.contains("piston")        ||
               id.contains("chest")        || id.contains("hopper")        ||
               id.contains("barrel")       || id.contains("brewing_stand") ||
               id.contains("dispenser")    || id.contains("dropper")       ||
               id.contains("door")         || id.contains("trapdoor")      ||
               id.contains("gate")         || id.contains("anvil")         ||
               id.contains("grindstone")   || id.contains("smoker")        ||
               id.contains("blast_furnace")|| id.contains("comparator")    ||
               id.contains("shulker")      || id.contains("ender_chest")   ||
               id.contains("crafter")      || id.contains("vault");
    }

    private void addSignal(BlockPos pos, SignalType type, int weight, MinecraftClient mc) {
        ChunkPos chunk = new ChunkPos(pos);
        SignalData data = chunkSignals.computeIfAbsent(chunk, k -> new SignalData());
        data.record(pos, type, weight);
        if (data.uniqueTypeCount() >= 3 && !data.diversityBonusApplied) {
            data.weightedTotal += W_DIVERSITY;
            data.diversityBonusApplied = true;
        }
        if (data.weightedTotal >= SIGNAL_THRESHOLD && flaggedChunks.add(chunk)) {
            notifyDetection(chunk, data, mc);
        }
    }

    private void notifyDetection(ChunkPos chunk, SignalData data, MinecraftClient mc) {
        if (!notifiedChunks.add(chunk)) return;
        mc.execute(() -> {
            if (mc.player == null) return;
            int wx = chunk.x * 16 + 8, wz = chunk.z * 16 + 8;
            mc.player.sendMessage(
                Text.literal("§c[ActivityDebug] §fBase activity at §eX:" + wx + " Z:" + wz +
                             " §7Weight:§f " + data.weightedTotal),
                false
            );
        });
    }

    public void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        MatrixStack matrices = ctx.matrixStack();
        if (matrices == null) return;

        Vec3d cam = ctx.camera().getPos();
        int   pCX = mc.player.getBlockX() >> 4;
        int   pCZ = mc.player.getBlockZ() >> 4;
        int   r   = radius.getValue().intValue();
        double y0 = BASE_RENDER_Y + yOffset.getValue();
        double y1 = y0 + 0.08;

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

        BufferBuilder fillBuf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int cx = pCX - r; cx <= pCX + r; cx++) {
            for (int cz = pCZ - r; cz <= pCZ + r; cz++) {
                ChunkPos   chunk = new ChunkPos(cx, cz);
                SignalData data  = chunkSignals.get(chunk);
                double x0 = cx * 16.0, z0 = cz * 16.0;
                double x2 = x0 + 16.0, z2 = z0 + 16.0;

                if (data == null || data.weightedTotal < SIGNAL_THRESHOLD) {

                    fillBox(fillBuf, mat, x0, y0, z0, x2, y1, z2, 0f, 0f, 0f, 0.33f);
                } else {

                    float intensity = Math.min(1.0f, (float) data.weightedTotal / MAX_WEIGHT);
                    double riseY    = intensity * 3.0;
                    fillBox(fillBuf, mat, x0, y0 + riseY, z0, x2, y1 + riseY, z2,
                            intensity, intensity, intensity, 0.24f + intensity * 0.55f);
                }
            }
        }
        BuiltBuffer fb = fillBuf.endNullable();
        if (fb != null) BufferRenderer.drawWithGlobalProgram(fb);

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    private void clearDistantChunks(MinecraftClient mc) {
        if (mc.player == null) return;
        int max = radius.getValue().intValue() + 4;
        int px  = mc.player.getBlockX() >> 4;
        int pz  = mc.player.getBlockZ() >> 4;
        chunkSignals.entrySet().removeIf(e -> isFar(e.getKey(), px, pz, max));
        flaggedChunks.removeIf(c  -> isFar(c, px, pz, max));
        notifiedChunks.removeIf(c -> isFar(c, px, pz, max));
    }

    private boolean isFar(ChunkPos c, int px, int pz, int max) {
        return Math.abs(c.x - px) > max || Math.abs(c.z - pz) > max;
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

    private static void v(BufferBuilder b, Matrix4f m,
                          double x, double y, double z,
                          float r, float g, float bv, float a) {
        b.vertex(m, (float) x, (float) y, (float) z).color(r, g, bv, a);
    }

    private enum SignalType { BLOCK_UPDATE, SOUND, ENTITY, WORLD_EVENT, BLOCK_ENTITY }

    private static final class SignalData {
        int     weightedTotal         = 0;
        boolean diversityBonusApplied = false;
        final ConcurrentHashMap<BlockPos, Integer>  posHits    = new ConcurrentHashMap<>();
        final EnumMap<SignalType, Integer>           typeCounts = new EnumMap<>(SignalType.class);

        void record(BlockPos pos, SignalType type, int weight) {
            int hits = posHits.merge(pos, 1, Integer::sum);
            if (hits == 1 || hits % 5 == 0) {
                weightedTotal += weight;
                typeCounts.merge(type, 1, Integer::sum);
            }

            if (posHits.size() > 200) posHits.clear();
        }

        int uniqueTypeCount() { return typeCounts.size(); }
    }
}
