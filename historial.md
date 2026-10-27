# Historial · SC Launcher Móvil

## Estado actual
- Fases 0-5 en `main` (cb84a4fa): pantallas PokéNav (WebView `assets/sc_ui` encima de MainActivity), modpack
  automático + JUGAR directo al servidor (`ScPack`), login Microsoft en ventana propia (`ScMicrosoft`, código +
  PKCE + state, redirección `nativeclient`, ID de Helios), auto-actualización desde nuestra web (`ScAppUpdate`,
  `movil/app/sc-app.json`; el actualizador de FCL está apagado).
- CI (`.github/scripts/sc-ui-check.mjs`) comprueba 30 cosas en el emulador: pantallas, ventana Microsoft (con página
  local de prueba: el emulador se congela con la real; la real se comprueba desde el runner), instalar 2.4.x,
  actualizar sin perder `options.txt`, reparar un Cobblemon roto, oferta + descarga + instalador de la app, juego
  arrancado 60 s. Informe en el artefacto `emulator-evidence/ui-check.txt`.
- NO comprobado aún: un login Microsoft real (lo hace Felix en su móvil, fase 6) y entrar al servidor desde un móvil
  real (el emulador no tiene GPU real).
- Microsoft: app propia en Azure `b02d1916-762f-4732-ad9e-f10fc0a71fe9`, solicitud a Mojang enviada 26-10 (respuesta a
  darksens96@hotmail.com). Al aprobarse: cambiar `OAUTH_API_KEY` (y el launcher de PC) a ese ID.
- Pendiente: fase 6 (móviles reales), quitar Terracotta/VPN y permisos sobrantes, textos legales/anuncios de FCL,
  CurseForge, revisión de licencias/NOTICE. Cada actualización del modpack baja 143 MB (el resource pack va dentro).

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
