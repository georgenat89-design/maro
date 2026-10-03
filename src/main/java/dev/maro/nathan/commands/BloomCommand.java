package dev.maro.nathan.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import dev.maro.runtime.commands.Command;
import dev.maro.runtime.systems.modules.Modules;
import net.minecraft.command.CommandSource;
import dev.maro.nathan.modules.Bloom;

/**
 * {@code /bloom debug} - dumps every stage of the Bloom chain to PNG.
 *
 * <p>Exists so that a report of "bloom does not work" can be answered by looking
 * at which stage first goes wrong, instead of by changing things until it does.
 * The files land in {@code .minecraft/bloom/debug} and are numbered in pipeline
 * order, so sorting them by name is reading them in the order they were drawn.
 */
public class BloomCommand extends Command {
    public BloomCommand() {
        super("bloom", "Dumps every stage of the Bloom post chain to .minecraft/bloom/debug as PNGs.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("debug").executes(context -> {
            Bloom bloom = Modules.get().get(Bloom.class);

            if (bloom == null) {
                error("Bloom is not registered.");
            } else if (!bloom.isActive()) {
                // Nothing to dump: the chain is only built while the module runs,
                // so the targets would not exist to read.
                warning("Bloom is off. Turn it on, look at something bright, then run this again.");
            } else {
                bloom.requestDebugDump();
            }

            return SINGLE_SUCCESS;
        }));
    }
}
