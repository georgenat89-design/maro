Sora interface font
===================

Copyright 2019 The Sora Project Authors (https://github.com/sora-xor/sora-font).
Sora SemiBold (600) and Bold (700) are static instances of the Google Fonts
variable font at commit a926665019d3f7f25c8b1212cecbfa871e70de82.
The current Maro Low derivative retains horizontal advances and weight while
scaling glyph outlines to 86% of their original height. Its internal family name
is Maro Low; the Sora setting and resource filenames retain profile compatibility.
They are bundled for local interface rendering under the SIL Open Font
License 1.1, retained in LICENSES/Sora-OFL.txt and packaged in the JAR.
No runtime font download is required. Rubik and Inter remain selectable.

Rubik menu font
===============

Copyright 2015 The Rubik Project Authors (https://github.com/googlefonts/rubik).
Rubik Medium and Bold were added with Maro build 693 under the SIL Open Font
License 1.1. The supplied license is retained in LICENSES/Rubik-OFL.txt and
packaged in the JAR. Inter and the Minecraft font remain selectable.

Maro startup presentation
=========================

Anubis 0.9.9.1's LoadingScreens was inspected as a visual behavior reference
for a central emblem that fills with progress. Maro's startup renderer,
geometric background, native title-button styling and loading integration
are independently implemented. No Anubis loading-screen code, images, fonts,
ImGui integration or executable entrypoints are bundled for this feature.
The startup M is a geometric rendering based on Maro's existing split-M branding.
Its early-loading glyph atlases are baked from the existing Rubik Bold font
under the retained SIL Open Font License 1.1.

Jump Circles replacement
=======================

JumpCircle's bounded ring storage, takeoff tracking, cubic expansion, feathered
bands, fade timing and facing-aligned logo placement are adapted from
net.anubis.client.module.impl.render.JumpCirclesModule in the user-supplied
Anubis 0.9.9.1+mc1.21.11 JAR by 4ldenz, under GPL-3.0-only.
Source project: https://github.com/4ldenz/Anubis-client
The license is retained in LICENSES/GPL-3.0-Anubis.txt and LICENSE-Anubis.
Maro changes: native module/jump/world rendering hooks, the existing Maro logo
with a small GPU glow filter, optional landing triggers, module renaming and
shared Spotify bass synchronization. The previous Orbit/Neon/Ripple effect was
replaced. Anubis's logo textures and original executable entrypoints are absent.
Full corresponding Maro source accompanies the JAR.

PacksMC external catalog
========================

Maro displays the first 50 public Trending cards from https://packsmc.com/,
with creator names, preview URLs and links to each pack's original page.
A bounded metadata snapshot is included; preview images load from PacksMC's
public image hosts at runtime. The public showcase listing is refreshed with
limit=50 and offset=0. Site documentation: https://packsmc.com/docs
Pack ZIPs are downloaded through PacksMC's own pages, preserving creator access,
credits and download flows. The original page opens in an isolated installed
browser inside Minecraft; completed Java ZIP downloads install automatically.
Manual local import remains available.
The collection installer visits these pages sequentially, skips already saved
archives and imports completed downloads into the player's resourcepacks folder.
It preserves the site's normal access flow. The ordinary Maro JAR and source archive contain
no PacksMC pack ZIPs, screenshots, website implementation or branding assets.
Downloaded previews are cached locally; a separate private installation collection
can include the user's downloaded original ZIPs and preview cache. It is not part
of the public mod/source distribution. Creator credits, licenses, original URLs
and file hashes accompany that collection. Creator terms remain attached to
catalog entries and on the site.
An earlier separate private client-with-library variant embeds the user's 49 downloaded
original ZIPs, 50 previews and full credits/hash manifest under maro-pack-library.
It streams selected archives into resourcepacks, validates their hashes and uses
native Apply. It contains no Packs+ exclusive archive. Install and Install all do
not open websites in that earlier variant. The current small client opens the
original page in the default external browser from Download pack / Creator page,
and contains no third-party pack archives or previews.
Maro's native browser and local import code are original implementations.
The former Bauloo catalog browser/downloader has been removed.

Anubis feature ports
===================

Tpahere Kill Bot, Tp Macro, Weapon Inspect, their supporting routines, knife models,
animation curves, and knife texture were adapted from the user-provided
Anubis 0.9.9.1+mc1.21.11 JAR, by 4ldenz.
Source project: https://github.com/4ldenz/Anubis-client
License: GPL-3.0-only. The supplied license is included in src/main/resources/LICENSE-Anubis
and is packaged in the resulting JAR. Maro's lifecycle, settings, friends, networking,
combat publication, and rendering integration replace the Anubis client framework.

Only the named features and their required support code/assets were ported. The
original executable entrypoints and native bootstrap are not bundled or executed.

Tpahere Kill Bot Bow Spam
========================

Meteor Client's BowSpam module was inspected as a behavior reference for
configurable native bow charge and use/release timing.
Source: https://github.com/MeteorDevelopment/meteor-client/blob/6e5d69133352a1b71d525733535a467dc196a94f/src/main/java/meteordevelopment/meteorclient/systems/modules/combat/BowSpam.java
Copyright (c) Meteor Development and contributors. License: GPL-3.0,
retained in LICENSES/GPL-3.0-Meteor.txt and packaged in the JAR.
Maro changes (2026-10-12): independent Sword/Bow Spam modes, persistent target
selection, native bow/ammunition checks, drag/gravity trajectory checks,
adaptive charge, published aim, cancellation, healing and selling integration.
No crossbow behavior or Meteor executable classes/framework are bundled.
Full corresponding Maro source accompanies the JAR.

Additional Maro options: automatic target lock, separately toggled bow shooting,
armor-cutoff weapon selection, ranged fallback and optional conservative native
charge timing. These do not claim verified behavior on Donut or other public servers.

Bedrock Void ESP
================


The supplied glazed-1.21.11-n-17.1.jar was inspected as a behavior reference for
enclosed non-bedrock components in Overworld and Nether bedrock layers. Maro's
scanner, state management, settings and rendering were implemented independently.
No Glazed classes, source code or assets are bundled.

Trident Boost
=============

Trident Boost and its Riptide impulse/water hooks were adapted from
net.anubis.client.module.impl.movement.TridentBoostModule and
com.example.client.mixin.MixinTridentBoostTridentItem in the supplied
Anubis 0.9.9.1+mc1.21.11 JAR, by 4ldenz, under GPL-3.0-only.
Source project: https://github.com/4ldenz/Anubis-client
The license is retained in src/main/resources/LICENSE-Anubis and
LICENSES/GPL-3.0-Anubis.txt and packaged in the resulting JAR.
Changes: native Maro lifecycle/settings, local-client-only impulse and water
hooks, preserving full native charge timing. Auto Trident has been removed. Corresponding
Maro source accompanies the JAR. Original Anubis entrypoints are not included.

Auto Replenish
==============

Meteor Client's meteordevelopment.meteorclient.systems.modules.player.AutoReplenish
in the local meteor-client-1.21.11-85.jar was inspected as a behavior reference.
Source project: https://github.com/MeteorDevelopment/meteor-client
Reference commit: 6e5d69133352a1b71d525733535a467dc196a94f
Authors: MineGame159, squidoodly, seasnail and Meteor Client contributors.
License: GPL-3.0, retained in LICENSES/GPL-3.0-Meteor.txt and packaged in the JAR.
Maro's implementation uses its own lifecycle/settings and native slot clicks,
with persistent empty-slot memory, strict component matching, optional worn-tool
replacement, inventory-context pauses, one transaction at a time, latency-aware
settling and bounded rejection backoff. Corresponding Maro source accompanies the
JAR. No Meteor executable classes or original module framework are bundled.

Phone video playback
====================

JCodec and JCodec JavaSE 0.2.5 by the JCodec project are bundled as nested JARs
for pure Java H.264/MP4 decoding and image conversion. Source:
https://github.com/jcodec/jcodec . License retained in
LICENSES/BSD-2-Clause-JCodec.txt. JCodec's included JAAD AAC decoder originated
under public domain; JCodec changes are under the same FreeBSD license.
The phone integration, background playback/cleanup and feed controls are
original Maro code. Demo loops have been removed from the production mod;
the original Neon loop remains solely in the separate native test fixture.

Fake Pay replacement
====================

Glazed 1.21.11 n-17.1 FakePay/FakeScoreboard were inspected statically as a behavior
reference for local /pay interception, white text with a green dollar symbol,
action-bar feedback, the hit-marker sound and a local balance deduction.
Glazed is All Rights Reserved; no Glazed code, classes or assets are incorporated
in this implementation. Maro's replacement uses its own native command hook,
BigDecimal parsing/formatting, settings, connection lifecycle and render-only
sidebar transformation. It does not replace or mutate server scoreboard data.

Shulker Dropper and Spawner Dropper
================================

The user's supplied Glazed 1.21.11-n-17.1 JAR by nnpg and AR3 was inspected statically
as a behavior reference for shop-buy/drop cycles and spawner loot-menu actions.
Glazed is All Rights Reserved. Maro's implementations, settings, state machines,
inventory protection, menu validation and native tests are independently written;
no Glazed source, classes, assets, entrypoints or binaries are bundled.

The five 3D harvesting-tool meshes in `dev/maro/render/pickaxe` are original geometry
reconstructions made from the user's Driver, Minty, Leviathan, Cold Snap and Star Wand
reference recordings. They are not imported Fortnite game meshes or textures. The fifteen
Ogg samples in `assets/maro/sounds/pickaxe` are edited tool sounds from the user-provided
recordings; those sounds retain their original ownership. Source hashes and edit intervals
are documented in `docs/pickaxe-audio-provenance.json`; full recordings are not bundled.
Product/item names identify the references. Epic Games has not endorsed this project.
Reproduce the audio edits with `tools/build_pickaxe_sounds.py` and the supplied recordings.


User-supplied 3D character models (CC BY 4.0)
============================================

- "Spongebob Squarepants" by sonicfan443:
  https://sketchfab.com/3d-models/spongebob-squarepants-4dc65614da13403fa45a9710b4b4d655
- "LeBron James" by Looney Tunes:
  https://sketchfab.com/3d-models/lebron-james-7c750ecf2a9d432c9dd3310a73aed7d3
- "Chun Li" by AlexGIsHim:
  https://sketchfab.com/3d-models/chun-li-f517785069b543cab262ecd1cfe84efe
- "skeleton" by sanik.pdo:
  https://sketchfab.com/3d-models/skeleton-aad392aff87246179789ac23063856a6
- "Jeffrey Epstein" by SigmaGrindsetMog:
  https://sketchfab.com/3d-models/jeffrey-epstein-bd6c6bf543184c8da1f457fc7bf0c494
- "Donald Trump Running" by Vadim Fedenko:
  https://sketchfab.com/3d-models/donald-trump-running-9890c103b8f249789f11ec1be0fec94a
- "Michael Jackson (The Way You Make Me Feel)" by MrLOCKiDY:
  https://sketchfab.com/3d-models/michael-jackson-the-way-you-make-me-feel-c2981b4047a54824ac06300c5b58c2d7
- "Roblox-Bacon-Hair" by Roblox (https://sketchfab.com/Robloxs):
  https://sketchfab.com/3d-models/roblox-bacon-hair-88a62bc6286947e9846c5cd445d2628b

- "Verity™ | Verity™" by SquishyFishyFish (https://sketchfab.com/skibidi_27636):
  https://sketchfab.com/3d-models/veritytm-veritytm-be4400e1f850406387b8016383ccb0ad
- "Minion" by aung (https://sketchfab.com/aung):
  https://sketchfab.com/3d-models/minion-df28a0618d3040f29c646f5e1dbddb8a
- "Travis Scott" by Alwxcat (https://sketchfab.com/ParallelAlex):
  https://sketchfab.com/3d-models/travis-scott-3353bd852c31409ebed67d49258867b9

- "Slender Fortress - Undead Furher (Adolf Hitler)" by rogerreyes582 (https://sketchfab.com/rogerreyes582):
  https://sketchfab.com/3d-models/slender-fortress-undead-furher-adolf-hitler-036f0e7b0204483589e7d7b1fa835677

The user provided these twelve ZIP archives with attribution/license files identifying
CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/). Those files are retained in
LICENSES and tools/model-sources. The assets were centered, scaled and baked for Maro's
character renderer; SpongeBob's whole limb components were rigged/lowered and a GLB walk
cycle added. LeBron's unattached ball was omitted from the wearable character. Chun-Li
retains the supplied skeleton with reduced geometry at three detail levels. Skeleton's
legacy diffuse colors are preserved and its limb groups are generated from the mesh.
Roblox Bacon Hair retains its supplied textures and Mixamo skeleton, with head, arm,
hand and leg assignments and three detail levels. Epstein/Trump retain source materials and skeletal limb assignments; Jackson's limb groups
are inferred from the supplied mesh. Runtime detail levels are prebaked; the portable
Trump GLB retains its source running animation. Source
hashes and conversion details are in docs/*-model.json. Verity retains its single-piece shape and original two materials. Minion preserves its supplied yellow body, overalls, goggles and textures, with mesh-based limb groups and three geometry budgets. Travis Scott retains the supplied body, head and accessory base-color textures and source skeletal assignments, with a centered/scaled mesh, lowered arms, bounded atlas and three baked geometry levels. Its portable GLB retains the source animation. No endorsement is implied.

Undead Fuhrer retains its supplied body/head textures and ValveBiped skeletal assignments;
the centered model, arm bends, hand grips and three detail levels are baked offline.

The character rendering/import framework is adapted from Anubis 0.9.9.1 by 4ldenz under
GPL-3.0-only; its original notices and LICENSES/GPL-3.0-Anubis.txt are retained.
