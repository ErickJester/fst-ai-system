import React from 'react'
import { useParams } from 'react-router-dom'
import { getBatchResults } from '../services/results'
import { useAsync } from '../hooks/useAsync'
import { PageShell, Table } from '../components/ui'
import Pendiente from './Pendiente'

const fmt = (s) => Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0')

// 2f · Resultados de una tanda
export default function ResultadosPage() {
  const { id, gid, tid } = useParams()
  const { data, error } = useAsync(() => getBatchResults(tid), [tid])

  return (
    <PageShell topbar={{ crumbs: [
      { label: 'Experimentos', to: '/experimentos' },
      { label: data?.experimento.nombre || '…', to: `/experimentos/${id}` },
      { label: data?.grupo.nombre || 'Grupo', to: `/experimentos/${id}/grupos/${gid}` },
      { label: data ? `Tanda ${data.letra} · Día 2` : '…' },
    ] }}>
      {error && <p className="field-error">Esta tanda todavía no tiene resultados en los datos de ejemplo.</p>}
      {data && (
        <>
          <h2 style={{ margin: '0 0 9px' }}>Resultados · Grupo {data.grupo.nombre.toLowerCase()}, Tanda {data.letra}</h2>
          <div className="num" style={{ fontSize: 12.5, color: 'var(--muted-2)', marginBottom: 22 }}>
            {data.grupo.tratamiento} · Día 2, {data.duracion_s} s de evaluación · modelo {data.modelo}
          </div>
          <Pendiente pantalla="2f" fase={6} />
          <Table>
            <thead><tr><th>Espécimen</th><th style={{ textAlign: 'right' }}>Nado activo</th><th style={{ textAlign: 'right' }}>Inmovilidad</th><th style={{ textAlign: 'right' }}>Escalamiento</th></tr></thead>
            <tbody>
              {data.especimenes.map((e) => (
                <tr key={e.rata}>
                  <td style={{ fontSize: 13 }}>Rata {e.rata} · Cilindro {e.cilindro}</td>
                  <td className="num" style={{ textAlign: 'right' }}>{fmt(e.nado_s)}</td>
                  <td className="num" style={{ textAlign: 'right' }}>{fmt(e.inmovilidad_s)}</td>
                  <td className="num" style={{ textAlign: 'right' }}>{fmt(e.escalamiento_s)}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        </>
      )}
    </PageShell>
  )
}
