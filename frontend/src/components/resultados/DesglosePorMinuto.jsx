import React from 'react'
import { fmt } from '../../lib/fst'
import { MUT60 } from '../../lib/estilos'
import { MUT, CONDUCTAS, SIN_VER, TODAS, CMAP, LBL, etiqueta } from './constantes'

const enMinuto = (segs, k) => segs.filter((s) => s[0] === k).reduce((a, s) => a + s[1], 0)

// Desglose por minuto de un espécimen: el selector, una barra por minuto y la tabla de segundos por conducta.
export default function DesglosePorMinuto({ lineas, linea, setLineaSel, innerRef }) {
  return (
    <>
      <div ref={innerRef} style={{ display: 'flex', alignItems: 'center', gap: 10, margin: '26px 0 12px' }}>
        <label className="k" htmlFor="lineaSel" style={{ flex: 1 }}>Desglose por minuto · {etiqueta(linea, linea.letra)}</label>
        <select className="input" id="lineaSel" style={{ width: 210, padding: '4px 6px', fontSize: 12 }} value={linea.clave} onChange={(e) => setLineaSel(e.target.value)}>
          {lineas.map((l) => <option key={l.clave} value={l.clave}>{etiqueta(l, l.letra)}</option>)}
        </select>
      </div>
      <div style={{ border: '1px solid var(--color-divider)', padding: '16px 18px' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {linea.minutos.map((segs, i) => (
            <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <span className="num" style={{ width: 74, fontSize: 11.5, color: MUT60 }}>{fmt(i * 60)}–{fmt(i * 60 + 60)}</span>
              <div style={{ flex: 1, display: 'flex', height: 18, border: '1px solid var(--color-divider)' }}>
                {segs.map((s, j) => <div key={j} title={LBL[s[0]] + ' · ' + s[1] + ' s'} style={{ background: CMAP[s[0]], width: (s[1] / 60) * 100 + '%' }} />)}
              </div>
            </div>
          ))}
        </div>
      </div>
      <div style={{ overflowX: 'auto' }}>
        <table className="table" style={{ marginTop: 10, fontSize: 12 }}>
          <thead><tr><th>Minuto</th>{CONDUCTAS.map((c) => <th key={c.id} style={{ textAlign: 'right' }}>{c.label}</th>)}<th style={{ textAlign: 'right', fontWeight: 400, color: MUT }}>{SIN_VER.label}</th></tr></thead>
          <tbody>
            {linea.minutos.map((segs, i) => (
              <tr key={i}>
                <td className="num">{fmt(i * 60)}–{fmt(i * 60 + 60)}</td>
                {TODAS.map((k) => <td key={k} className="num" style={{ textAlign: 'right', ...(k === SIN_VER.id && { color: MUT }) }}>{enMinuto(segs, k)} s</td>)}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}
