package dev.horizon.client.mixin;

import dev.horizon.client.HorizonClient;
import dev.horizon.client.modules.FreecamModule;
import dev.horizon.client.modules.FreelookModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class FreecamEntityMixin {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void onChangeLookDirection(double deltaX, double deltaY, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if ((Object) this != mc.player) return;
        if (HorizonClient.getInstance() == null) return;

        FreecamModule fc = getFreecam();
        if (fc != null && fc.isEnabled()) {
            fc.updateRotation(deltaX * 0.15, deltaY * 0.15);
            ci.cancel();
            return;
        }

        FreelookModule fl = getFreelook();
        if (fl != null && fl.isEnabled()) {
            fl.updateRotation(deltaX * 0.15, deltaY * 0.15);
            ci.cancel();
        }
    }

    private static FreecamModule getFreecam() {
        if (HorizonClient.getInstance() == null) return null;
        var mod = HorizonClient.getInstance().getModuleManager().getModule("FreeCam");
        return mod instanceof FreecamModule fc ? fc : null;
    }

    private static FreelookModule getFreelook() {
        if (HorizonClient.getInstance() == null) return null;
        var mod = HorizonClient.getInstance().getModuleManager().getModule("FreeLook");
        return mod instanceof FreelookModule fl ? fl : null;
    }
}
