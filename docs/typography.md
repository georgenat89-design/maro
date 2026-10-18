# Client-wide typography

Maro's menu pages, module editors, native controls inside Maro screens, custom
editors, HUD labels, music/lyrics, phone, builder panels, image pickers and
notifications display uppercase by default. Sora Bold is the default face, with
Sora SemiBold for sentence-case text. Existing client profiles adopt Sora and
uppercase once at look revision 5; later explicit font/case preferences remain
configurable. Rubik and Inter remain available. Window/Panels now default
to 75% size; look revision 4 updates only the previous 85% default and retains
other saved size choices.

Menu Text Size defaults to 90% and can be adjusted independently of HUD text or
panel size. The title menu's logo, type, buttons and native hitboxes are 20%
smaller, with the background continuing to fill the screen.

The Sora option now uses the Maro Low derivative: glyph outlines are 14% shorter
while horizontal advances, kerning and weight stay the same. This gives bold
all-caps labels a lower profile without narrowing them. The font outlines are
modified before rasterization; finished text textures are not vertically scaled.
GUI/HUD sizing retains the original nominal cap size so height normalization
does not undo the shorter style. Visible-ink centering follows the new outlines.
Rubik, Inter and vanilla alternatives retain their existing proportions.

The shared renderer rasterizes each line at its final physical size with
whole-pixel hinted glyph advances and restrained grayscale antialiasing. Text is rasterized once at native
framebuffer resolution, without a bicubic downsampling pass. Integer raster offsets and pixel-aligned placement avoid an
extra half-pixel texture blur. Flat labels use nearest texel sampling, and a
bounded coverage contrast trims faint edge halos without removing curve AA.
Rotated text retains linear sampling. Glyphs are positioned on the physical pixel grid;
transforms are retained, including rotation and resized HUDs. Widths use
logical advances, including trailing spaces, and match the drawing. Centered,
right-aligned, trimmed and wrapped labels use those same metrics. Wrapping also
handles long unbroken words and line breaks without splitting surrogate pairs.

Nested DrawContext clips are captured per context and attached to queued text
batches. Text textures are reused, limited to 768 cached lines and 16 MiB after
submission; width measurements are capped at 2048 entries. Textures are retired
only after GUI submission. Unsupported bundled glyphs use a shaped platform
Unicode fallback. The music/map renderer retains native-size cached glyph
atlases and uses the matching bold face/casing.

Uppercase changes display only. Stored usernames, file paths, commands, lyrics
and editable values retain their exact characters. Native text fields still own
editing/selection/narration; their width, clipping and caret calculations use the
same font as their display. Minecraft's own chat, server messages, item text and
unrelated mod screens retain their native renderer. Explicit Minecraft font
options in individual HUD modules remain available.

The Panels footer has a solid backing above the hotbar. Empty module categories
now offer user-facing navigation rather than implementation instructions.

Validation: `gradlew.bat build runProductionClientGameTest -PtypographyTestOnly=true`
(with optional `-PmeteorTestJar=<Meteor JAR>`) runs the production client. It checks
actual grayscale pixel coverage, nested clipping, input/caret behavior, GUI scales
2/3/4, Window/Panels layouts, all ten menu pages, every registered module editor
and its setting sections. It also exercises keyboard placement, staff/map HUDs,
Spotify/phone/lyrics, nametags/armor, accessories, image pickers, texture-pack
reloads and Screen GUI. Startup/reload/cancellation/failure checks run before the
tour. The runtime avoids external font downloads and Python dependencies.
