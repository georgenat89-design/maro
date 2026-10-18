# Phone song-skip response

Next/previous song controls start the opposite-hand gesture immediately, before media metadata or artwork arrives. A 220 ms button pulse acknowledges the input. The swipe lasts 520 ms, with early contact and a faster artwork stroke. The transport debounce is 90 ms; held keyboard shortcuts remain repeat-safe.

Metadata changes outside the phone also trigger the hand. Local skip confirmation does not replay the same gesture. The outgoing album stays visible while the next image loads; a late image slides in without restarting the hand or showing a blank frame. Phone and iPad, both hand placements, and previous/next direction use the same motion clock.

Native isolated-playback checks verify immediate reaction with delayed artwork, no duplicate hand on late image upload, native shortcuts, held-key suppression, volume, movement, focus cleanup and texture disposal. Spotify/Windows determines when playback itself changes; the UI acknowledges input immediately without pretending the media service has already accepted it.

## Game controls while the phone is open

**Game Actions** replaces the old opt-in Interact With Game setting and is enabled by default, including when upgrading an old profile. With Mouse Look active, left mouse attacks or mines continuously and right mouse uses the held item through the ordinary Minecraft interaction path. This includes launching fireworks and offhand Elytra boosts. The real camera raycast follows your aim down the shaft as each block breaks. Vanilla reach, mining speed, item cooldowns and server validation still apply.

Opening the phone captures already-held attack/use bindings before Minecraft clears them for a screen. Held mining and repeated item use continue until release. Hold Alt or press the Interact key to operate apps instead. Typing, loss of focus, turning Game Actions off, closing the phone and leaving the world release game inputs; returning to the game requires a fresh press.

`gradlew.bat build runProductionClientGameTest -PphoneActionsTestOnly=true` runs the existing phone/mouse/volume/artwork checks plus native mouse callbacks with real server-confirmed fireworks, offhand Elytra rocket consumption, successive survival mining directly beneath the player, pre-open held mining, and release/Alt/focus/disable/close cleanup. Playback remains isolated from the user's music.

The Spotify header now has a separate title row below Interact. Album artwork uses a
fixed rounded 148-unit square at (30,100), leaving breathing room around the title and
close control. Incoming/outgoing covers slide beneath that same clipped viewport, and
the opposite-hand gesture reaches its center. Existing transport, seek, volume and size
control positions are retained for both device layouts.

Local H.264 decoding converts every YUV frame into reusable BGR storage before copying it
into the reusable image. Cropped dimensions remove codec padding. This fixes the previous
first-frame-only playback caused by passing raw YUV planes into the BGR copy overload.
Regression fixtures compare successive decoded-color buffers to JCodec reference pixels
and drive real MP4 play, pause, resume, next and linked-file playback on Phone and iPad.

The Home app grid now derives card height from the device's available height. Phone and
iPad keep a 12-unit row gap and at least 14 units between the second row and footer;
labels and clickable regions use the same card bounds. Native tests verify all six home
controls are separate at GUI scales 2/4 and device sizes .55/1/1.45, plus actual app clicks.

Held Phone/iPad arms and the Laptop/iPad Click GUI hands use a one-framebuffer-pixel
coverage edge. Its width follows the actual GUI scale and device rotation, smoothing
the diagonal silhouette without blurring skin pixels or changing the skin sampler.
Texture coordinates stay within each arm face to prevent sleeve/atlas bleed. Both
classic and slim skins retain their correct arm widths, and Click GUI fades apply
to the hands as well as the device.
