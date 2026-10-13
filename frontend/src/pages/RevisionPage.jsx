import React, { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Topbar, { Brand } from '../components/Topbar'
import { Cargando, Migas } from '../components/ui'
import { useAtraparFoco } from '../hooks/useAtraparFoco'
import { useAsync } from '../hooks/useAsync'
import { getRevision, guardarSegundos, reiniciarAnalisis } from '../services/revision'
import { mensajeError } from '../services/api'
import { CONDUCTA_BD, DE_BD, fmt, enlaceResultados, especimen } from '../lib/fst'

const REACCION = 0.4 // lo que tarda una persona en reaccionar (s)
const LUPA_S = 40 // segundos visibles en la lupa
const FILA = 22 // alto de cada renglón de la línea de tiempo
const VELOCIDADES = [0.5, 0.75, 1, 1.5, 2]
// Botones y teclas (§9.7). «Dudosa» deja el segundo sin conducta (origen dudosa).
const BOTONES = [['1', 'nado', 'Nado'], ['2', 'inmovilidad', 'Inmovilidad'], ['3', 'escalamiento', 'Escalamiento'], ['4', 'activa', 'Activa'], ['0', null, 'Dudosa']]
const TECLA = Object.fromEntries(BOTONES.map(([k, c]) => [k, c]))
const NOMBRE = { nado: 'Nado activo', inmovilidad: 'Inmovilidad', escalamiento: 'Escalamiento', activa: 'Conducta activa' }
const nombre = (c) => (c ? NOMBRE[c] : 'Sin conducta')
const ORIGEN = { confirmada: 'Confirmado antes', corregida: 'Corregido antes', dudosa: 'Marcado como dudoso antes' }

// Muestra de color de una conducta, igual que en las líneas de tiempo.
function Muestra({ c }) {
  const fondo = c === 'activa' ? 'repeating-linear-gradient(135deg,var(--color-text) 0 3px,var(--color-neutral-400) 3px 6px)'
    : !c ? 'repeating-linear-gradient(135deg,color-mix(in srgb,var(--color-text) 30%,transparent) 0 2px,transparent 2px 5px)'
      : { nado: 'var(--color-text)', inmovilidad: 'var(--color-accent)', escalamiento: 'var(--color-neutral-400)' }[c]
  return <span className="sw" style={{ background: fondo }} />
}

// 2l · Revisión segundo a segundo (R-14, §9.7). Solo de un análisis completado y para quien
// subió el video o un Administrador. Tema oscuro; un espécimen a la vez, con el video
// corriendo. Las correcciones viven en un borrador del navegador hasta «Guardar».
export default function RevisionPage() {
  const { clave, id } = useParams()
  const [version, setVersion] = useState(0)
  const [guardado, setGuardado] = useState(false)
  const { data, error } = useAsync(() => getRevision(clave, id), [clave, id, version])

  if (error) return <SinRevision error={error} clave={clave} />
  if (!data) return <div className="tema-oscuro"><Cargando /></div>
  return (
    <Revisor key={version} datos={data} clave={clave} id={id} guardado={guardado}
      onGuardado={() => { setGuardado(true); setVersion((v) => v + 1) }} />
  )
}

function Revisor({ datos, clave, id, guardado, onGuardado }) {
  const navigate = useNavigate()
  const N = datos.duracion
  const ESP = datos.especimenes.map((e) => e.numeroCilindro)
  const BORRADOR = 'fst.revision.' + clave + '.' + id

  // Por espécimen: p (conducta que hay en el servidor), o (su origen), c (la de ahora) y v
  // (segundos vistos). Viven en una referencia; version vuelve a pintar.
  const L = useRef(null)
  const [recuperado] = useState(() => {
    L.current = Object.fromEntries(datos.especimenes.map((e) => {
      const p = e.segundos.map((x) => (x.conducta ? DE_BD[x.conducta] : null))
      return [e.numeroCilindro, { p, o: e.segundos.map((x) => x.origen), c: p.slice(), v: new Uint8Array(N) }]
    }))
    try {
      const b = JSON.parse(localStorage.getItem(BORRADOR))
      if (!b?.L) return false
      for (const e of ESP) {
        const x = b.L[e]
        if (!x || x.c.length !== N) return false
        L.current[e].c = x.c
        L.current[e].v = Uint8Array.from(x.v)
      }
      return true
    } catch {
      return false
    }
  })
  const hist = useRef([])
  const [, setVersion] = useState(0)
  const repintar = () => setVersion((v) => v + 1)

  const [tubo, setTubo] = useState(ESP[0])
  const [t, setT] = useState(0)
  const tRef = useRef(0)
  const ultimo = useRef(-1)
  const [play, setPlay] = useState(false)
  const [rate, setRate] = useState(1)
  const [aviso, setAviso] = useState('')
  const [resumen, setResumen] = useState(false)
  const [estado, setEstado] = useState({ guardando: false, error: '' })
  const videoRef = useRef(null)
  const raizRef = useRef(null)
  const cvMain = useRef(null)
  const cvLupa = useRef(null)
  const tAviso = useRef(0)

  const segDe = (x) => Math.max(0, Math.min(N - 1, Math.floor(x + 1e-6)))
  const s = segDe(t)
  const E = L.current[tubo]

  const avisar = useCallback((txt) => {
    setAviso(txt)
    clearTimeout(tAviso.current)
    tAviso.current = setTimeout(() => setAviso(''), 900)
  }, [])

  const guardarBorrador = useCallback(() => {
    try {
      const b = {}
      for (const e of ESP) b[e] = { c: L.current[e].c, v: Array.from(L.current[e].v) }
      localStorage.setItem(BORRADOR, JSON.stringify({ L: b }))
    } catch { /* sin almacenamiento: el avance dura hasta salir */ }
  }, [BORRADOR, ESP])

  // ── tiempo ────────────────────────────────────────────────────────────────
  const ir = useCallback((x) => {
    const nuevo = Math.max(0, Math.min(N - 0.05, x))
    tRef.current = nuevo
    ultimo.current = segDe(nuevo)
    if (videoRef.current) videoRef.current.currentTime = nuevo
    setT(nuevo)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [N])

  // Un segundo cuenta como visto cuando el cabezal ya lo dejó atrás, a 2× o más lento.
  useEffect(() => {
    if (!play) return
    const video = videoRef.current
    if (video) { video.playbackRate = rate; video.play().catch(() => setPlay(false)) }
    // Un reloj de 40 ms (no requestAnimationFrame, que algunos navegadores detienen).
    let previo = performance.now()
    function paso() {
      const ahora = performance.now()
      const nuevo = video ? video.currentTime : Math.min(N - 0.001, tRef.current + ((ahora - previo) / 1000) * rate)
      previo = ahora
      const sg = segDe(nuevo)
      if (sg !== ultimo.current) {
        if (rate <= 2 && ultimo.current >= 0 && sg > ultimo.current && sg - ultimo.current < 4) {
          for (let k = ultimo.current; k < sg; k++) L.current[tubo].v[k] = 1
        }
        ultimo.current = sg
        if (sg % 5 === 0) guardarBorrador()
      }
      tRef.current = nuevo
      setT(nuevo)
      if (nuevo >= N - 0.001) {
        L.current[tubo].v[N - 1] = 1
        setPlay(false)
        avisar(especimen(tubo) + ' terminado · Tab para el siguiente')
      }
    }
    const reloj = setInterval(paso, 40)
    return () => {
      clearInterval(reloj)
      if (video) video.pause()
      guardarBorrador()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [play, rate, tubo])

  // ── etiquetar ─────────────────────────────────────────────────────────────
  // Con el video corriendo: desde 0.4 s antes de la tecla hasta el siguiente cambio de color.
  // En pausa: solo el segundo donde estás.
  const aplicar = useCallback((c) => {
    const X = L.current[tubo]
    const s0 = play ? segDe(tRef.current - REACCION) : segDe(tRef.current)
    let s1 = s0
    if (play) { const viejo = X.c[s0]; while (s1 + 1 < N && X.c[s1 + 1] === viejo) s1++ }
    hist.current.push({ e: tubo, s0, c: X.c.slice(s0, s1 + 1) })
    if (hist.current.length > 200) hist.current.shift()
    for (let k = s0; k <= s1; k++) X.c[k] = c
    for (let k = s0; k <= segDe(tRef.current); k++) X.v[k] = 1
    avisar(nombre(c) + ' · ' + (play ? 'desde ' + fmt(s0) : 'solo ' + fmt(s0)))
    guardarBorrador()
    repintar()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tubo, play, N])

  const deshacer = useCallback(() => {
    const u = hist.current.pop()
    if (!u) return avisar('Nada que deshacer')
    u.c.forEach((c, i) => { L.current[u.e].c[u.s0 + i] = c })
    avisar('Deshecho')
    guardarBorrador()
    repintar()
  }, [avisar, guardarBorrador])

  const elegir = useCallback((e) => {
    setPlay(false)
    setTubo(e)
    ir(0)
  }, [ir])

  // ── guardar (R-14) ────────────────────────────────────────────────────────
  // Solo lo visto se guarda: confirmada si coincide con lo que había, corregida si cambió,
  // dudosa si quedó sin conducta. Lo pintado sin verlo se descarta (D-80).
  function cambios() {
    const out = []
    for (const e of ESP) {
      const X = L.current[e]
      const segundos = []
      for (let k = 0; k < N; k++) {
        if (!X.v[k]) continue
        const cambio = X.c[k] !== X.p[k]
        const origen = cambio ? (X.c[k] ? 'corregida' : 'dudosa') : X.c[k] ? (X.o[k] === 'maquina' ? 'confirmada' : X.o[k]) : X.o[k]
        if (cambio || origen !== X.o[k]) segundos.push({ segundo: k + 1, conducta: X.c[k] ? CONDUCTA_BD[X.c[k]] : null, origen })
      }
      if (segundos.length) out.push({ numeroCilindro: e, segundos })
    }
    return out
  }

  async function guardar() {
    setPlay(false)
    const lista = cambios()
    if (!lista.length) return avisar('No hay segundos vistos que guardar')
    setEstado({ guardando: true, error: '' })
    try {
      await guardarSegundos(clave, id, lista)
      try { localStorage.removeItem(BORRADOR) } catch { /* sin almacenamiento */ }
      onGuardado()
    } catch (e) {
      setEstado({ guardando: false, error: mensajeError(e, 'No se pudieron guardar las correcciones. El borrador sigue en este navegador.') })
    }
  }

  function descartar() {
    try { localStorage.removeItem(BORRADOR) } catch { /* sin almacenamiento */ }
    for (const e of ESP) { const X = L.current[e]; X.c = X.p.slice(); X.v = new Uint8Array(N) }
    hist.current = []
    ir(0)
    repintar()
  }

  async function reiniciar() {
    if (!window.confirm('¿Reiniciar el análisis? Se borrará el resultado actual, también las correcciones guardadas, y el video vuelve a la cola.')) return
    setPlay(false)
    try {
      await reiniciarAnalisis(clave, id)
      try { localStorage.removeItem(BORRADOR) } catch { /* sin almacenamiento */ }
      navigate('/analisis', { state: { aviso: 'Análisis reiniciado: ' + datos.grupo.nombre + ' · Tanda ' + datos.tanda + ' volvió a la cola.' } })
    } catch (e) {
      setEstado({ guardando: false, error: mensajeError(e, 'No se pudo reiniciar el análisis.') })
    }
  }

  // ── teclado (§9.7) ────────────────────────────────────────────────────────
  useEffect(() => {
    function onKey(e) {
      if (resumen || e.target.closest?.('input, select, textarea')) return
      const k = e.key
      if ((e.ctrlKey || e.metaKey) && k.toLowerCase() === 'z') { e.preventDefault(); deshacer(); return }
      if ((e.ctrlKey || e.metaKey) && k.toLowerCase() === 's') { e.preventDefault(); guardar(); return }
      if (e.repeat || e.ctrlKey || e.metaKey || e.altKey) return
      if (k in TECLA) { e.preventDefault(); aplicar(TECLA[k]) }
      else if (k === ' ') { e.preventDefault(); setPlay((x) => !x) }
      else if (k === 'ArrowRight') { e.preventDefault(); ir(Math.floor(tRef.current) + (e.shiftKey ? 5 : 1)) }
      else if (k === 'ArrowLeft') { e.preventDefault(); ir(Math.floor(tRef.current) - (e.shiftKey ? 5 : 1)) }
      else if (k === 'r' || k === 'R') ir(tRef.current - 3)
      else if (k === 'Tab') {
        e.preventDefault()
        const i = ESP.indexOf(tubo)
        elegir(ESP[(i + (e.shiftKey ? ESP.length - 1 : 1)) % ESP.length])
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  // ── líneas de tiempo (canvas) ─────────────────────────────────────────────
  useEffect(() => {
    const css = getComputedStyle(raizRef.current)
    const tok = (n) => css.getPropertyValue(n).trim()
    const C = { ink: tok('--color-text'), acc: tok('--color-accent'), n400: tok('--color-neutral-400'), n200: tok('--color-neutral-200'), bg: tok('--color-bg'), marca: tok('--color-accent-600') }
    function rayas(a, b, paso) {
      const cv = document.createElement('canvas')
      cv.width = cv.height = paso * 2
      const x = cv.getContext('2d')
      if (!x) return null
      x.fillStyle = b; x.fillRect(0, 0, cv.width, cv.height)
      x.strokeStyle = a; x.lineWidth = paso * 0.7
      for (let i = -2; i <= 2; i++) { x.beginPath(); x.moveTo(i * paso * 2, cv.height); x.lineTo(i * paso * 2 + cv.width, 0); x.stroke() }
      return cv
    }
    const PAT = { activa: rayas(C.ink, C.n400, 3), nada: rayas('#5a5c60', C.bg, 2.5) }
    const fill = (ctx, c) => (c === 'nado' ? C.ink : c === 'inmovilidad' ? C.acc : c === 'escalamiento' ? C.n400
      : (PAT[c === 'activa' ? 'activa' : 'nada'] && ctx.createPattern(PAT[c === 'activa' ? 'activa' : 'nada'], 'repeat')) || C.n200)
    function prep(cv, alto) {
      const ctx = cv?.getContext('2d')
      if (!ctx) return [null, 0]
      const dpr = window.devicePixelRatio || 1
      const w = cv.clientWidth
      cv.width = Math.round(w * dpr); cv.height = Math.round(alto * dpr); cv.style.height = alto + 'px'
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      ctx.clearRect(0, 0, w, alto)
      return [ctx, w]
    }
    // Los 300 s, un renglón por espécimen; lo no visto, más tenue.
    const [ctx, w] = prep(cvMain.current, FILA * ESP.length)
    if (ctx) {
      const px = w / N
      ESP.forEach((e, i) => {
        const X = L.current[e]
        const y = i * FILA
        ctx.fillStyle = C.n200; ctx.fillRect(0, y + 3, w, FILA - 6)
        for (let k = 0; k < N; k++) {
          ctx.globalAlpha = X.v[k] ? 1 : 0.45
          ctx.fillStyle = fill(ctx, X.c[k])
          ctx.fillRect(k * px, y + 3, Math.max(px, 0.6) + 0.3, FILA - 6)
        }
        ctx.globalAlpha = 1
        if (e === tubo) { ctx.strokeStyle = C.marca; ctx.lineWidth = 2; ctx.strokeRect(1, y + 2, w - 2, FILA - 4) }
      })
      ctx.fillStyle = C.ink; ctx.fillRect((t / N) * w - 1, 0, 2, FILA * ESP.length)
    }
    // Lupa: 40 s alrededor del segundo actual; la franja de arriba marca lo corregido.
    const [cz, wz] = prep(cvLupa.current, 52)
    if (cz) {
      const X = L.current[tubo]
      const ini = Math.max(0, Math.min(N - LUPA_S, t - LUPA_S / 2))
      const pz = wz / LUPA_S
      cz.font = '11px Archivo, system-ui'
      for (let k = Math.floor(ini); k < Math.min(N, ini + LUPA_S + 1); k++) {
        const x = (k - ini) * pz
        cz.globalAlpha = X.v[k] ? 1 : 0.45; cz.fillStyle = fill(cz, X.c[k]); cz.fillRect(x + 1, 6, pz - 2, 26)
        cz.globalAlpha = 1
        if (X.c[k] !== X.p[k]) { cz.fillStyle = C.marca; cz.fillRect(x + 1, 0, pz - 2, 4) }
        if (k % 5 === 0) { cz.fillStyle = C.ink; cz.fillRect(x, 33, 1, 5); cz.fillText(fmt(k), x + 2, 49) }
      }
      cz.fillStyle = C.ink; cz.fillRect((t - ini) * pz - 1, 0, 2, 38)
    }
  })

  function clicLinea(ev, lupa) {
    const r = ev.currentTarget.getBoundingClientRect()
    const x = ev.clientX - r.left
    if (lupa) {
      const ini = Math.max(0, Math.min(N - LUPA_S, t - LUPA_S / 2))
      ir(Math.floor(ini + (x / r.width) * LUPA_S) + 0.02)
    } else {
      const e = ESP[Math.floor((ev.clientY - r.top) / FILA)]
      if (e && e !== tubo) { setPlay(false); setTubo(e) }
      ir(Math.floor((x / r.width) * N) + 0.02)
    }
  }

  // ── datos de los paneles ──────────────────────────────────────────────────
  const c = E.c[s]
  const visto = (e) => L.current[e].v.reduce((a, b) => a + b, 0)
  const corregidos = (e) => L.current[e].c.reduce((a, x, k) => a + (x !== L.current[e].p[k] ? 1 : 0), 0)
  const totalCorr = ESP.reduce((a, e) => a + corregidos(e), 0)
  const conCorr = ESP.filter((e) => corregidos(e)).length
  const cuenta = (arr) => { const o = { nado: 0, inmovilidad: 0, escalamiento: 0, activa: 0, null: 0 }; arr.forEach((x) => { o[x ?? 'null']++ }); return o }
  const sistema = cuenta(E.p)
  const ahora = cuenta(E.c)
  const agrupado = ESP.some((e) => L.current[e].c.includes('activa'))
  const r = datos.especimenes.find((e) => e.numeroCilindro === tubo).recuadro
  const pct = (v, total) => (100 * v) / total + '%'
  const origenAhora = c !== E.p[s] ? 'Corregido · antes: ' + nombre(E.p[s]).toLowerCase() : ORIGEN[E.o[s]] || 'Conducta del sistema'

  return (
    <div ref={raizRef} className="app tema-oscuro">
      <Topbar>
        <Brand inline />
        <Migas items={[
          { to: '/experimentos', label: 'Experimentos' },
          { to: `/experimentos/${clave}`, label: datos.experimento.titulo },
          { to: enlaceResultados(clave, datos.grupo.id, datos.tanda), label: datos.grupo.nombre + ' · Tanda ' + datos.tanda },
          { label: 'Revisión segundo a segundo' },
        ]} />
      </Topbar>

      <div className="page">
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 24, flexWrap: 'wrap' }}>
          <div style={{ flex: 1 }}>
            <h2 style={{ margin: '0 0 9px' }}>Revisión segundo a segundo · {datos.grupo.nombre}, Tanda {datos.tanda}</h2>
            <div className="num" style={{ fontSize: 12.5, color: 'var(--muted-2)' }}>{datos.grupo.tratamiento} · Día 2, {N} s</div>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <Link className="btn btn-secondary" to={enlaceResultados(clave, datos.grupo.id, datos.tanda)}>Volver a resultados</Link>
            <button type="button" className="btn btn-secondary" onClick={() => { setPlay(false); setResumen(true) }}>Resumen</button>
            <button type="button" className="btn btn-primary" disabled={estado.guardando} onClick={guardar}>{estado.guardando ? 'Guardando…' : 'Guardar correcciones'}</button>
          </div>
        </div>

        <div style={{ marginTop: 16 }} role="status">
          {estado.error && <div className="note-bar">{estado.error}</div>}
          {!estado.error && (guardado && !totalCorr
            ? <div className="ok-bar"><strong>Correcciones guardadas.</strong> Se recalcularon los totales por minuto y los reportes se regeneran con los datos nuevos.</div>
            : totalCorr || recuperado
              ? (
                <div className="note-bar" style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                  <span style={{ flex: 1 }}><strong>Borrador sin guardar</strong> · {totalCorr} {totalCorr === 1 ? 'segundo corregido' : 'segundos corregidos'} en {conCorr} {conCorr === 1 ? 'espécimen' : 'especímenes'}. El avance vive en este navegador hasta que elijas «Guardar correcciones».</span>
                  <button type="button" className="linkbtn" style={{ fontSize: 12 }} onClick={descartar}>Descartar borrador</button>
                </div>
              )
              : <div className="hint">Cada segundo ya trae la conducta que puso el sistema. Si lo que ves no coincide con «Ahora», pulsa la conducta correcta. Solo se guarda lo que ya viste.</div>)}
        </div>

        <div role="tablist" aria-label="Especímenes" style={{ display: 'flex', borderBottom: '2px solid var(--color-divider)', margin: '18px 0 20px' }}>
          {ESP.map((e) => (
            <button key={e} type="button" role="tab" aria-selected={e === tubo} className={'tab' + (e === tubo ? ' on' : '')} onClick={() => elegir(e)}>
              {especimen(e)} <small className="num" style={{ fontWeight: 400, opacity: 0.75, marginLeft: 6 }}>{Math.round((100 * visto(e)) / N)} % visto</small>
            </button>
          ))}
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) 300px', gap: 2, background: 'var(--color-divider)' }}>
          <div style={{ background: 'var(--color-bg)', paddingRight: 24 }}>
            <div className="rev-stage">
              {datos.videoUrl
                ? <video ref={videoRef} src={datos.videoUrl} preload="auto" muted playsInline aria-label={'Video de la tanda ' + datos.tanda} />
                : (
                  <p style={{ position: 'absolute', left: 0, right: 0, top: '38%', margin: '0 auto', maxWidth: 520, padding: '0 20px', textAlign: 'center', fontSize: 12.5, lineHeight: 1.55, color: 'var(--muted-2)' }}>
                    Aquí va el video de la tanda (la copia H.264 de los últimos 300 s): la genera el worker al terminar el análisis y la entrega el servidor. Disponible cuando el backend esté conectado. Sin video, el cabezal avanza igual y se puede revisar con las teclas.
                  </p>
                )}
              {/* Oscurece todo menos el tubo del espécimen que se revisa (cajas.json). */}
              {[[0, 0, 100, r.y0 / datos.alto * 100], [0, r.y1 / datos.alto * 100, 100, 100 - r.y1 / datos.alto * 100],
                [0, r.y0 / datos.alto * 100, r.x0 / datos.ancho * 100, (r.y1 - r.y0) / datos.alto * 100],
                [r.x1 / datos.ancho * 100, r.y0 / datos.alto * 100, 100 - r.x1 / datos.ancho * 100, (r.y1 - r.y0) / datos.alto * 100]].map(([x, y, a, b], k) => (
                <div key={k} className="rev-velo" style={{ left: x + '%', top: y + '%', width: a + '%', height: b + '%' }} />
              ))}
              <div className="rev-spot" style={{ left: pct(r.x0, datos.ancho), top: pct(r.y0, datos.alto), width: pct(r.x1 - r.x0, datos.ancho), height: pct(r.y1 - r.y0, datos.alto) }}>
                <span>{especimen(tubo, datos.tanda)}</span>
              </div>
              <div className="rev-badge num"><span className="nd">{fmt(s)}</span> / {fmt(N)} · segundo {s + 1} de {N} · {rate}×</div>
              <div className="rev-ahora" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>Ahora: <Muestra c={c} />{nombre(c)}</div>
              {!play && <div className="rev-pausa">En pausa · una tecla cambia solo este segundo</div>}
              {aviso && <div className="rev-toast" role="status">{aviso}</div>}
            </div>

            <div style={{ marginTop: 16 }}>
              <div className="rev-tl">
                <div className="k">Lupa · 40 s</div>
                <canvas ref={cvLupa} onClick={(e) => clicLinea(e, true)} aria-label="Lupa de 40 segundos: clic para ir a ese segundo" />
              </div>
              <div className="rev-tl" style={{ marginTop: 8 }}>
                <div>{ESP.map((e) => <div key={e} style={{ height: FILA, display: 'flex', alignItems: 'center', fontSize: 11.5, color: e === tubo ? 'var(--color-accent-700)' : 'var(--muted)', fontWeight: e === tubo ? 700 : 400 }}>{especimen(e)}</div>)}</div>
                <canvas ref={cvMain} onClick={(e) => clicLinea(e, false)} aria-label="Línea de tiempo de los especímenes: clic para ir a ese segundo" />
              </div>
              <div className="rev-tl" style={{ marginTop: 2 }}>
                <div />
                <div className="num" style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: 'var(--muted)' }}>{[0, 60, 120, 180, 240, 300].map((x) => <span key={x}>{fmt(x)}</span>)}</div>
              </div>
            </div>

            <div style={{ display: 'flex', gap: 8, marginTop: 18 }}>
              {BOTONES.map(([k, cl, label]) => (
                <button key={k} type="button" className={'rev-cls' + (c === cl ? ' ahora' : '')} onClick={() => aplicar(cl)}>
                  <span className="rev-key">{k}</span><Muestra c={cl} />{label}
                </button>
              ))}
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginTop: 14, flexWrap: 'wrap' }}>
              <span className="k">Velocidad</span>
              <span className="seg" style={{ display: 'inline-flex' }}>
                {VELOCIDADES.map((v) => (
                  <button key={v} type="button" className={'seg-opt' + (rate === v ? ' on' : '')} aria-pressed={rate === v} onClick={() => setRate(v)}>{v}×</button>
                ))}
              </span>
              <button type="button" className="btn btn-secondary" style={{ minWidth: 132 }} onClick={() => setPlay((x) => !x)}>{play ? 'Pausar' : 'Reproducir'}</button>
            </div>
            <p className="hint" style={{ margin: '12px 0 0' }}>
              <kbd>Espacio</kbd> pausa · <kbd>←</kbd> <kbd>→</kbd> 1 s · <kbd>Shift</kbd>+flecha 5 s · <kbd>R</kbd> vuelve 3 s · <kbd>Tab</kbd> siguiente espécimen · <kbd>Ctrl</kbd>+<kbd>Z</kbd> deshacer · <kbd>Ctrl</kbd>+<kbd>S</kbd> guardar · clic en una línea para ir a ese segundo
            </p>
          </div>

          <div style={{ background: 'var(--color-bg)', paddingLeft: 24, display: 'flex', flexDirection: 'column', gap: 24 }}>
            <div>
              <div className="k" style={{ marginBottom: 10 }}>Ahora</div>
              <div className="nd" style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 22 }}><Muestra c={c} />{nombre(c)}</div>
              <div style={{ fontSize: 12, marginTop: 6, color: 'var(--muted-2)' }}>{origenAhora}</div>
              <p className="hint" style={{ margin: '10px 0 0' }}>Con el video corriendo, la tecla cambia desde 0.4 s antes hasta el siguiente cambio de color; en pausa, solo el segundo actual.</p>
            </div>

            <div>
              <div className="k" style={{ marginBottom: 10 }}>Avance de este espécimen</div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <div className="meter" style={{ flex: 1 }}><div style={{ width: pct(visto(tubo), N) }} /></div>
                <span className="num nd" style={{ fontSize: 13 }}>{Math.round((100 * visto(tubo)) / N)} %</span>
              </div>
              <p className="hint" style={{ margin: '8px 0 0' }}>Solo cuenta como visto lo que pasó a 2× o más lento, y solo lo visto se guarda.</p>
            </div>

            <div>
              <div className="k" style={{ marginBottom: 6 }}>Segundos por conducta · {especimen(tubo)}</div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr auto auto', gap: '0 14px', fontSize: 11, color: 'var(--muted)', paddingBottom: 4, borderBottom: '1px solid var(--color-divider)' }}><span /><span>Antes</span><span style={{ minWidth: 42, textAlign: 'right' }}>Ahora</span></div>
              {['nado', 'inmovilidad', 'escalamiento', 'activa', null].map((k) => (
                <div key={String(k)} style={{ display: 'grid', gridTemplateColumns: '1fr auto auto', gap: '0 14px', alignItems: 'center', padding: '6px 0', borderBottom: '1px solid var(--color-divider)', fontSize: 12 }}>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}><Muestra c={k} />{nombre(k)}</span>
                  <span className="num" style={{ textAlign: 'right', color: 'var(--muted)' }}>{sistema[String(k)]} s</span>
                  <span className="num nd" style={{ textAlign: 'right', minWidth: 42, color: sistema[String(k)] !== ahora[String(k)] ? 'var(--color-accent-700)' : undefined }}>{ahora[String(k)]} s</span>
                </div>
              ))}
            </div>

            <div>
              <div className="k" style={{ marginBottom: 10 }}>Nivel al guardar</div>
              <span className="tag tag-accent">{agrupado ? 'Agrupado' : 'Preciso'}</span>
              <p className="hint" style={{ margin: '8px 0 0' }}>{agrupado ? 'Hay segundos de conducta activa: los resultados lo avisarán.' : 'Ningún segundo está como conducta activa.'}</p>
            </div>

            <div>
              <div className="k" style={{ marginBottom: 10 }}>Reiniciar el análisis</div>
              <p className="hint" style={{ margin: '0 0 10px' }}>Vuelve a analizar el video desde cero. Al empezar se borra el resultado actual, también las correcciones guardadas.</p>
              <button type="button" className="btn btn-secondary" onClick={reiniciar}>Reiniciar el análisis…</button>
            </div>
          </div>
        </div>
      </div>

      {resumen && <Resumen especimenes={ESP} L={L.current} N={N} tanda={datos.tanda} onCerrar={() => setResumen(false)} />}
    </div>
  )
}

// Resumen antes de guardar: segundos por conducta de cada espécimen, corregidos y vistos.
function Resumen({ especimenes, L, N, tanda, onCerrar }) {
  const ref = useRef(null)
  useAtraparFoco(ref, true)
  useEffect(() => {
    ref.current?.querySelector('button')?.focus()
    const onKey = (e) => { if (e.key === 'Escape') onCerrar() }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onCerrar])
  const CS = ['nado', 'inmovilidad', 'escalamiento', 'activa', null]
  return (
    <div className="dialog-backdrop" onClick={(e) => { if (e.target === e.currentTarget) onCerrar() }}>
      <div ref={ref} className="dialog" role="dialog" aria-modal="true" aria-labelledby="resT" style={{ width: 'min(760px, 100%)', border: '2px solid var(--color-text)' }}>
        <div className="dialog-title" id="resT">Resumen de la revisión</div>
        <table className="table" style={{ fontSize: 12 }}>
          <thead>
            <tr><th>Espécimen</th>{CS.map((c) => <th key={String(c)} style={{ textAlign: 'right' }}>{nombre(c)}</th>)}<th style={{ textAlign: 'right' }}>Corregidos</th><th style={{ textAlign: 'right' }}>Visto</th></tr>
          </thead>
          <tbody>
            {especimenes.map((e) => {
              const X = L[e]
              const n = (c) => X.c.filter((x) => x === c).length
              return (
                <tr key={e}>
                  <td>{especimen(e, tanda)}</td>
                  {CS.map((c) => <td key={String(c)} className="num" style={{ textAlign: 'right' }}>{n(c)} s</td>)}
                  <td className="num" style={{ textAlign: 'right' }}>{X.c.filter((x, k) => x !== X.p[k]).length}</td>
                  <td className="num" style={{ textAlign: 'right' }}>{Math.round((100 * X.v.reduce((a, b) => a + b, 0)) / N)} %</td>
                </tr>
              )
            })}
          </tbody>
        </table>
        <p className="hint">Al guardar, solo cuentan los segundos vistos; lo corregido sin verlo se descarta.</p>
        <div className="dialog-actions"><button type="button" className="btn btn-primary" onClick={onCerrar}>Cerrar</button></div>
      </div>
    </div>
  )
}

// No se puede revisar: sin permiso (403), todavía no está completado (409) o no existe.
function SinRevision({ error, clave }) {
  const st = error.response?.status
  return (
    <div className="app tema-oscuro">
      <Topbar><Brand sub="Revisión segundo a segundo" /></Topbar>
      <div className="page">
        <h3 style={{ margin: '0 0 8px' }}>{st === 403 ? 'No puedes revisar este análisis' : st === 409 ? 'Este análisis todavía no se puede revisar' : 'No existe ese análisis'}</h3>
        <p className="lead" style={{ marginBottom: 16, maxWidth: 560 }}>
          {st === 403 ? 'Solo quien subió el video o un Administrador puede revisar y corregir sus segundos.'
            : st === 409 ? 'Solo se revisa un análisis completado.' : 'Revisa el enlace o vuelve al experimento.'}
        </p>
        <Link className="btn btn-primary" to={`/experimentos/${clave}`}>Volver al experimento</Link>
      </div>
    </div>
  )
}
