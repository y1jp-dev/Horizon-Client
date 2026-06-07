package dev.horizon.client.mixin;

import dev.horizon.client.modules.SusChunkModule;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class SusChunkMixin {

    @Inject(method = "onUnloadChunk", at = @At("HEAD"))
    private void horizon$susOnUnloadChunk(UnloadChunkS2CPacket packet, CallbackInfo ci) {
        SusChunkModule mod = SusChunkModule.get();
        if (mod == null || !mod.isEnabled()) return;
        mod.onChunkUnload(packet.pos());
    }
}
