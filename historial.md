# Historial · SC Launcher Móvil

## Estado actual
- Publicada la **0.1.17** (28-10, versionCode 18; historial abajo). Base: pantallas PokéNav, modpack automático con dos perfiles (Ajustes ›
  «Modpack Móvil / PRO»; `movil/sc-pack.json` y `movil/sc-pack-pro.json`, este último lo rehace solo
  `movil_pro.js` en descargas-auto), login Microsoft (código + PKCE), auto-actualización (`movil/app/sc-app.json`),
  botones táctiles del servidor (Pokémon/M, Lanzar/R, Poké ▲▼), reinstalación de instalaciones cortadas, velocidad de
  descarga, avisos anónimos SC-40..45.
- Sacar versión: subir versionCode/versionName en `FCL/build.gradle.kts`, push a main (prueba rápida ~27 min), si pasa
  etiqueta `vX.Y.Z` en ese commit, APK de los artefactos de ese run a `movil/app/` con nombre nuevo, `sc-app.json` al
  final, comprobar por Cloudflare. Los cambios del modpack NO necesitan versión de la app.
- NO comprobado aún: login Microsoft real, Móvil PRO en un móvil real, los menús «rotos» del S25 (esperando capturas).
- Microsoft: app propia `b02d1916-762f-4732-ad9e-f10fc0a71fe9` pendiente de Mojang; al aprobarse, cambiar `OAUTH_API_KEY`.
- Pendiente: quitar Terracotta/VPN y permisos sobrantes, textos legales de FCL, CurseForge, licencias/NOTICE; probar en
  Móvil PRO la carga bajo demanda de ModernFix (`mixin.perf.dynamic_resources`), sin activar aún.

## 2026-10-28 · Publicar solo con la prueba del emulador en verde (sesión «Error SC-41», Felix)
- `publicar` ahora `needs: [build, emulator]`: una etiqueta solo publica si la prueba del emulador pasa (~20 min más).
- La prueba rápida nunca acababa en verde: (1) `ls backup` sin carpeta (el caso bueno) hacía fallar a node justo tras
  «a second update»; (2) al apagar, netsimd/crashpad_handler/el lanzador del emulador y nuestros http.server/logcat
  dejaban el paso esperando hasta el tope de 45 min (lista en `evidence/procesos-al-cerrar.txt`): ahora se matan;
  (3) la fase 5 esperaba el diálogo «Nueva versión de la app», que con wifi no sale desde la 0.1.6 (se instala sola).
- Verificado: run 40628819795 (9f5beff9) todo OK, emulador 18 min. Sin probar aún una etiqueta real con el nuevo `needs`.

## 2026-10-28 · SC-41 falsa alarma y fallo de instalación de la 0.1.15 (sesión «Error SC-41»)
- Alerta 5 jugadores/hora con SC-41. Pistas `s` (103-103, 8025-8025, 0-1, 42-45): pasos locales, no descargas. El
  temporizador de velocidad de FetchTask emite 0 B/s cada segundo aunque no se descargue nada; una descarga cortada de
  verdad acaba en SC-40 (timeout 10 s × 3). Arreglo 59bcd77b: el reloj solo corre con un FetchTask activo, pista `k` =
  paso que más lleva, el handler se quita en `finally` (Cancelar dejaba un SC-41 tardío).
- La prueba del emulador de esa rama cazó que la 0.1.15 (ya publicada) no instalaba el modpack: `sameContent()` dejaba
  salir un NoSuchFileException (`mods/sc-avisos-cliente-1.5.0.jar` desaparece entre isRegularFile y size). ee4d6350: si no
  se puede comprobar, se escribe como antes. Ambos van en la 0.1.16 (publicada); su emulador: instala 2.4.4 en 299 s,
  31 mods, dos actualizaciones OK (el job acaba por el tope de 45 min del emulador, como siempre).
- Pendiente: en 1-2 días comprobar en `stats.json` que SC-41 baja con la 0.1.16+ y qué `k` traen los que queden.

## 2026-10-28 · 0.1.16-0.1.17 y modpacks más ligeros
- 0.1.16: si una actualización del modpack falla, reinstala desde cero; SC-40 lleva la causa y el archivo.
- 0.1.17 (optimizaciones aprobadas): APK sin Java 8/17/25 (solo el 21; se borran los ya instalados), huellas del modpack en
  4 hilos (MinecraftInstanceTask), controles por defecto desde el primer arranque.
- Modpacks (descargas-auto, RESOURCE-MC/historial 28-10 02:58): el pack va aparte en el índice → Móvil 143→80 MB, PRO
  245→116 MB, y una actualización sin cambio de pack no lo vuelve a bajar. Pantallas = juego actual de `/menus/`.

## 2026-10-28 · 0.1.12 a 0.1.15 y publicación automática
- 0.1.12: memoria del juego según el perfil (ScMemoria, sesión «movil-memoria»: OOM al recargar recursos quitaba el pack) +
  reabrir la app tras auto-actualizarse (ScResultadoInstalacion, el estado del instalador va a una actividad).
- 0.1.13: Cancelar/Reintentar durante la descarga del modpack.
- 0.1.14: 12 correcciones automáticas aprobadas por Felix (ScAuto, ScRecordatorios, ScServicioDescarga, descargas
  reanudables con ruta directa, prefetch y revisión diaria con wifi, aviso SC-45 automático).
- 0.1.15: actualizaciones del modpack sin reescribir los ~10.000 archivos sin cambios + progreso real al descomprimir
  (sesión «movil-paso-lento», rama fix/paso-lento).
- **Publicar ahora = etiquetar**: subir versionCode/versionName en `FCL/build.gradle.kts`, commit a main, y
  `git tag -a vX.Y.Z -m "<nota para jugadores>"` + push del tag. El job «publicar» de SC Build crea la GitHub Release
  con los 3 APK y la línea `<!-- sc-app {...} -->`; en OVH-A `descargas-auto/app_movil.js` (cron cada 2 min,
  `app.log`) la publica en `movil/app/` + `sc-app.json`. 0.1.14 y 0.1.15 salieron así (~15 min, verificadas por
  Cloudflare). `scripts/publica-app.sh` queda solo como plan B (ya no crear la release a mano si existe).
- Ojo: el hook local de compilar Java falla con esta app (no hay entorno Android en el PC): compila solo en GitHub.

## 2026-10-27 noche (3) · 0.1.11: menú del juego propio
- Diseño de Codex aprobado por Felix (`E:/tmp/app-android/codex-menu/`). `ScMenuJuego` + `assets/sc_ui/menu-juego.html`:
  el botón flotante (MenuView, lente roja `sc_menu_lente`) abre nuestro panel en vez de los cajones de FCL; cada opción
  llama al manejador de FCL vía `GameMenu.scAccion`/`scEstado`. «Bloquear la cámara» fuera (LOCK_VIEW solo fija el botón).
  «Opciones avanzadas» abre el menú completo de FCL.
- Memoria: sesión «movil-memoria» trabaja en la rama `fix/memoria-pack` (OOM al recargar recursos = el pack se quita).

## 2026-10-27 noche (2) · 0.1.8, 0.1.9 y 0.1.10: pantallas propias en lugar de las de FCL
- Diseños de Codex aprobados por Felix (`E:/tmp/app-android/codex-pantallas/`).
- 0.1.8 carga: `ScCarga` escucha el executor de LauncherHelper (el TaskDialog de FCL ya no sale); 6 pasos, barra real
  (archivos hechos/total), velocidad, tiempo restante medido en el móvil, «no cierres la app», estado «la descarga no
  avanza» tras 1 min con Reintentar, Cancelar.
- 0.1.9 error: `ScCierre` (GameMenu.onExit) guarda la causa leída del log y reabre la app con `ScReabrir` (proceso
  :crash); la página enseña la causa, Volver a jugar, Enviar informe (SC-45) y Ver detalles. JVMCrashActivity ya no sale.
- 0.1.10 bienvenida: `ScBienvenida` (página bienvenida.html sobre SplashActivity): Empezar acepta normas+GPL, permiso,
  barra real de los componentes; el EULA/aviso/lista de FCL ya no salen.
- Sin probar en el emulador ni en un móvil (Felix las prueba a mano). Pendiente: el menú lateral de FCL dentro del juego.

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
