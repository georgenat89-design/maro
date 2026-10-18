# TikTok scrolling

Wheel notches no longer disappear during a cooldown. Fractional input accumulates to one step;
invalid input is ignored and a single unusually large callback remains bounded to one step.
The browser retains up to 16 pending steps, runs one gesture at a time and replaces queued
forward movement when the user reverses direction. Feed changes, reload, resize and close
cancel pending gestures, including delayed callbacks from the old page.

Video feeds use the web player's native wheel navigation and browser snap animation, with
requests serialized through each transition. No synthetic touch drag remains held between
videos. The video crop stays fixed during the transition instead of briefly
switching to the full website, including a brief grace period while the next video buffers.
Only visible dialogs interrupt video focus. Browser JPEG capture
quality is 94 and the decoder's ceiling is 60 frames per second; actual delivery depends on the
browser and machine. The existing physical-pixel budget, single decoder and latest-frame-only
queue remain bounded.

Photo slideshows advance their own carousel with the wheel or the Scroll button. The browser
uses a visible next/previous arrow when one exists, otherwise a horizontal scroll container;
at the first/last photo the gesture moves to the previous/next feed post. These gestures share
the same serialized queue as videos. Focus content detects both large visible photos and videos.

Media focus also detects TikTok's visible canvas player when its underlying video element is
hidden. It ranks visible media by screen coverage and proximity to the viewport center, so a
partially visible upcoming post does not displace the current clip. This fixes the fallback
that showed a small clip inside the captured webpage with large black margins and website controls.

The phone and iPad now use a portrait browser viewport for the feed, regardless of the device
shape. The crop fits the media's complete intrinsic aspect ratio inside the available panel;
landscape tablets keep side padding instead of cutting off the bottom of portrait content.
Bottom controls sit outside the media, with a wide Scroll button and a smaller Previous button.

The **Fit / Fill** button beside the feed selector changes framing instantly. **Fit** preserves
the complete clip; **Fill** zooms uniformly to cover the video area and crops its edges. Neither
mode stretches the picture. Phone defaults to Fill; iPad defaults to Fit to preserve complete
portrait videos. Each device saves its own preference in Spotify Phone settings. Full-website
mode and visible login dialogs always fit without zooming. Pointer coordinates use the exact
frame uploaded to the GPU, keeping clicks aligned with cropped content.

Native validation uses Minecraft 1.21.11 with a Meteor companion and an installed browser:

```
./gradlew.bat runProductionClientGameTest -PphoneTikTokTestOnly=true -PliveTikTokTest=true
```

The local 13-card video fixture receives ten consecutive callbacks through Minecraft's
`Mouse.onMouseScroll`, then ten reverse callbacks, on both Phone and iPad. A second ten-scroll
run uses captured game controls and focused video, checking every destination, stable cropping,
continued frames and unchanged hotbar selection. Direction reversal must discard stale queued
forward movement. Existing typing, clicking, previous-video controls, fractional input, full
website scrolling, topic routing and browser cleanup checks remain enabled. The optional live
run attempts ten video identity changes on TikTok using a fresh anonymous test browser profile.
If TikTok presents sign-up, the result records how many changes were verified before that prompt;
the test does not create an account, accept provider terms or inspect a user's existing session.

Additional native framing fixtures cover visible canvas players with hidden underlying videos,
visible video players, portrait and landscape clips, both framing modes, cropped pointer input
and login-dialog restoration on both Phone and iPad. The optional live framing check opens a
fresh anonymous TikTok profile on each device:

```
./gradlew.bat runProductionClientGameTest -PphoneTikTokTestOnly=true -PmediaFramingLiveOnly=true
```

Live checks depend on TikTok serving playable media without a provider prompt. Local fixtures
verify layout and input independently of that service.

The photo fixture adds twelve slides with four visible corner markers. On both Phone and iPad,
native wheel callbacks advance ten photos and return ten photos, the Scroll button advances a
photo, and the carousel boundary moves to the next post. The checks verify complete portrait
framing and removal of the website sidebar. These local slideshow checks do not claim access to
TikTok content behind a provider login prompt.

Browser capture uses a visible portrait viewport at least 512 CSS pixels wide,
accommodating TikTok's minimum feed layout. Media detection includes visible canvas players
with hidden video elements. Capture pixels map using each screencast frame's actual device
metadata; pointer coordinates use the visual viewport scale, including pages without a mobile
viewport tag. This prevents cropped bottoms and site controls leaking into focused media after
browser sizing changes. Local native fixtures include this scaled-viewport case and verify the
exact center of the media receives a click.

The browser viewport follows page scrolling. A fixed page-coordinate capture clip is deliberately
not set: it can leave the captured picture on the first post even after DOM scrolling advances.
