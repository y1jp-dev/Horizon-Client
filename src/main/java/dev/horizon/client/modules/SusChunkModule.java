package dev.horizon.client.modules;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

public class SusChunkModule extends Module {

    public final Setting<Double> simDistance = addSetting(
            new Setting<>("Sim Distance", "Scan radius", 6.0, 1.0, 16.0));
    public final Setting<Double> sensitivity = addSetting(
            new Setting<>("Sensitivity",  "Min clusters", 2.0, 1.0, 8.0));
    public final Setting<Double> fillAlpha   = addSetting(
            new Setting<>("Alpha", "Fill opacity (0-20)", 7.0, 0.0, 20.0));

    private static final float HR = 1.0f, HG = 0.15f, HB = 0.15f;
    private static final float RENDER_Y = 63.0f;

    private final ConcurrentLinkedQueue<ScanResult> pendingResults = new ConcurrentLinkedQueue<>();
    private final Set<ChunkPos> inFlight = Collections.synchronizedSet(new HashSet<>());
    private final ConcurrentHashMap<ChunkPos, Integer> flagged = new ConcurrentHashMap<>();

    private ExecutorService executor;



    private boolean listenerActive = false;

    public SusChunkModule() {
        super("Sus Chunk", "Flags chunks with many amethyst clusters");
    }

    @Override
    protected void onEnable() {
        flagged.clear();
        inFlight.clear();
        pendingResults.clear();
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "horizon-suschunk-scanner");
            t.setDaemon(true);
            return t;
        });



        if (!listenerActive) {
            listenerActive = true;
            ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
                if (!isEnabled() || executor == null) return;
                submitChunk(chunk);
            });
        }
    }

    @Override
    protected void onDisable() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        flagged.clear();
        inFlight.clear();
        pendingResults.clear();
    }

    private void submitChunk(WorldChunk chunk) {
        ChunkPos cp = chunk.getPos();
        if (inFlight.contains(cp)) return;


        ChunkSection[] sections = chunk.getSectionArray().clone();
        int bottomY;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        bottomY = mc.world.getBottomY();

        inFlight.add(cp);
        executor.submit(() -> {
            int count = scanSections(sections);
            pendingResults.add(new ScanResult(cp, count));
        });
    }



    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || executor == null) return;

        int threshold = (int) Math.round(sensitivity.getValue());
        int range     = (int) Math.round(simDistance.getValue());
        ChunkPos pc   = new ChunkPos(mc.player.getBlockPos());


        flagged.keySet().removeIf(cp ->
            Math.abs(cp.x - pc.x) > range || Math.abs(cp.z - pc.z) > range);


        ScanResult result;
        while ((result = pendingResults.poll()) != null) {
            inFlight.remove(result.pos);
            if (result.count >= threshold) {
                boolean firstTime = !flagged.containsKey(result.pos);
                flagged.put(result.pos, result.count);
                if (firstTime) sendChat(mc, result.pos, result.count);
            } else {
                flagged.remove(result.pos);
            }
        }
    }

    private static int scanSections(ChunkSection[] sections) {
        int found = 0;
        for (ChunkSection section : sections) {
            if (section == null || section.isEmpty()) continue;
            for (int i = 0; i < 4096; i++) {
                int lx = (i >> 8) & 0xF;
                int ly = (i >> 4) & 0xF;
                int lz =  i       & 0xF;
                if (section.getBlockState(lx, ly, lz).getBlock() == Blocks.AMETHYST_CLUSTER) {
                    found++;
                }
            }
        }
        return found;
    }

    private void sendChat(MinecraftClient mc, ChunkPos cp, int count) {
        if (mc.inGameHud == null) return;
        mc.execute(() -> mc.inGameHud.getChatHud().addMessage(
            Text.literal("[Horizon] ").formatted(Formatting.RED)
            .append(Text.literal("[Sus Chunk] ").formatted(Formatting.LIGHT_PURPLE))
            .append(Text.literal("Detected " + count + " cluster"
                + (count == 1 ? "" : "s") + " at "
                + cp.getStartX() + ", " + cp.getStartZ())
                .formatted(Formatting.WHITE))
        ));
    }



    public void render(WorldRenderContext context) {
        if (flagged.isEmpty()) return;

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
            BufferBuilder fillBuf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (ChunkPos cp : flagged.keySet()) {
                double x1 = cp.getStartX(), z1 = cp.getStartZ();
                double x2 = x1 + 16, z2 = z1 + 16;
                fillBox(fillBuf, mat, x1, yB, z1, x2, yT, z2, HR, HG, HB, alpha);
            }
            BuiltBuffer fb = fillBuf.endNullable();
            if (fb != null) BufferRenderer.drawWithGlobalProgram(fb);
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
        b.vertex(m, (float)x, (float)y, (float)z).color(r, g, bv, a);
    }

    private record ScanResult(ChunkPos pos, int count) {}
}
