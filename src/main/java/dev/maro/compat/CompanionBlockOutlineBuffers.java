package dev.maro.compat;

import dev.maro.Maro;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Optional integration; no companion classes are loaded when the addon is absent. */
public final class CompanionBlockOutlineBuffers {
    private CompanionBlockOutlineBuffers() {}
    private static final ClassValue<MethodHandle> RESERVE = new ClassValue<>() {
        @Override protected MethodHandle computeValue(Class<?> eventType) {
            try {
                var lookup = MethodHandles.publicLookup();
                var renderer = eventType.getField("renderer");
                var triangles = renderer.getType().getField("triangles");
                var mesh = MethodHandles.filterReturnValue(lookup.unreflectGetter(renderer), lookup.unreflectGetter(triangles));
                var ensure = lookup.unreflect(triangles.getType().getMethod("ensureCapacity", int.class, int.class));
                return MethodHandles.filterArguments(ensure, 0, mesh)
                    .asType(MethodType.methodType(void.class, Object.class, int.class, int.class));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unsupported companion block outline renderer", e);
            }
        }
    };
    private static boolean reported;
    public static boolean reserve(Object event) {
        try {
            // Centre + ten outer vertices, ten triangles. The addon omits this reservation,
            // so a fresh mesh writes its first float to address zero.
            RESERVE.get(event.getClass()).invokeExact(event, 11, 30);
            return true;
        } catch (Throwable t) {
            if (!reported) {
                reported = true;
                Maro.LOGGER.error("Skipping incompatible companion block outlines to protect the native vertex buffer", t);
            }
            return false;
        }
    }
}
