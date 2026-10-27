// Builds the default touch layout (assets/controllers/00000000.json) from Codex's sheet
// (E:/tmp/app-android/codex-controles/ficha-controles.md, approved design 27-10) with Felix's additions:
// ◀ / ▶ next to the hotbar (mouse wheel: previous / next slot). Usage: node monta-controles.js <00000000.json>
const fs = require('fs'), crypto = require('crypto')
const f = process.argv[2]
const j = JSON.parse(fs.readFileSync(f, 'utf8'))
const W = 2340, H = 1080          // the sheet's reference screen
const argb = (a, hex) => { const v = (a << 24) | parseInt(hex, 16); return v | 0 }  // signed int like FCL stores
const estilo = (name, stroke, sw) => ({
  name, textColor: argb(255, 'FBF7E9'), textSize: 14, strokeColor: stroke, strokeWidth: sw, cornerRadius: 30,
  fillColor: argb(89, '16233E'), textColorPressed: argb(255, '16233E'), textSizePressed: 14,
  strokeColorPressed: argb(255, 'FBF7E9'), strokeWidthPressed: sw + 4, cornerRadiusPressed: 30, fillColorPressed: argb(140, 'FBF7E9'),
})
j.buttonStyles = [estilo('Default', argb(166, 'FBF7E9'), 10), estilo('SC Servidor', argb(255, 'B22839'), 14)]
const ds = j.directionStyles[0]
ds.styleType = 'ROCKER'
Object.assign(ds.rockerStyle, { bgFillColor: argb(89, '16233E'), bgStrokeColor: argb(166, 'FBF7E9'), rockerFillColor: argb(140, 'FBF7E9'),
  rockerStrokeColor: argb(255, 'FBF7E9'), rockerSize: 330 })

const vd = j.viewGroups[0].viewData
const porTecla = new Map(vd.buttonList.map(b => [JSON.stringify([...b.event.pressEvent.outputKeycodes, ...b.event.clickEvent.outputKeycodes]), b]))
const base = vd.buttonList.find(b => b.text === 'Chat')
const vacio = e => ({ autoKeep: false, autoClick: false, openMenu: false, switchTouchMode: false, input: false, quickInput: false, outputText: '', outputKeycodes: e || [], bindViewGroup: [] })
const nuevo = () => { const b = JSON.parse(JSON.stringify(base)); b.id = crypto.randomUUID(); for (const k of ['pressEvent', 'longPressEvent', 'clickEvent', 'doubleClickEvent']) b.event[k] = vacio(); return b }
// centre (% of W, % of H) and size (% of H) → FCL position: per-mille of (screen − size), size per-mille of height
const coloca = (b, cx, cy, wH, hH, vis) => {
  const w = wH * H / 100, h = hH * H / 100
  b.baseInfo.xPosition = Math.round(Math.min(1000, Math.max(0, (cx * W / 100 - w / 2) / (W - w) * 1000)))
  b.baseInfo.yPosition = Math.round(Math.min(1000, Math.max(0, (cy * H / 100 - h / 2) / (H - h) * 1000)))
  b.baseInfo.sizeType = 'PERCENTAGE'
  b.baseInfo.percentageWidth = { reference: 'SCREEN_HEIGHT', size: Math.round(wH * 10) }
  b.baseInfo.percentageHeight = { reference: 'SCREEN_HEIGHT', size: Math.round(hH * 10) }
  b.baseInfo.visibilityType = vis
  return b
}
const lista = []
const usa = (tecla, texto, cx, cy, w, h, vis = 'IN_GAME', servidor = false, mantener = true) => {
  let b = porTecla.get(JSON.stringify(tecla))
  if (!b) { b = nuevo() }
  b.text = texto; b.style = servidor ? 'SC Servidor' : 'Default'
  for (const k of ['pressEvent', 'longPressEvent', 'clickEvent', 'doubleClickEvent']) b.event[k] = vacio()
  b.event.pressEvent = vacio(tecla)          // key held while the finger is down (hold = swim, hold = break)
  lista.push(coloca(b, cx, cy, w, h, vis))
  return b
}
// Codex table (centre X %W, centre Y %H, width %H, height %H); the left column moved up a little so ◀ fits
usa([57], 'Saltar', 90, 80, 24, 24)
usa([42], 'Agachar', 76, 83, 22, 14)
usa([1000], 'Atacar', 76, 61, 24, 18)
usa([1002], 'Usar', 90, 50, 24, 18)
usa([18], 'Inventario', 32, 81, 24, 12)
usa([16], 'Soltar', 32, 66, 20, 12)
usa([20], 'Chat', 84, 9, 18, 12)
usa([1], 'Menú', 94, 9, 18, 12, 'ALWAYS')
usa([50], 'Pokémon', 32, 35, 26, 12, 'IN_GAME', true)
usa([19], 'Lanzar', 32, 51, 22, 12, 'IN_GAME', true)
usa([103], 'Poké ▲', 13, 39, 18, 12, 'IN_GAME', true)
usa([108], 'Poké ▼', 13, 54, 18, 12, 'IN_GAME', true)
usa([1003], '◀', 35.2, 95, 10, 10)       // mouse wheel up = previous hotbar slot
usa([1004], '▶', 64.8, 95, 10, 10)       // mouse wheel down = next hotbar slot
// keyboard: the FCL «input» action (opens Android's keyboard); Enter / Backspace only while a menu or chat is open
const tec = vd.buttonList.find(b => b.event.clickEvent.input)
tec.text = 'Teclado'; tec.style = 'Default'; lista.push(coloca(tec, 73, 9, 24, 12, 'ALWAYS'))
usa([28], 'Enter', 62, 9, 16, 12, 'MENU')
usa([14], 'Borrar', 51, 9, 16, 12, 'MENU')
vd.buttonList = lista                      // F3 and anything else of the old layout goes
const d = vd.directionList[0]
coloca(d, 17, 80, 30, 30, 'IN_GAME')
j.name = 'Servidor Cobblemon'; j.version = '1.4.0'; j.versionCode = 1400; j.author = 'Servidor Cobblemon'
j.description = 'Controles del Servidor Cobblemon (diseño 27-10): joystick, Saltar (mantén para nadar), Agachar, Romper, Usar, Inventario, Soltar, Pokémon, Lanzar, Poké ▲▼, ◀ ▶ para la barra, Chat, Teclado y Menú.'
fs.writeFileSync(f, JSON.stringify(j, null, 2) + '\n')
console.log(lista.map(b => `${b.text.replace('\n', '/')}@${b.baseInfo.xPosition},${b.baseInfo.yPosition} ${b.baseInfo.visibilityType}`).join('\n'))
