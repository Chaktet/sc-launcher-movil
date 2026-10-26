// SC UI check (CI emulator, debug APK): drives our WebView page through the DevTools protocol (adb-forwarded to
// localhost:9222), walks the screens with the real Java bridge and saves a screenshot of each one in evidence/.
// Fails on JS exceptions, console errors, or a screen that does not show what it should.
import { execFileSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'

const pkg = process.argv[2]
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const shot = (name) => {
  execFileSync('adb', ['shell', 'screencap', '-p', '/sdcard/ui.png'])
  execFileSync('adb', ['pull', '/sdcard/ui.png', `evidence/${name}.png`])
  console.log('screenshot', name)
}

let targets = []
for (let i = 0; i < 30 && !targets.find((t) => t.url.includes('sc_ui/index.html')); i++) {
  try { targets = await (await fetch('http://127.0.0.1:9222/json')).json() } catch { /* not up yet */ }
  await sleep(2000)
}
const page = targets.find((t) => t.url.includes('sc_ui/index.html'))
if (!page) { console.error('::error::our page (sc_ui/index.html) is not loaded in any WebView'); process.exit(1) }

const ws = new WebSocket(page.webSocketDebuggerUrl)
let id = 0
const pend = new Map()
const errores = []
ws.onmessage = (e) => {
  const m = JSON.parse(e.data)
  if (m.id && pend.has(m.id)) { pend.get(m.id)(m); pend.delete(m.id) }
  if (m.method === 'Runtime.exceptionThrown') errores.push('exception: ' + JSON.stringify(m.params.exceptionDetails).slice(0, 400))
  if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') errores.push('console.error: ' + JSON.stringify(m.params.args).slice(0, 400))
}
await new Promise((r, j) => { ws.onopen = r; ws.onerror = j })
const call = (method, params = {}) => new Promise((r) => { const i = ++id; pend.set(i, r); ws.send(JSON.stringify({ id: i, method, params })) })
const ev = async (expr) => {
  const r = await call('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })
  if (r.result?.exceptionDetails) throw new Error('JS: ' + JSON.stringify(r.result.exceptionDetails).slice(0, 300))
  return r.result?.result?.value
}
await call('Runtime.enable')

const informe = []
const comprueba = (cond, msg) => { informe.push((cond ? 'OK   ' : 'FAIL ') + msg); if (!cond) process.exitCode = 1 }
const visible = (sel) => ev(`(() => { const e = document.querySelector(${JSON.stringify(sel)}); return !!e && !e.closest('[hidden]') && e.getBoundingClientRect().height > 0 })()`)

await sleep(3000)
comprueba(await visible('#vCuenta'), 'first start without account shows «¿Cómo quieres entrar?»')
shot('ui-1-cuenta')

await ev(`document.getElementById('btnNoPremium').click()`)
await sleep(800)
comprueba(await visible('#npNombre'), 'No Premium shows the name field')
shot('ui-2-no-premium')
await ev(`(() => { const i = document.getElementById('npNombre'); i.value = 'PruebaCI'; document.getElementById('npEntrar').click() })()`)
await sleep(6000) // estado.json + novedades from the real server
comprueba(await visible('#vInicio'), 'after entering the name the main screen is shown')
comprueba((await ev(`document.getElementById('cuentaNombre').textContent`)) === 'PruebaCI', 'header shows the account name (account created in FCL)')
const regiones = await ev(`document.querySelectorAll('#listaRegiones .region').length`)
comprueba(regiones >= 1, `regions online loaded from estado.json (${regiones})`)
const novedad = await ev(`document.getElementById('novTitulo').textContent`)
comprueba(novedad && novedad !== 'Novedades del servidor', `latest news title loaded: «${novedad}»`)
shot('ui-3-inicio')

await ev(`document.querySelector('[data-p=ajustes]').click()`)
await sleep(800)
const renders = await ev(`document.querySelectorAll('#selRender option').length`)
comprueba(renders >= 1, `settings list the renderers (${renders})`)
comprueba((await ev(`document.getElementById('ajCuenta').textContent`)).startsWith('PruebaCI'), 'settings show the account')
shot('ui-4-ajustes')

await ev(`document.querySelector('[data-p=jugar]').click()`)
await sleep(500)
await ev(`document.getElementById('btnJugar').click()`)
await sleep(1500)
comprueba(await visible('#avisoError'), 'JUGAR without the modpack shows the error card (phase 3 installs it)')
shot('ui-5-jugar-sin-pack')

await ev(`SC.avanzado()`)
await sleep(1500)
shot('ui-6-avanzado-fcl')
execFileSync('adb', ['shell', 'input', 'keyevent', 'KEYCODE_BACK'])
await sleep(1500)
comprueba(await ev(`document.visibilityState === 'visible' && !document.getElementById('vInicio').hidden`), 'back from FCL home brings our screens back')
shot('ui-7-vuelta')

comprueba(errores.length === 0, `no JS errors (${errores.length})`)
const texto = informe.join('\n') + (errores.length ? '\n\n' + errores.join('\n') : '')
writeFileSync('evidence/ui-check.txt', texto + '\n')
console.log(texto)
ws.close()
