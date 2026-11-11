import React, { useRef, useState } from 'react'
import PanelGrafica from './PanelGrafica'
import Patrones, { patronId } from './Patrones'
import MuestraLeyenda from './MuestraLeyenda'
import Marca from './Marca'
import { CONDUCTAS_BARRAS, CONDUCTAS_GRAFICA, ESCALA_SEGUNDOS, NOTA_MIXTO, estilosDeGrupos, numero, textoMediaDe, textoN, textoAnimales } from './estilos'
import { UNIDADES, fraseDeComparacion, marcasDelEje, textoMediaDeEnUnidad, tiempoEnPalabras, valorEnUnidad } from './explicacion'

// G1 · ¿Cuánto hizo cada grupo? La media de una conducta por grupo, con su ± desviación estándar y un punto por espécimen.
// Por defecto se dibuja como punto con línea (el punto grande es el promedio; la línea llega a ± una desviación estándar;
// los puntos pequeños son los especímenes); «Barras» es la forma de antes. Con «Las tres juntas», tres marcas por grupo, una por
// conducta. La conducta activa no se dibuja: se dice aparte, con sus segundos.
// Un grupo con niveles mezclados no lleva marca: su tabla trae una fila por tanda (porNivel).
const W = 420
const H = 300
const M = { l: 46, r: 8, t: 14, b: 66 }
const PH = H - M.t - M.b
const PW = W - M.l - M.r
const TRES = 'tres'
const CAR = 6.3          // ancho medio de un carácter de la etiqueta, en píxeles del viewBox (font-size 11.5)
const ESCALA_PASO = 30   // la escala crece en múltiplos de 30 s

export const ESTILOS_G1 = [
  { clave: 'puntos', etiqueta: 'Puntos con línea' },
  { clave: 'barras', etiqueta: 'Barras' },
]

// Con «Las tres juntas» la conducta se distingue por su color y por su forma, no solo por el color.
const FORMA_DE_CONDUCTA = { 'nado activo': 'circulo', inmovilidad: 'rombo', escalamiento: 'triangulo' }

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

// Lo que se dice al pasar el cursor por el punto grande (el promedio) o por uno pequeño (un espécimen).
// No se dice «espécimen 3»: el orden de los valores no corresponde a ningún número de espécimen.
const conPorcentaje = (segundos, unidad, base) => (unidad === 'pct'
  ? valorEnUnidad(segundos, 'pct', base)
  : `${valorEnUnidad(segundos, unidad, base)} (${valorEnUnidad(segundos, 'pct', base)} del tiempo)`)

function lecturaDePromedio(grupo, conducta, m, unidad, base) {
  const rango = m.de == null ? '' : `; la línea va de ${tiempoEnPalabras(Math.max(0, m.media - m.de))} a ${tiempoEnPalabras(m.media + m.de)}`
  const promedio = unidad === 'pct'
    ? textoMediaDeEnUnidad(m.media, m.de, unidad, base)
    : `${textoMediaDeEnUnidad(m.media, m.de, unidad, base)} (${valorEnUnidad(m.media, 'pct', base)} del tiempo)`
  return `Promedio de ${grupo}, ${conducta}: ${promedio}${rango}.`
}

const lecturaDeEspecimen = (grupo, conducta, v, unidad, base) => `Un espécimen de ${grupo}, ${conducta}: ${conPorcentaje(v, unidad, base)}.`

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

// Un punto grande (el promedio), una línea hasta ± una desviación estándar y, detrás, un punto pequeño por espécimen.
function PuntoConLinea({ cx, ancho, y, m, forma, color, puntos, grupo, conducta, unidad, base, ver, quitar }) {
  const cap = Math.max(5, ancho * 0.28)
  const lo = y(Math.min(y.escala, m.media + (m.de ?? 0)))
  const hi = y(Math.max(0, m.media - (m.de ?? 0)))
  const separacion = Math.max(2.4, ancho * 0.1)
  return (
    <g>
      {puntos && m.valoresS.map((v, i) => (
        <circle key={i} cx={cx + (((i * 7) % 5) - 2) * separacion} cy={y(v)} r="2.7" fill="var(--color-text)" fillOpacity="0.38" stroke="var(--color-bg)" strokeWidth="0.6"
          style={{ cursor: 'pointer' }} onMouseEnter={() => ver(lecturaDeEspecimen(grupo, conducta, v, unidad, base))} onMouseLeave={quitar}>
          <title>{lecturaDeEspecimen(grupo, conducta, v, unidad, base)}</title>
        </circle>
      ))}
      {m.de != null && (
        <g stroke="var(--color-text)" strokeWidth="1.6" fill="none">
          <line x1={cx} y1={lo} x2={cx} y2={hi} />
          <line x1={cx - cap} y1={lo} x2={cx + cap} y2={lo} />
          <line x1={cx - cap} y1={hi} x2={cx + cap} y2={hi} />
        </g>
      )}
      <g style={{ cursor: 'pointer' }} onMouseEnter={() => ver(lecturaDePromedio(grupo, conducta, m, unidad, base))} onMouseLeave={quitar}>
        <Marca forma={forma} cx={cx} cy={y(m.media)} r={6.5} fill={color} stroke="var(--color-text)" sw={1.4} />
        <title>{lecturaDePromedio(grupo, conducta, m, unidad, base)}</title>
      </g>
    </g>
  )
}

// La muestra de la leyenda de un grupo o de una conducta en la forma de punto.
function MuestraMarca({ forma, color }) {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true" focusable="false" style={{ flex: 'none' }}>
      <Marca forma={forma} cx={7} cy={7} r={4.6} fill={color} stroke="var(--color-text)" sw={1.2} />
    </svg>
  )
}

export default function G1BarrasGrupos({ datos, conducta, setConducta, puntos, setPuntos, estilo = 'puntos', setEstilo, unidad = 's', setUnidad, prefijo }) {
  const svgRef = useRef(null)
  const [lectura, setLectura] = useState(null)
  const grupos = datos.grupos
  const estilos = estilosDeGrupos(grupos)
  const tres = conducta === TRES
  const dePuntos = estilo === 'puntos'
  const mostradas = tres ? CONDUCTAS_BARRAS : CONDUCTAS_BARRAS.filter((c) => c.clave === conducta)
  const slot = grupos.length ? PW / grupos.length : PW
  const bw = tres ? Math.min(26, slot * 0.22) : Math.min(44, slot * 0.5)
  const offsets = tres ? [-1, 0, 1].map((k) => k * bw * 1.15) : [0]
  const activa = CONDUCTAS_GRAFICA.find((c) => c.clave === 'conducta activa')
  const dibujable = (g) => !g.nivelMixto && g.porConducta && g.n > 0
  const maxCar = Math.max(4, Math.floor((slot - 6) / CAR))
  const baseDe = (g) => g.duracionMediaS || ESCALA_SEGUNDOS

  // La escala es de 300 s (la ventana del Día 2) y solo crece si algún grupo la rebasa con su media + DE o con un espécimen.
  const alturas = grupos.filter(dibujable).flatMap((g) => [
    ...mostradas.map((c) => (g.porConducta[c.clave].media ?? 0) + (g.porConducta[c.clave].de ?? 0)),
    ...mostradas.flatMap((c) => g.porConducta[c.clave].valoresS),
  ])
  const escala = Math.max(ESCALA_SEGUNDOS, techo(Math.max(0, ...alturas)))
  const y = (v) => M.t + PH - (Math.min(Math.max(v, 0), escala) / escala) * PH
  y.escala = escala
  const marcas = marcasDelEje(escala, unidad)

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
  const conActiva = grupos.filter((g) => dibujable(g) && g.porConducta[activa.clave].media > 0)
  if (conActiva.length) {
    notas.push(`Conducta activa, fuera de la gráfica (segundos medios por espécimen): ${conActiva.map((g) => `${g.etiqueta} ${numero(g.porConducta[activa.clave].media)} s`).join(' · ')}.`)
  }

  const frase = tres ? [] : fraseDeComparacion(grupos, conducta)
  const titulo = '¿Cuánto hizo cada grupo?'
  const etiquetaSvg = dePuntos
    ? 'Puntos con línea: el promedio de cada grupo con su ± desviación estándar y un punto por espécimen, del Día 2.'
    : 'Barras agrupadas: media ± desviación estándar del tiempo por conducta en cada grupo, en segundos del Día 2.'
  const selector = { width: 190, padding: '4px 6px', fontSize: 12 }

  return (
    <PanelGrafica
      titulo={titulo}
      subtitulo={dePuntos
        ? 'Cada punto pequeño es un espécimen. El punto grande es el promedio del grupo y su línea llega a ± una desviación estándar. El eje llega a 300 s, la ventana del Día 2.'
        : 'Promedio ± desviación estándar de los especímenes, en segundos analizados. El eje llega a 300 s, la ventana del Día 2.'}
      nombre={`g1_${tres ? 'tres_conductas' : conducta.replace(' ', '_')}`}
      svgRef={svgRef}
      controles={(
        <>
          <label htmlFor={`${prefijo}-conducta`} style={{ fontSize: 12.5 }}>Conducta </label>
          <select id={`${prefijo}-conducta`} className="input" style={selector} value={conducta}
            onChange={(e) => setConducta(e.target.value)}>
            {CONDUCTAS_BARRAS.map((c) => <option key={c.clave} value={c.clave}>{c.etiqueta}</option>)}
            <option value={TRES}>Las tres juntas</option>
          </select>
          {setEstilo && (
            <>
              <label htmlFor={`${prefijo}-estilo`} style={{ fontSize: 12.5 }}>Ver como </label>
              <select id={`${prefijo}-estilo`} className="input" style={selector} value={estilo} onChange={(e) => setEstilo(e.target.value)}>
                {ESTILOS_G1.map((e) => <option key={e.clave} value={e.clave}>{e.etiqueta}</option>)}
              </select>
            </>
          )}
          {setUnidad && (
            <>
              <label htmlFor={`${prefijo}-unidad`} style={{ fontSize: 12.5 }}>Mostrar en </label>
              <select id={`${prefijo}-unidad`} className="input" style={selector} value={unidad} onChange={(e) => setUnidad(e.target.value)}>
                {UNIDADES.map((u) => <option key={u.clave} value={u.clave}>{u.etiqueta}</option>)}
              </select>
            </>
          )}
          <label style={{ fontSize: 12.5, display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <input type="checkbox" checked={puntos} onChange={(e) => setPuntos(e.target.checked)} /> Mostrar especímenes
          </label>
        </>
      )}
      leyenda={tres
        ? CONDUCTAS_BARRAS.map((c) => (
          <span key={c.clave} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            {dePuntos ? <MuestraMarca forma={FORMA_DE_CONDUCTA[c.clave]} color={c.color} /> : <MuestraLeyenda color={c.color} patron={c.patron} />}{c.etiqueta}
          </span>
        ))
        : grupos.map((g) => (
          <span key={g.idGrupo} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            {dePuntos ? <MuestraMarca forma={estilos.get(g.idGrupo).forma} color={estilos.get(g.idGrupo).color} /> : <MuestraLeyenda color={estilos.get(g.idGrupo).color} patron={estilos.get(g.idGrupo).patron} />}{g.etiqueta}
          </span>
        ))}
      avisos={avisos} notas={notas}
      tabla={(
        <table className="table" style={{ fontSize: 12 }}>
          <caption className="hint" style={{ textAlign: 'left', padding: '0 0 6px' }}>Promedio ± desviación estándar por grupo, Día 2, en segundos.</caption>
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
                      {g.nivelMixto ? 'por tanda' : g.porConducta ? textoMediaDe(g.porConducta[c.clave].media, g.porConducta[c.clave].de) : 'sin datos'}
                    </td>
                  ))}
                </tr>
                {g.nivelMixto && (g.porNivel || []).map((r) => (
                  <tr key={`${g.idGrupo}-${r.letra}`}>
                    <td style={{ paddingLeft: 18 }}>Tanda {r.letra} · {r.nivelClasif}</td>
                    <td className="num">{textoAnimales(r.n)}</td>
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
      {frase.length > 0 && (
        <div role="note" style={{ margin: '0 0 12px', padding: '10px 14px', borderLeft: '3px solid var(--color-accent)', background: 'var(--color-surface)', fontSize: 13.5, lineHeight: 1.6 }}>
          <strong style={{ display: 'block', fontSize: 12, marginBottom: 3 }}>En palabras</strong>
          {frase.map((texto) => <span key={texto} style={{ display: 'block' }}>{texto}</span>)}
        </div>
      )}
      <svg ref={svgRef} viewBox={`0 0 ${W} ${H}`} role="img" aria-labelledby={`${prefijo}-t ${prefijo}-d`}
        style={{ display: 'block', width: '100%', maxWidth: 560, height: 'auto', fontFamily: 'var(--font-body)' }}>
        <title id={`${prefijo}-t`}>{titulo}</title>
        <desc id={`${prefijo}-d`}>{etiquetaSvg}</desc>
        <Patrones prefijo={prefijo} />
        {marcas.map(({ segundos, texto }) => (
          <g key={segundos}>
            <line x1={M.l} x2={W - M.r} y1={y(segundos)} y2={y(segundos)} stroke="var(--color-divider)" strokeWidth={segundos === 0 ? 1.4 : 0.8} />
            <text x={M.l - 6} y={y(segundos) + 4} textAnchor="end" fontSize="10.5" fill="var(--muted-2)">{texto}</text>
          </g>
        ))}
        {unidad === 's' && <text x={M.l} y={M.t - 4} fontSize="10.5" fill="var(--muted-2)">s</text>}
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
                if (dePuntos) {
                  const forma = tres ? FORMA_DE_CONDUCTA[c.clave] : estilos.get(g.idGrupo).forma
                  return m.media == null ? null : (
                    <PuntoConLinea key={c.clave} cx={cx + offsets[j]} ancho={bw} y={y} m={m} forma={forma} color={color} puntos={puntos}
                      grupo={g.etiqueta} conducta={c.etiqueta.toLowerCase()} unidad={unidad} base={baseDe(g)}
                      ver={setLectura} quitar={() => setLectura(null)} />
                  )
                }
                const patron = tres ? c.patron : estilos.get(g.idGrupo).patron
                return (
                  <Barra key={c.clave} x={cx + offsets[j] - bw / 2} bw={bw} valor={m.media} de={m.de} color={color} patron={patron}
                    prefijo={prefijo} valoresS={m.valoresS} puntos={puntos} etiqueta={`${g.etiqueta}, ${c.etiqueta}`} y={y} />
                )
              })}
              {dibujable(g) && !tres && (() => {
                const m = g.porConducta[mostradas[0].clave]
                return m.media == null ? null : (
                  <text x={cx} y={y(Math.min(escala, m.media + (m.de ?? 0))) - (dePuntos ? 10 : 6)} textAnchor="middle" fontSize="10.5" fill="var(--color-text)" stroke="var(--color-bg)" strokeWidth="3" paintOrder="stroke">
                    {textoMediaDeEnUnidad(m.media, m.de, unidad, baseDe(g))}
                  </text>
                )
              })()}
              {!dibujable(g) && (
                <text x={cx} y={y(0) - 8} textAnchor="middle" fontSize="10.5" fill="var(--muted-2)">{g.nivelMixto ? 'tandas no comparables' : 'sin datos'}</text>
              )}
              {lineas.map((linea, k) => (
                <text key={k} x={cx} y={yNombre + k * 13} textAnchor="middle" fontSize="11.5" fill="var(--color-text)" fontWeight={g.tipo === 'control' ? 700 : undefined}>{linea}</text>
              ))}
              <text x={cx} y={yN} textAnchor="middle" fontSize="10.5" fill="var(--muted-2)">{textoN(g)}</text>
            </g>
          )
        })}
        <line x1={M.l} x2={W - M.r} y1={y(0)} y2={y(0)} stroke="var(--color-text)" strokeWidth="1.4" />
      </svg>
      {dePuntos && (
        <p className="no-print" role="status" style={{ margin: '8px 0 0', minHeight: '2.6em', fontSize: 12.5, lineHeight: 1.5, color: lectura ? 'var(--color-text)' : 'var(--muted-2)' }}>
          {lectura || 'Pasa el cursor sobre un punto para ver su valor.'}
        </p>
      )}
    </PanelGrafica>
  )
}
