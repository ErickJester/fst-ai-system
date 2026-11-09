import React, { useRef, useState } from 'react'
import PanelGrafica from './PanelGrafica'
import Patrones from './Patrones'
import { CONDUCTAS_GRAFICA, NOTA_MIXTO, SEGUNDOS_MINUTO, estilosDeGrupos, textoMediaDe, textoN } from './estilos'

// G2 · Curva por minuto: una línea por grupo para la conducta elegida, con banda ± desviación estándar y un marcador por minuto.
// Usa porMinuto tal cual lo manda el backend: no recalcula medias ni desviaciones. Un minuto sin especímenes
// (n = 0) no se dibuja; un minuto incompleto va con marcador hueco y su rango de segundos.
const W = 420
const H = 300
const M = { l: 46, r: 12, t: 16, b: 62 }
const PH = H - M.t - M.b
const PW = W - M.l - M.r
const MARCAS_Y = [0, 10, 20, 30, 40, 50, 60]
const TRAZOS = { solida: undefined, discontinua: '6 4', punteada: '1.5 3', 'trazo-punto': '8 3 1.5 3', larga: '14 4' }

const dibujable = (g) => !g.nivelMixto && !!g.porMinuto && g.n > 0

// El valor de un grupo en un minuto, si hay especímenes con segundos en ese minuto y su media existe.
function entradaDe(g, minuto, clave) {
  const e = g.porMinuto?.find((x) => x.minuto === minuto)
  if (!e || e.n === 0) return null
  const s = e.porConducta?.[clave]
  return s && s.media != null ? { e, s } : null
}

// Los minutos seguidos con valor; un minuto sin datos corta la línea.
function tramos(g, minutos, clave) {
  const salida = []
  let actual = []
  minutos.forEach((m, i) => {
    const p = entradaDe(g, m, clave)
    if (p) actual.push({ i, m, ...p })
    else if (actual.length) {
      salida.push(actual)
      actual = []
    }
  })
  if (actual.length) salida.push(actual)
  return salida
}

function rangoSegundos(e) {
  return e.segundosMin === e.segundosMax ? `${e.segundosMin}` : `${e.segundosMin}–${e.segundosMax}`
}

function celda(g, m, clave) {
  if (!dibujable(g)) return g.nivelMixto ? 'por tanda' : 'sin datos'
  const p = entradaDe(g, m, clave)
  if (!p) return 'sin datos'
  const texto = textoMediaDe(p.s.media, p.s.de)
  return p.e.incompleto ? `${texto} · incompleto, ${rangoSegundos(p.e)} s de ${SEGUNDOS_MINUTO}` : texto
}

function Marcador({ forma, x, y, color, hueco }) {
  const r = 4
  const marca = { stroke: color, strokeWidth: 1.6, fill: hueco ? 'var(--color-bg)' : color }
  if (forma === 'cruz') {
    return (
      <g stroke={color} strokeWidth="1.8">
        <line x1={x - r} y1={y - r} x2={x + r} y2={y + r} />
        <line x1={x - r} y1={y + r} x2={x + r} y2={y - r} />
      </g>
    )
  }
  if (forma === 'rombo') return <polygon points={`${x},${y - r} ${x + r},${y} ${x},${y + r} ${x - r},${y}`} {...marca} />
  if (forma === 'triangulo') return <polygon points={`${x},${y - r} ${x + r},${y + r * 0.8} ${x - r},${y + r * 0.8}`} {...marca} />
  if (forma === 'cuadrado') return <rect x={x - r} y={y - r} width={2 * r} height={2 * r} {...marca} />
  return <circle cx={x} cy={y} r={r} {...marca} />
}

// La muestra de leyenda de un grupo en esta gráfica: su línea con el trazo y el marcador del grupo.
function MuestraLinea({ color, trazo, forma }) {
  return (
    <svg width="26" height="12" viewBox="0 0 26 12" aria-hidden="true" focusable="false" style={{ flex: 'none' }}>
      <line x1="1" y1="6" x2="25" y2="6" stroke={color} strokeWidth="1.6" strokeDasharray={TRAZOS[trazo]} />
      <Marcador forma={forma} x={13} y={6} color={color} />
    </svg>
  )
}

// La banda media ± desviación estándar: solo donde hay DE; un minuto aislado se dibuja como una franja estrecha.
function subtramosConDe(t) {
  const salida = []
  let actual = []
  for (const p of t) {
    if (p.s.de != null) actual.push(p)
    else if (actual.length) {
      salida.push(actual)
      actual = []
    }
  }
  if (actual.length) salida.push(actual)
  return salida
}

function pathBanda(sub, x, y) {
  if (sub.length === 1) {
    const p = sub[0]
    const xa = x(p.i)
    const arriba = y(p.s.media + p.s.de)
    const abajo = y(p.s.media - p.s.de)
    return `M${xa - 4},${arriba} L${xa + 4},${arriba} L${xa + 4},${abajo} L${xa - 4},${abajo} Z`
  }
  const arriba = sub.map((p) => `${x(p.i)},${y(p.s.media + p.s.de)}`)
  const abajo = sub.map((p) => `${x(p.i)},${y(p.s.media - p.s.de)}`).reverse()
  return `M${[...arriba, ...abajo].join(' L')} Z`
}

const pathLinea = (t, x, y) => `M${t.map((p) => `${x(p.i)},${y(p.s.media)}`).join(' L')}`

export default function G2CurvaPorMinuto({ datos, prefijo }) {
  const svgRef = useRef(null)
  const [clave, setClave] = useState('inmovilidad')
  const grupos = datos.grupos
  const minutos = datos.minutos
  const estilos = estilosDeGrupos(grupos)
  const conducta = CONDUCTAS_GRAFICA.find((c) => c.clave === clave)
  const x = (i) => M.l + PW * ((i + 0.5) / minutos.length)
  const y = (v) => M.t + PH - (Math.min(Math.max(v, 0), SEGUNDOS_MINUTO) / SEGUNDOS_MINUTO) * PH
  const incompletoEn = (m) => grupos.some((g) => dibujable(g) && g.porMinuto.find((e) => e.minuto === m)?.incompleto)

  const avisos = []
  const notas = []
  for (const g of grupos) {
    if (g.nivelMixto) avisos.push(`${g.etiqueta}: ${NOTA_MIXTO}`)
    else if (g.n === 0) avisos.push(`${g.etiqueta}: sin datos.`)
    if (g.faltantes.length) {
      const faltan = g.faltantes.reduce((a, f) => a + f.nCilindros, 0)
      avisos.push(`${g.etiqueta}: ${faltan} ${faltan === 1 ? 'espécimen pendiente' : 'especímenes pendientes'} (${g.faltantes.map((f) => `Tanda ${f.letra} ${f.estado ?? 'sin subir'}`).join(', ')}).`)
    }
  }
  notas.push('Un minuto con menos de 60 s en algún espécimen es incompleto; sus segundos son los reales.')

  const titulo = '¿Cómo cambió minuto a minuto?'

  return (
    <PanelGrafica
      titulo={titulo}
      subtitulo="Promedio por minuto de la conducta elegida, con banda ± desviación estándar."
      nombre={`g2_${clave.replace(/ /g, '_')}`}
      svgRef={svgRef}
      controles={(
        <>
          <label htmlFor={`${prefijo}-conducta`} style={{ fontSize: 12.5 }}>Conducta </label>
          <select id={`${prefijo}-conducta`} className="input" style={{ width: 190, padding: '4px 6px', fontSize: 12 }} value={clave}
            onChange={(e) => setClave(e.target.value)}>
            {CONDUCTAS_GRAFICA.map((c) => <option key={c.clave} value={c.clave}>{c.etiqueta}</option>)}
          </select>
        </>
      )}
      leyenda={grupos.map((g) => {
        const est = estilos.get(g.idGrupo)
        const estado = g.nivelMixto ? ' · tandas no comparables' : g.n === 0 ? ' · sin datos' : ''
        return (
          <span key={g.idGrupo} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <MuestraLinea color={est.color} trazo={est.trazo} forma={est.forma} />{g.etiqueta}{estado}
          </span>
        )
      })}
      avisos={avisos} notas={notas}
      tabla={(
        <table className="table" style={{ fontSize: 12 }}>
          <caption className="hint" style={{ textAlign: 'left', padding: '0 0 6px' }}>
            {`Promedio ± desviación estándar por minuto, Día 2, en segundos. Conducta: ${conducta.etiqueta.toLowerCase()}.`}
          </caption>
          <thead>
            <tr>
              <th>Grupo</th><th>n</th>
              {minutos.map((m) => <th key={m} style={{ textAlign: 'right' }}>{`min ${m}`}</th>)}
            </tr>
          </thead>
          <tbody>
            {grupos.map((g) => (
              <tr key={g.idGrupo}>
                <td>{g.etiqueta}</td>
                <td className="num">{textoN(g)}</td>
                {minutos.map((m) => <td key={m} className="num" style={{ textAlign: 'right' }}>{celda(g, m, clave)}</td>)}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    >
      <svg ref={svgRef} viewBox={`0 0 ${W} ${H}`} role="img" aria-labelledby={`${prefijo}-t ${prefijo}-d`}
        style={{ display: 'block', width: '100%', maxWidth: 560, height: 'auto', fontFamily: 'var(--font-body)' }}>
        <title id={`${prefijo}-t`}>{titulo}</title>
        <desc id={`${prefijo}-d`}>Curva por minuto: media por minuto de la conducta elegida en cada grupo, con banda ± desviación estándar, en segundos del Día 2.</desc>
        <Patrones prefijo={prefijo} />
        {MARCAS_Y.map((v) => (
          <g key={v}>
            <line x1={M.l} x2={W - M.r} y1={y(v)} y2={y(v)} stroke="var(--color-divider)" strokeWidth={v === 0 ? 1.4 : 0.8} />
            <text x={M.l - 6} y={y(v) + 4} textAnchor="end" fontSize="10.5" fill="var(--muted-2)">{v}</text>
          </g>
        ))}
        <text x={M.l} y={M.t - 4} fontSize="10.5" fill="var(--muted-2)">s</text>
        {minutos.map((m, i) => (
          <g key={m}>
            <text x={x(i)} y={M.t + PH + 16} textAnchor="middle" fontSize="10.5" fill="var(--color-text)">{`min ${m}`}</text>
            {incompletoEn(m) && (
              <text x={x(i)} y={M.t + PH + 30} textAnchor="middle" fontSize="9.5" fill="var(--muted-2)">incompleto</text>
            )}
          </g>
        ))}
        {grupos.filter(dibujable).map((g) => {
          const est = estilos.get(g.idGrupo)
          return (
            <g key={g.idGrupo}>
              {tramos(g, minutos, clave).map((t) => (
                <g key={t[0].m}>
                  {subtramosConDe(t).map((sub) => (
                    <path key={sub[0].m} data-banda={g.etiqueta} d={pathBanda(sub, x, y)} fill={est.color} fillOpacity="0.14" stroke="none" />
                  ))}
                  <path data-linea={g.etiqueta} d={pathLinea(t, x, y)} fill="none" stroke={est.color} strokeWidth="1.6"
                    strokeDasharray={TRAZOS[est.trazo]} />
                  {t.map((p) => (
                    <g key={p.m} data-punto={`${g.etiqueta}|${p.m}`} data-incompleto={p.e.incompleto ? 'si' : undefined}>
                      <title>{`${g.etiqueta}, min ${p.m}: ${textoMediaDe(p.s.media, p.s.de)}${p.e.incompleto ? `, incompleto (${rangoSegundos(p.e)} s de ${SEGUNDOS_MINUTO})` : ''}`}</title>
                      <Marcador forma={est.forma} x={x(p.i)} y={y(p.s.media)} color={est.color} hueco={p.e.incompleto} />
                    </g>
                  ))}
                </g>
              ))}
            </g>
          )
        })}
        <line x1={M.l} x2={W - M.r} y1={y(0)} y2={y(0)} stroke="var(--color-text)" strokeWidth="1.4" />
      </svg>
    </PanelGrafica>
  )
}
