package dev.maro.nathan;
import dev.maro.Maro;
import dev.maro.module.ModuleManager;
import dev.maro.nathan.modules.*;
public final class NameeProtectAddon {
 public static final org.slf4j.Logger LOG=Maro.LOGGER;
 public static final dev.maro.runtime.systems.modules.Category CATEGORY=new dev.maro.runtime.systems.modules.Category("Nathan");
 public static void init(){
  ModuleManager.register(new AutoWalk());ModuleManager.register(new Bloom());ModuleManager.register(new ChatMacros());
  ModuleManager.register(new ColorCorrect());ModuleManager.register(new CustomFov());ModuleManager.register(new FakeXp());
  ModuleManager.register(new FastXp());ModuleManager.register(new FreeCam());ModuleManager.register(new FreeLook());
  ModuleManager.register(new Hats());ModuleManager.register(new KeySounds());ModuleManager.register(new KeyZoom());
  ModuleManager.register(new Keystrokes());ModuleManager.register(new MotionBlur());ModuleManager.register(new RegionMap());
  ModuleManager.register(new SmartEat());ModuleManager.register(new SpawnerProtect());ModuleManager.register(new SpotifyHud());
  ModuleManager.register(new SpinBot());ModuleManager.register(new SwingSpeed());
 }
}
