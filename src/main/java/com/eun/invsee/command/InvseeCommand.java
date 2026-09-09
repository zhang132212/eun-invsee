package com.eun.invsee.command;

import com.eun.invsee.data.UserNameCache;
import com.eun.invsee.service.BoxOpener;
import com.eun.invsee.service.InvseeService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * /invsee <玩家> [页码]  查看在线/离线玩家背包（只读，OP≥2）
 * /invsee box <玩家> <位置码>  打开某格容器(潜影盒等)的只读内容界面
 */
public final class InvseeCommand {

    private InvseeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("invsee")
                .requires(src -> src.permissions().hasPermission(
                        new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                .executes(ctx -> {
                    InvseeService.execute(ctx.getSource(), null, 1);
                    return 1;
                })
                .then(boxSub())
                .then(playerArg());
        dispatcher.register(root);
    }

    private static final SuggestionProvider<CommandSourceStack> SUGGEST = InvseeCommand::suggestPlayers;

    private static LiteralArgumentBuilder<CommandSourceStack> boxSub() {
        return Commands.literal("box")
                .then(Commands.argument("target", StringArgumentType.string())
                        .suggests(SUGGEST)
                        .then(Commands.argument("code", StringArgumentType.word())
                                .executes(ctx -> BoxOpener.execute(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "target"),
                                        StringArgumentType.getString(ctx, "code")))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> playerArg() {
        return Commands.argument("player", StringArgumentType.string())
                .suggests(SUGGEST)
                .executes(ctx -> InvseeService.execute(ctx.getSource(),
                        StringArgumentType.getString(ctx, "player"), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1, 10000))
                        .executes(ctx -> InvseeService.execute(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                IntegerArgumentType.getInteger(ctx, "page"))));
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx,
                                                                 SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase();
        Set<String> seen = new LinkedHashSet<>();
        MinecraftServer server = ctx.getSource().getServer();
        for (String n : server.getPlayerList().getPlayerNamesArray()) {
            if (n.toLowerCase().startsWith(typed)) {
                seen.add(n);
            }
        }
        for (NameAndId n : UserNameCache.snapshot(server)) {
            if (n.name().toLowerCase().startsWith(typed)) {
                seen.add(n.name());
                if (seen.size() >= 60) {
                    break;
                }
            }
        }
        for (String s : seen) {
            builder.suggest(s);
        }
        return builder.buildFuture();
    }
}
