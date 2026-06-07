package dev.horizon.client.mixin;

import dev.horizon.client.modules.PrimeChunkModule;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class PrimeChunkMixin {

    @Inject(method = "onBlockEvent", at = @At("TAIL"))
    private void horizon$onBlockEvent(BlockEventS2CPacket packet, CallbackInfo ci) {
        PrimeChunkModule mod = PrimeChunkModule.get();
        if (mod == null || !mod.isEnabled()) return;

        Block block = packet.getBlock();
        if (PrimeChunkModule.isPistonRelated(block)) {
            mod.onPistonActivity(packet.getPos());
        }
    }

    @Inject(method = "onBlockUpdate", at = @At("TAIL"))
    private void horizon$onBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        PrimeChunkModule mod = PrimeChunkModule.get();
        if (mod == null || !mod.isEnabled()) return;

        BlockState state = packet.getState();
        if (PrimeChunkModule.isPistonRelated(state.getBlock())) {
            mod.onPistonActivity(packet.getPos());
        }
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("TAIL"))
    private void horizon$onChunkDeltaUpdate(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        PrimeChunkModule mod = PrimeChunkModule.get();
        if (mod == null || !mod.isEnabled()) return;

        packet.visitUpdates((pos, state) -> {
            if (PrimeChunkModule.isPistonRelated(state.getBlock())) {

                mod.onPistonActivity(pos.toImmutable());
            }
        });
    }

    @Inject(method = "onUnloadChunk", at = @At("HEAD"))
    private void horizon$onUnloadChunk(UnloadChunkS2CPacket packet, CallbackInfo ci) {
        PrimeChunkModule mod = PrimeChunkModule.get();
        if (mod == null || !mod.isEnabled()) return;
        mod.onChunkUnload(packet.pos());
    }
}
