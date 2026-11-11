import React, { useId, useState } from 'react'
import Patrones, { patronId } from './graficas/Patrones'
import MuestraLeyenda from './graficas/MuestraLeyenda'
import { CONDUCTAS_GRAFICA, SIN_VER } from './graficas/estilos'

// Segundo a segundo: la conducta de cada segundo de un espécimen, en una franja y en una tabla. Lo que llega
// del servidor se muestra tal cual; un segundo que no viene queda vacío en la franja y no aparece en la tabla.
const CATEGORIAS = [...CONDUCTAS_GRAFICA, SIN_VER]
// Cuadrícula: un renglón por minuto y un cuadro por segundo (60 por renglón), para que cada segundo se distinga.
const ANCHO_CUADRO = 12
const ALTO_CUADRO = 22
const PASO_RENGLON = 28
const MARGEN_IZQ = 44
const ANCHO_VISTA = MARGEN_IZQ + 60 * ANCHO_CUADRO

// La categoría de un segundo: null es «Sin ver».
const categoriaDe = (conducta) => (conducta == null ? SIN_VER : CONDUCTAS_GRAFICA.find((c) => c.clave === conducta) ?? { etiqueta: conducta, color: 'var(--color-divider)', patron: null, clave: conducta })

// m:ss de un número de segundos desde el inicio.
function mss(segundos) {
  return `${Math.floor(segundos / 60)}:${String(segundos % 60).padStart(2, '0')}`
}

// El segundo s (1 a 300) empieza en s − 1: el segundo 1 es 0:00, como en la revisión.
const tiempoDe = (s) => mss(s - 1)

// Los textos del origen: «Sistema» para lo que sale de la máquina y los de la revisión para lo demás.
const ORIGEN = {
  maquina: 'Sistema',
  confirmada: 'Confirmado en revisión',
  corregida: 'Corregido en revisión',
  dudosa: 'Marcado como no se ve',
}

// Un origen que no está en la lista (o null) se dice «Sin dato», sin inventar una corrección.
const origenTexto = (origen) => (Object.prototype.hasOwnProperty.call(ORIGEN, origen) ? ORIGEN[origen] : 'Sin dato')

export default function SegundoASegundo({ datos }) {
  const id = useId().replace(/[^a-zA-Z0-9]/g, '')
  const especimenes = datos?.especimenes ?? []
  const [indice, setIndice] = useState(0)

  if (!especimenes.length) return <p className="hint">No hay segundos para mostrar.</p>

  const i = Math.min(indice, especimenes.length - 1)
  const actual = especimenes[i]
  const W = datos.duracionS || 300
  const minutos = Math.max(1, Math.ceil(W / 60))
  const altoVista = minutos * PASO_RENGLON + 20
  const ordenados = [...actual.segundos].sort((a, b) => a.s - b.s)
  const etiquetaEspecimen = `Tanda ${datos.letra} · Espécimen ${actual.numeroCilindro}`

  return (
    <section aria-labelledby={`${id}-titulo`} style={{ display: 'flex', flexDirection: 'column', gap: 14, minWidth: 0 }}>
      <h3 id={`${id}-titulo`} style={{ margin: 0, fontSize: 16 }}>{`Segundo a segundo · ${datos.grupo?.etiqueta ?? ''}, Tanda ${datos.letra}`}</h3>

      <div className="no-print" style={{ display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'center' }}>
        <label htmlFor={`${id}-esp`} style={{ fontSize: 12.5 }}>Espécimen </label>
        <select id={`${id}-esp`} className="input" style={{ width: 230, padding: '4px 6px', fontSize: 12 }} value={i}
          onChange={(e) => setIndice(Number(e.target.value))}>
          {especimenes.map((e, k) => (
            <option key={e.numeroCilindro ?? k} value={k}>{`Tanda ${datos.letra} · Espécimen ${e.numeroCilindro}`}</option>
          ))}
        </select>
      </div>

      <svg viewBox={`0 0 ${ANCHO_VISTA} ${altoVista}`} role="img" aria-labelledby={`${id}-t ${id}-d`}
        style={{ display: 'block', width: '100%', maxWidth: 800, height: 'auto', fontFamily: 'var(--font-body)' }}>
        <title id={`${id}-t`}>{`Conducta segundo a segundo, ${etiquetaEspecimen}`}</title>
        <desc id={`${id}-d`}>Una cuadrícula con un renglón por minuto y un cuadro por segundo: el color y el patrón indican la conducta; los segundos sin dato quedan vacíos.</desc>
        <Patrones prefijo={id} />
        {Array.from({ length: minutos }, (_, m) => (
          <text key={m} x={0} y={m * PASO_RENGLON + ALTO_CUADRO / 2 + 3.5} fontSize="10" fill="var(--muted-2)">{`Min ${m + 1}`}</text>
        ))}
        {actual.segundos.map((x) => {
          const c = categoriaDe(x.conducta)
          const sinVer = x.conducta == null
          const m = Math.floor((x.s - 1) / 60)
          const px = MARGEN_IZQ + ((x.s - 1) % 60) * ANCHO_CUADRO
          const py = m * PASO_RENGLON
          return (
            <g key={x.s} data-s={x.s} data-conducta={sinVer ? 'sin ver' : x.conducta}>
              <rect x={px} y={py} width={ANCHO_CUADRO - 1.5} height={ALTO_CUADRO} fill={c.color} shapeRendering="crispEdges" />
              {c.patron && <rect x={px} y={py} width={ANCHO_CUADRO - 1.5} height={ALTO_CUADRO} fill={`url(#${patronId(id, c.patron)})`} />}
              {sinVer && (
                <rect x={px} y={py} width={ANCHO_CUADRO - 1.5} height={ALTO_CUADRO} fill="none" stroke="var(--color-text)" strokeWidth="1"
                  vectorEffect="non-scaling-stroke" />
              )}
            </g>
          )
        })}
        {[0, 10, 20, 30, 40, 50].map((seg) => (
          <g key={seg}>
            <line x1={MARGEN_IZQ + seg * ANCHO_CUADRO - 0.75} x2={MARGEN_IZQ + seg * ANCHO_CUADRO - 0.75} y1={minutos * PASO_RENGLON - 2} y2={minutos * PASO_RENGLON + 3}
              stroke="var(--color-text)" strokeWidth="1" vectorEffect="non-scaling-stroke" />
            <text x={MARGEN_IZQ + seg * ANCHO_CUADRO} y={minutos * PASO_RENGLON + 14} fontSize="10" fill="var(--muted-2)">{`${seg} s`}</text>
          </g>
        ))}
      </svg>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px 16px', fontSize: 12 }}>
        {CATEGORIAS.map((c) => (
          <span key={c.clave} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <MuestraLeyenda color={c.color} patron={c.patron} borde={c.clave === SIN_VER.clave ? 'var(--color-text)' : undefined} />{c.etiqueta}
          </span>
        ))}
      </div>

      <div tabIndex={0} style={{ maxHeight: 360, overflowY: 'auto', overflowX: 'auto' }}>
        <table className="table" style={{ fontSize: 12 }}>
          <caption className="hint" style={{ textAlign: 'left', padding: '0 0 6px' }}>
            {`Conducta de cada segundo del espécimen ${actual.numeroCilindro}, Tanda ${datos.letra}.`}
          </caption>
          <thead>
            <tr><th>Tiempo (m:ss)</th><th>Conducta</th><th>Origen</th></tr>
          </thead>
          <tbody>
            {ordenados.map((x) => (
              <tr key={x.s}>
                <td className="num">{tiempoDe(x.s)}</td>
                <td>{categoriaDe(x.conducta).etiqueta}</td>
                <td>{origenTexto(x.origen)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
