package dev.horizon.client.modules;

import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public class TriggerBot extends Module {

    public final Setting<Boolean> anyItem       = addSetting(new Setting<>("Any Item",        "Attack with any held item",        false));
    public final Setting<Boolean> inScreen      = addSetting(new Setting<>("Work In Screen",  "Attack even with screen open",     false));
    public final Setting<Boolean> whileUse      = addSetting(new Setting<>("While Use",       "Attack while right-clicking",      false));
    public final Setting<Boolean> onLeftClick   = addSetting(new Setting<>("On Left Click",   "Only trigger while holding LMB",   false));
    public final Setting<Double>  minSwordDelay = addSetting(new Setting<>("Min Sword Delay", "Min sword attack delay (ms)",      540.0, 0.0, 1000.0));
    public final Setting<Double>  maxSwordDelay = addSetting(new Setting<>("Max Sword Delay", "Max sword attack delay (ms)",      550.0, 0.0, 1000.0));
    public final Setting<Double>  minAxeDelay   = addSetting(new Setting<>("Min Axe Delay",   "Min axe attack delay (ms)",        780.0, 0.0, 1000.0));
    public final Setting<Double>  maxAxeDelay   = addSetting(new Setting<>("Max Axe Delay",   "Max axe attack delay (ms)",        800.0, 0.0, 1000.0));
    public final Setting<Boolean> checkShield   = addSetting(new Setting<>("Check Shield",    "Skip shielding targets",           false));
    public final Setting<Boolean> onlyCritSword = addSetting(new Setting<>("Only Crit Sword", "Only sword-crit when in the air",  false));
    public final Setting<Boolean> onlyCritAxe   = addSetting(new Setting<>("Only Crit Axe",   "Only axe-crit when in the air",    false));
    public final Setting<Boolean> swingHand     = addSetting(new Setting<>("Swing Hand",      "Swing hand on attack",             true));
    public final Setting<Boolean> whileAscend   = addSetting(new Setting<>("While Ascending", "Attack while moving upward",       false));
    public final Setting<Boolean> allEntities   = addSetting(new Setting<>("All Entities",    "Target any living entity",         false));
    public final Setting<Boolean> sticky        = addSetting(new Setting<>("Same Player",     "Only attack the targeted player",  false));

    private final Random random = new Random();
    private long lastAttack  = 0;
    private int  swordDelay;
    private int  axeDelay;

    public TriggerBot() {
        super("TriggerBot", "Automatically hits players for you");
    }

    @Override
    protected void onEnable() {
        randomizeDelays();
    }

    @Override
    protected void onDisable() {}

    private void randomizeDelays() {
        swordDelay = randBetween(minSwordDelay.getValue().intValue(), maxSwordDelay.getValue().intValue());
        axeDelay   = randBetween(minAxeDelay.getValue().intValue(),   maxAxeDelay.getValue().intValue());
    }

    private int randBetween(int min, int max) {
        if (min >= max) return min;
        return min + random.nextInt(max - min + 1);
    }

    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!inScreen.getValue() && mc.currentScreen != null) return;
        if (onLeftClick.getValue() &&
                GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS)
            return;
        if (!whileUse.getValue() &&
                GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS)
            return;
        if (!whileAscend.getValue() && mc.player.isOnGround() == false && mc.player.getVelocity().y > 0) return;

        Item item = mc.player.getMainHandStack().getItem();
        if (!isItemAllowed(item)) return;

        Entity target = getTarget(mc);
        if (target == null) return;
        if (!isValidTarget(mc, target, item instanceof SwordItem ? onlyCritSword.getValue() : onlyCritAxe.getValue()))
            return;

        int delay = (item instanceof AxeItem) ? axeDelay : swordDelay;
        long now  = System.currentTimeMillis();
        if (now - lastAttack < delay) return;

        mc.interactionManager.attackEntity(mc.player, target);
        if (swingHand.getValue()) mc.player.swingHand(Hand.MAIN_HAND);
        lastAttack = now;
        randomizeDelays();
    }

    private boolean isItemAllowed(Item item) {
        if (anyItem.getValue()) return true;
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof MaceItem;
    }

    private Entity getTarget(MinecraftClient mc) {
        if (!(mc.crosshairTarget instanceof EntityHitResult ehr)) return null;
        Entity e = ehr.getEntity();
        if (sticky.getValue() && e != mc.targetedEntity) return null;
        return e;
    }

    private boolean isValidTarget(MinecraftClient mc, Entity entity, boolean critCheck) {
        if (entity == null || !entity.isAlive()) return false;
        boolean typeValid = entity instanceof PlayerEntity
                || (allEntities.getValue())
                || entity instanceof SkeletonEntity;
        if (!typeValid) return false;
        if (checkShield.getValue() && entity instanceof PlayerEntity p && p.isBlocking()) return false;
        if (critCheck) return canCrit(mc);
        return true;
    }

    private boolean canCrit(MinecraftClient mc) {
        return !mc.player.isOnGround()
                && mc.player.getVelocity().y < 0
                && !mc.player.isClimbing()
                && !mc.player.isSubmergedInWater();
    }
}
