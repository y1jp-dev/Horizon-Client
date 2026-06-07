package dev.horizon.client.mixin;

import dev.horizon.client.HorizonClient;
import dev.horizon.client.modules.FreecamModule;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class FreecamInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void lockPlayerBodyMovementInFreecam(CallbackInfo ci) {
        FreecamModule fc = getFreecam();
        if (fc == null || !fc.isEnabled()) return;

        Input self = (Input)(Object) this;

        float forward = 0f;
        float sideways = 0f;
        if (fc.playerWasForward)  forward   += 1f;
        if (fc.playerWasBackward) forward   -= 1f;
        if (fc.playerWasRight)    sideways  += 1f;
        if (fc.playerWasLeft)     sideways  -= 1f;

        self.movementForward  = forward;
        self.movementSideways = sideways;

        self.playerInput = new PlayerInput(
            fc.playerWasForward,
            fc.playerWasBackward,
            fc.playerWasLeft,
            fc.playerWasRight,
            fc.playerWasJumping,
            fc.playerWasSneaking,
            fc.playerWasSprinting
        );
    }

    private static FreecamModule getFreecam() {
        if (HorizonClient.getInstance() == null) return null;
        var mod = HorizonClient.getInstance().getModuleManager().getModule("FreeCam");
        return mod instanceof FreecamModule fc ? fc : null;
    }
}
