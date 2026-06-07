package dev.horizon.client.mixin;

import dev.horizon.client.modules.NameProtectModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.Frustum;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class OwnNametagMixin {

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private <E extends Entity> void showOwnNametag(E entity, Frustum frustum,
            double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        NameProtectModule mod = NameProtectModule.get();
        if (mod == null || !mod.isEnabled()) return;
        if (entity == MinecraftClient.getInstance().player) cir.setReturnValue(true);
    }
}
