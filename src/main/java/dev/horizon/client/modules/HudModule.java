package dev.horizon.client.modules;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.horizon.client.HorizonClient;
import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix4f;

import java.util.*;
import java.util.stream.Collectors;

public class HudModule extends Module {

    public final Setting<Boolean> showWatermark  = addSetting(new Setting<>("Watermark",   "Show watermark",      true));
    public final Setting<Boolean> showFPS        = addSetting(new Setting<>("FPS",         "Show FPS",            true));
    public final Setting<Boolean> showPing       = addSetting(new Setting<>("Ping",        "Show ping",           true));
    public final Setting<Boolean> showCoords     = addSetting(new Setting<>("Coords",      "Show coordinates",    true));
    public final Setting<Boolean> showModuleList = addSetting(new Setting<>("Module List", "Show active modules", true));
    public final Setting<Boolean> showRadar      = addSetting(new Setting<>("Radar",       "Show player radar",   true));
    public final Setting<Boolean> lowercase      = addSetting(new Setting<>("Lowercase",   "Lowercase modules",   false));
    public final Setting<Double>  radarRange     = addSetting(new Setting<>("Radar Range", "Radar detection range (blocks)", 64.0, 16.0, 256.0));

    private static final int BG       = 0xBB111111;
    private static final int WHITE    = 0xFFEEEEEE;
    private static final int GREY     = 0xFF777777;
    private static final int DIM      = 0xFF444444;
    private static final int DOT_SELF = 0xFFCCCCCC;
    private static final int DOT_OTH  = 0xFFFFFFFF;
    private static final int RING_COL = 0xFF252525;

    private static final int GRID = 2;

    private static final int RADAR_R = 40;

    private static final int DEFAULT_WM_PX    = 4,  DEFAULT_WM_PY    = 4;
    private static final int DEFAULT_INFO_PX  = 56, DEFAULT_INFO_PY  = 4;
    private static final int DEFAULT_COORD_PX = 4,  DEFAULT_COORD_PY = 20;
    private static final int DEFAULT_RADAR_PX = 4,  DEFAULT_RADAR_PY = 40;

    private int wmPX    = DEFAULT_WM_PX,    wmPY    = DEFAULT_WM_PY;
    private int infoPX  = DEFAULT_INFO_PX,  infoPY  = DEFAULT_INFO_PY;
    private int coordPX = DEFAULT_COORD_PX, coordPY = DEFAULT_COORD_PY;
    private int radarPX = DEFAULT_RADAR_PX, radarPY = DEFAULT_RADAR_PY;

    private String dragging = null;
    private int dragOffX, dragOffY;

    private long startMs = System.currentTimeMillis();
    private static HudModule INSTANCE;

    public HudModule() {
        super("HUD", "On-screen info");
        INSTANCE = this;
        if (!isEnabled()) toggle();
    }

    public static HudModule get() { return INSTANCE; }

    public void render(DrawContext ctx, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!isEnabled() || mc.player == null) return;

        boolean editing = mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;
        if (mc.currentScreen != null && !editing) return;

        var tr  = mc.textRenderer;
        int sw  = ctx.getScaledWindowWidth();
        int sh  = ctx.getScaledWindowHeight();
        int fh  = tr.fontHeight;
        int lh  = fh + 6;
        int pad = 5;
        int r   = 3;
        int m   = 4;

        float animT = (System.currentTimeMillis() - startMs) / 1000f;

        if (showWatermark.getValue()) {
            int wmBoxW = tr.getWidth("HORIZON") + pad * 2;
            fillRR(ctx, wmPX, wmPY, wmBoxW, lh, r, BG);
            if (editing) drawEditLabel(ctx, tr, wmPX, wmPY, wmBoxW, lh, "watermark");
            ctx.drawText(tr, "HORIZON", wmPX + pad, wmPY + (lh - fh) / 2, WHITE, false);
        }

        {
            var parts = buildInfoParts(mc, tr);
            if (!parts.isEmpty()) {
                int iW = infoWidth(tr, parts, pad);
                fillRR(ctx, infoPX, infoPY, iW, lh, r, BG);
                if (editing) drawEditLabel(ctx, tr, infoPX, infoPY, iW, lh, "info");
                int tx = infoPX + pad, ty = infoPY + (lh - fh) / 2;
                for (int i = 0; i < parts.size(); i++) {
                    if (i > 0) tx += tr.getWidth("  ");
                    String val = parts.get(i)[0], unit = parts.get(i)[1];
                    ctx.drawText(tr, val, tx, ty, WHITE, false);
                    tx += tr.getWidth(val);
                    if (unit != null) { ctx.drawText(tr, unit, tx, ty, GREY, false); tx += tr.getWidth(unit); }
                }
            }
        }

        if (showCoords.getValue()) {
            int bx2 = (int)mc.player.getX(), by2 = (int)mc.player.getY(), bz = (int)mc.player.getZ();
            String label = "XYZ", vals = "  " + bx2 + "  " + by2 + "  " + bz;
            int coordPW = tr.getWidth(label) + tr.getWidth(vals) + pad * 2;
            fillRR(ctx, coordPX, coordPY, coordPW, lh, r, BG);
            if (editing) drawEditLabel(ctx, tr, coordPX, coordPY, coordPW, lh, "coord");
            int ty = coordPY + (lh - fh) / 2;
            ctx.drawText(tr, label, coordPX + pad,                      ty, GREY,  false);
            ctx.drawText(tr, vals,  coordPX + pad + tr.getWidth(label), ty, WHITE, false);
        }

        if (showRadar.getValue()) {
            int cx = radarPX + RADAR_R, cy = radarPY + RADAR_R;
            drawRadarAt(ctx, mc, animT, cx, cy, RADAR_R, editing);
        }

        if (showModuleList.getValue()) {
            var mgr = HorizonClient.getInstance().getModuleManager();
            List<Module> mods = mgr.getModules().stream()
                    .filter(mod -> mod.isEnabled() && !(mod instanceof HudModule))
                    .sorted(Comparator.comparingInt(mod ->
                            -tr.getWidth(lowercase.getValue() ? mod.getName().toLowerCase() : mod.getName())))
                    .collect(Collectors.toList());
            if (!mods.isEmpty()) {
                int ry = m;
                for (Module mod : mods) {
                    String name = lowercase.getValue() ? mod.getName().toLowerCase() : mod.getName();
                    int pw = tr.getWidth(name) + pad * 2;
                    int bx = sw - m - pw;
                    fillRR(ctx, bx, ry, pw, lh, r, BG);
                    ctx.drawText(tr, name, bx + pad, ry + (lh - fh) / 2, WHITE, false);
                    fillVGrad(ctx, bx + pw - 2, ry, 2, lh, WHITE, 0xFFAAAAAA);
                    ry += lh + 2;
                }
            }
        }
    }

    public boolean onMouseClick(double mx, double my, int button) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) return false;

        var tr = mc.textRenderer;
        int fh = tr.fontHeight, lh = fh + 6, pad = 5;
        int imx = (int) mx, imy = (int) my;

        if (button != 0) return false;

        if (showRadar.getValue()) {
            int rX = radarPX, rY = radarPY;
            int rW = RADAR_R * 2, rH = RADAR_R * 2;
            if (imx >= rX && imx < rX + rW && imy >= rY && imy < rY + rH) {
                dragging = "radar"; dragOffX = imx - rX; dragOffY = imy - rY; return true;
            }
        }
        if (showCoords.getValue()) {
            int cX = coordPX, cY = coordPY;
            int cW = coordWidth(mc, tr, pad);
            if (imx >= cX && imx < cX + cW && imy >= cY && imy < cY + lh) {
                dragging = "coord"; dragOffX = imx - cX; dragOffY = imy - cY; return true;
            }
        }

        {
            var parts = buildInfoParts(mc, tr);
            if (!parts.isEmpty()) {
                int iX = infoPX, iY = infoPY;
                int iW = infoWidth(tr, parts, pad);
                if (imx >= iX && imx < iX + iW && imy >= iY && imy < iY + lh) {
                    dragging = "info"; dragOffX = imx - iX; dragOffY = imy - iY; return true;
                }
            }
        }
        if (showWatermark.getValue()) {
            int wX = wmPX, wY = wmPY;
            int wW = tr.getWidth("HORIZON") + pad * 2;
            if (imx >= wX && imx < wX + wW && imy >= wY && imy < wY + lh) {
                dragging = "watermark"; dragOffX = imx - wX; dragOffY = imy - wY; return true;
            }
        }
        return false;
    }

    public boolean onMouseDrag(double mx, double my) {
        if (dragging == null) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        var tr = mc.textRenderer;
        int pad = 5;

        int nx = (int) Math.round(mx - dragOffX);
        int ny = (int) Math.round(my - dragOffY);

        int px = snapToGrid(nx);
        int py = snapToGrid(ny);

        int lh = tr.fontHeight + 6;
        int elemW, elemH;
        switch (dragging) {
            case "watermark" -> { elemW = tr.getWidth("HORIZON") + pad * 2; elemH = lh; }
            case "info"      -> {
                var parts = buildInfoParts(mc, tr);
                elemW = parts.isEmpty() ? GRID * 2 : infoWidth(tr, parts, pad);
                elemH = lh;
            }
            case "coord"     -> { elemW = coordWidth(mc, tr, pad); elemH = lh; }
            case "radar"     -> { elemW = RADAR_R * 2; elemH = RADAR_R * 2; }
            default          -> { elemW = GRID; elemH = GRID; }
        }

        px = Math.max(0, Math.min(px, sw - elemW));
        py = Math.max(0, Math.min(py, sh - elemH));

        switch (dragging) {
            case "watermark" -> { wmPX    = px; wmPY    = py; }
            case "info"      -> { infoPX  = px; infoPY  = py; }
            case "coord"     -> { coordPX = px; coordPY = py; }
            case "radar"     -> { radarPX = px; radarPY = py; }
        }
        return true;
    }

    public boolean onMouseRelease(double mx, double my, int button) {
        if (dragging != null) {
            dragging = null;
            HorizonClient inst = HorizonClient.getInstance();
            if (inst != null) inst.saveConfig();
            return true;
        }
        return false;
    }

    private static int snapToGrid(int v) {

        return (int) Math.round((double) v / GRID) * GRID;
    }

    private static void drawEditLabel(DrawContext ctx, net.minecraft.client.font.TextRenderer tr,
                                       int x, int y, int w, int h, String name) {
        ctx.fill(x,     y,     x + w, y + 1,     0xAAFFFFFF);
        ctx.fill(x,     y + h - 1, x + w, y + h, 0xAAFFFFFF);
        ctx.fill(x,     y,     x + 1, y + h,     0xAAFFFFFF);
        ctx.fill(x + w - 1, y, x + w, y + h,     0xAAFFFFFF);
    }

    private static void drawGrid(DrawContext ctx, int sw, int sh) {
        int gridColor = 0x18FFFFFF;
        for (int x = 0; x < sw; x += GRID) ctx.fill(x, 0, x + 1, sh, gridColor);
        for (int y = 0; y < sh; y += GRID) ctx.fill(0, y, sw, y + 1, gridColor);
    }

    public int getWmPX()    { return wmPX;    } public void setWmPX(int v)    { wmPX    = v; }
    public int getWmPY()    { return wmPY;    } public void setWmPY(int v)    { wmPY    = v; }
    public int getInfoPX()  { return infoPX;  } public void setInfoPX(int v)  { infoPX  = v; }
    public int getInfoPY()  { return infoPY;  } public void setInfoPY(int v)  { infoPY  = v; }
    public int getCoordPX() { return coordPX; } public void setCoordPX(int v) { coordPX = v; }
    public int getCoordPY() { return coordPY; } public void setCoordPY(int v) { coordPY = v; }
    public int getRadarPX() { return radarPX; } public void setRadarPX(int v) { radarPX = v; }
    public int getRadarPY() { return radarPY; } public void setRadarPY(int v) { radarPY = v; }

    @Override
    public void resetToDefaults() {
        super.resetToDefaults();
        wmPX    = DEFAULT_WM_PX;    wmPY    = DEFAULT_WM_PY;
        infoPX  = DEFAULT_INFO_PX;  infoPY  = DEFAULT_INFO_PY;
        coordPX = DEFAULT_COORD_PX; coordPY = DEFAULT_COORD_PY;
        radarPX = DEFAULT_RADAR_PX; radarPY = DEFAULT_RADAR_PY;
        HorizonClient inst = HorizonClient.getInstance();
        if (inst != null) inst.saveConfig();
    }

    private List<String[]> buildInfoParts(MinecraftClient mc, net.minecraft.client.font.TextRenderer tr) {
        var parts = new ArrayList<String[]>();
        if (showFPS.getValue())  parts.add(new String[]{String.valueOf(mc.getCurrentFps()), "fps"});
        if (showPing.getValue()) {
            long ping = getPing(mc);
            parts.add(new String[]{ping < 0 ? "N/A" : String.valueOf(ping), "ms"});
        }
        return parts;
    }

    private int infoWidth(net.minecraft.client.font.TextRenderer tr, List<String[]> parts, int pad) {
        int w = 0;
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) w += tr.getWidth("  ");
            w += tr.getWidth(parts.get(i)[0]);
            if (parts.get(i)[1] != null) w += tr.getWidth(parts.get(i)[1]);
        }
        return w + pad * 2;
    }

    private int coordWidth(MinecraftClient mc, net.minecraft.client.font.TextRenderer tr, int pad) {
        if (mc.player == null) return 0;
        String label = "XYZ";
        String vals  = "  " + (int) mc.player.getX() + "  " + (int) mc.player.getY() + "  " + (int) mc.player.getZ();
        return tr.getWidth(label) + tr.getWidth(vals) + pad * 2;
    }

    private void drawRadarAt(DrawContext ctx, MinecraftClient mc, float animT,
                              int cx, int cy, int radarR, boolean editing) {
        if (mc.player == null || mc.world == null) return;
        double range = radarRange.getValue();

        fillCircle(ctx, cx, cy, radarR, BG);
        drawRing(ctx, cx, cy, (int)(radarR * 0.50f), RING_COL);
        drawRing(ctx, cx, cy, (int)(radarR * 0.75f), RING_COL);
        drawSweep(ctx, cx, cy, radarR - 1, (animT * 90f) % 360f);

        var tr = mc.textRenderer;
        float yaw    = (mc.player.getYaw() % 360 + 360) % 360;
        float yawRad = (float) Math.toRadians(-yaw);

        String[] cardinals = {"N","S","E","W"};
        float[] cdx = {0,0,1,-1}, cdz = {-1,1,0,0};
        for (int i = 0; i < 4; i++) {
            float sx = cdx[i]*(float)Math.cos(yawRad) - cdz[i]*(float)Math.sin(yawRad);
            float sz = cdx[i]*(float)Math.sin(yawRad) + cdz[i]*(float)Math.cos(yawRad);
            ctx.drawText(tr, cardinals[i],
                    cx + (int)(sx*(radarR-6)) - tr.getWidth(cardinals[i])/2,
                    cy - (int)(sz*(radarR-6)) - tr.fontHeight/2,
                    GREY, false);
        }

        double px = mc.player.getX(), pz = mc.player.getZ();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            double dx = p.getX()-px, dz = p.getZ()-pz;
            if (Math.sqrt(dx*dx+dz*dz) > range) continue;
            float sx = (float)(dx*Math.cos(yawRad) - dz*Math.sin(yawRad));
            float sz = (float)(dx*Math.sin(yawRad) + dz*Math.cos(yawRad));
            int dotX = cx + (int)(sx/range*(radarR-8));
            int dotY = cy - (int)(sz/range*(radarR-8));
            float od = (float)Math.sqrt((dotX-cx)*(double)(dotX-cx) + (dotY-cy)*(double)(dotY-cy));
            if (od > radarR-7) { dotX = cx+(int)((dotX-cx)/od*(radarR-7)); dotY = cy+(int)((dotY-cy)/od*(radarR-7)); }
            fillCircle(ctx, dotX, dotY, 2, DOT_OTH);
        }
        fillCircle(ctx, cx, cy, 3, DOT_SELF);
        drawRing(ctx, cx, cy, radarR,   0xFF1A1A1A);
        drawRing(ctx, cx, cy, radarR-1, RING_COL);

        if (editing) {
            ctx.fill(cx-radarR,   cy-radarR,   cx+radarR,   cy-radarR+1, 0xAAFFFFFF);
            ctx.fill(cx-radarR,   cy+radarR-1, cx+radarR,   cy+radarR,   0xAAFFFFFF);
            ctx.fill(cx-radarR,   cy-radarR,   cx-radarR+1, cy+radarR,   0xAAFFFFFF);
            ctx.fill(cx+radarR-1, cy-radarR,   cx+radarR,   cy+radarR,   0xAAFFFFFF);
        }
    }

    private static void drawSweep(DrawContext ctx, int cx, int cy, int r, float angleDeg) {
        Matrix4f mat = ctx.getMatrices().peek().getPositionMatrix();
        beginDraw();
        BufferBuilder buf = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        float end = (float)Math.toRadians(angleDeg-90f), start = end-(float)Math.toRadians(55f);
        buf.vertex(mat,cx,cy,0).color(0x00AAAAAA);
        for (int i=0;i<=24;i++){
            float a=start+(end-start)*i/24f;
            int alpha=(int)((float)i/24f*40);
            buf.vertex(mat,cx+(float)(Math.cos(a)*r),cy+(float)(Math.sin(a)*r),0).color((alpha<<24)|0xAAAAAA);
        }
        flush(buf); endDraw();
    }

    static void fillRR(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        if (w<=0||h<=0) return;
        r=Math.min(r,Math.min(w/2,h/2));
        int step=r<=4?2:5;
        Matrix4f mat=ctx.getMatrices().peek().getPositionMatrix();
        beginDraw();
        BufferBuilder buf=Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN,VertexFormats.POSITION_COLOR);
        float aA=(color>>24&0xFF)/255f,aR=(color>>16&0xFF)/255f,aG=(color>>8&0xFF)/255f,aB=(color&0xFF)/255f;
        buf.vertex(mat,x+w/2f,y+h/2f,0).color(aR,aG,aB,aA);
        int[][] cc={{x+w-r,y+r},{x+w-r,y+h-r},{x+r,y+h-r},{x+r,y+r}};
        int[] sd={-90,0,90,180};
        for(int c=0;c<4;c++){
            int s=sd[c];
            for(int i=s;i<=s+90;i+=step){float a=(float)Math.toRadians(i);buf.vertex(mat,cc[c][0]+(float)(Math.cos(a)*r),cc[c][1]+(float)(Math.sin(a)*r),0).color(aR,aG,aB,aA);}
            float ae=(float)Math.toRadians(s+90);buf.vertex(mat,cc[c][0]+(float)(Math.cos(ae)*r),cc[c][1]+(float)(Math.sin(ae)*r),0).color(aR,aG,aB,aA);
        }
        float a0=(float)Math.toRadians(-90);buf.vertex(mat,cc[0][0]+(float)(Math.cos(a0)*r),cc[0][1]+(float)(Math.sin(a0)*r),0).color(aR,aG,aB,aA);
        flush(buf); endDraw();
    }

    private static void fillCircle(DrawContext ctx, int cx, int cy, int r, int color) {
        if(r<=0) return;
        Matrix4f mat=ctx.getMatrices().peek().getPositionMatrix();
        beginDraw();
        BufferBuilder buf=Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN,VertexFormats.POSITION_COLOR);
        buf.vertex(mat,cx,cy,0).color(color);
        int step=r<=6?3:r<=20?5:8;
        for(int i=0;i<=360;i+=step){float a=(float)Math.toRadians(i);buf.vertex(mat,cx+(float)(Math.cos(a)*r),cy+(float)(Math.sin(a)*r),0).color(color);}
        flush(buf); endDraw();
    }

    private static void drawRing(DrawContext ctx, int cx, int cy, int r, int color) {
        if(r<=0) return;
        Matrix4f mat=ctx.getMatrices().peek().getPositionMatrix();
        beginDraw();
        BufferBuilder buf=Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES,VertexFormats.POSITION_COLOR);
        for(int i=0;i<360;i+=6){
            float a0=(float)Math.toRadians(i),a1=(float)Math.toRadians(i+6);
            buf.vertex(mat,cx+(float)(Math.cos(a0)*r),cy+(float)(Math.sin(a0)*r),0).color(color);
            buf.vertex(mat,cx+(float)(Math.cos(a1)*r),cy+(float)(Math.sin(a1)*r),0).color(color);
        }
        flush(buf); endDraw();
    }

    static void fillVGrad(DrawContext ctx, int x, int y, int w, int h, int cT, int cB) {
        if(w<=0||h<=0) return;
        Matrix4f mat=ctx.getMatrices().peek().getPositionMatrix();
        beginDraw();
        BufferBuilder buf=Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
        buf.vertex(mat,x,  y,  0).color(cT); buf.vertex(mat,x+w,y,  0).color(cT);
        buf.vertex(mat,x+w,y+h,0).color(cB); buf.vertex(mat,x,  y+h,0).color(cB);
        flush(buf); endDraw();
    }

    static void beginDraw() {
        RenderSystem.disableCull(); RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.setShaderColor(1f,1f,1f,1f);
    }
    static void endDraw() { RenderSystem.enableCull(); RenderSystem.disableBlend(); }
    static void flush(BufferBuilder buf) { BuiltBuffer b=buf.endNullable(); if(b!=null) BufferRenderer.drawWithGlobalProgram(b); }

    private long getPing(MinecraftClient mc) {
        if(mc.getNetworkHandler()==null||mc.player==null) return -1;
        var e=mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return e==null?-1:e.getLatency();
    }
}
