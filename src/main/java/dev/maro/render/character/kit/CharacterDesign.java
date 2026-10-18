// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public abstract class CharacterDesign {
    private final String id;
    private final String name;

    protected CharacterDesign(String id, String name) {
        if (id == null || !id.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("character id must match [a-z0-9_]+: " + id);
        }
        this.id = id;
        this.name = name;
    }

    public final String id() {
        return this.id;
    }

    public final String name() {
        return this.name;
    }

    protected abstract void define(Rig var1);

    public final Rig rig() {
        Rig rig = new Rig();
        this.define(rig);
        rig.refineFeatures();
        return rig;
    }
}
