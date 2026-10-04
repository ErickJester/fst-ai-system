import React from 'react'
import { Link, useParams } from 'react-router-dom'
import { getGroup } from '../services/groups'
import { useAsync } from '../hooks/useAsync'
import { PageShell, EstadoTag, Tag } from '../components/ui'
import { TIPO_GRUPO } from './ExperimentoPage'
import Pendiente from './Pendiente'

// 2d · Detalle de grupo
export default function GrupoPage() {
  const { id, gid } = useParams()
  const { data, error } = useAsync(() => getGroup(gid), [gid])
  const nombre = data ? (data.tipo === 'CONTROL' ? 'Grupo control' : data.nombre) : '…'

  return (
    <PageShell topbar={{ crumbs: [
      { label: 'Experimentos', to: '/experimentos' },
      { label: data?.experimento?.nombre || '…', to: `/experimentos/${id}` },
      { label: nombre },
    ] }}>
      {error && <p className="field-error">No existe ese grupo en los datos de ejemplo.</p>}
      {data && (
        <>
          <h2 style={{ margin: '0 0 9px' }}>
            {nombre} <Tag variant={TIPO_GRUPO[data.tipo].variant} style={{ verticalAlign: 'middle', marginLeft: 8 }}>{TIPO_GRUPO[data.tipo].label}</Tag>
          </h2>
          <div style={{ display: 'flex', gap: 24, fontSize: 13, color: 'var(--muted-2)', marginBottom: 22 }}>
            <span>{data.tratamiento}</span><span className="num">{data.n_especimenes} especímenes</span>
          </div>
          <Pendiente pantalla="2d" fase={3} />
          {data.tandas.map((t) => (
            <div key={t.id} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '13px 20px', background: 'var(--color-surface)', borderBottom: '1px solid var(--color-divider)' }}>
              <span className="nd" style={{ fontSize: 16 }}>Tanda {t.letra}</span>
              <span className="num muted" style={{ fontSize: 11.5 }}>Ratas {t.especimenes[0].rata}–{t.especimenes.at(-1).rata} · cilindros P1–P{t.n_cilindros}</span>
              <div style={{ flex: 1 }} />
              {t.estado === 'DONE' && <Link to={`/experimentos/${id}/grupos/${gid}/tandas/${t.id}/resultados`} style={{ fontSize: 12 }}>Ver resultados</Link>}
              <EstadoTag status={t.estado} />
            </div>
          ))}
        </>
      )}
    </PageShell>
  )
}
