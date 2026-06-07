package dev.horizon.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.horizon.client.HorizonClient;
import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

public class HorizonScreen extends Screen {

private enum Category {
        RENDER("Render","*"), MOVEMENT("Movement",">"),
        MISC("Misc","~"), CLIENT("Client","#"),
        PVP("PvP","!"), DEBUG("Debug","?");
        final String label, icon;
        Category(String l, String i){label=l;icon=i;}
    }

    private static Category categoryOf(Module m) {
        return switch (m.getName()) {
            case "Storage ESP","Ore ESP","Player ESP","Spawner ESP",
                 "Sus Chunk Finder","Prime Chunk Finder","Item ESP","Bedrock Holes" -> Category.RENDER;
            case "AutoSprint","FreeCam"                                              -> Category.MOVEMENT;
            case "FullBright","Hand View","FreeLook","Name Protect"                 -> Category.MISC;
            case "HUD", "Horizon"                                                    -> Category.CLIENT;
            case "Horizon Debug","Block Entity Debug","Relog Debug",
                 "Spawner Debug","Activity Debug","Deepslate Debug"                 -> Category.DEBUG;
            case "CrystalMacro","AnchorMacro","AimAssist","TriggerBot",
                 "HoverTotem","Inv Totem","KillAura"                                -> Category.PVP;
            default                                                                  -> Category.MISC;
        };
    }

    private int gx, gy;
    private static Category selCat = Category.RENDER;
    private Module openMod = null;
    private Module bindingMod = null;
    private Setting<?> editStr = null;
    private StringBuilder editBuf = new StringBuilder();
    private long editStartMs;
    private int modScroll, setScroll, lastSetH;
    private Setting<?> dragSlider;
    private int dragSX, dragSW;
    private int bindRowY = -1, rstRowY = -1;
    private final List<SettingRow> setRows = new ArrayList<>();
    private record SettingRow(Setting<?> s, int y){}
    private final Map<String, Float> anim = new HashMap<>();

    public HorizonScreen(){super(Text.literal("Horizon"));}

    @Override protected void init(){gx=(width-480)/2; gy=(height-300)/2;}

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta){
        ctx.fill(0,0,width,height,0x90000000);
        setRows.clear(); bindRowY=-1; rstRowY=-1;

        fillRR(ctx, gx-4,gy-4,480+8,300+8, 12+4, 0x18FFFFFF);
        fillRR(ctx, gx-2,gy-2,480+4,300+4, 12+2, 0x28FFFFFF);

        fillRR(ctx, gx,gy,480,300, 12, 0xF2111111);

        fillRRLeft(ctx, gx,gy,98,300, 12, 0xFF141414);

        fillRRRight(ctx, gx+98,gy, 480-98,300, 12, 0xFF141414);

        int _sbH = 300-2, _sbMid = _sbH/2;
        fillVGrad(ctx, gx+98, gy+1,        1, _sbMid,       0x00FFFFFF, 0xFFFFFFFF);
        fillVGrad(ctx, gx+98, gy+1+_sbMid, 1, _sbH-_sbMid,  0xFFFFFFFF,  0x10FFFFFF);

        drawTitleBar(ctx);
        drawCatPanel(ctx,mx,my);
        if(openMod==null) drawModList(ctx,mx,my);
        else              drawSetPanel(ctx,mx,my);

        for(var c:children())
            if(c instanceof net.minecraft.client.gui.Drawable d) d.render(ctx,mx,my,delta);

        if(bindingMod!=null) drawBindOverlay(ctx);
    }

    private void drawTitleBar(DrawContext ctx){
        ctx.drawTextWithShadow(textRenderer,Text.literal("HORIZON"), gx+10,gy+9, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer,Text.literal("v3"),
                gx+10+textRenderer.getWidth("HORIZON")+4, gy+11, 0xFF666666);
        fillHGrad(ctx, gx+10,gy+22, 98-10*2,1, 0xFFFFFFFF,0x00000000);
    }

    private void drawCatPanel(DrawContext ctx, int mx, int my){
        int cx=gx+10, cy=gy+30;
        for(Category cat:sortedCats()){
            boolean sel=cat==selCat;
            boolean hov=hov(mx,my,cx,cy,98-10*2,24);
            String k="cat_"+cat.name();
            float t=animStep(k,hov||sel);
            if(t>0.01f){
                int bg=lerpC(0x00000000,sel?0xFF222222:0xFF1A1A1A,t);
                fillRR(ctx,cx,cy,98-10*2,24,5,bg);
            }
            if(sel){

                fillVGrad(ctx,cx,cy+3,2,24-6,0xFFFFFFFF,0xFFAAAAAA);
                fillHGrad(ctx,cx+2,cy+3,8,24-6,0x30FFFFFF,0x00000000);
            }
            int ic=sel?0xFFFFFFFF:lerpC(0xFF666666,0xFFAAAAAA,t);
            int tc=sel?0xFFF0F0F0:lerpC(0xFF666666,0xFFAAAAAA,t);
            ctx.drawTextWithShadow(textRenderer,Text.literal(cat.icon), cx+8, cy+(24-8)/2, ic);
            ctx.drawTextWithShadow(textRenderer,Text.literal(cat.label),cx+20,cy+(24-8)/2, tc);
            cy+=24+2;
        }
    }

    private void drawModList(DrawContext ctx, int mx, int my){
        var mods=modsIn(selCat);
        int rx=gx+98+10, ry=gy+30, rw=480-98-10*2, cH=300-36;
        int totH=mods.size()*(24+2);
        modScroll=clamp(modScroll,0,Math.max(0,totH-cH));
        ctx.enableScissor(gx+98+2,ry,gx+480-2,ry+cH);
        for(int i=0;i<mods.size();i++){
            Module m=mods.get(i);
            int iy=ry+i*(24+2)-modScroll;
            boolean hov=hov(mx,my,rx,iy,rw-6,24);
            boolean en=m.isEnabled();
            float t=animStep("m_"+i+"_"+selCat.name(),hov);
            if(t>0.01f) fillRR(ctx,rx,iy,rw-6,24,5,lerpC(0x00000000,0xFF1E1E1E,t));

            if(en){
                fillRR(ctx,rx+4,iy+24/2-5,10,10,5,0x30FFFFFF);
                fillRR(ctx,rx+6,iy+24/2-3,6,6,3,0xFFFFFFFF);
            } else {
                fillRR(ctx,rx+6,iy+24/2-3,6,6,3,0xFF2A2A2A);
            }
            ctx.drawTextWithShadow(textRenderer,Text.literal(m.getName()),
                    rx+18,iy+(24-8)/2, en?0xFFF0F0F0:0xFFAAAAAA);
            if(t>0.1f){
                int ac=lerpC(0x00000000,0xFF666666,t);
                String h="settings";
                ctx.drawTextWithShadow(textRenderer,Text.literal(h),
                        rx+rw-textRenderer.getWidth(h)-18,iy+(24-8)/2,ac);
                ctx.drawTextWithShadow(textRenderer,Text.literal(">"),rx+rw-16,iy+(24-8)/2,ac);
            }
        }
        ctx.disableScissor();
        if(totH>cH) drawScrollbar(ctx,gx+480-5,ry,cH,totH,modScroll);
    }

    private void drawSetPanel(DrawContext ctx, int mx, int my){
        int rx=gx+98+10, ry=gy+28, rw=480-98-10*2;

        String back="< Back";
        float bt=animStep("back",hov(mx,my,rx,ry,textRenderer.getWidth(back)+10,12));
        ctx.drawTextWithShadow(textRenderer,Text.literal(back),rx,ry+2,lerpC(0xFF666666,0xFFFFFFFF,bt));

        int hY=ry+18;
        ctx.drawTextWithShadow(textRenderer,Text.literal(openMod.getName()),rx,hY,0xFFF0F0F0);
        ctx.drawTextWithShadow(textRenderer,Text.literal(openMod.getDescription()),rx,hY+11,0xFFAAAAAA);
        int togX=gx+480-10-40;
        drawToggle(ctx,togX,hY+1,openMod.isEnabled(),hov(mx,my,togX,hY+1,32,14));
        fillHGrad(ctx,rx,hY+25,rw-4,1,0xFFFFFFFF,0x00000000);

        int cTop=hY+32, cH=300-(cTop-gy)-8;
        setScroll=clamp(setScroll,0,Math.max(0,lastSetH-cH));
        ctx.enableScissor(gx+98+2,cTop-4,gx+480-2,cTop+cH);
        int sy=cTop-setScroll;
        for(Setting<?> s:openMod.getSettings()){
            setRows.add(new SettingRow(s,sy));
            sy=drawSetting(ctx,mx,my,s,rx,sy,rw-6);
            sy+=8;
        }

        if(!openMod.getSettings().isEmpty()){ctx.fill(rx,sy,rx+rw-4,sy+1,0xFF3A3A3A);sy+=10;}

        bindRowY=sy;
        ctx.drawTextWithShadow(textRenderer,Text.literal("Keybind"),rx,sy+1,0xFFAAAAAA);
        String bl=openMod.getKeybindName();
        int blW=textRenderer.getWidth(bl);
        int bpx=rx+rw-blW-26;
        int bpw=blW+16;
        float kt=animStep("keybind",hov(mx,my,bpx,sy-2,bpw,14));
        fillRR(ctx,bpx,sy-2,bpw,14,4,lerpC(0xFF111111,0xFF1E1E1E,kt));
        fillHGrad(ctx,bpx,sy-2,bpw,1,lerpC(0xFF444444,0xFFFFFFFF,kt),lerpC(0xFF444444,0xFFAAAAAA,kt));
        ctx.drawTextWithShadow(textRenderer,Text.literal(bl),bpx+8,sy+1,lerpC(0xFFCCCCCC,0xFFFFFFFF,kt));
        sy+=18;

        rstRowY=sy;
        String rl="Reset Defaults";
        int rlW=textRenderer.getWidth(rl);
        int rpx=rx+(rw-4)/2-rlW/2;
        float rt=animStep("reset",hov(mx,my,rpx-6,sy-2,rlW+12,14));
        fillRR(ctx,rpx-6,sy-2,rlW+12,14,4,lerpC(0xFF111111,0xFF1E0808,rt));
        fillHGrad(ctx,rpx-6,sy-2,rlW+12,1,lerpC(0xFF444444,0xFFAA4040,rt),lerpC(0xFF444444,0xFF884040,rt));
        ctx.drawTextWithShadow(textRenderer,Text.literal(rl),rpx,sy+1,lerpC(0xFF666666,0xFFEE8888,rt));
        sy+=16;

        ctx.disableScissor();
        lastSetH=sy-cTop+setScroll;
        if(lastSetH>cH) drawScrollbar(ctx,gx+480-5,cTop,cH,lastSetH,setScroll);
    }

    private void drawBindOverlay(DrawContext ctx){
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0,0,400);
        ctx.fill(0,0,width,height,0xC0000000);
        String m1="Binding: "+bindingMod.getName();
        String m2="Press any key  |  ESC = cancel  |  DEL = clear";
        int cx2=gx+480/2,cy2=gy+300/2;
        int w1=textRenderer.getWidth(m1),w2=textRenderer.getWidth(m2);
        int bw=Math.max(w1,w2)+28,bh=42,bx=cx2-bw/2,by=cy2-bh/2;
        fillRR(ctx,bx-2,by-2,bw+4,bh+4,8,0x30FFFFFF);
        fillRR(ctx,bx,by,bw,bh,7,0xFF111111);
        fillHGrad(ctx,bx+7,by,bw-14,1,0xFFFFFFFF,0xFFAAAAAA);
        fillHGrad(ctx,bx+7,by+bh-1,bw-14,1,0xFFFFFFFF,0xFFAAAAAA);
        ctx.drawTextWithShadow(textRenderer,Text.literal(m1),cx2-w1/2,by+9,0xFFF0F0F0);
        ctx.drawTextWithShadow(textRenderer,Text.literal(m2),cx2-w2/2,by+24,0xFFAAAAAA);
        ctx.getMatrices().pop();
    }

    private int drawSetting(DrawContext ctx, int mx, int my, Setting<?> s, int x, int y, int w){
        ctx.drawTextWithShadow(textRenderer,Text.literal(s.getName()),x,y,0xFFF0F0F0);
        return switch(s.getType()){
            case BOOLEAN -> {
                drawToggle(ctx,x+w-34,y-1,(Boolean)s.getValue(),hov(mx,my,x+w-36,y-2,32,14));
                yield y+14;
            }
            case SLIDER -> {
                double val=(Double)s.getValue(),mn=(Double)s.getMin(),mx2=(Double)s.getMax();
                double pct=(val-mn)/(mx2-mn);
                int tY=y+13,tX=x,tW=w-4,fW=(int)(tW*pct);
                fillRR(ctx,tX,tY,tW,4,2,0xFF1E1E1E);
                if(fW>2) fillHGrad(ctx,tX,tY,fW,4,0xFFFFFFFF,0xFFBBBBBB);

                fillRR(ctx,tX+fW-5,tY-3,10,10,5,0x30FFFFFF);
                fillRR(ctx,tX+fW-3,tY-1,6,6,3,0xFFFFFFFF);
                String vs=String.valueOf((int)Math.round(val));
                ctx.drawTextWithShadow(textRenderer,Text.literal(vs),x+w-textRenderer.getWidth(vs),y,0xFFAAAAAA);
                yield y+26;
            }
            case STRING -> {
                boolean ed=editStr==s;
                String cur=ed?editBuf.toString():(String)s.getValue();
                boolean showCursor=ed&&((System.currentTimeMillis()-editStartMs)%900<450);
                String disp=cur+(showCursor?"|":(ed?" ":""));
                int bH=15,bY=y+12;
                fillRR(ctx,x-1,bY-1,w+2,bH+2,4,ed?0xFFCCCCCC:0xFF444444);
                fillRR(ctx,x,bY,w,bH,3,ed?0xFF1E1E1E:0xFF141414);
                String vis=disp;
                while(vis.length()>1&&textRenderer.getWidth(vis)>w-10) vis=vis.substring(1);
                ctx.drawTextWithShadow(textRenderer,Text.literal(vis),x+5,bY+4,ed?0xFFF0F0F0:0xFFAAAAAA);
                if(ed){String h="Enter ^";ctx.drawTextWithShadow(textRenderer,Text.literal(h),x+w-textRenderer.getWidth(h),y,0xFF55CC88);}
                yield bY+bH+3;
            }
            default -> y+14;
        };
    }

    private void drawToggle(DrawContext ctx, int x, int y, boolean on, boolean hov){
        int tw=32,th=14,r2=th/2;
        if(on){

            fillRR(ctx,x,y,tw,th,r2,0xFFFFFFFF);

            if(tw>r2*2) fillHGrad(ctx,x+r2,y,tw-r2*2,th,0xFFFFFFFF,0xFFBBBBBB);

            fillRR(ctx,x+tw-r2*2,y,r2*2,th,r2,0xFFBBBBBB);
        } else {
            fillRR(ctx,x,y,tw,th,r2,0xFF2A2A2A);
        }
        int kx=on?x+tw-th+1:x+1;
        if(on) fillRR(ctx,kx-1,y,th,th,r2,0x30FFFFFF);
        fillRR(ctx,kx,y+1,th-2,th-2,(th-2)/2,0xFFFFFFFF);
    }

    private void drawScrollbar(DrawContext ctx, int x, int y, int cH, int totH, int scroll){
        int bH=Math.max(20,cH*cH/totH);
        int bY=y+scroll*(cH-bH)/Math.max(1,totH-cH);
        fillRR(ctx,x,y,3,cH,1,0xFF0F0F0F);
        fillRR(ctx,x,bY,3,bH,1,0xFFCCCCCC);
    }

    private static void beginDraw(){
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.setShaderColor(1f,1f,1f,1f);
    }

    private static void endDraw(){
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void flush(BufferBuilder buf){
        BuiltBuffer built = buf.endNullable();
        if(built!=null) BufferRenderer.drawWithGlobalProgram(built);
    }

    private static void fillRR(DrawContext ctx, int x, int y, int w, int h, int r, int color){
        if(w<=0||h<=0) return;
        r=Math.min(r,Math.min(w,h)/2);

        int step = r<=6 ? 2 : r<=12 ? 5 : 10;
        Matrix4f mat=ctx.getMatrices().peek().getPositionMatrix();
        beginDraw();
        BufferBuilder buf=Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN,VertexFormats.POSITION_COLOR);
        buf.vertex(mat, x+w/2f, y+h/2f, 0).color(color);
        int[][] cc={{x+w-r,y+r},{x+w-r,y+h-r},{x+r,y+h-r},{x+r,y+r}};
        for(int c=0;c<4;c++){
            int s=(c-1)*90, e=s+90;
            for(int i=s;i<=e;i+=step){
                float a=(float)Math.toRadians(i);
                buf.vertex(mat, cc[c][0]+(float)(Math.cos(a)*r), cc[c][1]+(float)(Math.sin(a)*r), 0).color(color);
            }

            float a=(float)Math.toRadians(e);
            buf.vertex(mat, cc[c][0]+(float)(Math.cos(a)*r), cc[c][1]+(float)(Math.sin(a)*r), 0).color(color);
        }

        float a0=(float)Math.toRadians(-90);
        buf.vertex(mat, cc[0][0]+(float)(Math.cos(a0)*r), cc[0][1]+(float)(Math.sin(a0)*r), 0).color(color);
        flush(buf);
        endDraw();
    }

    private static void fillRRLeft(DrawContext ctx, int x, int y, int w, int h, int r, int color){
        if(w<=0||h<=0) return;
        r=Math.min(r,Math.min(w,h)/2);
        int step = r<=6 ? 2 : r<=12 ? 5 : 10;
        Matrix4f mat=ctx.getMatrices().peek().getPositionMatrix();
        beginDraw();
        BufferBuilder buf=Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN,VertexFormats.POSITION_COLOR);
        buf.vertex(mat,x+w/2f,y+h/2f,0).color(color);

        buf.vertex(mat,x+w,y,0).color(color);
        buf.vertex(mat,x+w,y+h,0).color(color);

        int[] blc={x+r,y+h-r};
        for(int i=90;i<=180;i+=step){float a=(float)Math.toRadians(i);buf.vertex(mat,blc[0]+(float)(Math.cos(a)*r),blc[1]+(float)(Math.sin(a)*r),0).color(color);}
        buf.vertex(mat,blc[0]+(float)(Math.cos(Math.toRadians(180))*r),blc[1]+(float)(Math.sin(Math.toRadians(180))*r),0).color(color);

        int[] tlc={x+r,y+r};
        for(int i=180;i<=270;i+=step){float a=(float)Math.toRadians(i);buf.vertex(mat,tlc[0]+(float)(Math.cos(a)*r),tlc[1]+(float)(Math.sin(a)*r),0).color(color);}
        buf.vertex(mat,tlc[0]+(float)(Math.cos(Math.toRadians(270))*r),tlc[1]+(float)(Math.sin(Math.toRadians(270))*r),0).color(color);        buf.vertex(mat,x+w,y,0).color(color);
        flush(buf);
        endDraw();
    }

    private static void fillRRRight(DrawContext ctx, int x, int y, int w, int h, int r, int color){
        if(w<=0||h<=0) return;
        r=Math.min(r,Math.min(w,h)/2);
        int step = r<=6 ? 2 : r<=12 ? 5 : 10;
        Matrix4f mat=ctx.getMatrices().peek().getPositionMatrix();
        beginDraw();
        BufferBuilder buf=Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN,VertexFormats.POSITION_COLOR);
        buf.vertex(mat,x+w/2f,y+h/2f,0).color(color);

        buf.vertex(mat,x,y,0).color(color);

        int[] trc={x+w-r,y+r};
        for(int i=-90;i<=0;i+=step){float a=(float)Math.toRadians(i);buf.vertex(mat,trc[0]+(float)(Math.cos(a)*r),trc[1]+(float)(Math.sin(a)*r),0).color(color);}
        buf.vertex(mat,trc[0]+(float)(Math.cos(0)*r),trc[1]+(float)(Math.sin(0)*r),0).color(color);

        int[] brc={x+w-r,y+h-r};
        for(int i=0;i<=90;i+=step){float a=(float)Math.toRadians(i);buf.vertex(mat,brc[0]+(float)(Math.cos(a)*r),brc[1]+(float)(Math.sin(a)*r),0).color(color);}
        buf.vertex(mat,brc[0]+(float)(Math.cos(Math.toRadians(90))*r),brc[1]+(float)(Math.sin(Math.toRadians(90))*r),0).color(color);

        buf.vertex(mat,x,y+h,0).color(color);
        buf.vertex(mat,x,y,0).color(color);
        flush(buf);
        endDraw();
    }

    private static void fillHGrad(DrawContext ctx, int x, int y, int w, int h, int cL, int cR){
        if(w<=0||h<=0) return;
        Matrix4f mat=ctx.getMatrices().peek().getPositionMatrix();
        beginDraw();
        BufferBuilder buf=Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
        buf.vertex(mat,x,    y,   0).color(cL);
        buf.vertex(mat,x,    y+h, 0).color(cL);
        buf.vertex(mat,x+w,  y+h, 0).color(cR);
        buf.vertex(mat,x+w,  y,   0).color(cR);
        flush(buf);
        endDraw();
    }

    private static void fillVGrad(DrawContext ctx, int x, int y, int w, int h, int cT, int cB){
        if(w<=0||h<=0) return;
        Matrix4f mat=ctx.getMatrices().peek().getPositionMatrix();
        beginDraw();
        BufferBuilder buf=Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
        buf.vertex(mat,x,   y,   0).color(cT);
        buf.vertex(mat,x+w, y,   0).color(cT);
        buf.vertex(mat,x+w, y+h, 0).color(cB);
        buf.vertex(mat,x,   y+h, 0).color(cB);
        flush(buf);
        endDraw();
    }

    @Override
    public boolean keyPressed(int kc,int sc,int mods){
        if(editStr!=null){
            switch(kc){
                case GLFW.GLFW_KEY_ESCAPE    ->{editStr=null;editBuf.setLength(0);return true;}
                case GLFW.GLFW_KEY_ENTER,
                     GLFW.GLFW_KEY_KP_ENTER  ->{commitStr();return true;}
                case GLFW.GLFW_KEY_BACKSPACE ->{if(editBuf.length()>0)editBuf.deleteCharAt(editBuf.length()-1);return true;}
                case GLFW.GLFW_KEY_DELETE    ->{editBuf.setLength(0);return true;}
            }
            return true;
        }
        if(bindingMod!=null){
            if(kc==GLFW.GLFW_KEY_ESCAPE) bindingMod=null;
            else if(kc==GLFW.GLFW_KEY_DELETE||kc==GLFW.GLFW_KEY_BACKSPACE){bindingMod.setKeybind(GLFW.GLFW_KEY_UNKNOWN);bindingMod=null;}
            else{bindingMod.setKeybind(kc);bindingMod=null;}
            return true;
        }
        if(kc==GLFW.GLFW_KEY_ESCAPE){
            if(openMod!=null){openMod=null;setScroll=0;}else close();
            return true;
        }
        return super.keyPressed(kc,sc,mods);
    }

    @Override
    public boolean charTyped(char ch,int mods){
        if(editStr!=null){if(ch>=32&&ch!=127)editBuf.append(ch);return true;}
        return super.charTyped(ch,mods);
    }

    @Override
    public boolean mouseScrolled(double mx,double my,double hA,double vA){
        if(mx>gx+98&&mx<gx+480&&my>gy&&my<gy+300){
            if(openMod==null)modScroll=Math.max(0,modScroll-(int)(vA*12));
            else             setScroll=Math.max(0,setScroll-(int)(vA*12));
            return true;
        }
        return super.mouseScrolled(mx,my,hA,vA);
    }

    @Override
    public boolean mouseClicked(double mxD,double myD,int btn){
        if(bindingMod!=null){bindingMod=null;return true;}
        if(editStr!=null) commitStr();
        int mx=(int)mxD,my=(int)myD;

        int cx=gx+10,cy=gy+30;
        for(Category cat:sortedCats()){
            if(hov(mx,my,cx,cy,98-10*2,24)){
                if(selCat!=cat){selCat=cat;openMod=null;modScroll=0;setScroll=0;}
                return true;
            }
            cy+=24+2;
        }

        if(openMod==null){
            var mods=modsIn(selCat);
            int rx=gx+98+10,ry=gy+30,rw=480-98-10*2;
            for(int i=0;i<mods.size();i++){
                Module m=mods.get(i);
                int iy=ry+i*(24+2)-modScroll;
                if(hov(mx,my,rx,iy,rw-6,24)){

                    int settingsTextW = textRenderer.getWidth("settings");
                    boolean onSettingsHint = hov(mx,my, rx+rw-settingsTextW-18, iy, settingsTextW+18, 24);
                    if(onSettingsHint || btn==1){openMod=m;setScroll=0;}
                    else if(btn==0) m.toggle();
                    return true;
                }
            }
        } else {
            int rx=gx+98+10,ry=gy+28,rw=480-98-10*2;
            String back="< Back";
            if(hov(mx,my,rx,ry,textRenderer.getWidth(back)+10,12)){openMod=null;setScroll=0;return true;}
            int hY=ry+18,togX=gx+480-10-40;
            if(hov(mx,my,togX,hY+1,32,14)){openMod.toggle();return true;}

            for(SettingRow sr:setRows){
                Setting<?> s=sr.s();int sy=sr.y();
                if(s.getType()==Setting.SettingType.BOOLEAN){
                    if(hov(mx,my,rx+rw-38,sy-2,32,14)){((Setting<Boolean>)s).setValue(!(Boolean)s.getValue());return true;}
                } else if(s.getType()==Setting.SettingType.SLIDER){
                    int tX=rx,tW=rw-4,tY=sy+13;
                    if(hov(mx,my,tX,tY-5,tW,14)){
                        double pct=clamp01((double)(mx-tX)/tW);
                        double mn=(Double)s.getMin(),mxV=(Double)s.getMax();
                        ((Setting<Double>)s).setValue((double)Math.round(mn+pct*(mxV-mn)));
                        dragSlider=s;dragSX=tX;dragSW=tW;return true;
                    }
                } else if(s.getType()==Setting.SettingType.STRING){
                    int bY=sy+12;
                    if(hov(mx,my,rx-1,bY-1,rw-3,17)){
                        if(editStr==s) commitStr();
                        else{editStr=s;editBuf=new StringBuilder((String)s.getValue());editStartMs=System.currentTimeMillis();}
                        return true;
                    }
                }
            }
            if(bindRowY>=0){
                String bl=openMod.getKeybindName();int blW=textRenderer.getWidth(bl);
                int bpx=rx+rw-blW-26;
                if(hov(mx,my,bpx,bindRowY-2,blW+16,14)){bindingMod=openMod;return true;}
            }
            if(rstRowY>=0){
                String rl="Reset Defaults";int rlW=textRenderer.getWidth(rl);
                int rpx=rx+(rw-4)/2-rlW/2;
                if(hov(mx,my,rpx-6,rstRowY-2,rlW+12,14)){openMod.resetToDefaults();return true;}
            }
        }
        return super.mouseClicked(mxD,myD,btn);
    }

    @Override
    public boolean mouseDragged(double mx,double my,int btn,double dX,double dY){
        if(dragSlider!=null&&dragSlider.getType()==Setting.SettingType.SLIDER){
            double pct=clamp01((mx-dragSX)/dragSW);
            double mn=(Double)dragSlider.getMin(),mxV=(Double)dragSlider.getMax();
            ((Setting<Double>)dragSlider).setValue((double)Math.round(mn+pct*(mxV-mn)));
            return true;
        }
        return super.mouseDragged(mx,my,btn,dX,dY);
    }

    @Override public boolean mouseReleased(double mx,double my,int btn){dragSlider=null;return super.mouseReleased(mx,my,btn);}

    private void commitStr(){if(editStr==null)return;((Setting<String>)editStr).setValue(editBuf.toString());editStr=null;editBuf.setLength(0);}
    private static boolean hov(int mx,int my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    private static double clamp01(double v){return Math.max(0,Math.min(1,v));}
    private float animStep(String k,boolean active){float t=anim.getOrDefault(k,0f);t=active?Math.min(1f,t+0.16f):Math.max(0f,t-0.10f);anim.put(k,t);return t;}
    private static int lerpC(int a,int b,float t){
        if(t<=0)return a;if(t>=1)return b;
        int aA=(a>>24)&0xFF,rA=(a>>16)&0xFF,gA=(a>>8)&0xFF,bA=a&0xFF;
        int aB=(b>>24)&0xFF,rB=(b>>16)&0xFF,gB=(b>>8)&0xFF,bB=b&0xFF;
        return((aA+(int)((aB-aA)*t))<<24)|((rA+(int)((rB-rA)*t))<<16)|((gA+(int)((gB-gA)*t))<<8)|(bA+(int)((bB-bA)*t));
    }
    private static Category[] sortedCats(){
        return Arrays.stream(Category.values()).sorted(Comparator.comparingInt((Category c)->c.label.length()).reversed()).toArray(Category[]::new);
    }
    private List<Module> modsIn(Category cat){
        return HorizonClient.getInstance().getModuleManager().getModules().stream()
                .filter(m->categoryOf(m)==cat)
                .sorted(Comparator.comparingInt((Module m)->m.getName().length()).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public void close(){
        if(editStr!=null) commitStr();
        var inst=dev.horizon.client.HorizonClient.getInstance();
        if(inst!=null) inst.getConfigManager().save();

        dev.horizon.client.modules.HorizonGuiModule guiMod =
                dev.horizon.client.modules.HorizonGuiModule.get();
        if (guiMod != null && guiMod.isEnabled()) guiMod.setEnabled(false);
        super.close();
    }

    @Override public boolean shouldPause(){return false;}
}
