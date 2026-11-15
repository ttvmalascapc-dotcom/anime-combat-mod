package com.animecombat.server;

import com.animecombat.AnimeCombatMod;
import com.animecombat.core.AbilityDef;
import com.animecombat.core.CharacterDef;
import com.animecombat.core.PlayerCombatState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Player commands. No operator permission is needed for normal play (admin commands come in a later phase). */
public final class AnimeCommands {
    private AnimeCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("anime")
                .then(CommandManager.literal("list").executes(AnimeCommands::list))
                .then(CommandManager.literal("info").executes(AnimeCommands::info))
                .then(CommandManager.literal("select")
                        .then(CommandManager.argument("character", StringArgumentType.word())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(AnimeCombatMod.CHARACTERS.ids(), builder))
                                .executes(AnimeCommands::select))));
    }

    private static int list(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        PlayerCombatState st = ServerCombat.state(player);
        for (CharacterDef d : AnimeCombatMod.CHARACTERS.all()) {
            String line = d.id() + " - " + d.displayName() + " (" + d.franchise().displayName() + ")"
                    + (st.isUnlocked(d.id()) ? "" : " [locked]")
                    + (d.id().equals(st.selectedId()) ? " <selected>" : "");
            ctx.getSource().sendFeedback(() -> Text.literal(line), false);
        }
        return 1;
    }

    private static int info(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        PlayerCombatState st = ServerCombat.state(player);
        CharacterDef def = st.selectedId() == null ? null : AnimeCombatMod.CHARACTERS.get(st.selectedId()).orElse(null);
        if (def == null) {
            ctx.getSource().sendError(Text.literal("No character selected. Use /anime select <character>"));
            return 0;
        }
        long now = ServerCombat.now(player);
        ctx.getSource().sendFeedback(() -> Text.literal(def.displayName() + " - " + (int) st.energy(def, now) + "/"
                + def.maxEnergy() + " " + def.resource().displayName()), false);
        int slot = 1;
        for (AbilityDef a : def.abilities()) {
            String line = slot++ + ". " + a.displayName() + " [" + a.kind() + "] cost " + a.energyCost()
                    + ", cooldown " + String.format("%.1fs", a.cooldownTicks() / 20.0) + " - " + a.description();
            ctx.getSource().sendFeedback(() -> Text.literal(line), false);
        }
        return 1;
    }

    private static int select(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        String id = StringArgumentType.getString(ctx, "character");
        CharacterDef def = AnimeCombatMod.CHARACTERS.get(id).orElse(null);
        if (def == null) {
            ctx.getSource().sendError(Text.literal("Unknown character: " + id));
            return 0;
        }
        if (!ServerCombat.select(player, def)) {
            ctx.getSource().sendError(Text.literal(def.displayName() + " is not unlocked."));
            return 0;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("You are now " + def.displayName()), false);
        return 1;
    }
}
