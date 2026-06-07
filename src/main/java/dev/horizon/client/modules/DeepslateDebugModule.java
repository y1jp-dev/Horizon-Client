package dev.horizon.client.modules;

import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class DeepslateDebugModule extends Module {

    public final Setting<Double> yThreshold = addSetting(
        new Setting<>("Y Threshold", "Spam FLY UP in chat below this Y level", -30.0, -58.0, 0.0)
    );

    private boolean wasBelow = false;

    private volatile boolean isCycling = false;

    private volatile long lastCycleEnd = -1L;

    private static final long COOLDOWN_MS = 3000L;

    public DeepslateDebugModule() {
        super("Deepslate Debug", "Relog method but without relogging");
    }

    @Override
    protected void onEnable() {
        wasBelow = false;
        isCycling = false;
        lastCycleEnd = -1L;
    }

    @Override
    protected void onDisable() {
        wasBelow = false;
        isCycling = false;
        lastCycleEnd = -1L;
    }

    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        double playerY   = mc.player.getY();
        double threshold = yThreshold.getValue();
        boolean isBelow  = playerY <= threshold;

        if (isBelow) {

            mc.player.sendMessage(Text.literal("[Horizon] FLY UP"), false);

            if (!isCycling) {
                long now = System.currentTimeMillis();
                if (lastCycleEnd < 0 || (now - lastCycleEnd) >= COOLDOWN_MS) {
                    triggerRenderDistanceCycle(mc);
                }
            }
        }

        wasBelow = isBelow;
    }

    private void triggerRenderDistanceCycle(MinecraftClient mc) {
        isCycling = true;
        mc.options.getViewDistance().setValue(2);

        new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            mc.execute(() -> {
                mc.options.getViewDistance().setValue(32);
                lastCycleEnd = System.currentTimeMillis();
                isCycling = false;
            });
        }, "horizon-deepslate-rd").start();
    }
}
