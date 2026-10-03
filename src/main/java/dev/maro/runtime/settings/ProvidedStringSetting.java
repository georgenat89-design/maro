package dev.maro.runtime.settings;

public final class ProvidedStringSetting { public static class Builder extends Setting.Builder<String,Builder> {
 public Builder supplier(java.util.function.Supplier<String[]> supplier){choices=supplier;return this;}
} }
