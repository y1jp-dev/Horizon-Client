package dev.horizon.client.modules;

import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class ItemESPModule extends Module {

    public final Setting<Double>  range     = addSetting(new Setting<>("Range",      "Max render distance",  128.0, 10.0, 512.0));
    public final Setting<Double>  scale     = addSetting(new Setting<>("Scale",      "Label scale",          1.0,   0.1,  5.0));
    public final Setting<Boolean> showName  = addSetting(new Setting<>("Show Name",  "Show the item name",   true));
    public final Setting<Boolean> showCount = addSetting(new Setting<>("Show Count", "Show the stack count", true));
    public final Setting<Boolean> showIcon  = addSetting(new Setting<>("Show Icon",  "Show the item icon",   false));

    public ItemESPModule() {
        super("Item ESP", "Shows dropped items with name and count labels");
    }

    public void render(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        Vec3d   camPos    = context.camera().getPos();
        float   tickDelta = context.tickCounter().getTickDelta(true);
        double  rangeVal  = range.getValue();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;

            double dist = mc.player.distanceTo(itemEntity);
            if (dist > rangeVal) continue;

            ItemStack stack = itemEntity.getStack();
            if (stack.isEmpty()) continue;

            double x = MathHelper.lerp(tickDelta, itemEntity.lastRenderX, itemEntity.getX()) - camPos.x;
            double y = MathHelper.lerp(tickDelta, itemEntity.lastRenderY, itemEntity.getY()) - camPos.y + 1.0;
            double z = MathHelper.lerp(tickDelta, itemEntity.lastRenderZ, itemEntity.getZ()) - camPos.z;

            renderItemLabel(mc, matrices, consumers, context.camera(), x, y, z, stack);
        }
    }

    private void renderItemLabel(MinecraftClient mc, MatrixStack matrices,
                                  VertexConsumerProvider consumers, Camera camera,
                                  double x, double y, double z, ItemStack stack) {
        matrices.push();
        matrices.translate(x, y, z);

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

        float s = scale.getValue().floatValue() * 0.025f;
        matrices.scale(-s, -s, s);

        Matrix4f matrix  = matrices.peek().getPositionMatrix();
        TextRenderer tr  = mc.textRenderer;
        int yOffset      = 0;

        if (showIcon.getValue()) {
            matrices.push();
            matrices.scale(16.0f, 16.0f, 16.0f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
            mc.getItemRenderer().renderItem(
                    stack,
                    ModelTransformationMode.GUI,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    OverlayTexture.DEFAULT_UV,
                    matrices,
                    consumers,
                    mc.world,
                    0
            );
            matrices.pop();
            yOffset += 10;
        }

        if (showName.getValue()) {
            String name  = stack.getName().getString();
            int    width = tr.getWidth(name);
            tr.draw(
                    Text.literal(name),
                    -width / 2.0f,
                    yOffset,
                    0xFFFFFFFF,
                    false,
                    matrix,
                    consumers,
                    TextRenderer.TextLayerType.SEE_THROUGH,
                    0,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE
            );
            yOffset += 10;
        }

        if (showCount.getValue() && stack.getCount() > 1) {
            String count = "x" + stack.getCount();
            int    width = tr.getWidth(count);
            tr.draw(
                    Text.literal(count),
                    -width / 2.0f,
                    yOffset,
                    0xFFAAAAAA,
                    false,
                    matrix,
                    consumers,
                    TextRenderer.TextLayerType.SEE_THROUGH,
                    0,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE
            );
        }

        matrices.pop();
    }
}
