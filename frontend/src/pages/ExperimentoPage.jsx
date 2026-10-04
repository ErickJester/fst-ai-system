import React from 'react'
import { Link, useParams } from 'react-router-dom'
import { getExperiment } from '../services/experiments'
import { useAsync } from '../hooks/useAsync'
import { PageShell, EstadoTag, Tag } from '../components/ui'
import Pendiente from './Pendiente'

export const TIPO_GRUPO = {
  CONTROL: { label: 'control', variant: 'neutral' },
  REFERENCIA: { label: 'referencia', variant: 'outline' },
  TRATAMIENTO: { label: 'tratamiento experimental', variant: 'accent' },
}

// 2c · Detalle de experimento
export default function ExperimentoPage() {
  const { id } = useParams()
  const { data, error } = useAsync(() => getExperiment(id), [id])

  return (
    <PageShell topbar={{ crumbs: [{ label: 'Experimentos', to: '/experimentos' }, { label: data?.nombre || '…' }] }}>
      {error && <p className="field-error">Este experimento no tiene detalle en los datos de ejemplo.</p>}
      {data && (
        <>
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: 28, marginBottom: 22 }}>
            <h2 style={{ margin: 0, flex: 1 }}>{data.nombre}</h2>
            <Link className="btn btn-secondary" to={`/experimentos/${id}/cargar`}>Cargar videos pendientes</Link>
          </div>
          <Pendiente pantalla="2c" fase={3} />
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, background: 'var(--color-divider)', border: '1px solid var(--color-divider)' }}>
            {data.grupos.map((g) => (
              <div key={g.id} style={{ background: 'var(--color-bg)', padding: '18px 20px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 11, marginBottom: 10 }}>
                  <span className="nd" style={{ fontSize: 17 }}>{g.nombre}</span>
                  <Tag variant={TIPO_GRUPO[g.tipo].variant}>{TIPO_GRUPO[g.tipo].label}</Tag>
                </div>
                {g.tandas.map((t) => (
                  <div key={t.id} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '9px 0', borderBottom: '1px solid var(--color-divider)' }}>
                    <span className="nd" style={{ fontSize: 13 }}>Tanda {t.letra}</span>
                    <div style={{ flex: 1 }} />
                    <EstadoTag status={t.estado} />
                  </div>
                ))}
                <Link className="btn btn-ghost" to={`/experimentos/${id}/grupos/${g.id}`} style={{ marginTop: 12 }}>Abrir grupo →</Link>
              </div>
            ))}
          </div>
        </>
      )}
    </PageShell>
  )
}
