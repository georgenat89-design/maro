Armor HUD uses one equipment card with native item icons, readable percentages and
thin durability meters. Main-hand and offhand items have extra spacing from armor.
Column layout places slot names beside the icons. Low condition uses a restrained
red highlight; its threshold is adjustable. Native item rendering preserves glint.

Durability meters ease between damage updates and reset immediately when a different
item is equipped. Zero durability has no colored fill. Counts use a separate badge;
non-damageable items never display a durability percentage. Empty slots can be shown
as muted silhouettes or hidden. Panel and Background Opacity control the backdrop.

The card sits beside the hotbar when it fits, otherwise above the health area. It fits
the available viewport at large GUI scales without overwriting the saved Scale value.
Position opens the existing drag/resize editor, and manual placement stays on screen.
The existing setting names and values remain compatible with saved configs.

Nametags, the equipment row above them, and held-item captions share the active
Custom Model's visual height. Changing Model Size updates the complete label stack
immediately, for your own third-person tag and selected other players. Players with
no active replacement retain the vanilla height policy. The vanilla name label also
uses the replacement's height while preserving its original clearance.
