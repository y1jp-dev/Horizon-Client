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
import org.joml.Matrix4f;

import java.util.concurrent.ConcurrentHashMap;

public class PrimeChunkModule extends Module {

    private static PrimeChunkModule INSTANCE;
    public static PrimeChunkModule get() { return INSTANCE; }

    public final Setting<Double> threshold = addSetting(new Setting<>(
            "Threshold", "Piston packets needed to flag a chunk", 5.0, 1.0, 100.0));
    public final Setting<Double> fillAlpha = addSetting(new Setting<>(
            "Alpha", "Fill opacity (0–20)", 8.0, 0.0, 20.0));
    public final Setting<Boolean> tracers  = addSetting(new Setting<>(
            "Tracers", "Draw tracers to flagged chunks", true));

    private static final float CR = 0.0f, CG = 1.0f, CB = 0.2f;
    private static final float RENDER_Y = 63.0f;

    private final ConcurrentHashMap<Long, Integer> hitCounts   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> flaggedChunks = new ConcurrentHashMap<>();

    public PrimeChunkModule() {
        super("Prime Chunk Finder", "Detects piston activity packets");
        INSTANCE = this;
    }

    @Override
    protected void onEnable() {
        hitCounts.clear();
        flaggedChunks.clear();
    }

    @Override
    protected void onDisable() {
        hitCounts.clear();
        flaggedChunks.clear();
    }

    public void onPistonActivity(BlockPos pos) {
        if (!isEnabled()) return;

        if (pos.getY() > 0) return;
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        long key = ChunkPos.toLong(cx, cz);
        int count = hitCounts.merge(key, 1, Integer::sum);
        int thresh = (int) Math.round(threshold.getValue());
        if (count >= thresh) {
            flaggedChunks.put(key, count);
        }
    }

    public void onChunkUnload(ChunkPos pos) {
        long key = ChunkPos.toLong(pos.x, pos.z);
        hitCounts.remove(key);
        flaggedChunks.remove(key);
    }

    public static boolean isPistonRelated(Block block) {
        return block == Blocks.PISTON
            || block == Blocks.STICKY_PISTON
            || block == Blocks.PISTON_HEAD
            || block == Blocks.MOVING_PISTON;
    }

    public void render(WorldRenderContext context) {
        if (flaggedChunks.isEmpty()) return;

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

        if (alpha > 0f) {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (long key : flaggedChunks.keySet()) {
                int cx = ChunkPos.getPackedX(key), cz = ChunkPos.getPackedZ(key);
                double x1 = cx * 16.0, z1 = cz * 16.0;
                double x2 = x1 + 16.0, z2 = z1 + 16.0;
                fillBox(buf, mat, x1, yB, z1, x2, yT, z2, CR, CG, CB, alpha);
            }
            BuiltBuffer fb = buf.endNullable();
            if (fb != null) BufferRenderer.drawWithGlobalProgram(fb);
        }

        if (tracers.getValue()) {
            Vec3d look = Vec3d.fromPolar(context.camera().getPitch(), context.camera().getYaw());
            float ox = (float)(cam.x + look.x * 10);
            float oy = (float)(cam.y + look.y * 10);
            float oz = (float)(cam.z + look.z * 10);
            BufferBuilder tb = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (long key : flaggedChunks.keySet()) {
                int cx = ChunkPos.getPackedX(key), cz = ChunkPos.getPackedZ(key);
                v(tb, mat, ox, oy, oz, CR, CG, CB, 0.5f);
                v(tb, mat, cx * 16f + 8f, RENDER_Y, cz * 16f + 8f, CR, CG, CB, 0.5f);
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

    private static void fillBox(BufferBuilder b, Matrix4f m,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 float r, float g, float bv, float a) {

        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x2,y1,z1,r,g,bv,a);
        v(b,m,x2,y1,z2,r,g,bv,a); v(b,m,x1,y1,z2,r,g,bv,a);

        v(b,m,x1,y2,z1,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a);
        v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a);

        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x1,y2,z1,r,g,bv,a);
        v(b,m,x2,y2,z1,r,g,bv,a); v(b,m,x2,y1,z1,r,g,bv,a);

        v(b,m,x1,y1,z2,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a);
        v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a);

        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x1,y1,z2,r,g,bv,a);
        v(b,m,x1,y2,z2,r,g,bv,a); v(b,m,x1,y2,z1,r,g,bv,a);

        v(b,m,x2,y1,z1,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a);
        v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a);
    }

    private static void v(BufferBuilder b, Matrix4f m,
                           double x, double y, double z,
                           float r, float g, float bv, float a) {
        b.vertex(m, (float) x, (float) y, (float) z).color(r, g, bv, a);
    }
}
