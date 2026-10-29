import React, { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { listNotifications, unreadCount, setRead, markAllRead } from '../services/notifications'
import { fechaCorta } from '../lib/fst'
import { version } from '../../package.json'
import { MUT60, MUT70 } from '../lib/estilos'

// 2k · Barra superior: lo que cada pantalla pone a la izquierda (children),
// más la campana de notificaciones y el menú de usuario.
// Las notificaciones se vuelven a consultar cada 30 s, solo mientras la pestaña está visible. Al volver a verla, se consultan
// de inmediato.
const CADA_MS = 30000

// Texto del enlace de cada tipo de notificación (los seis del documento).
const ENLACE = {
  ANALYSIS_DONE: 'Ver resultados',
  ANALYSIS_FAILED: 'Ver detalle del error',
  ANALYSIS_PAUSED: 'Ver el análisis en pausa',
  ANALYSIS_CANCELED: 'Ver el análisis',
  DISK_ALERT: 'Ver el estado del sistema',
  VIDEO_DELETED: 'Ver el experimento',
}

// Hoy → «15:41»; ayer → «ayer»; antes → «18 feb 2026».
function cuando(iso) {
  const d = new Date(iso)
  const dia = (x) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime()
  const dias = Math.round((dia(new Date()) - dia(d)) / 86400000)
  if (dias === 0) return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0')
  if (dias === 1) return 'ayer'
  const local = d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
  return fechaCorta(local)
}
const BELL = (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="square" aria-hidden="true">
    <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
    <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
  </svg>
)

export default function Topbar({ children }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(null) // 'n' | 'u' | null
  const [list, setList] = useState([])
  const ref = useRef(null)

  // El número de la campana es el total de la cuenta (unreadCount), no el de las cien de la lista.
  const [unread, setUnread] = useState(0)

  const contar = useCallback(() => {
    unreadCount().then(setUnread).catch(() => { /* se reintenta en la siguiente consulta */ })
  }, [])

  const cargar = useCallback(() => {
    listNotifications().then(setList).catch(() => { /* se reintenta en la siguiente consulta */ })
    contar()
  }, [contar])

  useEffect(() => {
    cargar()
    // Con la pestaña oculta no se consulta; al volver a verla, se consulta en seguida.
    const id = setInterval(() => { if (document.visibilityState === 'visible') cargar() }, CADA_MS)
    const alVolver = () => { if (document.visibilityState === 'visible') cargar() }
    document.addEventListener('visibilitychange', alVolver)
    return () => {
      clearInterval(id)
      document.removeEventListener('visibilitychange', alVolver)
    }
  }, [cargar])

  // Se marca en pantalla de inmediato; si el servidor falla, se vuelve a consultar.
  function marcar(id, isRead) {
    setList((l) => l.map((n) => (n.id === id ? { ...n, is_read: isRead } : n)))
    setUnread((u) => Math.max(0, isRead ? u - 1 : u + 1))
    setRead(id, isRead).then(contar).catch(cargar)
  }

  function marcarTodas() {
    setList((l) => l.map((n) => ({ ...n, is_read: true })))
    setUnread(0)
    markAllRead().then(contar).catch(cargar)
  }

  useEffect(() => {
    if (!open) return
    const onDown = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(null) }
    const onKey = (e) => { if (e.key === 'Escape') setOpen(null) }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const toggle = (which) => setOpen((o) => (o === which ? null : which))

  function abrirNotif(n) {
    if (!n.is_read) marcar(n.id, true)
    setOpen(null)
    navigate(n.enlace)
  }

  function salir(e) {
    e.preventDefault()
    logout()
    navigate('/login')
  }

  return (
    <nav className="nav">
      {children}
      <div ref={ref} style={{ display: 'contents' }}>
        <button type="button" className="tb-btn" aria-label="Notificaciones" aria-expanded={open === 'n'} onClick={() => toggle('n')}>
          {BELL}
          {unread > 0 && <span className="tb-dot" />}
        </button>
        <button type="button" className="tb-btn" aria-label="Menú de usuario" aria-expanded={open === 'u'} onClick={() => toggle('u')}>
          <span className={'avatar' + (user.admin ? ' admin' : '')}>{user.ini}</span>
          <span style={{ fontSize: 10 }}>{open === 'u' ? '▴' : '▾'}</span>
        </button>

        {open === 'n' && (
          <div className="dropdown" style={{ width: 460 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '12px 14px', borderBottom: '2px solid var(--color-divider)' }}>
              <span className="nd" style={{ fontSize: 14 }}>Notificaciones</span>
              {unread > 0 && <span className="tag tag-accent">{unread} sin leer</span>}
              <div style={{ flex: 1 }} />
              <button type="button" className="linkbtn" style={{ fontSize: 12 }} onClick={marcarTodas}>
                Marcar todas como leídas
              </button>
            </div>
            {list.map((n) => (
              <div key={n.id} className="notif" style={{ background: n.is_read ? 'var(--color-bg)' : 'var(--color-accent-100)' }}>
                <span style={{ width: 8, height: 8, marginTop: 5, background: n.is_read ? 'transparent' : 'var(--color-accent)', border: '1px solid var(--color-divider)' }} />
                <div>
                  <div className="nd" style={{ fontSize: 13 }}>{n.titulo}</div>
                  <div style={{ fontSize: 12, lineHeight: 1.5, marginTop: 2, color: MUT70 }}>{n.texto}</div>
                  <a href={n.enlace} style={{ display: 'inline-block', marginTop: 5 }} onClick={(e) => { e.preventDefault(); abrirNotif(n) }}>{ENLACE[n.tipo]}</a>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 5 }}>
                  <span className="num" style={{ fontSize: 11, whiteSpace: 'nowrap', color: 'var(--muted)' }}>{cuando(n.creada)}</span>
                  <button type="button" className="linkbtn" style={{ fontSize: 11, whiteSpace: 'nowrap' }}
                    onClick={() => marcar(n.id, !n.is_read)}>
                    {n.is_read ? 'Marcar como no leída' : 'Marcar como leída'}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}

        {open === 'u' && (
          <div className="dropdown" style={{ width: 230 }}>
            <div style={{ padding: '12px 14px', borderBottom: '1px solid var(--color-divider)' }}>
              <div className="nd" style={{ fontSize: 13 }}>{(user.nombre + ' ' + user.apellidos).trim()}</div>
              <div className="num" style={{ fontSize: 11.5, color: MUT60 }}>{user.correo} · {user.rol}</div>
            </div>
            <Link className="menu-item" to="/perfil" style={{ borderBottom: '1px solid var(--color-divider)' }}>Mi perfil</Link>
            <a className="menu-item" href="/login" onClick={salir}>Cerrar sesión</a>
            <div className="num" style={{ padding: '8px 14px', fontSize: 11, color: 'var(--muted)', borderTop: '1px solid var(--color-divider)' }}>FST v{version}</div>
          </div>
        )}
      </div>
    </nav>
  )
}

// Marca «FST» con subtítulo opcional, como en cada mockup.
export function Brand({ sub, inline }) {
  return (
    <span className="nav-brand" style={inline ? { marginRight: 0 } : undefined}>
      <Link to="/experimentos" style={{ fontSize: 'inherit' }}>FST</Link>
      {sub && <span className="nav-sub">{sub}</span>}
    </span>
  )
}
