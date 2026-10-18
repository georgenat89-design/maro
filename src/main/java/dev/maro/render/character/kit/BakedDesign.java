// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.kit;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class BakedDesign
extends CharacterDesign {
    private final Source source;

    public BakedDesign(String id, String name, Source source) {
        super(id, name);
        this.source = source;
    }

    public static BakedDesign builtIn(String id, String name, String resource) {
        return new BakedDesign(id, name, () -> {
            InputStream in = BakedDesign.class.getResourceAsStream(resource);
            if (in == null) {
                throw new FileNotFoundException(resource);
            }
            return in;
        });
    }

    InputStream open() throws IOException {
        return this.source.open();
    }

    @Override
    protected void define(Rig rig) {
        throw new UnsupportedOperationException(this.id() + " is baked and has no rig");
    }

    @Environment(value=EnvType.CLIENT)
    public static interface Source {
        public InputStream open() throws IOException;
    }
}
