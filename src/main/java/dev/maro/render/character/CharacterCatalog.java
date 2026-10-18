// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character;

import java.util.ArrayList;
import java.util.List;
import dev.maro.render.character.kit.BakedDesign;
import dev.maro.render.character.kit.CharacterDesign;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class CharacterCatalog {
    private static final List<CharacterDesign> BUILT_IN = builtIns();

    private static List<CharacterDesign> builtIns() {
        return List.of(BakedDesign.builtIn("spongebob", "SpongeBob", "/assets/maro/characters/spongebob.anc"),
            BakedDesign.builtIn("lebron", "LeBron James", "/assets/maro/characters/lebron.anc"),
            BakedDesign.builtIn("chun_li", "Chun-Li", "/assets/maro/characters/chun_li.anc"),
            BakedDesign.builtIn("skeleton", "Skeleton", "/assets/maro/characters/skeleton.anc"),
            BakedDesign.builtIn("jeffrey_epstein", "Jeffrey Epstein", "/assets/maro/characters/jeffrey_epstein.anc"),
            BakedDesign.builtIn("donald_trump", "Donald Trump", "/assets/maro/characters/donald_trump.anc"),
            BakedDesign.builtIn("michael_jackson", "Michael Jackson", "/assets/maro/characters/michael_jackson.anc"),
            BakedDesign.builtIn("roblox_bacon_hair", "Roblox Bacon Hair", "/assets/maro/characters/roblox_bacon_hair.anc"),
            BakedDesign.builtIn("verity", "Verity", "/assets/maro/characters/verity.anc"),
            BakedDesign.builtIn("minion", "Minion", "/assets/maro/characters/minion.anc"),
            BakedDesign.builtIn("travis_scott", "Travis Scott", "/assets/maro/characters/travis_scott.anc"),
            BakedDesign.builtIn("undead_fuhrer", "Undead Fuhrer", "/assets/maro/characters/undead_fuhrer.anc"));
    }

    private static volatile List<CharacterDesign> designs = BUILT_IN;

    private CharacterCatalog() {
    }

    public static List<CharacterDesign> all() {
        return designs;
    }

    public static boolean refresh() {
        ArrayList<CharacterDesign> next = new ArrayList<CharacterDesign>(BUILT_IN);
        ArrayList<String> taken = new ArrayList<String>(CharacterCatalog.names(BUILT_IN));
        for (BakedDesign custom : CustomCharacters.scan()) {
            if (taken.contains(custom.name())) continue;
            taken.add(custom.name());
            next.add(custom);
        }
        List<CharacterDesign> old = designs;
        designs = List.copyOf(next);
        if (old.size() != next.size()) {
            return true;
        }
        for (int i = 0; i < next.size(); ++i) {
            if (old.get(i).id().equals(((CharacterDesign)next.get(i)).id())) continue;
            return true;
        }
        return false;
    }

    public static List<String> names() {
        return CharacterCatalog.names(designs);
    }

    private static List<String> names(List<CharacterDesign> list) {
        ArrayList<String> out = new ArrayList<String>(list.size());
        for (CharacterDesign design : list) {
            out.add(design.name());
        }
        return out;
    }

    public static CharacterDesign byName(String name) {
        List<CharacterDesign> list = designs;
        for (CharacterDesign design : list) {
            if (!design.name().equals(name)) continue;
            return design;
        }
        return null;
    }
}
