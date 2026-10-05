package dev.maro.render.esp;

import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;

import java.util.function.Supplier;

/**
 * The vertex plumbing behind the Player ESP's silhouettes. Everything is routed into the game's
 * outline render layers, which draw flat colour into whatever the entity outline framebuffer is at
 * the time, and everything else (lighting, overlays, normals) is thrown away. Adapted from Meteor
 * Client (GPL-3.0).
 */
public final class SilhouetteBuffers {
    private SilhouetteBuffers() {
    }

    /** Collects silhouette geometry for one frame; {@link #draw()} flushes it into the outline target. */
    public static final class Provider implements VertexConsumerProvider {
        private final VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(new BufferAllocator(1536));

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            if (layer.isOutline()) return new Flat(immediate.getBuffer(layer));

            var outline = layer.getAffectedOutline();
            if (outline.isPresent()) return new Flat(immediate.getBuffer(outline.get()));

            return Noop.INSTANCE;
        }

        public void draw() {
            immediate.draw();
        }
    }

    /** Hands render dispatcher calls to whichever provider is current. */
    public static final class Forwarding extends VertexConsumerProvider.Immediate {
        private final Supplier<VertexConsumerProvider> target;

        public Forwarding(Supplier<VertexConsumerProvider> target) {
            super(null, null);
            this.target = target;
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            VertexConsumerProvider provider = target.get();
            return provider == null ? Noop.INSTANCE : provider.getBuffer(layer);
        }

        @Override
        public void draw() {
        }

        @Override
        public void draw(RenderLayer layer) {
        }
    }

    public static final class NoopImmediate extends VertexConsumerProvider.Immediate {
        public static final NoopImmediate INSTANCE = new NoopImmediate();

        private NoopImmediate() {
            super(null, null);
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            return Noop.INSTANCE;
        }

        @Override
        public void draw() {
        }

        @Override
        public void draw(RenderLayer layer) {
        }
    }

    public static final class NoopOutline extends OutlineVertexConsumerProvider {
        public static final NoopOutline INSTANCE = new NoopOutline();

        private NoopOutline() {
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            return Noop.INSTANCE;
        }

        @Override
        public void draw() {
        }
    }

    /** Keeps position, colour and texture (the outline shader alpha-tests the texture); drops the rest. */
    private record Flat(VertexConsumer consumer) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            consumer.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            consumer.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer color(int argb) {
            consumer.color(argb);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            consumer.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer lineWidth(float width) {
            return this;
        }
    }

    private static final class Noop implements VertexConsumer {
        static final Noop INSTANCE = new Noop();

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer color(int argb) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer lineWidth(float width) {
            return this;
        }
    }
}
