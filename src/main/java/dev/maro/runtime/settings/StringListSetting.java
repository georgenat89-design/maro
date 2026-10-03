package dev.maro.runtime.settings;

    public final class StringListSetting { public static class Builder extends Setting.Builder<java.util.List<String>,Builder> {
     @SafeVarargs public final Builder defaultValue(String... values) { defaultValue=java.util.List.of(values);return this; }
     public Builder filter(java.util.function.Predicate<String> filter) { return this; }
    } }
