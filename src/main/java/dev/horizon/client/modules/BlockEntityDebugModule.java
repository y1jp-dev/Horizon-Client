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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlockEntityDebugModule extends Module {

    private static BlockEntityDebugModule INSTANCE;

    private final Map<BlockPos, Boolean> tracked = new ConcurrentHashMap<>();

    public final Setting<Boolean> tracers   = addSetting(new Setting<>("Tracers",    "Draw tracers to block entities", true));
    public final Setting<Double>  fillAlpha = addSetting(new Setting<>("Fill Alpha", "Fill opacity (0–20)",            20.0, 0.0, 20.0));

    public BlockEntityDebugModule() {
        super("Block Entity Debug", "Packet-detected block entity highlighter");
        INSTANCE = this;
    }

    public static BlockEntityDebugModule get() { return INSTANCE; }

    @Override
    protected void onEnable() {

        tracked.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        int pcx = mc.player.getBlockPos().getX() >> 4;
        int pcz = mc.player.getBlockPos().getZ() >> 4;
        for (int cx = pcx - 8; cx <= pcx + 8; cx++) {
            for (int cz = pcz - 8; cz <= pcz + 8; cz++) {
                if (!mc.world.isChunkLoaded(cx, cz)) continue;
                for (BlockEntity be : mc.world.getChunk(cx, cz).getBlockEntities().values()) {
                    if (isTarget(be)) tracked.put(be.getPos().toImmutable(), Boolean.TRUE);
                }
            }
        }
    }

    @Override
    protected void onDisable() {
        tracked.clear();
    }

    public void onBlockEntityPacket(BlockPos pos, BlockEntity be) {
        if (!isEnabled()) return;
        if (be != null && isTarget(be)) {
            tracked.put(pos.toImmutable(), Boolean.TRUE);
        } else {
            tracked.remove(pos);
        }
    }

    public void onChunkBlockEntities(Iterable<BlockEntity> entities) {
        if (!isEnabled()) return;
        for (BlockEntity be : entities) {
            if (isTarget(be)) tracked.put(be.getPos().toImmutable(), Boolean.TRUE);
        }
    }

    public void onChunkUnload(ChunkPos chunk) {
        tracked.keySet().removeIf(pos ->
            (pos.getX() >> 4) == chunk.x && (pos.getZ() >> 4) == chunk.z
        );
    }

    public void render(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        List<BlockPos> positions = new ArrayList<>(tracked.keySet());
        if (positions.isEmpty()) return;

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
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (BlockPos pos : positions) {
            double x1 = pos.getX(), y1 = pos.getY(), z1 = pos.getZ();
            fillBox(buf, mat, x1, y1, z1, x1 + 1, y1 + 1, z1 + 1, 1f, 1f, 1f, fa);
        }
        BuiltBuffer built = buf.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);

        if (tracers.getValue()) {
            Vec3d look = Vec3d.fromPolar(context.camera().getPitch(), context.camera().getYaw());
            double ox = camPos.x + look.x * 10;
            double oy = camPos.y + look.y * 10;
            double oz = camPos.z + look.z * 10;

            buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (BlockPos pos : positions) {
                v(buf, mat, ox, oy, oz, 1f, 1f, 1f, 1f);
                v(buf, mat, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1f, 1f, 1f, 1f);
            }
            built = buf.endNullable();
            if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    private static boolean isTarget(BlockEntity be) {
        return be instanceof ChestBlockEntity
            || be instanceof TrappedChestBlockEntity
            || be instanceof EnderChestBlockEntity
            || be instanceof BarrelBlockEntity
            || be instanceof ShulkerBoxBlockEntity
            || be instanceof HopperBlockEntity
            || be instanceof FurnaceBlockEntity
            || be instanceof BlastFurnaceBlockEntity
            || be instanceof SmokerBlockEntity
            || be instanceof DispenserBlockEntity
            || be instanceof DropperBlockEntity;
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
}
