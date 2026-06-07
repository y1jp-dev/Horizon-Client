package dev.horizon.client.modules;

import dev.horizon.client.mixin.HandledScreenAccessor;
import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public class HoverTotem extends Module {

    public final Setting<Boolean> hotbarTotem       = addSetting(new Setting<>("Hotbar Totem",          "Also stock a hotbar slot with a totem",          true));
    public final Setting<Double>  hotbarSlot         = addSetting(new Setting<>("Hotbar Slot",           "Hotbar slot to stock (1-9)",                     1.0, 1.0, 9.0));
    public final Setting<Boolean> autoSwitchToTotem  = addSetting(new Setting<>("Auto Switch To Totem",  "Switch to the totem hotbar slot on inv open",    false));
    public final Setting<Boolean> autoInvOpen        = addSetting(new Setting<>("Auto Inv Open",         "Auto-open inventory when offhand has no totem",  false));

    private boolean shouldOpenInv  = false;
    private boolean totemEquipped  = false;
    private boolean wasAutoOpened  = false;
    private boolean hadOffhandTotem = false;
    private long lastSwapTime = 0;

    public HoverTotem() {
        super("HoverTotem", "Equips a totem when hovering over one");
    }

    @Override
    protected void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        shouldOpenInv   = false;
        totemEquipped   = false;
        wasAutoOpened   = false;
        lastSwapTime    = 0;
        hadOffhandTotem = mc != null && mc.player != null
                && mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
    }

    @Override
    protected void onDisable() {
        shouldOpenInv  = false;
        totemEquipped  = false;
        wasAutoOpened  = false;
        lastSwapTime   = 0;
        hadOffhandTotem = false;
    }

    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        boolean hasOffhandTotem = mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);

        if (hadOffhandTotem && !hasOffhandTotem
                && autoInvOpen.getValue()
                && hasTotemInInventory(mc)
                && mc.currentScreen == null) {
            shouldOpenInv  = true;
            totemEquipped  = false;
            wasAutoOpened  = true;
        }
        hadOffhandTotem = hasOffhandTotem;

        if (autoInvOpen.getValue() && !hasOffhandTotem
                && !shouldOpenInv
                && hasTotemInInventory(mc)
                && mc.currentScreen == null) {
            shouldOpenInv = true;
        }

        if (shouldOpenInv && mc.currentScreen == null) {
            if (hasTotemInInventory(mc)) {
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.setScreen(new InventoryScreen(mc.player));
                        shouldOpenInv  = false;
                        totemEquipped  = false;
                        wasAutoOpened  = true;
                    }
                });
            } else {
                shouldOpenInv = false;
            }
            return;
        }

        if (!(mc.currentScreen instanceof InventoryScreen inventoryScreen)) {
            if (wasAutoOpened && !totemEquipped
                    && mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                totemEquipped = true;
                wasAutoOpened = false;
            }
            if (wasAutoOpened && !totemEquipped) {
                shouldOpenInv = true;
            }
            return;
        }

        if (autoInvOpen.getValue() && !totemEquipped
                && mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            totemEquipped = true;
            wasAutoOpened = false;
            mc.execute(() -> {
                if (mc.player != null) mc.player.closeHandledScreen();
            });
            return;
        }

        Slot focusedSlot = ((HandledScreenAccessor) inventoryScreen).horizon$getFocusedSlot();
        if (focusedSlot == null || focusedSlot.getIndex() > 35) return;

        if (autoSwitchToTotem.getValue()) {
            mc.player.getInventory().selectedSlot = hotbarSlot.getValue().intValue() - 1;
        }

        if (!focusedSlot.getStack().isOf(Items.TOTEM_OF_UNDYING)) return;

        long now = System.currentTimeMillis();
        if (now - lastSwapTime < 100) return;

        int slotIndex = focusedSlot.getIndex();
        int syncId    = inventoryScreen.getScreenHandler().syncId;

        if (!mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            equipOffhandTotem(mc, syncId, slotIndex);
            lastSwapTime = now;
            return;
        }

        if (hotbarTotem.getValue()) {
            int hotbarIndex = hotbarSlot.getValue().intValue() - 1;
            if (!mc.player.getInventory().getStack(hotbarIndex).isOf(Items.TOTEM_OF_UNDYING)) {
                equipHotbarTotem(mc, syncId, slotIndex, hotbarIndex);
                lastSwapTime = now;
            }
        }
    }

    private boolean hasTotemInInventory(MinecraftClient mc) {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return true;
        }
        return false;
    }

    private void equipOffhandTotem(MinecraftClient mc, int syncId, int slotIndex) {
        mc.interactionManager.clickSlot(syncId, slotIndex, 40, SlotActionType.SWAP, mc.player);
    }

    private void equipHotbarTotem(MinecraftClient mc, int syncId, int slotIndex, int hotbarIndex) {
        mc.interactionManager.clickSlot(syncId, slotIndex, hotbarIndex, SlotActionType.SWAP, mc.player);
    }
}
