package dev.horizon.client.modules;

import dev.horizon.client.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class FullBrightModule extends Module {

    private static final int REFRESH_BELOW = 200;
    private static final int SET_DURATION  = Integer.MAX_VALUE;

    public FullBrightModule() {
        super("FullBright", "Night vision effect");
    }

    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        StatusEffectInstance current = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (current == null || current.getDuration() < REFRESH_BELOW) {
            mc.player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NIGHT_VISION,
                SET_DURATION,
                0,
                true,
                false,
                false
            ));
        }
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }
}
