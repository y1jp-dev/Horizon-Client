package dev.horizon.client.modules;

import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;

public class HandViewModel extends Module {

    public final Setting<Double> fov = addSetting(
            new Setting<>("FOV", "Hand FOV", 70.0, 30.0, 160.0));

    public final Setting<Double> swingSpeed = addSetting(
            new Setting<>("Swing Speed", "Higher = slower swing", 0.0, 0.0, 20.0));

    private static HandViewModel INSTANCE;

    public HandViewModel() {
        super("Hand View", "Hand FOV");
        INSTANCE = this;
    }

    public static HandViewModel get() { return INSTANCE; }


    public float getSwingMultiplier() {

        double v = swingSpeed.getValue();
        return (float)(1.0 - (v / 20.0) * 0.95);
    }
}
