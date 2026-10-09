#!/usr/bin/env python3
"""Draws the Cosmetics weapon and tool skins: 50 swords, 25 pickaxes, 25 shovels and 25 tridents as 32x32
pixel art, with an item model and item definition for each, under assets/maro.

Run from the repository root:  python3 tools/cosmeticgen.py
Every skin is drawn from a shape (blade form, guard, head) and a material (colours and an
effect), so the look of a whole family can be changed in one place. Output is deterministic.
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join("src", "main", "resources", "assets", "maro")
SIZE = 32


def hexc(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c)


# ---- materials: blade (dark, mid, light), metal for guard and pommel, grip, accent, effect --------

MATERIALS = {
    "phoenix":   dict(blade=("#7a1f0c", "#e8641c", "#ffd25a"), metal=("#6b3a12", "#d8902a", "#ffe08a"), grip="#4a1a10", accent="#ffef9e", fx="flame"),
    "frost":     dict(blade=("#3c6d93", "#9fd6f5", "#f2fbff"), metal=("#4a5d73", "#a8bfd6", "#e8f4ff"), grip="#2a3b52", accent="#d8f6ff", fx="frost"),
    "acid":      dict(blade=("#2b5a12", "#7bd62a", "#e4ff8a"), metal=("#3a3f2a", "#7f8a55", "#c8d48f"), grip="#1f2a14", accent="#c6ff3a", fx="drip"),
    "amethyst":  dict(blade=("#4a2477", "#9a5ee0", "#e3c9ff"), metal=("#3d2a55", "#8c6fb8", "#d9c8f2"), grip="#2a1a3d", accent="#f0d9ff", fx="crystal"),
    "royal":     dict(blade=("#8a8f99", "#dfe4ea", "#ffffff"), metal=("#7a5a12", "#e3b23c", "#fff0a0"), grip="#5a1a2a", accent="#3aa0ff", fx="gem"),
    "arcane":    dict(blade=("#1e2a6e", "#4a6cf0", "#bcd0ff"), metal=("#2c2f4a", "#6f74a8", "#c5c8ec"), grip="#1a1a33", accent="#7cf7ff", fx="runes"),
    "ashen":     dict(blade=("#2a2a2a", "#6e6a66", "#c9c2b8"), metal=("#3a3330", "#7a6a5c", "#b9a894"), grip="#1e1a18", accent="#ff7a3a", fx="embers"),
    "blood":     dict(blade=("#3d0508", "#a3121c", "#ff5a5f"), metal=("#2a1a1a", "#5e3a3a", "#a87070"), grip="#1a0a0a", accent="#ff2b38", fx="drip"),
    "celestial": dict(blade=("#141a4a", "#3b4fb8", "#9fb4ff"), metal=("#5a4a1a", "#c9a640", "#ffe9a0"), grip="#101433", accent="#ffffff", fx="stars"),
    "sakura":    dict(blade=("#a8577a", "#f2a3c4", "#fff0f6"), metal=("#5a3a2a", "#9c6a4a", "#d9ab88"), grip="#3a2420", accent="#ffd1e3", fx="petals"),
    "copper":    dict(blade=("#7a3a1e", "#c46a3c", "#f2b088"), metal=("#2f6a5c", "#4fa38c", "#9fe0cc"), grip="#3a2418", accent="#5fd0b0", fx="patina"),
    "cyber":     dict(blade=("#0a1a2a", "#14324a", "#2a5a7a"), metal=("#1a1a24", "#3a3a4a", "#6a6a80"), grip="#101018", accent="#29f0ff", fx="neon"),
    "diamond":   dict(blade=("#1f8a8a", "#5ae8e0", "#d6fffb"), metal=("#2a3a3a", "#5c7a78", "#a8c8c4"), grip="#1a2a2a", accent="#e8fffd", fx="crystal"),
    "bone":      dict(blade=("#8a826a", "#d9d0b0", "#fffbe8"), metal=("#4a4030", "#8a7a5c", "#c8b896"), grip="#3a3024", accent="#ff4a2a", fx="cracks"),
    "echo":      dict(blade=("#06262a", "#0f5560", "#2fd0c8"), metal=("#0a1a1e", "#1f3a40", "#3f6a70"), grip="#061014", accent="#4ff7ef", fx="pulse"),
    "emerald":   dict(blade=("#0c5a2a", "#22b05a", "#9cffc0"), metal=("#5a4a1a", "#c9a640", "#ffe9a0"), grip="#1a2a1a", accent="#d8ffe6", fx="gem"),
    "ember":     dict(blade=("#3a1a10", "#8a3a1a", "#ff8a3a"), metal=("#2a2220", "#5a4a40", "#9a8a7a"), grip="#1a1210", accent="#ffd04a", fx="embers"),
    "galaxy":    dict(blade=("#120a2a", "#3a1a6a", "#8a5ae0"), metal=("#1a1a2a", "#4a4a6a", "#9a9ac0"), grip="#0a0a14", accent="#ffffff", fx="stars"),
    "gold":      dict(blade=("#8a5a0a", "#e8b828", "#fff3a0"), metal=("#5a2a6a", "#9a4ab8", "#e0a8ff"), grip="#3a1a4a", accent="#ff4a6a", fx="gem"),
    "hellfire":  dict(blade=("#1a0a0a", "#5a1010", "#ff3a1a"), metal=("#2a1a1a", "#4a2a2a", "#8a4a3a"), grip="#100808", accent="#ffb02a", fx="flame"),
    "jade":      dict(blade=("#1a5a4a", "#3ab08a", "#b8f2dc"), metal=("#6a4a1a", "#c88a2a", "#ffd88a"), grip="#2a1a10", accent="#ffe08a", fx="runes"),
    "lava":      dict(blade=("#2a1410", "#4a2418", "#ff6a1a"), metal=("#1a1a1a", "#3a3a3a", "#6a6a6a"), grip="#100a08", accent="#ffd23a", fx="cracks"),
    "storm":     dict(blade=("#2a3a5a", "#5a7ab8", "#dfe8ff"), metal=("#3a3a4a", "#7a7a8a", "#c8c8d8"), grip="#1a1a2a", accent="#fff36a", fx="bolt"),
    "midnight":  dict(blade=("#0a0e1e", "#1e2a4a", "#4a6ab0"), metal=("#1a1a2a", "#3a3a5a", "#7a7aa8"), grip="#0a0a14", accent="#9ab8ff", fx="stars"),
    "molten":    dict(blade=("#5a1a0a", "#e85a1a", "#ffe08a"), metal=("#2a2a2a", "#5a5a5a", "#9a9a9a"), grip="#1a1a1a", accent="#fff0b0", fx="flame"),
    "nebula":    dict(blade=("#2a0a3a", "#8a2a9a", "#ff8ae0"), metal=("#1a1a2a", "#4a3a6a", "#9a8ac0"), grip="#100a1a", accent="#ffffff", fx="stars"),
    "netherite": dict(blade=("#1e1b1f", "#4a454c", "#8a8490"), metal=("#5a4a1a", "#c9a640", "#ffe9a0"), grip="#100e10", accent="#ffcf4a", fx="runes"),
    "ocean":     dict(blade=("#0a3a5a", "#1a8ab0", "#9ff0ff"), metal=("#3a5a5a", "#6aa8a0", "#c8f0e8"), grip="#0a2a2a", accent="#e8ffff", fx="waves"),
    "obsidian":  dict(blade=("#0e0a16", "#2a1a3a", "#6a4a9a"), metal=("#1a1420", "#3a2a48", "#7a5aa0"), grip="#08060a", accent="#c08aff", fx="crystal"),
    "prismarine":dict(blade=("#1a5a5a", "#4ab8a8", "#c8fff0"), metal=("#2a4a4a", "#5a8a84", "#a8d8d0"), grip="#143030", accent="#a8fff0", fx="crystal"),
    "radiant":   dict(blade=("#a8a080", "#f2ecc8", "#ffffff"), metal=("#8a6a1a", "#f2c84a", "#fff6c0"), grip="#4a3a20", accent="#ffffff", fx="glow"),
    "ruby":      dict(blade=("#5a0a1a", "#d81a3a", "#ffa0b0"), metal=("#5a4a1a", "#c9a640", "#ffe9a0"), grip="#2a0a10", accent="#ffe0e6", fx="gem"),
    "sapphire":  dict(blade=("#0a1a5a", "#1a4ad8", "#a0c0ff"), metal=("#4a4a5a", "#9a9ab0", "#e0e0f0"), grip="#10142a", accent="#e0e8ff", fx="gem"),
    "shadow":    dict(blade=("#08080c", "#1e1e28", "#4a4a5e"), metal=("#14141a", "#2e2e3a", "#5a5a6e"), grip="#060608", accent="#b0b0ff", fx="smoke"),
    "solar":     dict(blade=("#b86a0a", "#ffc23a", "#fffbe0"), metal=("#8a4a0a", "#e8901a", "#ffd08a"), grip="#4a2a0a", accent="#ffffff", fx="glow"),
    "toxic":     dict(blade=("#1a3a0a", "#4a9a1a", "#b8ff4a"), metal=("#2a2a1a", "#5a5a3a", "#9a9a6a"), grip="#141a0a", accent="#d8ff6a", fx="drip"),
    "void":      dict(blade=("#05030a", "#140a24", "#3a1a6a"), metal=("#100a1a", "#2a1a3a", "#5a3a7a"), grip="#050308", accent="#d07aff", fx="stars"),
    "warped":    dict(blade=("#0a3a3a", "#1a8a7a", "#5af0d0"), metal=("#2a1a3a", "#5a3a6a", "#9a6aa8"), grip="#14101a", accent="#ff7af0", fx="patina"),
    "wither":    dict(blade=("#101010", "#2e2e2e", "#6a6a6a"), metal=("#1a1a1a", "#3a3a3a", "#6e6e6e"), grip="#0a0a0a", accent="#e8e8e8", fx="smoke"),
    "zenith":    dict(blade=("#3a6a8a", "#8ad0f0", "#ffffff"), metal=("#5a4a8a", "#a88ae8", "#e8d8ff"), grip="#2a2050", accent="#ffe0ff", fx="glow"),
    "crimson":   dict(blade=("#4a0a1a", "#a81a3a", "#ff6a8a"), metal=("#3a1a2a", "#7a3a5a", "#c07a9a"), grip="#200810", accent="#ff9ab0", fx="petals"),
    "thunder":   dict(blade=("#4a4a1a", "#c8c82a", "#ffffb0"), metal=("#2a2a4a", "#5a5a9a", "#a0a0e0"), grip="#1a1a30", accent="#8ad8ff", fx="bolt"),
}

# ---- the families ---------------------------------------------------------------------------------

SWORDS = [
    ("Phoenix Grace", "saber", "winged", "phoenix"), ("Abominable Great Saber", "greatsaber", "cross", "frost"),
    ("Abominable Blade", "straight", "cross", "frost"), ("Abominable Scythe", "scythe", "none", "frost"),
    ("Acidic Cleaver", "cleaver", "round", "acid"), ("Amethyst Shuriken", "shuriken", "none", "amethyst"),
    ("Ancient Royal Great Sword", "great", "winged", "royal"), ("Arcane Rapier", "rapier", "round", "arcane"),
    ("Ashen Katana", "katana", "disc", "ashen"), ("Blood Moon Saber", "saber", "curved", "blood"),
    ("Celestial Edge", "straight", "winged", "celestial"), ("Cherry Blossom Katana", "katana", "disc", "sakura"),
    ("Copper Rustblade", "straight", "cross", "copper"), ("Crimson Fang", "dagger", "curved", "crimson"),
    ("Crystal Longsword", "long", "cross", "diamond"), ("Cyber Katana", "katana", "disc", "cyber"),
    ("Diamond Claymore", "great", "cross", "diamond"), ("Dragonbone Cleaver", "cleaver", "curved", "bone"),
    ("Echo Reaver", "flamberge", "winged", "echo"), ("Emerald Rapier", "rapier", "round", "emerald"),
    ("Ember Dagger", "dagger", "cross", "ember"), ("Frostbite Edge", "flamberge", "cross", "frost"),
    ("Galaxy Blade", "long", "winged", "galaxy"), ("Glacier Greatsword", "great", "curved", "frost"),
    ("Golden Sovereign", "great", "winged", "gold"), ("Hellfire Scythe", "scythe", "none", "hellfire"),
    ("Inferno Saber", "greatsaber", "curved", "hellfire"), ("Jade Serpent", "flamberge", "round", "jade"),
    ("Lava Cleaver", "cleaver", "cross", "lava"), ("Lightning Katana", "katana", "disc", "storm"),
    ("Midnight Rapier", "rapier", "curved", "midnight"), ("Molten Edge", "straight", "curved", "molten"),
    ("Nebula Scythe", "scythe", "none", "nebula"), ("Netherite Executioner", "cleaver", "winged", "netherite"),
    ("Ocean's Wrath", "saber", "curved", "ocean"), ("Obsidian Fang", "dagger", "winged", "obsidian"),
    ("Prismarine Saber", "saber", "cross", "prismarine"), ("Radiant Paladin Sword", "long", "winged", "radiant"),
    ("Ruby Cutlass", "saber", "round", "ruby"), ("Sakura Petal Blade", "straight", "disc", "sakura"),
    ("Sapphire Longsword", "long", "cross", "sapphire"), ("Shadow Reaper Scythe", "scythe", "none", "shadow"),
    ("Solar Flare Blade", "flamberge", "winged", "solar"), ("Storm Breaker", "great", "curved", "thunder"),
    ("Toxic Venom Dagger", "dagger", "round", "toxic"), ("Void Ripper", "flamberge", "curved", "void"),
    ("Warped Cleaver", "cleaver", "round", "warped"), ("Wither Bane", "long", "curved", "wither"),
    ("Wyvern Fang Shuriken", "shuriken", "none", "obsidian"), ("Zenith Greatsword", "great", "winged", "zenith"),
]

TOOLS = ["Abyssal", "Amethyst", "Arctic", "Blaze", "Celestial", "Cobalt", "Copper", "Crystal", "Dragonbone", "Ember",
         "Emerald", "Frostbite", "Galaxy", "Gilded", "Jade", "Lava", "Lunar", "Netherite", "Obsidian", "Phoenix",
         "Prismarine", "Ruby", "Sculk", "Thunder", "Void"]
TOOL_MATERIAL = {"Abyssal": "void", "Amethyst": "amethyst", "Arctic": "frost", "Blaze": "hellfire", "Celestial": "celestial",
                 "Cobalt": "sapphire", "Copper": "copper", "Crystal": "diamond", "Dragonbone": "bone", "Ember": "ember",
                 "Emerald": "emerald", "Frostbite": "frost", "Galaxy": "galaxy", "Gilded": "gold", "Jade": "jade",
                 "Lava": "lava", "Lunar": "midnight", "Netherite": "netherite", "Obsidian": "obsidian", "Phoenix": "phoenix",
                 "Prismarine": "prismarine", "Ruby": "ruby", "Sculk": "echo", "Thunder": "thunder", "Void": "nebula"}
PICK_FORMS = ["classic", "heavy", "spike", "crescent", "classic"]
SHOVEL_FORMS = ["spade", "round", "pointed", "broad", "spade"]
TRIDENT_FORMS = ["classic", "barbed", "crescent", "royal", "classic"]


def slug(name):
    return "".join(ch if ch.isalnum() else "_" for ch in name.lower()).strip("_").replace("__", "_")


# ---- drawing --------------------------------------------------------------------------------------

class Canvas:
    """Pixels by role, so outlines and shading can be worked out once the shape is in."""

    def __init__(self):
        self.px = {}

    def put(self, x, y, color, layer=0):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            old = self.px.get((x, y))
            if old is None or layer >= old[1]:
                self.px[(x, y)] = (color, layer)

    def outline(self, color):
        filled = set(self.px)
        for (x, y) in list(filled):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (x + dx, y + dy)
                if n not in filled and 0 <= n[0] < SIZE and 0 <= n[1] < SIZE:
                    self.px[n] = (color(x, y), -1)

    def image(self):
        img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        for (x, y), (c, _) in self.px.items():
            img.putpixel((x, y), (c[0], c[1], c[2], 255))
        return img


# The axis runs from the bottom left (pommel, t = 0) to the top right (tip, t = 1).
P0 = (3.2, 28.8)
P1 = (28.8, 3.2)
L = math.dist(P0, P1)
U = ((P1[0] - P0[0]) / L, (P1[1] - P0[1]) / L)
N = (-U[1], U[0])  # perpendicular, pointing to the lower right


def coords(x, y):
    px, py = x + 0.5 - P0[0], y + 0.5 - P0[1]
    return (px * U[0] + py * U[1]) / L, px * N[0] + py * N[1]


def at(t, d):
    return P0[0] + U[0] * t * L + N[0] * d, P0[1] + U[1] * t * L + N[1] * d


def blade_profile(form, t):
    """Half-width and centre offset of the blade at t, or None outside it."""
    start = 0.33
    if form == "dagger":
        if t < start or t > 0.76:
            return None
        u = (t - start) / (0.76 - start)
        return (1.7 * (1 - max(0, u - 0.55) / 0.45) + 0.2, 0.0)
    if t < start or t > 0.995:
        return None
    u = (t - start) / (0.995 - start)
    tip = lambda k: 1 - max(0.0, u - k) / (1 - k)
    if form == "straight":
        return (1.8 * tip(0.8) + 0.15, 0.0)
    if form == "long":
        return (1.5 * tip(0.86) + 0.15, 0.0)
    if form == "great":
        return (2.7 * tip(0.82) + 0.2, 0.0)
    if form == "rapier":
        return (0.85 * tip(0.7) + 0.2, 0.0)
    if form == "katana":
        return (1.25 * tip(0.88) + 0.15, -1.6 * u * u)
    if form == "saber":
        return (1.9 * tip(0.84) + 0.2, -2.6 * u * u)
    if form == "greatsaber":
        return (2.6 * tip(0.8) + 0.25, -2.8 * u * u)
    if form == "cleaver":
        if u > 0.93:
            return None
        return (1.6 + 1.9 * u, 0.9 * u)
    if form == "flamberge":
        return (1.7 * tip(0.84) + 0.2 + 0.55 * math.sin(u * math.pi * 7), 0.0)
    return None


def draw_sword(name, form, guard, mat):
    m = MATERIALS[mat]
    bd, bm, bl = (hexc(c) for c in m["blade"])
    md, mm, ml = (hexc(c) for c in m["metal"])
    grip = hexc(m["grip"])
    accent = hexc(m["accent"])
    rnd = random.Random(name)
    c = Canvas()

    if form == "shuriken":
        cx, cy = 16, 16
        for y in range(SIZE):
            for x in range(SIZE):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                r = math.hypot(dx, dy)
                a = math.atan2(dy, dx)
                star = 5 + 8.5 * (abs(math.cos(a * 2)) ** 3)
                if r <= star:
                    col = mix(bm, bl, 1 - r / 13) if (int(math.degrees(a) // 45) % 2 == 0) else mix(bd, bm, 1 - r / 13)
                    c.put(x, y, col, 1)
                if r <= 3.2:
                    c.put(x, y, mm if r > 1.8 else accent, 2)
        effects(c, m["fx"], rnd, bd, bm, bl, accent, lambda x, y: True)
        c.outline(lambda x, y: shade(bd, 0.45))
        return c.image()

    if form == "scythe":
        # A long shaft, and a curved blade sweeping back from its top towards the upper left.
        for y in range(SIZE):
            for x in range(SIZE):
                t, d = coords(x, y)
                if 0.02 <= t <= 0.92 and abs(d) <= 0.9:
                    c.put(x, y, mix(shade(mm, 0.55), shade(mm, 0.95), t) if int(t * L) % 4 else ml, 1)
        top = at(0.9, 0.6)
        curve = []
        for i in range(61):
            sv = i / 60
            px = top[0] - N[0] * sv * 15 - U[0] * (sv * sv) * 9
            py = top[1] - N[1] * sv * 15 - U[1] * (sv * sv) * 9
            curve.append((px, py, sv))
        blade = set()
        for y in range(SIZE):
            for x in range(SIZE):
                best = None
                for (cx, cy, sv) in curve:
                    dist = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                    if best is None or dist < best[0]:
                        best = (dist, sv, cx, cy)
                dist, sv, cx, cy = best
                width = 2.6 * (1 - sv) ** 0.8 + 0.35
                if dist <= width:
                    # Which side of the curve: towards the tip of the shaft (the cutting edge) is lit.
                    side = (x + 0.5 - cx) * U[0] + (y + 0.5 - cy) * U[1]
                    f = dist / width
                    col = mix(bm, bl, f) if side > 0 else mix(bm, bd, f * 0.8)
                    c.put(x, y, col, 2)
                    blade.add((x, y))
        for k in (-1, 0, 1):
            x, y = at(0.9, k * 0.9)
            c.put(int(x), int(y), accent if k == 0 else mm, 3)
        effects(c, m["fx"], rnd, bd, bm, bl, accent, lambda x, y: (x, y) in blade)
        c.outline(lambda x, y: shade(bd if (x, y) in blade else md, 0.45))
        return c.image()

    blade_cells = set()
    for y in range(SIZE):
        for x in range(SIZE):
            t, d = coords(x, y)
            prof = blade_profile(form, t)
            if prof is not None:
                half, off = prof
                dd = d - off
                if abs(dd) <= half:
                    blade_cells.add((x, y))
                    edge = abs(dd) / max(0.01, half)
                    base = mix(bm, bl, (t - 0.33) * 0.6)
                    if dd < 0 and edge > 0.45:
                        col = mix(base, bl, 0.75)       # the lit edge
                    elif dd > 0 and edge > 0.6:
                        col = mix(base, bd, 0.55)       # the shaded edge
                    elif abs(dd) < 0.45 and form not in ("rapier", "katana"):
                        col = mix(base, bd, 0.28)       # the fuller
                    else:
                        col = base
                    c.put(x, y, col, 1)
            # The grip and pommel.
            if 0.07 <= t <= 0.27 and abs(d) <= 1.05:
                band = int(t * L) % 3 == 0
                c.put(x, y, shade(grip, 1.35) if band else grip, 1)
            if t < 0.09 and math.hypot(*(lambda p: (x + 0.5 - p[0], y + 0.5 - p[1]))(at(0.04, 0))) <= 1.7:
                c.put(x, y, accent if t < 0.05 and abs(d) < 0.6 else mm, 2)
            # The guard.
            g = guard_shape(guard, t, d)
            if g is not None:
                c.put(x, y, mix(md, ml, g), 2)
    if guard in ("winged", "round", "curved", "cross", "disc"):
        gx, gy = at(0.3, 0)
        c.put(int(gx), int(gy), accent, 3)
    effects(c, m["fx"], rnd, bd, bm, bl, accent, lambda x, y: (x, y) in blade_cells)
    c.outline(lambda x, y: shade(bd if (x, y) in blade_cells else md, 0.45))
    return c.image()


def guard_shape(guard, t, d):
    if guard == "none":
        return None
    if guard == "cross" and 0.27 <= t <= 0.32 and abs(d) <= 4.2:
        return 1 - abs(d) / 4.2 * 0.6
    if guard == "round" and math.hypot((t - 0.3) * L, d) <= 2.3:
        return 0.8
    if guard == "disc" and 0.28 <= t <= 0.31 and abs(d) <= 2.4:
        return 0.5
    if guard == "curved":
        tt = 0.3 + (d / 5.0) ** 2 * 0.06
        if abs(t - tt) <= 0.025 and abs(d) <= 5.0:
            return 1 - abs(d) / 5.0 * 0.5
    if guard == "winged":
        tt = 0.29 - (abs(d) / 6.0) ** 1.5 * 0.08
        if abs(t - tt) <= 0.03 + 0.01 * (abs(d) / 6) and abs(d) <= 6.0:
            return 0.9 - abs(d) / 6.0 * 0.5
    return None


def effects(c, fx, rnd, bd, bm, bl, accent, on):
    cells = [p for p in list(c.px) if on(*p)]
    if not cells:
        return
    if fx in ("flame", "embers"):
        for (x, y) in cells:
            t, d = coords(x, y)
            if d < -0.3 and rnd.random() < (0.55 if fx == "flame" else 0.15):
                c.put(x, y, mix(accent, (255, 90, 20), rnd.random() * 0.6), 4)
    elif fx in ("frost", "stars", "glow"):
        for (x, y) in rnd.sample(cells, min(len(cells), 6 if fx != "glow" else 10)):
            c.put(x, y, accent if fx != "glow" else mix(bl, (255, 255, 255), 0.7), 4)
    elif fx == "crystal":
        for (x, y) in cells:
            if (x + y) % 4 == 0:
                c.put(x, y, mix(c.px[(x, y)][0], bl, 0.45), 4)
    elif fx == "runes":
        for (x, y) in cells:
            t, d = coords(x, y)
            if abs(d) < 0.5 and int(t * L) % 3 == 0:
                c.put(x, y, accent, 4)
    elif fx == "gem":
        pass
    elif fx == "drip":
        for (x, y) in rnd.sample(cells, min(len(cells), 7)):
            c.put(x, y, accent, 4)
            c.put(x, y + 1, mix(accent, bd, 0.3), 4)
    elif fx == "petals":
        for (x, y) in rnd.sample(cells, min(len(cells), 6)):
            c.put(x, y, accent, 4)
    elif fx in ("patina", "cracks", "smoke"):
        for (x, y) in cells:
            if rnd.random() < 0.18:
                c.put(x, y, accent if fx != "smoke" else mix(bd, bm, 0.5), 4)
    elif fx == "neon":
        for (x, y) in cells:
            t, d = coords(x, y)
            if abs(d) > 0.6 and abs(d) < 1.4:
                c.put(x, y, accent, 4)
    elif fx == "pulse":
        for (x, y) in cells:
            t, d = coords(x, y)
            if int(t * L) % 4 == 0:
                c.put(x, y, accent, 4)
    elif fx == "bolt":
        for (x, y) in cells:
            t, d = coords(x, y)
            if abs(d - math.sin(t * 40) * 0.8) < 0.45:
                c.put(x, y, accent, 4)
    elif fx == "waves":
        for (x, y) in cells:
            t, d = coords(x, y)
            if abs(d - math.sin(t * 30) * 0.9) < 0.35:
                c.put(x, y, accent, 4)


def draw_pickaxe(name, form, mat):
    m = MATERIALS[mat]
    bd, bm, bl = (hexc(c) for c in m["blade"])
    md, mm, ml = (hexc(c) for c in m["metal"])
    grip = hexc(m["grip"])
    accent = hexc(m["accent"])
    rnd = random.Random(name)
    c = Canvas()
    head = set()
    for y in range(SIZE):
        for x in range(SIZE):
            t, d = coords(x, y)
            if 0.03 <= t <= 0.84 and abs(d) <= 0.95:
                handle = mix(grip, md, 0.55)
                c.put(x, y, mix(shade(handle, 0.9), shade(handle, 1.4), t) if int(t * L) % 4 else mm, 1)
            # The head: an arc across the top of the handle, curving back at the points.
            span = {"classic": 11.5, "heavy": 10.5, "spike": 12.5, "crescent": 12.0}[form]
            thick = {"classic": 1.9, "heavy": 2.6, "spike": 1.6, "crescent": 2.0}[form]
            bend = {"classic": 0.11, "heavy": 0.08, "spike": 0.06, "crescent": 0.17}[form]
            centre = 0.84 - (d / span) ** 2 * bend * (1.0 if abs(d) <= span else 0)
            half = thick * (1 - (abs(d) / span) ** 2 * 0.65) + 0.15
            if abs(d) <= span and abs((t - centre) * L) <= half:
                head.add((x, y))
                k = (t - centre) * L / half
                col = mix(bm, bl, 0.5 - k * 0.5) if k < 0 else mix(bm, bd, k * 0.7)
                c.put(x, y, col, 2)
    for k in (-1, 0, 1):
        x, y = at(0.84, k * 0.9)
        c.put(int(x), int(y), mm if k else accent, 3)
    effects(c, m["fx"], rnd, bd, bm, bl, accent, lambda x, y: (x, y) in head)
    c.outline(lambda x, y: shade(bd if (x, y) in head else md, 0.45))
    return c.image()


def draw_shovel(name, form, mat):
    m = MATERIALS[mat]
    bd, bm, bl = (hexc(c) for c in m["blade"])
    md, mm, ml = (hexc(c) for c in m["metal"])
    grip = hexc(m["grip"])
    accent = hexc(m["accent"])
    rnd = random.Random(name)
    c = Canvas()
    head = set()
    for y in range(SIZE):
        for x in range(SIZE):
            t, d = coords(x, y)
            if 0.03 <= t <= 0.66 and abs(d) <= 0.95:
                handle = mix(grip, md, 0.55)
                c.put(x, y, mix(shade(handle, 0.9), shade(handle, 1.4), t) if int(t * L) % 4 else mm, 1)
            if 0.02 <= t <= 0.08 and abs(d) <= 2.2:
                c.put(x, y, mm, 2)
            # The blade: a spade from t = 0.62 to the tip.
            if 0.6 <= t <= 1.0:
                u = (t - 0.6) / 0.4
                w = {"spade": 3.6, "round": 3.9, "pointed": 3.5, "broad": 4.6}[form]
                if form == "round":
                    half = w * math.sqrt(max(0.0, 1 - (max(0, u - 0.45) / 0.55) ** 2))
                elif form == "pointed":
                    half = w * (1 - max(0, u - 0.4) / 0.6) + 0.2
                elif form == "broad":
                    half = w * (0.85 + 0.15 * u) if u < 0.9 else w * (1 - (u - 0.9) / 0.1 * 0.5)
                else:
                    half = w * math.sqrt(max(0.0, 1 - (max(0, u - 0.55) / 0.45) ** 2)) * (0.9 + 0.1 * (1 - u))
                if u < 0.12:
                    half = min(half, 1.3 + u * 18)
                if abs(d) <= half:
                    head.add((x, y))
                    e = abs(d) / max(0.01, half)
                    col = mix(bm, bl, 0.7) if d < 0 and e > 0.5 else (mix(bm, bd, 0.5) if d > 0 and e > 0.6 else bm)
                    if abs(d) < 0.5 and u < 0.7:
                        col = mix(col, bd, 0.3)
                    c.put(x, y, col, 2)
    x, y = at(0.62, 0)
    c.put(int(x), int(y), accent, 3)
    effects(c, m["fx"], rnd, bd, bm, bl, accent, lambda x, y: (x, y) in head)
    c.outline(lambda x, y: shade(bd if (x, y) in head else md, 0.45))
    return c.image()


def draw_trident(name, form, mat):
    m = MATERIALS[mat]
    bd, bm, bl = (hexc(c) for c in m["blade"])
    md, mm, ml = (hexc(c) for c in m["metal"])
    grip = hexc(m["grip"])
    accent = hexc(m["accent"])
    rnd = random.Random(name + " trident")
    c = Canvas()
    head = set()
    spread = {"classic": 3.9, "barbed": 3.7, "crescent": 3.4, "royal": 4.1}[form]
    base = 0.55  # where the head starts along the shaft

    def prong(x, y, t, d, centre, start, end, width):
        if not start <= t <= end:
            return False
        u = (t - start) / (end - start)
        half = width if u < 0.65 else width * (1 - (u - 0.65) / 0.35) + 0.12
        if abs(d - centre) > half:
            return False
        head.add((x, y))
        e = (d - centre) / max(0.01, half)
        col = mix(bm, bl, 0.75) if e < -0.2 else (mix(bm, bd, 0.55) if e > 0.4 else bm)
        if u > 0.85:
            col = mix(col, bl, 0.6)
        c.put(x, y, col, 2)
        return True

    for y in range(SIZE):
        for x in range(SIZE):
            t, d = coords(x, y)
            # The shaft, banded every few pixels, with a wrapped grip low down.
            if 0.02 <= t <= base + 0.04 and abs(d) <= 0.8:
                handle = mix(grip, md, 0.55)
                c.put(x, y, mix(shade(handle, 0.9), shade(handle, 1.4), t) if int(t * L) % 5 else mm, 1)
            if 0.14 <= t <= 0.26 and abs(d) <= 1.05:
                c.put(x, y, grip if int(t * L) % 2 else shade(grip, 1.5), 1)
            if t < 0.05 and abs(d) <= 1.3:
                c.put(x, y, mm, 2)
            # The crossbar the prongs grow from.
            if base <= t <= base + 0.06 and abs(d) <= spread + 0.8:
                head.add((x, y))
                c.put(x, y, mix(md, ml, 0.5 - d / (2 * (spread + 0.8))), 2)
            # The middle prong, longest, to the tip.
            prong(x, y, t, d, 0.0, base + 0.04, 1.0, 0.95)
            # The side prongs: straight, hooked outward (barbed), bowed out (crescent) or tall (royal).
            for side in (-1, 1):
                start, end = base + 0.04, {"classic": 0.88, "barbed": 0.86, "crescent": 0.88, "royal": 0.92}[form]
                u = max(0.0, min(1.0, (t - start) / (end - start)))
                centre = side * spread
                if form == "crescent":
                    centre = side * (spread + math.sin(u * math.pi) * 1.1)
                prong(x, y, t, d, centre, start, end, 0.8)
                if form == "barbed" and start + 0.1 <= t <= start + 0.14 and 0 < side * d - spread <= 1.9:
                    head.add((x, y))
                    c.put(x, y, mix(bm, bd, 0.3), 2)
    # A gem or a bright stud where the prongs meet.
    x, y = at(base + 0.03, 0)
    c.put(int(x), int(y), accent, 3)
    if form == "royal":
        for k in (-1, 1):
            x, y = at(base + 0.03, k * (spread + 0.6))
            c.put(int(x), int(y), accent, 3)
    effects(c, m["fx"], rnd, bd, bm, bl, accent, lambda x, y: (x, y) in head)
    c.outline(lambda x, y: shade(bd if (x, y) in head else md, 0.45))
    return c.image()


def write(kind, name, image, catalogue):
    ident = kind + "_" + slug(name)
    tex = os.path.join(ROOT, "textures", "item", "cosmetic", ident + ".png")
    os.makedirs(os.path.dirname(tex), exist_ok=True)
    image.save(tex)
    model = os.path.join(ROOT, "models", "item", "cosmetic", ident + ".json")
    os.makedirs(os.path.dirname(model), exist_ok=True)
    with open(model, "w") as f:
        json.dump({"parent": "minecraft:item/handheld", "textures": {"layer0": "maro:item/cosmetic/" + ident}}, f, indent=2)
        f.write("\n")
    item = os.path.join(ROOT, "items", "cosmetic", ident + ".json")
    os.makedirs(os.path.dirname(item), exist_ok=True)
    with open(item, "w") as f:
        json.dump({"model": {"type": "minecraft:model", "model": "maro:item/cosmetic/" + ident}}, f, indent=2)
        f.write("\n")
    catalogue.append({"kind": kind, "id": ident, "name": name})


def main():
    catalogue = []
    for name, form, guard, mat in SWORDS:
        write("sword", name, draw_sword(name, form, guard, mat), catalogue)
    for i, base in enumerate(TOOLS):
        write("pickaxe", base + " Pickaxe", draw_pickaxe(base, PICK_FORMS[i % len(PICK_FORMS)], TOOL_MATERIAL[base]), catalogue)
    for i, base in enumerate(TOOLS):
        write("shovel", base + " Shovel", draw_shovel(base, SHOVEL_FORMS[i % len(SHOVEL_FORMS)], TOOL_MATERIAL[base]), catalogue)
    for i, base in enumerate(TOOLS):
        write("trident", base + " Trident", draw_trident(base, TRIDENT_FORMS[i % len(TRIDENT_FORMS)], TOOL_MATERIAL[base]), catalogue)
    with open(os.path.join(ROOT, "cosmetics.json"), "w") as f:
        json.dump(catalogue, f, indent=1)
        f.write("\n")
    # A contact sheet to look the set over.
    sheet = Image.new("RGBA", (SIZE * 10 * 4, SIZE * 10 * 4), (24, 26, 34, 255))
    for i, entry in enumerate(catalogue):
        img = Image.open(os.path.join(ROOT, "textures", "item", "cosmetic", entry["id"] + ".png")).resize((SIZE * 4, SIZE * 4), Image.NEAREST)
        sheet.alpha_composite(img, ((i % 10) * SIZE * 4, (i // 10) * SIZE * 4))
    out = os.environ.get("COSMETIC_SHEET")
    if out:
        sheet.save(out)
    print(len(catalogue), "cosmetics written")


if __name__ == "__main__":
    main()
