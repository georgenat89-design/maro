# Gameplay Textures and Cosmetics

Potato Graphics now lives in **Gameplay Textures → Appearance → Potato Graphics**,
alongside Motion Blur, Bloom and Color. Its toggle and original Flat, Soft and
Normal styles use the existing texture renderer. Flat uses each block texture's
average color; Soft retains a little texture detail. Transparency is preserved.
Changing the style triggers one resource reload and leaves video options intact.
The former standalone card is hidden; its stored settings and keybind are retained.
A shortcut in the Gameplay Textures sidebar opens the same Appearance section.

The module search understands merged features and setting names. Motion Blur,
Bloom, Potato Graphics, Gamma Boost and the former Better Textures / Better Looks
names find Gameplay Textures. Exact feature matches rank above partial setting
matches. Hats and sword/pickaxe skins find Cosmetics. Hidden implementation modules
stay out of the results, and each owning module appears once.

Cosmetics uses a category picker, a searchable style grid and a live character
preview. Ctrl+F focuses style search; Escape dismisses the picker or clears search
before closing. Larger windows show two style columns. Smaller windows use one
column and a compact header. Colors and motion controls scroll inside their panel.
Selecting an item skin updates the preview without enabling Cosmetics; Apply look
enables it. Preview rotation, sizes, equipment overlap rules and all existing styles
are retained. Icon stacks are cached for the selected category and scrolling uses
elapsed frame time.

The client continues to use the small metadata-only pack catalog. Download pack
opens its original page in the default external web browser; ZIP import and saved
Apply stay inside Minecraft.

Validation uses native Minecraft 1.21.11 production-client tests: merged-feature
search ranking and uniqueness; the actual Appearance toggle activating Flat/Soft
sprite loading and restoring normal textures; unchanged video options; Cosmetics
search, style clicks and category picker at GUI scales 2, 3 and 4. Existing texture
import/apply/restoration, preview and 153-item cosmetic catalog checks remain in
the suite. Final menu screenshots are visually reviewed.
