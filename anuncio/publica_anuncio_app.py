#!/usr/bin/env python3
"""Felix 27-10: announcement of the Android app SC Launcher 0.1.0 as a Components V2 card (same look as the other
#anuncios posts, model: E:/RESOURCE-MC/jornadas-importacion/anuncio/publica_anuncio_ruleta.py).
Usage on Hetzner: python3 publica_anuncio_app.py prueba|anuncios
prueba = hidden «diseño-pruebas», never pings; anuncios mentions @Joven + @Anuncios (E:/PROYECTOS-MC/CLAUDE.md).
The bot token is read from the SC Guardián .env and never printed."""
import json, sys, urllib.request, uuid, os

T = next(l.split('=', 1)[1].strip() for l in open('/opt/sc-guardian/.env') if l.startswith('DISCORD_TOKEN='))
DESTINO = sys.argv[1] if len(sys.argv) > 1 else ''
CANALES = {'prueba': '1560189603921788938', 'anuncios': json.load(open('/opt/sc-guardian/data/canales.json')).get('anuncios')}
if not CANALES.get(DESTINO):
    sys.exit('uso: publica_anuncio_app.py prueba|anuncios')
AQUI = os.path.dirname(os.path.abspath(__file__))
ADJUNTO = 'app-inicio.png'
JOVEN, ANUNCIOS = '1302228308913557605', '1302232737507442749'
GUIA = 'https://servidorcobblemon.es/como-jugar/movil'
# stable link: the web redirects to the newest arm64 APK announced in sc-app.json, so the post never goes stale
APK = 'https://servidorcobblemon.es/descargas/android'
H = {'Authorization': 'Bot ' + T, 'User-Agent': 'SCGuardian (servidorcobblemon.es, 1.0)'}


def e(n, i):
    return f'<:{n}:{i}>'


LOGO, MASTER, SHINY, BALL, RIBBON, CORAZON = (
    e('sc_logo', '1560189265646981120'), e('sc_masterball', '1560189268964548718'),
    e('sc_shiny', '1560189284554637393'), e('sc_pokeball', '1560189271237722152'),
    e('sc_ribbon', '1560189280654205099'), e('sc_sweetheart', '1560189286748524596'))
texto = lambda t: {'type': 10, 'content': t}
raya = {'type': 14, 'spacing': 1, 'divider': True}
MAC, LINUX = "https://servidorcobblemon.es/como-jugar/mac", "https://servidorcobblemon.es/como-jugar/linux"
boton = lambda etiqueta, url, emoji: {"type": 2, "style": 5, "label": etiqueta, "url": url, "emoji": {"name": emoji}}
tarjeta = {"type": 17, "accent_color": 0xB22839, "components": [
    texto(f"# {LOGO} ¡Ya puedes jugar desde el móvil! 📱\n"
          "Sacamos **SC Launcher para Android**, nuestra app oficial. Es gratis y es igualita al launcher del PC."),
    {"type": 12, "items": [{"media": {"url": "attachment://" + ADJUNTO}}]},
    raya,
    texto(f"### {BALL} La instalas, le das a JUGAR y listo\n"
          "Nada de buscar mods ni pelearte con configuraciones. Entras con tu cuenta de Microsoft o en No Premium, "
          "pulsas **JUGAR** y en un rato estás dentro.\n\n"
          f"### {MASTER} Tus Pokémon van contigo\n"
          "Es el mismo servidor y la misma cuenta: tu equipo, tu PC y tu Pokédex te esperan igual que en el ordenador. "
          "Empieza una captura en casa y termínala en el bus.\n\n"
          f"### {SHINY} Te olvidas de actualizar\n"
          "Cuando sacamos algo nuevo, la app se pone al día sola. Y si algún día algo va raro, le das a "
          "**Reparar archivos** y arreglado."),
    {"type": 1, "components": [boton("Descargar la app", APK, "📲"), boton("Cómo instalarla", GUIA, "📖")]},
    texto("-# Al instalarla, Android te preguntará si permites apps de fuera de Google Play. Dile que sí: es nuestra app oficial."),
    raya,
    texto(f"### {LOGO} ¿Juegas en Mac o Linux?\n"
          "También puedes: el **SC Launcher** ya está para Mac y Linux, y funciona igual que en Windows."),
    {"type": 1, "components": [boton("Mac", MAC, "🍎"), boton("Linux", LINUX, "🐧")]},
    raya,
    texto(f"## ¡Nos vemos dentro! {CORAZON}"),
]}
componentes = [tarjeta]
if DESTINO == 'anuncios':
    componentes.insert(0, texto(f'<@&{JOVEN}> <@&{ANUNCIOS}>'))
    menciones = {'parse': [], 'roles': [JOVEN, ANUNCIOS]}
else:
    menciones = {'parse': []}

limite = '----sc' + uuid.uuid4().hex
datos = {'flags': 32768, 'components': componentes, 'allowed_mentions': menciones,
         'attachments': [{'id': 0, 'filename': ADJUNTO}]}
cuerpo = f'--{limite}\r\nContent-Disposition: form-data; name="payload_json"\r\nContent-Type: application/json\r\n\r\n'.encode()
cuerpo += json.dumps(datos).encode() + b'\r\n'
cuerpo += f'--{limite}\r\nContent-Disposition: form-data; name="files[0]"; filename="{ADJUNTO}"\r\nContent-Type: image/png\r\n\r\n'.encode()
cuerpo += open(os.path.join(AQUI, ADJUNTO), 'rb').read() + b'\r\n' + f'--{limite}--\r\n'.encode()
r = urllib.request.Request(f'https://discord.com/api/v10/channels/{CANALES[DESTINO]}/messages', data=cuerpo,
                           method='POST', headers={**H, 'Content-Type': 'multipart/form-data; boundary=' + limite})
m = json.loads(urllib.request.urlopen(r).read())
print('hecho en', DESTINO, 'mensaje', m['id'], 'flags', m.get('flags'), 'adjuntos', len(m.get('attachments', [])),
      'bloques', len(m.get('components', [])))
