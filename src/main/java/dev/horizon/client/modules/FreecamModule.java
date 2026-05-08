package dev.horizon.client.modules;

import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

public class FreecamModule extends Module {

    public final Setting<Double> speed = addSetting(
            new Setting<>("Speed", "Speed", 1.0, 0.1, 10.0));


    public final Vector3d currentPosition  = new Vector3d();
    public final Vector3d previousPosition = new Vector3d();


    public float yaw, pitch;
    public float previousYaw, previousPitch;


    public boolean movingForward, movingBackward;
    public boolean movingLeft,    movingRight;
    public boolean movingUp,      movingDown;

    private Perspective savedPerspective;
    private long lastFrameTime;

    public FreecamModule() {
        super("FreeCam", "Free camera");
    }

    @Override
    protected void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options == null) {
            setEnabled(false);
            return;
        }

        mc.options.getFovEffectScale().setValue(0.0);
        mc.options.getBobView().setValue(false);


        Vec3d eye = mc.player.getEyePos();
        currentPosition.set(eye.x, eye.y, eye.z);
        previousPosition.set(eye.x, eye.y, eye.z);

        yaw   = mc.player.getYaw();
        pitch = mc.player.getPitch();

        savedPerspective = mc.options.getPerspective();
        if (savedPerspective == Perspective.THIRD_PERSON_BACK) {
            yaw   += 180f;
            pitch *= -1f;
        }

        previousYaw   = yaw;
        previousPitch = pitch;

        mc.options.setPerspective(Perspective.FIRST_PERSON);
        lastFrameTime = System.currentTimeMillis();


        resetCameraMovement();
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            mc.options.setPerspective(
                savedPerspective != null ? savedPerspective : Perspective.FIRST_PERSON);
        }
        resetCameraMovement();
    }


    public void onTick() {


    }


    public void onRender(float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        previousPosition.set(currentPosition);
        previousYaw   = yaw;
        previousPitch = pitch;

        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastFrameTime) / 1000f, 0.1f);
        if (dt < 0.001f) dt = 0.016f;
        lastFrameTime = now;

        Vec3d forward = Vec3d.fromPolar(0, yaw);
        Vec3d right   = Vec3d.fromPolar(0, yaw + 90f);

        double spd = speed.getValue() * 2.0;
        if (mc.options.sprintKey.isPressed()) spd *= 2.0;

        double mx = 0, my = 0, mz = 0;
        if (movingForward)  { mx += forward.x * spd; mz += forward.z * spd; }
        if (movingBackward) { mx -= forward.x * spd; mz -= forward.z * spd; }
        if (movingRight)    { mx += right.x   * spd; mz += right.z   * spd; }
        if (movingLeft)     { mx -= right.x   * spd; mz -= right.z   * spd; }
        if (movingUp)       { my += spd; }
        if (movingDown)     { my -= spd; }

        currentPosition.x += mx * dt * 5.0;
        currentPosition.y += my * dt * 5.0;
        currentPosition.z += mz * dt * 5.0;
    }

    public void updateRotation(double deltaYaw, double deltaPitch) {
        yaw   = MathHelper.wrapDegrees(yaw   + (float) deltaYaw);
        pitch = MathHelper.clamp(pitch + (float) deltaPitch, -90f, 90f);
    }

    public double getInterpX(float pt) { return MathHelper.lerp(pt, previousPosition.x, currentPosition.x); }
    public double getInterpY(float pt) { return MathHelper.lerp(pt, previousPosition.y, currentPosition.y); }
    public double getInterpZ(float pt) { return MathHelper.lerp(pt, previousPosition.z, currentPosition.z); }
    public float  getInterpYaw(float pt)   { return (float) MathHelper.lerpAngleDegrees(pt, previousYaw, yaw); }
    public float  getInterpPitch(float pt) { return (float) MathHelper.lerp(pt, previousPitch, pitch); }

    private void resetCameraMovement() {
        movingForward = movingBackward = false;
        movingLeft    = movingRight    = false;
        movingUp      = movingDown     = false;
    }
}
