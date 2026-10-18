package dev.maro.chunkpulse;

import dev.maro.module.impl.visuals.RedstoneBypass;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

public final class PulseCommands {
    public static void init(){
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(literal("chunkpulse")
            .executes(ctx->{module().list();return 1;})
            .then(literal("toggle").executes(ctx->{module().toggle();return 1;}))
            .then(literal("list").executes(ctx->{module().list();return 1;}))
            .then(literal("clear").executes(ctx->{module().clear();return 1;}))
            .then(literal("export").executes(ctx->{module().export(false);return 1;})
                .then(literal("waypoints").executes(ctx->{module().export(true);return 1;})))
            .then(literal("threshold").then(argument("tier",StringArgumentType.word())
                .suggests((ctx,builder)->{for(String tier:new String[]{"low","medium","high","huge"})builder.suggest(tier);return builder.buildFuture();})
                .then(argument("value",DoubleArgumentType.doubleArg(1,100000)).executes(ctx->{module().threshold(StringArgumentType.getString(ctx,"tier"),DoubleArgumentType.getDouble(ctx,"value"));return 1;}))))
            .then(literal("debug").executes(ctx->{module().debugToggle();return 1;}))));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->{
            var root=literal("chunkpulse");
            root.then(literal("plane").then(argument("mode",StringArgumentType.word()).suggests((ctx,b)->{for(var p:PulseConfig.Plane.values())b.suggest(p.name());return b.buildFuture();}).executes(ctx->{module().plane(StringArgumentType.getString(ctx,"mode"));return 1;})));
            for(String name:new String[]{"see-through","labels","history"})root.then(literal(name).then(literal("on").executes(ctx->{module().display(name,true);return 1;})).then(literal("off").executes(ctx->{module().display(name,false);return 1;})));
            for(String name:new String[]{"found","unfound"})root.then(literal(name).executes(ctx->{module().found(null,name.equals("found"));return 1;}).then(argument("id",StringArgumentType.word()).suggests((ctx,b)->{for(var view:module().discovery().views())b.suggest(view.id());return b.buildFuture();}).executes(ctx->{module().found(StringArgumentType.getString(ctx,"id"),name.equals("found"));return 1;})));
            root.then(literal("signal").then(literal("on").executes(ctx->{module().signal(true);return 1;})).then(literal("off").executes(ctx->{module().signal(false);return 1;})));
            root.then(literal("clusters").executes(ctx->{module().clusterList();return 1;}));
            dispatcher.register(root);
        });
    }
    private static RedstoneBypass module(){return RedstoneBypass.instance();}
    private PulseCommands(){ }
}
