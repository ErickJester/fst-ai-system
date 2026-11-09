import React, { useRef } from 'react'
import PanelGrafica from './PanelGrafica'
import Patrones, { patronId } from './Patrones'
import MuestraLeyenda from './MuestraLeyenda'
import { CONDUCTAS_BARRAS, CONDUCTAS_GRAFICA, ESCALA_SEGUNDOS, NOTA_MIXTO, estilosDeGrupos, numero, textoMediaDe, textoN } from './estilos'

// G1 · Barras agrupadas: la media de una conducta por grupo, con su ± DE y un punto por espécimen. Con «Las tres juntas»,
// tres barras por grupo, una por conducta. La conducta activa no va en las barras: se dice aparte, con sus segundos.
// Un grupo con niveles mezclados no lleva barra: su tabla trae una fila por tanda (porNivel).
const W = 420
const H = 300
const M = { l: 46, r: 8, t: 14, b: 66 }
const PH = H - M.t - M.b
const PW = W - M.l - M.r
const TRES = 'tres'
const CAR = 6.3          // ancho medio de un carácter de la etiqueta, en píxeles del viewBox (font-size 11.5)
const ESCALA_PASO = 30   // la escala crece en múltiplos de 30 s

const techo = (valor) => Math.ceil(valor / ESCALA_PASO) * ESCALA_PASO

// Parte un nombre en hasta dos renglones de maxCar caracteres; lo que no cabe se recorta con «…».
export function renglonesDeEtiqueta(texto, maxCar) {
  if (texto.length <= maxCar) return [texto]
  const lineas = []
  let actual = ''
  for (const palabra of texto.split(' ')) {
    const candidata = actual ? `${actual} ${palabra}` : palabra
    if (candidata.length <= maxCar) {
      actual = candidata
    } else {
      if (actual) lineas.push(actual)
      actual = palabra
    }
  }
  if (actual) lineas.push(actual)
  const dos = lineas.slice(0, 2).map((l) => (l.length > maxCar ? `${l.slice(0, maxCar - 1)}…` : l))
  if (lineas.length > 2) dos[1] = `${dos[1].slice(0, maxCar - 1)}…`
  return dos
}

// Las barras de un grupo con valor; lo que no existe no se dibuja.
function Barra({ x, bw, valor, de, color, patron, prefijo, valoresS, puntos, etiqueta, y }) {
  const top = y(valor)
  const base = y(0)
  const cx = x + bw / 2
  const lo = y(Math.min(y.escala, valor + (de ?? 0)))
  const hi = y(Math.max(0, valor - (de ?? 0)))
  const capW = bw * 0.3
  return (
    <g>
      <rect x={x} y={top} width={bw} height={base - top} fill={color} />
      {patron && <rect x={x} y={top} width={bw} height={base - top} fill={`url(#${patronId(prefijo, patron)})`} />}
      {de != null && (
        <g stroke="var(--color-text)" strokeWidth="1.4" fill="none">
          <line x1={cx} y1={lo} x2={cx} y2={hi} />
          <line x1={cx - capW} y1={lo} x2={cx + capW} y2={lo} />
          <line x1={cx - capW} y1={hi} x2={cx + capW} y2={hi} />
        </g>
      )}
      {puntos && valoresS.map((v, i) => (
        <circle key={i} cx={cx + (((i * 7) % 5) - 2) * (bw * 0.12)} cy={y(v)} r="2.6" fill="var(--color-text)" stroke="var(--color-bg)" strokeWidth="0.8">
          <title>{`${etiqueta}, espécimen ${i + 1}: ${numero(v)} s`}</title>
        </circle>
      ))}
    </g>
  )
}

export default function G1BarrasGrupos({ datos, conducta, setConducta, puntos, setPuntos, prefijo }) {
  const svgRef = useRef(null)
  const grupos = datos.grupos
  const estilos = estilosDeGrupos(grupos)
  const tres = conducta === TRES
  const mostradas = tres ? CONDUCTAS_BARRAS : CONDUCTAS_BARRAS.filter((c) => c.clave === conducta)
  const slot = grupos.length ? PW / grupos.length : PW
  const bw = tres ? Math.min(26, slot * 0.22) : Math.min(44, slot * 0.5)
  const offsets = tres ? [-1, 0, 1].map((k) => k * bw * 1.15) : [0]
  const activa = CONDUCTAS_GRAFICA.find((c) => c.clave === 'conducta activa')
  const dibujable = (g) => !g.nivelMixto && g.porConducta && g.n > 0
  const maxCar = Math.max(4, Math.floor((slot - 6) / CAR))

  // La escala es de 300 s (la ventana del Día 2) y solo crece si algún grupo la rebasa con su media + DE o con un espécimen.
  const alturas = grupos.filter(dibujable).flatMap((g) => [
    ...mostradas.map((c) => (g.porConducta[c.clave].media ?? 0) + (g.porConducta[c.clave].de ?? 0)),
    ...mostradas.flatMap((c) => g.porConducta[c.clave].valoresS),
  ])
  const escala = Math.max(ESCALA_SEGUNDOS, techo(Math.max(0, ...alturas)))
  const y = (v) => M.t + PH - (Math.min(Math.max(v, 0), escala) / escala) * PH
  y.escala = escala
  const marcas = Array.from({ length: 6 }, (_, k) => (escala / 5) * k)

  const notas = []
  for (const g of grupos) {
    if (g.nivelMixto) notas.push(`${g.etiqueta}: ${NOTA_MIXTO}`)
    else if (g.n === 0) notas.push(`${g.etiqueta}: sin datos.`)
    if (g.faltantes.length) {
      const faltan = g.faltantes.reduce((a, f) => a + f.nCilindros, 0)
      notas.push(`${g.etiqueta}: ${faltan} especímenes pendientes (${g.faltantes.map((f) => `Tanda ${f.letra} ${f.estado ?? 'sin subir'}`).join(', ')}).`)
    }
  }
  const conActiva = grupos.filter((g) => dibujable(g) && g.porConducta[activa.clave].media > 0)
  if (conActiva.length) {
    notas.push(`Conducta activa, fuera de las barras (segundos medios por espécimen): ${conActiva.map((g) => `${g.etiqueta} ${numero(g.porConducta[activa.clave].media)} s`).join(' · ')}.`)
  }

  const titulo = 'Tiempo por conducta · Día 2'
  const etiquetaSvg = 'Barras agrupadas: media ± DE del tiempo por conducta en cada grupo, en segundos del Día 2.'

  return (
    <PanelGrafica
      titulo={titulo}
      subtitulo="Media ± DE de los especímenes, en segundos analizados. El eje llega a 300 s, la ventana del Día 2."
      nombre={`g1_${tres ? 'tres_conductas' : conducta.replace(' ', '_')}`}
      svgRef={svgRef}
      controles={(
        <>
          <label htmlFor={`${prefijo}-conducta`} style={{ fontSize: 12.5 }}>Conducta </label>
          <select id={`${prefijo}-conducta`} className="input" style={{ width: 190, padding: '4px 6px', fontSize: 12 }} value={conducta}
            onChange={(e) => setConducta(e.target.value)}>
            {CONDUCTAS_BARRAS.map((c) => <option key={c.clave} value={c.clave}>{c.etiqueta}</option>)}
            <option value={TRES}>Las tres juntas</option>
          </select>
          <label style={{ fontSize: 12.5, display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <input type="checkbox" checked={puntos} onChange={(e) => setPuntos(e.target.checked)} /> Mostrar especímenes
          </label>
        </>
      )}
      leyenda={tres
        ? CONDUCTAS_BARRAS.map((c) => (
          <span key={c.clave} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <MuestraLeyenda color={c.color} patron={c.patron} />{c.etiqueta}
          </span>
        ))
        : grupos.map((g) => (
          <span key={g.idGrupo} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <MuestraLeyenda color={estilos.get(g.idGrupo).color} patron={estilos.get(g.idGrupo).patron} />{g.etiqueta}
          </span>
        ))}
      notas={notas}
      tabla={(
        <table className="table" style={{ fontSize: 12 }}>
          <caption className="hint" style={{ textAlign: 'left', padding: '0 0 6px' }}>Media ± DE por grupo, Día 2, en segundos.</caption>
          <thead>
            <tr>
              <th>Grupo</th><th>n</th>
              {CONDUCTAS_GRAFICA.map((c) => <th key={c.clave} style={{ textAlign: 'right' }}>{c.etiqueta}</th>)}
            </tr>
          </thead>
          <tbody>
            {grupos.map((g) => (
              <React.Fragment key={g.idGrupo}>
                <tr>
                  <td>{g.etiqueta}</td>
                  <td className="num">{textoN(g)}</td>
                  {CONDUCTAS_GRAFICA.map((c) => (
                    <td key={c.clave} className="num" style={{ textAlign: 'right' }}>
                      {g.nivelMixto ? 'por nivel' : g.porConducta ? textoMediaDe(g.porConducta[c.clave].media, g.porConducta[c.clave].de) : 'sin datos'}
                    </td>
                  ))}
                </tr>
                {g.nivelMixto && (g.porNivel || []).map((r) => (
                  <tr key={`${g.idGrupo}-${r.letra}`}>
                    <td style={{ paddingLeft: 18 }}>Tanda {r.letra} · {r.nivelClasif}</td>
                    <td className="num">n = {r.n}</td>
                    {CONDUCTAS_GRAFICA.map((c) => (
                      <td key={c.clave} className="num" style={{ textAlign: 'right' }}>{textoMediaDe(r.porConducta[c.clave].media, r.porConducta[c.clave].de)}</td>
                    ))}
                  </tr>
                ))}
              </React.Fragment>
            ))}
          </tbody>
        </table>
      )}
    >
      <svg ref={svgRef} viewBox={`0 0 ${W} ${H}`} role="img" aria-labelledby={`${prefijo}-t ${prefijo}-d`}
        style={{ display: 'block', width: '100%', maxWidth: 560, height: 'auto', fontFamily: 'var(--font-body)' }}>
        <title id={`${prefijo}-t`}>{titulo}</title>
        <desc id={`${prefijo}-d`}>{etiquetaSvg}</desc>
        <Patrones prefijo={prefijo} />
        {marcas.map((v) => (
          <g key={v}>
            <line x1={M.l} x2={W - M.r} y1={y(v)} y2={y(v)} stroke="var(--color-divider)" strokeWidth={v === 0 ? 1.4 : 0.8} />
            <text x={M.l - 6} y={y(v) + 4} textAnchor="end" fontSize="10.5" fill="var(--muted-2)">{Math.round(v)}</text>
          </g>
        ))}
        <text x={M.l} y={M.t - 4} fontSize="10.5" fill="var(--muted-2)">s</text>
        {grupos.map((g, i) => {
          const cx = M.l + slot * (i + 0.5)
          const lineas = renglonesDeEtiqueta(g.etiqueta, maxCar)
          const yNombre = M.t + PH + 16
          const yN = yNombre + (lineas.length - 1) * 13 + 15
          return (
            <g key={g.idGrupo}>
              <title>{g.etiqueta}</title>
              {dibujable(g) && mostradas.map((c, j) => {
                const m = g.porConducta[c.clave]
                const color = tres ? c.color : estilos.get(g.idGrupo).color
                const patron = tres ? c.patron : estilos.get(g.idGrupo).patron
                return (
                  <Barra key={c.clave} x={cx + offsets[j] - bw / 2} bw={bw} valor={m.media} de={m.de} color={color} patron={patron}
                    prefijo={prefijo} valoresS={m.valoresS} puntos={puntos} etiqueta={`${g.etiqueta}, ${c.etiqueta}`} y={y} />
                )
              })}
              {dibujable(g) && !tres && (() => {
                const m = g.porConducta[mostradas[0].clave]
                return m.media == null ? null : (
                  <text x={cx} y={y(Math.min(escala, m.media + (m.de ?? 0))) - 6} textAnchor="middle" fontSize="10.5" fill="var(--color-text)" stroke="var(--color-bg)" strokeWidth="3" paintOrder="stroke">
                    {textoMediaDe(m.media, m.de)}
                  </text>
                )
              })()}
              {!dibujable(g) && (
                <text x={cx} y={y(0) - 8} textAnchor="middle" fontSize="10.5" fill="var(--muted-2)">{g.nivelMixto ? 'nivel mixto' : 'sin datos'}</text>
              )}
              {lineas.map((linea, k) => (
                <text key={k} x={cx} y={yNombre + k * 13} textAnchor="middle" fontSize="11.5" fill="var(--color-text)">{linea}</text>
              ))}
              <text x={cx} y={yN} textAnchor="middle" fontSize="10.5" fill="var(--muted-2)">{textoN(g)}</text>
            </g>
          )
        })}
        <line x1={M.l} x2={W - M.r} y1={y(0)} y2={y(0)} stroke="var(--color-text)" strokeWidth="1.4" />
      </svg>
    </PanelGrafica>
  )
}
