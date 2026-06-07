package dev.horizon.client.mixin;

import dev.horizon.client.HorizonClient;
import dev.horizon.client.gui.HorizonScreen;
import dev.horizon.client.module.Module;
import dev.horizon.client.modules.*;
import dev.horizon.client.modules.HorizonGuiModule;
import dev.horizon.client.modules.FreelookModule;
import dev.horizon.client.modules.HoverTotem;
import dev.horizon.client.modules.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@Mixin(MinecraftClient.class)
public abstract class ClientMixin {

    @Shadow public Screen currentScreen;
    @Shadow public abstract void setScreen(Screen screen);

    private final Map<Integer, Boolean> prevKeyState = new HashMap<>();

    private boolean wasMouseDown = false;
    private double lastMX, lastMY;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient)(Object) this;
        if (HorizonClient.getInstance() == null) return;
        if (client.player == null || client.world == null) return;

        for (Module mod : HorizonClient.getInstance().getModuleManager().getModules()) {
            if (!mod.isEnabled()) continue;
            if (mod instanceof SprintModule     sprint)  sprint.onTick();
            if (mod instanceof FullBrightModule fb)      fb.onTick();
            if (mod instanceof AimAssist        aim)     aim.onTick();
            if (mod instanceof TriggerBot       tb)      tb.onTick();
            if (mod instanceof AnchorMacro      anchor)  anchor.onTick();
            if (mod instanceof CrystalMacro     crystal) crystal.onTick();
            if (mod instanceof HoverTotem        ht)      ht.onTick();
            if (mod instanceof InvTotem          it)      it.onTick();
            if (mod instanceof RelogDebugModule dd)      dd.onTick();
            if (mod instanceof DeepslateDebugModule dsd) dsd.onTick();
        }

        if (!(currentScreen instanceof ChatScreen)) {
            wasMouseDown = false;
            return;
        }
        HudModule hud = HudModule.get();
        if (hud == null) return;

        long window = client.getWindow().getHandle();
        boolean mouseDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        double[] mx = new double[1], my = new double[1];
        GLFW.glfwGetCursorPos(window, mx, my);

        double guiScale = client.getWindow().getScaleFactor();
        double guiMX = mx[0] / guiScale;
        double guiMY = my[0] / guiScale;

        if (mouseDown && !wasMouseDown) {
            hud.onMouseClick(guiMX, guiMY, 0);
        } else if (mouseDown && wasMouseDown) {
            hud.onMouseDrag(guiMX, guiMY);
        } else if (!mouseDown && wasMouseDown) {
            hud.onMouseRelease(guiMX, guiMY, 0);
        }

        wasMouseDown = mouseDown;
        lastMX = guiMX;
        lastMY = guiMY;
    }

    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void onHandleInputEvents(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient)(Object) this;
        if (HorizonClient.getInstance() == null) return;

        long window = client.getWindow().getHandle();

        HorizonGuiModule guiMod = HorizonGuiModule.get();
        int guiKey = (guiMod != null && guiMod.getKeybind() != GLFW.GLFW_KEY_UNKNOWN)
                     ? guiMod.getKeybind()
                     : GLFW.GLFW_KEY_RIGHT_SHIFT;
        boolean guiKeyNow  = GLFW.glfwGetKey(window, guiKey) == GLFW.GLFW_PRESS;
        boolean guiKeyPrev = prevKeyState.getOrDefault(guiKey, false);

        if (guiKeyNow && !guiKeyPrev &&
                (currentScreen == null || currentScreen instanceof HorizonScreen)) {
            if (guiMod != null) {
                guiMod.toggle();
            } else {

                if (currentScreen == null) client.setScreen(new HorizonScreen());
            }
        }
        prevKeyState.put(guiKey, guiKeyNow);

        if (currentScreen != null) return;

        var usedKeys = new HashSet<Integer>();
        for (Module mod : HorizonClient.getInstance().getModuleManager().getModules()) {
            if (mod instanceof HorizonGuiModule) continue;
            int key = mod.getKeybind();
            if (key != GLFW.GLFW_KEY_UNKNOWN) usedKeys.add(key);
        }

        Map<Integer, Boolean> nowState    = new HashMap<>();
        Map<Integer, Boolean> justPressed  = new HashMap<>();
        Map<Integer, Boolean> justReleased = new HashMap<>();
        for (int key : usedKeys) {
            boolean now  = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
            boolean prev = prevKeyState.getOrDefault(key, false);
            nowState.put(key, now);
            justPressed.put(key,   now && !prev);
            justReleased.put(key, !now && prev);
        }

        for (Module mod : HorizonClient.getInstance().getModuleManager().getModules()) {
            if (mod instanceof HorizonGuiModule) continue;
            int key = mod.getKeybind();
            if (key == GLFW.GLFW_KEY_UNKNOWN) continue;
            boolean pressed  = justPressed.getOrDefault(key,  false);
            boolean released = justReleased.getOrDefault(key, false);
            if (mod instanceof FreelookModule fl && fl.isHoldMode()) {
                if (pressed  && !fl.isEnabled()) fl.toggle();
                else if (released && fl.isEnabled()) fl.toggle();
            } else {
                if (pressed) mod.toggle();
            }
        }

        for (int key : usedKeys) prevKeyState.put(key, nowState.get(key));
    }

    @Inject(method = "doAttack", at = @At("HEAD"))
    private void onDoAttack(CallbackInfoReturnable<Boolean> ci) {
        MinecraftClient client = (MinecraftClient)(Object) this;
        if (HorizonClient.getInstance() == null || client.targetedEntity == null) return;
        Entity target = client.targetedEntity;
        if (!(target instanceof PlayerEntity attacked)) return;
        for (Module mod : HorizonClient.getInstance().getModuleManager().getModules()) {
            if (mod instanceof AimAssist aim && aim.isEnabled()) aim.onAttack(attacked);
        }
    }
}
