import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { FieldError } from '../components/ui'
import { isIpn } from '../lib/fst'

// 2h · Iniciar sesión y recuperar contraseña.
export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [vista, setVista] = useState('login')
  const [email, setEmail] = useState('')
  const [pass, setPass] = useState('')
  const [err, setErr] = useState({ msg: '', campo: null })
  const [rEmail, setREmail] = useState('')
  const [rErr, setRErr] = useState('')
  const [enviado, setEnviado] = useState('')

  function entrar(e) {
    e.preventDefault()
    if (!isIpn(email)) return setErr({ msg: 'Usa tu correo institucional @ipn.mx.', campo: 'email' })
    if (!pass) return setErr({ msg: 'Escribe tu contraseña.', campo: 'pass' })
    setErr({ msg: '', campo: null })
    const u = login(email)
    navigate(u.temporal ? '/primer-acceso' : '/experimentos')
  }

  function recuperar(e) {
    e.preventDefault()
    if (!isIpn(rEmail)) return setRErr('Usa tu correo institucional @ipn.mx.')
    setRErr('')
    setEnviado(rEmail.trim())
  }

  if (vista === 'recuperar') {
    return (
      <div className="acc-page">
        <div className="acc-card">
          <form noValidate onSubmit={recuperar} style={{ padding: '30px 34px 32px' }}>
            <div className="nd" style={{ fontSize: 24, color: 'var(--color-accent)', marginBottom: 26 }}>FST</div>
            <h3 style={{ margin: '0 0 10px' }}>Recuperar contraseña</h3>
            <p style={{ margin: '0 0 22px', fontSize: 13, lineHeight: 1.6, color: 'color-mix(in srgb,var(--color-text) 70%,transparent)', textWrap: 'pretty' }}>
              Escribe el correo de tu cuenta y te enviamos un enlace de restablecimiento válido por 30 minutos.
            </p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div className="field">
                <label htmlFor="rEmail">Correo institucional</label>
                <input className={'input num' + (rErr ? ' error' : '')} id="rEmail" type="email" placeholder="nombre@ipn.mx" autoFocus
                  value={rEmail} onChange={(e) => setREmail(e.target.value)} />
              </div>
              <FieldError msg={rErr} />
              <button className="btn btn-primary btn-block" type="submit">Enviar enlace</button>
              <a href="#" style={{ fontSize: 12.5 }} onClick={(e) => { e.preventDefault(); setEnviado(''); setVista('login') }}>Volver a iniciar sesión</a>
            </div>
            {enviado && (
              <div>
                <hr className="hr" />
                <div className="ok-bar" style={{ padding: '13px 15px', lineHeight: 1.6 }}>
                  <strong>Enviado.</strong> Revisa {enviado}. Por seguridad este mensaje aparece igual exista o no la cuenta.
                </div>
              </div>
            )}
          </form>
        </div>
      </div>
    )
  }

  return (
    <div className="acc-page">
      <div className="acc-card">
        <div style={{ background: 'var(--color-accent)', color: 'var(--color-bg)', padding: '22px 34px' }}>
          <div className="nd" style={{ fontSize: 24 }}>FST</div>
          <div style={{ fontSize: 11, lineHeight: 1.5, marginTop: 4, color: 'color-mix(in srgb,#fff 78%,transparent)' }}>
            Análisis de la prueba de nado forzado (<em>Forced Swim Test</em>, FST)<br />Laboratorio de Bioquímica Estructural · ENMyH-IPN
          </div>
        </div>
        <form noValidate onSubmit={entrar} style={{ padding: '30px 34px 32px' }}>
          <h3 style={{ margin: '0 0 22px' }}>Iniciar sesión</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div className="field">
              <label htmlFor="lEmail">Correo institucional</label>
              <input className={'input num' + (err.campo === 'email' ? ' error' : '')} id="lEmail" type="email" placeholder="nombre@ipn.mx" autoComplete="username"
                value={email} onChange={(e) => setEmail(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="lPass">Contraseña</label>
              <input className={'input num' + (err.campo === 'pass' ? ' error' : '')} id="lPass" type="password" placeholder="••••••••••" autoComplete="current-password"
                value={pass} onChange={(e) => setPass(e.target.value)} />
            </div>
            <FieldError msg={err.msg} />
            <button className="btn btn-primary btn-block" type="submit">Entrar</button>
            <a href="#" style={{ fontSize: 12.5 }} onClick={(e) => { e.preventDefault(); setREmail(email); setVista('recuperar') }}>Olvidé mi contraseña</a>
          </div>
        </form>
      </div>
    </div>
  )
}
