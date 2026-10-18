# Gameplay update validation — 2026-10-12

Built with Java 21. Native production-client tests used Minecraft 1.21.11,
Fabric and the optional Meteor 1.21.11-85 companion.

- Build and gametest compilation passed.
- Texture suite passed: exactly 50 current PacksMC cards and all 50 preview links,
  creator credits, search/favorites, three GUI scales, ZIP validation, application,
  priority and restoration.
- Gameplay update suite passed: one module registry entry, legacy migration and
  existing-profile precedence, appearance hooks, embedded creator-page rendering,
  a real browser attachment download, automatic installation/application and
  temporary-file cleanup. Expanded sword/pickaxe previews rendered in Minecraft.
- Phone/TikTok suite passed: repeated forward and reverse gestures across a
  five-page snapping fixture, ordinary website wheel input, crop-coordinate
  clicks, small trackpad deltas, locked-cursor navigation, Phone/iPad topic routing,
  video playback and browser cleanup.
- Phone polish passed with distinct incoming/outgoing artwork, both hands,
  previous-track direction and iPad. Delayed artwork retained the outgoing cover;
  finger/cover clocks matched and old GPU textures closed after transitions.
  Rounded viewport screenshots were visually reviewed after the final clip change.
- Jump Circle passed: the rename field is absent, the standard label remains,
  jump/landing, rings, Maro logo, audio onset and lifecycle checks pass.
- Asset validation found 153 distinct pixel images, complete model/definition
  pairs, 66 sword and 37 pickaxe images at 64×64, and 25 shovels/25 tridents at
  their existing 32×32 resolution.

Automated download tests used local creator-page fixtures in an actual installed
browser. Separately, 49 free packs from the captured first-50 catalog were downloaded
through PacksMC's normal public pages and validated for the user's local collection.
No PacksMC account sign-in or personalized TikTok FYP was used. There is no claim
of a measured FPS gain.

## Texture workspace revision

The redesigned overview was rendered and visually reviewed at GUI scales 2, 3
and 4. Sidebar navigation and the Appearance card were clicked at each scale.
Production-client coverage also saved two different pack fixtures through a real
embedded browser, automatically advanced between pages and completed the queue.
Neither download changed the active loadout. Resume omitted saved packs; removal
of config associations followed by scanning recovered both local ZIPs. The saved
pack inspector's Apply action enabled the selected local archive. Skip and the
50-entry collection cap passed. The existing texture priority/restoration and
50 live card/preview checks passed again.

All 50 live preview links were fetched and written to the offline cache; cached
reads preserved the original image bytes. A private local installation collection
contains 49 original pack ZIPs, catalog metadata, all 50 cached previews and the
updated Maro JAR. Every pack ZIP passed CRC, root metadata, nonempty assets, bounded
size/entry count and unsafe path/executable checks. Its manifest retains creator
credits, licenses, source URLs, byte sizes and SHA-256 hashes.
Vanilla Utility Pack (16d024d9-63bf-4804-b2db-56a0d8ba46ab) was excluded because
its normal page requires Packs+ membership. No gate/account/payment steps were
bypassed. Pack archives and preview images remain outside the mod JAR and source
distribution. This validation does not claim visual compatibility of every texture
or runtime performance measurements for all 49 packs.

## Included-library and preview correction

Install pack now streams an included archive directly from the private client JAR,
checks its SHA-256 and ZIP contents, registers it and applies it on the same screen.
Install all saves the included library without changing the current loadout.
Creator page is the explicit website action. Restricted or unincluded primary
actions show availability and do not navigate. Closing the screen cancels pending
activation while allowing the archive to finish saving.

Native original fixtures verify Install/Apply, Install all, unchanged archive bytes
and loadout, corrupt-hash rejection, restricted-pack behavior and closing during
installation. Pixel checks verify transparent preview corners, opaque interior
pixels and antialiased edges. Preview screenshots were reviewed at three scales.
The standard mod/source remain free of third-party pack files; the user's separate
private client variant contains 49 original ZIPs, 50 previews and the credits/hash
manifest. Its embedded archive hashes and JAR CRC are checked after packaging.

## Small client with external downloads

The subsequent preference change restores the standard small JAR without the
embedded collection. Download pack and Creator page dispatch the selected canonical
pack URL to Minecraft's operating-system browser opener on a worker. Browse website
opens the Trending listing. Saved Apply and ZIP import remain native, and rounded
preview decoding is retained. Native input checks capture and verify external
dispatch URLs, confirm the game menu stays open, and check that Download does not
silently install an archive. Release inspection verifies no pack-library payload.
