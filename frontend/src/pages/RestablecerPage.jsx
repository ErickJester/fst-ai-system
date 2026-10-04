import React, { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { FieldError, Campo, Acceso } from '../components/ui'
import { useErrorDeCampo } from '../hooks/useErrorDeCampo'
import { resetPassword } from '../services/auth'
import { mensajeError } from '../services/api'
import { textoTarjeta } from '../lib/estilos'

// 2h · Restablecer contraseña desde el enlace del correo (/restablecer?token=…).
// El enlace vale 60 minutos y un solo uso; si venció, se pide uno nuevo.
export default function RestablecerPage() {
  const [params] = useSearchParams()
  const token = params.get('token') || ''
  const [v, setV] = useState({ nw: '', rep: '' })
  const [guardando, setGuardando] = useState(false)
  const [estado, setEstado] = useState(token ? 'form' : 'vencido') // form | listo | vencido
  const { err, fail, clase } = useErrorDeCampo()

  // Otro enlace abierto sobre esta misma pantalla empieza de cero.
  useEffect(() => {
    setEstado(token ? 'form' : 'vencido')
    setV({ nw: '', rep: '' })
    setGuardando(false)
  }, [token])
  const set = (k) => (e) => setV({ ...v, [k]: e.target.value })

  async function guardar(e) {
    e.preventDefault()
    if (guardando) return
    if (v.nw.length < 8) return fail('nw', 'La nueva contraseña debe tener mínimo 8 caracteres.')
    if (v.nw !== v.rep) return fail('rep', 'Las contraseñas no coinciden.')
    fail(null, '')
    setGuardando(true)
    try {
      await resetPassword(token, v.nw)
      setEstado('listo')
    } catch (e) {
      if (e.response?.status === 400) setEstado('vencido')
      else fail(null, mensajeError(e, 'No se pudo cambiar la contraseña. Inténtalo de nuevo.'))
      setGuardando(false)
    }
  }

  if (estado === 'listo') {
    return (
      <Acceso encabezado>
        <div style={{ padding: '30px 34px 32px' }}>
          <h3 style={{ margin: '0 0 10px' }}>Contraseña actualizada</h3>
          <p style={textoTarjeta}>Ya puedes iniciar sesión con tu nueva contraseña.</p>
          <Link className="btn btn-primary btn-block" to="/login">Iniciar sesión</Link>
        </div>
      </Acceso>
    )
  }

  if (estado === 'vencido') {
    return (
      <Acceso encabezado>
        <div style={{ padding: '30px 34px 32px' }}>
          <h3 style={{ margin: '0 0 10px' }}>El enlace ya no es válido</h3>
          <p style={textoTarjeta}>
            Venció (vale 60 minutos) o ya se usó. Pide uno nuevo con el correo de tu cuenta.
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <Link className="btn btn-primary btn-block" to="/login?recuperar=1">Pedir un enlace nuevo</Link>
            <Link to="/login" style={{ fontSize: 12.5 }}>Volver a iniciar sesión</Link>
          </div>
        </div>
      </Acceso>
    )
  }

  const cls = (k) => clase(k, 'input num')

  return (
    <Acceso encabezado>
      <form noValidate onSubmit={guardar} style={{ padding: '30px 34px 32px' }}>
        <h3 style={{ margin: '0 0 10px' }}>Nueva contraseña</h3>
        <p style={textoTarjeta}>Escribe la contraseña con la que vas a entrar de ahora en adelante.</p>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <Campo id="rNew" label="Nueva contraseña"><input className={cls('nw')} id="rNew" type="password" autoComplete="new-password" autoFocus value={v.nw} onChange={set('nw')} /></Campo>
          <Campo id="rRep" label="Confirmar nueva contraseña"><input className={cls('rep')} id="rRep" type="password" autoComplete="new-password" value={v.rep} onChange={set('rep')} /></Campo>
          <div className="hint">Mínimo 8 caracteres.</div>
          <FieldError msg={err.msg} />
          <button className="btn btn-primary btn-block" type="submit" disabled={guardando}>Guardar contraseña</button>
          <Link to="/login" style={{ fontSize: 12.5 }}>Volver a iniciar sesión</Link>
        </div>
      </form>
    </Acceso>
  )
}
