package dev.horizon.client.gui;

import dev.horizon.client.HorizonClient;
import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;


public class HorizonScreen extends Screen {


    private static final int GUI_WIDTH  = 440;
    private static final int GUI_HEIGHT = 280;
    private static final int RADIUS     = 12;
    private static final int LEFT_W     = 140;
    private static final int PANEL_PAD  = 10;
    private static final int MODULE_H   = 24;
    private static final int MODULE_PAD = 4;


    private static final int C_BG           = 0xF0101010;
    private static final int C_MODULE_HOVER = 0xFF1E1E1E;
    private static final int C_MODULE_SEL   = 0xFF262626;
    private static final int C_TEXT_PRIMARY = 0xFFEEEEEE;
    private static final int C_TEXT_MUTED   = 0xFF888888;
    private static final int C_TEXT_BIND    = 0xFFAAAAAA;
    private static final int C_DIVIDER      = 0xFF222222;
    private static final int C_TOGGLE_ON    = 0xFFFFFFFF;
    private static final int C_TOGGLE_OFF   = 0xFF333333;
    private static final int C_TOGGLE_KNOB  = 0xFF101010;
    private static final int C_SLIDER_TRACK = 0xFF2E2E2E;
    private static final int C_SLIDER_FILL  = 0xFFCCCCCC;
    private static final int C_SLIDER_THUMB = 0xFFFFFFFF;
    private static final int C_BIND_WAITING = 0xFF3A3A1A;
    private static final int C_BIND_BORDER  = 0xFFCCCC44;
    private static final int C_DOT_ON       = 0xFFFFFFFF;
    private static final int C_DOT_OFF      = 0xFF404040;


    private int guiX, guiY;
    private Module selectedModule     = null;
    private Module bindingModule      = null;


    private int scrollOffset = 0;
    private int lastSettingsHeight = 0;


    private Setting<?> draggingSetting = null;
    private int sliderX, sliderW;


    private record SliderHitbox(Setting<?> setting, int x, int y, int w) {}
    private java.util.List<SliderHitbox> sliderHitboxes = new java.util.ArrayList<>();


    private int keybindRowY = -1;
    private int resetRowY     = -1;

    public HorizonScreen() {
        super(Text.literal("Horizon Client"));
    }

    @Override
    protected void init() {
        guiX = (width  - GUI_WIDTH)  / 2;
        guiY = (height - GUI_HEIGHT) / 2;
        List<Module> modules = HorizonClient.getInstance().getModuleManager().getModules();
        if (!modules.isEmpty() && selectedModule == null) selectedModule = modules.get(0);
    }




    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x80000000);
        sliderHitboxes.clear();
        keybindRowY = -1;
        resetRowY     = -1;

        drawRoundRect(ctx, guiX, guiY, GUI_WIDTH, GUI_HEIGHT, RADIUS, C_BG);

        drawTitleBar(ctx);
        drawLeftPanel(ctx, mouseX, mouseY);
        drawRightPanel(ctx, mouseX, mouseY);


        if (bindingModule != null) {
            drawBindOverlay(ctx);
        }


        for (var child : this.children()) {
            if (child instanceof net.minecraft.client.gui.Drawable d) {
                d.render(ctx, mouseX, mouseY, delta);
            }
        }
    }

    private void drawTitleBar(DrawContext ctx) {
        int ty = guiY + 7;
        ctx.drawTextWithShadow(textRenderer, Text.literal("HORIZON"), guiX + PANEL_PAD, ty, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("1.21.4"), guiX + PANEL_PAD + 54, ty + 1, C_TEXT_MUTED);
        fillRect(ctx, guiX + PANEL_PAD, guiY + 20, GUI_WIDTH - PANEL_PAD * 2, 1, C_DIVIDER);
    }

    private void drawLeftPanel(DrawContext ctx, int mouseX, int mouseY) {
        List<Module> modules = HorizonClient.getInstance().getModuleManager().getModules();
        int sx = guiX + PANEL_PAD;
        int sy = guiY + 26;

        for (int i = 0; i < modules.size(); i++) {
            Module mod = modules.get(i);
            int mx = sx;
            int my = sy + i * (MODULE_H + MODULE_PAD);
            int mw = LEFT_W - PANEL_PAD;

            boolean hov = isHov(mouseX, mouseY, mx, my, mw, MODULE_H);
            boolean sel = mod == selectedModule;

            if (sel || hov)
                drawRoundRect(ctx, mx, my, mw, MODULE_H, 5, sel ? C_MODULE_SEL : C_MODULE_HOVER);


            fillRect(ctx, mx + 4, my + (MODULE_H / 2) - 2, 4, 4, mod.isEnabled() ? C_DOT_ON : C_DOT_OFF);


            int tc = sel ? C_TEXT_PRIMARY : (hov ? 0xFFDDDDDD : C_TEXT_MUTED);
            ctx.drawTextWithShadow(textRenderer, Text.literal(mod.getName()), mx + 14, my + (MODULE_H - 8) / 2, tc);
        }

        fillRect(ctx, guiX + LEFT_W, guiY + 22, 1, GUI_HEIGHT - 28, C_DIVIDER);
    }

    private void drawRightPanel(DrawContext ctx, int mouseX, int mouseY) {
        if (selectedModule == null) return;

        int rx = guiX + LEFT_W + PANEL_PAD + 4;
        int ry = guiY + 26;
        int rw = GUI_WIDTH - LEFT_W - PANEL_PAD * 2 - 8;


        ctx.drawTextWithShadow(textRenderer, Text.literal(selectedModule.getName()), rx, ry, C_TEXT_PRIMARY);
        ctx.drawTextWithShadow(textRenderer, Text.literal(selectedModule.getDescription()), rx, ry + 12, C_TEXT_MUTED);
        fillRect(ctx, rx, ry + 23, rw, 1, C_DIVIDER);

        int togX = guiX + GUI_WIDTH - PANEL_PAD - 32;
        drawToggle(ctx, togX, ry + 2, selectedModule.isEnabled());


        int contentTop = ry + 32;
        int contentBottom = guiY + GUI_HEIGHT - 6;
        int contentH = contentBottom - contentTop;


        int maxScroll = Math.max(0, lastSettingsHeight - contentH);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));


        int scaledX = guiX + LEFT_W + 1;
        int scaledY = contentTop;
        int scaledW = GUI_WIDTH - LEFT_W - 2;
        int scaledH = contentH;
        ctx.enableScissor(scaledX, scaledY, scaledX + scaledW, scaledY + scaledH);

        int sy = contentTop - scrollOffset;

        List<Setting<?>> settings = selectedModule.getSettings();
        if (!settings.isEmpty()) {
            for (Setting<?> s : settings) {
                sy = drawSetting(ctx, mouseX, mouseY, s, rx, sy, rw);
                sy += 6;
            }
            fillRect(ctx, rx, sy, rw, 1, C_DIVIDER);
            sy += 8;
        }


        keybindRowY = sy;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Keybind"), rx, sy, C_TEXT_PRIMARY);

        String bindLabel = "[" + selectedModule.getKeybindName() + "]";
        int bindW = textRenderer.getWidth(bindLabel);
        boolean bindHov = isHov(mouseX, mouseY, rx + rw - bindW - 20, sy - 2, bindW + 20, 14);
        int bindBg = bindHov ? 0xFF2A2A2A : 0xFF1A1A1A;
        fillRect(ctx, rx + rw - bindW - 16, sy - 2, bindW + 14, 13, bindBg);
        ctx.drawTextWithShadow(textRenderer, Text.literal(bindLabel),
                rx + rw - bindW - 9, sy, C_TEXT_BIND);
        sy += 14;


        resetRowY = sy;
        String resetLabel = "[ Reset ]";
        int resetW = textRenderer.getWidth(resetLabel);
        int resetX = rx + rw / 2 - resetW / 2;
        boolean resetHov = isHov(mouseX, mouseY, resetX - 4, sy - 2, resetW + 8, 13);
        fillRect(ctx, resetX - 4, sy - 2, resetW + 8, 13, resetHov ? 0xFF2A2A2A : 0xFF1A1A1A);
        ctx.drawTextWithShadow(textRenderer, Text.literal(resetLabel), resetX, sy, resetHov ? 0xFFFFFFFF : C_TEXT_MUTED);
        sy += 14;

        ctx.disableScissor();


        lastSettingsHeight = sy - contentTop + scrollOffset;


        if (lastSettingsHeight > contentH) {
            int barX = guiX + GUI_WIDTH - 5;
            int barH = Math.max(20, contentH * contentH / lastSettingsHeight);
            int barY = contentTop + (scrollOffset * (contentH - barH) / Math.max(1, lastSettingsHeight - contentH));
            fillRect(ctx, barX, contentTop, 3, contentH, 0xFF222222);
            fillRect(ctx, barX, barY, 3, barH, 0xFF555555);
        }
    }

    private void drawBindOverlay(DrawContext ctx) {

        ctx.fill(guiX, guiY, guiX + GUI_WIDTH, guiY + GUI_HEIGHT, 0xC0000000);

        String msg1 = "Binding: " + bindingModule.getName();
        String msg2 = "Press any key  •  ESC to cancel  •  DEL to clear";

        int cx = guiX + GUI_WIDTH / 2;
        int cy = guiY + GUI_HEIGHT / 2;

        int w1 = textRenderer.getWidth(msg1);
        int w2 = textRenderer.getWidth(msg2);
        int boxW = Math.max(w1, w2) + 24;
        int boxH = 38;
        int bx = cx - boxW / 2;
        int by = cy - boxH / 2;

        drawRoundRect(ctx, bx, by, boxW, boxH, 6, 0xFF1A1A0A);
        fillRect(ctx, bx, by, boxW, 1, C_BIND_BORDER);
        fillRect(ctx, bx, by + boxH - 1, boxW, 1, C_BIND_BORDER);

        ctx.drawTextWithShadow(textRenderer, Text.literal(msg1), cx - w1 / 2, by + 8, 0xFFEEEE44);
        ctx.drawTextWithShadow(textRenderer, Text.literal(msg2), cx - w2 / 2, by + 22, C_TEXT_MUTED);
    }

    private int drawSetting(DrawContext ctx, int mouseX, int mouseY,
                             Setting<?> s, int x, int y, int w) {
        ctx.drawTextWithShadow(textRenderer, Text.literal(s.getName()), x, y, C_TEXT_PRIMARY);
        return switch (s.getType()) {
            case BOOLEAN -> {
                drawToggle(ctx, x + w - 32, y - 1, (Boolean) s.getValue());
                yield y + 14;
            }
            case SLIDER -> {
                double val = (Double) s.getValue();
                double min = (Double) s.getMin();
                double max = (Double) s.getMax();
                double pct = (val - min) / (max - min);
                int tw = w - 4, ty = y + 13, th = 4;

                fillRect(ctx, x, ty, tw, th, C_SLIDER_TRACK);
                fillRect(ctx, x, ty, (int)(tw * pct), th, C_SLIDER_FILL);
                int thumbX = x + (int)(tw * pct) - 3;
                fillRect(ctx, thumbX, ty - 2, 6, th + 4, C_SLIDER_THUMB);


                String valStr = String.valueOf((int) Math.round(val));
                int vw = textRenderer.getWidth(valStr);
                ctx.drawTextWithShadow(textRenderer, Text.literal(valStr), x + w - vw, y, C_TEXT_MUTED);

                sliderHitboxes.add(new SliderHitbox(s, x, ty - 4, tw));
                yield y + 24;
            }
            default -> y + 14;
        };
    }

    private void drawToggle(DrawContext ctx, int x, int y, boolean on) {
        int tw = 30, th = 12;
        drawRoundRect(ctx, x, y, tw, th, th / 2, on ? C_TOGGLE_ON : C_TOGGLE_OFF);
        int knobX = on ? x + tw - th + 1 : x + 1;
        drawRoundRect(ctx, knobX, y + 1, th - 2, th - 2, (th - 2) / 2, C_TOGGLE_KNOB);
    }




    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bindingModule != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {

                bindingModule = null;
            } else if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {

                bindingModule.setKeybind(GLFW.GLFW_KEY_UNKNOWN);
                bindingModule = null;
            } else {

                bindingModule.setKeybind(keyCode);
                bindingModule = null;
            }
            return true;
        }


        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }




    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {

        if (mouseX > guiX + LEFT_W && mouseX < guiX + GUI_WIDTH
                && mouseY > guiY && mouseY < guiY + GUI_HEIGHT) {
            scrollOffset -= (int)(verticalAmount * 12);
            scrollOffset = Math.max(0, scrollOffset);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (bindingModule != null) {

            bindingModule = null;
            return true;
        }

        int mx = (int) mouseX, my = (int) mouseY;
        List<Module> modules = HorizonClient.getInstance().getModuleManager().getModules();


        int sx = guiX + PANEL_PAD;
        int sy = guiY + 26;
        int mw = LEFT_W - PANEL_PAD;
        for (int i = 0; i < modules.size(); i++) {
            Module mod = modules.get(i);
            int rowY = sy + i * (MODULE_H + MODULE_PAD);
            if (isHov(mx, my, sx, rowY, mw, MODULE_H)) {
                if (button == 0) { selectedModule = mod; scrollOffset = 0; }
                else if (button == 1) mod.toggle();
                return true;
            }
        }


        if (selectedModule != null) {
            int rx = guiX + LEFT_W + PANEL_PAD + 4;
            int ry = guiY + 26;
            int rw = GUI_WIDTH - LEFT_W - PANEL_PAD * 2 - 8;


            int togX = guiX + GUI_WIDTH - PANEL_PAD - 32;
            if (isHov(mx, my, togX, ry + 2, 30, 12)) {
                selectedModule.toggle();
                return true;
            }





            int settingY = ry + 32 - scrollOffset;
            for (Setting<?> s : selectedModule.getSettings()) {
                if (s.getType() == Setting.SettingType.BOOLEAN) {
                    int togSX = rx + rw - 32;
                    if (isHov(mx, my, togSX, settingY - 1, 30, 12)) {
                        ((Setting<Boolean>) s).setValue(!(Boolean) s.getValue());
                        return true;
                    }
                    settingY += 20;
                } else if (s.getType() == Setting.SettingType.SLIDER) {
                    int tw = rw - 4;
                    int ty = settingY + 13;
                    if (isHov(mx, my, rx, ty - 4, tw, 12)) {
                        double pct = Math.max(0, Math.min(1, (double)(mx - rx) / tw));
                        double min = (Double) s.getMin(), max = (Double) s.getMax();
                        ((Setting<Double>) s).setValue((double) Math.round(min + pct * (max - min)));
                        draggingSetting = s;
                        sliderX = rx; sliderW = tw;
                        return true;
                    }
                    settingY += 30;
                }

            }


            if (keybindRowY >= 0) {
                String bindLabel = "[" + selectedModule.getKeybindName() + "]";
                int bindW = textRenderer.getWidth(bindLabel);
                int btnX = rx + rw - bindW - 16;
                if (isHov(mx, my, btnX, keybindRowY - 2, bindW + 14, 13)) {
                    bindingModule = selectedModule;
                    return true;
                }
            }

            if (resetRowY >= 0) {
                String resetLabel = "[ Reset ]";
                int resetW = textRenderer.getWidth(resetLabel);
                int resetX = rx + rw / 2 - resetW / 2;
                if (isHov(mx, my, resetX - 4, resetRowY - 2, resetW + 8, 13)) {
                    selectedModule.resetToDefaults();
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dX, double dY) {
        if (draggingSetting != null && draggingSetting.getType() == Setting.SettingType.SLIDER) {
            double pct = Math.max(0, Math.min(1, (mouseX - sliderX) / sliderW));
            double min = (Double) draggingSetting.getMin(), max = (Double) draggingSetting.getMax();
            ((Setting<Double>) draggingSetting).setValue((double) Math.round(min + pct * (max - min)));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dX, dY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSetting = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }




    private static void fillRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + h, color);
    }

    private static void drawRoundRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w / 2, h / 2));
        ctx.fill(x + r, y, x + w - r, y + h, color);
        ctx.fill(x, y + r, x + r, y + h - r, color);
        ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
        fillCorner(ctx, x,         y,         r, color, true,  true);
        fillCorner(ctx, x + w - r, y,         r, color, false, true);
        fillCorner(ctx, x,         y + h - r, r, color, true,  false);
        fillCorner(ctx, x + w - r, y + h - r, r, color, false, false);
    }

    private static void fillCorner(DrawContext ctx, int ox, int oy, int r, int color,
                                    boolean left, boolean top) {
        float cx = left ? ox + r : ox;
        float cy = top  ? oy + r : oy;
        for (int row = 0; row < r; row++) {
            float py = top ? cy - row - 1 : cy + row;


            float dist = Math.abs(cy - py);
            float hc   = (float) Math.sqrt(Math.max(0.0, (double) r * r - (double) dist * dist));
            int x0 = left  ? Math.round(cx - hc) : (int) cx;
            int x1 = left  ? (int) cx             : Math.round(cx + hc);
            ctx.fill(x0, (int) py, x1, (int) py + 1, color);
        }
    }

    private static boolean isHov(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void close() {

        dev.horizon.client.HorizonClient inst = dev.horizon.client.HorizonClient.getInstance();
        if (inst != null) inst.getConfigManager().save();
        super.close();
    }

    @Override public boolean shouldPause() { return false; }
}
