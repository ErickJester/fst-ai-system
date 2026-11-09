import React from 'react'
import { r1 } from '../../lib/fst'
import { MUT70 } from '../../lib/estilos'
import { INK, MUT, PEND, CONDUCTAS, DIA2_S, COLOR_TIPO, COLOR_AGRUPADO, PENDIENTE } from './constantes'

const pct = (v) => (v / DIA2_S) * 100 + '%'
const val = (m, de) => r1(m) + ' ± ' + r1(de) + ' s'

function Barra({ m, de, color, h }) {
  return (
    <div style={{ height: h, background: 'color-mix(in srgb,var(--color-text) 10%,transparent)', position: 'relative' }}>
      <div style={{ height: h, background: color, width: pct(m) }} />
      <div className="bar-de" style={{ height: h + 6, left: pct(Math.max(0, m - de)) }} />
      <div className="bar-de" style={{ height: h + 6, left: pct(Math.min(DIA2_S, m + de)) }} />
    </div>
  )
}

// «4 especímenes pendientes (Tanda B en cola)»; sin datos: «8 de 8 especímenes pendientes (…)».
function notaComparacion(g) {
  const n = 'n = ' + g.n + ' de ' + g.n_total
  if (!g.pendientes.length) return n
  const faltan = g.pendientes.reduce((a, p) => a + p.n, 0)
  const causas = g.pendientes.map((p) => 'Tanda ' + p.tanda + ' ' + (PENDIENTE[p.estado] || 'sin subir')).join(', ')
  const texto = (g.n === 0 ? faltan + ' de ' + g.n_total : faltan) + ' especímenes pendientes (' + causas + ')'
  return g.n === 0 ? texto : n + ' · ' + texto
}

function Comparacion({ g, c }) {
  const notaFg = g.pendientes.length ? PEND : MUT
  let valor, valorFg, body
  if (g.por_nivel) {
    valor = 'por nivel'
    valorFg = MUT
    body = (
      <>
        <div className="note-bar" style={{ fontSize: 11.5, lineHeight: 1.5, marginBottom: 8 }}>Este grupo mezcla niveles de clasificación; se muestran por separado.</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
          {g.por_nivel.map((u) => (
            <div key={u.tanda}>
              <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 10, marginBottom: 4 }}>
                <span style={{ fontSize: 11.5, color: MUT70 }}>{u.nivel.toLowerCase()} · Tanda {u.tanda} · n = {u.n}</span>
                <span className="num nd" style={{ fontSize: 11, whiteSpace: 'nowrap' }}>{val(u.por_conducta[c].media_s, u.por_conducta[c].de_s)}</span>
              </div>
              <Barra m={u.por_conducta[c].media_s} de={u.por_conducta[c].de_s} color={u.nivel === 'AGRUPADO' ? COLOR_AGRUPADO : COLOR_TIPO[g.tipo]} h={8} />
            </div>
          ))}
        </div>
      </>
    )
  } else if (g.por_conducta[c].media_s == null) {
    valor = 'sin datos'
    valorFg = PEND
    body = <div style={{ height: 10, background: 'repeating-linear-gradient(135deg,color-mix(in srgb,var(--color-text) 18%,transparent) 0 3px,transparent 3px 6px)', border: '1px solid var(--color-divider)' }} />
  } else {
    const { media_s, de_s } = g.por_conducta[c]
    valor = val(media_s, de_s)
    valorFg = INK
    body = <Barra m={media_s} de={de_s} color={COLOR_TIPO[g.tipo]} h={10} />
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 10, marginBottom: 5 }}>
        <span style={{ fontSize: 12 }}>{g.grupo}</span>
        <span className="num nd" style={{ fontSize: 11.5, whiteSpace: 'nowrap', color: valorFg }}>{valor}</span>
      </div>
      {body}
      <div className="num" style={{ fontSize: 11, marginTop: 5, color: notaFg }}>{notaComparacion(g)}</div>
    </div>
  )
}

// Promedio por espécimen de cada grupo, para una conducta que se elige; el selector va en el encabezado de la sección.
export default function ComparacionGrupos({ comparacion, conducta, setConducta }) {
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
        <label className="k" htmlFor="cConducta" style={{ flex: 1 }}>Comparación entre grupos · Día 2</label>
        <select className="input" id="cConducta" style={{ width: 150, padding: '4px 6px', fontSize: 12 }} value={conducta} onChange={(e) => setConducta(e.target.value)}>
          {CONDUCTAS.map((x) => <option key={x.id} value={x.id}>{x.label}</option>)}
        </select>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 15 }}>
        {comparacion ? comparacion.map((g) => <Comparacion key={g.grupo} g={g} c={conducta} />) : <p className="hint">Cargando…</p>}
      </div>
      <p className="hint" style={{ margin: '12px 0 0' }}>
        Promedio por espécimen de cada grupo (media ± DE), en segundos sobre los 300 s del Día 2. Las marcas verticales delimitan ± una DE. Los especímenes sin Día 2 analizado (en cola, procesando, con error o sin subir) se señalan y no entran al promedio. Cada grupo entra con todas sus tandas, no solo la que se ve arriba.
      </p>
    </div>
  )
}
