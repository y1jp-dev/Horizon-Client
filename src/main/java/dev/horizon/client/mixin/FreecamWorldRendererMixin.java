package dev.horizon.client.mixin;

import dev.horizon.client.HorizonClient;
import dev.horizon.client.modules.FreecamModule;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(WorldRenderer.class)
public abstract class FreecamWorldRendererMixin {

    @ModifyVariable(method = "setupTerrain", at = @At("HEAD"), ordinal = 1, argsOnly = true, require = 0)
    private boolean forceChunksAroundCamera(boolean value) {
        if (!isFreecamActive()) return value;
        return true;
    }

    private static boolean isFreecamActive() {
        if (HorizonClient.getInstance() == null) return false;
        var mod = HorizonClient.getInstance().getModuleManager().getModule("FreeCam");
        return mod instanceof FreecamModule fc && fc.isEnabled();
    }
}
