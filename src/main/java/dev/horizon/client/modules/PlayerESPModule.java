package dev.horizon.client.modules;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;


public class PlayerESPModule extends Module {

    public final Setting<Boolean> showFriendly = addSetting(new Setting<>("Friendly",   "All players",        true));
    public final Setting<Boolean> tracers      = addSetting(new Setting<>("Tracers",    "Tracers",       false));
    public final Setting<Boolean> outline = addSetting(new Setting<>("Outline", "Show outline", true));
    public final Setting<Double>  fillAlpha    = addSetting(new Setting<>("Fill Alpha", "Fill opacity", 3.2, 0.0, 20.0));


    private static final float FR = 1.0f, FG = 1.0f, FB = 1.0f;

    private static final float OR = 1.0f, OG = 1.0f, OB = 1.0f;

    public PlayerESPModule() {
        super("Player ESP", "Highlights other players");
    }

    public void render(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        Vec3d camPos = context.camera().getPos();


        List<AbstractClientPlayerEntity> players = new ArrayList<>();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            players.add(p);
        }

        if (players.isEmpty()) return;

        float tickDelta = context.tickCounter().getTickDelta(true);

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
            for (AbstractClientPlayerEntity p : players) {
                Box box = getInterpolatedBox(p, tickDelta);
                fillBox(buf, mat, box.minX, box.minY, box.minZ,
                                  box.maxX, box.maxY, box.maxZ,
                                  FR, FG, FB, fa);
            }
            BuiltBuffer built = buf.endNullable();
            if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        }


        if (outline.getValue()) {
        RenderSystem.lineWidth(1.5f);
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (AbstractClientPlayerEntity p : players) {
            Box box = getInterpolatedBox(p, tickDelta);
            outlineBox(buf, mat, box.minX, box.minY, box.minZ,
                                 box.maxX, box.maxY, box.maxZ,
                                 OR, OG, OB, 1f);
        }
        BuiltBuffer built = buf.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        }


        if (tracers.getValue()) {
            Vec3d look = Vec3d.fromPolar(context.camera().getPitch(), context.camera().getYaw());
            double ox = camPos.x + look.x * 10;
            double oy = camPos.y + look.y * 10;
            double oz = camPos.z + look.z * 10;

            BufferBuilder tracerBuf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (AbstractClientPlayerEntity p : players) {
                Box box = getInterpolatedBox(p, tickDelta);
                double cx = (box.minX + box.maxX) * 0.5;
                double cy = box.minY;
                double cz = (box.minZ + box.maxZ) * 0.5;
                v(tracerBuf, mat, ox, oy, oz, OR, OG, OB, 0.7f);
                v(tracerBuf, mat, cx, cy, cz, OR, OG, OB, 0.7f);
            }
            BuiltBuffer tracerBuilt = tracerBuf.endNullable();
            if (tracerBuilt != null) BufferRenderer.drawWithGlobalProgram(tracerBuilt);
        }

        RenderSystem.lineWidth(1f);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        matrices.pop();
    }


    private static Box getInterpolatedBox(AbstractClientPlayerEntity p, float tickDelta) {
        double x = lerp(tickDelta, p.lastRenderX, p.getX());
        double y = lerp(tickDelta, p.lastRenderY, p.getY());
        double z = lerp(tickDelta, p.lastRenderZ, p.getZ());
        Box bb = p.getBoundingBox();
        double hw = (bb.maxX - bb.minX) * 0.5;
        double h  =  bb.maxY - bb.minY;
        return new Box(x - hw, y, z - hw, x + hw, y + h, z + hw);
    }

    private static double lerp(float t, double a, double b) { return a + t * (b - a); }



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
