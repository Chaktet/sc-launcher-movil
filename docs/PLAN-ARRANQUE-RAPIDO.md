# Plan · faster game start on phones (analysis 2026-11-12; phases 0, 1a, 1b, 2a built 12-11)

Felix: «optimizar el arranque del launcher móvil, que no tarde tantísimo las primeras veces, sobre todo en móviles
medio antiguos o con menos GB de RAM». Codex reviewed the complete plan on 12-11 (10 findings, all folded in below).

## Status (12-11)
- **0 + 2a** built in the app, version 0.1.25 (not published: the app publisher is still paused).
- **1a** built: `sc-menus-cliente` branch `perf/pantallas-rapido` (worktree `E:/PROYECTOS-MC/sc-menus-cliente-rapido`,
  one commit on 1.8.1, `Syncer.java` is identical on every branch so it merges anywhere). SyncerTest 26/26; the log
  line on a second sync reads `screens vN up to date (quick check: 65 sampled, …, checked in 0.0 s)`. The app drops the
  stamp on «Reparar archivos» and on every pack install. Reaches phones only when a modpack ships that mod build.
- **1b** built (premise corrected): Cobblemon 1.8.3 `Cobblemon.initialize()` calls `ShowdownThread.launch()`, which
  BLOCKS the main thread until Showdown is up (6 s on the emulator). After that, a client on a server never uses the
  service (species arrive by packet via `PokemonSpecies.reload(Map)`; Moves/Abilities/HeldItems/BagItems call
  `getService()` only in their `SERVER_DATA` reload). So instead of «no Showdown until single-player», launch() only
  starts the thread and `getService()`/`queue()` wait on their own thread. `sc-memoria-cliente` 1.7.0, branch
  `perf/showdown-sin-esperar` (worktree `E:/PROYECTOS-MC/sc-memoria-cliente-showdown`), switch
  `showdown_sin_esperar`. Built, NOT yet run in a client (Claude's test client was busy): before it ships, a start on
  the title screen, a multiplayer battle and a single-player world open/close/reopen.
- **1c dropped (12-11), reason from Loader 0.19.3's code:** production does no runtime mod remap (`processedMods` stays
  empty); the only first-start work is remapping the Minecraft jar into
  `.fabric/remappedJars/minecraft-1.21.1-0.19.3/client-intermediary.jar` (`GameProviderHelper`), reused when the file
  merely EXISTS (no hash). Shipping it is possible (relative path, keyed by MC + Loader version) but: on the emulator
  the whole step took ≤ 1 s («preparing JARs» and «Loading 124 mods» in the same second), it adds the jar to every pack
  download, and a damaged shipped copy would never be rebuilt. Reopen only if SC-47 shows `f`=1 starts with an `l`
  much larger than `f`=0 starts.

## What is known (measured or read in code)
- SC-47 (historial 08-11, 19 players): `u` = 0 min, `t` = 2-13 min, 12 of 19 on high-end phones. `u` is the launcher's
  own preparation (`ScCarga`), `t` runs from the game activity opening to Minecraft's first frame (`ScArranque`).
  **So the launcher is not the slow part: the wait is inside the JVM / Fabric / Minecraft boot.**
- No log of a real phone start exists on this PC. The only mobile game log is the CI emulator's
  (`E:/tmp/movil-errores/evid/emulator-evidence/juego-latest.log`), x86 and cut at 60 s. Every per-phase number below
  comes from it and is a lower bound, not a phone measurement.
- Emulator timeline, 124 mods: Fabric «preparing JARs on first launch»; `[SC Menus] … 10366 files checked in 8.7 s`
  on thread `main`, before the game initialises, on EVERY start (`sc-menus-cliente-movil` `Syncer.java`:188-211: one
  stat per file, sha only when size/mtime changed); `Cobblemon Showdown` service starts on the client (6 s there);
  resource reload reached at +40 s.
- The Móvil modpack already has ModernFix's dynamic model bakery («Dynamic model bakery loading finished»), FerriteCore,
  ImmediatelyFast, BadOptimizations: vanilla block/item models are already loaded on demand. What is still loaded in
  full at every start is Cobblemon's own data: bedrock models, posers, animations and MoLang, on the render thread
  (PC log: «2000000 MoLang strings seen»). Móvil = 6,303 Pokémon models, Lite = 1,623.
- JVM: heap `-Xms` 512 MB growing to `-Xmx` 2-4 GB (`ScMemoria`), no GC chosen (FCL's G1 block is commented out in
  `DefaultLauncher.java`:144-156), no CDS flags. The game folder is on shared storage (`FCLPath.SC_SHARED_ROOT`),
  which Android serves through FUSE (slow for many small files).
- Manifest already has `appCategory="game"`, `isGame`, `largeHeap`.

## Phase 0 · measure on real phones (first, small, app only)
Nothing below is chosen blind. Corrected after Codex's plan review (12-11, 10 findings, all taken):
- **Source: the game's own output, not `latest.log`.** `GameMenu.onLog` already receives every line the JVM prints, in
  the same process (it writes `latest_game.log`). `ScArranque.linea()` checks each chunk with a few `contains` until
  the menu is ready and stamps it with `SystemClock.elapsedRealtime()` since the game activity opened. No file is
  read, no FUSE, no `HH:mm:ss` parsing (log4j has no date or ms), no stale log from the previous run, no midnight or
  clock-change problem. A marker never seen is sent as `x` (unknown), never 0.
- **Keys.** `ScAvisos` reserves `c v p r a` (dropped as extras) and sc-stats only counts the letters in `CAMPOS_PISTA`
  (`sc-launcher/tools/sc-stats.py`:190, max 50 values per code/field/day). SC-47 already uses `t u m g y`. So the new
  keys are free letters of that list (no sc-stats change):
  `l` → «Loading N mods» (JVM + Fabric discovery/remap), `s` → the `[SC Menus]` check's own duration («checked in
  8.7 s», not a timestamp), `k` → first «Reloading ResourceManager» (bootstrap done), `d` → first frame
  (`onGraphicOutput`), `h` → menu ready (ModernFix «Game took N seconds to start»; `x` if it is not printed),
  `e` → «Showdown has been started», `n` → number of «Reloading ResourceManager» up to `h`, `f` = 1 when «preparing
  JARs on first launch» appears. Seconds go in steps (5 s up to 1 min, 20 s up to 5 min, 1 min up to 15 min, then
  900), so each key stays under 50 values.
- **First frame is not the menu** (`JVMActivity` fires `onGraphicOutput` on the second surface update, while
  Minecraft's loading overlay still runs the resource reload). SC-47 is sent at `h`, at game exit, or 10 min after the
  first frame, whichever comes first; `d-k` alone is not read as «Pokémon load time».
- **Sample bias.** SC-47 is still sent only for slow starts (`t`, `u` or `h` ≥ 120 s), so improvements below 2 min
  leave the sample. Before/after is read from the share of players sending SC-47 per app version, the per-phase keys
  of those who still send it, and the `SC arranque: …` line written to the app log for EVERY start (support zip). A
  separate «every start» code would trip the watchdog and needs a monitor change; not now.
- Check: CI emulator evidence (`app-log/fcl.log`) shows the `SC arranque:` line with the SC-47 query and the new keys
  (the emulator does not reach the first frame, so `d`/`h` are `x` there); then 2 days of `stats.json` by `g` (gama).
- Free data today: one «Informe para soporte» zip from Felix's S25 after a cold start, and one from a weak phone.
- Decision table: `l` large → 1c/1d; `s` large → 1a/2b; `h-k` large → phase 3; `n` > 1 → find who triggers the
  second reload (cheapest win of all if it happens).

## Phase 1 · safe quick wins (each one alone, each measured against phase 0)
- **1a. Screens check without touching 10,366 files.** When the published manifest serial+version equals the
  installed stamp and the last install finished cleanly, skip the per-file pass. Full pass only on a new manifest,
  «Reparar archivos», or an unfinished install marker. Repo `sc-menus-cliente-movil`.
  Codex 12-11: a clean stamp does not see files deleted or overwritten later, and «Reparar archivos» today does not
  touch the mod's state. So: a cheap sanity check every start (screens folder present, file count as in the
  manifest), a full pass at least every 7 days, and «Reparar archivos» drops the stamp so the next start is a full pass.
- **1b. No Showdown on the client until a single-player world opens.** Our phones only join the network; the battle
  engine runs on the server. GraalJS without JIT on a phone is slow and heavy (memory `showdown-combates-node`).
  FIRST read Cobblemon 1.8.3: confirm no client code waits for the service when joining a server. If it does, drop
  this item. Mixin in `sc-memoria-cliente`, config switch to turn it off.
  Codex 12-11: «nothing waits for it» is not enough; also check every reader of objects the service initialises
  (client battle UI, stat calculations) and starting it later when a single-player world opens, closes and reopens.
  Needs a multiplayer battle test and a single-player test before it ships.
- **1c. Ship Fabric's first-start work inside the modpack** (`.fabric/remappedJars`, `.fabric/processedMods`), built
  by `movil_auto.js` from a reference start. First start only. FIRST confirm Loader 0.19.3 accepts a shipped cache
  (same relative paths, no absolute path or hash of the device inside).
  Codex 12-11: the cache must be keyed to Minecraft, Loader, mappings and the exact mod set (Móvil/Lite/PRO and every
  pack update) and be rebuilt by the Loader when it does not match; the win is measured as download + unpack + start.
- **1d. JVM flags, A/B on a real phone, keep only what measures better:** `-Xms` near half of `-Xmx` (fewer heap
  resizes while loading); confirm which GC the JVM picks on 4-6 GB phones; confirm the bundled JRE 21 still carries
  `lib/server/classes.jsa` (the JDK's own class cache; stripped JREs lose it). No C1-only (`TieredStopAtLevel=1`):
  faster start, lower FPS for the whole session. No AppCDS for mods (Knot transforms classes; CDS already crashed
  Java on Arclight, memory `arclight-cds-y-arranque`).

## Phase 2 · the wait feels shorter and is explained
- **2a. Real progress on `ScArranque`.** The launcher tails `latest.log` and maps the same markers to stages
  («Cargando 124 mods», «Comprobando pantallas», «Cargando Pokémon», «Abriendo el menú») with a time left learned
  from this phone's previous start (per-phase seconds saved in prefs). The first start says it is the long one.
  Replaces today's «nothing is measurable in this phase» page.
  Codex 12-11: the history is kept per profile (Móvil/Lite/PRO) and pack version, cold (`f`=1) and warm starts apart;
  with no matching history, or once the estimate is passed, the page shows the stage without a countdown. The page
  ends at the first frame (Minecraft's own loading bar takes over there).
- **2b. Game data on app-private storage** (no FUSE) only if phase 0 shows `a`/`b` dominated by file access after 1a.
  Needs a one-time migration of existing installs and touches «Informe para soporte» and screenshots: medium risk.

## Phase 3 · structural: Pokémon assets on demand (only if phase 0 shows the reload dominates)
The idea that removes the cost instead of hiding it, and also the memory problem behind Móvil Lite:
- **Build time** (`movil_auto.js`, OVH-A): write `sc_indice.json` into the mobile pack: species/aspect → its model,
  poser and animation files. The pack keeps every file; only the index is new.
- **Client** (`sc-memoria-cliente`): Cobblemon's repositories read the index at reload and register lazy entries
  instead of parsing 6,303 models. First time a species is drawn its files are parsed on a worker thread; until then
  the substitute model shows. A small LRU frees species not seen for a while on low-RAM phones (limit from `gama`).
- Effect: reload time and heap scale with the Pokémon on screen, not with the pack. Lite could become unnecessary.
- Risk: touches Cobblemon internals (every Cobblemon update can break it) → one config switch falls back to the full
  load, and the mod logs which path it took so SC-47 can report it. Money/saves untouched; Codex review of the diff.
- **3b, cheaper alternative, Felix decides:** «Lite first»: weak and medium phones install Lite for the first
  session (16 MB, fast) and the full Móvil pack downloads in the background for the next start. Cost: no Pokémon
  skins in the first session.

## Done means
- SC-47 median `t` per gama, before vs after, from `stats.json` (same app version range), plus one cold start on
  Felix's S25 and on one weak phone, read from the support zip. Targets to confirm with Felix after phase 0.
- App changes go out as one version (0.1.25: phase 0 + 2a), modpack/mod changes need no app version.
- Blocker to remember: 0.1.24 is built but not published and the `app_movil.js` cron is paused (historial 29-10).
