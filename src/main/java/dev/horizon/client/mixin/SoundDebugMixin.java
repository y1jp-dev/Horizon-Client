package dev.horizon.client.mixin;

import dev.horizon.client.modules.HorizonDebugModule;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundSystem.class)
public class SoundDebugMixin {

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"))
    private void onPlay(SoundInstance sound, CallbackInfo ci) {
        HorizonDebugModule mod = HorizonDebugModule.get();
        if (mod == null || !mod.isEnabled()) return;
        String id = sound.getId().toString();
        Vec3d pos = new Vec3d(sound.getX(), sound.getY(), sound.getZ());
        mod.onSoundPlayedById(id, pos);
    }
}
