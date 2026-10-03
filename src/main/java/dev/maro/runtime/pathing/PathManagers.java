package dev.maro.runtime.pathing;

import net.minecraft.util.math.BlockPos;
import java.lang.reflect.*;
/** Optional Baritone adapter. Without Baritone, Spawner Protect keeps its manual/reachable-block path. */
public final class PathManagers {
 private static final PathManagers INSTANCE=new PathManagers();public static PathManagers get(){return INSTANCE;}
 private Object primary(){try{var api=Class.forName("baritone.api.BaritoneAPI");Object provider=api.getMethod("getProvider").invoke(null);return Class.forName("baritone.api.IBaritoneProvider").getMethod("getPrimaryBaritone").invoke(provider);}catch(ReflectiveOperationException|LinkageError e){return null;}}
 public String getName(){return primary()==null?"none":"Baritone";}
 public void moveTo(BlockPos pos,boolean ignore){Object primary=primary();if(primary==null)return;try{Object process=Class.forName("baritone.api.IBaritone").getMethod("getCustomGoalProcess").invoke(primary);Object goal=Class.forName("baritone.api.pathing.goals.GoalNear").getConstructor(BlockPos.class,int.class).newInstance(pos,1);Class.forName("baritone.api.process.ICustomGoalProcess").getMethod("setGoalAndPath",Class.forName("baritone.api.pathing.goals.Goal")).invoke(process,goal);}catch(ReflectiveOperationException|LinkageError e){dev.maro.Maro.LOGGER.warn("Could not start Baritone path",e);}}
 public boolean isPathing(){Object primary=primary();if(primary==null)return false;try{Object behavior=Class.forName("baritone.api.IBaritone").getMethod("getPathingBehavior").invoke(primary);return (boolean)Class.forName("baritone.api.behavior.IPathingBehavior").getMethod("isPathing").invoke(behavior);}catch(ReflectiveOperationException|LinkageError e){return false;}}
 public void stop(){Object primary=primary();if(primary==null)return;try{Object behavior=Class.forName("baritone.api.IBaritone").getMethod("getPathingBehavior").invoke(primary);Class.forName("baritone.api.behavior.IPathingBehavior").getMethod("cancelEverything").invoke(behavior);}catch(ReflectiveOperationException|LinkageError e){dev.maro.Maro.LOGGER.debug("Could not stop Baritone path",e);}}
}
