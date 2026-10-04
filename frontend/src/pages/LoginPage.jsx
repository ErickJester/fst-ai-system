import React, { useState } from 'react'
import { useNavigate, Navigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import '../styles/pages/login.css'

export default function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [error, setError] = useState(false)

  if (user) {
    return <Navigate to={user.role === 'admin' ? '/admin' : '/dashboard'} replace />
  }

  const handleSubmit = (e) => {
    e.preventDefault()
    const result = login(email, password)
    if (result.ok) {
      const u = JSON.parse(localStorage.getItem('fst_user'))
      navigate(u.role === 'admin' ? '/admin' : '/dashboard')
    } else {
      setError(true)
    }
  }

  return (
    <div className="pg-login">
      <header className="topbar">
        <div className="topbar-logo">
          <div className="topbar-icon">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <path d="M6 1v5.5L2.5 13a1 1 0 00.9 1.5h9.2a1 1 0 00.9-1.5L10 6.5V1" stroke="#c8d8f0" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round"/>
              <path d="M5 1h6" stroke="#c8d8f0" strokeWidth="1.3" strokeLinecap="round"/>
              <circle cx="5.5" cy="11" r="1" fill="#8fa3c1"/>
              <circle cx="9" cy="12.5" r=".75" fill="#8fa3c1"/>
            </svg>
          </div>
          <div>
            <div className="topbar-name">Sistema FST</div>
            <div className="topbar-sub">Análisis conductual automatizado</div>
          </div>
        </div>
        <div className="topbar-divider"></div>
        <div className="topbar-badge">TT 2026-B066 · ESCOM-IPN</div>
      </header>

      <main className="page">
        <div className="card-wrap">
          <div className="card">
            <div className="card-header">
              <div className="card-title">Iniciar sesión</div>
              <div className="card-subtitle">Ingresa tus credenciales institucionales para continuar.</div>
            </div>

            <div className="card-body">
              {error && (
                <div className="error-banner" style={{ display: 'flex' }}>
                  <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                    <circle cx="8" cy="8" r="7" stroke="#f87171" strokeWidth="1.4"/>
                    <path d="M8 5v3.5" stroke="#f87171" strokeWidth="1.5" strokeLinecap="round"/>
                    <circle cx="8" cy="11" r=".7" fill="#f87171"/>
                  </svg>
                  <div className="error-banner-text">
                    <strong>Credenciales incorrectas</strong>
                    El correo o la contraseña no son válidos. Verifica tus datos e inténtalo de nuevo.
                  </div>
                </div>
              )}

              <form onSubmit={handleSubmit}>
                <div className="form-group">
                  <label className="form-label" htmlFor="email">
                    Correo electrónico <span className="required">*</span>
                  </label>
                  <div className="input-wrap">
                    <span className="input-icon">
                      <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                        <rect x="1.5" y="3.5" width="13" height="9" rx="1.5" stroke="#9ca3af" strokeWidth="1.3"/>
                        <path d="M1.5 5.5l6.5 4 6.5-4" stroke="#9ca3af" strokeWidth="1.3" strokeLinecap="round"/>
                      </svg>
                    </span>
                    <input
                      id="email"
                      className={`form-input ${error ? 'error' : ''}`}
                      type="email"
                      placeholder="usuario@ipn.mx"
                      autoComplete="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      required
                    />
                  </div>
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="password">
                    Contraseña <span className="required">*</span>
                  </label>
                  <div className="input-wrap">
                    <span className="input-icon">
                      <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                        <rect x="3" y="7" width="10" height="7" rx="1.5" stroke="#9ca3af" strokeWidth="1.3"/>
                        <path d="M5.5 7V5a2.5 2.5 0 015 0v2" stroke="#9ca3af" strokeWidth="1.3" strokeLinecap="round"/>
                        <circle cx="8" cy="10.5" r="1" fill="#9ca3af"/>
                      </svg>
                    </span>
                    <input
                      id="password"
                      className={`form-input ${error ? 'error' : ''}`}
                      type={showPwd ? 'text' : 'password'}
                      placeholder="••••••••"
                      autoComplete="current-password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      required
                    />
                    <button
                      type="button"
                      className="input-toggle"
                      title="Mostrar contraseña"
                      aria-label="Mostrar u ocultar contraseña"
                      onClick={() => setShowPwd((v) => !v)}
                    >
                      <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                        <path d="M1 8s2.5-5 7-5 7 5 7 5-2.5 5-7 5-7-5-7-5z" stroke="#9ca3af" strokeWidth="1.3"/>
                        <circle cx="8" cy="8" r="2" stroke="#9ca3af" strokeWidth="1.3"/>
                      </svg>
                    </button>
                  </div>
                </div>

                <div className="forgot-row">
                  <a href="#" className="link" onClick={(e) => e.preventDefault()}>¿Olvidé mi contraseña?</a>
                </div>

                <div>
                  <button type="submit" className="btn-primary">
                    <svg width="15" height="15" viewBox="0 0 15 15" fill="none">
                      <path d="M2 7.5h11M8.5 3l4.5 4.5L8.5 12" stroke="#fff" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                    Ingresar
                  </button>
                </div>
              </form>
            </div>

            <div className="card-footer">
              <p className="footer-note">
                Acceso restringido. Si no tienes cuenta, contacta al administrador del sistema.
              </p>
            </div>
          </div>

          <p className="page-note">
            Sistema de análisis conductual FST · ESCOM-IPN · TT 2026-B066
          </p>
        </div>
      </main>
    </div>
  )
}
