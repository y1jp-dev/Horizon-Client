package dev.horizon.client.modules;

import dev.horizon.client.module.Module;
import net.minecraft.util.Identifier;

public class SkinProtectModule extends Module {

    private static final Identifier FAKE_SKIN = Identifier.of("horizon", "no_skin");

    private static boolean active = false;

    public SkinProtectModule() {
        super("Skin Protect", "Hides your real skin");
    }

    @Override
    protected void onEnable()  { active = true;  }

    @Override
    protected void onDisable() { active = false; }

    public static Identifier getOverrideSkin() {
        return active ? FAKE_SKIN : null;
    }
}
