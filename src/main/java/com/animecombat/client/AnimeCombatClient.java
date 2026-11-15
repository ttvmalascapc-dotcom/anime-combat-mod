package com.animecombat.client;

import com.animecombat.AnimeCombatMod;
import com.animecombat.core.AbilityDef;
import com.animecombat.core.CharacterDef;
import com.animecombat.core.KeybindProfiles;
import com.animecombat.net.AnimePackets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * Client side: stores the server snapshot and turns the player's per-character keybind profile into
 * "use ability" requests. Inputs are read from the profile, never hardcoded, so rebinding is just data.
 * (The in-game rebinding screen is a later phase; the profile file is created in the config folder.)
 */
public final class AnimeCombatClient implements ClientModInitializer {
    private static final boolean[] DOWN = new boolean[CharacterDef.MAX_ABILITIES];
    private static KeybindProfiles profiles = new KeybindProfiles();

    @Override
    public void onInitializeClient() {
        profiles = loadProfiles();
        ClientPlayNetworking.registerGlobalReceiver(AnimePackets.SyncState.ID, (payload, context) -> ClientCombatState.apply(payload));
        ClientTickEvents.END_CLIENT_TICK.register(AnimeCombatClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientCombatState.clear());
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null || client.currentScreen != null) {
            Arrays.fill(DOWN, false);
            return;
        }
        CharacterDef def = AnimeCombatMod.CHARACTERS.get(ClientCombatState.selectedId()).orElse(null);
        if (def == null) {
            Arrays.fill(DOWN, false);
            return;
        }
        long window = client.getWindow().getHandle();
        List<AbilityDef> abilities = def.abilities();
        for (int i = 0; i < abilities.size(); i++) {
            KeybindProfiles.Input input = profiles.get(def, abilities.get(i).id());
            boolean down = input != null && isDown(window, input);
            if (down && !DOWN[i] && ClientPlayNetworking.canSend(AnimePackets.UseAbility.ID)) {
                ClientPlayNetworking.send(new AnimePackets.UseAbility(abilities.get(i).id()));
            }
            DOWN[i] = down;
        }
    }

    private static boolean isDown(long window, KeybindProfiles.Input input) {
        return switch (input.device()) {
            case KEYBOARD -> GLFW.glfwGetKey(window, input.code()) == GLFW.GLFW_PRESS;
            case MOUSE -> GLFW.glfwGetMouseButton(window, input.code()) == GLFW.GLFW_PRESS;
        };
    }

    private static Path profilePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("animecombat-keybinds.properties");
    }

    private static KeybindProfiles loadProfiles() {
        Path file = profilePath();
        KeybindProfiles loaded;
        if (Files.isRegularFile(file)) {
            Properties p = new Properties();
            try (var reader = Files.newBufferedReader(file)) {
                p.load(reader);
            } catch (IOException e) {
                AnimeCombatMod.LOGGER.warn("Could not read keybinds, using defaults", e);
            }
            loaded = KeybindProfiles.fromProperties(p, AnimeCombatMod.CHARACTERS);
        } else {
            loaded = KeybindProfiles.fromProperties(new Properties(), AnimeCombatMod.CHARACTERS);
            try (var writer = Files.newBufferedWriter(file)) {
                loaded.toProperties(AnimeCombatMod.CHARACTERS).store(writer, "Anime Combat keybinds (GLFW key codes)");
            } catch (IOException e) {
                AnimeCombatMod.LOGGER.warn("Could not write default keybinds", e);
            }
        }
        return loaded;
    }
}
