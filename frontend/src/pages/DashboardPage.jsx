import React, { useState, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useApi } from '../hooks/useApi'

function StatCard({ label, value, sub, color }) {
  return (
    <div className="stat-card">
      <div className="stat-label">{label}</div>
      <div className="stat-value" style={color ? { color } : undefined}>{value}</div>
      {sub && <div className="stat-sub">{sub}</div>}
    </div>
  )
}

function Badge({ status }) {
  const map = {
    DONE: { cls: 'badge-completado', label: 'Completado' },
    RUNNING: { cls: 'badge-proceso', label: 'En proceso' },
    QUEUED: { cls: 'badge-pendiente', label: 'Pendiente' },
    FAILED: { cls: 'badge-error', label: 'Error' },
    NONE: { cls: 'badge-pendiente', label: 'Sin analizar' },
  }
  const { cls, label } = map[status] || map.NONE
  return (
    <span className={`badge ${cls}`}>
      <span className="badge-dot" />
      {label}
    </span>
  )
}

export default function DashboardPage() {
  const api = useApi()
  const navigate = useNavigate()
  const [sessions, setSessions] = useState([])
  const [search, setSearch] = useState('')
  const [filterStatus, setFilterStatus] = useState('all')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function load() {
      try {
        const res = await api.get('/api/sessions')
        const sessionsData = res.data

        const enriched = await Promise.all(
          sessionsData.map(async (s) => {
            try {
              const vRes = await api.get(`/api/sessions/${s.id}/videos`)
              const videos = vRes.data
              let latestStatus = 'NONE'

              for (const v of videos) {
                try {
                  const jRes = await api.post('/api/jobs', { video_id: v.id }).catch(() => null)
                  if (jRes?.data?.status) {
                    const st = jRes.data.status
                    if (st === 'RUNNING' || st === 'QUEUED') { latestStatus = st; break }
                    if (st === 'DONE' && latestStatus !== 'RUNNING') latestStatus = st
                    if (st === 'FAILED' && latestStatus === 'NONE') latestStatus = st
                  }
                } catch {}
              }

              let notes = {}
              try { notes = s.notes ? JSON.parse(s.notes) : {} } catch { notes = { raw: s.notes } }

              return {
                ...s,
                videoCount: videos.length,
                status: latestStatus,
                treatment: notes.treatment || '—',
                animals: notes.animals || '—',
              }
            } catch {
              return { ...s, videoCount: 0, status: 'NONE', treatment: '—', animals: '—' }
            }
          })
        )

        setSessions(enriched)
      } catch (err) {
        console.error('Error loading sessions:', err)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [api])

  const filtered = useMemo(() => {
    return sessions.filter((s) => {
      const matchSearch = !search || s.name.toLowerCase().includes(search.toLowerCase())
      const matchStatus = filterStatus === 'all' || s.status === filterStatus
      return matchSearch && matchStatus
    })
  }, [sessions, search, filterStatus])

  const stats = useMemo(() => {
    const total = sessions.length
    const done = sessions.filter((s) => s.status === 'DONE').length
    const processing = sessions.filter((s) => s.status === 'RUNNING' || s.status === 'QUEUED').length
    const failed = sessions.filter((s) => s.status === 'FAILED').length
    return { total, done, processing, failed }
  }, [sessions])

  return (
    <main className="page page--wide">
      <div className="page-header">
        <div>
          <div className="page-title">Mis experimentos</div>
          <div className="page-subtitle">Panel de control del investigador</div>
        </div>
        <button className="btn-primary btn-primary--sm" onClick={() => navigate('/experiments/new')}>
          <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
            <path d="M6.5 1v11M1 6.5h11" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
          </svg>
          Nuevo experimento
        </button>
      </div>

      <div className="stats-row">
        <StatCard label="Total experimentos" value={stats.total} sub="todos los estados" />
        <StatCard label="Completados" value={stats.done} sub="análisis finalizado" color="#065f46" />
        <StatCard label="En proceso" value={stats.processing} sub="analizando o en cola" color="#1d4ed8" />
        <StatCard label="Con errores" value={stats.failed} sub="requieren atención" color="#991b1b" />
      </div>

      <div className="card">
        <div className="card-header card-header--between">
          <div className="card-title-row">
            <span className="card-title">Experimentos</span>
            <span className="card-count">{filtered.length}</span>
          </div>
          <div className="card-toolbar">
            <div className="search-wrap">
              <span className="search-icon">
                <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
                  <circle cx="5.5" cy="5.5" r="4" stroke="currentColor" strokeWidth="1.2"/>
                  <path d="M9 9l3 3" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round"/>
                </svg>
              </span>
              <input
                className="search-input"
                type="search"
                placeholder="Buscar experimento…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
            <select className="filter-select" value={filterStatus} onChange={(e) => setFilterStatus(e.target.value)}>
              <option value="all">Todos</option>
              <option value="DONE">Completado</option>
              <option value="RUNNING">En proceso</option>
              <option value="QUEUED">Pendiente</option>
              <option value="FAILED">Error</option>
              <option value="NONE">Sin analizar</option>
            </select>
          </div>
        </div>

        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Experimento</th>
                <th>Tratamiento</th>
                <th>Animales</th>
                <th>Videos</th>
                <th>Estado</th>
                <th>Fecha</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan="6" style={{ textAlign: 'center', padding: 32, color: 'var(--c-text-muted)' }}>Cargando experimentos…</td></tr>
              ) : filtered.length === 0 ? (
                <tr><td colSpan="6" style={{ textAlign: 'center', padding: 32, color: 'var(--c-text-muted)' }}>No se encontraron experimentos.</td></tr>
              ) : (
                filtered.map((s) => (
                  <tr key={s.id}>
                    <td>
                      <span
                        className="exp-name"
                        onClick={() => {
                          if (s.status === 'DONE') navigate(`/experiments/${s.id}/results`)
                          else if (s.status === 'RUNNING' || s.status === 'QUEUED') navigate(`/experiments/${s.id}/progress`)
                        }}
                      >
                        {s.name}
                      </span>
                      <div className="exp-id">EXP-{String(s.id).padStart(3, '0')}</div>
                    </td>
                    <td>{s.treatment}</td>
                    <td>{s.animals}</td>
                    <td>{s.videoCount}</td>
                    <td><Badge status={s.status} /></td>
                    <td style={{ fontSize: 13, color: 'var(--c-text-muted)' }}>
                      {new Date(s.created_at).toLocaleDateString('es-MX', { day: '2-digit', month: 'short', year: 'numeric' })}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </main>
  )
}
