import React from 'react'
import { fmt } from '../../lib/fst'
import { MUT60, MUT70 } from '../../lib/estilos'
import { CONDUCTAS } from './constantes'

// Columna de la derecha, en todas las pestañas: duración y especímenes analizados, y la leyenda de conductas.
export default function PanelLateral({ r, especimenes, g, grupoCompleto }) {
  return (
    <div style={{ background: 'var(--color-bg)', paddingLeft: 26, display: 'flex', flexDirection: 'column', gap: 24 }}>
      <div>
        <div className="k" style={{ marginBottom: 12 }}>Análisis</div>
        <div style={{ borderTop: '1px solid var(--color-divider)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, padding: '8px 0', borderBottom: '1px solid var(--color-divider)', fontSize: 11.5 }}>
            <span style={{ color: MUT70 }}>Duración analizada</span>
            <span className="num">{fmt(r.duracion_analizada_s)} / {fmt(r.duracion_s)}</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, padding: '8px 0', borderBottom: '1px solid var(--color-divider)', fontSize: 11.5 }}>
            <span style={{ color: MUT70 }}>Especímenes analizados</span>
            <span className="num">{especimenes.length} de {grupoCompleto ? g.n_especimenes : especimenes.length}</span>
          </div>
        </div>
      </div>

      <div>
        <div className="k" style={{ marginBottom: 12 }}>Leyenda de conductas</div>
        <div style={{ borderTop: '1px solid var(--color-divider)' }}>
          {CONDUCTAS.map((b) => (
            <div key={b.id} style={{ display: 'flex', alignItems: 'flex-start', gap: 10, padding: '9px 0', borderBottom: '1px solid var(--color-divider)' }}>
              <span className="sw" style={{ marginTop: 4, background: b.color }} />
              <div>
                <div className="nd" style={{ fontSize: 12.5 }}>{b.label}</div>
                <div style={{ fontSize: 11.5, lineHeight: 1.5, color: MUT60 }}>{b.nota}</div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
