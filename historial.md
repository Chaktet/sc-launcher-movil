# Historial · SC Launcher Móvil

## Estado actual
- Publicada la **0.1.4** (27-10, versionCode 5): pantallas PokéNav, modpack automático con dos perfiles (Ajustes ›
  «Modpack Móvil / PRO»; `movil/sc-pack.json` y `movil/sc-pack-pro.json`, este último lo rehace solo
  `movil_pro.js` en descargas-auto), login Microsoft (código + PKCE), auto-actualización (`movil/app/sc-app.json`),
  botones táctiles del servidor (Pokémon/M, Lanzar/R, Poké ▲▼), reinstalación de instalaciones cortadas, velocidad de
  descarga, avisos anónimos SC-40..44.
- Sacar versión: subir versionCode/versionName en `FCL/build.gradle.kts`, push a main (prueba rápida ~27 min), si pasa
  etiqueta `vX.Y.Z` en ese commit, APK de los artefactos de ese run a `movil/app/` con nombre nuevo, `sc-app.json` al
  final, comprobar por Cloudflare. Los cambios del modpack NO necesitan versión de la app.
- NO comprobado aún: login Microsoft real, Móvil PRO en un móvil real, los menús «rotos» del S25 (esperando capturas).
- Microsoft: app propia `b02d1916-762f-4732-ad9e-f10fc0a71fe9` pendiente de Mojang; al aprobarse, cambiar `OAUTH_API_KEY`.
- Pendiente: quitar Terracotta/VPN y permisos sobrantes, textos legales de FCL, CurseForge, licencias/NOTICE; Java 8/17/25
  van en el APK sin usarse (solo hace falta el 21: ~60 MB menos y primer arranque más rápido).

## 2026-10-27 noche · 0.1.5, 0.1.6 y 0.1.7 publicadas (sin prueba larga, Felix las prueba a mano)
- 0.1.5: las actualizaciones del modpack ya no usan ModpackUpdateTask de FCL (copiaba la instancia entera a
  `.minecraft/backup` en un solo paso «0 / 1» y nunca borraba las copias); se usa ModrinthInstallTask directo y se borran
  las copias viejas.
- 0.1.6: auto-actualización con PackageInstaller (USER_ACTION_NOT_REQUIRED en Android 12+: tras la primera, sin preguntar);
  comprueba al abrir, al volver y cada 30 min; con wifi descarga e instala sola, con datos pregunta; `ScActualizada`
  reabre la app (o notificación). Barra táctil de objetos: tamaño calculado bien (antes 0x0 → escala 1).
- 0.1.7: esquema de botones de Codex aprobado por Felix (+ ◀ ▶ junto a la barra, barra táctil más alta); generado por
  `.github/scripts/monta-controles.js`, layout 1400.
- Móvil PRO lleva ahora el SC-Pack de PC entero (receta 2, igual md5 que el launcher de PC), con sc-armor3d y pantallas de PC.
- Publicar: `scripts/publica-app.sh <run> <versión> <code> "<notas>"` (APK + sc-app.json + Cloudflare + tag + release).
- Pendiente: pantallas propias en lugar de las de FCL (carga con barra real, paso, «no cierres la app», tiempo restante).

## 2026-10-27 tarde · 0.1.4 publicada (0.1.1-0.1.3 nunca salieron)
- Felix pasó dos fallos de jugadores con la 0.1.0: «No se pudo preparar el juego · …/SC Cobblemon/modpack.cfg» (una
  primera instalación cortada deja la instancia sin modpack.cfg; FCL además se niega a instalar encima: ahora se borra
  mods/ y el json de la versión y se instala de nuevo, options.txt se queda) y «se queda en 99 %» (los últimos pasos son
  las descargas grandes: ahora se ve la velocidad). Avisos anónimos como en PC (`ScAvisos`, SC-40..44, `a=android`,
  interruptor en Ajustes que también pone `-Dsc.avisos` al juego; tabla en sc-launcher/docs/ERRORES.md).
- CI: prueba rápida en cada push (~27 min de comprobaciones; tope 45 min porque el emulador se cuelga al apagarse) y la
  completa (Móvil PRO + juego 20 min) a mano (`prueba=completa`) o los domingos. La completa de 0.1.3 cazó el «already
  exists»; la rápida de 43f10b32 pasó todo.
- Publicado: tag v0.1.4 (43f10b32), GitHub Release, `movil/app/SC-Launcher-0.1.4-<abi>.apk` + `sc-app.json` versionCode 5
  (copia `sc-app.json.bak-0.1.0`); los 3 APK bajados por Cloudflare coinciden en tamaño y sha256; `/descargas/android` → 0.1.4.
  Novedad `2026-10-27-movil-app-0.1.4`; #actualizaciones publicado (lote de 6).
- Pendiente: capturas de Felix de los menús «rotos» en el S25 (no se reproduce); un login Microsoft real; prueba de
  Móvil PRO en un móvil real.

## 2026-10-27 · 0.1.1: Móvil PRO, teclas del servidor, prueba a tamaño de móvil
- Felix probó la 0.1.0 en un S25 Ultra: carga lenta, menú principal y Opciones «rotos», sin forma de pulsar M (inicial),
  y pidió elegir PRO/LITE (eligió la opción A: «Móvil» por defecto + «Móvil PRO»).
- Controles: el esquema por defecto (versionCode 1300) suma Pokémon (M, también el inicial), Lanzar (R) y Poké ▲/▼
  (cambiar de Pokémon). `ScControls` lo pone una vez a quien ya tenía el viejo (copia en `00000000.json.anterior`).
  El PokéNav del servidor es un objeto de la barra (se toca), no necesita tecla.
- Móvil PRO: `movil_pro.js` (RESOURCE-MC, descargas-auto) = PRO de PC sin Ixeris, BorderlessFullscreen, ChatShot, spark,
  Iris+shaders, sc-armor3d, sc-lockserver; pack, pantallas y sc-* del móvil. `auto_descargas.js` lo rehace solo cuando
  cambia el PRO de PC o el móvil → `movil/sc-pack-pro.json`. Primero publicado: 2.4.3-1.1.52 (85 mods, 178 MB).
  En la app: Ajustes › «Modpack Móvil / Modpack PRO»; cambiar = actualizar la misma instancia (opciones y mundos se quedan).
- Carga: en el emulador el juego tarda ~9 min (el móvil real menos). La mayor parte es Cobblemon (modelos/animaciones);
  además sc-menus-cliente espera hasta 90 s la primera vez bajando las pantallas (10.247 ficheros). Voice chat no tiene
  nativos para Android (avisa, no rompe).
- Menús rotos: en el emulador a 1920x1080 las pantallas FancyMenu eligen escala 3 (640x360), igual que en PC; no se
  reproduce. Pendiente: capturas de Felix del menú principal y Opciones en el S25.
- Codex (high) revisó: 1 bloqueante (un cambio de perfil a medias podía lanzar una mezcla: ahora se olvida la versión
  instalada antes de instalar) y 2 bugs de controles (copia y cambio atómico), arreglados; el 4.º (mismo mod con dos
  nombres en PRO) no pasa con las listas de hoy, queda como mejora.
- Verificado: run 40505332276 (rama) 44/44: instala Móvil, actualiza, PRO (85 jars), vuelve a Móvil (31, sin los de PRO),
  opciones intactas, PRO cargando 21 min sin fallos de mods.

## 2026-10-27 · Fases 2-5 y publicación de la 0.1.0
- Felix (26-10 noche): «déjame este trabajo terminado y publicado cuando lo tengas listo y comprobado».
- Hecho: pantallas, modpack automático, login Microsoft (ventana propia; el emulador se congela con la página real de
  Microsoft, así que en CI la sustituye una página local y la real se pide desde el runner), auto-actualización,
  actualizador de FCL apagado (habría ofrecido FCL a los jugadores).
- Bugs encontrados por la prueba: «Reparar» no veía los mods que vienen dentro del modpack (Cobblemon); las opciones
  del jugador se perdían al actualizar; el apagado del emulador colgaba el CI. Codex revisó login y actualizador (8
  puntos, 2 bloqueantes: PKCE/state y firma del APK): todos arreglados.
- Verificado: run 40471006866 en `main` (cb84a4fa) verde, APK firmado con nuestra clave, 30/30 comprobaciones.
  Publicado en `/var/www/packs/movil/app/` (OVH-A) + `sc-app.json` (versionCode 1) y GitHub Release `v0.1.0`; los 3 APK
  descargados por Cloudflare coinciden en tamaño y sha256. La sesión web actualiza /como-jugar/movil y #como-entrar.
- Pendiente: prueba en móvil real (login Microsoft real, entrar y jugar 15 min); entrada de novedades y anuncio en
  Discord, cuando Felix lo vea.
- Para la próxima versión: subir `versionCode`/`versionName` en `FCL/build.gradle.kts`, etiqueta `vX.Y.Z`, subir los
  APK con nombre nuevo a `movil/app/` y reescribir `sc-app.json` (sha256 y size de cada ABI).

## 2026-10-26 · Revisión del plan, fases 0 y 1, Microsoft, diseños
- Codex revisó el plan (10 puntos, 4 bloqueantes) → plan corregido en `sc-launcher` (commit 71c6179).
- Fase 0: claves de FCL fuera del repo, workflow `SC Build` (arm64, arm, x86_64; firma solo en `main`/etiquetas `v*`;
  comprueba certificado con apksigner; prueba en emulador x86_64). Run 40409790223 verde: la app abre y sigue viva a
  los 40 s sin cierres.
- Fase 1: commits 9638595a + 0b552af7 (identidad, español 1.487 frases, carpeta propia).
- Felix: «tiene que ser el mismo diseño que el actual» → referencias = capturas reales del launcher 1.17.8.
- Azure: Felix se dio de alta en la cuenta gratuita (directorio propio); app registrada y formulario enviado con su OK.

## 2026-10-26 · Arranque del proyecto
- Felix aprobó el plan `sc-launcher/docs/PLAN-APP-ANDROID.md` (app Android propia, fork de FCL, solo desde la web).
- Hecho: fork público, clon en `E:/PROYECTOS-MC/sc-launcher-movil` (remote `upstream`), clave `sc-release.jks`
  (RSA 4096, alias `sc-launcher`, válida hasta 2126) y secretos `SC_KEYSTORE_BASE64`, `SC_KEYSTORE_PASSWORD`,
  `OAUTH_API_KEY` en el repo. Felix guardó copia de la clave en su Drive.
