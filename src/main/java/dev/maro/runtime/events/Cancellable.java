package dev.maro.runtime.events;

public class Cancellable { private boolean cancelled; public void cancel() { cancelled = true; } public boolean isCancelled() { return cancelled; } }
