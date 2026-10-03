package dev.maro.runtime.settings;
public final class IntSetting { public static class Builder extends Setting.Builder<Integer, Builder> { public Builder(){step=1;} public Builder defaultValue(int value){defaultValue=value;return this;} } }
