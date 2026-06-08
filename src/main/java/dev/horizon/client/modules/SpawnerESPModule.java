package dev.horizon.client.modules;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class SpawnerESPModule extends Module {

    public final Setting<Boolean> tracers    = addSetting(new Setting<>("Tracers",     "Tracers",                     true));
    public final Setting<Boolean> outline    = addSetting(new Setting<>("Outline",     "Show outline",                false));
    public final Setting<Double>  fillAlpha  = addSetting(new Setting<>("Fill Alpha",  "Fill opacity",                20.0, 0.0, 20.0));
    public final Setting<Boolean> fullCover  = addSetting(new Setting<>("Full Cover",  "Cover the entire block face", false));

    private static final float FR = 1.0f, FG = 0.0f, FB = 0.0f;
    private static final float OR = 1.0f, OG = 0.2f, OB = 0.2f;

    private static final double INSET = 2.0 / 16.0;

    public SpawnerESPModule() {
        super("Spawner ESP", "Highlights mob spawners");
    }

    public void render(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        ClientWorld world  = context.world();
        Vec3d camPos = context.camera().getPos();

        List<MobSpawnerBlockEntity> spawners = new ArrayList<>();
        int pcx = mc.player.getBlockPos().getX() >> 4;
        int pcz = mc.player.getBlockPos().getZ() >> 4;

        for (int cx = pcx - 6; cx <= pcx + 6; cx++) {
            for (int cz = pcz - 6; cz <= pcz + 6; cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                for (BlockEntity be : world.getChunk(cx, cz).getBlockEntities().values()) {
                    if (be instanceof MobSpawnerBlockEntity msbe)
                        spawners.add(msbe);
                }
            }
        }

        if (spawners.isEmpty()) return;

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Tessellator tess = Tessellator.getInstance();
        float fa = fillAlpha.getValue().floatValue() / 20f;

        if (fa > 0f) {
            double pad = fullCover.getValue() ? 0.0 : INSET;
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (MobSpawnerBlockEntity msbe : spawners) {
                BlockPos p = msbe.getPos();
                double x1 = p.getX() + pad,     y1 = p.getY() + pad,     z1 = p.getZ() + pad;
                double x2 = p.getX() + 1 - pad, y2 = p.getY() + 1 - pad, z2 = p.getZ() + 1 - pad;
                fillBox(buf, mat, x1, y1, z1, x2, y2, z2, FR, FG, FB, fa);
            }
            BuiltBuffer fb = buf.endNullable();
            if (fb != null) BufferRenderer.drawWithGlobalProgram(fb);
        }

        if (outline.getValue()) {
            double pad = fullCover.getValue() ? 0.0 : INSET;
            RenderSystem.lineWidth(1.5f);
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (MobSpawnerBlockEntity msbe : spawners) {
                BlockPos p = msbe.getPos();
                double x1 = p.getX() + pad,     y1 = p.getY() + pad,     z1 = p.getZ() + pad;
                double x2 = p.getX() + 1 - pad, y2 = p.getY() + 1 - pad, z2 = p.getZ() + 1 - pad;
                outlineBox(buf, mat, x1, y1, z1, x2, y2, z2, OR, OG, OB, 1f);
            }
            BuiltBuffer lb = buf.endNullable();
            if (lb != null) BufferRenderer.drawWithGlobalProgram(lb);
        }

        if (tracers.getValue()) {
            Vec3d look = Vec3d.fromPolar(context.camera().getPitch(), context.camera().getYaw());
            double ox = camPos.x + look.x * 10, oy = camPos.y + look.y * 10, oz = camPos.z + look.z * 10;
            float tracerAlpha = outline.getValue() ? 1f : Math.max(0.05f, fa);
            RenderSystem.lineWidth(2.5f);
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (MobSpawnerBlockEntity msbe : spawners) {
                BlockPos p = msbe.getPos();
                v(buf, mat, ox, oy, oz, OR, OG, OB, tracerAlpha);
                v(buf, mat, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, OR, OG, OB, tracerAlpha);
            }
            BuiltBuffer tb = buf.endNullable();
            if (tb != null) BufferRenderer.drawWithGlobalProgram(tb);
        }

        RenderSystem.lineWidth(1f);
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

    private static void v(BufferBuilder b, Matrix4f m, double x, double y, double z, float r, float g, float bv, float a) {
        b.vertex(m, (float)x, (float)y, (float)z).color(r, g, bv, a);
    }
}
