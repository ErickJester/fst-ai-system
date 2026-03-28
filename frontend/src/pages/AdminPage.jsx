import React, { useState } from 'react'
import Sidebar from '../components/layout/Sidebar'
import { mockUsers, mockExperiments, mockDisk, mockQueue } from '../data/adminMockData'

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
  }
  const { cls, label } = map[status] || map.QUEUED
  return <span className={`badge ${cls}`}><span className="badge-dot" />{label}</span>
}

/* ── System Status ─────────────────────────────────────────────── */
function SystemPane() {
  const activeUsers = mockUsers.filter((u) => u.active).length
  const totalExp = mockExperiments.length
  const queueCount = mockQueue.pending + mockQueue.processing

  return (
    <div>
      <div className="page-header">
        <div>
          <div className="page-title">Estado del sistema</div>
          <div className="page-subtitle">Recursos, cola de procesamiento y métricas operativas</div>
        </div>
      </div>

      <div className="stats-row">
        <StatCard label="Usuarios activos" value={activeUsers} sub={`${mockUsers.length - activeUsers} inactivo(s)`} />
        <StatCard label="Experimentos totales" value={totalExp} sub="todos los investigadores" />
        <StatCard label="En cola / procesando" value={queueCount} sub={queueCount > 0 ? 'tareas pendientes' : 'sin tareas pendientes'} color="#1d4ed8" />
        <StatCard label="Videos próx. a borrar" value="3" sub="en los próximos 7 días" color="#92400e" />
      </div>

      <div className="card">
        <div className="card-header card-header--between">
          <div className="card-header-left">
            <div className="card-header-icon">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><circle cx="7" cy="7" r="5.5" stroke="#4b6490" strokeWidth="1.1"/><path d="M7 4v4M5 8l2 2 2-2" stroke="#4b6490" strokeWidth="1.1" strokeLinecap="round" strokeLinejoin="round"/></svg>
            </div>
            <div>
              <div className="card-title">Almacenamiento y cola de procesamiento</div>
              <div className="card-subtitle">Los videos se eliminan automáticamente a los 30 días; los resultados se conservan.</div>
            </div>
          </div>
        </div>
        <div className="card-body">
          <div className="disk-panel">
            {/* Disk gauge */}
            <div>
              <div className="gauge-label">
                <span>Espacio en disco</span>
                <span style={{ fontWeight: 800 }}>{mockDisk.usedPct}%</span>
              </div>
              <div className={`gauge-track ${mockDisk.usedPct > 80 ? 'gauge-crit' : mockDisk.usedPct > 60 ? 'gauge-warn' : 'gauge-ok'}`}>
                <div className="gauge-fill" style={{ width: `${mockDisk.usedPct}%` }} />
              </div>
              <div className="gauge-sub">{mockDisk.usedGB} GB de {mockDisk.totalGB} GB utilizados</div>
              <div className="gauge-breakdown">
                <div className="gb-row"><div style={{ display: 'flex', alignItems: 'center', gap: 5 }}><div className="gb-dot" style={{ background: '#3b6fb6' }} /><span>Videos activos</span></div><span style={{ fontWeight: 600 }}>{mockDisk.videosGB} GB</span></div>
                <div className="gb-row"><div style={{ display: 'flex', alignItems: 'center', gap: 5 }}><div className="gb-dot" style={{ background: '#10b981' }} /><span>Resultados / reportes</span></div><span style={{ fontWeight: 600 }}>{mockDisk.resultsGB} GB</span></div>
                <div className="gb-row"><div style={{ display: 'flex', alignItems: 'center', gap: 5 }}><div className="gb-dot" style={{ background: '#e5e7eb' }} /><span>Disponible</span></div><span style={{ fontWeight: 600 }}>{mockDisk.freeGB} GB</span></div>
              </div>
            </div>

            {/* Queue */}
            <div>
              <div className="queue-stat-row">
                <div><div className="q-label">En cola</div><div className="q-val" style={{ color: 'var(--c-proc-text)' }}>{mockQueue.pending}</div></div>
                <div><div className="q-label">Procesando</div><div className="q-val" style={{ color: '#1d4ed8' }}>{mockQueue.processing}</div></div>
                <div><div className="q-label">Completados hoy</div><div className="q-val" style={{ color: 'var(--c-ok-text)' }}>{mockQueue.completedToday}</div></div>
                <div><div className="q-label">Errores hoy</div><div className="q-val" style={{ color: 'var(--c-err-text)' }}>{mockQueue.errorsToday}</div></div>
              </div>
              <div className="queue-items">
                {mockQueue.items.length === 0 ? (
                  <div style={{ fontSize: '12.5px', color: 'var(--c-text-muted)', padding: '12px 0', textAlign: 'center' }}>
                    Sin tareas en cola.
                  </div>
                ) : (
                  mockQueue.items.map((item, i) => (
                    <div key={i} className={`qi-row ${item.active ? 'qi-active' : ''}`}>
                      <span className="qi-order">{i + 1}</span>
                      <span style={{ flex: 1, fontWeight: 600 }}>{item.name}</span>
                      <span style={{ color: 'var(--c-text-muted)', fontSize: 12 }}>{item.user}</span>
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

/* ── Users ─────────────────────────────────────────────────────── */
function UsersPane() {
  const [showModal, setShowModal] = useState(false)
  const [editUser, setEditUser] = useState(null)
  const [search, setSearch] = useState('')

  const filtered = mockUsers.filter((u) =>
    !search || u.name.toLowerCase().includes(search.toLowerCase()) || u.email.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div>
      <div className="page-header">
        <div>
          <div className="page-title">Gestión de usuarios</div>
          <div className="page-subtitle">Crear, modificar y desactivar cuentas de investigadores y administradores.</div>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <div className="search-wrap">
            <span className="search-icon">
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><circle cx="5.5" cy="5.5" r="4" stroke="currentColor" strokeWidth="1.2"/><path d="M9 9l3 3" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round"/></svg>
            </span>
            <input className="search-input" type="search" placeholder="Buscar usuario…" value={search} onChange={(e) => setSearch(e.target.value)} />
          </div>
          <button className="btn-primary btn-primary--sm" onClick={() => { setEditUser(null); setShowModal(true) }}>
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><path d="M6.5 1v11M1 6.5h11" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></svg>
            Crear usuario
          </button>
        </div>
      </div>

      <div className="card">
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Usuario</th>
                <th>Correo</th>
                <th>Rol</th>
                <th>Estado</th>
                <th>Experimentos</th>
                <th>Última actividad</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((u) => (
                <tr key={u.id}>
                  <td style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <div className="user-avatar" style={{ width: 28, height: 28, fontSize: 10, background: '#3b5a8a', color: '#c8d8f0', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, flexShrink: 0 }}>{u.initials}</div>
                    <span style={{ fontWeight: 600 }}>{u.name}</span>
                  </td>
                  <td style={{ color: 'var(--c-text-muted)', fontSize: 13 }}>{u.email}</td>
                  <td>
                    <span className={`role-badge ${u.role === 'admin' ? 'role-adm' : 'role-inv'}`}>
                      {u.role === 'admin' ? 'Admin' : 'Investigador'}
                    </span>
                  </td>
                  <td>
                    <span className={`status-badge ${u.active ? 'sb-active' : 'sb-inactive'}`}>
                      <span className="s-dot" />{u.active ? 'Activo' : 'Inactivo'}
                    </span>
                  </td>
                  <td>{u.experiments}</td>
                  <td style={{ fontSize: 13, color: 'var(--c-text-muted)' }}>{u.lastActive}</td>
                  <td>
                    <div className="row-acts">
                      <button className="icon-btn" title="Editar" onClick={() => { setEditUser(u); setShowModal(true) }}>
                        <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><path d="M9.5 1.5l2 2-7 7H2.5V8.5l7-7z" stroke="currentColor" strokeWidth="1.1" strokeLinejoin="round"/></svg>
                      </button>
                      <button className={`icon-btn ${u.active ? 'deactivate' : 'activate'}`} title={u.active ? 'Desactivar' : 'Activar'}>
                        {u.active ? (
                          <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><circle cx="6.5" cy="6.5" r="5" stroke="currentColor" strokeWidth="1.1"/><path d="M4 4l5 5M9 4l-5 5" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round"/></svg>
                        ) : (
                          <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><circle cx="6.5" cy="6.5" r="5" stroke="currentColor" strokeWidth="1.1"/><path d="M4 6.5l2 2 3-3" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round"/></svg>
                        )}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Modal */}
      {showModal && (
        <div className="overlay" style={{ display: 'flex' }}>
          <div className="modal">
            <div className="modal-header">
              <span className="modal-title">{editUser ? 'Editar usuario' : 'Crear usuario'}</span>
              <button className="modal-close" onClick={() => setShowModal(false)}>
                <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M3 3l8 8M11 3l-8 8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/></svg>
              </button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label className="form-label">Nombre completo <span className="req">*</span></label>
                <input className="form-input form-input--plain" defaultValue={editUser?.name || ''} placeholder="Nombre del usuario" />
              </div>
              <div className="form-group">
                <label className="form-label">Correo electrónico <span className="req">*</span></label>
                <input className="form-input form-input--plain" type="email" defaultValue={editUser?.email || ''} placeholder="usuario@ipn.mx" />
              </div>
              <div className="form-group">
                <label className="form-label">Rol <span className="req">*</span></label>
                <select className="form-select" defaultValue={editUser?.role || 'investigador'}>
                  <option value="investigador">Investigador</option>
                  <option value="admin">Administrador</option>
                </select>
              </div>
              {!editUser && (
                <div className="form-group">
                  <label className="form-label">Contraseña temporal <span className="req">*</span></label>
                  <input className="form-input form-input--plain" type="password" placeholder="Mínimo 8 caracteres" />
                </div>
              )}
            </div>
            <div className="modal-footer">
              <button className="btn-ghost" onClick={() => setShowModal(false)}>Cancelar</button>
              <button className="btn-primary btn-primary--sm" onClick={() => { setShowModal(false); alert('Funcionalidad pendiente de backend') }}>
                {editUser ? 'Guardar cambios' : 'Crear usuario'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

/* ── All Experiments ────────────────────────────────────────────── */
function ExperimentsPane() {
  return (
    <div>
      <div className="page-header">
        <div>
          <div className="page-title">Todos los experimentos</div>
          <div className="page-subtitle">Vista global de todos los experimentos de todos los investigadores.</div>
        </div>
      </div>
      <div className="card">
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Experimento</th>
                <th>Investigador</th>
                <th>Estado</th>
                <th>Videos</th>
                <th>Fecha</th>
              </tr>
            </thead>
            <tbody>
              {mockExperiments.map((exp) => (
                <tr key={exp.id}>
                  <td>
                    <span style={{ fontWeight: 600, color: 'var(--c-link)' }}>{exp.name}</span>
                    <div className="exp-id">EXP-{String(exp.id).padStart(3, '0')}</div>
                  </td>
                  <td>
                    <span style={{ display: 'inline-block', fontSize: '10.5px', background: '#eef2f8', color: '#4b6490', borderRadius: 10, padding: '1px 7px', fontWeight: 600 }}>
                      {exp.owner}
                    </span>
                  </td>
                  <td><Badge status={exp.status} /></td>
                  <td>{exp.videos}</td>
                  <td style={{ fontSize: 13, color: 'var(--c-text-muted)' }}>{exp.date}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

/* ── Admin Page (main) ─────────────────────────────────────────── */
export default function AdminPage() {
  const [activeSection, setActiveSection] = useState('sistema')

  return (
    <>
      <Sidebar activeKey={activeSection} onSelect={setActiveSection} />
      <main className="main">
        {activeSection === 'sistema' && <SystemPane />}
        {activeSection === 'usuarios' && <UsersPane />}
        {activeSection === 'experimentos' && <ExperimentsPane />}
        {activeSection === 'logs' && (
          <div style={{ padding: 48, textAlign: 'center', color: 'var(--c-text-muted)' }}>
            Logs del servidor — funcionalidad pendiente de implementación.
          </div>
        )}
        {activeSection === 'config' && (
          <div style={{ padding: 48, textAlign: 'center', color: 'var(--c-text-muted)' }}>
            Configuración del sistema — funcionalidad pendiente de implementación.
          </div>
        )}
      </main>
    </>
  )
}
