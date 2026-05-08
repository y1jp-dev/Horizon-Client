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
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;


public class OreESPModule extends Module {

    public final Setting<Boolean> showCoal     = addSetting(new Setting<>("Coal",     "Coal",     true));
    public final Setting<Boolean> showIron     = addSetting(new Setting<>("Iron",     "Iron",     true));
    public final Setting<Boolean> showGold     = addSetting(new Setting<>("Gold",     "Gold",     true));
    public final Setting<Boolean> showRedstone = addSetting(new Setting<>("Redstone", "Redstone", true));
    public final Setting<Boolean> showLapis    = addSetting(new Setting<>("Lapis",    "Lapis",    true));
    public final Setting<Boolean> showDiamond  = addSetting(new Setting<>("Diamond",  "Diamond",  true));
    public final Setting<Boolean> showEmerald  = addSetting(new Setting<>("Emerald",  "Emerald",  true));
    public final Setting<Boolean> showDebris   = addSetting(new Setting<>("Debris",   "Debris",         true));
    public final Setting<Boolean> showGilded   = addSetting(new Setting<>("Gilded",   "Gilded",      true));
    public final Setting<Boolean> tracers      = addSetting(new Setting<>("Tracers",  "Tracers",      false));
    public final Setting<Boolean> outline = addSetting(new Setting<>("Outline", "Show outline", true));
    public final Setting<Double>  fillAlpha    = addSetting(new Setting<>("Fill Alpha", "Fill opacity", 3.2, 0.0, 20.0));


    private static final int T_COAL     = 0;
    private static final int T_IRON     = 1;
    private static final int T_GOLD     = 2;
    private static final int T_REDSTONE = 3;
    private static final int T_LAPIS    = 4;
    private static final int T_DIAMOND  = 5;
    private static final int T_EMERALD  = 6;
    private static final int T_DEBRIS   = 7;
    private static final int T_GILDED   = 8;


    private static final int[] FILL_COLORS = {
        0x28404040,
        0x28C0C0C0,
        0x28FFD700,
        0x28CC0000,
        0x280033CC,
        0x2800FFFF,
        0x2800CC44,
        0x2844CCFF,
        0x28FF8C00,
    };


    private static final int[] LINE_COLORS = {
        0xFF606060,
        0xFFDCDCDC,
        0xFFFFD700,
        0xFFFF2222,
        0xFF2255FF,
        0xFF00FFFF,
        0xFF00FF55,
        0xFF44CCFF,
        0xFFFF8C00,
    };


    private static final int SCAN_RADIUS = 32;

    public OreESPModule() {
        super("Ore ESP", "Highlights deepslate ores");
    }


    public void render(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        Vec3d camPos = context.camera().getPos();
        BlockPos center = mc.player.getBlockPos();


        List<Entry> entries = new ArrayList<>();

        int minX = center.getX() - SCAN_RADIUS;
        int maxX = center.getX() + SCAN_RADIUS;
        int minY = Math.max(mc.world.getBottomY(), center.getY() - SCAN_RADIUS);
        int maxY = Math.min(mc.world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, center.getX(), center.getZ()), center.getY() + SCAN_RADIUS);
        int minZ = center.getZ() - SCAN_RADIUS;
        int maxZ = center.getZ() + SCAN_RADIUS;

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutable.set(x, y, z);

                    if (!mc.world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    Block block = mc.world.getBlockState(mutable).getBlock();
                    int t = typeOf(block);
                    if (t >= 0) entries.add(new Entry(mutable.toImmutable(), t));
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


        float fa = fillAlpha.getValue().floatValue() / 20f;
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (Entry e : entries) {
            int argb = FILL_COLORS[e.type];
            float a = fa;
            float r = ((argb >> 16) & 0xFF) / 255f;
            float g = ((argb >>  8) & 0xFF) / 255f;
            float b = ( argb        & 0xFF) / 255f;
            double x1 = e.pos.getX(), y1 = e.pos.getY(), z1 = e.pos.getZ();
            double x2 = x1+1, y2 = y1+1, z2 = z1+1;
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
            double x2 = x1+1, y2 = y1+1, z2 = z1+1;
            outlineBox(buf, mat, x1, y1, z1, x2, y2, z2, r, g, b, 1f);
        }
        built = buf.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        }


        if (tracers.getValue()) {
            Vec3d look = Vec3d.fromPolar(context.camera().getPitch(), context.camera().getYaw());
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
