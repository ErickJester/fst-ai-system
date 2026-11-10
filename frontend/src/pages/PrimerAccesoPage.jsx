import React, { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { FieldError, Campo, Acceso } from '../components/ui'
import { useErrorDeCampo } from '../hooks/useErrorDeCampo'
import { mensajeError } from '../services/api'
import { textoTarjeta } from '../lib/estilos'

// 2i · Cambiar contraseña en el primer acceso. Hasta cambiarla no hay acceso
// a ninguna otra pantalla (lo impone el router).
export default function PrimerAccesoPage() {
  const { logout, cambiarPassword } = useAuth()
  const navigate = useNavigate()
  const [v, setV] = useState({ tmp: '', nw: '', rep: '' })
  const [acepta, setAcepta] = useState(false) // aviso de privacidad y términos: obligatorio; no se guarda
  const [guardando, setGuardando] = useState(false)
  const { err, fail, clase } = useErrorDeCampo()
  const set = (k) => (e) => setV({ ...v, [k]: e.target.value })

  async function guardar(e) {
    e.preventDefault()
    if (guardando) return
    if (!v.tmp) return fail('tmp', 'Escribe la contraseña temporal.')
    if (v.nw.length < 8) return fail('nw', 'La nueva contraseña debe tener mínimo 8 caracteres.')
    if (v.nw === v.tmp) return fail('nw', 'La nueva contraseña debe ser distinta de la temporal.')
    if (v.nw !== v.rep) return fail('rep', 'Las contraseñas no coinciden.')
    if (new TextEncoder().encode(v.nw).length > 72) return fail('nw', 'La nueva contraseña no puede pasar de 72 bytes.')
    if (!acepta) return fail('acepta', 'Debes aceptar el aviso de privacidad y los términos para continuar.')
    fail(null, '')
    setGuardando(true)
    try {
      await cambiarPassword(v.tmp, v.nw, true)
      navigate('/experimentos')
    } catch (e) {
      // El servidor dice «La contraseña actual no es correcta.» cuando la temporal no coincide; ese caso marca el campo.
      const texto = mensajeError(e, 'No se pudo cambiar la contraseña. Inténtalo de nuevo.')
      if (texto === 'La contraseña actual no es correcta.') fail('tmp', 'La contraseña temporal no es correcta.')
      else fail(null, texto)
      setGuardando(false)
    }
  }

  const cls = (k) => clase(k, 'input num')

  return (
    <Acceso encabezado>
      <form noValidate onSubmit={guardar} style={{ padding: '30px 34px 32px' }}>
        <h3 style={{ margin: '0 0 10px' }}>Cambiar contraseña</h3>
        <p style={textoTarjeta}>
          Entraste con una contraseña temporal. Debes cambiarla antes de usar el sistema; hasta entonces no hay acceso a ninguna otra pantalla.
        </p>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <Campo id="pTmp" label="Contraseña temporal"><input className={cls('tmp')} id="pTmp" type="password" autoComplete="current-password" value={v.tmp} onChange={set('tmp')} /></Campo>
          <Campo id="pNew" label="Nueva contraseña"><input className={cls('nw')} id="pNew" type="password" autoComplete="new-password" value={v.nw} onChange={set('nw')} /></Campo>
          <Campo id="pRep" label="Confirmar nueva contraseña"><input className={cls('rep')} id="pRep" type="password" autoComplete="new-password" value={v.rep} onChange={set('rep')} /></Campo>
          <div className="hint">Mínimo 8 caracteres, distinta de la temporal.</div>
          <label htmlFor="pAcepta" style={{ display: 'flex', gap: 10, alignItems: 'flex-start', fontSize: 13, lineHeight: 1.5 }}>
            <input id="pAcepta" type="checkbox" checked={acepta} onChange={(e) => setAcepta(e.target.checked)} style={{ marginTop: 3 }} />
            <span>
              He leído y acepto el <Link to="/aviso-privacidad" target="_blank" rel="noopener noreferrer">Aviso de privacidad</Link> y los{' '}
              <Link to="/terminos" target="_blank" rel="noopener noreferrer">Términos y condiciones de uso</Link>.
            </span>
          </label>
          <FieldError msg={err.msg} />
          <button className="btn btn-primary btn-block" type="submit" disabled={guardando}>Guardar y entrar</button>
        </div>
        <hr className="hr" />
        <a href="/login" style={{ fontSize: 12.5 }} onClick={(e) => { e.preventDefault(); logout(); navigate('/login') }}>Cerrar sesión</a>
      </form>
    </Acceso>
  )
}
