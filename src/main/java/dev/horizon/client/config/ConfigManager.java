package dev.horizon.client.config;

import com.google.gson.*;
import dev.horizon.client.module.Module;
import dev.horizon.client.module.ModuleManager;
import dev.horizon.client.module.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;

public class ConfigManager {

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("horizon.json");

    private final ModuleManager moduleManager;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ConfigManager(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public void save() {
        JsonObject root = new JsonObject();
        for (Module mod : moduleManager.getModules()) {
            JsonObject mObj = new JsonObject();
            mObj.addProperty("enabled", mod.isEnabled());
            mObj.addProperty("keybind", mod.getKeybind());
            JsonObject settings = new JsonObject();
            for (Setting<?> s : mod.getSettings()) {
                if (s.getType() == Setting.SettingType.BOOLEAN)
                    settings.addProperty(s.getName(), (Boolean) s.getValue());
                else if (s.getType() == Setting.SettingType.SLIDER)
                    settings.addProperty(s.getName(), (Double) s.getValue());
            }
            mObj.add("settings", settings);
            root.add(mod.getName(), mObj);
        }
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
            gson.toJson(root, w);
        } catch (IOException e) { e.printStackTrace(); }
    }

    public void load() {
        if (!Files.exists(CONFIG_PATH)) return;
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (Module mod : moduleManager.getModules()) {
                if (!root.has(mod.getName())) continue;
                JsonObject mObj = root.getAsJsonObject(mod.getName());
                if (mObj.has("enabled") && mObj.get("enabled").getAsBoolean() != mod.isEnabled())
                    mod.toggle();
                if (mObj.has("keybind"))
                    mod.setKeybind(mObj.get("keybind").getAsInt());
                if (mObj.has("settings")) {
                    JsonObject settings = mObj.getAsJsonObject("settings");
                    for (Setting<?> s : mod.getSettings()) {
                        if (!settings.has(s.getName())) continue;
                        if (s.getType() == Setting.SettingType.BOOLEAN)
                            ((Setting<Boolean>) s).setValue(settings.get(s.getName()).getAsBoolean());
                        else if (s.getType() == Setting.SettingType.SLIDER)
                            ((Setting<Double>) s).setValue(settings.get(s.getName()).getAsDouble());
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
