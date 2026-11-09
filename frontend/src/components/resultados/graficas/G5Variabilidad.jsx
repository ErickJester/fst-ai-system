import React, { useRef } from 'react'
import PanelGrafica from './PanelGrafica'
import Patrones, { patronId } from './Patrones'
import MuestraLeyenda from './MuestraLeyenda'
import { CONDUCTAS_GRAFICA, NOTA_MIXTO, estilosDeGrupos, numero, porcentaje, textoAnimales, textoN } from './estilos'

// G5 · Variabilidad entre animales de un grupo: el coeficiente de variación (DE ÷ media) por conducta y grupo, con
// la varianza debajo de cada barra. Mide la variación entre animales, no entre analistas. No recalcula nada: usa cv y varianza.
const W = 560
const H = 300
const M = { l: 46, r: 8, t: 14, b: 66 }
const PH = H - M.t - M.b
const PW = W - M.l - M.r
const PASO_CV = 0.1 // la escala del CV crece en múltiplos de 0,1

const dibujable = (g) => !g.nivelMixto && g.porConducta && g.n > 0

// La escala llega al múltiplo de 0,1 igual o inmediatamente mayor que el mayor CV que se dibuja.
function escalaCv(valores) {
  const mayor = Math.max(0, ...valores)
  return Math.max(1, Math.ceil(mayor * 10 - 1e-9)) * PASO_CV
}

const textoCv = (cv) => (cv == null ? 'no aplica' : `CV ${porcentaje(cv)}`)
const textoVarianza = (varianza) => (varianza == null ? 'no aplica' : `${numero(varianza)} s²`)

export default function G5Variabilidad({ datos, prefijo }) {
  const svgRef = useRef(null)
  const grupos = datos.grupos
  const estilos = estilosDeGrupos(grupos)
  const conductas = CONDUCTAS_GRAFICA
  const slotC = PW / conductas.length
  const colW = slotC / Math.max(1, grupos.length)
  const bw = Math.min(22, colW * 0.7)
  // Con poco ancho, el texto sobre y bajo las barras se acorta o se quita; el dato queda en <title> y en la tabla.
  const mostrarVarianza = colW >= 46

  // Etiqueta sobre la barra: «CV 47 %», «47 %» o nada, según el ancho de la columna.
  function etiquetaBarra(cv) {
    if (colW >= 40) return textoCv(cv)
    if (colW >= 24) return porcentaje(cv)
    return null
  }

  // Texto de un grupo sin barra: «no aplica», «sin datos» o «tandas no comparables», con versiones cortas si la columna es angosta.
  function textoVacio(g, sinDatos) {
    if (colW >= 40) return sinDatos ? (g.nivelMixto ? 'tandas no comparables' : 'sin datos') : 'no aplica'
    if (colW >= 24) return sinDatos ? (g.nivelMixto ? 'no comp.' : '—') : 'n/a'
    return null
  }

  const cvs = grupos
    .filter(dibujable)
    .flatMap((g) => conductas.map((c) => g.porConducta[c.clave]?.cv).filter((v) => v != null))
  const escala = escalaCv(cvs)
  const y = (v) => M.t + PH - (Math.min(Math.max(v, 0), escala) / escala) * PH
  const marcas = Array.from({ length: 6 }, (_, k) => (escala / 5) * k)

  const avisos = []
  const notas = ['La variación es el coeficiente de variación (desviación estándar ÷ promedio). Mide la variación entre animales de cada grupo, no entre analistas.']
  for (const g of grupos) {
    if (g.nivelMixto) avisos.push(`${g.etiqueta}: ${NOTA_MIXTO}`)
    else if (g.n === 0) avisos.push(`${g.etiqueta}: sin datos.`)
    if (g.faltantes.length) {
      const faltan = g.faltantes.reduce((a, f) => a + f.nCilindros, 0)
      avisos.push(`${g.etiqueta}: ${faltan} ${faltan === 1 ? 'animal pendiente' : 'animales pendientes'} (${g.faltantes.map((f) => `Tanda ${f.letra} ${f.estado ?? 'sin subir'}`).join(', ')}).`)
    }
  }

  if (!mostrarVarianza && grupos.length) notas.push('La varianza de cada grupo está en la tabla.')

  const titulo = '¿Qué tan parecidos son los animales?'
  const etiquetaSvg = 'Barras del coeficiente de variación entre animales de cada grupo, por conducta. La varianza va debajo de cada barra.'
  const sinCvDibujable = cvs.length === 0

  return (
    <PanelGrafica
      titulo={titulo}
      subtitulo="Variación de cada grupo entre sus animales: desviación estándar dividida entre el promedio."
      nombre="g5_variabilidad"
      svgRef={svgRef}
      leyenda={grupos.map((g) => (
        <span key={g.idGrupo} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontWeight: g.tipo === 'control' ? 700 : 400 }}>
          <MuestraLeyenda color={estilos.get(g.idGrupo).color} patron={estilos.get(g.idGrupo).patron} />{g.etiqueta}
        </span>
      ))}
      avisos={avisos} notas={notas}
      tabla={(
        <table className="table" style={{ fontSize: 12 }}>
          <caption className="hint" style={{ textAlign: 'left', padding: '0 0 6px' }}>
            CV y varianza entre animales de cada grupo, Día 2. Variación entre animales, no entre analistas.
          </caption>
          <thead>
            <tr>
              <th>Grupo</th><th>n</th>
              {conductas.map((c) => <th key={c.clave} style={{ textAlign: 'right' }}>{c.etiqueta}</th>)}
            </tr>
          </thead>
          <tbody>
            {grupos.map((g) => (
              <React.Fragment key={g.idGrupo}>
                <tr>
                  <td>{g.etiqueta}</td>
                  <td className="num">{textoN(g)}</td>
                  {conductas.map((c) => (
                    <td key={c.clave} className="num" style={{ textAlign: 'right' }}>
                      {g.nivelMixto ? 'por tanda' : g.porConducta ? textoCelda(g.porConducta[c.clave]) : 'sin datos'}
                    </td>
                  ))}
                </tr>
                {g.nivelMixto && (g.porNivel || []).map((r) => (
                  <tr key={`${g.idGrupo}-${r.letra}`}>
                    <td style={{ paddingLeft: 18 }}>Tanda {r.letra} · {r.nivelClasif}</td>
                    <td className="num">{textoAnimales(r.n)}</td>
                    {conductas.map((c) => (
                      <td key={c.clave} className="num" style={{ textAlign: 'right' }}>{textoCelda(r.porConducta[c.clave])}</td>
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
        {!sinCvDibujable && marcas.map((v) => (
          <g key={v}>
            <line x1={M.l} x2={W - M.r} y1={y(v)} y2={y(v)} stroke="var(--color-divider)" strokeWidth={v === 0 ? 1.4 : 0.8} />
            <text x={M.l - 6} y={y(v) + 4} textAnchor="end" fontSize="10.5" fill="var(--muted-2)">{porcentaje(v)}</text>
          </g>
        ))}
        <text x={M.l} y={M.t - 4} fontSize="10.5" fill="var(--muted-2)">CV</text>
        {sinCvDibujable && (
          <text x={W / 2} y={M.t + PH / 2} textAnchor="middle" fontSize="12" fill="var(--color-text)">
            No hay coeficientes de variación que dibujar: no aplica.
          </text>
        )}
        {conductas.map((c, ci) => {
          const cxC = M.l + slotC * (ci + 0.5)
          return (
            <g key={c.clave}>
              {grupos.map((g, j) => {
                const cx = M.l + slotC * ci + colW * (j + 0.5)
                const estilo = estilos.get(g.idGrupo)
                const celda = dibujable(g) ? g.porConducta[c.clave] : null
                const cv = celda?.cv ?? null
                const varianza = celda?.varianza ?? null
                const sinDatos = !dibujable(g)
                return (
                  <g key={g.idGrupo}>
                    <title>{`${g.etiqueta}, ${c.etiqueta}: ${textoCv(cv)}; varianza ${textoVarianza(varianza)}`}</title>
                    {cv != null && (
                      <g>
                        <rect x={cx - bw / 2} y={y(cv)} width={bw} height={y(0) - y(cv)} fill={estilo.color} />
                        {estilo.patron && <rect x={cx - bw / 2} y={y(cv)} width={bw} height={y(0) - y(cv)} fill={`url(#${patronId(prefijo, estilo.patron)})`} />}
                        {etiquetaBarra(cv) && (
                          <text x={cx} y={y(cv) - 5} textAnchor="middle" fontSize="9.5" fill="var(--color-text)">{etiquetaBarra(cv)}</text>
                        )}
                      </g>
                    )}
                    {cv == null && textoVacio(g, sinDatos) && (
                      <text x={cx} y={y(0) - 6} textAnchor="middle" fontSize="8.5" fill="var(--muted-2)">{textoVacio(g, sinDatos)}</text>
                    )}
                    {mostrarVarianza && (
                      <>
                        <text x={cx} y={M.t + PH + 28} textAnchor="middle" fontSize="8.5" fill="var(--color-text)">
                          {varianza == null ? 'varianza:' : 'varianza'}
                        </text>
                        <text x={cx} y={M.t + PH + 40} textAnchor="middle" fontSize="8.5" fill="var(--color-text)">
                          {varianza == null ? 'no aplica' : textoVarianza(varianza)}
                        </text>
                      </>
                    )}
                  </g>
                )
              })}
              <text x={cxC} y={M.t + PH + 14} textAnchor="middle" fontSize="11" fill="var(--color-text)">{c.etiqueta}</text>
            </g>
          )
        })}
        <line x1={M.l} x2={W - M.r} y1={y(0)} y2={y(0)} stroke="var(--color-text)" strokeWidth="1.4" />
      </svg>
    </PanelGrafica>
  )
}

// Una celda de la tabla: «CV 47 % · varianza 50,0 s²»; lo que no existe dice «no aplica».
function textoCelda(estadisticos) {
  return `${textoCv(estadisticos?.cv ?? null)} · varianza ${textoVarianza(estadisticos?.varianza ?? null)}`
}
