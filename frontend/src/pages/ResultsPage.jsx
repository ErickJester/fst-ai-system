import React, { useState, useEffect, useMemo } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useApi } from '../hooks/useApi'

const DAYS = [
  { key: 'DAY1', label: 'Día 1' },
  { key: 'DAY2', label: 'Día 2' },
]

const pct = (n, total) => (total ? (n / total) * 100 : 0)

function SummaryCards({ animals }) {
  const avg = (fn) => {
    if (!animals.length) return 0
    return animals.reduce((s, a) => s + fn(a), 0) / animals.length
  }
  const det = avg((a) => pct(a.frames_detected, a.frames_total))
  const frz = avg((a) => pct(a.frames_freeze + a.frames_lost, a.frames_total))
  const none = avg((a) => pct(a.frames_none, a.frames_total))
  return (
    <div className="sum-grid">
      <div className="sum-card sum-det">
        <div className="sum-label">Detección real promedio</div>
        <div className="sum-val">{det.toFixed(1)}%</div>
        <div className="sum-avg">YOLO + track + clásico</div>
      </div>
      <div className="sum-card sum-frz">
        <div className="sum-label">Bbox congelada promedio</div>
        <div className="sum-val">{frz.toFixed(1)}%</div>
        <div className="sum-avg">freeze + lost</div>
      </div>
      <div className="sum-card sum-none">
        <div className="sum-label">Sin bbox promedio</div>
        <div className="sum-val">{none.toFixed(1)}%</div>
        <div className="sum-avg">frames sin detección</div>
      </div>
    </div>
  )
}

function AnimalBars({ animals }) {
  return (
    <div className="animal-bars">
      {animals.map((a) => (
        <div key={a.rat_idx} className="abar-row">
          <span className="abar-label">Rata {a.rat_idx + 1}</span>
          <div className="abar-track">
            <div className="abar-det" style={{ width: `${pct(a.frames_detected, a.frames_total)}%` }} />
            <div className="abar-frz" style={{ width: `${pct(a.frames_freeze + a.frames_lost, a.frames_total)}%` }} />
            <div className="abar-none" style={{ width: `${pct(a.frames_none, a.frames_total)}%` }} />
          </div>
          <span className="abar-total">{a.frames_total} fr</span>
        </div>
      ))}
    </div>
  )
}

function TrackingTable({ animals }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Animal</th>
            <th className="th-det">Detectado (%)</th>
            <th>YOLO</th>
            <th>Track</th>
            <th>Clásico</th>
            <th className="th-frz">Freeze</th>
            <th className="th-frz">Lost</th>
            <th className="th-none">Sin bbox</th>
            <th>Total frames</th>
          </tr>
        </thead>
        <tbody>
          {animals.map((a) => (
            <tr key={a.rat_idx}>
              <td className="rat-name">Rata {a.rat_idx + 1}</td>
              <td className="val-det">{a.detected_pct.toFixed(1)}%</td>
              <td className="val-num">{a.frames_yolo}</td>
              <td className="val-num">{a.frames_track}</td>
              <td className="val-num">{a.frames_classic}</td>
              <td className="val-frz">{a.frames_freeze}</td>
              <td className="val-frz">{a.frames_lost}</td>
              <td className="val-none">{a.frames_none}</td>
              <td className="val-tot">{a.frames_total}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// Resultados del plugin de conducta: las etiquetas las define el plugin,
// así que las columnas se arman a partir de `labels`.
function BehaviorTable({ data }) {
  const { labels, animals } = data
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

export default function ResultsPage() {
  const { id } = useParams()
  const api = useApi()
  const [experiment, setExperiment] = useState(null)
  const [results, setResults] = useState({})
  const [behavior, setBehavior] = useState({})
  const [activeDay, setActiveDay] = useState('DAY1')
  const [activeTab, setActiveTab] = useState('resumen')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function load() {
      try {
        const [expRes, resRes, behRes] = await Promise.all([
          api.get(`/experiments/${id}`),
          api.get(`/experiments/${id}/results`),
          api.get(`/experiments/${id}/behavior`).catch(() => null),
        ])
        setExperiment(expRes.data)
        setResults(resRes.data.results || {})
        setBehavior(behRes?.data?.results || {})
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
  const animals = (day?.animals || []).filter((a) => a.frames_total !== undefined)
  const videoUrl = day?.has_tracked_video ? `${api.defaults.baseURL}/api/jobs/${day.job_id}/video` : null

  const dayBehavior = behavior[activeDay]

  const tabs = [
    { key: 'resumen', label: 'Resumen' },
    { key: 'detalle', label: 'Tabla detallada' },
    { key: 'video', label: 'Video anotado' },
    ...(dayBehavior ? [{ key: 'conducta', label: 'Conducta' }] : []),
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
          <div className="page-title">Resultados del tracking — {experiment?.name || '…'}</div>
          <div className="page-subtitle">
            {notes.treatment || experiment?.treatment || ''} · {notes.animals || '?'} animales
          </div>
        </div>
      </div>

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

      {loading ? (
        <div style={{ textAlign: 'center', padding: 48, color: 'var(--c-text-muted)' }}>Cargando resultados…</div>
      ) : !day ? (
        <div style={{ textAlign: 'center', padding: 48, color: 'var(--c-text-muted)' }}>No hay un análisis terminado para este día.</div>
      ) : (
        <>
          {activeTab === 'resumen' && (
            <div className="card">
              <div className="card-header card-header--between">
                <div className="card-header-left">
                  <div className="card-header-icon">
                    <svg width="15" height="15" viewBox="0 0 15 15" fill="none"><rect x="1.5" y="3" width="12" height="9" rx="1.5" stroke="#4b6490" strokeWidth="1.1"/><path d="M4.5 3V2M10.5 3V2M1.5 6.5h12" stroke="#4b6490" strokeWidth="1.1" strokeLinecap="round"/></svg>
                  </div>
                  <div>
                    <div className="card-title">Calidad del tracking por animal</div>
                    <div className="card-subtitle">Porcentaje de frames según el origen de la bbox</div>
                  </div>
                </div>
                <div className="legend">
                  <span className="leg leg-det"><span className="leg-dot" style={{ background: 'var(--t-det)' }} />Detectado</span>
                  <span className="leg leg-frz"><span className="leg-dot" style={{ background: 'var(--t-frz)' }} />Congelado</span>
                  <span className="leg leg-none"><span className="leg-dot" style={{ background: 'var(--t-none)' }} />Sin bbox</span>
                </div>
              </div>
              <div className="card-body">
                <SummaryCards animals={animals} />
                <AnimalBars animals={animals} />
              </div>
            </div>
          )}

          {activeTab === 'detalle' && (
            <div className="card">
              <div className="card-header">
                <div className="card-header-icon">
                  <svg width="15" height="15" viewBox="0 0 15 15" fill="none"><rect x="1.5" y="1.5" width="12" height="12" rx="1.5" stroke="#4b6490" strokeWidth="1.1"/><path d="M1.5 5.5h12M1.5 9h12M5.5 1.5v12" stroke="#4b6490" strokeWidth="1.1"/></svg>
                </div>
                <div>
                  <div className="card-title">Frames por fuente de detección</div>
                  <div className="card-subtitle">Detectado + freeze + lost + sin bbox = total de frames procesados</div>
                </div>
              </div>
              <TrackingTable animals={animals} />
            </div>
          )}

          {activeTab === 'video' && (
            <div className="card">
              <div className="card-body">
                {videoUrl ? (
                  <video className="tracked-video" src={videoUrl} controls preload="metadata" />
                ) : (
                  <div style={{ textAlign: 'center', padding: 40, color: 'var(--c-text-muted)' }}>
                    El video anotado no está disponible.
                  </div>
                )}
              </div>
            </div>
          )}

          {activeTab === 'conducta' && dayBehavior && (
            <div className="card">
              <div className="card-header">
                <div>
                  <div className="card-title">Conducta por animal</div>
                  <div className="card-subtitle">
                    Clasificador: {dayBehavior.plugin} {dayBehavior.plugin_version}
                  </div>
                </div>
              </div>
              <BehaviorTable data={dayBehavior} />
            </div>
          )}
        </>
      )}
    </main>
  )
}
