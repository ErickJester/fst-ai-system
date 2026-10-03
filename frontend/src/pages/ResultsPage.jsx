import React, { useState, useEffect, useMemo } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useApi } from '../hooks/useApi'

const DAYS = [
  { key: 'DAY1', label: 'Día 1' },
  { key: 'DAY2', label: 'Día 2' },
]

// Las etiquetas de conducta las define el pipeline de análisis,
// así que las columnas se arman a partir de `labels`.
function BehaviorTable({ labels, animals }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Animal</th>
            {labels.map((l) => <th key={l}>{l} (s)</th>)}
            <th>Total (s)</th>
          </tr>
        </thead>
        <tbody>
          {animals.map((a) => {
            const total = labels.reduce((s, l) => s + (a.totals_s[l] || 0), 0)
            return (
              <tr key={a.rat_idx}>
                <td className="rat-name">Rata {a.rat_idx + 1}</td>
                {labels.map((l) => <td key={l} className="val-num">{(a.totals_s[l] || 0).toFixed(1)}</td>)}
                <td className="val-tot">{total.toFixed(1)}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function PerMinuteTable({ labels, animal }) {
  return (
    <div className="min-animal-section">
      <div className="min-animal-title">Rata {animal.rat_idx + 1}</div>
      <table className="min-tbl">
        <thead>
          <tr>
            <th>Minuto</th>
            {labels.map((l) => <th key={l}>{l} (s)</th>)}
          </tr>
        </thead>
        <tbody>
          {animal.per_minute.map((m) => (
            <tr key={m.minute}>
              <td>{m.minute}</td>
              {labels.map((l) => <td key={l}>{(m.seconds[l] || 0).toFixed(1)}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function Empty({ children }) {
  return <div style={{ textAlign: 'center', padding: 48, color: 'var(--c-text-muted)' }}>{children}</div>
}

export default function ResultsPage() {
  const { id } = useParams()
  const api = useApi()
  const [experiment, setExperiment] = useState(null)
  const [results, setResults] = useState({})
  const [activeDay, setActiveDay] = useState('DAY1')
  const [activeTab, setActiveTab] = useState('resumen')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function load() {
      try {
        const [expRes, resRes] = await Promise.all([
          api.get(`/experiments/${id}`),
          api.get(`/experiments/${id}/results`),
        ])
        setExperiment(expRes.data)
        setResults(resRes.data.results || {})
        if (!resRes.data.results?.DAY1 && resRes.data.results?.DAY2) setActiveDay('DAY2')
      } catch (err) {
        console.error(err)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [api, id])

  const notes = useMemo(() => {
    try { return experiment?.notes ? JSON.parse(experiment.notes) : {} } catch { return {} }
  }, [experiment])

  const day = results[activeDay]
  const hasAnyResults = Object.keys(results).length > 0
  const videoUrl = day?.has_annotated_video ? `${api.defaults.baseURL}/api/jobs/${day.job_id}/video` : null

  const tabs = [
    { key: 'resumen', label: 'Resumen' },
    { key: 'minutos', label: 'Por minuto' },
    ...(videoUrl ? [{ key: 'video', label: 'Video anotado' }] : []),
  ]

  return (
    <main className="page page--medium">
      <nav className="breadcrumb">
        <Link to="/dashboard">Mis experimentos</Link>
        <span className="breadcrumb-sep">›</span>
        <span>{experiment?.name || '…'}</span>
        <span className="breadcrumb-sep">›</span>
        <span>Resultados</span>
      </nav>

      <div className="page-header">
        <div>
          <div className="page-title">Resultados — {experiment?.name || '…'}</div>
          <div className="page-subtitle">
            {notes.treatment || experiment?.treatment || ''} · {notes.animals || '?'} animales
          </div>
        </div>
      </div>

      {loading ? (
        <Empty>Cargando resultados…</Empty>
      ) : !hasAnyResults ? (
        <div className="one-session-notice">
          <span>
            Aún no hay resultados. El pipeline de análisis de video todavía no está
            implementado: los videos subidos quedan en cola hasta que lo esté.
          </span>
        </div>
      ) : (
        <>
          <div className="section-tabs">
            {DAYS.map((d) => (
              <div
                key={d.key}
                className={`stab ${activeDay === d.key ? 'active' : ''}`}
                onClick={() => setActiveDay(d.key)}
              >
                {d.label}{results[d.key] ? '' : ' (sin analizar)'}
              </div>
            ))}
          </div>

          <div className="section-tabs">
            {tabs.map((t) => (
              <div key={t.key} className={`stab ${activeTab === t.key ? 'active' : ''}`} onClick={() => setActiveTab(t.key)}>
                {t.label}
              </div>
            ))}
          </div>

          {!day ? (
            <Empty>No hay un análisis terminado para este día.</Empty>
          ) : (
            <div className="card">
              <div className="card-header">
                <div>
                  <div className="card-title">Conducta por animal</div>
                  <div className="card-subtitle">
                    Pipeline: {day.pipeline || '—'} {day.pipeline_version || ''}
                  </div>
                </div>
              </div>

              {activeTab === 'resumen' && <BehaviorTable labels={day.labels} animals={day.animals} />}

              {activeTab === 'minutos' && (
                <div className="card-body">
                  {day.animals.map((a) => <PerMinuteTable key={a.rat_idx} labels={day.labels} animal={a} />)}
                </div>
              )}

              {activeTab === 'video' && videoUrl && (
                <div className="card-body">
                  <video className="annotated-video" src={videoUrl} controls preload="metadata" />
                </div>
              )}
            </div>
          )}
        </>
      )}
    </main>
  )
}
