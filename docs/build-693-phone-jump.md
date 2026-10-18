# Build 693, phone polish and Jump Circles

The exact build 693 tag is `944cece024d9a2203cef7301833ab618b16ce2a7`.
It is merged in `a75697d`, preserving the existing Texture Studio, physical
Keyboard Strokes chooser, Acrobatics fixes, custom models, phone apps, Fake Pay
and Screen GUI. The merge includes build 693's Rubik/Inter choice, quieter menu
appearance, scroll-only settings wheel and world-rendered floating window.

## Phone controls and artwork

The Interact control is now a centered pill with an app icon, hover easing and
the configured shortcut. **I** opens apps/unlocks controls; pressing it again
resumes mouse look. **Alt** temporarily releases the mouse for controls.

Playback shortcuts work while the phone is open and respect typing and
movement bindings. A held shortcut dispatches once until released. Defaults:

| Action | Key |
| --- | --- |
| Previous / next | Left / Right |
| Play / pause | P; Ctrl+Space also works |
| Output volume up / down | Page Up / Page Down |
| Mute / restore | M |

All are configurable in Spotify Phone's module settings. Mouse shortcuts work
while mouse look is active. Media commands use the shared Windows bridge.

Album art uses a worker-prepared, antialiased rounded mask and a plain textured
quad, replacing the old corner fan. Center cropping preserves its proportions.
Stale artwork jobs are discarded, uploads happen once per image, and textures
are freed after transitions and on close. A confirmed song change animates the
opposite skin hand and slides the artwork. Both holding sides work; **Show Hand**
and **Swipe Animation** control the hand effect. Phone text accounts for the
actual phone scale when choosing its font rasterization size.

## Videos

Videos opens TikTok directly. The old small topic buttons are replaced by
**Feed**, offering For You, Entertainment and Gaming with short descriptions.
One overflow menu holds reload, external browser, local files and website view.
The viewing surface is larger and retains the source video's aspect ratio.

The installed Edge/Chrome instance uses a separate phone profile, a mobile
viewport, bounded asynchronous frames and an explicit loading/retry surface.
Navigation failures are reported. A video focus view crops the captured page
to its visible player without altering TikTok's DOM, media URLs or playback.
Pointer input maps back to the captured region. Visible sign-in dialogs restore
the full page, and **Show full website** allows ordinary page browsing. Website
sign-in, connectivity and site availability still govern the feed; personalized
recommendations require the user's own TikTok session.

Local files are an explicit secondary choice. The Orbit and Neon demos are no
longer bundled in production or inserted into the library. The local player
uses a larger surface, tap-to-pause, a compact library bar and an overflow menu
for sound and switching feeds. H.264 MP4 files up to 720p/64 MB and saved direct
links remain supported. The Neon sample is only a native test fixture.

## Interact with the game

**Interact With Game** is off by default and is available in the module and the
phone's Settings app. While mouse look is active it forwards manual attack,
mining, item use and hotbar selection through vanilla client methods. Alt,
typing, app interaction, focus loss, disabling the option and closing the phone
release held actions. The vanilla menu attack lock is lifted only for these
opted-in phone actions; game attack timing and server rules still apply.

## Jump Circles and removals

The previous Jump Circle effect is replaced by an adaptation of Anubis's
JumpCirclesModule: cached 64-segment rings, a 24-ring circular buffer, cubic
opening, an 80 ms fade-in, fading feathered bands and facing-aligned logos.
Local jumps use the native jump event; remote jumps use Anubis's rise tracking.
Rings anchor just above the ground, use the scene depth test and cull at 128 m.

The logo is Maro's existing M asset, with a small filtered glow pass. Anubis's
logo and executable framework are not included. Color/radius/lifetime, logo
toggles, jump/landing triggers, custom module name and Spotify beat response
remain available. GPL attribution and corresponding source are retained.

Hand Shader is removed from registration, source, mixins, shader resources and
test paths. Existing saved entries for that removed module are simply ignored.

## Performance and validation

RGBA uploads now use a size-checked bulk copy into the owned NativeImage rather
than a Java call for every pixel. Browser and MP4 workers perform channel
conversion before publishing a frame. The browser keeps the latest pending
frame and caps decoding at 30 fps; GPU surfaces are reused until dimensions
change. Album preparation stays off the render thread. Block ESP render lists
are cached until registry size changes, and keyboard selection uses cached
physical codes and direct keybind access.

A native 80 × 512² image-copy workload observed 73–94% less copy time across
runs (24.8 ms to 1.56 ms in one run; 22.8 ms to 6.11 ms in another).
This is a copy microbenchmark, not an FPS claim.
Pixel checks compare channels with the old path and reject incomplete buffers.

Production Minecraft 1.21.11 tests run with Java 21, Fabric and Meteor 1.21.11-85:

```powershell
./gradlew.bat build runProductionClientGameTest -PspotifyPhoneTestOnly=true -PphoneTikTokTestOnly=true -PliveTikTokTest=true '-PmeteorTestJar=C:/Users/Nate/Downloads/meteor-client-1.21.11-85.jar'
./gradlew.bat runProductionClientGameTest -PjumpCircleTestOnly=true '-PmeteorTestJar=C:/Users/Nate/Downloads/meteor-client-1.21.11-85.jar'
./gradlew.bat runProductionClientGameTest -PkeyboardStrokesTestOnly=true '-PmeteorTestJar=C:/Users/Nate/Downloads/meteor-client-1.21.11-85.jar'
./gradlew.bat runProductionClientGameTest -PspatialGuiTestOnly=true '-PmeteorTestJar=C:/Users/Nate/Downloads/meteor-client-1.21.11-85.jar'
```

Tests cover native rendering and texture pixels, held input and hotkey rebinding,
both hand/device layouts, real vanilla placement/mining with the phone open,
focus/Alt cleanup, phone apps and saved playlists, MP4 play/pause/next/download,
browser video/input/cropped-player restoration, native jump/landing, logo modes,
Spotify onset/pause/session cleanup, bounded ring expiry, keyboard placement
and build 693's floating-window lifecycle. Live TikTok is captured separately
from the deterministic local browser fixture; personal sign-in is not tested.

Results on 2026-10-12: the full phone/app/MP4/browser suite passed in 1m10s.
The final focused browser suite, including cropped video pointer mapping,
full-page restoration and a live TikTok capture, passed in 45s. The Anubis/Maro
jump-circle suite passed in 26s. Final keyboard, Screen GUI and Acrobatics
checks passed in 52s; the build 693 floating-window checks passed in 48s.
Screenshots of the artwork, both swipe hands, live video and Maro logo ring
were visually reviewed. Personal account sign-in and personalized FYP behavior
were not exercised.
