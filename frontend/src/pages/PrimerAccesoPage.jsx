import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { FieldError, Campo } from '../components/ui'

// 2i · Cambiar contraseña en el primer acceso. Hasta cambiarla no hay acceso
// a ninguna otra pantalla (lo impone el router).
export default function PrimerAccesoPage() {
  const { logout, cambiarPassword } = useAuth()
  const navigate = useNavigate()
  const [v, setV] = useState({ tmp: '', nw: '', rep: '' })
  const [err, setErr] = useState({ msg: '', campo: null })
  const set = (k) => (e) => setV({ ...v, [k]: e.target.value })
  const [guardando, setGuardando] = useState(false)
  const fail = (campo, msg) => setErr({ campo, msg })

  async function guardar(e) {
    e.preventDefault()
    if (guardando) return
    if (!v.tmp) return fail('tmp', 'Escribe la contraseña temporal.')
    if (v.nw.length < 8) return fail('nw', 'La nueva contraseña debe tener mínimo 8 caracteres.')
    if (v.nw === v.tmp) return fail('nw', 'La nueva contraseña debe ser distinta de la temporal.')
    if (v.nw !== v.rep) return fail('rep', 'Las contraseñas no coinciden.')
    fail(null, '')
    setGuardando(true)
    try {
      await cambiarPassword(v.tmp, v.nw)
      navigate('/experimentos')
    } catch (e) {
      fail(null, e.response?.data?.error || 'No se pudo cambiar la contraseña. Inténtalo de nuevo.')
      setGuardando(false)
    }
  }

  const cls = (k) => 'input num' + (err.campo === k ? ' error' : '')

  return (
    <div className="acc-page">
      <div className="acc-card">
        <div style={{ background: 'var(--color-accent)', color: 'var(--color-bg)', padding: '22px 34px' }}>
          <div className="nd" style={{ fontSize: 24 }}>FST</div>
        </div>
        <form noValidate onSubmit={guardar} style={{ padding: '30px 34px 32px' }}>
          <h3 style={{ margin: '0 0 10px' }}>Cambiar contraseña</h3>
          <p style={{ margin: '0 0 22px', fontSize: 13, lineHeight: 1.6, color: 'color-mix(in srgb,var(--color-text) 70%,transparent)', textWrap: 'pretty' }}>
            Entraste con una contraseña temporal. Debes cambiarla antes de usar el sistema; hasta entonces no hay acceso a ninguna otra pantalla.
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <Campo id="pTmp" label="Contraseña temporal"><input className={cls('tmp')} id="pTmp" type="password" autoComplete="current-password" value={v.tmp} onChange={set('tmp')} /></Campo>
            <Campo id="pNew" label="Nueva contraseña"><input className={cls('nw')} id="pNew" type="password" autoComplete="new-password" value={v.nw} onChange={set('nw')} /></Campo>
            <Campo id="pRep" label="Confirmar nueva contraseña"><input className={cls('rep')} id="pRep" type="password" autoComplete="new-password" value={v.rep} onChange={set('rep')} /></Campo>
            <div className="hint">Mínimo 8 caracteres, distinta de la temporal.</div>
            <FieldError msg={err.msg} />
            <button className="btn btn-primary btn-block" type="submit" disabled={guardando}>Guardar y entrar</button>
          </div>
          <hr className="hr" />
          <a href="/login" style={{ fontSize: 12.5 }} onClick={(e) => { e.preventDefault(); logout(); navigate('/login') }}>Cerrar sesión</a>
        </form>
      </div>
    </div>
  )
}
