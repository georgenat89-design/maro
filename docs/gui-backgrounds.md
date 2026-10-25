# GUI backgrounds

Open **Settings → Background Studio**, or use the **GUI Background** settings card.
Backdrop changes are immediate and save with the client settings when the editor closes.
The original default, a solid color, a two-color gradient, transparent gameplay and a
custom PNG are supported. Gameplay Dimming controls contrast independently of backdrop
opacity. Control panels retain their normal readable surfaces.

Gradient presets are Maro Blue, Midnight, Ocean, Dusk and Forest. Both colors and the
direction remain editable. Customized colors survive loading a saved preset selection.

In the studio, choose **Import PNG** or drop a PNG into the window. The picker is drawn
inside Minecraft, including its path field, folders, drive navigation and Cancel action;
it never opens an unprotected operating-system dialog. Directory listing is asynchronous
and bounded, with no per-frame disk reads. The same protected picker is used for crosshair,
sky and totem images, resource-pack ZIP files, and phone MP4 attachments. Each entry point
keeps its existing file validation and asynchronous import. The same import button
becomes **Replace PNG** once an image is loaded. **Remove PNG** deletes the managed
asset; **Reset backdrop** restores the background settings and removes that image.
The selected image is stored at `maro/assets/gui-background.png`, independently of the
original source file.

Fit shows the entire image and Fill crops its edges while preserving proportions.
Stretch uses the entire viewport and Tile repeats the image. Scale, horizontal/vertical
alignment, tint and opacity are live controls. PNG alpha is preserved. A white tint
retains the image's colors. Flat menus use the screen backdrop; floating windows use
the same background inside their pane with a readability scrim.

Imports accept PNG files up to 32 MiB, at most 32768 pixels on either edge and at most
64 megapixels in source dimensions. The decoder subsamples large sources before
allocating their final bitmap. Stored textures are limited to 4096 pixels per edge and
4 megapixels. Invalid and missing files produce a readable fallback and status message.
An unsuccessful replacement preserves the current texture.

File access and decoding run only on import or the first request for the saved asset.
The GPU texture is cached across layout/color changes and frames. Replaced textures
retire after queued GUI batches complete. Resource reloads reupload retained pixels
without rereading or decoding the file. No polling loop or additional world render is
used. **Interface → Frosted Glass** uses the native menu blur with an adjustable
**Frosted Glass Strength** of 1–10. The selected radius applies only while Maro's menu
and blur are open; Minecraft's saved blur preference is preserved. The backdrop system
adds no separate blur pass.

`GuiBackgroundChecks.run(context)` exercises invalid/missing PNGs, bounded downscaling,
alpha blending through the native GPU GUI pipeline, texture reuse, all four layouts,
palette/settings persistence, and the studio at GUI scales 2, 3 and 4. Screenshots are
saved under the disposable test client's `run/screenshots` directory. Capture exclusion
is the responsibility of the shared Stream Proof presentation path, not the PNG cache.
The suite also runs `MenuBlurChecks`: native radius 1/5/9, visible changes in blur strength,
sharp foreground controls and preservation of Minecraft's saved blur setting.
`PhonePickerChecks` uses an inactive local phone identity and generated playback to verify
attachment picker cancellation and selection preserve the same conversation and unsent
draft, while interruption closes the suspended screen. It never connects, sends a message
or uploads an attachment.
