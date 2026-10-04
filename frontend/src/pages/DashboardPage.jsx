import React, { useState, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import DemoBar from '../components/DemoBar'
import '../styles/pages/dashboard.css'

// Datos de ejemplo del mockup v2 (sin backend todavía)
const EXPERIMENTOS = [
  { id: 35, codigo: 'FST-2024-035', nombre: 'Ketamina sub-anestésica', fecha: '18 mar 2025', tratamiento: 'Ketamina 15 mg/kg', sesion: 'Día 2 (5 min)', estado: 'proceso' },
  { id: 34, codigo: 'FST-2024-034', nombre: 'Sertralina 20 mg/kg', fecha: '15 mar 2025', tratamiento: 'Sertralina 20 mg/kg', sesion: 'Día 1 (20 min)', estado: 'proceso' },
  { id: 33, codigo: 'FST-2024-033', nombre: 'Fluoxetina 20 mg/kg', fecha: '10 mar 2025', tratamiento: 'Fluoxetina 20 mg/kg', sesion: 'Día 2 (5 min)', estado: 'completado' },
  { id: 32, codigo: 'FST-2024-032', nombre: 'Imipramina 15 mg/kg', fecha: '5 mar 2025', tratamiento: 'Imipramina 15 mg/kg', sesion: 'Día 2 (5 min)', estado: 'error' },
  { id: 31, codigo: 'FST-2024-031', nombre: 'Grupo control Abr-2', fecha: '20 feb 2025', tratamiento: 'Vehículo (control)', sesion: 'Día 2 (5 min)', estado: 'completado', expiraEn: '3 días' },
  { id: 30, codigo: 'FST-2024-030', nombre: 'Fluoxetina 10 mg/kg', fecha: '12 feb 2025', tratamiento: 'Fluoxetina 10 mg/kg', sesion: 'Día 1 y 2', estado: 'completado' },
]

const ESTADOS = {
  proceso: { cls: 'badge-proceso', label: 'En proceso' },
  completado: { cls: 'badge-completado', label: 'Completado' },
  error: { cls: 'badge-error', label: 'Error' },
}

const DEMO_STATES = [
  { key: 'normal', label: 'Normal' },
  { key: 'warn', label: 'Con alerta de vencimiento' },
  { key: 'empty', label: 'Sin experimentos' },
]

const IconoVer = () => (
  <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M1 7s2.3-5 6-5 6 5 6 5-2.3 5-6 5-6-5-6-5z" stroke="currentColor" strokeWidth="1.2"/><circle cx="7" cy="7" r="2" stroke="currentColor" strokeWidth="1.2"/></svg>
)
const IconoDescargar = () => (
  <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M7 1v8M4 6.5L7 9.5l3-3" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round"/><path d="M1.5 10.5v1.5a1 1 0 001 1h9a1 1 0 001-1v-1.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round"/></svg>
)
const IconoError = () => (
  <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><circle cx="7" cy="7" r="6" stroke="currentColor" strokeWidth="1.2"/><path d="M7 4.5v3.5" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round"/><circle cx="7" cy="10" r=".7" fill="currentColor"/></svg>
)
const IconoMas = () => (
  <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M7 1v12M1 7h12" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></svg>
)

export default function DashboardPage() {
  const navigate = useNavigate()
  const [demo, setDemo] = useState('normal')
  const [warnOpen, setWarnOpen] = useState(true)
  const [search, setSearch] = useState('')
  const [filtro, setFiltro] = useState('todos')

  const experimentos = demo === 'empty' ? [] : EXPERIMENTOS
  const filtrados = useMemo(() => experimentos.filter((e) => {
    const texto = `${e.codigo} ${e.nombre}`.toLowerCase()
    return (!search || texto.includes(search.toLowerCase())) && (filtro === 'todos' || e.estado === filtro)
  }), [experimentos, search, filtro])

  const cambiarDemo = (s) => { setDemo(s); setWarnOpen(true) }
  const abrir = (e) => navigate(e.estado === 'completado' ? `/experiments/${e.id}/results` : `/experiments/${e.id}/progress`)

  return (
    <div className="pg-dashboard">
      <DemoBar states={DEMO_STATES} value={demo} onChange={cambiarDemo} />

      <main className="page">
        <div className="page-header">
          <div>
            <div className="page-title">Mis experimentos</div>
            <div className="page-subtitle">Visualiza y gestiona los experimentos del laboratorio.</div>
          </div>
          <button className="btn-primary" onClick={() => navigate('/experiments/new')}>
            <IconoMas />
            Nuevo experimento
          </button>
        </div>

        {demo === 'warn' && warnOpen && (
          <div className="warn-banner" style={{ display: 'flex' }}>
            <div className="warn-banner-icon">
              <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
                <path d="M9 1.5L16.5 15H1.5L9 1.5z" stroke="#d97706" strokeWidth="1.4" strokeLinejoin="round"/>
                <path d="M9 7v4" stroke="#d97706" strokeWidth="1.5" strokeLinecap="round"/>
                <circle cx="9" cy="12.5" r=".75" fill="#d97706"/>
              </svg>
            </div>
            <div className="warn-banner-body">
              <div className="warn-banner-title">Videos próximos a eliminarse</div>
              <div className="warn-banner-text">
                Los siguientes experimentos tienen videos que serán borrados automáticamente en los próximos días.
                Los resultados y reportes se conservarán, pero no podrás re-analizar el video original.
              </div>
              <ul className="warn-banner-list">
                <li>FST-2024-031 — "Grupo control Abr-2" · expira en <strong>3 días</strong></li>
                <li>FST-2024-028 — "Fluoxetina 10 mg/kg" · expira en <strong>8 días</strong></li>
              </ul>
            </div>
            <button className="warn-close" title="Cerrar" onClick={() => setWarnOpen(false)}>
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M2 2l10 10M12 2L2 12" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
              </svg>
            </button>
          </div>
        )}

        <div className="stats-row">
          <div className="stat-card">
            <div className="stat-label">Total experimentos</div>
            <div className="stat-value">{demo === 'empty' ? 0 : 14}</div>
            <div className="stat-sub">últimos 12 meses</div>
          </div>
          <div className="stat-card">
            <div className="stat-label">Completados</div>
            <div className="stat-value" style={{ color: 'var(--c-ok-text)' }}>{demo === 'empty' ? 0 : 11}</div>
            <div className="stat-sub">con resultados disponibles</div>
          </div>
          <div className="stat-card">
            <div className="stat-label">En proceso</div>
            <div className="stat-value" style={{ color: 'var(--c-proc-text)' }}>{demo === 'empty' ? 0 : 2}</div>
            <div className="stat-sub">análisis en cola</div>
          </div>
          <div className="stat-card">
            <div className="stat-label">Con error</div>
            <div className="stat-value" style={{ color: 'var(--c-err-text)' }}>{demo === 'empty' ? 0 : 1}</div>
            <div className="stat-sub">requieren atención</div>
          </div>
        </div>

        <div className="card">
          <div className="card-header">
            <div className="card-title-row">
              <span className="card-title">Lista de experimentos</span>
              <span className="card-count">{demo === 'empty' ? 0 : 14} experimentos</span>
            </div>
            <div className="card-toolbar">
              <div className="search-wrap">
                <span className="search-icon">
                  <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                    <circle cx="6" cy="6" r="4.5" stroke="currentColor" strokeWidth="1.3"/>
                    <path d="M9.5 9.5l3 3" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
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
              <select className="filter-select" title="Filtrar por estado" value={filtro} onChange={(e) => setFiltro(e.target.value)}>
                <option value="todos">Todos los estados</option>
                <option value="completado">Completado</option>
                <option value="proceso">En proceso</option>
                <option value="error">Error</option>
              </select>
            </div>
          </div>

          {experimentos.length === 0 ? (
            <div className="empty-state" style={{ display: 'block' }}>
              <div className="empty-state-icon">
                <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
                  <rect x="8" y="12" width="32" height="28" rx="3" stroke="#9ca3af" strokeWidth="1.5"/>
                  <path d="M16 20h16M16 26h10" stroke="#9ca3af" strokeWidth="1.5" strokeLinecap="round"/>
                  <path d="M24 6v6M20 8l4-2 4 2" stroke="#9ca3af" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </div>
              <div className="empty-state-title">Aún no hay experimentos</div>
              <div className="empty-state-text">Crea el primer experimento subiendo un video del FST. El análisis comenzará automáticamente.</div>
              <button className="btn-primary" style={{ margin: '0 auto' }} onClick={() => navigate('/experiments/new')}>
                <IconoMas />
                Nuevo experimento
              </button>
            </div>
          ) : (
            <>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th className="sortable">Nombre del experimento <span className="sort-icon">↕</span></th>
                      <th className="sortable">Fecha <span className="sort-icon">↓</span></th>
                      <th>Tratamiento</th>
                      <th>Sesión</th>
                      <th>Estado</th>
                      <th>Video</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {filtrados.map((e) => (
                      <tr key={e.id}>
                        <td>
                          <a className="exp-name" href="#" onClick={(ev) => { ev.preventDefault(); abrir(e) }}>
                            {e.codigo} — {e.nombre}
                          </a>
                          <div className="exp-id">#EXP-{String(e.id).padStart(3, '0')}</div>
                        </td>
                        <td>{e.fecha}</td>
                        <td>{e.tratamiento}</td>
                        <td>{e.sesion}</td>
                        <td>
                          <span className={`badge ${ESTADOS[e.estado].cls}`}>
                            <span className="badge-dot"></span>{ESTADOS[e.estado].label}
                          </span>
                        </td>
                        <td style={{ fontSize: 12 }}>
                          <span style={{ color: 'var(--c-text-muted)' }}>Disponible</span>
                          {demo === 'warn' && e.expiraEn && (
                            <span className="expiry-chip" style={{ display: 'inline-flex' }}>
                              <svg width="10" height="10" viewBox="0 0 10 10" fill="none"><circle cx="5" cy="5" r="4" stroke="currentColor" strokeWidth="1"/><path d="M5 3v2.5l1.5 1" stroke="currentColor" strokeWidth="1" strokeLinecap="round"/></svg>
                              {e.expiraEn}
                            </span>
                          )}
                        </td>
                        <td>
                          <div className="row-actions">
                            {e.estado === 'error' ? (
                              <button className="icon-btn" title="Ver detalle del error" onClick={() => abrir(e)}><IconoError /></button>
                            ) : (
                              <button className="icon-btn" title="Ver resultados" onClick={() => abrir(e)}><IconoVer /></button>
                            )}
                            {e.estado === 'completado' && (
                              <button className="icon-btn" title="Descargar reporte (PDF/CSV)"><IconoDescargar /></button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="table-footer">
                <span className="table-info">Mostrando {filtrados.length} de 14 experimentos</span>
                <div className="pagination">
                  <button className="page-btn" disabled>‹</button>
                  <button className="page-btn active">1</button>
                  <button className="page-btn">2</button>
                  <button className="page-btn">3</button>
                  <button className="page-btn">›</button>
                </div>
              </div>
            </>
          )}
        </div>

        <div className="privacy-note">
          <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
            <path d="M6.5 1L1.5 3v3.5c0 3 2.5 5 5 5.5 2.5-.5 5-2.5 5-5.5V3L6.5 1z" stroke="#9ca3af" strokeWidth="1.1" strokeLinejoin="round"/>
            <path d="M4 6.5l2 2 3-3" stroke="#9ca3af" strokeWidth="1.1" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          Puedes ver los experimentos de todo el laboratorio.
        </div>
      </main>
    </div>
  )
}
