import React, { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { AccessShell, Field } from '../components/ui'

const esIPN = (v) => /^[^\s@]+@ipn\.mx$/i.test(v.trim())

// 2h · Iniciar sesión
export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState({})
  const [sending, setSending] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    if (!esIPN(email)) return setError({ email: 'Usa tu correo institucional @ipn.mx.' })
    if (!password) return setError({ password: 'Escribe tu contraseña.' })
    setError({})
    setSending(true)
    const res = await login(email, password)
    setSending(false)
    if (!res.ok) return setError({ form: res.error })
    if (res.user.must_change_password) return navigate('/primer-acceso', { replace: true })
    navigate(location.state?.from || '/experimentos', { replace: true })
  }

  return (
    <AccessShell header="full">
      <form onSubmit={submit} noValidate>
        <h3 style={{ margin: '0 0 22px' }}>Iniciar sesión</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <Field label="Correo institucional" htmlFor="email" error={error.email}>
            <input className={`input num${error.email ? ' error' : ''}`} id="email" type="email" placeholder="nombre@ipn.mx" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} />
          </Field>
          <Field label="Contraseña" htmlFor="password" error={error.password}>
            <input className={`input num${error.password ? ' error' : ''}`} id="password" type="password" placeholder="••••••••••" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
          </Field>
          {error.form && <div className="field-error" role="alert" style={{ margin: 0 }}>{error.form}</div>}
          <button className="btn btn-primary btn-block" type="submit" disabled={sending}>Entrar</button>
          <Link to="/recuperar" style={{ fontSize: 12.5 }}>Olvidé mi contraseña</Link>
        </div>
      </form>
    </AccessShell>
  )
}
