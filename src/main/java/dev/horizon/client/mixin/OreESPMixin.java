package dev.horizon.client.mixin;

import dev.horizon.client.modules.OreESPModule;
import dev.horizon.client.module.Module;
import dev.horizon.client.HorizonClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class OreESPMixin {

    @Inject(method = "onUnloadChunk", at = @At("HEAD"))
    private void horizon$oreOnUnloadChunk(UnloadChunkS2CPacket packet, CallbackInfo ci) {
        if (HorizonClient.getInstance() == null) return;
        for (Module m : HorizonClient.getInstance().getModuleManager().getModules()) {
            if (m instanceof OreESPModule ore && ore.isEnabled()) {
                ore.onChunkUnload(packet.pos());
            }
        }
    }

    @Inject(method = "onBlockUpdate", at = @At("TAIL"))
    private void horizon$oreOnBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        if (HorizonClient.getInstance() == null) return;
        for (Module m : HorizonClient.getInstance().getModuleManager().getModules()) {
            if (m instanceof OreESPModule ore && ore.isEnabled()) {
                ore.onBlockUpdate(packet.getPos());
            }
        }
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("TAIL"))
    private void horizon$oreOnChunkDeltaUpdate(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        if (HorizonClient.getInstance() == null) return;
        for (Module m : HorizonClient.getInstance().getModuleManager().getModules()) {
            if (m instanceof OreESPModule ore && ore.isEnabled()) {
                packet.visitUpdates((pos, state) -> ore.onBlockUpdate(pos));
            }
        }
    }
}
