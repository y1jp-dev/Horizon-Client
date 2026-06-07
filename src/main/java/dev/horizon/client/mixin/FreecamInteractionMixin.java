package dev.horizon.client.mixin;

import dev.horizon.client.HorizonClient;
import dev.horizon.client.modules.FreecamModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class FreecamInteractionMixin {

    @Shadow private MinecraftClient client;

    @Inject(method = "updateCrosshairTarget", at = @At("RETURN"))
    private void freecamOverrideCrosshairTarget(float tickDelta, CallbackInfo ci) {
        FreecamModule fc = getFreecam();
        if (fc == null || !fc.isEnabled() || !fc.cameraMine.getValue()) return;

        MinecraftClient mc = this.client;
        if (mc.world == null || mc.player == null) return;

        Vec3d camPos = new Vec3d(
                fc.getInterpX(tickDelta),
                fc.getInterpY(tickDelta),
                fc.getInterpZ(tickDelta)
        );

        Vec3d camDir = Vec3d.fromPolar(fc.getInterpPitch(tickDelta), fc.getInterpYaw(tickDelta));

        double reach = mc.player.getBlockInteractionRange();
        Vec3d end = camPos.add(camDir.multiply(reach));

        RaycastContext ctx = new RaycastContext(
                camPos,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );

        mc.crosshairTarget = mc.world.raycast(ctx);
    }

    private static FreecamModule getFreecam() {
        if (HorizonClient.getInstance() == null) return null;
        var mod = HorizonClient.getInstance().getModuleManager().getModule("FreeCam");
        return mod instanceof FreecamModule fc ? fc : null;
    }
}
