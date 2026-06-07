package dev.horizon.client.modules;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerDebugModule extends Module {

    private static SpawnerDebugModule INSTANCE;

    private final Set<Long> trackedChunks = ConcurrentHashMap.newKeySet();

    public final Setting<Double> fillAlpha = addSetting(
            new Setting<>("Fill Alpha", "Fill opacity (0–20)", 8.0, 0.0, 20.0));

    public SpawnerDebugModule() {
        super("Spawner Debug", "Packet-detected spawner chunk column highlighter");
        INSTANCE = this;
    }

    public static SpawnerDebugModule get() { return INSTANCE; }

    @Override
    protected void onEnable() {
        trackedChunks.clear();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        int pcx = mc.player.getBlockPos().getX() >> 4;
        int pcz = mc.player.getBlockPos().getZ() >> 4;
        for (int cx = pcx - 8; cx <= pcx + 8; cx++) {
            for (int cz = pcz - 8; cz <= pcz + 8; cz++) {
                if (!mc.world.isChunkLoaded(cx, cz)) continue;
                WorldChunk chunk = mc.world.getChunk(cx, cz);
                if (chunkHasSpawner(chunk)) {
                    trackedChunks.add(ChunkPos.toLong(cx, cz));
                }
            }
        }
    }

    @Override
    protected void onDisable() {
        trackedChunks.clear();
    }

    public void onChunkData(int chunkX, int chunkZ) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        WorldChunk chunk = mc.world.getChunk(chunkX, chunkZ);
        long key = ChunkPos.toLong(chunkX, chunkZ);
        if (chunkHasSpawner(chunk)) {
            trackedChunks.add(key);
        }

    }

    public void onBlockEntityUpdate(BlockPos pos, BlockEntity be) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        long key = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        if (be instanceof MobSpawnerBlockEntity) {
            trackedChunks.add(key);
        } else {

            WorldChunk chunk = mc.world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (!chunkHasSpawner(chunk)) {
                trackedChunks.remove(key);
            }
        }
    }

    public void onChunkUnload(ChunkPos chunk) {
        trackedChunks.remove(chunk.toLong());
    }

    public void render(WorldRenderContext context) {
        if (trackedChunks.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        Vec3d cam   = context.camera().getPos();
        float alpha = fillAlpha.getValue().floatValue() / 20f;

        double yMin = mc.world.getBottomY();
        double yMax = mc.world.getBottomY() + mc.world.getHeight();

        List<Long> chunks = new ArrayList<>(trackedChunks);

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

        if (alpha > 0f) {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (long key : chunks) {
                int cx = ChunkPos.getPackedX(key);
                int cz = ChunkPos.getPackedZ(key);
                double x1 = cx * 16.0, z1 = cz * 16.0;
                double x2 = x1 + 16.0, z2 = z1 + 16.0;
                fillBox(buf, mat, x1, yMin, z1, x2, yMax, z2, 1f, 1f, 1f, alpha);
            }
            BuiltBuffer fb = buf.endNullable();
            if (fb != null) BufferRenderer.drawWithGlobalProgram(fb);
        }

        RenderSystem.lineWidth(1.5f);
        BufferBuilder ob = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (long key : chunks) {
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            double x1 = cx * 16.0, z1 = cz * 16.0;
            double x2 = x1 + 16.0, z2 = z1 + 16.0;
            outlineBox(ob, mat, x1, yMin, z1, x2, yMax, z2, 1f, 1f, 1f, 1f);
        }
        BuiltBuffer ol = ob.endNullable();
        if (ol != null) BufferRenderer.drawWithGlobalProgram(ol);
        RenderSystem.lineWidth(1f);

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    private static boolean chunkHasSpawner(WorldChunk chunk) {
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof MobSpawnerBlockEntity) return true;
        }
        return false;
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
