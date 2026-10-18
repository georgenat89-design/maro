# Custom Model

Open **Visuals → Custom Model** to browse the Character Wardrobe. Choosing a model previews it immediately; the Enabled button applies it in the world. Drag the preview to turn it without moving your real player. Appearance changes are local to your client.

The wardrobe has 30 built-in models:

- CJ, Neighbor, Tung Tung Tung Sahur, Tralalero Tralala, Astronaut, Neon Robot,
  Ninja, Frog, Penguin, Hitler (Historical), and Jewish Skin.
- Arnold Schwarzenegger, David Laid, LeBron James, IShowSpeed, Kai Cenat,
  Jeffrey Epstein, P. Diddy, and Enslaved Person (Historical).
- Michael Jordan, Kobe Bryant, Shaquille O'Neal, Stephen Curry, Kevin Durant,
  Lionel Messi, Cristiano Ronaldo, Mike Tyson, Bruce Lee, Dwayne Johnson, and John Cena.

Sakura, Luna, Mika, Aoi and Miku have been removed. Old saved selections for those
names fall back to the default model (Neon Robot for self, None for others) when
settings load. Auto Trident has been removed; manual Trident Boost remains.

The portraits are original stylized likenesses. Continuous bevelled cross-sections
replace the stacked round primitives for human heads, torsos and limbs.
Individual profiles control jaw
and head proportions, nose/eye spacing, shoulders, muscle volume, skin/hair color,
hair style and facial hair. Clothing includes gym tanks, numbered basketball
uniforms, football kits, shirts, a suit, a tracksuit, boxing shorts and casualwear.
Uniforms have original front lettering and rear names/numbers; fingers, ear folds,
eyelids, nostrils, seams, pockets, laces and accessories receive fine geometry.
Classic outfit choices are fixed visual presets and do not track current teams.
The historical portraits use plain clothing. Enslaved Person is a neutral clothed
historical figure, without racial caricature. Jewish Skin wears casual clothing,
a kippah and animated side curls (peyot).

List cards, outlines and labels share the same viewport clipping. Partly visible
cards respond only inside their visible area; footer controls stay accessible.
Scroll is clamped on resize, and the footer does not capture list scrolling.

**Your Model** and **Other Players** have independent selections, including None. Other Players can affect Friends or Everyone; Friends is the default and uses Maro's friend list. Low, Medium, and High control mesh detail. Items, first-person arms, and ink outlines can be toggled separately. Outlines default off for clean edges. Characters follow vanilla movement, crouching, swimming, riding, and held-item poses. Incompatible armor and accessory layers are suppressed while a replacement model is active; their modules remain enabled.

High is the default for new settings; existing saved detail choices are preserved.
Faces, hands, eyes and fringes receive finer sampling within the existing triangle
budgets. CJ and Neighbor retain their supplied meshes/UVs with gentler directional
lighting. Tralalero's body/fins receive more of the mesh budget. Tung has a wooden
bat that follows his grip rigidly and hides when he holds a real item.
The seven original characters have rebuilt anatomy and fine features: articulated
space-suit gloves and chest controls, robot joints/fingers, a masked ninja with
cloth wraps, frog hind legs and webbed feet, penguin flippers/short legs, and
continuous human anatomy, planar jaw/cheek/brow structure and detailed hands and clothing. The historical model remains in plain
clothing; Jewish Skin remains a contemporary casual character with a kippah and side curls.

The astronaut's helmet seal, chest controls and life-support pack use NASA's
[spacesuit component overview](https://www.nasa.gov/humans-in-space/what-is-a-spacesuit/)
as a visual reference. No reference-site image assets are bundled.
The expanded sports roster uses visual references from the
[NBA legends archive](https://www.nba.com/archive-75),
[Gymshark's athlete page](https://www.gymshark.com/pages/every-strong-belongs),
[WWE's John Cena page](https://www.wwe.com/superstars/john-cena), and
[Messi's club profile](https://www.intermiamicf.com/players/lionel-messi/).
The side curls follow the hairstyle described by
[Chabad's peyot overview](https://www.chabad.org/library/article_cdo/aid/2963669/jewish/Why-Do-Some-Chassidic-Jews-Have-Long-Sidelocks-Peyot.htm).

Use **Characters Folder** to open `config/maro/characters`. Add a self-contained, uncompressed glTF 2.0 `.glb` file, then click **Reload GLB**. Skeleton recognition animates supported limbs, with a shape-based fallback for models without a recognized rig. Large imports may take time on their first load. Prepared meshes are cached under `.baked` in that folder. Failed or unsupported imports leave the vanilla player visible.

Meshes are prepared in a bounded worker pool. Render commands capture each part's transforms and deformation angles before deferred drawing. Springs use interpolated game time; repeated rendering at the same time does not advance animation, and the wardrobe has its own animation identity. Cache eviction and disable invalidate pending results, world changes reset motion, and textures are recreated after resource reloads. Unselected old characters are never substituted while another selection loads.

Validation: `gradlew.bat build runProductionClientGameTest -PcustomModelTestOnly=true -PmeteorTestJar=<Meteor JAR>` exercises all 30 characters at High through the production Minecraft renderer, captures front/profile views and rear uniform details, checks screenshot differences, finite indexed meshes and duplicate-pass motion for every model, and checks Low detail on all procedural designs. Pixel comparison and clicks at GUI scales 2/3/4 verify list/footer clipping and controls. It also tests separate self/other targeting, spectator and first-person guards, every model crouching with held equipment, disable cleanup, GPU texture recreation, and an original textured GLB fixture through import and disk-cache reload. The fixture generator is `tools/custom_model_fixture.py`.

The adapted Anubis engine, original built-ins, and their GPL-3.0-only attribution are documented in `THIRD_PARTY_NOTICES.md`. Corresponding source is distributed with the built JAR.
