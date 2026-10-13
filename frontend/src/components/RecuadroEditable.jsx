import React, { useRef } from 'react'

const MIN = 40 // lado mínimo del recuadro, en píxeles de la imagen
const ESQUINAS = [['x0', 'y0'], ['x1', 'y0'], ['x0', 'y1'], ['x1', 'y1']]
const limitar = (v, a, b) => Math.min(b, Math.max(a, v))

// Recuadro de un cilindro sobre la imagen fija de la pausa (D-89). Todo va en píxeles de la
// imagen: el SVG usa su tamaño como viewBox, así las coordenadas no dependen de cómo se vea.
// Se arrastra desde el centro y se redimensiona desde las esquinas; con el foco en el
// recuadro, las flechas lo mueven (Mayús: 20 px) y Alt + flechas cambian su tamaño.
// Los demás cilindros se ven punteados, solo como referencia.
export default function RecuadroEditable({ src, ancho, alto, recuadro, otros = [], onChange, etiqueta }) {
  const svgRef = useRef(null)
  const arrastre = useRef(null) // { modo: 'mover' | [kx, ky], desde: {x, y}, inicial }

  // Punto del puntero → coordenadas de la imagen.
  function punto(e) {
    const p = svgRef.current.createSVGPoint()
    p.x = e.clientX
    p.y = e.clientY
    const q = p.matrixTransform(svgRef.current.getScreenCTM().inverse())
    return { x: q.x, y: q.y }
  }

  // Mantiene el recuadro dentro de la imagen y con un tamaño mínimo.
  function ajustar(r) {
    const x0 = limitar(Math.round(Math.min(r.x0, r.x1 - MIN)), 0, ancho - MIN)
    const y0 = limitar(Math.round(Math.min(r.y0, r.y1 - MIN)), 0, alto - MIN)
    return { ...recuadro, x0, y0, x1: limitar(Math.round(Math.max(r.x1, x0 + MIN)), x0 + MIN, ancho), y1: limitar(Math.round(Math.max(r.y1, y0 + MIN)), y0 + MIN, alto) }
  }

  function mover(r, dx, dy) {
    const w = r.x1 - r.x0
    const h = r.y1 - r.y0
    const x0 = limitar(r.x0 + dx, 0, ancho - w)
    const y0 = limitar(r.y0 + dy, 0, alto - h)
    return { ...r, x0: Math.round(x0), y0: Math.round(y0), x1: Math.round(x0 + w), y1: Math.round(y0 + h) }
  }

  function empezar(modo) {
    return (e) => {
      e.preventDefault()
      e.stopPropagation()
      // Capturar el puntero sigue el arrastre aunque salga del recuadro; si no se puede, igual funciona.
      try { e.currentTarget.setPointerCapture(e.pointerId) } catch { /* puntero no activo */ }
      arrastre.current = { modo, desde: punto(e), inicial: recuadro }
    }
  }

  function seguir(e) {
    const a = arrastre.current
    if (!a) return
    const p = punto(e)
    const dx = p.x - a.desde.x
    const dy = p.y - a.desde.y
    if (a.modo === 'mover') onChange(mover(a.inicial, dx, dy))
    else {
      const [kx, ky] = a.modo
      onChange(ajustar({ ...a.inicial, [kx]: a.inicial[kx] + dx, [ky]: a.inicial[ky] + dy }))
    }
  }

  function soltar() {
    arrastre.current = null
  }

  function onKey(e) {
    const paso = e.shiftKey ? 20 : 4
    const d = { ArrowLeft: [-paso, 0], ArrowRight: [paso, 0], ArrowUp: [0, -paso], ArrowDown: [0, paso] }[e.key]
    if (!d) return
    e.preventDefault()
    if (e.altKey) onChange(ajustar({ ...recuadro, x1: recuadro.x1 + d[0], y1: recuadro.y1 + d[1] }))
    else onChange(mover(recuadro, d[0], d[1]))
  }

  const r = recuadro
  const lado = Math.max(14, Math.round(ancho / 60)) // tamaño de las esquinas
  return (
    <div style={{ position: 'relative', lineHeight: 0, background: '#111', userSelect: 'none' }}>
      {src ? <img src={src} alt="Imagen fija del instante de la pausa" style={{ width: '100%', display: 'block' }} /> : <div style={{ aspectRatio: ancho + ' / ' + alto }} />}
      <svg ref={svgRef} viewBox={`0 0 ${ancho} ${alto}`} style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', touchAction: 'none' }}
        onPointerMove={seguir} onPointerUp={soltar} onPointerCancel={soltar}>
        {/* Oscurece todo menos el recuadro que se ajusta. */}
        <path d={`M0 0H${ancho}V${alto}H0Z M${r.x0} ${r.y0}V${r.y1}H${r.x1}V${r.y0}Z`} fill="rgba(0,0,0,.35)" fillRule="evenodd" pointerEvents="none" />
        {otros.map((o) => (
          <g key={o.cilindro} pointerEvents="none">
            <rect x={o.x0} y={o.y0} width={o.x1 - o.x0} height={o.y1 - o.y0} fill="none" stroke="#fff" strokeOpacity=".7" strokeWidth={ancho / 400} strokeDasharray={ancho / 120} />
            <text x={o.x0 + 8} y={o.y0 + ancho / 45} fill="#fff" fontSize={ancho / 55} fontFamily="monospace">{o.cilindro}</text>
          </g>
        ))}
        <rect x={r.x0} y={r.y0} width={r.x1 - r.x0} height={r.y1 - r.y0} fill="rgba(158,27,80,.12)" stroke="#e8336f" strokeWidth={ancho / 250}
          style={{ cursor: 'move', outline: 'none' }} tabIndex={0} aria-label={`${etiqueta}: x ${r.x0}–${r.x1}, y ${r.y0}–${r.y1}`} onPointerDown={empezar('mover')} onKeyDown={onKey} />
        <text x={r.x0 + 8} y={r.y0 + ancho / 45} fill="#fff" fontSize={ancho / 50} fontFamily="monospace" fontWeight="700" pointerEvents="none">{r.cilindro}</text>
        {ESQUINAS.map(([kx, ky]) => (
          <rect key={kx + ky} x={r[kx] - lado / 2} y={r[ky] - lado / 2} width={lado} height={lado} fill="#fff" stroke="#e8336f" strokeWidth={ancho / 500}
            style={{ cursor: (kx === 'x0') === (ky === 'y0') ? 'nwse-resize' : 'nesw-resize' }} onPointerDown={empezar([kx, ky])} />
        ))}
      </svg>
    </div>
  )
}
