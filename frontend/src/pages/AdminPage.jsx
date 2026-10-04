import React, { useState } from 'react'
import DemoBar from '../components/DemoBar'
import '../styles/pages/admin.css'

const DEMO_STATES = [
  { key: 'normal', label: 'Normal' },
  { key: 'diskwarn', label: 'Alerta disco' },
  { key: 'queue', label: 'Cola activa' },
  { key: 'modal_new', label: 'Modal — Crear usuario' },
  { key: 'modal_edit', label: 'Modal — Editar usuario' },
]

// Datos de ejemplo del mockup v2
const USERS = [
  { id: 1, name: 'M. Sánchez', email: 'm.sanchez@ipn.mx', role: 'inv', active: true, exps: 14, activity: [5, 8, 3, 6, 9, 4, 7] },
  { id: 2, name: 'L. Torres', email: 'l.torres@ipn.mx', role: 'inv', active: true, exps: 12, activity: [3, 5, 6, 4, 8, 5, 6] },
  { id: 3, name: 'R. Castro', email: 'r.castro@ipn.mx', role: 'inv', active: true, exps: 9, activity: [2, 3, 1, 4, 2, 3, 1] },
  { id: 4, name: 'P. Vega', email: 'p.vega@ipn.mx', role: 'inv', active: true, exps: 7, activity: [1, 2, 3, 1, 2, 4, 1] },
  { id: 5, name: 'C. Morales', email: 'c.morales@ipn.mx', role: 'inv', active: true, exps: 0, activity: [0, 0, 0, 0, 0, 0, 0] },
  { id: 6, name: 'J. Herrera', email: 'j.herrera@ipn.mx', role: 'inv', active: false, exps: 0, activity: [0, 0, 0, 0, 0, 0, 0] },
  { id: 7, name: 'A. Ramírez', email: 'a.ramirez@ipn.mx', role: 'adm', active: true, exps: '-', activity: [2, 1, 2, 1, 2, 1, 2] },
  { id: 8, name: 'I. Salinas', email: 'i.salinas@ipn.mx', role: 'adm', active: true, exps: '-', activity: [1, 0, 1, 0, 1, 0, 1] },
]

const ALL_EXPS = [
  { id: 'FST-2024-035', owner: 'M. Sánchez', date: '18 mar 2025', trat: 'Ketamina 15 mg/kg', status: 'proc' },
  { id: 'FST-2024-034', owner: 'M. Sánchez', date: '15 mar 2025', trat: 'Sertralina 20 mg/kg', status: 'proc' },
  { id: 'FST-2024-033', owner: 'M. Sánchez', date: '10 mar 2025', trat: 'Fluoxetina 20 mg/kg', status: 'done' },
  { id: 'FST-2024-031', owner: 'M. Sánchez', date: '20 feb 2025', trat: 'Vehículo (control)', status: 'done' },
  { id: 'FST-2024-022', owner: 'L. Torres', date: '12 mar 2025', trat: 'Imipramina 30 mg/kg', status: 'done' },
  { id: 'FST-2024-021', owner: 'L. Torres', date: '5 mar 2025', trat: 'Control salino', status: 'err' },
  { id: 'FST-2024-015', owner: 'R. Castro', date: '1 mar 2025', trat: 'Escitalopram 5 mg', status: 'done' },
  { id: 'FST-2024-009', owner: 'P. Vega', date: '18 feb 2025', trat: 'Duloxetina 10 mg/kg', status: 'done' },
]

const DISK = {
  normal: { used: 38, vidGB: '31.4 GB', free: '61.6 GB', cls: 'gauge-ok', lbl: '38%' },
  diskwarn: { used: 81, vidGB: '67.2 GB', free: '18.8 GB', cls: 'gauge-warn', lbl: '81%' },
}

const QUEUE_DATA = [
  { pos: 1, name: 'FST-2024-036 — Ketamina 30 mg/kg', user: 'M. Sánchez', day: 'Día 2', eta: '~4 min', active: true },
  { pos: 2, name: 'FST-2024-022 — Imipramina Día 1', user: 'L. Torres', day: 'Día 1', eta: '~22 min', active: false },
  { pos: 3, name: 'FST-2024-015 — Escitalopram', user: 'R. Castro', day: 'Día 2', eta: '~31 min', active: false },
]

const IconoCheck = () => (
  <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><path d="M2 7l3.5 3.5 5.5-5.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>
)
const IconoBuscar = () => (
  <span className="s-icon"><svg width="13" height="13" viewBox="0 0 13 13" fill="none"><circle cx="5.5" cy="5.5" r="4" stroke="currentColor" strokeWidth="1.2"/><path d="M9 9l3 3" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round"/></svg></span>
)

function CardHeader({ icon, title, sub, children }) {
  return (
    <div className="card-header">
      <div className="ch-left">
        <div className="ch-icon">{icon}</div>
        <div><div className="card-title">{title}</div><div className="card-sub">{sub}</div></div>
      </div>
      {children}
    </div>
  )
}

function Sistema({ demo }) {
  const d = DISK[demo === 'diskwarn' ? 'diskwarn' : 'normal']
  const cola = demo === 'queue'
  return (
    <div>
      <div className="page-header">
        <div>
          <div className="page-title">Estado del sistema</div>
          <div className="page-sub">Recursos, cola de procesamiento y métricas operativas</div>
        </div>
      </div>

      <div className="stat-grid">
        <div className="stat-card">
          <div className="stat-label">Usuarios activos</div>
          <div className="stat-val">7</div>
          <div className="stat-sub">1 inactivo</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">Experimentos totales</div>
          <div className="stat-val">42</div>
          <div className="stat-sub">todos los investigadores</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">En cola / procesando</div>
          <div className="stat-val" style={{ color: '#1d4ed8' }}>{cola ? 3 : 0}</div>
          <div className="stat-sub">{cola ? '1 procesando · 2 en espera' : 'sin tareas pendientes'}</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">Videos próx. a borrar</div>
          <div className="stat-val" style={{ color: '#92400e' }}>3</div>
          <div className="stat-sub">en los próximos 7 días</div>
        </div>
      </div>

      <div className="card">
        <CardHeader
          icon={<svg width="14" height="14" viewBox="0 0 14 14" fill="none"><circle cx="7" cy="7" r="5.5" stroke="#4b6490" strokeWidth="1.1"/><path d="M7 4v4M5 8l2 2 2-2" stroke="#4b6490" strokeWidth="1.1" strokeLinecap="round" strokeLinejoin="round"/></svg>}
          title="Almacenamiento y cola de procesamiento"
          sub="Los videos se eliminan automáticamente a los 30 días; los resultados se conservan."
        />
        <div className="card-body">
          <div className="disk-panel">
            <div className={`gauge-wrap ${d.cls}`}>
              <div className="gauge-label">
                <span>Espacio en disco</span>
                <span style={{ fontWeight: '800' }}>{d.lbl}</span>
              </div>
              <div className="gauge-track">
                <div className="gauge-fill" style={{ width: `${d.used}%` }}></div>
              </div>
              <div className="gauge-sub">{d.used}% usado de 100 GB — {d.free} disponibles</div>
              <div className="gauge-breakdown">
                <div className="gb-row"><div style={{ display: 'flex', alignItems: 'center', gap: '5px' }}><div className="gb-dot" style={{ background: '#3b6fb6' }}></div><span>Videos activos</span></div><span style={{ fontWeight: '600' }}>{d.vidGB}</span></div>
                <div className="gb-row"><div style={{ display: 'flex', alignItems: 'center', gap: '5px' }}><div className="gb-dot" style={{ background: '#10b981' }}></div><span>Resultados / reportes</span></div><span style={{ fontWeight: '600' }}>2.1 GB</span></div>
                <div className="gb-row"><div style={{ display: 'flex', alignItems: 'center', gap: '5px' }}><div className="gb-dot" style={{ background: '#e5e7eb' }}></div><span>Disponible</span></div><span style={{ fontWeight: '600' }}>{d.free}</span></div>
              </div>
            </div>

            <div className="queue-panel">
              <div className="queue-stat-row">
                <div className="q-stat"><div className="q-label">En cola</div><div className="q-val" style={{ color: 'var(--c-proc-txt)' }}>{cola ? 2 : 0}</div></div>
                <div className="q-stat"><div className="q-label">Procesando</div><div className="q-val" style={{ color: '#1d4ed8' }}>{cola ? 1 : 0}</div></div>
                <div className="q-stat"><div className="q-label">Completados hoy</div><div className="q-val" style={{ color: 'var(--c-ok-txt)' }}>5</div></div>
                <div className="q-stat"><div className="q-label">Errores hoy</div><div className="q-val" style={{ color: 'var(--c-err-txt)' }}>1</div></div>
              </div>
              <div className="queue-items">
                {!cola ? (
                  <div style={{ fontSize: '12.5px', color: 'var(--c-muted)', padding: '12px 0', textAlign: 'center' }}>Sin tareas en cola.</div>
                ) : QUEUE_DATA.map((q) => (
                  <div key={q.pos} className={`qi-row ${q.active ? 'qi-active' : ''}`}>
                    <div className="qi-order">{q.pos}</div>
                    <div>
                      <div className="qi-name">{q.name}</div>
                      <div className="qi-user">{q.user} · {q.day}</div>
                    </div>
                    {q.active ? (
                      <div style={{ display: 'flex', alignItems: 'center', gap: '5px', marginLeft: 'auto' }}>
                        <svg width="12" height="12" viewBox="0 0 12 12" fill="none"><circle cx="6" cy="6" r="5" stroke="#93c5fd" strokeWidth="1.1"/><path d="M6 6l0-3" stroke="#3b82f6" strokeWidth="1.2" strokeLinecap="round"><animateTransform attributeName="transform" type="rotate" from="0 6 6" to="360 6 6" dur=".8s" repeatCount="indefinite"/></path></svg>
                        <span style={{ fontSize: '11.5px', color: 'var(--c-proc-txt)', fontWeight: 600 }}>Procesando…</span>
                      </div>
                    ) : (
                      <div className="qi-eta" style={{ marginLeft: 'auto' }}>{q.eta}</div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

function Usuarios({ onNew, onEdit }) {
  const [busqueda, setBusqueda] = useState('')
  const [rol, setRol] = useState('')
  const [estado, setEstado] = useState('')
  const filtrados = USERS.filter((u) =>
    (!busqueda || `${u.name} ${u.email}`.toLowerCase().includes(busqueda.toLowerCase())) &&
    (!rol || u.role === rol) &&
    (!estado || (estado === 'active') === u.active),
  )
  return (
    <div>
      <div className="page-header">
        <div>
          <div className="page-title">Gestión de usuarios</div>
          <div className="page-sub">Crear, modificar y desactivar cuentas de investigadores y administradores.</div>
        </div>
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          <div className="search-wrap">
            <IconoBuscar />
            <input className="search-input" type="search" placeholder="Buscar usuario…" value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
          </div>
          <button className="btn-primary" onClick={onNew}>
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><path d="M6.5 1v11M1 6.5h11" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></svg>
            Crear usuario
          </button>
        </div>
      </div>

      <div className="card">
        <CardHeader
          icon={<svg width="14" height="14" viewBox="0 0 14 14" fill="none"><circle cx="5" cy="4.5" r="2.5" stroke="#4b6490" strokeWidth="1.1"/><path d="M1 12c0-2.5 1.8-4 4-4s4 1.5 4 4" stroke="#4b6490" strokeWidth="1.1" strokeLinecap="round"/><circle cx="10.5" cy="4.5" r="2" stroke="#4b6490" strokeWidth="1.1"/><path d="M12.5 12c0-2-1.2-3-2.5-3" stroke="#4b6490" strokeWidth="1.1" strokeLinecap="round"/></svg>}
          title="Usuarios registrados"
          sub="Actividad y estado de todas las cuentas del sistema."
        >
          <div style={{ display: 'flex', gap: '6px' }}>
            <select className="form-select" style={{ height: '30px', fontSize: '12px', padding: '0 24px 0 8px' }} value={rol} onChange={(e) => setRol(e.target.value)}>
              <option value="">Todos los roles</option><option value="inv">Investigador</option><option value="adm">Administrador</option>
            </select>
            <select className="form-select" style={{ height: '30px', fontSize: '12px', padding: '0 24px 0 8px' }} value={estado} onChange={(e) => setEstado(e.target.value)}>
              <option value="">Todos los estados</option><option value="active">Activo</option><option value="inactive">Inactivo</option>
            </select>
          </div>
        </CardHeader>
        <div className="tbl-wrap">
          <table>
            <thead>
              <tr>
                <th className="sortable">Usuario ↕</th>
                <th>Correo</th>
                <th>Rol</th>
                <th>Estado</th>
                <th className="sortable">Experimentos ↕</th>
                <th>Actividad 30 días</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {filtrados.map((u) => {
                const maxAct = Math.max(...u.activity, 1)
                return (
                  <tr key={u.id} style={u.active ? undefined : { opacity: 0.65 }}>
                    <td style={{ fontWeight: 600 }}>{u.name}</td>
                    <td style={{ fontSize: '12.5px', color: 'var(--c-muted)' }}>{u.email}</td>
                    <td>
                      {u.role === 'adm'
                        ? <span className="role-badge role-adm">Admin</span>
                        : <span className="role-badge role-inv">Investigador</span>}
                    </td>
                    <td>
                      {u.active
                        ? <span className="status-badge sb-active"><span className="s-dot"></span>Activo</span>
                        : <span className="status-badge sb-inactive"><span className="s-dot"></span>Inactivo</span>}
                    </td>
                    <td>
                      {u.exps === '-' ? '—' : (
                        <span className="exp-count-badge">
                          <svg width="12" height="12" viewBox="0 0 12 12" fill="none"><path d="M3 1v4.5L1 10a.75.75 0 00.7 1.1h8.6A.75.75 0 0011 10L9 5.5V1" stroke="currentColor" strokeWidth="1"/><path d="M3 1h6" stroke="currentColor" strokeWidth="1" strokeLinecap="round"/></svg>
                          {u.exps}
                        </span>
                      )}
                    </td>
                    <td style={{ minWidth: '90px' }}>
                      {u.activity.map((v, i) => (
                        <React.Fragment key={i}>
                          <span className="act-bar" style={{ width: `${Math.round((v / maxAct) * 18) + 4}px`, opacity: u.active ? 1 : 0.35 }}></span>{' '}
                        </React.Fragment>
                      ))}
                    </td>
                    <td>
                      <div className="row-acts">
                        <button className="icon-btn" title="Editar usuario" onClick={() => onEdit(u)}>
                          <svg width="12" height="12" viewBox="0 0 12 12" fill="none"><path d="M8.5 1.5l2 2-7 7H1.5v-2l7-7z" stroke="currentColor" strokeWidth="1.1" strokeLinejoin="round"/></svg>
                        </button>
                        {u.active ? (
                          <button className="icon-btn deactivate" title="Desactivar cuenta">
                            <svg width="12" height="12" viewBox="0 0 12 12" fill="none"><circle cx="6" cy="6" r="5" stroke="currentColor" strokeWidth="1.1"/><path d="M4 4l4 4M8 4l-4 4" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round"/></svg>
                          </button>
                        ) : (
                          <button className="icon-btn activate" title="Reactivar cuenta">
                            <svg width="12" height="12" viewBox="0 0 12 12" fill="none"><circle cx="6" cy="6" r="5" stroke="currentColor" strokeWidth="1.1"/><path d="M3.5 6l2 2 3-3" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round" strokeLinejoin="round"/></svg>
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        <div style={{ padding: '10px 18px', borderTop: '1px solid #eaecf0', background: '#f9fafb', fontSize: '12.5px', color: 'var(--c-muted)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span>8 usuarios registrados</span>
          <span style={{ fontSize: '11.5px' }}>El administrador tiene acceso a todos los experimentos.</span>
        </div>
      </div>
    </div>
  )
}

function Experimentos() {
  const [busqueda, setBusqueda] = useState('')
  const [dueno, setDueno] = useState('')
  const filtrados = ALL_EXPS.filter((e) =>
    (!busqueda || `${e.id} ${e.trat}`.toLowerCase().includes(busqueda.toLowerCase())) &&
    (!dueno || e.owner === dueno),
  )
  return (
    <div>
      <div className="page-header">
        <div>
          <div className="page-title">Todos los experimentos</div>
          <div className="page-sub">El administrador puede ver y gestionar experimentos de cualquier investigador.</div>
        </div>
        <div className="search-wrap">
          <IconoBuscar />
          <input className="search-input" type="search" placeholder="Buscar experimento…" value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
        </div>
      </div>
      <div className="card">
        <CardHeader
          icon={<svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M4 1v5L1 12.5a1 1 0 00.9 1.5h10.2a1 1 0 00.9-1.5L10 6V1" stroke="#4b6490" strokeWidth="1.1" strokeLinecap="round" strokeLinejoin="round"/><path d="M4 1h6" stroke="#4b6490" strokeWidth="1.1" strokeLinecap="round"/></svg>}
          title="Experimentos — todos los investigadores"
          sub="42 experimentos · 4 investigadores"
        >
          <select className="form-select" style={{ height: '30px', fontSize: '12px', padding: '0 24px 0 8px' }} value={dueno} onChange={(e) => setDueno(e.target.value)}>
            <option value="">Todos los investigadores</option><option>M. Sánchez</option><option>L. Torres</option><option>R. Castro</option><option>P. Vega</option>
          </select>
        </CardHeader>
        <div className="tbl-wrap">
          <table>
            <thead>
              <tr>
                <th>Experimento</th>
                <th>Investigador</th>
                <th>Fecha</th>
                <th>Tratamiento</th>
                <th>Estado</th>
                <th>Video</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {filtrados.map((e) => (
                <tr key={e.id}>
                  <td><a href="#" style={{ color: 'var(--c-link)', fontWeight: 600, textDecoration: 'none' }} onClick={(ev) => ev.preventDefault()}>{e.id}</a></td>
                  <td><span className="owner-chip">{e.owner}</span></td>
                  <td style={{ fontSize: '12.5px', color: 'var(--c-muted)' }}>{e.date}</td>
                  <td style={{ fontSize: '13px', maxWidth: '180px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{e.trat}</td>
                  <td>
                    {e.status === 'done' && <span className="status-badge sb-active"><span className="s-dot"></span>Completado</span>}
                    {e.status === 'proc' && (
                      <span className="status-badge" style={{ background: 'var(--c-proc-bg)', color: 'var(--c-proc-txt)', borderColor: 'var(--c-proc-bdr)' }}>
                        <span className="s-dot" style={{ background: '#3b82f6', animation: 'blink 1.4s infinite' }}></span>En proceso
                      </span>
                    )}
                    {e.status === 'err' && (
                      <span className="status-badge" style={{ background: 'var(--c-err-bg)', color: 'var(--c-err-txt)', borderColor: 'var(--c-err-bdr)' }}>
                        <span className="s-dot" style={{ background: '#ef4444' }}></span>Error
                      </span>
                    )}
                  </td>
                  <td style={{ fontSize: '12px', color: 'var(--c-muted)' }}>Disponible</td>
                  <td>
                    <div className="row-acts">
                      <button className="icon-btn" title="Ver resultados">
                        <svg width="12" height="12" viewBox="0 0 12 12" fill="none"><path d="M1 6s2-4.5 5-4.5S11 6 11 6s-2 4.5-5 4.5S1 6 1 6z" stroke="currentColor" strokeWidth="1.1"/><circle cx="6" cy="6" r="1.5" stroke="currentColor" strokeWidth="1.1"/></svg>
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div style={{ padding: '10px 18px', borderTop: '1px solid #eaecf0', background: '#f9fafb', fontSize: '12px', color: 'var(--c-muted)' }}>
          Mostrando {filtrados.length} de 42 · <a href="#" style={{ color: 'var(--c-link)' }} onClick={(ev) => ev.preventDefault()}>Ver todos</a>
        </div>
      </div>
    </div>
  )
}

function UserModal({ modal, onClose }) {
  const editando = modal.mode === 'edit'
  const u = modal.user
  const partes = u ? u.name.split(' ') : []
  const [form, setForm] = useState({
    nombre: editando ? partes[1] || '' : '',
    apellido: editando ? (partes[0] || '').replace('.', '') : '',
    email: editando ? u.email : '',
    rol: editando ? u.role : '',
    pwd: '',
    estado: editando ? (u.active ? 'active' : 'inactive') : 'active',
  })
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }))

  return (
    <div className="overlay open" onClick={(e) => { if (e.target === e.currentTarget) onClose() }}>
      <div className="modal">
        <div className="modal-header">
          <div className="modal-title">{editando ? `Editar usuario — ${u.name}` : 'Crear nuevo usuario'}</div>
          <button className="modal-close" onClick={onClose}>
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M2 2l10 10M12 2L2 12" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/></svg>
          </button>
        </div>
        <div className="modal-body">
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
            <div className="form-group">
              <label className="form-label">Nombre <span className="req">*</span></label>
              <input className="form-input" type="text" placeholder="Nombre" value={form.nombre} onChange={set('nombre')} />
            </div>
            <div className="form-group">
              <label className="form-label">Apellido <span className="req">*</span></label>
              <input className="form-input" type="text" placeholder="Apellido" value={form.apellido} onChange={set('apellido')} />
            </div>
          </div>
          <div className="form-group">
            <label className="form-label">Correo electrónico institucional <span className="req">*</span></label>
            <input className="form-input" type="email" placeholder="usuario@ipn.mx" value={form.email} onChange={set('email')} />
          </div>
          <div className="form-group">
            <label className="form-label">Rol <span className="req">*</span></label>
            <select className="form-select" value={form.rol} onChange={set('rol')}>
              <option value="">Seleccionar rol…</option>
              <option value="inv">Investigador</option>
              <option value="adm">Administrador</option>
            </select>
          </div>
          {!editando ? (
            <div className="form-group">
              <label className="form-label">Contraseña temporal <span className="req">*</span></label>
              <input className="form-input" type="password" placeholder="El usuario la cambiará al primer ingreso" value={form.pwd} onChange={set('pwd')} />
              <div style={{ fontSize: '11.5px', color: 'var(--c-muted)', marginTop: '3px' }}>Se enviará un correo de bienvenida con las instrucciones.</div>
            </div>
          ) : (
            <div className="form-group">
              <label className="form-label">Estado de la cuenta</label>
              <select className="form-select" value={form.estado} onChange={set('estado')}>
                <option value="active">Activo</option>
                <option value="inactive">Inactivo (desactivado)</option>
              </select>
            </div>
          )}
        </div>
        <div className="modal-footer">
          <button className="btn-ghost" onClick={onClose}>Cancelar</button>
          <button className="btn-primary" onClick={onClose}>
            <IconoCheck />
            {editando ? 'Guardar cambios' : 'Crear usuario'}
          </button>
        </div>
      </div>
    </div>
  )
}

const SECCIONES = [
  { key: 'sistema', label: 'Estado del sistema', icon: <svg width="15" height="15" viewBox="0 0 15 15" fill="none"><rect x="1.5" y="1.5" width="5" height="5" rx="1" stroke="currentColor" strokeWidth="1.1"/><rect x="8.5" y="1.5" width="5" height="5" rx="1" stroke="currentColor" strokeWidth="1.1"/><rect x="1.5" y="8.5" width="5" height="5" rx="1" stroke="currentColor" strokeWidth="1.1"/><rect x="8.5" y="8.5" width="5" height="5" rx="1" stroke="currentColor" strokeWidth="1.1"/></svg> },
  { key: 'usuarios', label: 'Usuarios', badge: '8', icon: <svg width="15" height="15" viewBox="0 0 15 15" fill="none"><circle cx="5.5" cy="5" r="2.5" stroke="currentColor" strokeWidth="1.1"/><path d="M1 13c0-2.5 2-4 4.5-4s4.5 1.5 4.5 4" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round"/><circle cx="11.5" cy="5" r="2" stroke="currentColor" strokeWidth="1.1"/><path d="M13.5 13c0-2-1.3-3-3-3" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round"/></svg> },
  { key: 'experimentos', label: 'Todos los experimentos', badge: '42', icon: <svg width="15" height="15" viewBox="0 0 15 15" fill="none"><path d="M5 1v5L1.5 12a1 1 0 00.9 1.5h10.2a1 1 0 00.9-1.5L10 6V1" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round" strokeLinejoin="round"/><path d="M4.5 1h6" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round"/></svg> },
]

export default function AdminPage() {
  const [demo, setDemo] = useState('normal')
  const [seccion, setSeccion] = useState('sistema')
  const [modal, setModal] = useState(null)

  const cambiarDemo = (k) => {
    setDemo(k)
    setModal(null)
    if (k === 'diskwarn' || k === 'queue') setSeccion('sistema')
    if (k === 'modal_new') setModal({ mode: 'new' })
    if (k === 'modal_edit') setModal({ mode: 'edit', user: USERS[0] })
  }

  return (
    <div className="pg-admin">
      <DemoBar states={DEMO_STATES} value={demo} onChange={cambiarDemo} />

      <div className="layout">
        <aside className="sidebar">
          <div className="sidebar-section">Principal</div>
          {SECCIONES.map((s) => (
            <div key={s.key} className={`sitem ${seccion === s.key ? 'active' : ''}`} onClick={() => setSeccion(s.key)}>
              {s.icon}
              {s.label}
              {s.key === 'sistema' && demo === 'diskwarn' && <span className="sitem-badge warn" style={{ display: 'inline' }}>!</span>}
              {s.badge && <span className="sitem-badge blue">{s.badge}</span>}
            </div>
          ))}

          <div className="sdivider"></div>
          <div className="sidebar-section">Sistema</div>
          <div className="sitem">
            <svg width="15" height="15" viewBox="0 0 15 15" fill="none"><rect x="2" y="1.5" width="11" height="12" rx="1.5" stroke="currentColor" strokeWidth="1.1"/><path d="M5 5h5M5 7.5h5M5 10h3" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round"/></svg>
            Logs del servidor
          </div>
          <div className="sitem">
            <svg width="15" height="15" viewBox="0 0 15 15" fill="none"><circle cx="7.5" cy="7.5" r="2" stroke="currentColor" strokeWidth="1.1"/><path d="M7.5 1.5v1.5M7.5 12v1.5M1.5 7.5H3M12 7.5h1.5M3.4 3.4l1 1M10.6 10.6l1 1M3.4 11.6l1-1M10.6 4.4l1-1" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round"/></svg>
            Configuración
          </div>
        </aside>

        <main className="main">
          {demo === 'diskwarn' && (
            <div className="alert-bar warn" style={{ display: 'flex' }}>
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" style={{ flexShrink: 0, marginTop: '1px' }}><path d="M8 1.5L14.5 13H1.5L8 1.5z" stroke="#d97706" strokeWidth="1.3" strokeLinejoin="round"/><path d="M8 6v3.5" stroke="#d97706" strokeWidth="1.4" strokeLinecap="round"/><circle cx="8" cy="11" r=".7" fill="#d97706"/></svg>
              <span><strong>Almacenamiento al 81%.</strong> El sistema eliminará automáticamente los videos más antiguos al llegar al 90%. Considera liberar espacio o ampliar el volumen del servidor.</span>
            </div>
          )}
          {demo === 'queue' && (
            <div className="alert-bar info" style={{ display: 'flex' }}>
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" style={{ flexShrink: 0, marginTop: '1px' }}><path d="M8 1.5L14.5 13H1.5L8 1.5z" stroke="#d97706" strokeWidth="1.3" strokeLinejoin="round"/><path d="M8 6v3.5" stroke="#d97706" strokeWidth="1.4" strokeLinecap="round"/><circle cx="8" cy="11" r=".7" fill="#d97706"/></svg>
              <span><strong>3 análisis en curso.</strong> El servidor está procesando videos. El tiempo estimado de finalización de la cola es aproximadamente 31 minutos.</span>
            </div>
          )}

          {seccion === 'sistema' && <Sistema demo={demo} />}
          {seccion === 'usuarios' && <Usuarios onNew={() => setModal({ mode: 'new' })} onEdit={(u) => setModal({ mode: 'edit', user: u })} />}
          {seccion === 'experimentos' && <Experimentos />}
        </main>
      </div>

      {modal && <UserModal key={`${modal.mode}-${modal.user?.id ?? 'new'}`} modal={modal} onClose={() => setModal(null)} />}
    </div>
  )
}
