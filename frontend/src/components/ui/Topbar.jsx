import React, { useCallback, useEffect, useRef, useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../../contexts/AuthContext'
import * as notifService from '../../services/notifications'
import Breadcrumbs from './Breadcrumbs'
import Tag from './Tag'

const BellIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="square" aria-hidden="true">
    <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
    <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
  </svg>
)

const initials = (u) => ((u.nombre?.[0] || '') + (u.apellidos?.[0] || '')).toUpperCase()

// Barra superior (pantalla 2k).
// - sub: texto junto a la marca (p. ej. «Análisis en curso»)
// - crumbs: ruta de migas [{ label, to }]; sustituye a los enlaces de sección
// - links: muestra Experimentos / Análisis en curso / Administración
// - actions: enlaces extra antes de la campana (p. ej. «Salir» en 2b)
export default function Topbar({ sub, crumbs, links = false, actions }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(null) // 'notif' | 'user' | null
  const [notifs, setNotifs] = useState([])
  const ref = useRef(null)

  const loadNotifs = useCallback(() => {
    notifService.listNotifications().then(setNotifs).catch(() => {})
  }, [])

  useEffect(loadNotifs, [loadNotifs])

  useEffect(() => {
    const onDoc = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(null) }
    const onKey = (e) => { if (e.key === 'Escape') setOpen(null) }
    document.addEventListener('mousedown', onDoc)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDoc)
      document.removeEventListener('keydown', onKey)
    }
  }, [])

  const toggle = (which) => {
    setOpen((o) => (o === which ? null : which))
    if (which === 'notif') loadNotifs()
  }

  const setRead = async (id, isRead) => {
    await notifService.setRead(id, isRead)
    loadNotifs()
  }
  const markAll = async () => {
    await notifService.markAllRead()
    loadNotifs()
  }
  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  if (!user) return null
  const unread = notifs.filter((n) => !n.is_read).length
  const isAdmin = user.role === 'ADMIN'

  return (
    <header className="nav" ref={ref}>
      <span className="nav-brand" style={crumbs ? { marginRight: 0 } : undefined}>
        <Link to="/experimentos">FST</Link>
        {sub && <span className="nav-sub">{sub}</span>}
      </span>

      {crumbs && <Breadcrumbs items={crumbs} />}

      {links && (
        <>
          <NavLink to="/experimentos" end>Experimentos</NavLink>
          <NavLink to="/analisis">Análisis en curso</NavLink>
          {isAdmin && <NavLink to="/admin">Administración</NavLink>}
        </>
      )}

      {actions}

      <button type="button" className="tb-btn" aria-label="Notificaciones" aria-expanded={open === 'notif'} onClick={() => toggle('notif')}>
        <BellIcon />
        {unread > 0 && <span className="tb-dot" />}
      </button>

      <button type="button" className="tb-btn" aria-label="Menú de usuario" aria-expanded={open === 'user'} onClick={() => toggle('user')}>
        <span className={`avatar${isAdmin ? ' admin' : ''}`}>{initials(user)}</span>
        <span style={{ fontSize: 10 }}>{open === 'user' ? '▴' : '▾'}</span>
      </button>

      {open === 'notif' && (
        <div className="dropdown" style={{ width: 460, maxWidth: 'calc(100vw - 32px)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '12px 14px', borderBottom: '2px solid var(--color-divider)' }}>
            <span className="nd" style={{ fontSize: 14 }}>Notificaciones</span>
            {unread > 0 && <Tag variant="accent">{unread} sin leer</Tag>}
            <div style={{ flex: 1 }} />
            <button type="button" className="linkbtn" style={{ fontSize: 12 }} onClick={markAll}>Marcar todas como leídas</button>
          </div>
          {notifs.length === 0 && <div className="hint" style={{ padding: '12px 14px' }}>No hay notificaciones.</div>}
          {notifs.map((n) => (
            <div key={n.id} className="notif" style={{ background: n.is_read ? 'var(--color-bg)' : 'var(--color-accent-100)' }}>
              <span style={{ width: 8, height: 8, marginTop: 5, background: n.is_read ? 'transparent' : 'var(--color-accent)', border: '1px solid var(--color-divider)' }} />
              <div>
                <div className="nd" style={{ fontSize: 13 }}>{n.titulo}</div>
                <div style={{ fontSize: 12, lineHeight: 1.5, marginTop: 2, color: 'color-mix(in srgb,var(--color-text) 70%,transparent)' }}>{n.texto}</div>
                <Link
                  to={n.enlace}
                  style={{ display: 'inline-block', marginTop: 5 }}
                  onClick={() => { if (!n.is_read) setRead(n.id, true); setOpen(null) }}
                >
                  {n.tipo === 'ANALYSIS_FAILED' ? 'Ver detalle del error' : 'Ver resultados'}
                </Link>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 5 }}>
                <span className="num muted" style={{ fontSize: 11, whiteSpace: 'nowrap' }}>{n.creada}</span>
                <button type="button" className="linkbtn" style={{ fontSize: 11, whiteSpace: 'nowrap' }} onClick={() => setRead(n.id, !n.is_read)}>
                  {n.is_read ? 'Marcar como no leída' : 'Marcar como leída'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {open === 'user' && (
        <div className="dropdown" style={{ width: 230 }}>
          <div style={{ padding: '12px 14px', borderBottom: '1px solid var(--color-divider)' }}>
            <div className="nd" style={{ fontSize: 13 }}>{user.nombre} {user.apellidos}</div>
            <div className="num" style={{ fontSize: 11.5, color: 'color-mix(in srgb,var(--color-text) 60%,transparent)' }}>
              {user.email} · {isAdmin ? 'Administrador' : 'Investigador'}
            </div>
          </div>
          <Link className="menu-item" to="/perfil" style={{ borderBottom: '1px solid var(--color-divider)' }} onClick={() => setOpen(null)}>Mi perfil</Link>
          <button type="button" className="menu-item linkbtn" style={{ width: '100%', textAlign: 'left', textDecoration: 'none' }} onClick={handleLogout}>Cerrar sesión</button>
        </div>
      )}
    </header>
  )
}
