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

// ---- Phase 3: JUGAR installs the real server modpack, then a newly published pack is applied by itself ----
const adb = (...a) => execFileSync('adb', ['shell', ...a]).toString().trim()
const packDe = async () => JSON.parse(await ev('SC.estado()')).pack
const esperaPack = async (version, minutos, nombre) => {
  let visto = false
  for (let s = 0; s < minutos * 60; s += 5) {
    if ((await packDe()) === version) return true
    if (!visto && await visible('#zonaProgreso')) { visto = true; await sleep(8000); shot(nombre) }
    if (await visible('#avisoError')) {
      informe.push('     error card: ' + await ev(`document.getElementById('errTitulo').textContent + ' · ' + document.getElementById('errTexto').textContent`))
      return false
    }
    await sleep(5000)
  }
  return false
}
await ev(`SC.debug('debug_no_launch', '1')`)
await ev(`document.querySelector('[data-p=jugar]').click()`)
await sleep(500)
const real = await (await fetch('https://descargas.servidorcobblemon.es/movil/sc-pack.json')).json()
await ev(`document.getElementById('btnJugar').click()`)
const t0 = Date.now()
comprueba(await esperaPack(real.version, 15, 'ui-5-instalando'), `JUGAR installs the server modpack ${real.version} (${Math.round((Date.now() - t0) / 1000)} s)`)
shot('ui-6-instalado')
const run = adb('find', '/sdcard/SCLauncher', '-maxdepth', '4', '-type', 'd', '-name', "'SC Cobblemon'").split('\n')[0]
comprueba(run.length > 0, `instance folder exists: ${run}`)
const mods = run ? adb('ls', `'${run}/mods'`).split('\n').filter((x) => x.endsWith('.jar')) : []
comprueba(mods.some((m) => m.startsWith('Cobblemon-fabric-')), `instance has the mods (${mods.length} jars, Cobblemon included)`)
comprueba(run && adb('ls', `'${run}/resourcepacks'`).includes('SC-Pack-Movil.zip'), 'instance has the mobile resource pack')
comprueba((await ev(`document.getElementById('perfilDetalle').textContent`)) === 'Modpack ' + real.version, 'home card shows the installed modpack version')

// player data must survive an update
adb('sh', '-c', `"echo sc_ci_option:1 >> '${run}/options.txt'"`)
const ci = JSON.parse(execFileSync('cat', ['packci/sc-pack.json']).toString())
await ev(`SC.debug('debug_manifest_url', 'http://10.0.2.2:8000/sc-pack.json')`)
await ev(`document.getElementById('btnJugar').click()`)
const t1 = Date.now()
comprueba(await esperaPack(ci.version, 15, 'ui-7-actualizando'), `a newly published pack (${ci.version}) is applied on JUGAR (${Math.round((Date.now() - t1) / 1000)} s)`)
comprueba(adb('cat', `'${run}/config/sc-ci-marker.txt'`).includes(ci.version), 'the new pack files are in the instance')
comprueba(adb('cat', `'${run}/options.txt'`).includes('sc_ci_option:1'), "the player's options.txt survived the update")
const mods2 = adb('ls', `'${run}/mods'`).split('\n').filter((x) => x.endsWith('.jar'))
comprueba(mods2.length === mods.length, `mods still complete after the update (${mods2.length})`)
await ev(`SC.debug('debug_manifest_url', '')`)

// repair: a broken mod is detected by its hash and downloaded again
const victima = mods.find((m) => m.startsWith('Cobblemon-fabric-'))
adb('sh', '-c', `"echo roto > '${run}/mods/${victima}'"`)
await ev(`SC.reparar()`)
let reparado = false
for (let s = 0; s < 600 && !reparado; s += 5) { await sleep(5000); reparado = await visible('#velo') }
const tam = Number(adb('stat', '-c', '%s', `'${run}/mods/${victima}'`))
comprueba(reparado && tam > 1_000_000, `«Reparar archivos» restores a broken mod (${victima}: ${tam} bytes)`)
shot('ui-8-reparado')
await ev(`document.getElementById('dlgSi').click()`)

// quickPlay: the instance points at the server
await sleep(3000)
comprueba(adb('cat', `'${run}/fclversion.cfg'`).includes('mc.servidorcobblemon.es'), 'instance settings (fclversion.cfg) carry the server address for quickPlay')

await ev(`SC.avanzado()`)
await sleep(1500)
shot('ui-6-avanzado-fcl')
execFileSync('adb', ['shell', 'input', 'keyevent', 'KEYCODE_BACK'])
await sleep(1500)
comprueba(await ev(`document.visibilityState === 'visible' && !document.getElementById('vInicio').hidden`), 'back from FCL home brings our screens back')
shot('ui-7-vuelta')

comprueba(errores.length === 0, `no JS errors (${errores.length})`)

// Real launch: the game process starts with quickPlay to our server. The emulator has no real GPU, so reaching the
// server is only reported (it is checked on real phones in phase 6); the launch itself must happen.
await ev(`SC.debug('debug_no_launch', '0')`)
await ev(`document.getElementById('btnJugar').click()`)
let jvm = false
for (let s = 0; s < 120 && !jvm; s += 5) {
  await sleep(5000)
  jvm = /JVMActivity/.test(execFileSync('adb', ['shell', 'dumpsys', 'activity', 'activities']).toString().split('\n').filter((l) => /ResumedActivity/.test(l)).join(' '))
}
comprueba(jvm, 'JUGAR starts the game (JVMActivity in front)')
await sleep(60000)
shot('ui-9-juego')
const log = adb('sh', '-c', `"tail -c 20000 '${run}/logs/latest.log' 2>/dev/null; ls /sdcard/SCLauncher/log 2>/dev/null"`)
writeFileSync('evidence/juego-latest.log', log + '\n')
const argsOk = /quickPlayMultiplayer|mc\.servidorcobblemon\.es/.test(execFileSync('adb', ['logcat', '-d']).toString() + log)
informe.push('INFO ' + `server address seen in the game start logs: ${argsOk}`)
const texto = informe.join('\n') + (errores.length ? '\n\n' + errores.join('\n') : '')
writeFileSync('evidence/ui-check.txt', texto + '\n')
console.log(texto)
ws.close()
