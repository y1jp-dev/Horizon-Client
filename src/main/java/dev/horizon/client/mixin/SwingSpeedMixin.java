package dev.horizon.client.mixin;

import dev.horizon.client.modules.HandViewModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class SwingSpeedMixin {

    @Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true)
    private void modifyHandSwingDuration(CallbackInfoReturnable<Integer> cir) {
        HandViewModel mod = HandViewModel.get();
        if (mod == null || !mod.isEnabled()) return;

        double speedValue = mod.swingSpeed.getValue();
        if (speedValue <= 0) return;

        float multiplier = 1.0f + (float) (speedValue / 20.0 * 9.0);
        int newDuration = Math.round(cir.getReturnValue() * multiplier);
        cir.setReturnValue(newDuration);
    }
}
