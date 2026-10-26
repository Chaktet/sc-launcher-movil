# Design: the Android version of our launcher — SAME design as the current desktop launcher

## Goal (one sentence)
Draw mockups of our Android launcher app («SC Launcher») that look EXACTLY like the current desktop launcher (1.17.8,
attached screenshots), only re-laid-out for a phone held in landscape. Felix, the owner: «Tiene que ser el mismo diseño
que el actual, cuidado con eso». No new style, no new colours, no new components, no redesign.

## References (attached images, also in ./ref/)
- `01-pc-elegir-cuenta.png`: account choice (Premium / No Premium), left card with the background art.
- `02-pc-no-premium.png`: No Premium name entry.
- `03-pc-inicio.png`: main screen: red PokéNav-style frame, header (Poké-ball light + 3 dots, «SERVIDOR COBBLEMON»
  pixel logo, account box top-right with skin head), tabs (Jugar / Eventos / Novedades / Galería / Ajustes), hero card
  «TU AVENTURA, A UN CLIC.», card with «REGIONES EN LÍNEA» (Kanto/Kalos player counts), profile + «Cambiar», big blue
  JUGAR button, «Reparar archivos» / «ZIP de soporte», «PRÓXIMO EVENTO» card, «LA GRAN NOVEDAD DEL SERVIDOR» card,
  footer (version, servidorcobblemon.es, Web / Tienda / Discord).
- `04-pc-novedades.png`, `05-pc-ajustes.png`: other tabs.
- `ref-fondo-valle.png`: the only background art Felix has validated («Valle Cobblemon»). Use only this art (cropped)
  wherever the desktop shows art. `icono-sc.png`: app icon.

## Exact design tokens (from the desktop CSS; use these, nothing else)
- Colours: red `#B22839` (frame), red-dark `#6E1B2C`, red-light `#D2404F`, cream `#F6F0DE` (panels), paper `#FFFCF2`,
  ink `#16233E` (text, 3 px outlines), muted `#415875`, blue `#466D94`, button blue `#1F5FB8` / `#17488C` / hover
  `#2A71CF`, tab `#A8CEEE`, gold `#E7BF57` (active-tab underline, Tienda), offer green `#2F6B2F` on `#DDEFD6`,
  line `#E9F2FA`, disabled `#D8D5C9`.
- Fonts: titles/logo `Silkscreen` (pixel font, uppercase), body `Source Sans 3` (semibold/bold for labels).
- Shapes: pixel-cut corners (12/8/4 px steps), 3 px ink outlines, cards = ink 3 px + blue 2 px + light line 2 px inner
  borders, buttons with a light top bevel and a dark bottom bevel. Copy them exactly from the screenshots.

## Target screen
Phone landscape 2400 × 1080 px (= 800 × 360 dp at 3x; design at 2400 × 1080). Also check it still fits 1600 × 720
(older phones). Safe margin 16 dp; camera notch may cover the left 32 dp. Touch targets ≥ 44 dp. No desktop window
bar (no minimise/maximise/close), no hover states.

## What to draw (one image per screen, 2400 × 1080, plus one overview sheet)
1. `m1-elegir-cuenta.png` — same as 01 (art card + «¿Cómo quieres entrar?» with Premium / No Premium).
2. `m2-no-premium.png` — same as 02, with the Android keyboard NOT covering the name field (field in the top half).
3. `m3-inicio.png` — the main screen. Same header, tabs, cards and JUGAR as 03, compressed to 360 dp height. Tabs for
   the phone: Jugar, Eventos, Novedades, Ajustes (no Galería). Remove «ZIP de soporte»; keep «Reparar archivos».
   «Cambiar» profile is not needed (phone has one profile): show «Servidor Cobblemon · Móvil». Keep regions online,
   próximo evento and la gran novedad (they can become a narrower right column or a row; your call, but the same
   cards).
4. `m4-preparando.png` — main screen while JUGAR prepares the game: the JUGAR button becomes a progress bar in the same
   style with a status line, e.g. «Actualizando el modpack · 38 / 73 MB» and «Comprobando archivos…»; plus a small
   error variant drawn as a second state on the same image: «No se pudo descargar. Revisa tu conexión» + «Reintentar».
5. `m5-actualizar-app.png` — dialog in the SAME style as the desktop dialogs (cream panel, pixel title, «i» icon, two
   buttons like «No, gracias» / «Sí, abrirlo»): «Hay una versión nueva de SC Launcher» with «Actualizar» / «Más tarde»,
   and a second state explaining that Android will ask once for permission to install apps from SC Launcher.
6. `m6-ajustes.png` — the Ajustes tab like 05, phone-sized, with: memoria RAM (slider), renderizador, controles
   táctiles, idioma, cuenta (cerrar sesión), «Acerca de» (credits FoldCraftLauncher, licencia GPL-3.0).
7. `hoja-resumen.png` — 2048 px wide sheet with the 6 screens side by side, small.

## Constraints
- Spanish texts, short. Never a fixed clock time («Empieza en 5 d 21 h 44 min», not «a las 20:00»).
- Every button, text and number shown must be something the app really has (listed above); no invented features, no
  prices, no Pokémon names in text.
- Same visual language as the desktop screenshots: if you hesitate, copy the desktop element as-is and only resize it.

## Output (files in this folder)
- The 7 PNGs above.
- `ficha-movil.md`: build spec for Claude: per screen, a table «Elemento | Tamaño y posición (dp, origin top-left,
  800 × 360) | Color / fuente / tamaño de letra (sp)», the paddings and gaps, what scrolls (if anything), and for m4 the
  states and texts. Plus «How it was generated»: tool, references, prompt.
Nothing else.
