package com.animecombat.server;

import com.animecombat.AnimeCombatMod;
import com.animecombat.core.AbilityDef;
import com.animecombat.core.CharacterDef;
import com.animecombat.core.PlayerCombatState;
import com.animecombat.net.AnimePackets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

/** Server-authoritative combat: validates every ability request, owns all state, persists it. */
public final class ServerCombat {
    private ServerCombat() {}

    private static final int AUTOSAVE_TICKS = 6000;
    private static final Map<UUID, PlayerCombatState> STATES = new HashMap<>();
    private static final Map<UUID, Integer> LAST_SYNCED_ENERGY = new HashMap<>();

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            STATES.put(player.getUuid(), load(server, player.getUuid()));
            if (state(player).selectedId() == null) {
                player.sendMessage(Text.literal("Anime Combat: use /anime list and /anime select <character>"), false);
            }
            sync(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            PlayerCombatState st = STATES.remove(player.getUuid());
            LAST_SYNCED_ENERGY.remove(player.getUuid());
            if (st != null) save(server, player.getUuid(), st);
        });

        ServerPlayNetworking.registerGlobalReceiver(AnimePackets.UseAbility.ID, (payload, context) ->
                handleUse(context.player(), payload.abilityId()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int tick = server.getTicks();
            if (tick % 20 == 0) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) syncIfEnergyChanged(p);
            }
            if (tick % AUTOSAVE_TICKS == 0) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    PlayerCombatState st = STATES.get(p.getUuid());
                    if (st != null) save(server, p.getUuid(), st);
                }
            }
        });

        // Guard abilities (e.g. Substitution Jutsu) negate damage, except void/kill-type damage.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                PlayerCombatState st = STATES.get(player.getUuid());
                if (st != null && st.isGuarding(now(player))) return false;
            }
            return true;
        });
    }

    // ---- public API used by commands ------------------------------------------------

    public static PlayerCombatState state(ServerPlayerEntity player) {
        return STATES.computeIfAbsent(player.getUuid(), id -> PlayerCombatState.fresh(AnimeCombatMod.CHARACTERS));
    }

    public static boolean select(ServerPlayerEntity player, CharacterDef def) {
        boolean ok = state(player).select(def, now(player));
        if (ok) sync(player);
        return ok;
    }

    public static long now(ServerPlayerEntity player) {
        return ((ServerWorld) player.getEntityWorld()).getServer().getTicks();
    }

    // ---- ability handling -------------------------------------------------------------

    private static void handleUse(ServerPlayerEntity player, String abilityId) {
        if (!player.isAlive() || player.isSpectator()) return;
        PlayerCombatState st = state(player);
        String charId = st.selectedId();
        CharacterDef def = charId == null ? null : AnimeCombatMod.CHARACTERS.get(charId).orElse(null);
        if (def == null) {
            player.sendMessage(Text.literal("Select a character first: /anime select <character>"), true);
            return;
        }
        long now = now(player);
        double mult = st.damageMultiplier(now);
        PlayerCombatState.UseResult result = st.tryUse(def, abilityId, now);
        switch (result.status()) {
            case OK -> {
                AbilityDef ability = result.ability();
                AbilityExecutor.execute(player, ability, mult);
                sync(player);
            }
            case NOT_ENOUGH_ENERGY ->
                    player.sendMessage(Text.literal("Not enough " + def.resource().displayName()), true);
            default -> { /* cooldowns, lockout, unknown ids: ignored silently (the HUD shows cooldowns) */ }
        }
    }

    // ---- sync ---------------------------------------------------------------------------

    private static void syncIfEnergyChanged(ServerPlayerEntity player) {
        PlayerCombatState st = STATES.get(player.getUuid());
        if (st == null || st.selectedId() == null) return;
        CharacterDef def = AnimeCombatMod.CHARACTERS.get(st.selectedId()).orElse(null);
        if (def == null) return;
        int energy = (int) st.energy(def, now(player));
        Integer last = LAST_SYNCED_ENERGY.get(player.getUuid());
        if (last == null || last != energy) sync(player);
    }

    public static void sync(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, AnimePackets.SyncState.ID)) return; // client without the mod
        PlayerCombatState st = state(player);
        long now = now(player);
        CharacterDef def = st.selectedId() == null ? null : AnimeCombatMod.CHARACTERS.get(st.selectedId()).orElse(null);
        String id = "";
        int energy = 0, max = 0;
        StringBuilder cooldowns = new StringBuilder();
        if (def != null) {
            id = def.id();
            energy = (int) st.energy(def, now);
            max = def.maxEnergy();
            for (AbilityDef a : def.abilities()) {
                int rem = st.cooldownRemaining(def.id(), a.id(), now);
                if (rem > 0) cooldowns.append(a.id()).append(':').append(rem).append(';');
            }
        }
        LAST_SYNCED_ENERGY.put(player.getUuid(), energy);
        ServerPlayNetworking.send(player, new AnimePackets.SyncState(id, energy, max, cooldowns.toString(), st.transformRemaining(now)));
    }

    // ---- persistence --------------------------------------------------------------------

    private static Path dir(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("animecombat");
    }

    private static PlayerCombatState load(MinecraftServer server, UUID id) {
        Path file = dir(server).resolve(id + ".properties");
        if (Files.isRegularFile(file)) {
            Properties p = new Properties();
            try (var reader = Files.newBufferedReader(file)) {
                p.load(reader);
                return PlayerCombatState.fromProperties(p, AnimeCombatMod.CHARACTERS);
            } catch (IOException | RuntimeException e) {
                AnimeCombatMod.LOGGER.warn("Could not read combat data for {}, starting fresh", id, e);
            }
        }
        return PlayerCombatState.fresh(AnimeCombatMod.CHARACTERS);
    }

    private static void save(MinecraftServer server, UUID id, PlayerCombatState st) {
        try {
            Path d = dir(server);
            Files.createDirectories(d);
            try (var writer = Files.newBufferedWriter(d.resolve(id + ".properties"))) {
                st.toProperties(0).store(writer, "Anime Combat player data");
            }
        } catch (IOException e) {
            AnimeCombatMod.LOGGER.warn("Could not save combat data for {}", id, e);
        }
    }
}
