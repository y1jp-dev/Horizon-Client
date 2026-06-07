package dev.horizon.client.modules;

import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.minecraft.client.MinecraftClient;

public class HomeReset extends Module {

    public final Setting<Double> homeNumber = addSetting(new Setting<>(
            "Home Number",
            "Which home slot to reset (1–5)",
            1.0, 1.0, 5.0
    ));

    private int  state      = 0;
    private long actionTime = 0;

    private static final long BETWEEN_DELAY_MS = 500L;

    public HomeReset() {
        super("Home Reset", "Resets selected home");
    }

    @Override
    protected void onEnable() {
        state      = 1;
        actionTime = 0;
    }

    @Override
    protected void onDisable() {
        state      = 0;
        actionTime = 0;
    }

    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.networkHandler == null) return;

        long now = System.currentTimeMillis();
        int  num = homeNumber.getValue().intValue();

        switch (state) {
            case 1 -> {

                if (now >= actionTime) {
                    mc.player.networkHandler.sendCommand("delhome " + num);
                    state      = 2;
                    actionTime = now + BETWEEN_DELAY_MS;
                }
            }
            case 2 -> {

                if (now >= actionTime) {
                    mc.player.networkHandler.sendCommand("sethome " + num);
                    mc.execute(this::toggle);
                }
            }
        }
    }
}
