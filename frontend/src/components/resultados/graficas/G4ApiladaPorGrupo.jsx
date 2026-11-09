import React, { useRef } from 'react'
import PanelGrafica from './PanelGrafica'
import Patrones, { patronId } from './Patrones'
import MuestraLeyenda from './MuestraLeyenda'
import { renglonesDeEtiqueta } from './G1BarrasGrupos'
import { CONDUCTAS_GRAFICA, NOTA_MIXTO, SIN_VER, numero, textoN, textoAnimales } from './estilos'

// G4 · Barra apilada por grupo: la media de cada categoría por animal, desde abajo, con las cinco categorías.
// La suma de los segmentos es la duración analizada (duracionMediaS). La escala no es fija: sigue a la duración real.
const W = 420
const H = 300
const M = { l: 46, r: 8, t: 14, b: 66 }
const PH = H - M.t - M.b
const PW = W - M.l - M.r
const ESCALA_PASO = 30   // la escala crece en múltiplos de 30 s
const MINIMO_ETIQUETA = 0.08 // un segmento lleva número si mide al menos el 8 % de la escala

const CATEGORIAS = [...CONDUCTAS_GRAFICA, SIN_VER]

const techo = (valor) => Math.ceil(valor / ESCALA_PASO) * ESCALA_PASO

// Un grupo se dibuja solo si tiene datos y sus tandas son comparables.
const dibujable = (g) => !g.nivelMixto && g.porConducta && g.n > 0

// Un texto corto en el color que se lee sobre el relleno del segmento.
const colorDeTexto = (clave) => (clave === 'nado activo' || clave === 'inmovilidad' ? 'var(--color-bg)' : 'var(--color-text)')

export default function G4ApiladaPorGrupo({ datos, prefijo }) {
  const svgRef = useRef(null)
  const grupos = datos.grupos
  const categorias = datos.conductas.map((clave) => CATEGORIAS.find((c) => c.clave === clave)).filter(Boolean)
  const slot = grupos.length ? PW / grupos.length : PW
  const bw = Math.min(44, slot * 0.5)
  const maxCar = Math.max(4, Math.floor((slot - 6) / 6.3))

  const duraciones = grupos.filter(dibujable).map((g) => g.duracionMediaS ?? 0)
  const escala = Math.max(ESCALA_PASO, techo(Math.max(0, ...duraciones)))
  const y = (v) => M.t + PH - (Math.min(Math.max(v, 0), escala) / escala) * PH
  const marcas = Array.from({ length: 6 }, (_, k) => (escala / 5) * k)

  const avisos = []
  const notas = []
  for (const g of grupos) {
    if (g.nivelMixto) avisos.push(`${g.etiqueta}: ${NOTA_MIXTO}`)
    else if (g.n === 0) avisos.push(`${g.etiqueta}: sin datos.`)
    if (g.faltantes.length) {
      const faltan = g.faltantes.reduce((a, f) => a + f.nCilindros, 0)
      avisos.push(`${g.etiqueta}: ${faltan} ${faltan === 1 ? 'animal pendiente' : 'animales pendientes'} (${g.faltantes.map((f) => `Tanda ${f.letra} ${f.estado ?? 'sin subir'}`).join(', ')}).`)
    }
  }

  const titulo = '¿En qué se fue el tiempo?'
  const etiquetaSvg = 'Barras apiladas: media por animal de cada categoría de conducta y de la categoría sin ver, en segundos, por grupo.'

  return (
    <PanelGrafica
      titulo={titulo}
      subtitulo="Promedio por animal de cada categoría; la suma es la duración analizada."
      nombre="g4_apilada"
      svgRef={svgRef}
      leyenda={categorias.map((c) => (
        <span key={c.clave} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <MuestraLeyenda color={c.color} patron={c.patron} borde={c.clave === SIN_VER.clave ? 'var(--color-text)' : undefined} />{c.etiqueta}
        </span>
      ))}
      avisos={avisos} notas={notas}
      tabla={(
        <table className="table" style={{ fontSize: 12 }}>
          <caption className="hint" style={{ textAlign: 'left', padding: '0 0 6px' }}>Promedio por animal de cada categoría, Día 2, en segundos.</caption>
          <thead>
            <tr>
              <th>Grupo</th><th>n</th>
              {categorias.map((c) => <th key={c.clave} style={{ textAlign: 'right' }}>{c.etiqueta}</th>)}
              <th style={{ textAlign: 'right' }}>Duración analizada</th>
            </tr>
          </thead>
          <tbody>
            {grupos.map((g) => (
              <React.Fragment key={g.idGrupo}>
                <tr>
                  <td>{g.etiqueta}</td>
                  <td className="num">{textoN(g)}</td>
                  {categorias.map((c) => (
                    <td key={c.clave} className="num" style={{ textAlign: 'right' }}>
                      {g.nivelMixto ? 'por tanda' : g.porConducta ? textoSegundos(g.porConducta[c.clave]?.media) : 'sin datos'}
                    </td>
                  ))}
                  <td className="num" style={{ textAlign: 'right' }}>
                    {g.nivelMixto ? 'por tanda' : textoSegundos(g.duracionMediaS)}
                  </td>
                </tr>
                {g.nivelMixto && (g.porNivel || []).map((r) => (
                  <tr key={`${g.idGrupo}-${r.letra}`}>
                    <td style={{ paddingLeft: 18 }}>Tanda {r.letra} · {r.nivelClasif}</td>
                    <td className="num">{textoAnimales(r.n)}</td>
                    {categorias.map((c) => (
                      <td key={c.clave} className="num" style={{ textAlign: 'right' }}>{textoSegundos(r.porConducta[c.clave]?.media)}</td>
                    ))}
                    <td className="num" style={{ textAlign: 'right' }}>—</td>
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
          let acumulado = 0
          return (
            <g key={g.idGrupo}>
              <title>{g.etiqueta}</title>
              {dibujable(g) && categorias.map((c) => {
                const media = g.porConducta[c.clave]?.media
                if (media == null || media <= 0) return null
                const base = y(acumulado)
                const tope = y(acumulado + media)
                acumulado += media
                const altura = base - tope
                return (
                  <g key={c.clave}>
                    <rect x={cx - bw / 2} y={tope} width={bw} height={altura} fill={c.color}
                      stroke={c.clave === SIN_VER.clave ? 'var(--color-text)' : 'none'} strokeWidth="0.8" />
                    {c.patron && <rect x={cx - bw / 2} y={tope} width={bw} height={altura} fill={`url(#${patronId(prefijo, c.patron)})`} />}
                    {media >= MINIMO_ETIQUETA * escala && (
                      <text x={cx} y={tope + altura / 2 + 4} textAnchor="middle" fontSize="10" fill={colorDeTexto(c.clave)}>
                        {numero(media)}
                      </text>
                    )}
                  </g>
                )
              })}
              {dibujable(g) && g.duracionMediaS != null && (
                <text x={cx} y={y(g.duracionMediaS) - 6} textAnchor="middle" fontSize="10.5" fill="var(--color-text)">
                  {numero(g.duracionMediaS)} s
                </text>
              )}
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
    </PanelGrafica>
  )
}

// «296,4 s», o «sin datos» si no existe; nunca un cero para lo que no se midió.
function textoSegundos(valor) {
  return valor == null ? 'sin datos' : `${numero(valor)} s`
}
