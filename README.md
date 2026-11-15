# Anime Combat (Fabric, Minecraft 1.21.11)

Anime fighting-game combat for Minecraft. Current state: see `CLAUDE_PROGRESS.md`.

## Getting the built mod (no tools to install)
1. Push this project to a GitHub repository (the workflow in `.github/workflows/build.yml` runs automatically).
2. Open the repository's **Actions** tab, open the latest run, and download **anime-combat-mod-jar-N** from *Artifacts*.
3. Unzip the download and put the `.jar` in your `mods` folder together with Fabric API.

You can also start a build manually: Actions -> *Build Anime Combat Mod* -> *Run workflow*.
If a build fails, download the **build-logs-N** artifact (or copy the red error lines) and send it back.

## In game (current build)
- `/anime list`, `/anime select <character>`, `/anime info`
- Default ability keys are in `config/animecombat-keybinds.properties` (created on first launch). Slot 1-8 default to R, Z, X, C, V, G, B, N.
