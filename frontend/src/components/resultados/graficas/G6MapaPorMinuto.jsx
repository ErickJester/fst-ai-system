import React, { useId, useRef, useState } from 'react'
import PanelGrafica from './PanelGrafica'
import { renglonesDeEtiqueta } from './G1BarrasGrupos'
import { CONDUCTAS_GRAFICA, NOTA_MIXTO, SEGUNDOS_MINUTO, numero, textoN } from './estilos'

// G6 · Mapa por minuto: la media de la conducta elegida en seis rangos fijos de 10 s, por grupo y minuto.
// Usa porMinuto tal cual lo manda el backend. Cada rango lleva su relleno y su patrón, para el blanco y negro.
// Los patrones viven en el <defs> de este mismo <svg>, con ids que empiezan por prefijo.
const W = 420
const TOP = 34
const IZQ = 118
const DER = 4
const RH = 44

const RANGOS = [
  { etiqueta: '0–10 s', fondo: 'var(--color-accent-100)', opacidad: 1, tinta: 'var(--color-text)', patron: 'punteado' },
  { etiqueta: '10–20 s', fondo: 'var(--color-accent-300)', opacidad: 1, tinta: 'var(--color-text)', patron: 'rayado' },
  { etiqueta: '20–30 s', fondo: 'var(--color-accent-700)', opacidad: 1, tinta: 'var(--color-bg)', patron: 'cuadriculado' },
  { etiqueta: '30–40 s', fondo: 'var(--color-text)', opacidad: 0.35, tinta: 'var(--color-text)', patron: 'horizontal' },
  { etiqueta: '40–50 s', fondo: 'var(--color-text)', opacidad: 0.7, tinta: 'var(--color-bg)', patron: 'vertical' },
  { etiqueta: '50–60 s', fondo: 'var(--color-text)', opacidad: 1, tinta: 'var(--color-bg)', patron: 'cruzado' },
]

const dibujable = (g) => !g.nivelMixto

// El rango de una media: los límites van al rango superior; 60 cae en 50–60.
function rangoDe(media) {
  return Math.min(RANGOS.length - 1, Math.max(0, Math.floor(media / 10)))
}

function rangoSegundos(e) {
  return e.segundosMin === e.segundosMax ? `${e.segundosMin}` : `${e.segundosMin}–${e.segundosMax}`
}

// La celda de un grupo en un minuto; null si no hay especímenes con segundos ahí o la media no existe.
function celdaDe(g, minuto, clave) {
  const e = g.porMinuto?.find((x) => x.minuto === minuto)
  if (!e || e.n === 0) return null
  const s = e.porConducta?.[clave]
  if (!s || s.media == null) return null
  return { e, s, k: rangoDe(s.media) }
}

function textoCelda(g, minuto, clave) {
  if (g.nivelMixto) return 'por tanda'
  const c = celdaDe(g, minuto, clave)
  if (!c) return 'sin datos'
  const texto = `${numero(c.s.media)} s`
  return c.e.incompleto ? `${texto} · incompleto, ${rangoSegundos(c.e)} s de ${SEGUNDOS_MINUTO}` : texto
}

// Un patrón de 6 × 6 px con la tinta del rango; los rayados van girados 45°.
function Teselado({ patron, ink }) {
  if (patron === 'punteado') return <circle cx="3" cy="3" r="1.2" fill={ink} />
  if (patron === 'rayado') return <line x1="0" y1="0" x2="0" y2="6" stroke={ink} strokeWidth="1.6" />
  if (patron === 'cuadriculado') {
    return (
      <>
        <line x1="0" y1="3" x2="6" y2="3" stroke={ink} strokeWidth="1" />
        <line x1="3" y1="0" x2="3" y2="6" stroke={ink} strokeWidth="1" />
      </>
    )
  }
  if (patron === 'horizontal') return <line x1="0" y1="3" x2="6" y2="3" stroke={ink} strokeWidth="1.4" />
  if (patron === 'vertical') return <line x1="3" y1="0" x2="3" y2="6" stroke={ink} strokeWidth="1.4" />
  return (
    <>
      <line x1="0" y1="0" x2="6" y2="6" stroke={ink} strokeWidth="1" />
      <line x1="6" y1="0" x2="0" y2="6" stroke={ink} strokeWidth="1" />
    </>
  )
}

function patronesRango(prefijo) {
  return [
    ...RANGOS.map((r, k) => (
      <pattern key={k} id={`${prefijo}-p${k}`} patternUnits="userSpaceOnUse" width="6" height="6"
        patternTransform={r.patron === 'rayado' ? 'rotate(45)' : undefined}>
        <Teselado patron={r.patron} ink={r.tinta} />
      </pattern>
    )),
    <pattern key="sin" id={`${prefijo}-sin`} patternUnits="userSpaceOnUse" width="6" height="6" patternTransform="rotate(45)">
      <line x1="0" y1="0" x2="0" y2="6" stroke="var(--color-neutral-400)" strokeWidth="1.4" />
    </pattern>,
  ]
}

// La muestra de leyenda de un rango (o de «sin datos»): su relleno y su patrón, con sus propios ids.
function MuestraRango({ k, sin }) {
  const prefijo = useId().replace(/[^a-zA-Z0-9]/g, '')
  const r = RANGOS[k]
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true" focusable="false" style={{ flex: 'none', border: '1px solid var(--color-divider)' }}>
      <defs>{patronesRango(prefijo)}</defs>
      {sin ? (
        <>
          <rect width="14" height="14" fill="var(--color-bg)" />
          <rect width="14" height="14" fill={`url(#${prefijo}-sin)`} />
        </>
      ) : (
        <>
          <rect width="14" height="14" fill={r.fondo} fillOpacity={r.opacidad} />
          <rect width="14" height="14" fill={`url(#${prefijo}-p${k})`} />
        </>
      )}
    </svg>
  )
}

// Ancho aproximado de un texto corto, para el fondo que lo separa del patrón.
function anchoTexto(texto, anchoCaracter) {
  return texto.length * anchoCaracter + 6
}

export default function G6MapaPorMinuto({ datos, prefijo }) {
  const svgRef = useRef(null)
  const [clave, setClave] = useState('inmovilidad')
  const grupos = datos.grupos
  const minutos = datos.minutos
  const conducta = CONDUCTAS_GRAFICA.find((c) => c.clave === clave)
  const CW = (W - IZQ - DER) / minutos.length
  const H = TOP + grupos.length * RH + 4
  const incompletoEn = (m) => grupos.some((g) => dibujable(g) && g.porMinuto?.find((e) => e.minuto === m)?.incompleto)

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

  const titulo = '¿En qué minuto cambia?'
  const maxCar = Math.max(6, Math.floor((IZQ - 10) / 6.3))

  return (
    <PanelGrafica
      titulo={titulo}
      subtitulo="Promedio por minuto de la conducta elegida, en seis rangos de 10 s."
      nombre={`g6_mapa_${clave.replace(/ /g, '_')}`}
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
      leyenda={[
        ...RANGOS.map((r, k) => (
          <span key={r.etiqueta} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <MuestraRango k={k} />{r.etiqueta}
          </span>
        )),
        <span key="sin" style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <MuestraRango sin />sin datos
        </span>,
      ]}
      avisos={avisos} notas={notas}
      tabla={(
        <table className="table" style={{ fontSize: 12 }}>
          <caption className="hint" style={{ textAlign: 'left', padding: '0 0 6px' }}>
            {`Promedio por minuto, Día 2, en segundos. Conducta: ${conducta.etiqueta.toLowerCase()}.`}
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
                {minutos.map((m) => <td key={m} className="num" style={{ textAlign: 'right' }}>{textoCelda(g, m, clave)}</td>)}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    >
      <svg ref={svgRef} viewBox={`0 0 ${W} ${H}`} role="img" aria-labelledby={`${prefijo}-t ${prefijo}-d`}
        style={{ display: 'block', width: '100%', maxWidth: 560, height: 'auto', fontFamily: 'var(--font-body)' }}>
        <title id={`${prefijo}-t`}>{titulo}</title>
        <desc id={`${prefijo}-d`}>Mapa por minuto: media de la conducta elegida por grupo y minuto, en seis rangos de 10 segundos.</desc>
        <defs>{patronesRango(prefijo)}</defs>
        {minutos.map((m, c) => (
          <g key={m}>
            <text x={IZQ + CW * (c + 0.5)} y={14} textAnchor="middle" fontSize="10.5" fill="var(--color-text)">{`min ${m}`}</text>
            {incompletoEn(m) && (
              <text x={IZQ + CW * (c + 0.5)} y={27} textAnchor="middle" fontSize="9" fill="var(--muted-2)">incompleto</text>
            )}
          </g>
        ))}
        {grupos.map((g, i) => {
          const y0 = TOP + i * RH
          const mid = y0 + RH / 2
          const lineas = renglonesDeEtiqueta(g.etiqueta, maxCar)
          const yN = mid - 6 + lineas.length * 12
          return (
            <g key={g.idGrupo} data-grupo={g.etiqueta}>
              <title>{g.etiqueta}</title>
              {lineas.map((t, k) => (
                <text key={k} x={IZQ - 6} y={mid - 6 + k * 12} textAnchor="end" fontSize="11" fill="var(--color-text)">{t}</text>
              ))}
              <text x={IZQ - 6} y={yN} textAnchor="end" fontSize="10" fill="var(--muted-2)">{textoN(g)}</text>
              {g.nivelMixto && (
                <text x={IZQ + (minutos.length * CW) / 2} y={mid + 4} textAnchor="middle" fontSize="11" fill="var(--muted-2)" data-mixto={g.etiqueta}>
                  tandas no comparables
                </text>
              )}
              {!g.nivelMixto && minutos.map((m, c) => {
                const x0 = IZQ + c * CW + 1
                const w = CW - 2
                const ty = y0 + 3
                const h = RH - 6
                const cel = celdaDe(g, m, clave)
                if (!cel) {
                  return (
                    <g key={m} data-celda={`${g.etiqueta}|${m}`} data-estado="sindatos">
                      <rect x={x0} y={ty} width={w} height={h} fill="var(--color-bg)" />
                      <rect x={x0} y={ty} width={w} height={h} fill={`url(#${prefijo}-sin)`} />
                    </g>
                  )
                }
                const r = RANGOS[cel.k]
                const inc = cel.e.incompleto
                return (
                  <g key={m} data-celda={`${g.etiqueta}|${m}`} data-rango={cel.k} data-incompleto={inc ? 'si' : undefined}>
                    <title>{`${g.etiqueta}, min ${m}: ${numero(cel.s.media)} s${inc ? `, incompleto (${rangoSegundos(cel.e)} s de ${SEGUNDOS_MINUTO})` : ''}`}</title>
                    <rect x={x0} y={ty} width={w} height={h} fill={r.fondo} fillOpacity={r.opacidad} />
                    <rect x={x0} y={ty} width={w} height={h} fill={`url(#${prefijo}-p${cel.k})`} />
                    {inc && <rect x={x0} y={ty} width={w} height={h} fill="none" stroke="var(--color-text)" strokeWidth="1.4" strokeDasharray="4 3" />}
                    <rect x={x0 + w / 2 - anchoTexto(numero(cel.s.media), 7.2) / 2} y={ty + h / 2 + (inc ? -1 : 4) - 11}
                      width={anchoTexto(numero(cel.s.media), 7.2)} height="15" rx="2" fill="var(--color-bg)" />
                    <text x={x0 + w / 2} y={ty + h / 2 + (inc ? -1 : 4)} textAnchor="middle" fontSize="11.5"
                      fontStyle={inc ? 'italic' : undefined} fill="var(--color-text)" fontWeight="700">
                      {numero(cel.s.media)}
                    </text>
                    {inc && (
                      <>
                        <rect x={x0 + w / 2 - anchoTexto('inc.', 4.6) / 2} y={ty + h - 11} width={anchoTexto('inc.', 4.6)} height="11" rx="2" fill="var(--color-bg)" />
                        <text x={x0 + w / 2} y={ty + h - 3} textAnchor="middle" fontSize="8.5" fill="var(--color-text)">inc.</text>
                      </>
                    )}
                  </g>
                )
              })}
            </g>
          )
        })}
      </svg>
    </PanelGrafica>
  )
}
