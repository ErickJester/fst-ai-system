import React, { useState, useEffect, useCallback } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { useApi } from '../hooks/useApi'
import { usePolling } from '../hooks/usePolling'

const STAGES = [
  { key: 'upload', name: 'Carga de video', desc: 'Verificación de integridad y formato del archivo de video.' },
  { key: 'roi', name: 'Detección de ROI', desc: 'Identificación automática de la región de interés (tanque de nado).' },
  { key: 'tracking', name: 'Tracking de animales', desc: 'Seguimiento frame a frame de cada animal en el campo de visión.' },
  { key: 'classify', name: 'Clasificación de conducta', desc: 'Asignación de conducta (nado, inmovilidad, escape) por frame y animal.' },
  { key: 'summary', name: 'Resumen de resultados', desc: 'Cálculo de tiempos totales y generación del reporte por animal.' },
]

function deriveStages(jobStatus) {
  if (jobStatus === 'DONE') return STAGES.map((s) => ({ ...s, state: 'done', pct: 100 }))
  if (jobStatus === 'FAILED') {
    return STAGES.map((s, i) => {
      if (i === 0) return { ...s, state: 'done', pct: 100 }
      if (i === 1) return { ...s, state: 'error', pct: 0 }
      return { ...s, state: 'wait', pct: 0 }
    })
  }
  if (jobStatus === 'RUNNING') {
    return STAGES.map((s, i) => {
      if (i < 2) return { ...s, state: 'done', pct: 100 }
      if (i === 2) return { ...s, state: 'active', pct: 65 }
      return { ...s, state: 'wait', pct: 0 }
    })
  }
  return STAGES.map((s) => ({ ...s, state: 'wait', pct: 0 }))
}

function overallPct(stages) {
  const done = stages.filter((s) => s.state === 'done').length
  const active = stages.find((s) => s.state === 'active')
  const total = stages.length
  return Math.round(((done + (active ? active.pct / 100 : 0)) / total) * 100)
}

function StageIcon({ state }) {
  if (state === 'done') return <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M3 7l3 3 5-5" stroke="#10b981" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>
  if (state === 'active') return <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><circle cx="7" cy="7" r="3" fill="#3b82f6"/></svg>
  if (state === 'error') return <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M4 4l6 6M10 4l-6 6" stroke="#ef4444" strokeWidth="2" strokeLinecap="round"/></svg>
  return <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><circle cx="7" cy="7" r="3" stroke="#d0d5dd" strokeWidth="1.5"/></svg>
}

export default function ProgressPage() {
  const { id } = useParams()
  const api = useApi()
  const navigate = useNavigate()
  const [session, setSession] = useState(null)
  const [videos, setVideos] = useState([])
  const [activeTab, setActiveTab] = useState(1)
  const [job, setJob] = useState(null)

  useEffect(() => {
    async function load() {
      try {
        const [sessRes, vidRes] = await Promise.all([
          api.get('/api/sessions'),
          api.get(`/api/sessions/${id}/videos`),
        ])
        const s = sessRes.data.find((x) => x.id === parseInt(id))
        setSession(s || { id, name: `Experimento ${id}`, created_at: new Date().toISOString() })
        setVideos(vidRes.data)
      } catch (err) {
        console.error(err)
      }
    }
    load()
  }, [api, id])

  const fetchJob = useCallback(async () => {
    if (videos.length === 0) return null
    const targetVideo = videos.find((v) => v.day === `DAY${activeTab}`) || videos[0]
    if (!targetVideo) return null
    try {
      const res = await api.post('/api/jobs', { video_id: targetVideo.id })
      setJob(res.data)
      return res.data
    } catch {
      return null
    }
  }, [api, videos, activeTab])

  const isDone = job?.status === 'DONE' || job?.status === 'FAILED'

  usePolling(fetchJob, 2000, videos.length > 0 && !isDone)

  useEffect(() => {
    if (videos.length > 0 && !job) fetchJob()
  }, [videos, fetchJob, job])

  const stages = deriveStages(job?.status || 'QUEUED')
  const pct = overallPct(stages)
  const statusLabel = job?.status === 'DONE' ? 'Completado' : job?.status === 'FAILED' ? 'Error' : job?.status === 'RUNNING' ? 'En proceso' : 'En espera'
  const pillClass = job?.status === 'DONE' ? 'pill-ok' : job?.status === 'FAILED' ? 'pill-err' : job?.status === 'RUNNING' ? 'pill-proc' : 'pill-wait'

  let notes = {}
  try { notes = session?.notes ? JSON.parse(session.notes) : {} } catch {}

  return (
    <main className="page" style={{ maxWidth: 820 }}>
      <nav className="breadcrumb">
        <Link to="/dashboard">Mis experimentos</Link>
        <span className="breadcrumb-sep">›</span>
        <span>{session?.name || '…'}</span>
        <span className="breadcrumb-sep">›</span>
        <span>Progreso del análisis</span>
      </nav>

      <div className="page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
          <div className="page-title">Progreso del análisis</div>
          <span className={`status-pill ${pillClass}`}>
            <span className="pill-dot" />
            {statusLabel}
          </span>
        </div>
        <div className="page-subtitle">
          {job?.status === 'DONE'
            ? 'El análisis ha finalizado correctamente.'
            : job?.status === 'FAILED'
            ? 'El análisis falló. Revisa los detalles del error.'
            : 'El análisis está en curso. Puedes cerrar esta pestaña; el proceso continuará en segundo plano.'}
        </div>
      </div>

      {job?.status === 'DONE' && (
        <div className="success-banner">
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none"><circle cx="10" cy="10" r="8" stroke="#10b981" strokeWidth="1.5"/><path d="M6.5 10l2.5 2.5 5-5" stroke="#10b981" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>
          <div className="success-banner-text">
            <strong>Análisis completado</strong>
            Los resultados están disponibles para consulta y descarga.
          </div>
          <button className="btn-primary btn-primary--sm" onClick={() => navigate(`/experiments/${id}/results`)}>
            Ver resultados →
          </button>
        </div>
      )}

      <div className="card">
        {/* Metadata strip */}
        <div className="meta-strip">
          <div className="meta-item">
            <span className="meta-label">Experimento:</span>
            <span className="meta-value">{session?.name || '…'}</span>
          </div>
          {notes.treatment && (
            <div className="meta-item">
              <span className="meta-label">Tratamiento:</span>
              <span className="meta-value">{notes.treatment}</span>
            </div>
          )}
          <div className="meta-item">
            <span className="meta-label">Iniciado:</span>
            <span className="meta-value">
              {session ? new Date(session.created_at).toLocaleDateString('es-MX', { day: '2-digit', month: 'short', year: 'numeric' }) : '…'}
            </span>
          </div>
          {notes.animals && (
            <div className="meta-item">
              <span className="meta-label">Animales:</span>
              <span className="meta-value">{notes.animals}</span>
            </div>
          )}
        </div>

        {/* Video tabs */}
        {videos.length > 0 && (
          <div className="video-tabs">
            {videos.map((v, i) => (
              <div
                key={v.id}
                className={`vtab ${activeTab === (i + 1) ? 'active' : ''} ${job?.status === 'DONE' ? 'done' : ''}`}
                onClick={() => setActiveTab(i + 1)}
              >
                <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><rect x="1" y="2.5" width="9" height="7" rx="1.2" stroke="currentColor" strokeWidth="1.1"/><path d="M10 5.5l2-1.5v5l-2-1.5V5.5z" stroke="currentColor" strokeWidth="1.1" strokeLinejoin="round"/></svg>
                {v.day === 'DAY1' ? 'Día 1 — Basal' : 'Día 2 — Post-tratamiento'}
              </div>
            ))}
          </div>
        )}

        {/* Pipeline stages */}
        {job?.status !== 'FAILED' && (
          <div className="pipeline">
            {stages.map((stage, i) => (
              <div key={stage.key} className={`stage ${stage.state}`}>
                <div className="stage-connector" />
                <div className="stage-icon"><StageIcon state={stage.state} /></div>
                <div className="stage-body">
                  <div className="stage-name">{stage.name}</div>
                  <div className="stage-desc">{stage.desc}</div>
                  {stage.state === 'active' && (
                    <div className="stage-progress">
                      <div className="stage-progress-bar-wrap">
                        <div className="stage-progress-bar" style={{ width: `${stage.pct}%` }} />
                      </div>
                      <span className="stage-progress-pct">{stage.pct}%</span>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Error panel */}
        {job?.status === 'FAILED' && (
          <div className="error-panel">
            <div className="err-header">
              <svg width="20" height="20" viewBox="0 0 20 20" fill="none" style={{ flexShrink: 0 }}>
                <circle cx="10" cy="10" r="8" stroke="#ef4444" strokeWidth="1.5"/>
                <path d="M10 6v5" stroke="#ef4444" strokeWidth="1.5" strokeLinecap="round"/>
                <circle cx="10" cy="13.5" r=".75" fill="#ef4444"/>
              </svg>
              <div>
                <div className="err-title">Error en el análisis</div>
                <div className="err-code">ERROR_PIPELINE</div>
                <div className="err-message">{job.error || 'Se produjo un error durante el procesamiento del video.'}</div>
              </div>
            </div>
            <div className="err-actions">
              <button className="btn-primary btn-primary--sm" onClick={() => window.location.reload()}>
                Reintentar análisis
              </button>
              <button className="btn-ghost" onClick={() => navigate('/dashboard')}>
                Volver al dashboard
              </button>
            </div>
          </div>
        )}

        {/* Overall progress */}
        {job?.status !== 'FAILED' && (
          <div className="overall-bar-wrap">
            <div className="overall-bar-header">
              <span className="overall-bar-label">Progreso total</span>
              <span className="overall-bar-pct">{pct}%</span>
            </div>
            <div className="overall-progress">
              <div className="overall-progress-fill" style={{ width: `${pct}%` }} />
            </div>
            <div className="overall-bar-sub">
              {job?.status === 'DONE'
                ? 'Análisis completado'
                : `Etapa ${stages.filter((s) => s.state === 'done').length + 1} de ${stages.length}`}
            </div>
          </div>
        )}
      </div>
    </main>
  )
}
