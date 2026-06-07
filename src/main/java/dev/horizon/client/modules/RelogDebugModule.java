package dev.horizon.client.modules;

import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class RelogDebugModule extends Module {

    public final Setting<Double> yLevel = addSetting(
        new Setting<>("Y Level", "Triggers below this Y level", -30.0, -64.0, 0.0)
    );

    public final Setting<Boolean> autoLog = addSetting(
        new Setting<>("Auto Log", "Automatically disconnects and turns off the module when triggered", false)
    );

    public RelogDebugModule() {
        super("Relog Debug", "Spam in chat when below a set Y level");
    }

    @Override
    protected void onEnable() {}

    @Override
    protected void onDisable() {}

    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        double y         = mc.player.getY();
        double threshold = yLevel.getValue();

        if (y <= threshold) {
            if (autoLog.getValue()) {

                mc.player.networkHandler.getConnection()
                    .disconnect(Text.literal("RELOG AND FLY UP"));
                this.setEnabled(false);
            } else {

                mc.player.sendMessage(
                    Text.literal("[Horizon] Y Entity Limit Packet Blocked RELOG FLY UP"),
                    false
                );
            }
        }
    }
}
