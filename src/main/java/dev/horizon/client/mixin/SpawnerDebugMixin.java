package dev.horizon.client.mixin;

import dev.horizon.client.modules.SpawnerDebugModule;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class SpawnerDebugMixin {

    @Inject(method = "onChunkData", at = @At("TAIL"))
    private void horizon$spawnerOnChunkData(ChunkDataS2CPacket packet, CallbackInfo ci) {
        SpawnerDebugModule mod = SpawnerDebugModule.get();
        if (mod == null || !mod.isEnabled()) return;
        mod.onChunkData(packet.getChunkX(), packet.getChunkZ());
    }

    @Inject(method = "onBlockEntityUpdate", at = @At("TAIL"))
    private void horizon$spawnerOnBlockEntityUpdate(BlockEntityUpdateS2CPacket packet, CallbackInfo ci) {
        SpawnerDebugModule mod = SpawnerDebugModule.get();
        if (mod == null || !mod.isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        BlockPos pos = packet.getPos();
        BlockEntity be = mc.world.getBlockEntity(pos);
        mod.onBlockEntityUpdate(pos, be);
    }

    @Inject(method = "onUnloadChunk", at = @At("HEAD"))
    private void horizon$spawnerOnUnloadChunk(UnloadChunkS2CPacket packet, CallbackInfo ci) {
        SpawnerDebugModule mod = SpawnerDebugModule.get();
        if (mod == null || !mod.isEnabled()) return;
        mod.onChunkUnload(packet.pos());
    }
}
