# Dropped-item Nametags

In **Visuals → Nametags → Dropped Items**, enable **Item Tags**, then open **Choose Items → Pick Items**. Search by translated item name or registry ID and click any number of item cards. Selected cards have an accent border and check mark. **Selected only** reviews the selection, **Select results** adds the current search results, and **Clear selection** removes all choices. An empty selection labels nothing.

**All Blocks**, beside Done, replaces the selection with every registered block item in one click. It clears the search and shows the selected blocks immediately. Non-block items such as diamonds, tools and totems are left out. Individual block choices can still be toggled afterward, and Done or Escape saves the full selection.

Changes take effect live. Done or Escape returns to the previous screen and saves the chosen registry IDs in the current Maro config. The initial selection contains Totem of Undying, Diamond, Netherite Ingot and Elytra; Item Tags starts off so existing player tags are unchanged. Unknown or removed item IDs are ignored when loading older configurations.

Only selected dropped item entities receive labels. Each label shows an item icon, its current name, and stack count when greater than one. **Item Distance** independently toggles the live distance in metres; it starts on and saves with the current config. Player tags retain their separate **Distance** setting. **Item Range** controls how far away dropped-item labels can appear and defaults to 64 blocks. The existing font, text colour, scale, constant-size and background controls also style item tags. Player equipment and held-item captions keep their existing behavior.

The renderer uses received client entities, interpolated positions and the current camera projection. It displays the nearest 128 selected stacks at most in a dense pile. Picker search is cached per edit; only the visible page builds widgets or renders icons. Neither opening the picker nor rendering labels picks up, equips or changes any item.

Native Minecraft 1.21.11 checks exercise name/ID search, multi-select clicks, selected-only filtering, scrolling, disk save, malformed IDs, real dropped-stack labels/counts/distance, live selection edits, range and the off switch.
