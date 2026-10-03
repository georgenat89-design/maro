package dev.maro.runtime.systems.modules;

import dev.maro.module.ModuleManager;
import java.util.*;
public final class Modules {
 private static final Modules INSTANCE=new Modules(); public static Modules get(){return INSTANCE;}
 public <T extends dev.maro.module.Module> T get(Class<T> type){return ModuleManager.get(type);}
 public Module get(String name){return getAll().stream().filter(m->m.name.equalsIgnoreCase(name)).findFirst().orElse(null);}
 public List<Module> getAll(){return ModuleManager.all().stream().filter(Module.class::isInstance).map(Module.class::cast).toList();}
 public void add(Module module){ModuleManager.register(module);}
}
