import React from 'react'
import { getQueue } from '../services/queue'
import { useAsync } from '../hooks/useAsync'
import { PageShell, Table, EstadoTag, Meter } from '../components/ui'
import Pendiente from './Pendiente'

const DIA = { DAY1: 'Día 1', DAY2: 'Día 2' }

// 2e · Análisis en curso
export default function AnalisisPage() {
  const { data } = useAsync(getQueue)

  return (
    <PageShell width={860} topbar={{ sub: 'Análisis en curso' }}>
      <h3 style={{ margin: '0 0 8px' }}>Análisis en curso</h3>
      <p className="lead" style={{ margin: '0 0 22px', maxWidth: 600 }}>
        El <em>pipeline</em> (cadena de procesamiento) atiende un trabajo a la vez. El orden lo fija el sistema al momento de la carga: no hay iniciar, pausar, cancelar ni reiniciar desde aquí.
      </p>
      <Pendiente pantalla="2e" fase={5} />
      {data && (
        <Table>
          <tbody>
            {[...data.cola, ...data.errores].map((j) => (
              <tr key={j.job_id}>
                <td className="num muted" style={{ width: 30 }}>{j.posicion || '—'}</td>
                <td>
                  {j.experimento} · {j.grupo} · Tanda {j.tanda}
                  {j.status === 'RUNNING' && <div style={{ marginTop: 6 }}><Meter value={j.progress_pct} label="Avance del análisis" /></div>}
                  {j.error && <div className="hint" style={{ color: 'var(--color-accent-700)' }}>{j.error.codigo} · {j.error.mensaje}</div>}
                </td>
                <td className="num" style={{ width: 70 }}>{DIA[j.dia]}</td>
                <td style={{ width: 130, textAlign: 'right' }}><EstadoTag status={j.status} /></td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </PageShell>
  )
}
