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

/**
 * Storage ESP.
 *
 * Rendering approach ported directly from Zyro client's StorageEspRenderer:
 *  - Hook: WorldRenderEvents.AFTER_ENTITIES
 *  - Matrix: use context.matrixStack() directly, push/translate(-camPos)/pop
 *  - Draw: Tessellator + QUADS + POSITION_COLOR for fill,
 *          DEBUG_LINES + POSITION_COLOR for outline
 *  - Shader: ShaderProgramKeys.POSITION_COLOR
 *  - Flush: BufferRenderer.drawWithGlobalProgram(buf.end())
 *
 * This is exactly how Zyro does it and it works.
 */
public class StorageESPModule extends Module {

    public final Setting<Boolean> showChests      = addSetting(new Setting<>("Chests",       "Show chests",        true));
    public final Setting<Boolean> showEnderChests = addSetting(new Setting<>("Ender Chests", "Show ender chests",  true));
    public final Setting<Boolean> showBarrels     = addSetting(new Setting<>("Barrels",      "Show barrels",       true));
    public final Setting<Boolean> showShulkers    = addSetting(new Setting<>("Shulkers",     "Show shulker boxes", true));
    public final Setting<Boolean> showHoppers     = addSetting(new Setting<>("Hoppers",      "Show hoppers",       true));
    public final Setting<Boolean> showSpawners    = addSetting(new Setting<>("Spawners",     "Show spawners",      true));
    public final Setting<Boolean> tracers         = addSetting(new Setting<>("Tracers",      "Draw tracer lines",  false));

    // Fill colors (ARGB) — 40 alpha so they're semi-transparent
    private static final int[] FILL_COLORS = {
        0x28FF8000, // orange  — chest
        0x287000FF, // purple  — ender chest
        0x288B4513, // brown   — barrel
        0x28FF00FF, // magenta — shulker
        0x28888888, // grey    — hopper
        0x28FF0000, // red     — spawner
    };
    // Outline colors (ARGB) — fully opaque
    private static final int[] LINE_COLORS = {
        0xFFFF8000,
        0xFF7000FF,
        0xFF8B4513,
        0xFFFF00FF,
        0xFF888888,
        0xFFFF0000,
    };

    public StorageESPModule() {
        super("Storage ESP", "Highlights storage containers through walls");
    }

    /**
     * Called from HorizonClient via WorldRenderEvents.AFTER_ENTITIES.
     * context.matrixStack() is the live game matrix stack at this point —
     * it already has the view transform applied by WorldRenderer.
     */
    public void render(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        ClientWorld world  = context.world();
        Vec3d       camPos = context.camera().getPos();

        // Collect all matching block entities within 6 chunks
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

        // Push the matrix and translate by -camPos so world coords work directly.
        // This is exactly what Zyro does: push -> translate(-cam) -> draw -> pop.
        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        // ── Draw filled quads ─────────────────────────────────────
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (Entry e : entries) {
            int argb = FILL_COLORS[e.type];
            float a = ((argb >> 24) & 0xFF) / 255f;
            float r = ((argb >> 16) & 0xFF) / 255f;
            float g = ((argb >>  8) & 0xFF) / 255f;
            float b = ( argb        & 0xFF) / 255f;
            double x1 = e.pos.getX(), y1 = e.pos.getY(), z1 = e.pos.getZ();
            double x2 = x1 + 1,       y2 = y1 + 1,       z2 = z1 + 1;
            fillBox(buf, mat, x1, y1, z1, x2, y2, z2, r, g, b, a);
        }

        BuiltBuffer built = buf.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);

        // ── Draw outlines ─────────────────────────────────────────
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
        // End outlines before starting tracers — Tessellator is not reentrant
        built = buf.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);

        // ── Draw tracers ──────────────────────────────────────────
        if (tracers.getValue()) {
            // Start tracers from 10 blocks in front of the camera (like Zyro).
            // Starting from exactly camPos clips against the near plane and is invisible.
            // Vec3d.fromPolar converts yaw/pitch to a direction unit vector.
            MinecraftClient mc = MinecraftClient.getInstance();
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

        // Restore state
        RenderSystem.lineWidth(1f);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    // ── Type detection ────────────────────────────────────────────

    private int typeOf(BlockEntity be) {
        if ((be instanceof ChestBlockEntity || be instanceof TrappedChestBlockEntity) && showChests.getValue())      return 0;
        if (be instanceof EnderChestBlockEntity && showEnderChests.getValue()) return 1;
        if (be instanceof BarrelBlockEntity     && showBarrels.getValue())     return 2;
        if (be instanceof ShulkerBoxBlockEntity && showShulkers.getValue())    return 3;
        if (be instanceof HopperBlockEntity     && showHoppers.getValue())     return 4;
        if (be instanceof MobSpawnerBlockEntity && showSpawners.getValue())    return 5;
        return -1;
    }

    // ── Drawing helpers ───────────────────────────────────────────

    /** 6 quads (24 vertices) forming a solid box. Matches Zyro's renderFilledBox exactly. */
    private static void fillBox(BufferBuilder b, Matrix4f m,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 float r, float g, float bv, float a) {
        // bottom
        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x2,y1,z1,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a); v(b,m,x1,y1,z2,r,g,bv,a);
        // top
        v(b,m,x1,y2,z1,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a);
        // north (z1)
        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x1,y2,z1,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a); v(b,m,x2,y1,z1,r,g,bv,a);
        // south (z2)
        v(b,m,x1,y1,z2,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a);
        // west (x1)
        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x1,y1,z2,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a); v(b,m,x1,y2,z1,r,g,bv,a);
        // east (x2)
        v(b,m,x2,y1,z1,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a);
    }

    /** 12 line segments forming a wireframe box. Matches Zyro's outline drawBox. */
    private static void outlineBox(BufferBuilder b, Matrix4f m,
                                    double x1, double y1, double z1,
                                    double x2, double y2, double z2,
                                    float r, float g, float bv, float a) {
        // bottom face loop
        v(b,m,x1,y1,z1,r,g,bv,a); v(b,m,x2,y1,z1,r,g,bv,a);
        v(b,m,x2,y1,z1,r,g,bv,a); v(b,m,x2,y1,z2,r,g,bv,a);
        v(b,m,x2,y1,z2,r,g,bv,a); v(b,m,x1,y1,z2,r,g,bv,a);
        v(b,m,x1,y1,z2,r,g,bv,a); v(b,m,x1,y1,z1,r,g,bv,a);
        // top face loop
        v(b,m,x1,y2,z1,r,g,bv,a); v(b,m,x2,y2,z1,r,g,bv,a);
        v(b,m,x2,y2,z1,r,g,bv,a); v(b,m,x2,y2,z2,r,g,bv,a);
        v(b,m,x2,y2,z2,r,g,bv,a); v(b,m,x1,y2,z2,r,g,bv,a);
        v(b,m,x1,y2,z2,r,g,bv,a); v(b,m,x1,y2,z1,r,g,bv,a);
        // verticals
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
