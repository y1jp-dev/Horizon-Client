package dev.horizon.client.mixin;

import dev.horizon.client.modules.NameProtectModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.TextVisitFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Collection;

@Mixin(TextVisitFactory.class)
public class NameProtectMixin {

    @ModifyVariable(
        method = "visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z",
        at = @At("HEAD"),
        argsOnly = true,
        index = 0
    )
    private static String replaceText(String text) {
        if (text == null || text.isEmpty()) return text;

        NameProtectModule mod = NameProtectModule.get();
        if (mod == null || !mod.isEnabled()) return text;

        String real = mod.getRealName();

        if (!real.isEmpty() && text.contains(real)) {
            text = text.replace(real, mod.getFakeName());
        }

        if (mod.hideOthers.getValue()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getNetworkHandler() != null) {
                Collection<PlayerListEntry> players = mc.getNetworkHandler().getPlayerList();
                String othersReplacement = mod.getOthersName();
                for (PlayerListEntry entry : players) {
                    String playerName = entry.getProfile().getName();

                    if (playerName.equals(real)) continue;
                    if (text.contains(playerName)) {
                        text = text.replace(playerName, othersReplacement);
                    }
                }
            }
        }

        return text;
    }
}
