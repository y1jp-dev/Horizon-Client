package dev.horizon.client.mixin;

import dev.horizon.client.HorizonClient;
import dev.horizon.client.modules.FreecamModule;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class FreecamKeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;

        FreecamModule fc = getFreecam();
        if (fc == null || !fc.isEnabled()) return;

        boolean pressed = action != 0;


        if (mc.options.forwardKey.matchesKey(key, scancode)) {
            fc.movingForward = pressed;
            ci.cancel();
        } else if (mc.options.backKey.matchesKey(key, scancode)) {
            fc.movingBackward = pressed;
            ci.cancel();
        } else if (mc.options.rightKey.matchesKey(key, scancode)) {
            fc.movingRight = pressed;
            ci.cancel();
        } else if (mc.options.leftKey.matchesKey(key, scancode)) {
            fc.movingLeft = pressed;
            ci.cancel();
        }

        else if (mc.options.jumpKey.matchesKey(key, scancode)) {
            fc.movingUp = pressed;
            ci.cancel();
        } else if (mc.options.sneakKey.matchesKey(key, scancode)) {
            fc.movingDown = pressed;
            ci.cancel();
        }
    }

    private static FreecamModule getFreecam() {
        if (HorizonClient.getInstance() == null) return null;
        var mod = HorizonClient.getInstance().getModuleManager().getModule("FreeCam");
        return mod instanceof FreecamModule fc ? fc : null;
    }
}
