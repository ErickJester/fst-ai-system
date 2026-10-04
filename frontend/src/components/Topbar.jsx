import React, { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { NOTIFS } from '../data/mock'
import { store } from '../lib/fst'

// 2k · Barra superior: lo que cada pantalla pone a la izquierda (children),
// más la campana de notificaciones y el menú de usuario.
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
  const [leidas, setLeidas] = useState(() => store.get('leidas', null))
  const ref = useRef(null)

  const list = NOTIFS.map((n) => ({ ...n, leida: leidas ? !!leidas[n.id] : n.leida }))
  const unread = list.filter((n) => !n.leida).length

  function save(next) {
    const m = {}
    next.forEach((n) => { m[n.id] = n.leida })
    store.set('leidas', m)
    setLeidas(m)
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
    save(list.map((x) => (x.id === n.id ? { ...x, leida: true } : x)))
    setOpen(null)
    navigate(n.to)
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
              <button type="button" className="linkbtn" style={{ fontSize: 12 }} onClick={() => save(list.map((n) => ({ ...n, leida: true })))}>
                Marcar todas como leídas
              </button>
            </div>
            {list.map((n) => (
              <div key={n.id} className="notif" style={{ background: n.leida ? 'var(--color-bg)' : 'var(--color-accent-100)' }}>
                <span style={{ width: 8, height: 8, marginTop: 5, background: n.leida ? 'transparent' : 'var(--color-accent)', border: '1px solid var(--color-divider)' }} />
                <div>
                  <div className="nd" style={{ fontSize: 13 }}>{n.titulo}</div>
                  <div style={{ fontSize: 12, lineHeight: 1.5, marginTop: 2, color: 'color-mix(in srgb,var(--color-text) 70%,transparent)' }}>{n.texto}</div>
                  <a href={n.to} style={{ display: 'inline-block', marginTop: 5 }} onClick={(e) => { e.preventDefault(); abrirNotif(n) }}>{n.enlace}</a>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 5 }}>
                  <span className="num" style={{ fontSize: 11, whiteSpace: 'nowrap', color: 'var(--muted)' }}>{n.hora}</span>
                  <button type="button" className="linkbtn" style={{ fontSize: 11, whiteSpace: 'nowrap' }}
                    onClick={() => save(list.map((x) => (x.id === n.id ? { ...x, leida: !x.leida } : x)))}>
                    {n.leida ? 'Marcar como no leída' : 'Marcar como leída'}
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
              <div className="num" style={{ fontSize: 11.5, color: 'color-mix(in srgb,var(--color-text) 60%,transparent)' }}>{user.correo} · {user.rol}</div>
            </div>
            <Link className="menu-item" to="/perfil" style={{ borderBottom: '1px solid var(--color-divider)' }}>Mi perfil</Link>
            <a className="menu-item" href="/login" onClick={salir}>Cerrar sesión</a>
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
