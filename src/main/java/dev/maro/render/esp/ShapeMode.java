package dev.maro.render.esp;

public enum ShapeMode {
    Sides, Lines, Both;
    public boolean sides() { return this != Lines; }
    public boolean lines() { return this != Sides; }
}
