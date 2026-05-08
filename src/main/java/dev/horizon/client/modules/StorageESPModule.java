package dev.horizon.client.modules;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.entity.*;
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


public class StorageESPModule extends Module {

    public final Setting<Boolean> showChests      = addSetting(new Setting<>("Chests",       "Chests",        true));
    public final Setting<Boolean> showEnderChests = addSetting(new Setting<>("Ender Chests", "Ender chests",  true));
    public final Setting<Boolean> showBarrels     = addSetting(new Setting<>("Barrels",      "Barrels",       true));
    public final Setting<Boolean> showShulkers    = addSetting(new Setting<>("Shulkers",     "Shulker boxes", true));
    public final Setting<Boolean> showHoppers     = addSetting(new Setting<>("Hoppers",      "Hoppers",       true));
    public final Setting<Boolean> tracers         = addSetting(new Setting<>("Tracers",      "Tracers",  false));
    public final Setting<Boolean> outline = addSetting(new Setting<>("Outline", "Show outline", true));
    public final Setting<Double>  fillAlpha       = addSetting(new Setting<>("Fill Alpha", "Fill opacity", 3.2, 0.0, 20.0));


    private static final int[] FILL_COLORS = {
        0x28FF8000,
        0x287000FF,
        0x288B4513,
        0x28FF00FF,
        0x28888888,
    };

    private static final int[] LINE_COLORS = {
        0xFFFF8000,
        0xFF7000FF,
        0xFF8B4513,
        0xFFFF00FF,
        0xFF888888,
        0xFFFF0000,
    };

    public StorageESPModule() {
        super("Storage ESP", "Highlights storage containers");
    }


    public void render(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        ClientWorld world  = context.world();
        Vec3d       camPos = context.camera().getPos();


        List<Entry> entries = new ArrayList<>();
        int pcx = mc.player.getBlockPos().getX() >> 4;
        int pcz = mc.player.getBlockPos().getZ() >> 4;

        for (int cx = pcx - 6; cx <= pcx + 6; cx++) {
            for (int cz = pcz - 6; cz <= pcz + 6; cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                for (BlockEntity be : world.getChunk(cx, cz).getBlockEntities().values()) {
                    int t = typeOf(be);
                    if (t >= 0) entries.add(new Entry(be.getPos(), t));
                }
            }
        }

        if (entries.isEmpty()) return;



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
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float fa = fillAlpha.getValue().floatValue() / 20f;
        for (Entry e : entries) {
            int argb = FILL_COLORS[e.type];
            float a = fa;
            float r = ((argb >> 16) & 0xFF) / 255f;
            float g = ((argb >>  8) & 0xFF) / 255f;
            float b = ( argb        & 0xFF) / 255f;
            double x1 = e.pos.getX(), y1 = e.pos.getY(), z1 = e.pos.getZ();
            double x2 = x1 + 1,       y2 = y1 + 1,       z2 = z1 + 1;
            fillBox(buf, mat, x1, y1, z1, x2, y2, z2, r, g, b, a);
        }

        BuiltBuffer built = buf.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);


        if (outline.getValue()) {
        RenderSystem.lineWidth(1.5f);
        buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (Entry e : entries) {
            int argb = LINE_COLORS[e.type];
            float r = ((argb >> 16) & 0xFF) / 255f;
            float g = ((argb >>  8) & 0xFF) / 255f;
            float b = ( argb        & 0xFF) / 255f;
            double x1 = e.pos.getX(), y1 = e.pos.getY(), z1 = e.pos.getZ();
            double x2 = x1 + 1,       y2 = y1 + 1,       z2 = z1 + 1;
            outlineBox(buf, mat, x1, y1, z1, x2, y2, z2, r, g, b, 1f);
        }

        built = buf.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        }


        if (tracers.getValue()) {



            net.minecraft.util.math.Vec3d look = net.minecraft.util.math.Vec3d.fromPolar(
                context.camera().getPitch(), context.camera().getYaw());
            double ox = camPos.x + look.x * 10;
            double oy = camPos.y + look.y * 10;
            double oz = camPos.z + look.z * 10;

            buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (Entry e : entries) {
                int argb = LINE_COLORS[e.type];
                float r = ((argb >> 16) & 0xFF) / 255f;
                float g = ((argb >>  8) & 0xFF) / 255f;
                float b = ( argb        & 0xFF) / 255f;
                v(buf, mat, ox, oy, oz, r, g, b, 0.7f);
                v(buf, mat, e.pos.getX() + 0.5, e.pos.getY() + 0.5, e.pos.getZ() + 0.5, r, g, b, 0.7f);
            }
            built = buf.endNullable();
            if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        }


        RenderSystem.lineWidth(1f);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        matrices.pop();
    }



    private int typeOf(BlockEntity be) {
        if ((be instanceof ChestBlockEntity || be instanceof TrappedChestBlockEntity) && showChests.getValue())      return 0;
        if (be instanceof EnderChestBlockEntity && showEnderChests.getValue()) return 1;
        if (be instanceof BarrelBlockEntity     && showBarrels.getValue())     return 2;
        if (be instanceof ShulkerBoxBlockEntity && showShulkers.getValue())    return 3;
        if (be instanceof HopperBlockEntity     && showHoppers.getValue())     return 4;
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
