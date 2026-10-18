package dev.maro.config;

import dev.maro.module.Module;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Favorites are independent of enabled states and selected gameplay configs. */
public final class ModuleFavorites {
    private static final Path FILE=ConfigManager.DIR.resolve("module-favorites.txt");
    private static final Set<String> NAMES=new HashSet<>();
    static {
        try { if(Files.exists(FILE))NAMES.addAll(Files.readAllLines(FILE)); }
        catch(java.io.IOException e){dev.maro.Maro.LOGGER.warn("Could not load module favorites",e);}
    }
    private ModuleFavorites(){}
    public static boolean contains(Module module){return NAMES.contains(module.getName());}
    public static void toggle(Module module){
        String name=module.getName();
        if(!NAMES.remove(name))NAMES.add(name);
        try {Files.createDirectories(FILE.getParent());Files.write(FILE,NAMES.stream().sorted().toList());}
        catch(java.io.IOException e){dev.maro.Maro.LOGGER.warn("Could not save module favorites",e);}
    }
}
