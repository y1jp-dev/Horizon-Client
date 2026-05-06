package dev.horizon.client.modules;

import dev.horizon.client.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/**
 * FullBright — applies a permanent Night Vision potion effect.
 *
 * The flicker fix: Minecraft plays the "effect about to expire" flicker
 * animation when ANY effect drops below 200 ticks (10 seconds). We keep
 * Night Vision at a fixed high duration so it never enters that window,
 * AND we set ambient=true which suppresses the flicker animation entirely
 * for beacon-style effects. Both guards together mean no flicker regardless
 * of what other effects the player has.
 */
public class FullBrightModule extends Module {

    // Refresh when duration drops below this threshold (5 seconds = 100 ticks).
    // Kept well above 200 so we never enter MC's "expiry flicker" window.
    private static final int REFRESH_BELOW = 100;
    private static final int SET_DURATION  = 32767; // max short — essentially permanent

    public FullBrightModule() {
        super("FullBright", "See in the dark without torches");
    }

    /** Called every client tick by ClientMixin while enabled. */
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        StatusEffectInstance current = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (current == null || current.getDuration() < REFRESH_BELOW) {
            mc.player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NIGHT_VISION,
                SET_DURATION, // very long — never enters the <200 tick flicker window
                0,            // amplifier 0 = level I, sufficient for full brightness
                true,         // ambient=true → suppresses the expiry flash animation
                false,        // showParticles=false
                false         // showIcon=false — hide from HUD
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
