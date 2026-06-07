package dev.horizon.client.mixin;

import dev.horizon.client.modules.ActivityDebugModule;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundSystem.class)
public class ActivityDebugSoundMixin {

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"))
    private void horizon$actSoundPlay(SoundInstance sound, CallbackInfo ci) {
        ActivityDebugModule mod = ActivityDebugModule.get();
        if (mod == null || !mod.isEnabled()) return;
        mod.onSoundPlayed(sound.getId().toString(), sound.getX(), sound.getY(), sound.getZ());
    }
}
