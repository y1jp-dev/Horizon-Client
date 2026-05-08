package dev.horizon.client.module;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;


public abstract class Module {

    private final String name;
    private final String description;
    private boolean enabled;
    private int keybind;
    private final List<Setting<?>> settings = new ArrayList<>();

    public Module(String name, String description) {
        this.name = name;
        this.description = description;
        this.enabled = false;
        this.keybind = GLFW.GLFW_KEY_UNKNOWN;
    }

    public String getName()         { return name; }
    public String getDescription()  { return description; }
    public boolean isEnabled()      { return enabled; }
    public int getKeybind()         { return keybind; }
    public void setKeybind(int key) { this.keybind = key; }


    public String getKeybindName() {
        if (keybind == GLFW.GLFW_KEY_UNKNOWN) return "None";
        String n = GLFW.glfwGetKeyName(keybind, 0);
        if (n != null) return n.toUpperCase();
        return switch (keybind) {
            case GLFW.GLFW_KEY_LEFT_SHIFT   -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT  -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL-> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT     -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT    -> "RALT";
            case GLFW.GLFW_KEY_CAPS_LOCK    -> "CAPS";
            case GLFW.GLFW_KEY_TAB          -> "TAB";
            case GLFW.GLFW_KEY_F1  -> "F1";  case GLFW.GLFW_KEY_F2  -> "F2";
            case GLFW.GLFW_KEY_F3  -> "F3";  case GLFW.GLFW_KEY_F4  -> "F4";
            case GLFW.GLFW_KEY_F5  -> "F5";  case GLFW.GLFW_KEY_F6  -> "F6";
            case GLFW.GLFW_KEY_F7  -> "F7";  case GLFW.GLFW_KEY_F8  -> "F8";
            case GLFW.GLFW_KEY_F9  -> "F9";  case GLFW.GLFW_KEY_F10 -> "F10";
            case GLFW.GLFW_KEY_F11 -> "F11"; case GLFW.GLFW_KEY_F12 -> "F12";
            default -> "KEY:" + keybind;
        };
    }

    public void toggle() {
        enabled = !enabled;
        if (enabled) onEnable();
        else onDisable();
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) toggle();
    }

    protected void onEnable()  {}
    protected void onDisable() {}

    public List<Setting<?>> getSettings() { return settings; }

    public void resetToDefaults() {
        for (Setting<?> s : settings) {
            if (s.getType() == Setting.SettingType.BOOLEAN)
                ((Setting<Boolean>) s).setValue((Boolean) s.getDefault());
            else if (s.getType() == Setting.SettingType.SLIDER)
                ((Setting<Double>) s).setValue((Double) s.getDefault());
        }
    }

    protected <T> Setting<T> addSetting(Setting<T> setting) {
        settings.add(setting);
        return setting;
    }
}
