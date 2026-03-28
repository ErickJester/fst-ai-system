import React, { useState, useEffect, useMemo } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useApi } from '../hooks/useApi'

function SummaryCards({ data }) {
  const avg = (key) => {
    if (!data.length) return 0
    return (data.reduce((s, r) => s + r[key], 0) / data.length).toFixed(1)
  }
  return (
    <div className="sum-grid">
      <div className="sum-card sum-swim">
        <div className="sum-label">Nado activo promedio</div>
        <div className="sum-val">{avg('swim_s')} s</div>
        <div className="sum-avg">{((avg('swim_s') / 300) * 100).toFixed(1)}% del total</div>
      </div>
      <div className="sum-card sum-imm">
        <div className="sum-label">Inmovilidad promedio</div>
        <div className="sum-val">{avg('immobile_s')} s</div>
        <div className="sum-avg">{((avg('immobile_s') / 300) * 100).toFixed(1)}% del total</div>
      </div>
      <div className="sum-card sum-esc">
        <div className="sum-label">Escape promedio</div>
        <div className="sum-val">{avg('escape_s')} s</div>
        <div className="sum-avg">{((avg('escape_s') / 300) * 100).toFixed(1)}% del total</div>
      </div>
    </div>
  )
}

function AnimalBars({ data }) {
  return (
    <div className="animal-bars">
      {data.map((r) => {
        const total = r.swim_s + r.immobile_s + r.escape_s || 1
        return (
          <div key={r.rat_idx} className="abar-row">
            <span className="abar-label">Rata {r.rat_idx + 1}</span>
            <div className="abar-track">
              <div className="abar-swim" style={{ width: `${(r.swim_s / total) * 100}%` }} />
              <div className="abar-imm" style={{ width: `${(r.immobile_s / total) * 100}%` }} />
              <div className="abar-esc" style={{ width: `${(r.escape_s / total) * 100}%` }} />
            </div>
            <span className="abar-total">{total.toFixed(0)} s</span>
          </div>
        )
      })}
    </div>
  )
}

function ResultsTable({ data }) {
  const totalRow = useMemo(() => {
    if (!data.length) return null
    const n = data.length
    const swim = data.reduce((s, r) => s + r.swim_s, 0)
    const imm = data.reduce((s, r) => s + r.immobile_s, 0)
    const esc = data.reduce((s, r) => s + r.escape_s, 0)
    const total = swim + imm + esc
    return { swim: swim / n, imm: imm / n, esc: esc / n, total: total / n }
  }, [data])

  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Animal</th>
            <th className="th-swim">Nado (s)</th>
            <th className="th-swim">Nado (%)</th>
            <th className="th-imm">Inmovilidad (s)</th>
            <th className="th-imm">Inmovilidad (%)</th>
            <th className="th-esc">Escape (s)</th>
            <th className="th-esc">Escape (%)</th>
            <th>Total (s)</th>
          </tr>
        </thead>
        <tbody>
          {data.map((r) => {
            const total = r.swim_s + r.immobile_s + r.escape_s || 1
            return (
              <tr key={r.rat_idx}>
                <td className="rat-name">Rata {r.rat_idx + 1}</td>
                <td className="val-swim">{r.swim_s.toFixed(2)}</td>
                <td className="val-swim">{((r.swim_s / total) * 100).toFixed(1)}%</td>
                <td className="val-imm">{r.immobile_s.toFixed(2)}</td>
                <td className="val-imm">{((r.immobile_s / total) * 100).toFixed(1)}%</td>
                <td className="val-esc">{r.escape_s.toFixed(2)}</td>
                <td className="val-esc">{((r.escape_s / total) * 100).toFixed(1)}%</td>
                <td className="val-tot">{total.toFixed(2)}</td>
              </tr>
            )
          })}
          {totalRow && (
            <tr className="total-row">
              <td className="rat-name">Promedio</td>
              <td className="val-swim">{totalRow.swim.toFixed(2)}</td>
              <td className="val-swim">{((totalRow.swim / totalRow.total) * 100).toFixed(1)}%</td>
              <td className="val-imm">{totalRow.imm.toFixed(2)}</td>
              <td className="val-imm">{((totalRow.imm / totalRow.total) * 100).toFixed(1)}%</td>
              <td className="val-esc">{totalRow.esc.toFixed(2)}</td>
              <td className="val-esc">{((totalRow.esc / totalRow.total) * 100).toFixed(1)}%</td>
              <td className="val-tot">{totalRow.total.toFixed(2)}</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}

export default function ResultsPage() {
  const { id } = useParams()
  const api = useApi()
  const [session, setSession] = useState(null)
  const [summary, setSummary] = useState([])
  const [activeTab, setActiveTab] = useState('resumen')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function load() {
      try {
        const [sessRes, vidRes] = await Promise.all([
          api.get('/api/sessions'),
          api.get(`/api/sessions/${id}/videos`),
        ])
        const s = sessRes.data.find((x) => x.id === parseInt(id))
        setSession(s || { id, name: `Experimento ${id}` })

        const videos = vidRes.data
        if (videos.length > 0) {
          const jobRes = await api.post('/api/jobs', { video_id: videos[0].id })
          if (jobRes.data.job_id) {
            const sumRes = await api.get(`/api/jobs/${jobRes.data.job_id}/summary`)
            setSummary(sumRes.data)
          }
        }
      } catch (err) {
        console.error(err)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [api, id])

  let notes = {}
  try { notes = session?.notes ? JSON.parse(session.notes) : {} } catch {}

  const tabs = [
    { key: 'resumen', label: 'Resumen' },
    { key: 'detalle', label: 'Tabla detallada' },
    { key: 'minutos', label: 'Por minuto' },
    { key: 'comparacion', label: 'Comparación Día 1 vs 2' },
  ]

  return (
    <main className="page page--medium">
      <nav className="breadcrumb">
        <Link to="/dashboard">Mis experimentos</Link>
        <span className="breadcrumb-sep">›</span>
        <span>{session?.name || '…'}</span>
        <span className="breadcrumb-sep">›</span>
        <span>Resultados</span>
      </nav>

      <div className="page-header">
        <div>
          <div className="page-title">Resultados — {session?.name || '…'}</div>
          <div className="page-subtitle">
            {notes.treatment || ''} · {notes.animals || '?'} animales
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
          <button className="btn-export pdf" onClick={() => alert('Descarga de PDF en desarrollo')}>
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M3 2h5.5L11 4.5V12H3V2z" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round"/><path d="M8 2v3h3" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round"/></svg>
            Descargar PDF
          </button>
          <button className="btn-export csv" onClick={() => alert('Descarga de CSV en desarrollo')}>
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><rect x="1.5" y="1.5" width="11" height="11" rx="1.5" stroke="currentColor" strokeWidth="1.1"/><path d="M1.5 5h11M1.5 8.5h11M5 5v7" stroke="currentColor" strokeWidth="1.1"/></svg>
            Descargar CSV
          </button>
        </div>
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
      ) : summary.length === 0 ? (
        <div style={{ textAlign: 'center', padding: 48, color: 'var(--c-text-muted)' }}>No hay resultados disponibles para este experimento.</div>
      ) : (
        <>
          {/* Tab: Resumen */}
          {activeTab === 'resumen' && (
            <div className="card">
              <div className="card-header card-header--between">
                <div className="card-header-left">
                  <div className="card-header-icon">
                    <svg width="15" height="15" viewBox="0 0 15 15" fill="none"><rect x="1.5" y="3" width="12" height="9" rx="1.5" stroke="#4b6490" strokeWidth="1.1"/><path d="M4.5 3V2M10.5 3V2M1.5 6.5h12" stroke="#4b6490" strokeWidth="1.1" strokeLinecap="round"/></svg>
                  </div>
                  <div>
                    <div className="card-title">Resumen por sesión</div>
                    <div className="card-subtitle">Sesión post-tratamiento (5 min / 300 s)</div>
                  </div>
                </div>
                <div className="legend">
                  <span className="leg leg-swim"><span className="leg-dot" style={{ background: 'var(--b-swim)' }} />Nado activo</span>
                  <span className="leg leg-imm"><span className="leg-dot" style={{ background: 'var(--b-imm)' }} />Inmovilidad</span>
                  <span className="leg leg-esc"><span className="leg-dot" style={{ background: 'var(--b-esc)' }} />Escape</span>
                </div>
              </div>
              <div className="card-body">
                <SummaryCards data={summary} />
                <AnimalBars data={summary} />
                <div className="rn07-note" style={{ marginTop: 14 }}>
                  <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><circle cx="6.5" cy="6.5" r="5.5" stroke="#9ca3af" strokeWidth="1.1"/><path d="M6.5 4.5v3.5" stroke="#9ca3af" strokeWidth="1.2" strokeLinecap="round"/><circle cx="6.5" cy="9.5" r=".6" fill="#9ca3af"/></svg>
                  Las tres conductas son mutuamente excluyentes por frame — la suma por animal es siempre igual a la duración de la sesión (RN07).
                </div>
              </div>
            </div>
          )}

          {/* Tab: Tabla detallada */}
          {activeTab === 'detalle' && (
            <div className="card">
              <div className="card-header card-header--between">
                <div className="card-header-left">
                  <div className="card-header-icon">
                    <svg width="15" height="15" viewBox="0 0 15 15" fill="none"><rect x="1.5" y="1.5" width="12" height="12" rx="1.5" stroke="#4b6490" strokeWidth="1.1"/><path d="M1.5 5.5h12M1.5 9h12M5.5 1.5v12" stroke="#4b6490" strokeWidth="1.1"/></svg>
                  </div>
                  <div>
                    <div className="card-title">Tabla de resultados por animal y sesión</div>
                    <div className="card-subtitle">Tiempo en segundos · conductas mutuamente excluyentes</div>
                  </div>
                </div>
              </div>
              <ResultsTable data={summary} />
            </div>
          )}

          {/* Tab: Por minuto */}
          {activeTab === 'minutos' && (
            <div className="card">
              <div className="card-header">
                <div className="card-header-icon">
                  <svg width="15" height="15" viewBox="0 0 15 15" fill="none"><circle cx="7.5" cy="7.5" r="6" stroke="#4b6490" strokeWidth="1.1"/><path d="M7.5 4.5v3.5l2 2" stroke="#4b6490" strokeWidth="1.1" strokeLinecap="round" strokeLinejoin="round"/></svg>
                </div>
                <div>
                  <div className="card-title">Desglose por minuto</div>
                  <div className="card-subtitle">Disponible cuando el pipeline reporte datos por minuto</div>
                </div>
              </div>
              <div className="card-body" style={{ textAlign: 'center', padding: 40, color: 'var(--c-text-muted)' }}>
                El desglose por minuto estará disponible cuando el backend implemente el reporte minuto a minuto.
              </div>
            </div>
          )}

          {/* Tab: Comparación */}
          {activeTab === 'comparacion' && (
            <div>
              <div className="one-session-notice">
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none" style={{ flexShrink: 0, marginTop: 1 }}><path d="M8 1.5L1 6v5l7 3.5 7-3.5V6L8 1.5z" stroke="#d97706" strokeWidth="1.2" strokeLinejoin="round"/></svg>
                <span>La comparación Día 1 vs Día 2 estará disponible cuando ambos videos hayan sido analizados y el backend soporte esta funcionalidad.</span>
              </div>
            </div>
          )}
        </>
      )}
    </main>
  )
}
