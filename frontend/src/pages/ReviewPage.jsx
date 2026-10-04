import React, { useState, useEffect, useMemo, useRef, useCallback } from 'react'
import { Link } from 'react-router-dom'
import '../styles/pages/revision.css'

// Revisión segundo a segundo (mockup v2 + sección 9.7). Solo interfaz:
// la reproducción se simula y las etiquetas son de ejemplo.
const DURACION = 300
const CLASES = {
  n: { nombre: 'Nado', color: 'var(--nado)', tecla: '1' },
  i: { nombre: 'Inmovilidad', color: 'var(--inm)', tecla: '2' },
  e: { nombre: 'Escalamiento', color: 'var(--esc)', tecla: '3' },
  a: { nombre: 'Activa', color: 'var(--act)', tecla: '4' },
  x: { nombre: 'No se ve', color: 'var(--none)', tecla: '0' },
}
const TECLA_A_CLASE = { 1: 'n', 2: 'i', 3: 'e', 4: 'a', 0: 'x' }
const VELOCIDADES = [0.5, 0.75, 1, 1.5, 2]
const REACCION_S = 0.4

// Tramos de ejemplo del mockup: [clase, duración en segundos]
const TRAMOS = [['n', 58], ['e', 1], ['n', 20], ['e', 12], ['n', 18], ['a', 1], ['n', 24], ['i', 4], ['n', 20], ['i', 9], ['n', 22], ['e', 6],
  ['n', 14], ['i', 15], ['n', 10], ['i', 35], ['n', 8], ['e', 1], ['n', 6], ['a', 2], ['n', 9], ['i', 7], ['n', 13], ['x', 6]]

function expandir(tramos, desplazamiento = 0) {
  const out = tramos.flatMap(([c, d]) => Array(d).fill(c))
  const rot = out.slice(desplazamiento).concat(out.slice(0, desplazamiento))
  return rot.slice(0, DURACION)
}

const TUBOS = [
  { n: 1, left: 130, esp: {} },
  { n: 2, left: 370, esp: { left: 60, top: 190 } },
  { n: 3, left: 610, esp: { left: 90, top: 120 } },
  { n: 4, left: 850, esp: { left: 70, top: 170 } },
]

const fmt = (s) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`

function Linea({ etiquetas, desde, hasta, actual, zoom, onSeek }) {
  const ref = useRef(null)
  const span = hasta - desde
  const tramos = []
  for (let s = Math.max(0, Math.floor(desde)); s < Math.min(DURACION, Math.ceil(hasta)); s++) {
    const c = etiquetas[s]
    const ultimo = tramos[tramos.length - 1]
    if (ultimo && ultimo.c === c) ultimo.fin = s + 1
    else tramos.push({ c, ini: s, fin: s + 1 })
  }
  const click = (e) => {
    const r = ref.current.getBoundingClientRect()
    onSeek(desde + ((e.clientX - r.left) / r.width) * span)
  }
  return (
    <div className={`line ${zoom ? 'zoom' : ''}`} ref={ref} onClick={click} style={{ cursor: 'pointer' }}>
      {tramos.map((t, i) => {
        const a = Math.max(t.ini, desde), b = Math.min(t.fin, hasta)
        return <i key={i} style={{ width: `${((b - a) / span) * 100}%`, background: CLASES[t.c].color }}></i>
      })}
      <div className="head" style={{ left: `${((actual - desde) / span) * 100}%` }}></div>
    </div>
  )
}

export default function ReviewPage() {
  const [etiquetas, setEtiquetas] = useState(() => TUBOS.map((_, k) => expandir(TRAMOS, k * 37)))
  const propuestas = useMemo(() => TUBOS.map((_, k) => expandir(TRAMOS, k * 37)), [])
  const [tubo, setTubo] = useState(0)
  const [t, setT] = useState([42, 0, 0, 0])
  const [visto, setVisto] = useState([180, 300, 130, 0])
  const [play, setPlay] = useState(false)
  const [vel, setVel] = useState(1)
  const [historial, setHistorial] = useState([])
  const [aviso, setAviso] = useState(null)

  const actual = t[tubo]
  const etq = etiquetas[tubo]

  const mover = useCallback((s) => {
    setT((prev) => prev.map((v, k) => (k === tubo ? Math.min(DURACION, Math.max(0, s)) : v)))
  }, [tubo])

  // reproducción simulada
  useEffect(() => {
    if (!play) return
    const iv = setInterval(() => {
      setT((prev) => prev.map((v, k) => {
        if (k !== tubo) return v
        const nuevo = Math.min(DURACION, v + 0.1 * vel)
        if (vel <= 2) setVisto((vs) => vs.map((x, j) => (j === tubo ? Math.max(x, nuevo) : x)))
        if (nuevo >= DURACION) setPlay(false)
        return nuevo
      }))
    }, 100)
    return () => clearInterval(iv)
  }, [play, vel, tubo])

  const etiquetar = useCallback((clase) => {
    setHistorial((h) => [...h, etiquetas])
    setEtiquetas((prev) => prev.map((arr, k) => {
      if (k !== tubo) return arr
      const nuevo = [...arr]
      const seg = Math.min(DURACION - 1, Math.floor(actual))
      if (!play) {
        nuevo[seg] = clase
      } else {
        // corriendo: desde 0.4 s antes hasta el siguiente cambio de color
        const ini = Math.max(0, Math.floor(actual - REACCION_S))
        const original = arr[seg]
        let fin = seg
        while (fin < DURACION && arr[fin] === original) fin++
        for (let s = ini; s < fin; s++) nuevo[s] = clase
      }
      return nuevo
    }))
  }, [etiquetas, tubo, actual, play])

  const deshacer = useCallback(() => {
    setHistorial((h) => {
      if (!h.length) return h
      setEtiquetas(h[h.length - 1])
      return h.slice(0, -1)
    })
  }, [])

  const guardar = useCallback(() => {
    setAviso('Correcciones guardadas (demo: sin servidor todavía)')
    setTimeout(() => setAviso(null), 2500)
  }, [])

  useEffect(() => {
    const onKey = (e) => {
      if (e.target.tagName === 'INPUT') return
      if (e.ctrlKey && e.key.toLowerCase() === 'z') { e.preventDefault(); deshacer(); return }
      if (e.ctrlKey && e.key.toLowerCase() === 's') { e.preventDefault(); guardar(); return }
      if (e.key === ' ') { e.preventDefault(); setPlay((p) => !p); return }
      if (e.key === 'ArrowRight') { e.preventDefault(); mover(actual + (e.shiftKey ? 5 : 1)); return }
      if (e.key === 'ArrowLeft') { e.preventDefault(); mover(actual - (e.shiftKey ? 5 : 1)); return }
      if (e.key.toLowerCase() === 'r') { mover(actual - 3); return }
      if (e.key === 'Tab') { e.preventDefault(); setTubo((k) => (k + 1) % TUBOS.length); return }
      if (e.key in TECLA_A_CLASE) etiquetar(TECLA_A_CLASE[e.key])
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [actual, mover, etiquetar, deshacer, guardar])

  const conteo = useMemo(() => {
    const c = { n: 0, i: 0, e: 0, a: 0, x: 0 }
    etq.forEach((k) => { c[k]++ })
    return c
  }, [etq])

  const claseActual = etq[Math.min(DURACION - 1, Math.floor(actual))]
  const esPropuesta = claseActual === propuestas[tubo][Math.min(DURACION - 1, Math.floor(actual))]
  const vistoPct = Math.round((visto[tubo] / DURACION) * 100)
  const zIni = Math.max(0, Math.min(DURACION - 40, actual - 20))
  const spotLeft = TUBOS[tubo].left - 8

  return (
    <div className="pg-revision">
      <div className="app" style={{ width: 'auto', maxWidth: 1280 }}>
        <div className="crumbs">
          <Link to="/dashboard" style={{ color: 'inherit' }}>Mis experimentos</Link> <span>›</span> FST-2024-036 — Ketamina 30 mg/kg <span>›</span> <b>Revisión segundo a segundo</b>
        </div>

        <section className="rev">
          <div className="bar">
            <div className="titulo">Control · Tanda A · Día 2 <small>revisión</small></div>
            <div className="tubos">
              {TUBOS.map((tb, k) => (
                <button key={tb.n} className={k === tubo ? 'sel' : ''} onClick={() => { setTubo(k); setPlay(false) }}>
                  Tubo {tb.n}<small>{fmt(t[k])}</small>
                </button>
              ))}
            </div>
            <div className="prog"><span>Visto</span><div className="b"><i style={{ width: `${vistoPct}%` }}></i></div><b>{vistoPct} %</b></div>
            <div className="chips">
              {['n', 'i', 'e', 'a'].map((k) => (
                <span key={k} className="chip"><i style={{ background: CLASES[k].color }}></i>{CLASES[k].nombre} {conteo[k]} s</span>
              ))}
            </div>
            <button className="btnTop" onClick={() => setAviso(`Resumen tubo ${tubo + 1}: ${['n', 'i', 'e', 'a', 'x'].map((k) => `${CLASES[k].nombre} ${conteo[k]} s`).join(' · ')}`)}>Resumen</button>
            <button className="btnTop p" onClick={guardar}>Guardar</button>
          </div>

          <div className="stage">
            <div className="scene">
              {TUBOS.map((tb) => (
                <div key={tb.n} className={`tubo t${tb.n}`}>
                  <div className="agua"></div>
                  <div className="esp" style={tb.esp}></div>
                </div>
              ))}
            </div>
            <div className="velo" style={{ left: 0, top: 0, right: 0, height: '22px' }}></div>
            <div className="velo" style={{ left: 0, top: '348px', right: 0, bottom: 0 }}></div>
            <div className="velo" style={{ left: 0, top: '22px', width: `${spotLeft}px`, height: '326px' }}></div>
            <div className="velo" style={{ left: `${spotLeft + 226}px`, top: '22px', right: 0, height: '326px' }}></div>
            <div className="spot" style={{ left: `${spotLeft}px` }}><span>Tubo {tubo + 1}</span></div>
            <div className="badge info">{fmt(actual)} / {fmt(DURACION)}</div>
            <div className="badge ahora">
              <div className="lab">Ahora</div>
              <div className="val" style={{ color: CLASES[claseActual].color }}>{CLASES[claseActual].nombre.toUpperCase()}</div>
              <div className="src">{esPropuesta ? 'propuesta del sistema' : 'corregido'}</div>
            </div>
            {!play && <div className="pausado">En pausa · una tecla cambia solo este segundo</div>}
            {aviso && <div className="pausado" style={{ bottom: 'auto', top: '60px' }}>{aviso}</div>}
          </div>

          <div className="tl">
            <div className="fila">
              <div className="lbl">Lupa (40 s)</div>
              <Linea etiquetas={etq} desde={zIni} hasta={zIni + 40} actual={actual} zoom onSeek={mover} />
            </div>
            <div className="fila">
              <div className="lbl">Tubo {tubo + 1} · 300 s</div>
              <Linea etiquetas={etq} desde={0} hasta={DURACION} actual={actual} onSeek={mover} />
            </div>
            <div className="marcas"><span>0:00</span><span>1:00</span><span>2:00</span><span>3:00</span><span>4:00</span><span>5:00</span></div>
          </div>

          <div className="ctl">
            <div className="botones">
              {['n', 'i', 'e', 'a', 'x'].map((k) => (
                <button key={k} onClick={() => etiquetar(k)}><span className="key">{CLASES[k].tecla}</span>{CLASES[k].nombre}</button>
              ))}
            </div>
            <div className="vel">Velocidad
              {VELOCIDADES.map((v) => (
                <button key={v} className={vel === v ? 'sel' : ''} onClick={() => setVel(v)}>{v}×</button>
              ))}
            </div>
            <div className="pista"><kbd>Espacio</kbd> pausa · <kbd>←</kbd><kbd>→</kbd> 1 s · <kbd>Shift</kbd>+flecha 5 s · <kbd>R</kbd> vuelve 3 s · <kbd>Tab</kbd> siguiente tubo · <kbd>Ctrl</kbd>+<kbd>Z</kbd> deshacer · <kbd>Ctrl</kbd>+<kbd>S</kbd> guardar</div>
          </div>
        </section>
      </div>
    </div>
  )
}
