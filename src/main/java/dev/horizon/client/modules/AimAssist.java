package dev.horizon.client.modules;

import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SwordItem;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public class AimAssist extends Module {

    public final Setting<Boolean> stickyAim    = addSetting(new Setting<>("Sticky Aim",    "Stay on last attacked player",   false));
    public final Setting<Double>  radius        = addSetting(new Setting<>("Radius",        "Target scan radius",              5.0,  0.5,  6.0));
    public final Setting<Boolean> seeOnly       = addSetting(new Setting<>("See Only",      "Only target visible players",    true));
    public final Setting<Double>  fov           = addSetting(new Setting<>("FOV",           "Max angle to aim within",       180.0,  5.0, 360.0));

    public final Setting<Double>  minYawSpeed   = addSetting(new Setting<>("Min H Speed",   "Min horizontal deg/tick",        15.0,  0.0, 50.0));
    public final Setting<Double>  maxYawSpeed   = addSetting(new Setting<>("Max H Speed",   "Max horizontal deg/tick",        25.0,  0.0, 50.0));
    public final Setting<Double>  minPitchSpeed = addSetting(new Setting<>("Min V Speed",   "Min vertical deg/tick",          15.0,  0.0, 50.0));
    public final Setting<Double>  maxPitchSpeed = addSetting(new Setting<>("Max V Speed",   "Max vertical deg/tick",          25.0,  0.0, 50.0));

    public final Setting<Double>  boostStrength = addSetting(new Setting<>("Boost",         "Speed multiplier for far targets (1=off, 3=max)", 2.5, 1.0, 5.0));

    public final Setting<Double>  aimAt         = addSetting(new Setting<>("Aim At",        "0=Head 1=Chest 2=Legs",           0.0,  0.0,  2.0));
    public final Setting<Boolean> yawAssist     = addSetting(new Setting<>("Horizontal",    "Enable horizontal assist",       true));
    public final Setting<Boolean> pitchAssist   = addSetting(new Setting<>("Vertical",      "Enable vertical assist",         true));
    public final Setting<Boolean> onLeftClick   = addSetting(new Setting<>("On Left Click", "Only assist while holding LMB", false));

    private final Random random = new Random();

    private float curYawSpeedDps;
    private float curPitchSpeedDps;
    private float targetYawSpeedDps;
    private float targetPitchSpeedDps;

    private float speedChangeTimer = 0f;

    private long lastApplyNanos = 0;

    private PlayerEntity stickyTarget = null;

    public AimAssist() {
        super("AimAssist", "Automatically aims at nearby players");
    }

    @Override
    protected void onEnable() {
        pickNewTargetSpeeds();
        curYawSpeedDps   = targetYawSpeedDps;
        curPitchSpeedDps = targetPitchSpeedDps;
        speedChangeTimer = 0f;
        lastApplyNanos   = 0;
        stickyTarget     = null;
    }

    @Override
    protected void onDisable() {
        lastApplyNanos = 0;
        stickyTarget   = null;
    }

    public void onTick() { }

    private void pickNewTargetSpeeds() {
        float minY = minYawSpeed.getValue().floatValue()   * 20f;
        float maxY = maxYawSpeed.getValue().floatValue()   * 20f;
        float minP = minPitchSpeed.getValue().floatValue() * 20f;
        float maxP = maxPitchSpeed.getValue().floatValue() * 20f;
        targetYawSpeedDps   = minY + random.nextFloat() * Math.max(0f, maxY - minY);
        targetPitchSpeedDps = minP + random.nextFloat() * Math.max(0f, maxP - minP);

        speedChangeTimer = 0.2f + random.nextFloat() * 0.3f;
    }

    public void applyRotation() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        var item = mc.player.getMainHandStack().getItem();
        if (!(item instanceof SwordItem) && !(item instanceof AxeItem) && !(item instanceof MaceItem)) return;

        if (onLeftClick.getValue() &&
                GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS)
            return;

        long nowNanos = System.nanoTime();
        if (lastApplyNanos == 0) {
            lastApplyNanos = nowNanos;
            return;
        }
        float dt = (nowNanos - lastApplyNanos) / 1_000_000_000.0f;
        dt = Math.min(dt, 0.1f);
        lastApplyNanos = nowNanos;

        speedChangeTimer -= dt;
        if (speedChangeTimer <= 0f) pickNewTargetSpeeds();

        float speedLerp = Math.min(1f, dt * 5f);
        curYawSpeedDps   += (targetYawSpeedDps   - curYawSpeedDps)   * speedLerp;
        curPitchSpeedDps += (targetPitchSpeedDps - curPitchSpeedDps) * speedLerp;

        PlayerEntity target = findTarget(mc);
        if (target == null) return;

        Vec3d targetPos = target.getPos();
        int mode = (int) Math.round(aimAt.getValue());
        switch (mode) {
            case 0 -> targetPos = targetPos.add(0, target.getEyeHeight(target.getPose()), 0);
            case 1 -> targetPos = targetPos.add(0, target.getHeight() * 0.7, 0);
            case 2 -> targetPos = targetPos.add(0, target.getHeight() * 0.3, 0);
        }

        if (getRotationDistance(mc, targetPos) > fov.getValue() / 2.0) return;

        float[] rots       = getRotationsTo(mc, targetPos);
        float currentYaw   = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        float yawDiff      = MathHelper.wrapDegrees(rots[0] - currentYaw);
        float pitchDiff    = MathHelper.wrapDegrees(rots[1] - currentPitch);

        float angularDist = (float) Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
        float maxBoost    = boostStrength.getValue().floatValue();
        float boost       = Math.min(maxBoost, Math.max(1f, angularDist / 15f));

        float maxYawStep   = curYawSpeedDps   * dt * boost;
        float maxPitchStep = curPitchSpeedDps * dt * boost;

        float yawEase   = smoothStep(yawDiff,   maxYawStep);
        float pitchEase = smoothStep(pitchDiff, maxPitchStep);

        if (yawAssist.getValue())   mc.player.setYaw(currentYaw   + yawEase);
        if (pitchAssist.getValue()) mc.player.setPitch(currentPitch + pitchEase);
    }

    private float smoothStep(float diff, float maxStep) {
        float absDiff = Math.abs(diff);
        if (absDiff < 0.001f) return 0f;

        float easedStep = maxStep * Math.min(1f, absDiff / (maxStep * 1.5f));
        easedStep = Math.min(easedStep, absDiff);
        return Math.copySign(easedStep, diff);
    }

    private PlayerEntity findTarget(MinecraftClient mc) {
        float r = radius.getValue().floatValue();
        if (stickyAim.getValue() && stickyTarget != null) {
            if (stickyTarget.isAlive() && mc.player.distanceTo(stickyTarget) < r) return stickyTarget;
            stickyTarget = null;
        }
        PlayerEntity best = null;
        double minDist = r;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (!p.isAlive()) continue;
            if (seeOnly.getValue() && !mc.player.canSee(p)) continue;
            double d = mc.player.distanceTo(p);
            if (d < minDist) { minDist = d; best = p; }
        }
        return best;
    }

    public void onAttack(PlayerEntity attacked) {
        stickyTarget = attacked;
    }

    private float[] getRotationsTo(MinecraftClient mc, Vec3d target) {
        Vec3d eye  = mc.player.getEyePos();
        double dx  = target.x - eye.x;
        double dy  = target.y - eye.y;
        double dz  = target.z - eye.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float)(-(Math.atan2(dy, hDist) * 180.0 / Math.PI));
        return new float[]{ yaw, pitch };
    }

    private double getRotationDistance(MinecraftClient mc, Vec3d target) {
        float[] t  = getRotationsTo(mc, target);
        float dy   = Math.abs(MathHelper.wrapDegrees(mc.player.getYaw()   - t[0]));
        float dp   = Math.abs(MathHelper.wrapDegrees(mc.player.getPitch() - t[1]));
        return Math.sqrt(dy * dy + dp * dp);
    }
}
