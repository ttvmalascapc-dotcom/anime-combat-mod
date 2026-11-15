package com.animecombat.net;

import com.animecombat.AnimeCombatMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class AnimePackets {
    private AnimePackets() {}

    /** Client -> server: "I pressed the key for this ability". The server decides if it happens. */
    public record UseAbility(String abilityId) implements CustomPayload {
        public static final CustomPayload.Id<UseAbility> ID =
                new CustomPayload.Id<>(Identifier.of(AnimeCombatMod.MOD_ID, "use_ability"));
        public static final PacketCodec<RegistryByteBuf, UseAbility> CODEC =
                PacketCodec.tuple(PacketCodecs.string(64), UseAbility::abilityId, UseAbility::new);

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * Server -> client: authoritative state snapshot for the HUD.
     * cooldowns is "ability:remainingTicks;ability:remainingTicks;" for the selected character.
     */
    public record SyncState(String selectedId, int energy, int maxEnergy, String cooldowns, int transformTicks)
            implements CustomPayload {
        public static final CustomPayload.Id<SyncState> ID =
                new CustomPayload.Id<>(Identifier.of(AnimeCombatMod.MOD_ID, "sync_state"));
        public static final PacketCodec<RegistryByteBuf, SyncState> CODEC = PacketCodec.tuple(
                PacketCodecs.string(64), SyncState::selectedId,
                PacketCodecs.VAR_INT, SyncState::energy,
                PacketCodecs.VAR_INT, SyncState::maxEnergy,
                PacketCodecs.string(1024), SyncState::cooldowns,
                PacketCodecs.VAR_INT, SyncState::transformTicks,
                SyncState::new);

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(UseAbility.ID, UseAbility.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncState.ID, SyncState.CODEC);
    }
}
