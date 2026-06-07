package dev.horizon.client.modules;

import dev.horizon.client.module.Module;
import dev.horizon.client.module.Setting;
import net.minecraft.client.MinecraftClient;

public class NameProtectModule extends Module {

    public final Setting<String> fakeName = addSetting(
            new Setting<>("Fake Name", "Name shown instead of yours", "Player"));

    public final Setting<Boolean> hideOthers = addSetting(
            new Setting<>("Hide Others", "Replace other players' names with a custom name", false));

    public final Setting<String> othersName = addSetting(
            new Setting<>("Others Name", "Name shown instead of other players' names", "Player"));

    private static NameProtectModule INSTANCE;

    public NameProtectModule() {
        super("Name Protect", "Replaces your username with a custom name");
        INSTANCE = this;
    }

    public static NameProtectModule get() { return INSTANCE; }

    public String getRealName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.getSession() == null ? "" : mc.getSession().getUsername();
    }

    public String getFakeName() {
        String name = fakeName.getValue().trim();
        return name.isEmpty() ? "Player" : name;
    }

    public String getOthersName() {
        String name = othersName.getValue().trim();
        return name.isEmpty() ? "Player" : name;
    }
}
