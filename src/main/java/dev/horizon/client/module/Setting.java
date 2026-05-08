package dev.horizon.client.module;


public class Setting<T> {

    private final String name;
    private final String description;
    private T value;
    private final T defaultValue;
    private final T min;
    private final T max;
    private final SettingType type;

    public enum SettingType {
        BOOLEAN, SLIDER, ENUM
    }


    public Setting(String name, String description, boolean defaultValue) {
        this.name = name;
        this.description = description;
        this.value = (T) Boolean.valueOf(defaultValue);
        this.defaultValue = (T) Boolean.valueOf(defaultValue);
        this.min = null;
        this.max = null;
        this.type = SettingType.BOOLEAN;
    }


    public Setting(String name, String description, double defaultValue, double min, double max) {
        this.name = name;
        this.description = description;
        this.value = (T) Double.valueOf(defaultValue);
        this.defaultValue = (T) Double.valueOf(defaultValue);
        this.min = (T) Double.valueOf(min);
        this.max = (T) Double.valueOf(max);
        this.type = SettingType.SLIDER;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public T getValue() { return value; }
    public void setValue(T value) { this.value = value; }
    public T getDefault() { return defaultValue; }
    public T getMin() { return min; }
    public T getMax() { return max; }
    public SettingType getType() { return type; }
}
