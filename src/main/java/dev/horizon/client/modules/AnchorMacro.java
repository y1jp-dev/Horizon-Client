package dev.horizon.client.modules;

import dev.horizon.client.mixin.MinecraftClientAccessor;
import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class AnchorMacro extends Module {

    public final Setting<Double>  switchDelay        = addSetting(new Setting<>("Switch Delay",      "Ticks before switching item",     0.0, 0.0, 20.0));
    public final Setting<Double>  glowstoneDelay     = addSetting(new Setting<>("Glowstone Delay",   "Ticks before placing glowstone",  0.0, 0.0, 20.0));
    public final Setting<Double>  explodeDelay       = addSetting(new Setting<>("Explode Delay",      "Ticks before exploding anchor",   0.0, 0.0, 20.0));
    public final Setting<Double>  totemSlot          = addSetting(new Setting<>("Totem Slot",         "Hotbar slot with totem (1-9)",    1.0, 1.0,  9.0));
    public final Setting<Boolean> switchBackToAnchor = addSetting(new Setting<>("Switch Back",        "Switch back to anchor after use", true));
    public final Setting<Double>  switchBackDelay    = addSetting(new Setting<>("Switch Back Delay",  "Ticks before switching back",     5.0, 0.0, 20.0));
    public final Setting<Boolean> pauseOnKill        = addSetting(new Setting<>("Pause On Kill",      "Pause when a player dies nearby", true));
    public final Setting<Double>  pauseDelay         = addSetting(new Setting<>("Pause Delay",        "Seconds to pause after kill",     2.0, 0.5, 10.0));

    private int keybindCounter, glowstoneDelayCounter, explodeDelayCounter, switchBackDelayCounter, pauseCounter;
    private boolean hasPlacedGlowstone, hasExplodedAnchor, shouldSwitchBack;
    private final List<PlayerEntity> deadPlayers = new ArrayList<>();

    public AnchorMacro() {
        super("AnchorMacro", "Automatically charges and explodes respawn anchors");
    }

    @Override
    protected void onEnable()  { reset(); }

    @Override
    protected void onDisable() { reset(); }

    private void reset() {
        keybindCounter = glowstoneDelayCounter = explodeDelayCounter = switchBackDelayCounter = pauseCounter = 0;
        hasPlacedGlowstone = hasExplodedAnchor = shouldSwitchBack = false;
        deadPlayers.clear();
    }

    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        if (pauseCounter > 0) pauseCounter--;
        if (pauseOnKill.getValue() && checkDeadPlayers(mc)) pauseCounter = (int)(pauseDelay.getValue() * 20.0);
        if (pauseCounter > 0) return;

        if (isShieldOrFoodActive(mc)) return;

        if (shouldSwitchBack && switchBackToAnchor.getValue()) {
            if (switchBackDelayCounter < switchBackDelay.getValue().intValue()) { switchBackDelayCounter++; return; }
            switchBackDelayCounter = 0;
            swapToItem(mc, Items.RESPAWN_ANCHOR);
            shouldSwitchBack = false;
            return;
        }

        boolean rmb = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (!rmb) {
            hasPlacedGlowstone = hasExplodedAnchor = shouldSwitchBack = false;
            return;
        }

        if (!(mc.crosshairTarget instanceof BlockHitResult bhr)) return;
        BlockPos pos = bhr.getBlockPos();
        if (mc.world.getBlockState(pos).getBlock() != Blocks.RESPAWN_ANCHOR) return;

        mc.options.useKey.setPressed(false);

        int charges = mc.world.getBlockState(pos).get(RespawnAnchorBlock.CHARGES);

        if (charges == 0 && !hasPlacedGlowstone) {

            if (!mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
                if (keybindCounter < switchDelay.getValue().intValue()) { keybindCounter++; return; }
                keybindCounter = 0;
                swapToItem(mc, Items.GLOWSTONE);
                return;
            }
            if (glowstoneDelayCounter < glowstoneDelay.getValue().intValue()) { glowstoneDelayCounter++; return; }
            glowstoneDelayCounter = 0;
            ((MinecraftClientAccessor)(Object) mc).invokeDoItemUse();
            hasPlacedGlowstone = true;
        } else if (charges > 0 && !hasExplodedAnchor) {
            if (hasLootNearby(mc, pos)) return;

            int slot = totemSlot.getValue().intValue() - 1;
            if (mc.player.getInventory().selectedSlot != slot) {
                if (keybindCounter < switchDelay.getValue().intValue()) { keybindCounter++; return; }
                keybindCounter = 0;
                mc.player.getInventory().selectedSlot = slot;
                return;
            }
            if (explodeDelayCounter < explodeDelay.getValue().intValue()) { explodeDelayCounter++; return; }
            explodeDelayCounter = 0;
            ((MinecraftClientAccessor)(Object) mc).invokeDoItemUse();
            hasExplodedAnchor = true;
            if (switchBackToAnchor.getValue()) { shouldSwitchBack = true; switchBackDelayCounter = 0; }
        }
    }

    private boolean checkDeadPlayers(MinecraftClient mc) {
        boolean found = false;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (p.getHealth() <= 0f && !deadPlayers.contains(p)) { deadPlayers.add(p); found = true; }
        }
        deadPlayers.removeIf(p -> p.getHealth() > 0f);
        return found;
    }

    private boolean isShieldOrFoodActive(MinecraftClient mc) {
        ItemStack main = mc.player.getMainHandStack();
        ItemStack off  = mc.player.getOffHandStack();
        boolean rmb = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean food   = main.contains(DataComponentTypes.FOOD) || off.contains(DataComponentTypes.FOOD);
        boolean shield = main.getItem() instanceof ShieldItem    || off.getItem() instanceof ShieldItem;
        return (food || shield) && rmb;
    }

    private void swapToItem(MinecraftClient mc, Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) {
                mc.player.getInventory().selectedSlot = i;
                return;
            }
        }
    }

    private boolean hasLootNearby(MinecraftClient mc, BlockPos pos) {
        double r = 10.0;
        Box box = new Box(pos.getX() - r, pos.getY() - r, pos.getZ() - r,
                          pos.getX() + r, pos.getY() + r, pos.getZ() + r);
        for (Entity e : mc.world.getEntitiesByClass(ItemEntity.class, box, ie -> true)) {
            ItemStack s = ((ItemEntity) e).getStack();
            if (s.isEmpty()) continue;
            Item item = s.getItem();
            if (item instanceof ArmorItem || item instanceof SwordItem) return true;
            if (item == Items.DIAMOND || item == Items.TOTEM_OF_UNDYING) return true;
        }
        return false;
    }
}
