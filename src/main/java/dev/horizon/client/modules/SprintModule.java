package dev.horizon.client.modules;

import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.minecraft.client.MinecraftClient;

/**
 * AutoSprint — forces the player to sprint whenever possible.
 * Uses Minecraft's built-in setSprinting method, no mixins needed.
 */
public class SprintModule extends Module {

    public final Setting<Boolean> omniSprint = addSetting(
            new Setting<>("Omni Sprint", "Sprint in all directions, not just forward", false)
    );

    public SprintModule() {
        super("AutoSprint", "Automatically sprints for you");
    }

    /**
     * Called every client tick while enabled (hooked from ClientMixin).
     * Forces sprinting if the player is moving (or always if Omni Sprint is on).
     */
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.player.input == null) return;
        if (mc.player.isSwimming() || mc.player.isCrawling()) return;
        if (mc.player.getHungerManager().getFoodLevel() <= 6) return; // can't sprint when starving

        boolean moving = mc.player.input.movementForward > 0;
        boolean shouldSprint = omniSprint.getValue()
                ? (mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0)
                : moving;

        if (shouldSprint && !mc.player.isSprinting()) {
            mc.player.setSprinting(true);
        }
    }
}
