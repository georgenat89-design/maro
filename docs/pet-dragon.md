# Dragon companion

Pet > Companion > Pet now includes Dragon. It renders Minecraft's detailed Ender Dragon
at miniature companion scale, with its native texture, glowing eyes, animated wings and
head/tail history. Auto movement floats it beside the player; Follow, Sidekick, Orbit,
Hover Height, Recall, Size, Animate, Fullbright and Name Tag remain available. Draco and
Ember are additional nickname choices.

Only visual state advances. The dragon and all multipart entities stay outside the client
and server entity registries; boss AI, entity ticks, combat, terrain changes and boss bars
are never started. Collision clearance uses its displayed size, not the full boss bounds.
The model is scaled around its own position and the optional label keeps normal text size.

Production-client checks exercise real queued rendering for all companion styles, then
verify dragon type, hovering/orbit, advancing/frozen wings, live size changes and absent
client/server registrations. Existing pet follow, collar, baby, recall and cleanup checks
remain in place.
