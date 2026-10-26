# SC Launcher Móvil · Android launcher for servidorcobblemon.es

> Fork of [FoldCraftLauncher](https://github.com/FCL-Team/FoldCraftLauncher) (GPL-3.0). Public repo
> `Chaktet/sc-launcher-movil`, upstream remote `upstream` = `FCL-Team/FoldCraftLauncher`.
> Plan and phase status: `E:/PROYECTOS-MC/sc-launcher/docs/PLAN-APP-ANDROID.md` (the master doc; keep it current).
> Shared network rules: `E:/PROYECTOS-MC/CLAUDE.md`. Shared state between sessions: `historial.md` (this folder).

## On session start
1. Read the top of `historial.md` ("Estado actual" + newest entries).
2. `git status`, `git log -10`.
3. Read the plan doc's phase table.

## Hard rules
- **Never build on this PC.** Builds run only on GitHub Actions (public repo, free). If something local is unavoidable,
  everything heavy (Gradle, Android SDK, emulator) goes to `E:\tmp\app-android\` with `TMP`, `TEMP` and
  `GRADLE_USER_HOME` on E:. Never C:.
- **Signing key:** release builds are signed ONLY with our key (`SC_KEYSTORE_BASE64` + `SC_KEYSTORE_PASSWORD` GitHub
  secrets, alias `sc-launcher`). Backup outside git: `E:\SECRETOS\sc-launcher-movil\` (`sc-release.jks` + `clave.txt`),
  which Felix also keeps a copy of. Losing it = no player can update the app. Never commit a keystore or print the
  password. FCL's own `key-store.jks` / `debug-key.jks` / `private_key.pepk` are not used for our builds.
  Certificate SHA-256: `ED:15:8C:CB:FB:4C:2A:DC:E8:48:F8:1B:A8:7F:21:3B:29:BB:4E:EF:85:CE:33:64:E6:62:7C:79:7B:DC:34:48`.
- **GPL-3.0:** keep `LICENSE`, upstream copyright headers and an FCL credit in the About screen; all our code stays
  public in this repo; every published APK must match a tagged commit here.
- **Upstream merges:** our code lives in `Sc*` classes/files (package `es.servidorcobblemon.movil` or `com.tungsten.fcl.sc`),
  inherited FCL code is touched as little as possible so `git merge upstream/main` stays easy.
- **Microsoft login:** `OAUTH_API_KEY` secret = Entra client ID. Today Helios's `1ce6e35a-126f-48fd-97fb-54d143ac6d45`
  (same as the desktop launcher); switch when our own Entra app gets Minecraft API access (client ID recorded in the
  plan doc and historial).
- **Distribution only from our website** (`descargas.servidorcobblemon.es`), never Google Play. Do not publish the app on
  the web guide `/como-jugar/movil` until the real-phone test (phase 6) passed and Felix says so.
- Screens: Codex designs (concept images), Felix approves, Claude builds (global CLAUDE.md, «Roles»).
- **Same design as the current desktop launcher** (Felix, 26-10: «tiene que ser el mismo diseño que el actual, cuidado
  con eso»): the red PokéNav look of `sc-launcher` (tokens `--pk-*` in `app/assets/css/scpokenav.css`, fonts Silkscreen
  + Source Sans 3), only re-laid-out for a landscape phone. References = screenshots of the REAL current launcher
  (take them with `--remote-debugging-port` on an isolated `--user-data-dir`), never old `diseno-*` proposals.

## Commands
- CI build: push to any branch → workflow `Build`; artifacts `app-<arch>`.
- Sync upstream: `git fetch upstream && git merge upstream/main` on a branch, CI must stay green.

## Live surfaces (verify before saying done)
- GitHub Actions run for the commit (green, signed APK artifact).
- The APK installed in the CI emulator / on a real phone, as each phase's check says.
