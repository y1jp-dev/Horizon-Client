package dev.horizon.client.mixin;

import dev.horizon.client.modules.HudModule;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class StatusEffectsMixin {

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void suppressVanillaEffects(DrawContext ctx, RenderTickCounter tc, CallbackInfo ci) {
        HudModule hud = HudModule.get();
        if (hud != null && hud.isEnabled()) ci.cancel();
    }
}
