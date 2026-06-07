package dev.horizon.client.mixin;

import dev.horizon.client.modules.HandViewModel;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class HandViewMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void onGetFov(Camera camera, float tickDelta, boolean changingFov,
                          CallbackInfoReturnable<Float> cir) {
        HandViewModel mod = HandViewModel.get();
        if (mod == null || !mod.isEnabled()) return;

        if (!changingFov) {
            cir.setReturnValue(mod.fov.getValue().floatValue());
        }
    }
}
