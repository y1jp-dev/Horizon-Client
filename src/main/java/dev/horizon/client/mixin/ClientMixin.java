package dev.horizon.client.mixin;

import dev.horizon.client.HorizonClient;
import dev.horizon.client.gui.HorizonScreen;
import dev.horizon.client.module.Module;
import dev.horizon.client.modules.FullBrightModule;
import dev.horizon.client.modules.SprintModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(MinecraftClient.class)
public abstract class ClientMixin {

    @Shadow public Screen currentScreen;
    @Shadow public abstract void setScreen(Screen screen);

    // Track previous key states so we fire on key-down, not key-held
    private final Map<Integer, Boolean> prevKeyState = new HashMap<>();

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient)(Object) this;
        if (HorizonClient.getInstance() == null) return;

        // ── Tick functional modules ─────────────────────────────
        // Guard: player and world must both be ready. During server join,
        // player exists before the world is fully loaded — ticking too early
        // causes NPE (player.input is null, effect registry not ready).
        if (client.player == null || client.world == null) return;

        for (Module mod : HorizonClient.getInstance().getModuleManager().getModules()) {
            if (!mod.isEnabled()) continue;
            if (mod instanceof SprintModule sprint)      sprint.onTick();
            if (mod instanceof FullBrightModule fb)      fb.onTick();
        }
    }

    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void onHandleInputEvents(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient)(Object) this;
        if (HorizonClient.getInstance() == null) return;

        long window = client.getWindow().getHandle();

        // ── Right Shift → open GUI ───────────────────────────────
        boolean rShiftNow = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean rShiftPrev = prevKeyState.getOrDefault(GLFW.GLFW_KEY_RIGHT_SHIFT, false);
        if (rShiftNow && !rShiftPrev && currentScreen == null) {
            client.setScreen(new HorizonScreen());
        }
        prevKeyState.put(GLFW.GLFW_KEY_RIGHT_SHIFT, rShiftNow);

        // ── Module keybinds ──────────────────────────────────────
        if (currentScreen != null) return; // don't fire binds while GUI is open

        for (Module mod : HorizonClient.getInstance().getModuleManager().getModules()) {
            int key = mod.getKeybind();
            if (key == GLFW.GLFW_KEY_UNKNOWN) continue;

            boolean pressed = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
            boolean wasPressed = prevKeyState.getOrDefault(key, false);

            if (pressed && !wasPressed) {
                mod.toggle();
            }
            prevKeyState.put(key, pressed);
        }
    }
}
