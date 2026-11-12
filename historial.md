# Historial · SC Launcher Móvil

## 2026-11-12 · App 0.1.26: el botón «Pokémon» del Móvil PRO abría el mapa de Xaero (Felix: «Cuando pase la prueba, publica la app 0.1.26»)
- Causa: el botón táctil «Pokémon» manda M; en PRO el mapa mundial de Xaero, sin línea en `options.txt`, viene en M y gana a
  Cobblemon. El PRO trae `key_gui.xaero_open_map:key.keyboard.n`, pero al pasar de Móvil a PRO FCL no pisa el `options.txt`
  que el juego ya reescribió y `ScPack.mergeOptions` leía el del disco: las teclas de los mods nuevos no llegaban nunca.
- Arreglo (84429c59, rama `fix/tecla-mapa-pro`): la mezcla lee las líneas del .mrpack; antes de cada partida `teclaDelMapa`
  pasa el mapa a N si no tiene tecla o comparte la de «Pokémon» (otra tecla elegida por el jugador se respeta).
- Comprobado: cliente de PC con los mods del PRO y el `options.txt` del Móvil 2.4.55: sin arreglo M abre el mapa; con él M abre
  Cobblemon y N el mapa. Prueba completa del emulador (run 43287974931): 49 OK, 0 FAIL, con los 3 casos nuevos de la tecla.
- Sin ver en un móvil real.

## Estado actual
- **12-11 · Fase 3 (Pokémon bajo demanda) LISTA, sin publicar:** sc-memoria-cliente **1.8.0**, rama
  `perf/pokemon-bajo-demanda` (worktree `E:/PROYECTOS-MC/sc-memoria-cliente-pokemon`, sobre la 1.7.0, último commit
  `7854bd2`). Codex «Sin hallazgos» en la ronda 3 de cierre del diff. **Felix (12-11): «Al próximo pack Móvil»**: el
  próximo `--rehacer-movil` lleva este jar (`build/libs/sc-memoria-cliente-1.8.0.jar` de esa rama, o rehacerlo allí;
  sustituye a la 1.7.0, un solo jar). Sin probar antes en móvil real. Sin verificar: móvil real, montar, multijugador.
- **12-11 · Arranque rápido PUBLICADO:** app **0.1.25** (code 26) en vivo y modpack **Móvil 2.4.50** / Lite
  2.4.50-lite-r2 con sc-menus-cliente **1.8.2** (1a) y sc-memoria-cliente **1.7.0** (1b). Móvil PRO 2.4.50-1.1.125-r9
  sigue con los jars de PC (1.8.1 / 1.6.0): le llegan cuando salgan en el pack de PC. Novedad: Felix dijo «No se publica
  esto» (vista previa 1570471756148252764 en diseño-pruebas, sin publicar).
- **PC (Felix «También en PC», 12-11):** sc-memoria-cliente **1.7.0** publicada en PRO/LITE/SUPER LITE del launcher
  (manifiesto 89e06608, copia `launcher/_backup-arranque-rapido-20261112-170152/`, Cloudflare OK; copia local sincronizada).
  sc-menus-cliente NO: otra sesión publicó la **1.9.0** (cartel de reinicio) en PC justo antes; la 1.8.2 la habría
  quitado. Otra sesión metió también la 1.9.0 en Móvil 2.4.51 (17:01Z), encima de la 1.8.2: **el 1a no está hoy en
  ningún pack**; vuelve cuando `6368466` se fusione en la rama de la 1.9.x y salga una versión nueva.
- **12-11 · Mano invisible en 1ª persona (Felix: en el móvil, en PC arreglado): Móvil 2.4.52 / Lite sin TooManyPlayers.**
  El único mod del móvil que no está en PC era TooManyPlayers, quitado de PC el 11-11 (sc-launcher historial). PRO ya no
  lo llevaba. `QUITAR_MOVIL` += `/^TooManyPlayers-/i` (`auto_descargas.js` md5 146e6b99, copia en OVH-A
  `auto_descargas.js.bak-toomany-20261112`), `--rehacer-movil` → 2.4.52. Cloudflare: los 3 manifiestos con tamaño y
  sha256 OK y 0 rastros del mod. Emulador (run 43266549235) con el 2.4.52: instala y el juego sigue a los 60 s. **Sin ver en un móvil real** que la mano vuelva (el emulador no llega a dibujar el juego).
- **12-11 · Móvil 2.4.54 / Lite / PRO con sc-avisos-cliente 1.7.0** (también en PC): una página web como motivo de
  desconexión sale como texto en español (detalle en sc-avisos-cliente/historial.md). Cloudflare OK en los 3.
- **El cron de `app_movil.js` está ACTIVO** (crontab de OVH-A, sin `#PAUSADO`): publicó la 0.1.24 sola el 08-11 y la
  0.1.25 el 12-11. Una etiqueta `vX.Y.Z` = publicación a todos en cuanto la CI pasa.
- Por SSH en dos saltos (Hetzner → OVH-A), las comillas anidadas se rompen sin avisar: copiar un script y ejecutarlo
  con `bash` allí (12-11: dos publicaciones paradas por eso).
- Pendiente: en 2 días leer SC-47 por `g` con las claves nuevas (`k`, `d`, `h`, `s`) y comparar con antes del 12-11.

## 2026-11-12 · Fase 3 cerrada con Codex: 3 rondas del diff, 7 fallos arreglados (sesión hija)
- Ronda 1 (5): carrera entre hilos al cargar un modelo o un grupo de animaciones; sustituto roto aceptado; modelo de la
  recarga anterior perdido si todos los nuevos fallan (Cobblemon nunca vacía `texturedModels` y lo conserva); zip de un
  pack retirado abierto toda la partida. Commit `f25a7c7`.
- Ronda 2 (2, sobre esos arreglos): se cerraba también un pack vivo con un archivo corrupto (ahora solo ante «zip file
  closed»); la cadena de «modelo anterior» crecía en cada recarga (se compacta al final de cada una). Commit `7854bd2`.
- Ronda 3 `xhigh --cierre`: «Sin hallazgos».
- Verificado tras cada ronda: huellas on/off idénticas con y sin el pack de casos límite (6.856 / 6.858 modelos), sin
  errores nuevos en el log; en juego dos recargas F3+T seguidas con especies vistas y nuevas dibujándose, sin errores.
- Ojo banco de pruebas: `lanza-movil.js` NO copia el jar; copiar `build/libs/…jar` a `movil-inst/mods/` antes de medir.
  El agente de `movil-inst` tiene ahora la orden `recarga` (F3+T).

## 2026-11-12 · Fase 3: animaciones y modelos de Pokémon bajo demanda (sesión hija)
- Pedido: que el móvil no lea los ~4.500 ficheros de animación y ~6.900 modelos de Cobblemon al arrancar.
- Medido (PC, instancia con los mods y packs exactos del Móvil 2.4.49, `E:/tmp/fase3/`): con la 1.3.0 la recarga eran
  62 s, 39 de FancyMenu y 8 de fuentes (ya arreglados en 1.4.0/1.6.0); con la 1.7.0 Cobblemon es 14 de 23 s, el 82 %
  animaciones, y sube el heap de 1,05 a 2,78 GB.
- Hecho: sc-memoria-cliente 1.8.0 (plan `docs/PLAN-FASE3-POKEMON-BAJO-DEMANDA.md` en esa rama): al cargar solo lista los
  ficheros; cada grupo de animación y cada modelo se lee la primera vez que se usa. Interruptor `pokemon_bajo_demanda`.
  Codex revisó el plan (8 hallazgos, todos atendidos: zip cerrado por otra recarga se reabre, mismo nombre en dos
  carpetas gana como en Cobblemon, fallo recordado sin trazas por fotograma, sustituto horneado al cargar).
- Verificado: huella (hash de cada vértice de 6.856 modelos + volcado completo de cada animación) IDÉNTICA encendido y
  apagado, con y sin un pack de casos límite. Tiempos: Cobblemon 13-18 s → 0,7-0,9 s (todos los núcleos), 29-40 s →
  2,4-3,1 s (4 núcleos); hasta el menú 80-119 s → 42-56 s (4 núcleos); heap máx. tras GC 2,7-2,8 GB → 1,1 GB. En juego
  (mundo individual): se ven y animan, también tras cambiar de pack en caliente y tras /reload; `false` = carga completa.
- NO verificado: móvil real (tirones al ver muchas especies nuevas de golpe), montar un Pokémon, multijugador.

## 2026-11-12 · Publicado: app 0.1.25 + Móvil 2.4.50 (Felix: «Publicar 0.1.25», «Pack móvil nuevo ya»)
- App: etiqueta `v0.1.25` en 794306a5 → run 43255220455 verde (compilaciones + emulador + release) → `app_movil.js`
  la publicó a las 16:38Z. Los 3 APK por Cloudflare: tamaño y sha256 = `sc-app.json`.
- Mods: sc-menus-cliente 1.8.2 = rama `perf/pantallas-rapido` + bump (a14aa7d; 1.9.x ya lo usan otras ramas sin
  publicar, que NO llevan el 1a hasta que se fusione). sc-memoria-cliente 1.7.0 = `perf/showdown-sin-esperar` 9b72653
  (incluye 1.4-1.6, ya en el pack de PC). Jars en `descargas-auto/mods-movil/`.
- Prueba antes de publicar: nueva entrada `pack_prueba` de la CI (0f469cb0): el emulador instala un `sc-pack.json` sin
  publicar. Run 43256007283 con 2.4.49 + los 2 jars: todo OK; «[SC Memoria] Showdown starts in the background», «[SC Menus]
  screens v171 up to date (… 6888 files checked in 9.1 s)», sello `rapido.txt` escrito. La 2ª partida (quick check) no
  se prueba en la CI: juego y app comparten proceso; queda cubierta por SyncerTest y la prueba local.
- Publicación: `auto_descargas.js --rehacer-movil --mod-movil …` (ensayo `--prueba` antes) → Móvil 2.4.50, Lite, PRO.
  Por Cloudflare: los 3 manifiestos con tamaño y sha256 correctos; Móvil y Lite llevan 1.8.2 + 1.7.0.

## 2026-11-12 · Arranque rápido del móvil, fases 0 + 2a + 1a + 1b (sesión hija, Felix: «que no tarde tantísimo»)
- Codex revisó el plan (10 hallazgos, todos aplicados). El más serio: `a`/`r`/`p` chocaban con claves fijas de ScAvisos.
- App 0.1.25 (70163a8f + 2c213f66): `ScArranque` lee la salida del juego (`GameMenu.onLog`, líneas completas, reloj
  monotónico) → SC-47 con `l s k d h e n f`, línea «SC arranque:» en fcl.log en cada arranque; pantalla de arranque con
  etapa, barra y «Quedan unos …» aprendido del arranque anterior. Felix la aprobó viendo 4 capturas (12-11). Codex de fondo: 3 fallos arreglados.
- 1a `6368466`: sello `rapido.txt`, 65 archivos en vez de 6.888-10.366 (8,8 s en el emulador); SyncerTest 26/26.
- 1b `2a5d50b`+`9b72653`: Cobblemon 1.8.3 bloquea el hilo principal en `ShowdownThread.launch()`; ahora arranca en
  paralelo y esperan solo `getService()`/`queue()`. Compila; NO probado en un cliente. 1c descartada (≤1 s, ver plan).
- Verificado: run 43174165045 todo OK (3 compilaciones + emulador); `arranque-sc47.txt`: `l=15 s=5 e=50 n=0 f=1`.
- NO verificable sin móvil real ni pack nuevo: `d`/`h`/`k` (el emulador no llega al primer fotograma), la barra en
  marcha, y la línea `[SC Menus] … quick check` en una 2ª partida (solo en el test local; el emulador usa el pack publicado).
- **29-10 · 0.1.21 RETIRADA, publicador PAUSADO:** `movil/app/sc-app.json` vuelve a 0.1.20 (copia de la 0.1.21 en `sc-app.json.bak-0.1.21-retirada`) y la línea de `app_movil.js` del cron de OVH-A está comentada (`#PAUSADO-20261029`, copia `descargas-auto/crontab.bak-20261029`). Motivo: móviles en 0.1.20 recibían «Actualización no válida… no está firmada» y, con la actualización obligatoria, no podían jugar. La 0.1.21 SÍ está firmada con nuestra clave: algunos Android no leen el firmante de un APK solo v2/v3. Arreglo en la 0.1.22 (9f8ac543). No reactivar el cron sin decidir cómo publicar la 0.1.22.
- Publicada la **0.1.21** (29-10, versionCode 22). Base: pantallas PokéNav, modpack automático con dos perfiles (Ajustes ›
  «Modpack Móvil / PRO»; `movil/sc-pack.json` y `movil/sc-pack-pro.json`, este último lo rehace solo
  `movil_pro.js` en descargas-auto), login Microsoft (código + PKCE), auto-actualización (`movil/app/sc-app.json`),
  botones táctiles del servidor (Pokémon/M, Lanzar/R, Poké ▲▼), reinstalación de instalaciones cortadas, velocidad de
  descarga, avisos anónimos SC-40..47, informe para soporte (zip).
- Sacar versión: subir versionCode/versionName en `FCL/build.gradle.kts`, push a main (prueba rápida ~27 min), si pasa
  etiqueta `vX.Y.Z` en ese commit, APK de los artefactos de ese run a `movil/app/` con nombre nuevo, `sc-app.json` al
  final, comprobar por Cloudflare. Los cambios del modpack NO necesitan versión de la app.
- NO comprobado aún: login Microsoft real, Móvil PRO en un móvil real, los menús «rotos» del S25 (esperando capturas).
- Microsoft: app propia `b02d1916-762f-4732-ad9e-f10fc0a71fe9` pendiente de Mojang; al aprobarse, cambiar `OAUTH_API_KEY`.
- Pendiente 0.1.21: en 1-2 días leer en stats.json los SC-40 (ahora con `k`/`f`/`h`) y los SC-47 (arranque lento,
  Redmi 13C = `23100RN82L`) y decidir el arreglo; si los SC-40 apuntan a cdn.modrinth.com, espejo en nuestro servidor
  como 2ª URL en `movil_auto.js`. Un móvil flojo con PRO elegido a mano nunca baja solo a Lite (1 FPS visto): proponer aviso.
- **08-11 · Medición de SC-46 mal (leído en el código, sin arreglar):** `FCLBridge.getFps()` devuelve los fotogramas
  desde la última lectura y lo pone a 0 (`jni/egl_bridge.c`:437). `ScAuto.vigilarRendimiento` lo lee cada 5 s
  (`ScAuto.kt`:279), así que cada muestra es ≈ 5× los FPS reales y el umbral 20 equivale a ~4 FPS. Con «mostrar FPS»
  activado, `GameMenu.java`:1456-1459 lee el mismo contador cada 1 s y el vigilante ve menos de lo real: puede bajar
  la calidad sin motivo. Los 120 s de espera empiezan antes de que el juego abra (2-13 min según SC-47), y «calor»
  durante la carga sale con `f=0`. SC-47 hoy (19 jugadores): `u=0`, `t` = 2-13 min, 12 de 19 de gama alta.
  El monitor ya solo avisa de SC-46/47 a partir de 15 por hora (cobblemon-web `b353ece2`).
- Pendiente: quitar Terracotta/VPN y permisos sobrantes, textos legales de FCL, CurseForge, licencias/NOTICE; probar en
  Móvil PRO la carga bajo demanda de ModernFix (`mixin.perf.dynamic_resources`), sin activar aún.

## 2026-11-08 · 0.1.24 FPS reales para la escalera automática (Felix «sí, arréglala»), SIN publicar
- Causa: `getFps()` pone el contador a 0 en cada lectura; ScAuto leía cada 5 s (≈5× los FPS reales, umbral 20 ≈ 4 FPS
  reales, subir con 45 ≈ 9) y con «mostrar FPS» el vigilante veía menos de lo real. Los 120 s empezaban con la carga
  (2-13 min) y daban «calor» con f=0.
- `265a1d13`: contador nativo que nadie pone a 0 (`sc_frames_total`, `CallbackBridge.getFramesTotal`), FPS =
  fotogramas / tiempo transcurrido; el vigilante espera al primer fotograma (`ScArranque.primerFotograma`) + 120 s.
  Efecto: ahora 20/45 son FPS de verdad: más móviles bajarán de escalón y menos subirán. Versión 0.1.24 (code 25),
  incluye la 0.1.23 (Cancelar en «Reparar archivos»), que nunca se publicó.
- `f868195d`: la prueba del emulador esperaba SC-Pack-Movil en PRO; desde el 27-10 PRO lleva el SC-Pack de PC (por eso
  fallaban los runs de la 0.1.23). Run 42506950728: 3 compilaciones + emulador OK; `getFramesTotal` está en
  `libpojavexec.so` del APK arm64. NO comprobado en marcha: el emulador solo juega 60 s y no llega al primer fotograma.
- Pendiente: publicar (etiqueta `v0.1.24`, APKs a `movil/app/`, `sc-app.json`; cron de `app_movil.js` sigue pausado).

## 2026-10-29 · 0.1.21 pantallas que caben, informe para soporte y reintento solo (sesión «launcher móvil responsive», Felix)
- Felix: en muchos móviles se cortan botones/textos; a muchos no les entra; 1500 s cargando en un Redmi 13C; un zip para
  que nos manden los registros; «sube toda la actualización».
- Pantallas: `escala.js` dibuja el diseño a 780×384 px CSS y lo escala entero en móviles más bajos (720×324 cortaba JUGAR,
  Reparar, el evento y Ajustes). Ajustes reordenado (cuenta en una fila, créditos en «Acerca de»). Medido con
  `E:/tmp/movil-ui/mide.js` (9 tamaños 640×288…1280×800, todas las pantallas): 44/135 casos cortados → 0.
- «Informe para soporte» (Ajustes, tarjeta de error, pantalla de cierre): `ScSoporte` hace un zip (logs del launcher y del
  juego, crash-reports, hs_err, options, menu_setting y resumen del móvil), quita tokens y lo abre con el menú de compartir.
- Causa de «no les entra»: SC-40 `ModpackCompletionException` ×6 en 0.1.20 (una descarga del modpack que falla; FCL pierde
  la causa). Los 145 archivos de los 3 modpacks bajan bien desde aquí. Ahora: 3 intentos automáticos sin perder lo bajado,
  y SC-40 dice el paso, archivo y HTTP. Nuevo SC-47 (preparar o abrir > 2 min, minutos + modelo + gama + RAM).
- Codex: 2 hallazgos (claves SC-47 reservadas, zip borrado al pedir otro) arreglados → «Sin hallazgos». La prueba del
  emulador fallaba porque esperaba el título antiguo del aviso de actualización: arreglada; run 40807597812 todo OK.
- Publicada por la etiqueta v0.1.21 (run 40810716001; su emulador se colgó sin log la 1ª vez y pasó al relanzarlo). sc-app.json
  0.1.21 y los 3 APK verificados por Cloudflare (tamaño + sha256). Sin probar en un móvil real. Novedad «La app del móvil, más ligera y para todos los móviles» publicada 29-10 (launcher + Discord, OK de Felix).

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

## 2026-10-28 · 0.1.20 actualización obligatoria y rendimiento automático
- JUGAR comprueba antes si hay app nueva: con ella solo deja «Actualizar» (sin conexión juega igual). Felix se saltó la 0.1.19 y la app le dejó seguir.
- Escalera de rendimiento 0..5 en `ScAuto` (gráficos rápidos → distancia 4 → resolución 75 % → distancia 2 y 60 % → Móvil Lite); baja con partida a <20 FPS o móvil caliente, sube tras 3 partidas a >45 FPS. Resolución = `windowScale` de `menu_setting.json`.
- `gama()`: RAM, núcleos y GPU (EGL fuera de pantalla) → los móviles flojos empiezan en el escalón 2, los medios en 1 (una vez, también a los ya instalados).
- Aviso SC-46 (x, f=FPS, n=escalón, m=modelo, g=GPU). El modpack Móvil 2.4.9 lleva BadOptimizations/MoreCulling/ScalableLux; Lite sin voz ni emotes.
- La prueba del emulador falló una vez por la comprobación de Microsoft (sin respuesta) y pasó al relanzarla. Verificado por Cloudflare (3 APK, sha256 arm64). Sin probar en un móvil real.

## 2026-10-28 · 0.1.19 Móvil Lite y SC-91 falsos
- Perfil «Móvil Lite» (`PROFILE_LITE`, `movil/sc-pack-lite.json`): el pack móvil sin skins de Pokémon (16 MB, 1.623 modelos frente a 6.303). Lo elige sola `ScAuto.perfilPorMemoria` si el móvil no da 2 GB al juego (<3,5 GB de RAM) o si la última partida perdió el pack por memoria; nunca si el jugador eligió perfil en Ajustes (`perfil_elegido`). Avisos con `p=movillite`.
- Causa de «no se ven items/skins» en gama baja: sin memoria al cargar las skins, Minecraft quitaba el pack entero.
- SC-91 «falta» de cada partida de móvil = falsa alarma de sc-avisos-cliente 1.5.0 (solo miraba SC-Pack.zip); 1.5.1 en Móvil/Lite 2.4.8.
- Descarga del APK «atascada al 100 %» en pestañas de otras apps: nota en /como-jugar/movil (sesión de guías web).
- Verificado por Cloudflare: sc-app.json 0.1.19, 3 APK con su tamaño, sha256 del arm64 igual. Sin probar en un móvil real.

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
