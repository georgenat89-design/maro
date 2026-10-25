# Branding, condensed typography and connected sidebar

Maro 1.0.6 uses Barlow Condensed SemiBold (600) and Bold (700), version 1.408.
This is the closest identified, properly sourced match to the supplied small
reference's heavy, narrow uppercase lettering. The image alone does not establish
the exact original font. Both cuts are unmodified Google Fonts distributions;
SIL OFL 1.1 is retained in `LICENSES/BarlowCondensed-OFL.txt` and the release JAR.
Source: https://github.com/google/fonts/tree/main/ofl/barlowcondensed

`Fonts` centralizes family selection and exposes the effective font file to
`SmoothHudText`, the native mesh renderer and map raster labels. Native Maro
widgets and rich tooltips use the same font metadata while retaining their
colors, links, decorations, alignment and clipping. HUD labels, notifications,
previews, editors, Maro-owned item counts and XP numbers follow the shared face.
Numeric values remain literal; Name Protect cannot replace their digits.

Caps Text defaults on to match the reference. Editable values retain their actual
casing and are measured with their rendered glyphs. Text uses genuine condensed
letterforms rather than horizontal compression, cached rasterization at physical
size, and Unicode fallback. The two font weights remain available; Caps Text
selects the stronger label weight. User font/casing controls still work.

Use Client Font Everywhere defaults on. Saved per-module font choices remain in
their existing configurations and become active again when this override is off.
Presentation revision 7 adopts the new face/override/Caps defaults once without
resetting module behavior, keybinds, ranges, values or saved profiles.

## One coherent wordmark

`MaroWordmark` renders the existing custom M as the first capital letter and
draws only literal lowercase `aro` after it. Visible mark bounds are matched to
the font's actual capital height and baseline, with a small measured ink gap.
Centering uses the complete word's measured bounds. Aliases and uppercase styles
cannot duplicate or rename the brand. Existing purple, blue and contour logo
treatments remain; the contour hover moves the entire word together. Existing
`.gg` brand suffixes remain where previously displayed.

The shared wordmark is used in ClickGUI, title/browsing/loading screens, settings,
the texture screen and background preview. Icon-only uses, URLs, prose and data
identifiers retain their existing roles.

## Connected navigation

The vertical category sidebar sits inside the left edge of the existing rounded
outer menu container. A subtle divider separates it from the workspace. Maro's
six real categories appear once, followed by a separated utility section. Icons,
hover surfaces, selected backgrounds and narrow accent markers retain the theme.
Navigation owns its clipped scroll region; browser and settings scrolling do not
move it. Page changes and resizing reveal the selected destination.

The browser keeps two columns beside an independent settings panel. Existing
preview docks remain available. Flat menus adapt their logical height between
480 and 640 for readable controls at different GUI scales. World Screen GUI keeps
its fixed 1080×640 canvas and stationary world anchor; navigation does not alter
the panel's world position, orientation or physical size.

## Verification

The focused native gate is `runProductionClientGameTest -PwordmarkTestOnly=true`.
It captures actual title/sidebar/loading pixels and measured wordmark bounds at
effective GUI scales 2, 3 and 4, plus a 960×540 viewport. It uses real category,
utility, search and wheel input, checks visible hover states and independent
scroll regions, verifies Caps on/off and literal search measurements, and checks
that module settings remain unchanged. Versioned release validation also records
the relevant editor, preview, world-panel and companion-mod regression results.

OS capture applications require separate verification. Existing fullscreen
transition driver blit diagnostics can occur in the inherited test environment;
settled rendering and owned-resource checks are recorded with each build.
