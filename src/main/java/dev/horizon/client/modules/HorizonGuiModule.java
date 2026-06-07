package dev.horizon.client.modules;

import dev.horizon.client.gui.HorizonScreen;
import dev.horizon.client.module.Module;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class HorizonGuiModule extends Module {

    private static HorizonGuiModule INSTANCE;

    private boolean handlingState = false;

    public HorizonGuiModule() {
        super("Horizon", "Horizon gui");
        setKeybind(GLFW.GLFW_KEY_RIGHT_SHIFT);
        INSTANCE = this;
    }

    public static HorizonGuiModule get() { return INSTANCE; }

    @Override
    protected void onEnable() {
        if (handlingState) return;

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null) {
            setEnabledRaw(false);
            return;
        }

        handlingState = true;
        try {
            if (!(mc.currentScreen instanceof HorizonScreen)) {
                mc.setScreen(new HorizonScreen());
            }
        } finally {
            handlingState = false;
        }
    }

    @Override
    protected void onDisable() {
        if (handlingState) return;
        handlingState = true;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen instanceof HorizonScreen) {
                mc.setScreen(null);
            }
        } finally {
            handlingState = false;
        }
    }
}
