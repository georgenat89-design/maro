# Main-menu scene

Use **Scene** at the bottom right of the main menu.

- **Landscape / Galaxy** switches between the river landscape and a floating Minecraft island surrounded by a nebula, stars and occasional meteors.
- **Time of day** changes scene lighting. **Auto** follows the computer's local time.
- **Trees** changes the trees on either scene.
- **Camera** offers Orbit, Drift and Paused. **Options** opens speed, zoom, view angle and direction controls. Click a value to reset it, or use **Reset camera** to restore all four camera defaults. Zoom and angle ease into the new view.
- **Transparent**, beside Scene, lets the scene show through the home panel, world/server pages and navigation buttons. Text stays opaque. Set **Main Menu Opacity** under Settings > Main Menu Scene to adjust its strength.

Scene controls use the main menu's blue palette, including the panel, selected icons, background tabs and orbit options. Hover tips use a padded, rounded blue box and remain inside the screen. Click GUI accent presets do not recolour the main-menu Scene controls. The main-menu/loading monogram keeps its original blue; the Click GUI monogram and glow follow the theme.

Preferences are stored in the normal `maro/client.json`. The scene uses its own bounded geometry and runtime Minecraft block textures; it never creates a gameplay world or changes the player's camera. Loading stays lightweight before resources are available.

The home screen presents a centered Maro mark and blue glass buttons over the scene. Singleplayer, Multiplayer and Realms form the main stack; Options and Quit share the lower row, with native Language and Accessibility buttons beside them. Accounts remains at the upper right. The welcome card and duplicate action shortcuts have been removed. Opening a native menu retains the compact navigation sidebar and the existing world/server view; Back/Escape returns to the centered home.

Main-menu typography uses Inter independently of the saved Click GUI/HUD font. The home and browsing sidebar use the original blue contour M from `assets/maro/textures/gui/logo.png`, preserving its transparency, detail and subtle hover response. The footer community button opens https://discord.gg/navo in the default browser; it supports mouse, keyboard focus, Enter and Space, with a visible focus outline and narrated action.

The main menu wordmark is MARO.GG in a smaller bold style. The Discord button centers its icon, invite label, and arrow as one group.
