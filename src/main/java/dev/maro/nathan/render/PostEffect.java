package dev.maro.nathan.render;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPipeline;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.UniformValue;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.util.Identifier;
import org.joml.Vector4f;

import dev.maro.nathan.NameeProtectAddon;

/**
 * Owns one post-processing chain: builds it, keeps it until the settings behind
 * it move, and runs it over the frame.
 *
 * <p>The chain is built in code rather than loaded from a {@code post_effect}
 * JSON. A post effect's uniforms are baked in when it loads, so from a JSON
 * every slider would be dead - the value the file was written with is the value
 * it keeps. Building it here means a slider can actually do something, at the
 * cost of rebuilding when one moves, which is a handful of times while somebody
 * drags and never again.
 *
 * <p>Shared rather than written twice because when one of these turns out to
 * need a correction on real hardware, there should be one place to correct.
 */
public final class PostEffect {
    /** Where the game's own frame lives, and where a pass writes it back to. */
    public static final Identifier MAIN = PostEffectProcessor.MAIN;

    private static final Identifier SCREEN_QUAD = Identifier.ofVanilla("core/screenquad");
    private static final Identifier BLIT = Identifier.ofVanilla("post/blit");

    private final Identifier id;
    private final String name;

    private PostEffectProcessor chain;
    private ProjectionMatrix2 matrixBuffer;

    /** What the chain was built from, so it is only rebuilt when that moves. */
    private String builtFrom;

    /**
     * Set when a shader will not compile or a chain will not load. Without it
     * the failure would be retried every frame, which turns one line in the log
     * into thousands.
     */
    private boolean broken;

    public PostEffect(String name) {
        this.name = name;
        this.id = ours(name);
    }

    /** An identifier in this addon's namespace. */
    public static Identifier ours(String path) {
        return Identifier.of("nameeprotect", path);
    }

    /** A target of this addon's own, to bounce a frame through. */
    public static Map<Identifier, PostEffectPipeline.Targets> target(String name, boolean persistent) {
        return Map.of(ours(name), new PostEffectPipeline.Targets(Optional.empty(), Optional.empty(), persistent, 0));
    }

    /** Reads one target, writes another, through one of our fragment shaders. */
    public static PostEffectPipeline.Pass pass(String fragment, List<PostEffectPipeline.Input> inputs,
                                            Identifier output, String block, List<UniformValue> uniforms) {
        return new PostEffectPipeline.Pass(SCREEN_QUAD, ours("post/" + fragment), inputs, output, Map.of(block, uniforms));
    }

    /** Copies one target onto another through the game's own blit shader. */
    public static PostEffectPipeline.Pass copy(Identifier from, Identifier to) {
        return new PostEffectPipeline.Pass(
            SCREEN_QUAD,
            BLIT,
            List.of(input("In", from)),
            to,
            Map.of("BlitConfig", List.of(new UniformValue.Vec4fValue(new Vector4f(1, 1, 1, 1)))));
    }

    /** A sampler in a shader: {@code name} here is {@code nameSampler} there. */
    public static PostEffectPipeline.Input input(String sampler, Identifier target) {
        return new PostEffectPipeline.TargetSampler(sampler, target, false, false);
    }

    /**
     * Runs the effect over {@code target}, building or rebuilding the chain
     * first if {@code key} has changed since it was last built.
     *
     * @return false when the effect could not run and has given up on itself.
     *         Nothing is thrown: this is called from inside the frame, and an
     *         exception escaping here would take the whole renderer down rather
     *         than one effect.
     */
    public boolean run(Framebuffer target, ObjectAllocator allocator,
                       String key, Supplier<PostEffectPipeline> config) {
        if (!prepare(key, config)) return false;

        try {
            chain.render(target, allocator);
            return true;
        } catch (Exception e) {
            broken = true;
            discard();
            NameeProtectAddon.LOG.error("nameeprotect could not run the {} post chain", name, e);

            return false;
        }
    }

    /** Builds the chain if it is not built, or was built from something else. */
    public boolean prepare(String key, Supplier<PostEffectPipeline> config) {
        if (broken) return false;

        try {
            if (chain == null || !key.equals(builtFrom)) {
                discard();

                if (matrixBuffer == null) matrixBuffer = new ProjectionMatrix2("nameeprotect " + name, 0.1F, 1000.0F, false);

                chain = PostEffectProcessor.parseEffect(
                    config.get(),
                    MinecraftClient.getInstance().getTextureManager(),
                    DefaultFramebufferSet.MAIN_ONLY,
                    id,
                    matrixBuffer);

                builtFrom = key;
            }

            return true;
        } catch (Exception e) {
            broken = true;
            discard();
            NameeProtectAddon.LOG.error("nameeprotect could not build the {} post chain", name, e);

            return false;
        }
    }

    /** The chain as built, or null. For whoever needs to reach into it between runs. */
    public PostEffectProcessor chain() {
        return chain;
    }

    /** Lets a broken effect be tried again, for when the module is toggled. */
    public void reset() {
        broken = false;
        discard();
    }

    public void discard() {
        if (chain != null) {
            chain.close();
            chain = null;
        }

        builtFrom = null;
    }
}
