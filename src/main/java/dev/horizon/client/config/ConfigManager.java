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
                else if (s.getType() == Setting.SettingType.STRING)
                    settings.addProperty(s.getName(), (String) s.getValue());
            }
            mObj.add("settings", settings);

            if (mod instanceof dev.horizon.client.modules.HudModule hud) {
                JsonObject pos = new JsonObject();
                pos.addProperty("wmPX",    hud.getWmPX());
                pos.addProperty("wmPY",    hud.getWmPY());
                pos.addProperty("infoPX",  hud.getInfoPX());
                pos.addProperty("infoPY",  hud.getInfoPY());
                pos.addProperty("coordPX", hud.getCoordPX());
                pos.addProperty("coordPY", hud.getCoordPY());
                pos.addProperty("radarPX", hud.getRadarPX());
                pos.addProperty("radarPY", hud.getRadarPY());
                mObj.add("hudPositions", pos);
            }
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
                        else if (s.getType() == Setting.SettingType.STRING)
                            ((Setting<String>) s).setValue(settings.get(s.getName()).getAsString());
                    }
                }

                if (mod instanceof dev.horizon.client.modules.HudModule hud && mObj.has("hudPositions")) {
                    JsonObject pos = mObj.getAsJsonObject("hudPositions");
                    if (pos.has("wmPX"))    hud.setWmPX(pos.get("wmPX").getAsInt());
                    if (pos.has("wmPY"))    hud.setWmPY(pos.get("wmPY").getAsInt());
                    if (pos.has("infoPX"))  hud.setInfoPX(pos.get("infoPX").getAsInt());
                    if (pos.has("infoPY"))  hud.setInfoPY(pos.get("infoPY").getAsInt());
                    if (pos.has("coordPX")) hud.setCoordPX(pos.get("coordPX").getAsInt());
                    if (pos.has("coordPY")) hud.setCoordPY(pos.get("coordPY").getAsInt());
                    if (pos.has("radarPX")) hud.setRadarPX(pos.get("radarPX").getAsInt());
                    if (pos.has("radarPY")) hud.setRadarPY(pos.get("radarPY").getAsInt());
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
