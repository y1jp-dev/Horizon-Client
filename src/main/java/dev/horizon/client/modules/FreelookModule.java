package dev.horizon.client.modules;

import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;

public class FreelookModule extends Module {

    public final Setting<Boolean> holdMode = addSetting(
            new Setting<>("Hold Mode", "Disable when key is released", true));
    public final Setting<Boolean> seeThroughWalls = addSetting(
            new Setting<>("See Through Walls", "Allow the camera to clip through blocks", false));

    public float yaw;
    public float pitch;
    public float previousYaw;
    public float previousPitch;

    private Perspective previousPerspective;

    public FreelookModule() {
        super("FreeLook", "Look around freely without changing movement direction");
    }

    @Override
    protected void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) {
            setEnabled(false);
            return;
        }
        yaw           = mc.player.getYaw();
        pitch         = mc.player.getPitch();
        previousYaw   = yaw;
        previousPitch = pitch;
        previousPerspective = mc.options.getPerspective();
        mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.options != null) {
            mc.options.setPerspective(
                previousPerspective != null ? previousPerspective : Perspective.FIRST_PERSON);
        }
    }

    public boolean isHoldMode() {
        return holdMode.getValue();
    }

    public boolean allowCameraNoClip() {
        return seeThroughWalls.getValue();
    }

    public void updateRotation(double deltaYaw, double deltaPitch) {
        previousYaw   = yaw;
        previousPitch = pitch;
        yaw   += (float) deltaYaw;
        pitch  = MathHelper.clamp(pitch + (float) deltaPitch, -90f, 90f);
    }

    public float getInterpolatedYaw(float partialTicks) {
        return MathHelper.lerpAngleDegrees(partialTicks, previousYaw, yaw);
    }

    public float getInterpolatedPitch(float partialTicks) {
        return (float) MathHelper.lerp(partialTicks, previousPitch, pitch);
    }
}
