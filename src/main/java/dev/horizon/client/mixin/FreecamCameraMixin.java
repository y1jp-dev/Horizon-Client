package dev.horizon.client.mixin;

import dev.horizon.client.HorizonClient;
import dev.horizon.client.modules.FreecamModule;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;


@Mixin(Camera.class)
public abstract class FreecamCameraMixin {

    private float storedTickDelta;

    @Inject(method = "update", at = @At("HEAD"))
    private void captureTickDelta(BlockView area, Entity focusedEntity,
                                  boolean thirdPerson, boolean inverseView,
                                  float tickDelta, CallbackInfo ci) {
        this.storedTickDelta = tickDelta;
    }


    @ModifyArgs(
        method = "update",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V")
    )
    private void overridePosition(Args args) {
        FreecamModule fc = getFreecam();
        if (fc == null || !fc.isEnabled()) return;
        args.set(0, fc.getInterpX(storedTickDelta));
        args.set(1, fc.getInterpY(storedTickDelta));
        args.set(2, fc.getInterpZ(storedTickDelta));
    }


    @ModifyArgs(
        method = "update",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V")
    )
    private void overrideRotation(Args args) {
        FreecamModule fc = getFreecam();
        if (fc == null || !fc.isEnabled()) return;
        args.set(0, fc.getInterpYaw(storedTickDelta));
        args.set(1, fc.getInterpPitch(storedTickDelta));
    }

    @Inject(method = "isThirdPerson", at = @At("RETURN"), cancellable = true)
    private void renderFocusedEntityInFreecam(CallbackInfoReturnable<Boolean> cir) {
        FreecamModule fc = getFreecam();
        if (fc == null || !fc.isEnabled()) return;
        cir.setReturnValue(true);
    }

    private static FreecamModule getFreecam() {
        if (HorizonClient.getInstance() == null) return null;
        var mod = HorizonClient.getInstance().getModuleManager().getModule("FreeCam");
        return mod instanceof FreecamModule fc ? fc : null;
    }
}
