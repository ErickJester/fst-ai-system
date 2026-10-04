import React from 'react'
import { Link } from 'react-router-dom'
import { listExperiments } from '../services/experiments'
import { useAsync } from '../hooks/useAsync'
import { PageShell, Table, Tag, Meter } from '../components/ui'
import Pendiente from './Pendiente'

const ESTADO_EXP = {
  EN_ANALISIS: { label: 'En análisis', variant: 'accent' },
  CARGA_INCOMPLETA: { label: 'Carga incompleta', variant: 'outline' },
  CONCLUIDO: { label: 'Concluido', variant: 'neutral' },
}

// 2a · Experimentos
export default function ExperimentosPage() {
  const { data, error } = useAsync(listExperiments)

  return (
    <PageShell topbar={{ links: true, sub: 'Laboratorio de Bioquímica Estructural · ENMyH-IPN' }}>
      <h2 style={{ margin: '0 0 8px' }}>Experimentos</h2>
      <p className="lead" style={{ maxWidth: 620, marginBottom: 22 }}>
        Prueba de nado forzado (<em>Forced Swim Test</em>, FST). El archivo completo del laboratorio, visible para cualquier cuenta activa.
      </p>
      <Pendiente pantalla="2a" fase={3} />
      {error && <p className="field-error">No se pudo cargar la lista de experimentos.</p>}
      {data && (
        <Table>
          <thead><tr><th style={{ width: '40%' }}>Experimento</th><th>Especímenes</th><th style={{ width: '18%' }}>Videos Día 2</th><th>Estado</th><th>Responsable</th></tr></thead>
          <tbody>
            {data.map((e) => (
              <tr key={e.id}>
                <td><Link to={`/experimentos/${e.id}`} className="nd" style={{ fontSize: 14, color: 'var(--color-text)', textDecoration: 'none' }}>{e.nombre}</Link></td>
                <td className="num">{e.n_especimenes}</td>
                <td>
                  <div className="num" style={{ fontSize: 12, marginBottom: 5 }}>{e.videos_dia2_listos} / {e.videos_dia2_total}</div>
                  <Meter value={(e.videos_dia2_listos / e.videos_dia2_total) * 100} label="Videos de Día 2 analizados" />
                </td>
                <td><Tag variant={ESTADO_EXP[e.estado].variant}>{ESTADO_EXP[e.estado].label}</Tag></td>
                <td style={{ fontSize: 13 }}>{e.responsable}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </PageShell>
  )
}
