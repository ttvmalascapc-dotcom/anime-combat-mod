# Anime Combat Mod - progress handoff

Target: Minecraft Java 1.21.11, Fabric, Java 21, Yarn mappings (versions of loader/yarn/Fabric API are resolved by CI).
Build: GitHub Actions only (`.github/workflows/build.yml`). The user does NOT build locally and does not want to.
Dev sandbox has no network and no Gradle: only the pure-Java core (`com.animecombat.core`) can be compiled/tested there
(`java -m jdk.compiler/com.sun.tools.javac.Main -d /tmp/out $(find src/main/java/com/animecombat/core src/coretest -name '*.java')`
then `java -cp /tmp/out com.animecombat.core.CoreTests`; note `--release` is unsupported in that JRE).

## Status
- NOT YET CONFIRMED TO COMPILE. No GitHub Actions build has run. The Minecraft-facing code was written from memory
  against the 1.21.11 Yarn API and has never been compiled. Treat the first CI run as the real test.
- Core logic: compiled and passing 53 checks locally.

## Done
- Phase 1-3: project/Gradle/Loom setup, CI workflow, character + ability framework (data model, registry, validation).
- Phase 4: Naruto and Goku fully defined (7 abilities each, transformation + ultimate, themes, energy stats).
- Server-authoritative combat: PlayerCombatState (energy regen, cooldowns, global lockout, transform multiplier,
  guard, selection/unlock rules, persistence), AbilityExecutor (melee arc, beam, burst, dash, guard, buff),
  ServerCombat (validation, sync, per-player save files in world/animecombat/, damage-negation for guard).
- Networking: UseAbility (C2S, string max 64) and SyncState (S2C, only sent to clients that have the mod).
- Commands: /anime list | info | select <id> (no OP needed).
- Client: per-character keybind profiles polled via GLFW (core KeybindProfiles: conflicts, override, reset,
  mouse buttons, persistence), file `config/animecombat-keybinds.properties`.

## Not done (nothing below exists yet)
- Characters: Sasuke, Vegeta, Luffy, Zoro, Ichigo, Aizen.
- Models/textures/animations/player model replacement, custom projectiles, SFX, cinematic ultimates.
- HUD (hotbar, cooldowns, energy, themes), character-select GUI, settings GUI, in-game keybind screen, HUD layouts.
- VFX quality/performance settings (current particles are server-sent vanilla particles; move to client-side effects).
- Admin/permission commands (1.21.11 changed permission APIs: check before writing), combo/dodge/block mechanics,
  progression/unlocking, lang file, mod icon, dedicated-server and multiplayer testing.

## Highest-risk API guesses (fix first if CI fails)
- `Entity.getEntityWorld()`, `Entity.getEntityPos()`, `Entity.velocityModified`, `LivingEntity.damage(ServerWorld, DamageSource, float)`
- `ServerPlayNetworking.canSend(player, id)`, `ClientPlayNetworking.canSend(id)`, `ServerLivingEntityEvents.ALLOW_DAMAGE` signature
- `MinecraftClient.getWindow().getHandle()`, `ServerPlayerEntity.getServer` replaced by `ServerWorld.getServer()`
- Loom `1.14-SNAPSHOT`, Gradle `9.2.1` in the workflow.

## Next steps (in order)
1. Get the first green CI build; fix compile errors from the log.
2. Client HUD (energy + cooldowns from ClientCombatState), then character theme system.
3. Remaining six characters, then combos/block/dodge.
4. Admin commands with permission checks, then GUIs, settings, performance presets, models/animations.

## Continuation prompt (paste into a new chat with this ZIP attached)
"Continue the Fabric 1.21.11 anime combat mod in the attached ZIP. Read CLAUDE_PROGRESS.md first, inspect the existing
code, preserve all working systems, do not restart or duplicate anything. I never build locally; builds happen in GitHub
Actions. If I paste a failed build log, fix those errors first. Then continue with the 'Next steps' list. Never claim
the mod compiles or works unless a successful Actions build confirms it."
