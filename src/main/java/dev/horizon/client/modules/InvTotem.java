package dev.horizon.client.modules;

import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class InvTotem extends Module {

    public final Setting<Boolean> autoOpen   = addSetting(new Setting<>("Auto Open",
            "Auto-open and close the inventory to equip totems", true));
    public final Setting<Boolean> blatantMode = addSetting(new Setting<>("Blatant Mode",
            "Prefer the first available totem slot (Blatant). Off = always random (Legit)", true));
    public final Setting<Double>  stayOpen   = addSetting(new Setting<>("Stay Open Ticks",
            "How many ticks to keep the inventory open after equipping (20 ticks = 1 s)",
            3.0, 1.0, 40.0));

    private final List<Long> recentIntervals = new ArrayList<>();
    private final Random     random          = new Random();

    private long    nextActionTime      = 0;
    private int     closeClock          = -1;
    private boolean justOpenedInventory = false;
    private boolean moduleOpenedScreen  = false;

    public InvTotem() {
        super("Inv Totem", "Automatically equips totems from inventory into your offhand");
    }

    @Override
    protected void onEnable() {
        nextActionTime      = System.currentTimeMillis();
        closeClock          = -1;
        justOpenedInventory = false;
        moduleOpenedScreen  = false;
    }

    @Override
    protected void onDisable() {
        closeClock          = -1;
        justOpenedInventory = false;
        moduleOpenedScreen  = false;
    }

    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        long now = System.currentTimeMillis();

        if (shouldOpenScreen(mc) && autoOpen.getValue()
                && !(mc.currentScreen instanceof InventoryScreen)) {
            mc.execute(() -> mc.setScreen(new InventoryScreen(mc.player)));
            justOpenedInventory = true;
            moduleOpenedScreen  = true;
            nextActionTime      = now + 150L + random.nextInt(100);
            return;
        }

        if (!(mc.currentScreen instanceof InventoryScreen invScreen)) {
            nextActionTime     = now;
            closeClock         = -1;
            moduleOpenedScreen = false;
            return;
        }

        if (closeClock == -1) {
            closeClock = stayOpen.getValue().intValue() + random.nextInt(3);
        }

        if (now < nextActionTime) return;

        if (justOpenedInventory) {
            justOpenedInventory = false;
            nextActionTime      = now + generateHumanDelay();
            return;
        }

        if (!mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            int slot = chooseTotemSlot(mc);
            if (slot != -1) {
                int syncId     = invScreen.getScreenHandler().syncId;
                int screenSlot = remapToScreenSlot(slot);
                mc.interactionManager.clickSlot(syncId, screenSlot, 40,
                        SlotActionType.SWAP, mc.player);
                nextActionTime = now + generateHumanDelay();
                return;
            }
        }

        if (shouldCloseScreen(mc) && autoOpen.getValue() && moduleOpenedScreen) {
            if (closeClock > 0) {
                closeClock--;
                return;
            }
            mc.execute(() -> mc.setScreen(null));
            moduleOpenedScreen = false;

            closeClock = stayOpen.getValue().intValue() + random.nextInt(3);
        }
    }

    private boolean shouldOpenScreen(MinecraftClient mc) {
        return !mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)
                && findFirstTotemSlot(mc) != -1;
    }

    private boolean shouldCloseScreen(MinecraftClient mc) {
        return mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
    }

    private int chooseTotemSlot(MinecraftClient mc) {
        if (blatantMode.getValue()) {
            return random.nextDouble() < 0.7
                    ? findFirstTotemSlot(mc)
                    : findRandomTotemSlot(mc);
        }
        return findRandomTotemSlot(mc);
    }

    private int findFirstTotemSlot(MinecraftClient mc) {
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING))
                return i;
        }
        return -1;
    }

    private int findRandomTotemSlot(MinecraftClient mc) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING))
                slots.add(i);
        }
        return slots.isEmpty() ? -1 : slots.get(random.nextInt(slots.size()));
    }

    private int remapToScreenSlot(int invSlot) {
        return invSlot < 9 ? 36 + invSlot : invSlot;
    }

    private long generateHumanDelay() {
        long lastDelay  = recentIntervals.isEmpty() ? 120L
                        : recentIntervals.get(recentIntervals.size() - 1);
        long base       = 100 + random.nextInt(100);
        long jitter     = (long) ((random.nextDouble() - 0.5) * (50 + random.nextInt(50)));
        long longPause  = random.nextDouble() < 0.1 ? 200 + random.nextInt(300) : 0L;
        long delay      = (base + jitter + longPause + lastDelay) / 2L;
        delay           = Math.max(50L, Math.min(600L, delay));
        recentIntervals.add(delay);
        if (recentIntervals.size() > 50) recentIntervals.remove(0);
        return delay;
    }
}
