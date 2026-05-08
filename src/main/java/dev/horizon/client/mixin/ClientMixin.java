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


    private final Map<Integer, Boolean> prevKeyState = new HashMap<>();

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient)(Object) this;
        if (HorizonClient.getInstance() == null) return;





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


        boolean rShiftNow = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean rShiftPrev = prevKeyState.getOrDefault(GLFW.GLFW_KEY_RIGHT_SHIFT, false);
        if (rShiftNow && !rShiftPrev && currentScreen == null) {
            client.setScreen(new HorizonScreen());
        }
        prevKeyState.put(GLFW.GLFW_KEY_RIGHT_SHIFT, rShiftNow);


        if (currentScreen != null) return;

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
