package dev.maro.runtime.utils.render.color;

public class Color {
 public int r, g, b, a;
 public Color(int r, int g, int b) { this(r, g, b, 255); }
 public Color(int r, int g, int b, int a) { this.r=r; this.g=g; this.b=b; this.a=a; }
 public Color(Color c) { this(c.r,c.g,c.b,c.a); }
 public Color(int argb) { this(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24); }
 public int getPacked() { return (a << 24) | (r << 16) | (g << 8) | b; }
 public Color set(int r,int g,int b,int a) { this.r=r;this.g=g;this.b=b;this.a=a; return this; }
 public Color set(Color c) { return set(c.r,c.g,c.b,c.a); }
}
