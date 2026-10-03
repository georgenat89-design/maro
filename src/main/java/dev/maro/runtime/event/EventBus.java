package dev.maro.runtime.event;

import java.lang.reflect.*;
import java.util.*;
import dev.maro.Maro;
/** Main-thread dispatcher for the ported modules. Disabled modules have no listeners. */
public final class EventBus {
    private record Listener(Object owner, Method method, Class<?> type, int priority) { }
    private final List<Listener> listeners = new ArrayList<>();
    private long failures;
    public long failureCount() { return failures; }
    public void subscribe(Object owner) {
        unsubscribe(owner);
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                EventHandler annotation = method.getAnnotation(EventHandler.class);
                if (annotation == null || method.getParameterCount() != 1) continue;
                method.setAccessible(true);
                listeners.add(new Listener(owner, method, method.getParameterTypes()[0], annotation.priority()));
            }
        }
        listeners.sort(Comparator.comparingInt(Listener::priority).reversed());
    }
    public void unsubscribe(Object owner) { listeners.removeIf(l -> l.owner == owner); }
    public <T> T post(T event) {
        for (Listener l : List.copyOf(listeners)) {
            if (!l.type.isInstance(event)) continue;
            try { l.method.invoke(l.owner, event); }
            catch (ReflectiveOperationException e) { failures++; Maro.LOGGER.error("Module event {} failed", l.method, e.getCause() == null ? e : e.getCause()); }
        }
        return event;
    }
}
