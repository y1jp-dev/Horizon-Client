package dev.horizon.client.mixin;

import dev.horizon.client.modules.SkinProtectModule;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.util.Identifier;

@Mixin(PlayerListEntry.class)
public class SkinProtectMixin {

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void horizon_overrideSkin(CallbackInfoReturnable<SkinTextures> cir) {
        Identifier override = SkinProtectModule.getOverrideSkin();
        if (override == null) return;

        SkinTextures original = cir.getReturnValue();
        if (original == null) return;

        cir.setReturnValue(new SkinTextures(
                override,
                original.textureUrl(),
                original.capeTexture(),
                original.elytraTexture(),
                original.model(),
                original.secure()
        ));
    }
}
