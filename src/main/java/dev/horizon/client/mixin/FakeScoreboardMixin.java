package dev.horizon.client.mixin;

import dev.horizon.client.modules.FakeScoreboardModule;
import net.minecraft.text.TextVisitFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TextVisitFactory.class)
public class FakeScoreboardMixin {

    private static final ThreadLocal<String>  PENDING_FAKE_VALUE    = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PENDING_SUPPRESS_AFTER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUPPRESS_NEXT          = new ThreadLocal<>();

    @ModifyVariable(
        method = "visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z",
        at = @At("HEAD"),
        argsOnly = true,
        index = 0
    )
    private static String fakeScoreboardValues(String text) {
        if (text == null || text.isEmpty()) return text;

        FakeScoreboardModule mod = FakeScoreboardModule.get();
        if (mod == null || !mod.isEnabled()) {
            PENDING_FAKE_VALUE.remove();
            PENDING_SUPPRESS_AFTER.remove();
            SUPPRESS_NEXT.remove();
            return text;
        }

        if (Boolean.TRUE.equals(SUPPRESS_NEXT.get())) {
            SUPPRESS_NEXT.remove();
            return "";
        }

        String pending = PENDING_FAKE_VALUE.get();
        if (pending != null) {
            PENDING_FAKE_VALUE.remove();
            boolean suppressAfter = Boolean.TRUE.equals(PENDING_SUPPRESS_AFTER.get());
            PENDING_SUPPRESS_AFTER.remove();
            if (suppressAfter) {
                SUPPRESS_NEXT.set(Boolean.TRUE);
            }

            return pending;
        }

        String plain = text.replaceAll("§[0-9a-fk-orA-FK-OR]", "");

        if (containsAny(plain, "Keyall", "keyall")) return text;

        String  fakeValue     = null;
        boolean suppressAfter = false;

        if      (containsAny(plain, "Money", "Shards", "Kills", "Deaths")) {
            fakeValue = "999M";
        } else if (containsAny(plain, "Playtime")) {
            fakeValue = "999d";
        } else if (containsAny(plain, "Team")) {
            fakeValue     = "§0Horizon Client";
            suppressAfter = true;
        } else {
            return text;
        }

        PENDING_FAKE_VALUE.set(fakeValue);
        if (suppressAfter) PENDING_SUPPRESS_AFTER.set(Boolean.TRUE);

        return text;
    }

    private static boolean containsAny(String text, String... keywords) {
        String lower = text.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase())) return true;
        }
        return false;
    }
}
