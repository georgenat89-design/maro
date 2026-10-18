# Spawner pillars and multiple Goliath destinations

Spawner Notifier has a Pillar section. Pillars are enabled by default, visible through blocks,
and independent of Tags. Pillar Range defaults to 512 blocks, Pillar Height to 256 blocks;
Pillar Color changes the beam and block outline. Geometry is anchored to each detected block,
fades upward and at the range boundary, and uses the existing batched world renderer. Disabling
the option, hiding the HUD, breaking the spawner or leaving the world removes the marker.

Auto Goliath > Pick On Map accepts multiple selections, including across regions. Left-click
tiles to add or remove destinations; at least one remains selected. Right-click a tile to
replace the selection with that single destination. Start stops when any selected Goliath is
reached. When choices exist in another region, it tries those regions directly. Choices within
one region retain the existing bounce through another region before retrying. Region/Goliath
settings remain quick single-destination controls. Selected tiles are saved in module config;
older single-target configs remain supported.

Native production-client coverage: `./gradlew.bat runProductionClientGameTest -PspawnerGoliathTestOnly=true`.
The checks use real world spawners, screenshot pixels with occluding blocks and tags disabled,
pillar toggles/range/removal, actual map hit testing, config round trips, same-region bounces,
secondary-target arrival, already-arrived startup, and cross-region routing/arrival.
