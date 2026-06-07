package dev.horizon.client.modules;

import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.Random;

public class CrystalMacro extends Module {

    public final Setting<Double>  placeDelay     = addSetting(new Setting<>("Place Delay",      "Crystal place delay (ticks)",      0.0,  0.0, 20.0));
    public final Setting<Double>  minCPS         = addSetting(new Setting<>("Min CPS",          "Minimum break CPS",                8.0,  1.0, 20.0));
    public final Setting<Double>  maxCPS         = addSetting(new Setting<>("Max CPS",          "Maximum break CPS",               12.0,  1.0, 20.0));
    public final Setting<Boolean> placeObi       = addSetting(new Setting<>("Place Obsidian",   "Place obsidian before crystal",   false));
    public final Setting<Double>  obiSwitchDelay = addSetting(new Setting<>("Obi Switch Delay", "Obsidian switch delay (ticks)",    0.0,  0.0, 20.0));

    private final Random random = new Random();
    private int placeCtr, breakCtr, obiSwitchCtr;
    private int nextBreakDelay;
    private boolean isPlacingObi;
    private BlockHitResult pendingObiHit;

    public CrystalMacro() {
        super("CrystalMacro", "Automatically places and breaks end crystals");
    }

    @Override
    protected void onEnable() {
        placeCtr = breakCtr = obiSwitchCtr = 0;
        isPlacingObi = false;
        pendingObiHit = null;
        nextBreakDelay = calcBreakDelay();
    }

    @Override
    protected void onDisable() {
        placeCtr = breakCtr = obiSwitchCtr = 0;
        isPlacingObi = false;
        pendingObiHit = null;
    }

    private int calcBreakDelay() {
        double min = minCPS.getValue(), max = maxCPS.getValue();
        if (min > max) { double t = min; min = max; max = t; }
        double cps = min + (max - min) * random.nextDouble();
        return Math.max(1, (int)(20.0 / cps));
    }

    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        if (placeCtr   > 0) placeCtr--;
        if (breakCtr   > 0) breakCtr--;
        if (obiSwitchCtr > 0) obiSwitchCtr--;

        if (mc.player.isSneaking()) return;
        if (!mc.options.useKey.isPressed()) { isPlacingObi = false; pendingObiHit = null; return; }

        if (isPlacingObi && obiSwitchCtr <= 0 && pendingObiHit != null) {
            finishObiPlacement(mc);
            return;
        }

        if (!mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) return;

        var target = mc.crosshairTarget;
        if (target == null) return;

        if (target instanceof BlockHitResult bhr)    handleBlock(mc, bhr);
        else if (target instanceof EntityHitResult ehr) handleEntity(mc, ehr);
    }

    private void handleBlock(MinecraftClient mc, BlockHitResult hit) {
        if (hit.getSide() != Direction.UP || placeCtr > 0 || isPlacingObi) return;
        BlockPos pos   = hit.getBlockPos();
        var      block = mc.world.getBlockState(pos).getBlock();

        if (placeObi.getValue() && block != Blocks.OBSIDIAN && block != Blocks.CRYING_OBSIDIAN) {
            int slot = findHotbarSlot(mc, Items.OBSIDIAN);
            if (slot == -1) return;
            mc.player.getInventory().selectedSlot = slot;
            isPlacingObi  = true;
            pendingObiHit = hit;
            obiSwitchCtr  = obiSwitchDelay.getValue().intValue();
        } else if (block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN) {
            if (!isValidPlacement(mc, pos)) return;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);
            placeCtr = placeDelay.getValue().intValue();
        }
    }

    private void finishObiPlacement(MinecraftClient mc) {
        if (pendingObiHit == null) { isPlacingObi = false; return; }
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, pendingObiHit);
        mc.player.swingHand(Hand.MAIN_HAND);
        int crystalSlot = findHotbarSlot(mc, Items.END_CRYSTAL);
        if (crystalSlot != -1) mc.player.getInventory().selectedSlot = crystalSlot;
        BlockPos pos = pendingObiHit.getBlockPos();
        if (isValidPlacement(mc, pos)) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, pendingObiHit);
            mc.player.swingHand(Hand.MAIN_HAND);
            placeCtr = placeDelay.getValue().intValue();
        }
        isPlacingObi  = false;
        pendingObiHit = null;
    }

    private void handleEntity(MinecraftClient mc, EntityHitResult hit) {
        if (breakCtr > 0 || isPlacingObi) return;
        Entity entity = hit.getEntity();
        if (entity == null || entity.isRemoved() || !(entity instanceof EndCrystalEntity)) return;
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
        breakCtr = nextBreakDelay;
        nextBreakDelay = calcBreakDelay();
    }

    private boolean isValidPlacement(MinecraftClient mc, BlockPos pos) {
        BlockPos up = pos.up();
        if (!mc.world.getBlockState(up).isAir()) return false;
        int x = up.getX(), y = up.getY(), z = up.getZ();
        return mc.world.getOtherEntities(null, new Box(x, y, z, x + 1.0, y + 2.0, z + 1.0)).isEmpty();
    }

    private int findHotbarSlot(MinecraftClient mc, Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }
}
