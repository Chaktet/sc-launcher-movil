# SC Launcher Android — ficha de construcción

## Alcance y precisión

Seis mockups horizontales y una hoja de contacto. Es la adaptación del launcher de escritorio 1.17.8: marco, jerarquía, componentes y arte existentes. Sin Galería, selección de perfil ni ZIP de soporte.

Los PNG son referencias visuales generadas, no capturas de una aplicación implementada. La generación aproxima los tipos, colores, ilustración y proporciones; **no garantiza identidad píxel a píxel con el escritorio ni cumplimiento de los tamaños táctiles dibujados**. Para construir, mandan los tokens y las cotas de esta ficha; no medir los controles sobre los PNG. El arte de producción debe recortarse directamente de `ref/ref-fondo-valle.png`, sin usar su reinterpretación generada. Las fuentes deben ser los archivos reales de Silkscreen y Source Sans 3.

## Sistema común

- Lienzo lógico: 800 × 360 dp, origen superior izquierdo. Notación de las tablas: `x, y; ancho × alto`.
- Exportación: 2400 × 1080 px, factor 3. A 1600 × 720 px el factor es 2; conservar las mismas cotas en dp. La resolución física por sí sola no determina la densidad Android: resolver con el tamaño lógico real.
- Zona segura de aplicación: x=48…784, y=16…344. Incluye 32 dp para cámara a la izquierda más 16 dp de margen. Ajustar a los insets reales si son mayores.
- Controles interactivos: área táctil mínima 44 × 44 dp. Un icono o un interruptor puede dibujarse menor dentro de esa área.
- Paddings generales: 12 dp dentro de paneles, 16 dp en formularios/diálogos. Gaps: 8 dp entre tarjetas y controles, 4 dp entre pestañas. No reducir tipografía o áreas táctiles para copiar la escala aparente de las imágenes.
- Silkscreen: logo y titulares de campaña en mayúsculas. Source Sans 3: formularios, títulos de contenido, etiquetas y botones. Cuerpo 14 sp, ayuda 12 sp, etiquetas 14 sp semibold, títulos 20 sp bold; excepciones explícitas en tablas.
- Esquinas escalonadas de 12/8/4; sin radios redondeados. Contorno exterior tinta de 3 unidades del diseño; líneas interiores azul 2 y clara 2. Aplicar coherentemente al escalado de densidad. Mantener el bisel claro superior y oscuro inferior del escritorio.
- No usar estados hover. Estado seleccionado azul oscuro con indicador dorado. Estado deshabilitado gris.
- Datos de cuenta, jugadores, evento, versión y progreso son los ejemplos suministrados; en ejecución se reemplazan por datos reales. No fijar el contador del evento ni los nombres de usuario.
- El teclado de m2 es interfaz del sistema, no un componente del launcher.

| Token | Valor |
|---|---|
| Marco / oscuro / claro | #B22839 / #6E1B2C / #D2404F |
| Panel / papel | #F6F0DE / #FFFCF2 |
| Tinta / secundario | #16233E / #415875 |
| Borde azul | #466D94 |
| Botón / bisel oscuro / pulsación | #1F5FB8 / #17488C / #2A71CF |
| Pestaña | #A8CEEE |
| Dorado | #E7BF57 |
| Evento / fondo oferta | #2F6B2F / #DDEFD6 |
| Línea interior | #E9F2FA |
| Deshabilitado | #D8D5C9 |

## m1 — elegir cuenta

| Elemento | Tamaño y posición (dp, origen superior izquierdo, 800 × 360) | Color / fuente / tamaño de letra (sp) |
|---|---|---|
| Cabecera | 48,16; 736×44 | Marco rojo; logo original |
| Luz y tres pilotos | 60,22; 76×32 | Copiar referencia de escritorio |
| Logo | 148,24; 248×28 | Papel; Silkscreen 16 |
| Panel general | 48,72; 736×272 | Crema, borde común |
| Tarjeta de arte | 60,84; 276×248 | Valle recortado arriba, transición a crema |
| MINECRAFT × POKÉMON | 72,218; 252×14 | Rojo; Source Sans 3 bold 11 |
| TU PRÓXIMA / AVENTURA / EMPIEZA AQUÍ. | 72,238; 252×66 | Tinta; Silkscreen 20, interlínea 22 |
| servidorcobblemon.es | 72,312; 252×16 | Secundario; Source Sans 3 12 |
| Tarjeta de cuenta | 348,92; 424×232 | Papel; padding 16 |
| TU CUENTA | 364,104; 392×14 | Rojo; Source Sans 3 bold 11 |
| ¿Cómo quieres entrar? | 364,124; 392×26 | Tinta; Source Sans 3 bold 20 |
| Texto introductorio | 364,152; 392×18 | Tinta; Source Sans 3 14 |
| Premium (cuenta Microsoft) | 364,172; 392×44 | Azul, papel; Source Sans 3 bold 16; icono Microsoft |
| No Premium (solo nombre) | 364,224; 392×44 | Pestaña, tinta; Source Sans 3 bold 16; icono de escritorio |
| Nota Microsoft | 364,284; 392×32 | Secundario; Source Sans 3 12, dos líneas |

Texto de nota: «Microsoft abre su propia ventana para iniciar sesión de forma segura». Sin scroll a tamaño base. El eslogan secundario del escritorio puede omitirse si no cabe; no reducir el titular.

## m2 — No Premium, teclado abierto

| Elemento | Tamaño y posición (dp, origen superior izquierdo, 800 × 360) | Color / fuente / tamaño de letra (sp) |
|---|---|---|
| Cabecera | 48,16; 736×44 | Igual a m1 |
| Tarjeta de arte compacta | 48,72; 260×136 | Valle recortado y crema |
| Titular de campaña | 60,166; 236×34 | Tinta; Silkscreen 14, dos líneas |
| Formulario | 320,64; 464×148 | Papel; padding 16 |
| CUENTA NO PREMIUM | 336,68; 432×12 | Rojo; Source Sans 3 bold 10 |
| Entra con tu nombre | 336,80; 432×24 | Tinta; Source Sans 3 bold 20 |
| Campo de nombre | 336,108; 432×44 | Papel; contorno tinta; Source Sans 3 16 |
| Etiqueta Nombre de usuario | Dentro del campo, arriba; 12 dp de padding horizontal | Secundario; Source Sans 3 semibold 11 |
| Texto Tu nombre en el juego | Dentro del campo, debajo de etiqueta | Tinta; Source Sans 3 16 |
| ¿Recordarme? e interruptor | 336,160; 160×44 | Tinta; Source Sans 3 semibold 14; interruptor azul |
| ENTRAR | 504,160; 160×44 | Azul/papel; Source Sans 3 bold 16 |
| Cancelar | 672,160; 96×44 | Pestaña/tinta; Source Sans 3 semibold 14 |
| Teclado Android ilustrativo | 0,220; 800×140 | Lo controla Android; no copiar las teclas al launcher |

Campo completo en la primera mitad: y=108…152 < 180. El teclado comienza en y=220, con 16 dp libres después de las acciones. La posición real depende del teclado instalado: redimensionar el área disponible con los insets del teclado y desplazar verticalmente el formulario si hace falta; nunca superponer teclado y campo. El arte puede ocultarse cuando el teclado ocupe más altura.

Con teclado cerrado, el panel recupera la altura de m1 y muestra «Tu cuenta se protege con contraseña DENTRO del servidor (/register y /login). SC Launcher no está afiliado a Mojang AB.» en 12 sp. ENTRAR usa el gris deshabilitado cuando el nombre no es válido. El mockup representa la colocación del formulario; no determina la validación del nombre.

## m3 — inicio

| Elemento | Tamaño y posición (dp, origen superior izquierdo, 800 × 360) | Color / fuente / tamaño de letra (sp) |
|---|---|---|
| Cabecera | 48,16; 736×44 | Rojo; luces y logo de m1 |
| Cuenta y Cambiar skin | 636,16; 148×44 | Papel; cabeza 28×28; nombre 14 sp bold, enlace 12 sp |
| Cuatro pestañas | 48,64; 736×44 | Jugar/Eventos/Novedades/Ajustes; anchos 181, gaps 4; Source Sans 3 bold 16 |
| Panel general | 48,112; 736×188 | Crema |
| Hero | 56,120; 720×48 | Valle recortado a la derecha; transición a crema izquierda |
| MINECRAFT × POKÉMON | 68,124; 350×12 | Rojo; Source Sans 3 bold 10 |
| TU AVENTURA, A UN CLIC. | 68,140; 540×24 | Tinta; Silkscreen 20 |
| Tarjeta de juego | 56,176; 448×116 | Papel |
| REGIONES EN LÍNEA | 68,180; 190×12 | Tinta; Source Sans 3 bold 10 |
| Kanto · 24 jugadores | 68,194; 190×14 | Tinta; Source Sans 3 12; indicador verde |
| Kalos · 28 jugadores | 68,208; 190×14 | Tinta; Source Sans 3 12; indicador verde |
| Servidor Cobblemon · Móvil | 270,184; 222×40 | Tinta; Source Sans 3 bold 16; permite dos líneas |
| JUGAR | 68,230; 276×50 | Azul/papel; Source Sans 3 bold 22; triángulo derecho |
| Reparar archivos | 352,230; 140×50 | Pestaña/tinta; Source Sans 3 semibold 14 |
| Próximo evento, tarjeta completa interactiva | 512,176; 264×54 | Papel; verde 10 sp para etiqueta, título 16 sp bold |
| Community Day / Empieza en 5 d 21 h 44 min | 524,193; 240×32 | Tinta/secundario; Source Sans 3 14/12 |
| Novedad, tarjeta completa interactiva | 512,238; 264×54 | Papel; estrella dorada; etiqueta roja 9 sp |
| Menús nuevos y el PokéNav | 524,256; 240×28 | Tinta; Source Sans 3 bold 14; permite dos líneas |
| Pie | 48,300; 736×44 | Marco rojo |
| Versión | 48,312; 170×16 | Papel; Source Sans 3 12 |
| servidorcobblemon.es, área de enlace | 244,300; 280×44 | Papel; Source Sans 3 12; centrado |
| Web / Tienda / Discord | 536,300; 248×44 | Anchos 72/80/80 y gaps 8; Source Sans 3 bold 14; Tienda dorada |

Las pestañas empiezan en x=48,233,418,603. Sin scroll. No mostrar selector Cambiar, RAM del perfil de PC ni «Elige tu perfil». Los PNG pueden conservar proporciones de hero mayores que estas cotas; usar esta distribución para garantizar altura táctil. El icono del calendario no crea otro control pequeño: pertenece a la tarjeta completa.

## m4 — preparando y error alternativo

Misma estructura, arte, pestañas, evento y novedad que m3. No habilitar otra ejecución de JUGAR durante la preparación.

| Elemento | Tamaño y posición (dp, origen superior izquierdo, 800 × 360) | Color / fuente / tamaño de letra (sp) |
|---|---|---|
| Tarjeta de juego | 56,176; 448×116 | Papel; mismo borde |
| Nombre móvil y regiones compactas | 68,180; 424×30 | Tinta; Source Sans 3 12/14 |
| Estado de descarga | 68,212; 424×18 | Tinta; Source Sans 3 bold 14 |
| Progreso | 68,234; 424×20 | Contorno tinta, relleno azul y restante pestaña |
| Estado de comprobación | 68,258; 424×18 | Secundario; Source Sans 3 12 |
| Muestra de error alternativa en la lámina | 56,276; 448×68 | Papel; tapa una porción del pie solo en esta documentación |
| Mensaje de error | 68,290; 272×36 | Tinta/secundario; Source Sans 3 bold 14 / regular 12 |
| Reintentar en la muestra | 356,288; 136×44 | Pestaña/tinta; Source Sans 3 bold 16 |

Estados y textos:
1. Descarga: «Actualizando el modpack · 38 / 73 MB», progreso proporcional 38/73 = 52,05 %.
2. Comprobación: «Comprobando archivos…». La imagen muestra ambos textos para enseñar los dos estilos; en ejecución actualizar el mensaje a la fase real, sin anunciar descarga y comprobación si no ocurren a la vez.
3. Error: «No se pudo descargar. Revisa tu conexión» y «Reintentar». Sustituye el contenido de progreso dentro de la tarjeta de juego; no añadir un error flotante permanente sobre el pie.
4. Reintento: vuelve al estado de preparación; no abre otra pantalla inventada.

La muestra inferior es un estado alternativo solicitado, no una segunda tarjeta visible durante el funcionamiento normal. Sin scroll.

## m5 — actualizar aplicación y permiso

La imagen muestra dos estados alternativos uno junto al otro para revisión. En la aplicación se presenta **un solo diálogo centrado**, sobre la pantalla actual. No implementar dos diálogos simultáneos ni copiar opciones de PC al fondo.

| Elemento | Tamaño y posición (dp, origen superior izquierdo, 800 × 360) | Color / fuente / tamaño de letra (sp) |
|---|---|---|
| Diálogo real, ambos estados | 184,68; 464×224 | Papel, borde escalonado; padding 16 |
| Icono i | 200,84; 32×32 | Azul/papel; copiar lenguaje de escritorio |
| Título | 244,84; 388×32 | Tinta; Silkscreen 18 |
| Mensaje | 200,132; 432×84 | Tinta; Source Sans 3 16, interlínea 20 |
| Botón secundario | 200,232; 208×44 | Pestaña/tinta; Source Sans 3 bold 16 |
| Botón principal | 424,232; 208×44 | Azul/papel; Source Sans 3 bold 16 |
| Panel izquierdo, solo lámina | 48,64; 344×248 | Estado Actualizar; disposición ilustrativa |
| Panel derecho, solo lámina | 408,64; 376×248 | Estado Permiso de Android; disposición ilustrativa |

Estado 1: título «ACTUALIZAR»; mensaje «Hay una versión nueva de SC Launcher.»; botones «Más tarde» y «Actualizar».

Estado 2: título «PERMISO DE ANDROID»; mensaje «Android te pedirá una vez permiso para instalar apps desde SC Launcher. Activa el permiso y vuelve para continuar.»; botones «Más tarde» y «Sí, abrirlo». El sistema decide si hace falta el permiso: comprobar su estado, no pedirlo otra vez si ya está concedido. El mockup no representa la pantalla de ajustes del propio Android.

Sin scroll a tamaño base. Con fuente de accesibilidad grande, desplazar solo el mensaje y mantener botones accesibles.

## m6 — ajustes

| Elemento | Tamaño y posición (dp, origen superior izquierdo, 800 × 360) | Color / fuente / tamaño de letra (sp) |
|---|---|---|
| AJUSTES y subtítulo | 48,16; 440×44 | Papel; Silkscreen 22 y Source Sans 3 12 |
| Logo | 560,20; 224×32 | Original |
| Panel general | 48,68; 736×276 | Crema |
| Ajustes seleccionado | 60,80; 128×44 | Azul oscuro/papel; Source Sans 3 bold 16 |
| Acerca de | 60,132; 128×44 | Pestaña/tinta; Source Sans 3 bold 16 |
| Listo | 60,288; 128×44 | Azul/papel; Source Sans 3 bold 16 |
| Memoria RAM y valor | 216,84; 264×18 | Tinta/secundario; Source Sans 3 semibold 14; valor según configuración real |
| Deslizador RAM, área táctil | 216,102; 264×44 | Azul y pestaña; pista visual 8 dp; cursor cuadrado 16 dp |
| Renderizador, etiqueta | 216,150; 264×16 | Tinta; Source Sans 3 semibold 14 |
| Renderizador, selector | 216,166; 264×44 | Pestaña/tinta; Source Sans 3 16 |
| Controles táctiles, fila interactiva | 216,218; 264×44 | Tinta; Source Sans 3 semibold 14; interruptor azul |
| Idioma, etiqueta | 504,84; 264×18 | Tinta; Source Sans 3 semibold 14 |
| Idioma, selector Español | 504,102; 264×44 | Pestaña/tinta; Source Sans 3 16 |
| Cuenta, usuario | 504,166; 264×24 | Tinta; Source Sans 3 bold 14 |
| Cerrar sesión | 620,194; 148×44 | Papel/rojo; Source Sans 3 bold 14 |
| Acerca de, bloque informativo | 216,274; 552×60 | Papel; padding 8 |
| Créditos y licencia | 228,282; 528×44 | Tinta/secundario; Source Sans 3 14/12 |

Créditos: «Basado en FoldCraftLauncher» y «Licencia GPL-3.0». La imagen usa «Automática» y «Automático» como ejemplos de estado. No definen una modalidad nueva: si la aplicación no la ofrece, usar el valor real de memoria y renderizador. No inventar otras opciones del desplegable, límites de RAM ni idiomas.

Sin scroll a tamaño base; si la ventana o el tamaño de texto disponible cambia, desplazar únicamente el contenido a la derecha. Barra lateral y Listo permanecen accesibles.

## Verificación y límites

- Comprobación de los siete archivos PNG: seis de 2400×1080 y hoja de 2048×190.
- Exportación a 1600×720 comprobada en memoria, sin crear otros entregables. La relación es idéntica, 20:9; no hay recorte adicional.
- Las cotas propuestas de paneles quedan dentro de 800×360; el contenido seguro de aplicación queda entre x=48…784 y y=16…344. El teclado del sistema ocupa también el espacio exterior del launcher.
- A densidad 2, 44 dp corresponden a 88 px; a densidad 3, a 132 px. Debe verificarse el área táctil en la implementación, porque el generador no respeta las cotas con exactitud.
- Revisión visual de las seis pantallas: se mantienen el marco rojo, la superficie crema, el borde pixelado, los botones azules y la jerarquía del escritorio. Se revisó el campo de nombre con teclado, la retirada de Galería/ZIP/selector de perfil, la variante de error y los dos estados de actualización.
- No se ha ejecutado una app Android ni se ha verificado accesibilidad en un teléfono. La reproducción exacta del CSS, fuentes, logo y arte se debe validar al construir; estas imágenes no prueban esa identidad.
- La hoja resumen reduce las seis imágenes en una sola fila; es una vista de conjunto, no una referencia para leer textos.

## How it was generated

Tool: built-in `image_gen.imagegen`; no API key or external image API runner. Local PowerShell / System.Drawing performed only file copying, resizing and assembling the overview sheet; it did not redraw the interface.

References:
- `ref/01-pc-elegir-cuenta.png`: m1, diálogos.
- `ref/02-pc-no-premium.png`: m2.
- `ref/03-pc-inicio.png`: m3 y m4.
- `ref/05-pc-ajustes.png`: m6 y estilo de paneles.
- `ref/ref-fondo-valle.png`: único arte de escena solicitado al generador.
- `ref/04-pc-novedades.png`: referencia visual suministrada, sin pantalla nueva de Novedades en este alcance.
- `ref/icono-sc.png`: entregado para identidad de aplicación; no se inventó un acceso adicional dentro de la interfaz.

Se generó una imagen por pantalla. Las correcciones usaron el PNG anterior como referencia. Se corrigieron el titular de m1, el tamaño relativo del hero y los textos de perfil de m3, la coherencia de m4 con m3 y el fondo de m5. Los originales generados tenían 1870×841 px; los entregables se normalizaron a 2400×1080. La hoja se montó a 2048×190.

### Prompts exactos

Las instrucciones de generación se conservan a continuación para reproducir el proceso. Las cotas de las tablas anteriores resuelven las diferencias entre las proporciones generadas y la distribución que debe implementar Claude.

#### m1 inicial

```text
Use case: ui-mockup. Produce ONE flat Android landscape app screenshot, exactly 2400x1080 pixels, full bleed, NO phone/device mockup, no perspective, no OS clock, no desktop titlebar. This is a faithful rearrangement of the supplied SC Launcher desktop screenshots, NOT a redesign. All coordinates below in dp on 800x360 logical canvas (multiply by 3). Same pixel-cut stepped 12/8/4 corners, ink #16233E 3px outlines at logical desktop scale, blue #466D94 plus light inner lines; light top/dark bottom bevel buttons. Palette strictly frame #B22839 dark #6E1B2C light #D2404F; cream #F6F0DE paper #FFFCF2 ink #16233E muted #415875 blue buttons #1F5FB8 dark #17488C, tabs #A8CEEE gold #E7BF57 disabled #D8D5C9 green #2F6B2F. Silkscreen uppercase pixel titles/logo, Source Sans 3 body, crisp readable Spanish text 14-16sp labels and 20sp headings. Copy the supplied logo/device lights and graphic language. Safe content x48..784,y16..344. Red frame outside. Use ONLY the supplied valley artwork cropped, do not invent or repaint scenery or characters. No extra features, no prices, no Pokémon names in text. One image only. Screen m1-elegir-cuenta. Reference image 1 account choice is exact style/layout source; reference image 2 is supporting original valley art. Header at x48 y16 h44: blue orb and three small red yellow green lights then SERVIDOR COBBLEMON pixel wordmark. Main cream outlined panel x48 y72 w736 h272. Left art card x60 y84 w276 h248: original valley image upper part fading into cream exactly as desktop; small red MINECRAFT × POKÉMON; pixel headline TU PRÓXIMA / AVENTURA / EMPIEZA AQUÍ. and small servidorcobblemon.es. Right paper panel x348 y92 w424 h232 inner padding16. Red eyebrow TU CUENTA then ¿Cómo quieres entrar? then Elige el tipo de cuenta que usas para jugar. Full width blue button at x364 y172 w392 h44 with Windows four squares icon and Premium (cuenta Microsoft). Pale blue button x364 y224 w392 h44 with same desktop red account icon and No Premium (solo nombre). Tiny muted note at y286 Microsoft abre su propia ventana para iniciar sesión de forma segura. Keep exact style of desktop, not round modern mobile UI.
```

#### m1 corrección

```text
Use case: ui-mockup. Produce ONE flat Android landscape app screenshot, exactly 2400x1080 pixels, full bleed, NO phone/device mockup, no perspective, no OS clock, no desktop titlebar. This is a faithful rearrangement of the supplied SC Launcher desktop screenshots, NOT a redesign. All coordinates below in dp on 800x360 logical canvas (multiply by 3). Same pixel-cut stepped 12/8/4 corners, ink #16233E 3px outlines at logical desktop scale, blue #466D94 plus light inner lines; light top/dark bottom bevel buttons. Palette strictly frame #B22839 dark #6E1B2C light #D2404F; cream #F6F0DE paper #FFFCF2 ink #16233E muted #415875 blue buttons #1F5FB8 dark #17488C, tabs #A8CEEE gold #E7BF57 disabled #D8D5C9 green #2F6B2F. Silkscreen uppercase pixel titles/logo, Source Sans 3 body, crisp readable Spanish text 14-16sp labels and 20sp headings. Copy the supplied logo/device lights and graphic language. Safe content x48..784,y16..344. Red frame outside. Use ONLY the supplied valley artwork cropped, do not invent or repaint scenery or characters. No extra features, no prices, no Pokémon names in text. One image only. Edit target reference1 m1 screenshot. Change ONLY a text spelling error in pixel headline at lower left: it MUST read exactly TU PRÓXIMA / AVENTURA / EMPIEZA AQUÍ. The word is E M P I E Z A, not EMPTEZA. Keep reference1 composition, account card, typography, art, lights and wordmark. Also enforce red safe strip at left32dp: shift left outline and its content to48dp from left edge, retaining right16dp margin; fit all content between48..784dp. All buttons remain44dp or higher at800x360 logical resolution. Preserve EVERYTHING else.2400x1080 full-bleed app screenshot.
```

#### m2

```text
Use case: ui-mockup. Produce ONE flat Android landscape app screenshot, exactly 2400x1080 pixels, full bleed, NO phone/device mockup, no perspective, no OS clock, no desktop titlebar. This is a faithful rearrangement of the supplied SC Launcher desktop screenshots, NOT a redesign. All coordinates below in dp on 800x360 logical canvas (multiply by 3). Same pixel-cut stepped 12/8/4 corners, ink #16233E 3px outlines at logical desktop scale, blue #466D94 plus light inner lines; light top/dark bottom bevel buttons. Palette strictly frame #B22839 dark #6E1B2C light #D2404F; cream #F6F0DE paper #FFFCF2 ink #16233E muted #415875 blue buttons #1F5FB8 dark #17488C, tabs #A8CEEE gold #E7BF57 disabled #D8D5C9 green #2F6B2F. Silkscreen uppercase pixel titles/logo, Source Sans 3 body, crisp readable Spanish text 14-16sp labels and 20sp headings. Copy the supplied logo/device lights and graphic language. Safe content x48..784,y16..344. Red frame outside. Use ONLY the supplied valley artwork cropped, do not invent or repaint scenery or characters. No extra features, no prices, no Pokémon names in text. One image only. Screen m2-no-premium. Copy visual identity exactly from reference 1 desktop No Premium. Supporting reference2 original valley art. Android keyboard visibly open in bottom region y220..344 full safe width x48..784, clean Android QWERTY Spanish keys in muted cream/ink, no clock. App header x48 y16 h44 blue orb, red yellow green dots, SERVIDOR COBBLEMON. Above keyboard, left compact art card x48 y72 w260 h136 original valley art with cream fade, tiny pixel TU PRÓXIMA AVENTURA EMPIEZA AQUÍ. Right paper form x320 y64 w464 h148. Main heading Entra con tu nombre, small red CUENTA NO PREMIUM. Name field x336 y108 w432 h44, label Nombre de usuario embedded above its text, placeholder Tu nombre en el juego, text cursor. This field is entirely in top half, NEVER obscured by keyboard. Bottom control row y160 h44: remember toggle with label ¿Recordarme? x336 width160; blue ENTRAR button x504 w160; pale Cancelar button x672 w96. Small account safety text can be omitted in keyboard-open view to keep all touch controls44dp. Keyboard is OS UI; app controls retain exact desktop stepped borders. Show one single landscape screenshot, not multiple panels.
```

#### m3 inicial

```text
Use case: ui-mockup. Produce ONE flat Android landscape app screenshot, exactly 2400x1080 pixels, full bleed, NO phone/device mockup, no perspective, no OS clock, no desktop titlebar. This is a faithful rearrangement of the supplied SC Launcher desktop screenshots, NOT a redesign. All coordinates below in dp on 800x360 logical canvas (multiply by 3). Same pixel-cut stepped 12/8/4 corners, ink #16233E 3px outlines at logical desktop scale, blue #466D94 plus light inner lines; light top/dark bottom bevel buttons. Palette strictly frame #B22839 dark #6E1B2C light #D2404F; cream #F6F0DE paper #FFFCF2 ink #16233E muted #415875 blue buttons #1F5FB8 dark #17488C, tabs #A8CEEE gold #E7BF57 disabled #D8D5C9 green #2F6B2F. Silkscreen uppercase pixel titles/logo, Source Sans 3 body, crisp readable Spanish text 14-16sp labels and 20sp headings. Copy the supplied logo/device lights and graphic language. Safe content x48..784,y16..344. Red frame outside. Use ONLY the supplied valley artwork cropped, do not invent or repaint scenery or characters. No extra features, no prices, no Pokémon names in text. One image only. Screen m3-inicio. Reference1 desktop main screenshot is source of exact identity and widgets, reference2 validated background. Header x48 y16 w736 h44 blue orb+3 dots, SERVIDOR COBBLEMON pixel logo at left; account paper box x636 y16 w148 h44 with skin head EntrenadorSC and Cambiar skin. Four equal width beveled tabs x48 y64 h44: Jugar (dark blue active), Eventos, Novedades with tiny gold marker, Ajustes. Main cream panel x48 y112 w736 h188. Hero x56 y120 w720 h48 original valley crop to right with cream fade at left, small red MINECRAFT × POKÉMON, pixel TU AVENTURA, A UN CLIC. Main play card x56 y176 w448 h116: top row small REGIONES EN LÍNEA, Kanto 24 jugadores, Kalos 28 jugadores at left; Servidor Cobblemon · Móvil at right. Big full blue JUGAR button x68 y230 w276 h50 white triangle; pale Reparar archivos x352 y230 w140 h50. Right cards x512 y176 w264 h54 and y238 h54: green PRÓXIMO EVENTO / Community Day / Empieza en 5 d 21 h 44 min; red gold-star LA GRAN NOVEDAD DEL SERVIDOR / Menús nuevos y el PokéNav / Léela en Noticias. Footer x48 y304 h40 text v1.17.8 · Estás al día, servidorcobblemon.es; Web, gold Tienda, Discord buttons each touch44 high. No Galería, no Cambiar profile, no ZIP. All components copy desktop reference without inventing style.
```

#### m3 corrección

```text
Use case: ui-mockup. Produce ONE flat Android landscape app screenshot, exactly 2400x1080 pixels, full bleed, NO phone/device mockup, no perspective, no OS clock, no desktop titlebar. This is a faithful rearrangement of the supplied SC Launcher desktop screenshots, NOT a redesign. All coordinates below in dp on 800x360 logical canvas (multiply by 3). Same pixel-cut stepped 12/8/4 corners, ink #16233E 3px outlines at logical desktop scale, blue #466D94 plus light inner lines; light top/dark bottom bevel buttons. Palette strictly frame #B22839 dark #6E1B2C light #D2404F; cream #F6F0DE paper #FFFCF2 ink #16233E muted #415875 blue buttons #1F5FB8 dark #17488C, tabs #A8CEEE gold #E7BF57 disabled #D8D5C9 green #2F6B2F. Silkscreen uppercase pixel titles/logo, Source Sans 3 body, crisp readable Spanish text 14-16sp labels and 20sp headings. Copy the supplied logo/device lights and graphic language. Safe content x48..784,y16..344. Red frame outside. Use ONLY the supplied valley artwork cropped, do not invent or repaint scenery or characters. No extra features, no prices, no Pokémon names in text. One image only. EDIT reference1 (our first landscape draft). Keep exact identity but FIX GEOMETRY, text and touch sizes. The first draft retained an oversized hero and undersized tabs. Draw a much shorter hero, only48dp tall, ONE line TU AVENTURA, A UN CLIC. Remove Elige tu perfil text and RAM automática · 8 GB. Allocate saved height to tabs and footer. This screen must be genuinely usable in landscape360dp height. Left notch safe margin48dp must be empty red along entire image, all content begins x48 or further. Header y16..60 (44dp), tabs y64..108 (44dp), hero y120..168 (48dp), play and event cards y176..292(116dp), footer y300..344(44dp). All these are STRICT ratios of the800x360 canvas, not suggestions. Header small logo height32dp, four tabs Jugar/Eventos/Novedades/Ajustes, account EntrenadorSC. Main card has REGIONES EN LÍNEA Kanto24 jugadores Kalos28 jugadores, Servidor Cobblemon · Móvil. Big JUGAR x68y230w276h50 and Reparar archivos x352y230w140h50. Two right cards stacked each54dp. footer three Web/Tienda/Discord touch44dp buttons. SAME PIXEL STYLE. No extra text. Original valley reference3 as crop. Output 2400x1080.
```

#### m4 inicial

```text
Use case: ui-mockup. Produce ONE flat Android landscape app screenshot, exactly 2400x1080 pixels, full bleed, NO phone/device mockup, no perspective, no OS clock, no desktop titlebar. This is a faithful rearrangement of the supplied SC Launcher desktop screenshots, NOT a redesign. All coordinates below in dp on 800x360 logical canvas (multiply by 3). Same pixel-cut stepped 12/8/4 corners, ink #16233E 3px outlines at logical desktop scale, blue #466D94 plus light inner lines; light top/dark bottom bevel buttons. Palette strictly frame #B22839 dark #6E1B2C light #D2404F; cream #F6F0DE paper #FFFCF2 ink #16233E muted #415875 blue buttons #1F5FB8 dark #17488C, tabs #A8CEEE gold #E7BF57 disabled #D8D5C9 green #2F6B2F. Silkscreen uppercase pixel titles/logo, Source Sans 3 body, crisp readable Spanish text 14-16sp labels and 20sp headings. Copy the supplied logo/device lights and graphic language. Safe content x48..784,y16..344. Red frame outside. Use ONLY the supplied valley artwork cropped, do not invent or repaint scenery or characters. No extra features, no prices, no Pokémon names in text. One image only. Screen m4-preparando. Exact desktop visual copy from reference1 main screen; original valley reference2. Header x48 y16 w736 h44 blue orb+3 dots, SERVIDOR COBBLEMON pixel logo at left; account paper box x636 y16 w148 h44 with skin head EntrenadorSC and Cambiar skin. Four equal width beveled tabs x48 y64 h44: Jugar (dark blue active), Eventos, Novedades with tiny gold marker, Ajustes. Main cream panel x48 y112 w736 h188. Hero x56 y120 w720 h48 original valley crop to right with cream fade at left, small red MINECRAFT × POKÉMON, pixel TU AVENTURA, A UN CLIC. Main play card x56 y176 w448 h116: top row small REGIONES EN LÍNEA, Kanto 24 jugadores, Kalos 28 jugadores at left; Servidor Cobblemon · Móvil at right. Big full blue JUGAR button x68 y230 w276 h50 white triangle; pale Reparar archivos x352 y230 w140 h50. Right cards x512 y176 w264 h54 and y238 h54: green PRÓXIMO EVENTO / Community Day / Empieza en 5 d 21 h 44 min; red gold-star LA GRAN NOVEDAD DEL SERVIDOR / Menús nuevos y el PokéNav / Léela en Noticias. Footer x48 y304 h40 text v1.17.8 · Estás al día, servidorcobblemon.es; Web, gold Tienda, Discord buttons each touch44 high. No Galería, no Cambiar profile, no ZIP. All components copy desktop reference without inventing style.Change the play area: instead of JUGAR and repair buttons show a blue beveled download progress bar at x68 y222 w424 h24, fill52%, text Actualizando el modpack · 38 / 73 MB above at y202, Comprobando archivos… below at y250. Keep other header/tabs/cards unchanged. Required SECOND STATE on the SAME image: a small paper cream inset variant across bottom-left x56 y276 w448 h68, ink/blue stepped border, text No se pudo descargar. / Revisa tu conexión at x68 y288 and pale-blue Reintentar button at x356 y288 w136 h44. This inset is a build-state alternate, not a simultaneous error during download; label subtly ERROR at its top edge. It may cover footer on left; right footer Web Tienda Discord still visible. Single screenshot main progress state plus small error alternate inset. Absolutely no modern rounded shapes.
```

#### m4 corrección

```text
Use case: ui-mockup. Produce ONE flat Android landscape app screenshot, exactly 2400x1080 pixels, full bleed, NO phone/device mockup, no perspective, no OS clock, no desktop titlebar. This is a faithful rearrangement of the supplied SC Launcher desktop screenshots, NOT a redesign. All coordinates below in dp on 800x360 logical canvas (multiply by 3). Same pixel-cut stepped 12/8/4 corners, ink #16233E 3px outlines at logical desktop scale, blue #466D94 plus light inner lines; light top/dark bottom bevel buttons. Palette strictly frame #B22839 dark #6E1B2C light #D2404F; cream #F6F0DE paper #FFFCF2 ink #16233E muted #415875 blue buttons #1F5FB8 dark #17488C, tabs #A8CEEE gold #E7BF57 disabled #D8D5C9 green #2F6B2F. Silkscreen uppercase pixel titles/logo, Source Sans 3 body, crisp readable Spanish text 14-16sp labels and 20sp headings. Copy the supplied logo/device lights and graphic language. Safe content x48..784,y16..344. Red frame outside. Use ONLY the supplied valley artwork cropped, do not invent or repaint scenery or characters. No extra features, no prices, no Pokémon names in text. One image only. Edit target reference1, the corrected phone home screen. Supporting ref2 shows required progress/error concepts only. PRESERVE target ref1 EXACT header, tabs, hero crop and short one-line headline, safe layout and cards, text and colours. CHANGE ONLY JUGAR and Reparar archivos region to a progress bar and status lines: Actualizando el modpack · 38 / 73 MB, blue beveled bar filled52%, Comprobando archivos… . DO NOT add RAM or GB, DO NOT add Elige tu perfil, DO NOT increase hero height. Include a small separate alternate-state inset at bottom left with No se pudo descargar. Revisa tu conexión and Reintentar pale-blue button. This inset is only documentation of error alternate state, not a new popup feature; no new navigation. Keep all source home elements faithfully copied.2400x1080.
```

#### m5 inicial

```text
Use case: ui-mockup. Produce ONE flat Android landscape app screenshot, exactly 2400x1080 pixels, full bleed, NO phone/device mockup, no perspective, no OS clock, no desktop titlebar. This is a faithful rearrangement of the supplied SC Launcher desktop screenshots, NOT a redesign. All coordinates below in dp on 800x360 logical canvas (multiply by 3). Same pixel-cut stepped 12/8/4 corners, ink #16233E 3px outlines at logical desktop scale, blue #466D94 plus light inner lines; light top/dark bottom bevel buttons. Palette strictly frame #B22839 dark #6E1B2C light #D2404F; cream #F6F0DE paper #FFFCF2 ink #16233E muted #415875 blue buttons #1F5FB8 dark #17488C, tabs #A8CEEE gold #E7BF57 disabled #D8D5C9 green #2F6B2F. Silkscreen uppercase pixel titles/logo, Source Sans 3 body, crisp readable Spanish text 14-16sp labels and 20sp headings. Copy the supplied logo/device lights and graphic language. Safe content x48..784,y16..344. Red frame outside. Use ONLY the supplied valley artwork cropped, do not invent or repaint scenery or characters. No extra features, no prices, no Pokémon names in text. One image only. Screen m5-actualizar-app. Two alternate dialog STATES SIDE BY SIDE in ONE image, each of same app viewport logical400x360, no board captions. Two cream pixel-outlined dialogs shown on red launcher background with very faint main tabs behind, styled exactly desktop paper panels and bevel buttons. Left panel x48 y64 w344 h248: blue square i icon, pixel heading ACTUALIZAR; body Hay una versión nueva de SC Launcher. Below ¡Actualiza para seguir jugando! OMIT this sentence because not a specified feature; only use Hay una versión nueva de SC Launcher. Buttons at bottom x64 y244 w148 h44 pale Más tarde and x224 y244 w148 h44 blue Actualizar. Right panel x408 y64 w376 h248: blue square i icon, pixel heading PERMISO DE ANDROID; body Android te pedirá una vez permiso para instalar apps desde SC Launcher. Then Activa el permiso y vuelve para continuar. Buttons pale Más tarde x424 y244 w160 h44, blue Sí, abrirlo x600 y244 w168 h44. No new icons, no fake app version, no art necessary, no phone mockup. These are alternative states of same dialog, not a split-screen feature. Same desktop styles, cream paper ink text blue outlines. Output exactly2400x1080.
```

#### m5 corrección

```text
Use case: ui-mockup. Produce ONE flat Android landscape app screenshot, exactly 2400x1080 pixels, full bleed, NO phone/device mockup, no perspective, no OS clock, no desktop titlebar. This is a faithful rearrangement of the supplied SC Launcher desktop screenshots, NOT a redesign. All coordinates below in dp on 800x360 logical canvas (multiply by 3). Same pixel-cut stepped 12/8/4 corners, ink #16233E 3px outlines at logical desktop scale, blue #466D94 plus light inner lines; light top/dark bottom bevel buttons. Palette strictly frame #B22839 dark #6E1B2C light #D2404F; cream #F6F0DE paper #FFFCF2 ink #16233E muted #415875 blue buttons #1F5FB8 dark #17488C, tabs #A8CEEE gold #E7BF57 disabled #D8D5C9 green #2F6B2F. Silkscreen uppercase pixel titles/logo, Source Sans 3 body, crisp readable Spanish text 14-16sp labels and 20sp headings. Copy the supplied logo/device lights and graphic language. Safe content x48..784,y16..344. Red frame outside. Use ONLY the supplied valley artwork cropped, do not invent or repaint scenery or characters. No extra features, no prices, no Pokémon names in text. One image only. Edit reference1 m5 image. Keep both update/permission dialogs and all their text exactly as drawn, same positions, same bevels. REMOVE the entire desktop settings UI in background (Mods, Minecraft, Java, logs etc must NOT be visible). Replace background with plain solid red #B22839, no texture, no gradient and no extra text. Dialog paper surfaces must be flat #FFFCF2, outlines #16233E, inner blue #466D94. Keep ACTUALIZAR, PERMISO DE ANDROID, information icons, Más tarde/Actualizar and Más tarde/Sí, abrirlo unchanged. Single2400x1080 image showing the TWO ALTERNATIVE STATES side by side. No desktop background, no buttons beyond these.
```

#### m6

```text
Use case: ui-mockup. Produce ONE flat Android landscape app screenshot, exactly 2400x1080 pixels, full bleed, NO phone/device mockup, no perspective, no OS clock, no desktop titlebar. This is a faithful rearrangement of the supplied SC Launcher desktop screenshots, NOT a redesign. All coordinates below in dp on 800x360 logical canvas (multiply by 3). Same pixel-cut stepped 12/8/4 corners, ink #16233E 3px outlines at logical desktop scale, blue #466D94 plus light inner lines; light top/dark bottom bevel buttons. Palette strictly frame #B22839 dark #6E1B2C light #D2404F; cream #F6F0DE paper #FFFCF2 ink #16233E muted #415875 blue buttons #1F5FB8 dark #17488C, tabs #A8CEEE gold #E7BF57 disabled #D8D5C9 green #2F6B2F. Silkscreen uppercase pixel titles/logo, Source Sans 3 body, crisp readable Spanish text 14-16sp labels and 20sp headings. Copy the supplied logo/device lights and graphic language. Safe content x48..784,y16..344. Red frame outside. Use ONLY the supplied valley artwork cropped, do not invent or repaint scenery or characters. No extra features, no prices, no Pokémon names in text. One image only. Screen m6-ajustes. Copy reference desktop settings screenshot with same red header pixel AJUSTES, smaller subtitle Tu cuenta, tu juego y las opciones del launcher. and SERVIDOR COBBLEMON logo top right. Need Android 800x360 logical composition, DO NOT retain tall desktop sidebar. Header x48 y16 w736 h44. Cream main panel x48 y68 w736 h276. Left pale blue sidebar x60 y80 w148: ONLY Ajustes dark selected, Acerca de pale, and Listo blue bottom; each button44dp high. Right content x224 y80 w544 h248, TWO columns of settings to fit phone: first col x224 width264, second x504 width264. First col top label Memoria RAM, horizontal ink outlined blue slider with square thumb in row h44; small value Automática (do not invent numeric GB). Next row Renderizador dropdown h44 shows Automático; next Controles táctiles toggle h44. Second col Idioma dropdown Español rowh44; Cuenta label EntrenadorSC followed Cerrar sesión outlined button44dp. Bottom across right area pale paper info Acerca de with text Basado en FoldCraftLauncher and Licencia GPL-3.0, no extra features. This settings screen keeps all six requested settings visible; controls touch44dp, ample horizontal space, no scroll required. Exactly desktop beveled pixel edges, no rounded Material controls, no new art.
```

