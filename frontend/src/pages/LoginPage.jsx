import React, { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { FieldError, Campo, Acceso } from '../components/ui'
import { useErrorDeCampo } from '../hooks/useErrorDeCampo'
import { forgotPassword } from '../services/auth'
import { mensajeError } from '../services/api'
import { AVISO_KEY } from '../services/config'
import { isIpn } from '../lib/fst'
import { textoTarjeta } from '../lib/estilos'

// 2h · Iniciar sesión y recuperar contraseña.
export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [vista, setVista] = useState('login')
  const [email, setEmail] = useState('')
  const [pass, setPass] = useState('')
  const [rEmail, setREmail] = useState('')
  const [enviado, setEnviado] = useState(false)
  // Aviso que deja api.js al vencer la sesión; se muestra una vez.
  const [caducada] = useState(() => {
    try {
      return sessionStorage.getItem(AVISO_KEY) === 'sesion-caducada'
    } catch {
      return false
    }
  })
  useEffect(() => {
    try { sessionStorage.removeItem(AVISO_KEY) } catch { /* sin almacenamiento */ }
  }, [])
  const [enviando, setEnviando] = useState(false)
  const entrada = useErrorDeCampo()
  const recuperacion = useErrorDeCampo()

  async function entrar(e) {
    e.preventDefault()
    if (enviando) return
    if (!isIpn(email)) return entrada.fail('email', 'Usa tu correo institucional @ipn.mx.')
    if (!pass) return entrada.fail('pass', 'Escribe tu contraseña.')
    entrada.fail(null, '')
    setEnviando(true)
    try {
      const u = await login(email, pass)
      navigate(u.temporal ? '/primer-acceso' : '/experimentos')
    } catch (e) {
      entrada.fail(null, mensajeError(e, 'No se pudo iniciar sesión. Inténtalo de nuevo.'))
      setEnviando(false)
    }
  }

  // El campo se marca con cualquier error, también el del servidor.
  async function recuperar(e) {
    e.preventDefault()
    if (enviando) return
    if (!isIpn(rEmail)) return recuperacion.fail('rEmail', 'Usa tu correo institucional @ipn.mx.')
    recuperacion.fail(null, '')
    setEnviando(true)
    try {
      await forgotPassword(rEmail.trim())
      setEnviado(true)
    } catch (e) {
      recuperacion.fail('rEmail', mensajeError(e, 'No se pudo enviar el enlace. Inténtalo de nuevo.'))
    } finally {
      setEnviando(false)
    }
  }

  if (vista === 'recuperar') {
    return (
      <Acceso>
        <form noValidate onSubmit={recuperar} style={{ padding: '30px 34px 32px' }}>
          <div className="nd" style={{ fontSize: 24, color: 'var(--color-accent)', marginBottom: 26 }}>FST</div>
          <h3 style={{ margin: '0 0 10px' }}>Recuperar contraseña</h3>
          <p style={textoTarjeta}>
            Escribe el correo de tu cuenta y te enviamos un enlace de restablecimiento válido por 30 minutos.
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <Campo id="rEmail" label="Correo institucional">
              <input className={recuperacion.clase('rEmail', 'input num')} id="rEmail" type="email" placeholder="nombre@ipn.mx" autoFocus
                value={rEmail} onChange={(e) => setREmail(e.target.value)} />
            </Campo>
            <FieldError msg={recuperacion.err.msg} />
            <button className="btn btn-primary btn-block" type="submit" disabled={enviando}>Enviar enlace</button>
            <a href="#" style={{ fontSize: 12.5 }} onClick={(e) => { e.preventDefault(); setEnviado(false); setVista('login') }}>Volver a iniciar sesión</a>
          </div>
          {enviado && (
            <div>
              <hr className="hr" />
              <div className="ok-bar" style={{ padding: '13px 15px', lineHeight: 1.6 }}>
                Si el correo está registrado, recibirás un enlace.
              </div>
            </div>
          )}
        </form>
      </Acceso>
    )
  }

  return (
    <Acceso encabezado sub={<>Análisis de la prueba de nado forzado (<em>Forced Swim Test</em>, FST)<br />Laboratorio de Bioquímica Estructural · ENMyH-IPN</>}>
      <form noValidate onSubmit={entrar} style={{ padding: '30px 34px 32px' }}>
        {caducada && <div className="note-bar" style={{ marginBottom: 18 }}>Tu sesión caducó. Vuelve a iniciar sesión.</div>}
        <h3 style={{ margin: '0 0 22px' }}>Iniciar sesión</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <Campo id="lEmail" label="Correo institucional">
            <input className={entrada.clase('email', 'input num')} id="lEmail" type="email" placeholder="nombre@ipn.mx" autoComplete="username"
              value={email} onChange={(e) => setEmail(e.target.value)} />
          </Campo>
          <Campo id="lPass" label="Contraseña">
            <input className={entrada.clase('pass', 'input num')} id="lPass" type="password" placeholder="••••••••••" autoComplete="current-password"
              value={pass} onChange={(e) => setPass(e.target.value)} />
          </Campo>
          <FieldError msg={entrada.err.msg} />
          <button className="btn btn-primary btn-block" type="submit" disabled={enviando}>Entrar</button>
          <a href="#" style={{ fontSize: 12.5 }} onClick={(e) => { e.preventDefault(); setREmail(email); setVista('recuperar') }}>Olvidé mi contraseña</a>
        </div>
      </form>
    </Acceso>
  )
}
