# Streamproof

Streamproof keeps Maro's private GUI and annotations on the same monitor and in the same
Minecraft instance. The normal Minecraft native window presents the clean image. A separate
owned, click-through native tool window presents your local image over the same client area.
That tool window is capture-excluded; the main game window is **never** affinity-excluded.

## Before sharing

1. Use Windows 10 version 2004 / build 19041 or newer, or Windows 11, with Desktop Window
   Manager composition available. Use windowed or a separately configured borderless window.
2. Enable **Misc → Streamproof before starting a screen share or recording**. Wait for the
   private-layer status and any texture-restoration work to finish.
3. Select the normal Minecraft window, or the intended display, in your capture application.
4. Inspect that application's actual preview. Confirm normal moving gameplay remains visible
   and Maro menus, HUD and ESP are absent. Repeat after changing capture method or application.
5. Disable Streamproof when finished. It does not automatically enable from saved configuration.

F11/exclusive fullscreen is unsupported: a fullscreen request while Streamproof is enabled
returns Minecraft to windowed mode. Streamproof does not create a borderless fullscreen mode.
The local layer follows the game's native client rectangle as it moves and resizes; it hides
when Minecraft loses focus, is minimized or is hidden.

## Rendering and scene effects

There is one world render per frame, followed by two GUI passes. The ordinary HUD and eligible
vanilla screens feed the clean image. Maro's GUI, imported backgrounds, model-preview panels,
notifications and HUD render into the local image. ESP/tracers use a separate annotations target
with the existing world matrices and depth, then composite onto the local image. The terrain is
not rendered a second time. The native contexts share GPU textures; no CPU video readback is used
by normal gameplay. GPU fences order the producer, private presentation and next game frame.

Some effects change the shared scene itself rather than adding an annotation. Preserving two
different versions of that scene would require another world render. Streamproof therefore
temporarily pauses incompatible effects, including Freecam/Free Look/zoom, custom player and
item models, Cosmetics, Emotes, sky/lighting/world changes, texture-replacement modules, Fake
Player, pets and scene name replacements. These effects are paused **locally as well as in the
capture**. This implementation does not claim to keep every scene-changing effect visible
locally while presenting its vanilla equivalent elsewhere.

The policy records each affected module's requested enabled state. Toggles and saved
configuration update that requested state while the effect is paused. Disabling Streamproof
restores only the effects still requested. Other modules retain their normal ticks, keybinds and
lifecycle; render suppression is scoped to the render thread and does not change what background
logic sees. Asynchronous atlas restoration must settle before private presentation is ready.
While ordinary textures and chunk meshes are being restored, the clean view uses a brief vanilla
Minecraft loading screen so stale modified scene data is not presented as clean gameplay. Enable
sharing only after the status is ready, ordinary gameplay has resumed and the recorder's preview
has been checked.

Maro menus are omitted from the clean GUI. Ordinary screens, such as the inventory, remain
available in the capture. Main-menu and loading branding, custom widget fonts and the floating
GUI's crosshair suppression have clean-pass guards. Chat, the chat screen, action-bar messages
and toasts are always private in Streamproof. This includes client-command output; there is no
capture setting that makes chat public.

If Windows cannot create or verify the private layer, or presentation fails, Streamproof keeps
client visuals hidden rather than displaying an unprotected local image. Ordinary gameplay
remains the intended public output. The status reports that the private layer is unavailable;
this is not a successful private-presentation state. Do not start sharing until the actual
capture preview has been checked. A failed private surface closes Maro's private GUI so invisible
controls cannot keep consuming gameplay input. The ordinary main framebuffer continues rendering
new world frames rather than presenting a frozen capture texture. Disabling and re-enabling
Streamproof releases the failed resources and attempts to build a new verified private layer.

## Capture scope and limits

The private layer uses `WDA_EXCLUDEFROMCAPTURE` (`0x11`) and verifies its affinity, process-owned
parent, tool-window/no-activation styles and mouse passthrough before showing it. The OS/runtime
check requires build 19041+ because earlier Windows versions interpret that affinity differently.
Microsoft limits this mechanism to supported OS capture paths with DWM composition and does not
provide a universal recording guarantee. See
[SetWindowDisplayAffinity](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setwindowdisplayaffinity).

Game/window capture that reads the final main-window image should receive clean pixels. A hook
that reads Maro's private framebuffer, the private tool window, or another point in the rendering
pipeline is outside that guarantee. OBS, Discord, Windows Graphics Capture and DXGI Desktop
Duplication must each be tested with the exact selected source and capture method; an OS affinity
check or a framebuffer screenshot alone does not verify them. Cameras pointed at the monitor,
remote display/driver behavior and independently drawn overlays are not protected by this design.

Gameplay actions remain visible: movement, mining, placed blocks, attacks, inventory changes,
server messages and their consequences are not erased. External mods and user-installed resource
packs are not automatically converted to vanilla by Maro's module-render guards. Hiding an overlay
does not make those actions or third-party modifications invisible.

## Verification

Use Java 21 and the normal Fabric dependencies:

```powershell
.\gradlew.bat remapJar remapGametestJar
.\gradlew.bat runProductionClientGameTest -PstreamproofTestOnly=true -PskipNativeCapture=true -PcaptureReview=false
.\gradlew.bat runProductionClientGameTest -PplayerPreviewTestOnly=true
.\gradlew.bat runProductionClientGameTest -PguiBackgroundTestOnly=true
```

The native Streamproof suite places a magenta private HUD and real through-wall ESP in a
disposable local world. It checks nonblack clean gameplay, local/private separation, native GL
front buffers, one-world-render counters, module state, window geometry, GUI scales, reloads,
fullscreen-request fallback and resource cleanup. A bounded injected surface failure checks
that the helper hides, private GUI input releases, live ordinary terrain changes remain visible
without private markers, requested state is retained and disable/re-enable recovers. The injected
state transition generates no synthetic production error log. With OS capture explicitly enabled,
the suite can also capture Windows' desktop screen DC
with `BitBlt(SRCCOPY | CAPTUREBLT)` and the main HWND with
`PrintWindow(PW_CLIENTONLY | PW_RENDERFULLCONTENT)`. Only the game's client rectangle is copied
and saved. GDI handles/DCs are released after each test. A native result must resemble the clean
terrain and contain no magenta probe; black output is never accepted as usable gameplay.

Internal GPU evidence and the method report are written under `run/screenshots/`. The completed
functional run used `skipNativeCapture=true`: OS display/window capture was stopped by the user
and is **not verified**. Its report, `streamproof-capture-methods.txt`, records that limitation.
When OS capture is enabled, cropped PNG evidence is saved for those APIs as well.
`PrintWindow` failure/black output is recorded as unsupported on that driver/API. Java Robot was
attempted in the headless gametest runtime and is not usable there; the desktop screen-DC test
provides the actual native display-capture check instead. These API tests do not verify a
particular sharing application. Desktop capture is not attempted if the test's own main window
is not foreground; this records an occluded/unavailable result, rather than misidentifying
another application's pixels as Minecraft or claiming a privacy pass. Other functional checks
may still complete in that case. An actual foreground result must contain usable clean gameplay.

| Method or transition | Verification status for this update |
| --- | --- |
| Clean GPU target versus local target | Passed: private HUD, through-wall Block ESP, bloom, tracers and Player ESP separation |
| Actual main/layer GL front buffers | Passed internal native readback; this does not verify a capture application |
| One world render per frame | Passed: 59 world renders for 59 observed frames |
| Native desktop `BitBlt`, game-client crop | Not tested; OS capture stopped by user |
| Main HWND `PrintWindow` | Not tested; OS capture stopped by user |
| GUI scales 2/3/4, resize, movement, hide/show, reload | Passed completed functional suite |
| Private-surface failure, live ordinary fallback and rebuild | Passed: 13,064 terrain samples changed while ordinary output remained live; private markers absent; rebuild recovered |
| Paused scene policy and shutdown | Passed: real Fake Block mesh replacement cleared, rebuild readiness and requested-state restoration verified; shutdown did not reactivate effects |
| Exclusive fullscreen request | Unsupported mode; safe return to windowed passed |
| OBS game/window/display capture | Not tested |
| Discord screen/window sharing | Not tested |
| Windows Graphics Capture / DXGI Desktop Duplication | Not tested |
| Java Robot | Unavailable in headless native test runtime |

These are results from the completed functional native suite. They preserve the distinction
between internal GPU/presentation checks, OS capture APIs and application-level sharing tests.
