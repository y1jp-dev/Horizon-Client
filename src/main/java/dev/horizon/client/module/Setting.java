package dev.horizon.client.module;

/**
 * Represents a configurable setting for a module.
 */
public class Setting<T> {

    private final String name;
    private final String description;
    private T value;
    private final T min;
    private final T max;
    private final SettingType type;

    public enum SettingType {
        BOOLEAN, SLIDER, ENUM
    }

    // Boolean setting
    public Setting(String name, String description, boolean defaultValue) {
        this.name = name;
        this.description = description;
        this.value = (T) Boolean.valueOf(defaultValue);
        this.min = null;
        this.max = null;
        this.type = SettingType.BOOLEAN;
    }

    // Slider setting
    public Setting(String name, String description, double defaultValue, double min, double max) {
        this.name = name;
        this.description = description;
        this.value = (T) Double.valueOf(defaultValue);
        this.min = (T) Double.valueOf(min);
        this.max = (T) Double.valueOf(max);
        this.type = SettingType.SLIDER;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public T getValue() { return value; }
    public void setValue(T value) { this.value = value; }
    public T getMin() { return min; }
    public T getMax() { return max; }
    public SettingType getType() { return type; }
}
