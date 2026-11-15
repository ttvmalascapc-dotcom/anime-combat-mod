package com.animecombat;

import com.animecombat.core.CharacterRegistry;
import com.animecombat.net.AnimePackets;
import com.animecombat.server.AnimeCommands;
import com.animecombat.server.ServerCombat;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AnimeCombatMod implements ModInitializer {
    public static final String MOD_ID = "animecombat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    /** Shared by client and server: both sides read the same character definitions. */
    public static final CharacterRegistry CHARACTERS = CharacterRegistry.createDefault();

    @Override
    public void onInitialize() {
        AnimePackets.registerPayloads();
        ServerCombat.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                AnimeCommands.register(dispatcher));
        LOGGER.info("Anime Combat loaded with {} characters", CHARACTERS.all().size());
    }
}
