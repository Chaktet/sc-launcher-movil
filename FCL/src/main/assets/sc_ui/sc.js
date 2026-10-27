/* SC Launcher (Android) · UI logic. Java exposes window.SC (ScBridge.java); Java calls back window.scApp.*.
 * Opened outside the app (plain browser) it runs on sample data, so the layout can be checked side by side with
 * the approved mockups. Times shown to players are always relative («Empieza en 5 d 21 h»), never clock times. */
(function () {
  'use strict'
  const $ = (id) => document.getElementById(id)
  const enApp = typeof window.SC !== 'undefined'

  // ---- Bridge (sample data when outside the app) ----
  const muestra = {
    estado: { cuenta: { nombre: 'EntrenadorSC', tipo: 'offline', cabeza: '' }, versionApp: '0.1.0', ram: { auto: true, max: 3072, total: 6144 },
      renderers: [{ id: 'auto', nombre: 'Automático' }], renderer: 'auto', idioma: 12 },
    estadoJson: JSON.stringify({ regiones: [{ nombre: 'Kanto', online: true, jugadores: 24 }, { nombre: 'Kalos', online: true, jugadores: 28 }],
      eventos: [], fijos: [{ nombre: 'Community Day', region: 'Kanto y Kalos', inicio: Date.now() + 5 * 864e5 + 21 * 36e5 + 44 * 6e4, fin: Date.now() + 6 * 864e5 }] }),
    novedadesJson: JSON.stringify({ entradas: [{ fecha: '2026-10-26', titulo: 'Menús nuevos y el PokéNav', puntos: ['• Ejemplo de punto.'] }] })
  }
  const SC = enApp ? window.SC : {
    estado: () => JSON.stringify(muestra.estado),
    pedir: (id, url) => setTimeout(() => window.scApp.respuesta(id, true, /estado/.test(url) ? muestra.estadoJson : muestra.novedadesJson), 150),
    entrarNoPremium: (n) => { muestra.estado.cuenta = { nombre: n, tipo: 'offline' }; return '' },
    entrarMicrosoft: () => {}, cerrarSesion: () => { muestra.estado.cuenta = null },
    jugar: () => {}, reparar: () => {}, avanzado: () => {}, abrir: (u) => console.log('abrir', u),
    ajuste: (k, v) => console.log('ajuste', k, v)
  }
  const leerEstado = () => { try { return JSON.parse(SC.estado()) } catch (e) { return {} } }

  // Async GET through Java (only our own hosts are allowed there)
  const pendientes = new Map(); let sigId = 1
  function pedir(url) {
    return new Promise((ok, mal) => { const id = String(sigId++); pendientes.set(id, { ok, mal }); SC.pedir(id, url) })
  }

  // ---- Views ----
  function mostrar(vista) {
    for (const v of ['vCuenta', 'vNoPremium', 'vInicio']) $(v).hidden = v !== vista
    $('cuentaFicha').hidden = vista !== 'vInicio'
  }
  function pestana(p) {
    document.querySelectorAll('.pestana').forEach((b) => b.toggleAttribute('aria-current', b.dataset.p === p))
    for (const [id, k] of [['pJugar', 'jugar'], ['pEventos', 'eventos'], ['pNovedades', 'novedades'], ['pAjustes', 'ajustes']]) $(id).hidden = k !== p
    if (p === 'novedades') { $('alertaNov').hidden = true; try { localStorage.setItem('scNovVista', ultimaNov) } catch (e) {} }
  }

  let estado = {}
  function pintarCuenta() {
    estado = leerEstado()
    const c = estado.cuenta
    if (!c) { mostrar('vCuenta'); $('btnCuentaVolver').hidden = true; return }
    $('cuentaNombre').textContent = c.nombre
    $('ajCuenta').textContent = c.nombre + (c.tipo === 'microsoft' ? ' · Premium' : ' · No Premium')
    // Same head service as the desktop launcher (minotar); offline names show the skin of that name, like in game
    $('avatarCabeza').onerror = () => { $('avatarCabeza').hidden = true }
    $('avatarCabeza').src = c.cabeza || ('https://minotar.net/helm/' + encodeURIComponent(c.uuid || c.nombre) + '/48')
    $('avatarCabeza').hidden = false
    $('verBadge').textContent = 'v' + (estado.versionApp || '')
    $('verApp').textContent = estado.versionApp || ''
    const nombrePerfil = (p) => p === 'pro' ? 'Móvil PRO' : 'Móvil'
    $('perfilDetalle').textContent = !estado.pack ? 'Se instala al pulsar JUGAR'
      : estado.perfil !== estado.perfilInstalado ? (nombrePerfil(estado.perfil) + ' · se instala al pulsar JUGAR')
      : ('Modpack ' + nombrePerfil(estado.perfilInstalado) + ' ' + estado.pack.split('-')[0])
    pintarAjustes()
    mostrar('vInicio')
  }

  // ---- Account ----
  $('btnMicrosoft').onclick = () => SC.entrarMicrosoft()
  $('btnNoPremium').onclick = () => { mostrar('vNoPremium'); $('npError').hidden = true; $('npNombre').value = ''; setTimeout(() => $('npNombre').focus(), 50) }
  $('npCancelar').onclick = () => { $('npNombre').blur(); estado.cuenta ? mostrar('vInicio') : mostrar('vCuenta') }
  function entrarNP() {
    const n = $('npNombre').value.trim()
    if (!/^[A-Za-z0-9_]{3,16}$/.test(n)) { $('npError').textContent = 'El nombre debe tener de 3 a 16 letras, números o guion bajo.'; $('npError').hidden = false; return }
    const err = SC.entrarNoPremium(n)
    if (err) { $('npError').textContent = err; $('npError').hidden = false; return }
    $('npNombre').blur(); pintarCuenta()
  }
  $('npEntrar').onclick = entrarNP
  $('npNombre').addEventListener('keydown', (e) => { if (e.key === 'Enter') entrarNP() })
  $('cuentaFicha').onclick = () => { mostrar('vCuenta'); $('btnCuentaVolver').hidden = false }
  $('btnCuentaVolver').onclick = () => mostrar('vInicio')
  $('btnCerrarSesion').onclick = () => dialogo('Cerrar sesión', '¿Quieres cerrar la sesión de esta cuenta en el móvil?', 'Cancelar', 'Cerrar sesión',
    () => { SC.cerrarSesion(); pintarCuenta() })

  // ---- Tabs, links ----
  document.querySelectorAll('.pestana').forEach((b) => { b.onclick = () => pestana(b.dataset.p) })
  document.querySelectorAll('[data-url]').forEach((b) => { b.onclick = () => SC.abrir(b.dataset.url) })
  $('pieWeb').onclick = () => SC.abrir('https://servidorcobblemon.es')
  $('btnCalendario').onclick = () => pestana('eventos')
  $('tarjetaNovedad').onclick = () => pestana('novedades')
  $('btnAvanzado').onclick = () => SC.avanzado()

  // ---- Play ----
  $('btnJugar').onclick = () => SC.jugar()
  $('btnReparar').onclick = () => dialogo('Reparar archivos', 'Se comprobarán todos los archivos del juego y se volverán a descargar los que estén mal. Tus ajustes y controles no se tocan.',
    'Cancelar', 'Reparar', () => SC.reparar())
  $('btnReintentar').onclick = () => { $('avisoError').hidden = true; SC.jugar() }

  // ---- Settings ----
  function pintarAjustes() {
    const r = estado.ram || {}
    const s = $('ramSlider')
    s.max = String(Math.max(1024, Math.floor((r.total || 4096) * 0.75 / 256) * 256))
    s.value = String(r.max || 2048)
    $('ramAuto').checked = !!r.auto
    s.disabled = !!r.auto
    $('ramValor').textContent = r.auto ? 'Automática' : (s.value + ' MB')
    const sel = $('selRender'); sel.innerHTML = ''
    for (const x of estado.renderers || []) { const o = document.createElement('option'); o.value = x.id; o.textContent = x.nombre; sel.appendChild(o) }
    sel.value = estado.renderer || ''
    $('selIdioma').value = String(estado.idioma || 12)
    $('selPerfil').value = estado.perfil || 'movil'
  }
  $('ramSlider').oninput = () => { $('ramValor').textContent = $('ramSlider').value + ' MB' }
  $('ramSlider').onchange = () => SC.ajuste('ram', $('ramSlider').value)
  $('ramAuto').onchange = () => { SC.ajuste('ramAuto', $('ramAuto').checked ? '1' : '0'); estado = leerEstado(); pintarAjustes() }
  $('selRender').onchange = () => SC.ajuste('renderer', $('selRender').value)
  $('selIdioma').onchange = () => SC.ajuste('idioma', $('selIdioma').value)
  // Switching modpack: installed on the next JUGAR (same instance, so settings, controls and worlds stay)
  $('selPerfil').onchange = () => {
    const pro = $('selPerfil').value === 'pro'
    SC.ajuste('perfil', $('selPerfil').value)
    setTimeout(pintarCuenta, 150)
    dialogo(pro ? 'Móvil PRO' : 'Móvil',
      (pro ? 'Más mods y animaciones, como el PRO del ordenador. Pide un móvil potente y tarda más en cargar. '
           : 'El modpack ligero, el que mejor va en la mayoría de móviles. ') +
      'Se instalará al pulsar JUGAR; tus ajustes, controles y opciones se conservan.', '', 'Entendido', null)
  }

  // ---- Dialog ----
  function dialogo(titulo, texto, no, si, alSi) {
    $('dlgTitulo').textContent = titulo; $('dlgTexto').textContent = texto
    $('dlgNo').textContent = no; $('dlgSi').textContent = si
    $('dlgNo').hidden = !no
    $('velo').hidden = false
    $('dlgNo').onclick = () => { $('velo').hidden = true }
    $('dlgSi').onclick = () => { $('velo').hidden = true; if (alSi) alSi() }
  }

  // ---- Server data (same files the desktop launcher reads) ----
  const HOST = 'https://descargas.servidorcobblemon.es/launcher/'
  function restante(ms) {
    if (ms <= 0) return 'Ya ha empezado'
    const d = Math.floor(ms / 864e5), h = Math.floor(ms % 864e5 / 36e5), m = Math.floor(ms % 36e5 / 6e4)
    return 'Empieza en ' + (d ? d + ' d ' : '') + (d || h ? h + ' h ' : '') + m + ' min'
  }
  let eventos = []
  function pintarEvento() {
    const ahora = Date.now()
    const prox = eventos.filter((e) => (e.fin || e.inicio) > ahora).sort((a, b) => a.inicio - b.inicio)[0]
    if (!prox) { $('tarjetaEvento').hidden = true; return }
    $('tarjetaEvento').hidden = false
    const enCurso = prox.inicio <= ahora
    $('evKicker').textContent = enCurso ? 'Evento en curso' : 'Próximo evento'
    $('evTitulo').textContent = prox.nombre || ''
    $('evCuenta').textContent = enCurso ? (prox.region || '') : restante(prox.inicio - ahora)
  }
  async function cargarEstado() {
    try {
      const j = JSON.parse(await pedir(HOST + 'estado.json?t=' + Date.now()))
      const caja = $('listaRegiones'); caja.innerHTML = ''
      for (const r of j.regiones || []) {
        const fila = document.createElement('div'); fila.className = 'region' + (r.online ? '' : ' off')
        const n = document.createElement('b'); n.textContent = r.nombre
        const v = document.createElement('span')
        if (r.online) {
          v.textContent = String(r.jugadores)
          const t = document.createElement('small'); t.className = 'regJug'; t.textContent = ' jugadores'; v.appendChild(t)
          v.title = r.jugadores + ' jugadores'
        } else v.textContent = 'Reinicio'
        fila.append(n, v); caja.appendChild(fila)
      }
      eventos = [...(j.eventos || []), ...(j.fijos || [])].filter((e) => e && e.inicio)
      pintarEvento()
      const lista = $('listaEventos'); lista.innerHTML = ''
      const ahora = Date.now()
      for (const e of eventos.filter((x) => (x.fin || x.inicio) > ahora).sort((a, b) => a.inicio - b.inicio).slice(0, 20)) {
        const it = document.createElement('div'); it.className = 'item'
        const b = document.createElement('b'); b.textContent = e.nombre || ''
        const s = document.createElement('span'); s.textContent = (e.region ? e.region + ' · ' : '') + (e.inicio <= ahora ? 'En curso' : restante(e.inicio - ahora))
        const d = document.createElement('span'); d.textContent = e.descripcion || ''
        it.append(b, s, d); lista.appendChild(it)
      }
      if (!lista.children.length) lista.innerHTML = '<div class="item"><span>No hay eventos anunciados ahora mismo.</span></div>'
    } catch (e) {
      $('listaRegiones').innerHTML = '<span class="regionCargando">Sin conexión</span>'
    }
  }
  let ultimaNov = ''
  async function cargarNovedades() {
    try {
      const j = JSON.parse(await pedir(HOST + 'novedades-menu.json?t=' + Date.now()))
      const ent = (j.entradas || []).slice(0, 30)
      if (ent[0]) { $('novTitulo').textContent = ent[0].titulo; ultimaNov = ent[0].fecha + ent[0].titulo }
      let vista = ''; try { vista = localStorage.getItem('scNovVista') || '' } catch (e) {}
      $('alertaNov').hidden = !ultimaNov || vista === ultimaNov
      const lista = $('listaNovedades'); lista.innerHTML = ''
      for (const n of ent) {
        const it = document.createElement('div'); it.className = 'item'
        const b = document.createElement('b'); b.textContent = n.titulo
        const ul = document.createElement('ul')
        for (const p of n.puntos || []) { if (!p) continue; const li = document.createElement('li'); li.textContent = p; ul.appendChild(li) }
        it.append(b, ul); lista.appendChild(it)
      }
    } catch (e) { /* keeps the default card text */ }
  }

  // ---- Called from Java ----
  window.scApp = {
    respuesta(id, ok, texto) { const p = pendientes.get(String(id)); if (!p) return; pendientes.delete(String(id)); ok ? p.ok(texto) : p.mal(new Error(texto)) },
    refrescar() { pintarCuenta() },
    // Progress of the modpack / game preparation (phase 3). pct < 0 = indeterminate.
    progreso(texto, pct, detalle) {
      $('zonaJugar').hidden = true; $('zonaProgreso').hidden = false; $('avisoError').hidden = true
      $('progTexto').textContent = texto || ''
      $('progDetalle').textContent = detalle || ''
      const v = pct < 0 ? 100 : Math.max(0, Math.min(100, pct))
      $('progRelleno').style.width = v + '%'; $('progPct').textContent = pct < 0 ? '' : Math.round(v) + '%'
    },
    listo() { $('zonaJugar').hidden = false; $('zonaProgreso').hidden = true; pintarCuenta() },
    error(titulo, texto) { this.listo(); $('errTitulo').textContent = titulo; $('errTexto').textContent = texto || ''; $('avisoError').hidden = false },
    dialogo(titulo, texto, no, si, accion) { dialogo(titulo, texto, no, si, accion ? () => SC.accion(accion) : null) },
    atras() { // Android back button: close dialog / go back inside our screens; returns true if handled
      if (!$('velo').hidden) { $('velo').hidden = true; return true }
      if (!$('vNoPremium').hidden) { $('npCancelar').click(); return true }
      if (!$('vCuenta').hidden && estado.cuenta) { mostrar('vInicio'); return true }
      if (!$('vInicio').hidden && $('pJugar').hidden) { pestana('jugar'); return true }
      return false
    }
  }

  pintarCuenta()
  cargarEstado(); cargarNovedades()
  if (SC.buscarActualizacion) SC.buscarActualizacion() // a newer app on our website is offered in a dialog
  setInterval(pintarEvento, 30000)
  setInterval(cargarEstado, 120000)
})()
