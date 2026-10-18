# Streamproof

1. Join a world and enable **Misc → Streamproof** on Windows.
2. Select your normal **Minecraft** window in your recording or screen-share application.
3. Check that application's preview before going live.

The client keeps one game instance, world and player. Minecraft's main native window presents
clean gameplay. An attached, click-through private render layer covers the same game area with
your local visuals. It has no taskbar or Alt-Tab entry, never takes focus, follows window movement
and resizing, and hides when Minecraft loses focus or is minimized. You play on your normal
screen without choosing a second capture window.

The private layer uses verified Windows capture exclusion before it becomes visible. Unsupported
exclusion stops Streamproof rather than showing an unprotected private layer. The layer is a
native owned tool window; it is not another Minecraft instance. It renders at game FPS while the
clean world render follows **Capture FPS** (15–60, default 30). The extra world render costs GPU
time; textures are shared directly without CPU video copies.

Private presentation now synchronizes GPU work in both directions, so the next game frame
cannot overwrite the shared image while the private layer is still reading it. The output
uses an exact pixel blit. Movement and resizing follow the native client rectangle and its
screen origin, without repeatedly changing the layer's stacking order.
Visibility is checked from Minecraft's main render loop even when the world renderer
is skipped, preventing a stale private image from remaining after the game is hidden.
These use [OpenGL sync objects](https://wikis.khronos.org/opengl/Sync_Object) and Windows'
[client rectangle](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-getclientrect)
and [screen conversion](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-clienttoscreen).

Maro's HUD, ESP, tracers, custom item models and menus are suppressed during the clean render.
Module enabled states, keybinds, tick logic and lifecycle callbacks stay unchanged. Maro menus
remain private; vanilla screens such as inventory appear in the public view. **Hide Chat** is on
by default. Name Protect remains active in the clean view. Disabling releases the layer and GPU
resources; Streamproof never reactivates automatically from a saved config.

Windows exclusion does not control every capture method. Graphics hooks that record the private
offscreen framebuffer can include private effects. See Microsoft's
[SetWindowDisplayAffinity documentation](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setwindowdisplayaffinity)
for OS capture limits. Check OBS, Discord or your recorder's actual preview. Those applications
are not exercised by the native tests.

Player actions, installed resource packs and overlays drawn independently by other mods remain
outside Maro's render suppression. Native production tests with Meteor check nonblack clean GPU
output, private HUD and through-wall ESP separation, actual main/layer front buffers, native
owner/style/affinity, mouse passthrough, menu restoration, vanilla inventory, module state,
background threads, frame limiting, resize and cleanup.
