// SC UI check (CI emulator, debug APK): drives our WebView page through the DevTools protocol (adb-forwarded to
// localhost:9222), walks the screens with the real Java bridge and saves a screenshot of each one in evidence/.
// Fails on JS exceptions, console errors, or a screen that does not show what it should.
import { execFileSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'

const pkg = process.argv[2]
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
// A failed screenshot (the emulator's GPU can die while the game renders) must not lose the rest of the report
const shot = (name) => {
  try {
    execFileSync('adb', ['shell', 'screencap', '-p', '/sdcard/ui.png'], { timeout: 30000 })
    execFileSync('adb', ['pull', '/sdcard/ui.png', `evidence/${name}.png`], { timeout: 30000 })
    console.log('screenshot', name)
  } catch (e) { console.log('screenshot', name, 'FAILED:', e.message.split('\n')[0]) }
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
// The report is written after every check, so a later crash or hang never hides what already passed or failed
const comprueba = (cond, msg) => {
  informe.push((cond ? 'OK   ' : 'FAIL ') + msg)
  if (!cond) process.exitCode = 1
  console.log((cond ? 'OK   ' : 'FAIL ') + msg)
  writeFileSync('evidence/ui-check.txt', informe.join('\n') + '\n')
}
const visible = (sel) => ev(`(() => { const e = document.querySelector(${JSON.stringify(sel)}); return !!e && !e.closest('[hidden]') && e.getBoundingClientRect().height > 0 })()`)

await sleep(3000)
comprueba(await visible('#vCuenta'), 'first start without account shows «¿Cómo quieres entrar?»')
shot('ui-1-cuenta')

// ---- Phase 4: «Cuenta Microsoft» opens Microsoft's sign-in page in our window (nobody signs in here) ----
const evalEn = (target, expr) => new Promise((res, rej) => {
  const s = new WebSocket(target.webSocketDebuggerUrl)
  s.onerror = rej
  s.onopen = () => s.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression: expr, returnByValue: true } }))
  s.onmessage = (e) => { const m = JSON.parse(e.data); if (m.id === 1) { s.close(); res(m.result?.result?.value) } }
})
// The emulator's software GPU freezes on Microsoft's real page, so a light local page plays Microsoft's part here
// (it redirects to nativeclient with the state it got); sc-smoke.sh checks the real authorize URL from the runner.
const buscaVentana = async (patron, segundos) => {
  for (let s = 0; s < segundos; s += 2) {
    await sleep(2000)
    try {
      const ts = await (await fetch('http://127.0.0.1:9222/json')).json()
      const t = ts.find((x) => patron.test(x.url))
      if (t) return t
    } catch { /* not up yet */ }
  }
  return null
}
const adbVive = (que) => {
  try { execFileSync('adb', ['shell', 'true'], { timeout: 30000 }); return true } catch (e) {
    comprueba(false, `the emulator still answers after ${que} (adb: ${e.message.split('\n')[0]})`)
    process.exit(1)
  }
}
// a) cancel: the window opens and back closes it without saving anything
await ev(`SC.debug('debug_ms_authorize_url', 'http://10.0.2.2:8000/fake-authorize.html?hold=1')`)
await ev(`document.getElementById('btnMicrosoft').click()`)
let ventana = await buscaVentana(/fake-authorize\.html/, 30)
shot('ui-1b-microsoft')
adbVive('opening the Microsoft window')
const params = ventana ? JSON.parse(await evalEn(ventana, 'JSON.stringify(window.scCi || {})')) : {}
comprueba(!!ventana, '«Cuenta Microsoft» opens the sign-in window')
comprueba(params.client === '1ce6e35a-126f-48fd-97fb-54d143ac6d45' && params.redirect === 'https://login.microsoftonline.com/common/oauth2/nativeclient' &&
  params.method === 'S256' && (params.challenge || '').length >= 43 && (params.state || '').length >= 20,
  `it asks with our client ID, the nativeclient redirect, PKCE S256 and a state (${JSON.stringify(params).slice(0, 200)})`)
execFileSync('adb', ['shell', 'input', 'keyevent', 'KEYCODE_BACK'], { timeout: 30000 })
await sleep(2500)
comprueba(await visible('#vCuenta') && !(await visible('#velo')), 'back closes the Microsoft window and returns to «¿Cómo quieres entrar?» with no error')
// b) the redirect with this attempt's code is caught and the code is exchanged with Microsoft (a fake code, so
//    Microsoft answers with an error: that answer proves the exchange reached Microsoft with our client)
await ev(`SC.debug('debug_ms_authorize_url', 'http://10.0.2.2:8000/fake-authorize.html')`)
execFileSync('adb', ['logcat', '-c'])
await ev(`document.getElementById('btnMicrosoft').click()`)
let errMs = ''
for (let s = 0; s < 60 && !errMs; s += 2) {
  await sleep(2000)
  if (await visible('#velo') && (await ev(`document.getElementById('dlgTitulo').textContent`)) === 'No se pudo entrar con Microsoft') {
    errMs = await ev(`document.getElementById('dlgTexto').textContent`)
  }
}
const logMs = execFileSync('adb', ['logcat', '-d'], { maxBuffer: 256 * 1024 * 1024 }).toString().split('\n').filter((l) => /SC: Microsoft|AADSTS|invalid_grant/.test(l)).slice(0, 5).join(' | ')
shot('ui-1c-microsoft-respuesta')
comprueba(!!errMs && /AADSTS\d+|invalid_grant|invalid_request/.test(errMs + ' ' + logMs),
  `the code from the redirect is exchanged with Microsoft (fake code refused by Microsoft: «${errMs.slice(0, 120)}» ${logMs.slice(0, 300)})`)
comprueba(!/AADSTS50011|AADSTS700016|AADSTS7000218/.test(errMs + logMs), 'Microsoft accepts our client ID and redirect (no AADSTS50011 / 700016 / 7000218)')
await ev(`document.getElementById('dlgSi').click()`)
await ev(`SC.debug('debug_ms_authorize_url', '')`)

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
await ev(`document.getElementById('velo').hidden = true`)
await ev(`SC.reparar()`)
let reparado = false
for (let s = 0; s < 600 && !reparado; s += 5) {
  await sleep(5000)
  reparado = await visible('#velo') && (await ev(`document.getElementById('dlgTitulo').textContent`)) === 'Reparar archivos'
  if (!reparado && await visible('#avisoError')) { informe.push('     error card: ' + await ev(`document.getElementById('errTitulo').textContent + ' · ' + document.getElementById('errTexto').textContent`)); break }
}
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

// ---- Phase 5: a newer app on our website is offered, downloaded, checked and handed to Android's installer ----
await ev(`SC.debug('debug_app_manifest_url', 'http://10.0.2.2:8000/sc-app.json')`)
await ev(`SC.buscarActualizacion()`)
let oferta = false
for (let s = 0; s < 30 && !oferta; s += 2) { await sleep(2000); oferta = await visible('#velo') && (await ev(`document.getElementById('dlgTitulo').textContent`)) === 'Nueva versión de la app' }
comprueba(oferta, 'a newer app version on the website is offered («Nueva versión de la app»)')
shot('ui-8b-app-nueva')
let instalador = ''
if (oferta) {
  await ev(`document.getElementById('dlgSi').click()`)
  for (let s = 0; s < 180 && !instalador; s += 3) {
    await sleep(3000)
    const top = execFileSync('adb', ['shell', 'dumpsys', 'activity', 'activities']).toString().split('\n').filter((l) => /ResumedActivity/.test(l)).join(' ')
    instalador = /packageinstaller/i.test(top) ? top.trim().slice(0, 160) : ''
    if (!instalador && await visible('#avisoError')) { informe.push('     error card: ' + await ev(`document.getElementById('errTitulo').textContent + ' · ' + document.getElementById('errTexto').textContent`)); break }
  }
}
comprueba(!!instalador, `«Actualizar» downloads the APK, checks it and opens Android's installer (${instalador || 'not opened'})`)
shot('ui-8c-instalador')
if (instalador) { execFileSync('adb', ['shell', 'input', 'keyevent', 'KEYCODE_BACK']); await sleep(2500) }
await ev(`SC.debug('debug_app_manifest_url', '')`)

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
const logJuego = execFileSync('adb', ['logcat', '-d'], { maxBuffer: 256 * 1024 * 1024 }).toString()
const caida = logJuego.match(/Incompatible mods found|Mod discovery failed|Error analyzing \[[^\]]*\]|OpenJDK exited with code : \d+/g)
comprueba(!caida, `the game is still running 60 s after JUGAR (Fabric found the mods)${caida ? ': ' + [...new Set(caida)].join(' · ') : ''}`)
shot('ui-9-juego')
const log = adb('sh', '-c', `"tail -c 20000 '${run}/logs/latest.log' 2>/dev/null; ls /sdcard/SCLauncher/log 2>/dev/null"`)
writeFileSync('evidence/juego-latest.log', log + '\n')
const argsOk = /quickPlayMultiplayer|mc\.servidorcobblemon\.es/.test(execFileSync('adb', ['logcat', '-d']).toString() + log)
informe.push('INFO ' + `server address seen in the game start logs: ${argsOk}`)
const texto = informe.join('\n') + (errores.length ? '\n\n' + errores.join('\n') : '')
writeFileSync('evidence/ui-check.txt', texto + '\n')
console.log(texto)
ws.close()
