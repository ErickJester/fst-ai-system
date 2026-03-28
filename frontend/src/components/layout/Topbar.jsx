import React, { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../contexts/AuthContext'

const FlaskIcon = () => (
  <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
    <path d="M6 1v5.5L2.5 13a1 1 0 00.9 1.5h9.2a1 1 0 00.9-1.5L10 6.5V1" stroke="#c8d8f0" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round"/>
    <path d="M5 1h6" stroke="#c8d8f0" strokeWidth="1.3" strokeLinecap="round"/>
    <circle cx="5.5" cy="11" r="1" fill="#8fa3c1"/>
    <circle cx="9" cy="12.5" r=".75" fill="#8fa3c1"/>
  </svg>
)

export default function Topbar({ variant = 'default' }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const menuRef = useRef(null)

  useEffect(() => {
    function handleClick(e) {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setDropdownOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <header className="topbar">
      <div className="topbar-brand">
        <div className="topbar-icon">
          <FlaskIcon />
        </div>
        <div>
          <div className="topbar-name">Sistema FST</div>
          <div className="topbar-sub">Análisis conductual automatizado</div>
        </div>
      </div>

      <div className="topbar-spacer" />

      <div className="topbar-badge">TT 2026-B066 · ESCOM-IPN</div>

      {variant === 'admin' && (
        <button className="notif-btn" title="Alertas del sistema">
          <svg width="17" height="17" viewBox="0 0 17 17" fill="none">
            <path d="M8.5 2a5.5 5.5 0 015.5 5.5c0 2.5.5 4 1.5 5H2c1-1 1.5-2.5 1.5-5A5.5 5.5 0 018.5 2z" stroke="#8fa3c1" strokeWidth="1.2"/>
            <path d="M7 14.5a1.5 1.5 0 003 0" stroke="#8fa3c1" strokeWidth="1.2" strokeLinecap="round"/>
          </svg>
        </button>
      )}

      {user && (
        <div className="user-menu" ref={menuRef} onClick={() => setDropdownOpen(!dropdownOpen)}>
          <div className="user-avatar">{user.initials}</div>
          <div className="user-info">
            <div className="user-name">{user.name}</div>
            <div className={`user-role ${variant === 'admin' ? 'admin-role' : ''}`}>
              {user.role === 'admin' ? 'Administrador' : 'Investigador'}
            </div>
          </div>
          <span className="user-chevron">
            <svg width="10" height="6" viewBox="0 0 10 6" fill="none">
              <path d="M1 1l4 4 4-4" stroke="#8fa3c1" strokeWidth="1.5" strokeLinecap="round"/>
            </svg>
          </span>

          <div className={`dropdown ${dropdownOpen ? 'open' : ''}`}>
            <div className="dropdown-item" onClick={() => {}}>
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><circle cx="7" cy="5" r="3" stroke="currentColor" strokeWidth="1.2"/><path d="M2 13c0-2.5 2-4.5 5-4.5s5 2 5 4.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round"/></svg>
              Mi perfil
            </div>
            <hr className="dropdown-divider" />
            <div className="dropdown-item danger" onClick={handleLogout}>
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M5 1H3a2 2 0 00-2 2v8a2 2 0 002 2h2M9 10l3-3-3-3M12 7H5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round"/></svg>
              Cerrar sesión
            </div>
          </div>
        </div>
      )}
    </header>
  )
}
