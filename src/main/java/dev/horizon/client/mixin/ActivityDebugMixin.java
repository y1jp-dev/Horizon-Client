package dev.horizon.client.mixin;

import dev.horizon.client.modules.ActivityDebugModule;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ActivityDebugMixin {

    @Inject(method = "onBlockUpdate", at = @At("TAIL"))
    private void horizon$actBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        ActivityDebugModule mod = ActivityDebugModule.get();
        if (mod == null || !mod.isEnabled()) return;
        mod.onBlockUpdate(packet.getPos());
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("TAIL"))
    private void horizon$actChunkDeltaUpdate(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        ActivityDebugModule mod = ActivityDebugModule.get();
        if (mod == null || !mod.isEnabled()) return;
        packet.visitUpdates((pos, state) -> mod.onChunkDeltaUpdate(pos));
    }

    @Inject(method = "onBlockEntityUpdate", at = @At("TAIL"))
    private void horizon$actBlockEntityUpdate(BlockEntityUpdateS2CPacket packet, CallbackInfo ci) {
        ActivityDebugModule mod = ActivityDebugModule.get();
        if (mod == null || !mod.isEnabled()) return;
        mod.onBlockEntityUpdate(packet.getPos());
    }

    @Inject(method = "onWorldEvent", at = @At("TAIL"))
    private void horizon$actWorldEvent(WorldEventS2CPacket packet, CallbackInfo ci) {
        ActivityDebugModule mod = ActivityDebugModule.get();
        if (mod == null || !mod.isEnabled()) return;
        mod.onWorldEvent(packet.getEventId(), packet.getPos());
    }
}
