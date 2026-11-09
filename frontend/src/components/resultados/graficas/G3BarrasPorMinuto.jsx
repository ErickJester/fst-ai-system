import React, { useRef, useState } from 'react'
import PanelGrafica from './PanelGrafica'
import Patrones, { patronId } from './Patrones'
import MuestraLeyenda from './MuestraLeyenda'
import { CONDUCTAS_GRAFICA, NOTA_MIXTO, SEGUNDOS_MINUTO, estilosDeGrupos, textoMediaDe, textoN } from './estilos'

// G3 · Barras por minuto: en cada minuto, una barra por grupo, con su ± desviación estándar. Usa porMinuto tal cual lo manda el
// backend: no recalcula medias ni desviaciones. Un minuto sin especímenes (n = 0) no tiene barra; un minuto
// incompleto va con rayado y su rango de segundos.
const W = 420
const H = 300
const M = { l: 46, r: 12, t: 16, b: 62 }
const PH = H - M.t - M.b
const PW = W - M.l - M.r
const MARCAS_Y = [0, 10, 20, 30, 40, 50, 60]

const dibujable = (g) => !g.nivelMixto && !!g.porMinuto && g.n > 0

// El valor de un grupo en un minuto, si hay especímenes con segundos en ese minuto y su media existe.
function entradaDe(g, minuto, clave) {
  const e = g.porMinuto?.find((x) => x.minuto === minuto)
  if (!e || e.n === 0) return null
  const s = e.porConducta?.[clave]
  return s && s.media != null ? { e, s } : null
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

export default function G3BarrasPorMinuto({ datos, prefijo }) {
  const svgRef = useRef(null)
  const [clave, setClave] = useState('inmovilidad')
  const grupos = datos.grupos
  const minutos = datos.minutos
  const estilos = estilosDeGrupos(grupos)
  const conducta = CONDUCTAS_GRAFICA.find((c) => c.clave === clave)
  const slot = PW / minutos.length
  const bw = Math.min(24, (slot * 0.8) / Math.max(1, grupos.length))
  const xInicio = (i, j) => M.l + slot * i + (slot - bw * grupos.length) / 2 + j * bw
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

  const titulo = '¿Cómo se compara cada minuto?'

  return (
    <PanelGrafica
      titulo={titulo}
      subtitulo="Promedio por minuto de la conducta elegida, con barra ± desviación estándar por grupo."
      nombre={`g3_${clave.replace(/ /g, '_')}`}
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
          <span key={g.idGrupo} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontWeight: g.tipo === 'control' ? 700 : 400 }}>
            <MuestraLeyenda color={est.color} patron={est.patron} />{g.etiqueta}{estado}
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
        <desc id={`${prefijo}-d`}>Barras por minuto: media de la conducta elegida en cada grupo y minuto, con barra ± desviación estándar, en segundos del Día 2.</desc>
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
            <text x={M.l + slot * (i + 0.5)} y={M.t + PH + 16} textAnchor="middle" fontSize="10.5" fill="var(--color-text)">{`min ${m}`}</text>
            {incompletoEn(m) && (
              <text x={M.l + slot * (i + 0.5)} y={M.t + PH + 30} textAnchor="middle" fontSize="9.5" fill="var(--muted-2)">incompleto</text>
            )}
          </g>
        ))}
        {minutos.flatMap((m, i) => grupos.map((g, j) => {
          const p = dibujable(g) ? entradaDe(g, m, clave) : null
          if (!p) return null
          const est = estilos.get(g.idGrupo)
          const x0 = xInicio(i, j)
          const cx = x0 + bw / 2
          const { e, s } = p
          const alto = y(0) - y(s.media)
          const top = y(s.media)
          const etiquetaInc = `${rangoSegundos(e)} s de ${SEGUNDOS_MINUTO}`
          return (
            <g key={`${g.idGrupo}-${m}`} data-barra={`${g.etiqueta}|${m}`} data-incompleto={e.incompleto ? 'si' : undefined}>
              <title>{`${g.etiqueta}, min ${m}: ${textoMediaDe(s.media, s.de)}${e.incompleto ? `, incompleto (${etiquetaInc})` : ''}`}</title>
              <rect x={x0} y={top} width={bw} height={alto} fill={est.color} />
              {est.patron && <rect x={x0} y={top} width={bw} height={alto} fill={`url(#${patronId(prefijo, est.patron)})`} />}
              {e.incompleto && (
                <>
                  <rect x={x0} y={top} width={bw} height={alto} fill={`url(#${patronId(prefijo, 'horizontal')})`} />
                  <rect x={x0} y={top} width={bw} height={alto} fill="none" stroke={est.color} strokeWidth="1.2" strokeDasharray="3 2" />
                </>
              )}
              {s.de != null && (
                <g data-error={g.etiqueta} stroke="var(--color-text)" strokeWidth="1.2" fill="none">
                  <line x1={cx} y1={y(s.media + s.de)} x2={cx} y2={y(s.media - s.de)} />
                  <line x1={cx - bw * 0.3} y1={y(s.media + s.de)} x2={cx + bw * 0.3} y2={y(s.media + s.de)} />
                  <line x1={cx - bw * 0.3} y1={y(s.media - s.de)} x2={cx + bw * 0.3} y2={y(s.media - s.de)} />
                </g>
              )}
            </g>
          )
        }))}
        <line x1={M.l} x2={W - M.r} y1={y(0)} y2={y(0)} stroke="var(--color-text)" strokeWidth="1.4" />
      </svg>
    </PanelGrafica>
  )
}
