# Historial · SC Launcher Móvil

## Estado actual
- Fases 0 y 1 en `main`: CI firma con nuestra clave, id `es.servidorcobblemon.movil`, nombre «SC Launcher», icono del
  launcher de PC, español por defecto (traducción completa validada), datos en `/SCLauncher` (no pisa a FCL).
- Fase 2 en curso: diseños de Codex APROBADOS por Felix (26-10), en `docs/sc-diseno/` (m1…m6 + ficha). Mismo diseño
  que el launcher de PC 1.17.8. Construcción: HTML/CSS con la marca PokéNav en una WebView encima de MainActivity.
- Microsoft: app propia en Azure, client ID `b02d1916-762f-4732-ad9e-f10fc0a71fe9`; solicitud a Mojang enviada 26-10
  (respuesta a darksens96@hotmail.com). Hasta entonces `OAUTH_API_KEY` = ID de Helios, que NO admite el flujo de código
  de dispositivo de FCL: la fase 4 tiene que usar ventana de login con redirección `nativeclient`.
- Pendiente: fases 2-6 (plan en `sc-launcher/docs/PLAN-APP-ANDROID.md`).

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
