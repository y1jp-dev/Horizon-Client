package dev.horizon.client.modules;

import dev.horizon.client.module.Module;
import net.minecraft.client.MinecraftClient;

public class SprintModule extends Module {

    public SprintModule() {
        super("AutoSprint", "Auto sprint");
    }

    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.player.input == null) return;
        if (mc.player.isSwimming() || mc.player.isCrawling()) return;
        if (mc.player.getHungerManager().getFoodLevel() <= 6) return;

        if (mc.player.isSneaking()) {
            if (mc.player.isSprinting()) mc.player.setSprinting(false);
            return;
        }

        if (mc.player.input.movementForward > 0 && !mc.player.isSprinting()) {
            mc.player.setSprinting(true);
        }
    }
}
