import React, { useRef, useState } from 'react'
import { useAuth } from '../contexts/AuthContext'
import Topbar, { Brand } from '../components/Topbar'
import { FieldError } from '../components/ui'
import { isIpn } from '../lib/fst'

const formStyle = { background: 'var(--color-bg)', padding: '18px 20px', display: 'flex', flexDirection: 'column', gap: 14 }
const btnStyle = { alignSelf: 'flex-start', justifyContent: 'flex-start' }

// 2j · Mi perfil: datos de la cuenta y cambio de contraseña.
export default function PerfilPage() {
  const { user, actualizarPerfil, cambiarPassword } = useAuth()

  // Datos: «Guardar cambios» se habilita al modificar algo.
  const inicial = { nombre: user.nombre, apellidos: user.apellidos, correo: user.correo }
  const [original, setOriginal] = useState(inicial)
  const [datos, setDatos] = useState(inicial)
  const [dErr, setDErr] = useState({ msg: '', campo: null })
  const [guardandoD, setGuardandoD] = useState(false)
  const dirty = Object.keys(datos).some((k) => datos[k] !== original[k])
  const refs = { nombre: useRef(), apellidos: useRef(), correo: useRef() }

  function failD(campo, msg) {
    setDErr({ campo, msg })
    if (campo) refs[campo].current.focus()
  }

  async function guardarDatos(e) {
    e.preventDefault()
    if (guardandoD) return
    if (!datos.nombre.trim()) return failD('nombre', 'Escribe el nombre.')
    if (!datos.apellidos.trim()) return failD('apellidos', 'Escribe los apellidos.')
    if (!isIpn(datos.correo)) return failD('correo', 'El correo debe ser institucional (@ipn.mx).')
    failD(null, '')
    setGuardandoD(true)
    try {
      await actualizarPerfil(datos)
      setOriginal(datos)
    } catch (e) {
      // El correo repetido lo detecta el servidor.
      failD(e.response?.status === 409 ? 'correo' : null, e.response?.data?.error || 'No se pudieron guardar los cambios.')
    } finally {
      setGuardandoD(false)
    }
  }

  const vacio = { act: '', nw: '', rep: '' }
  const [pass, setPass] = useState(vacio)
  const [pErr, setPErr] = useState({ msg: '', campo: null })
  const [guardandoP, setGuardandoP] = useState(false)
  const prefs = { act: useRef(), nw: useRef(), rep: useRef() }

  function failP(campo, msg) {
    setPErr({ campo, msg })
    if (campo) prefs[campo].current.focus()
  }

  async function cambiarPass(e) {
    e.preventDefault()
    if (guardandoP) return
    if (!pass.act) return failP('act', 'Escribe tu contraseña actual.')
    if (pass.nw.length < 8) return failP('nw', 'La nueva contraseña debe tener mínimo 8 caracteres.')
    if (pass.nw === pass.act) return failP('nw', 'La nueva contraseña debe ser distinta de la actual.')
    if (pass.nw !== pass.rep) return failP('rep', 'Las contraseñas no coinciden.')
    failP(null, '')
    setGuardandoP(true)
    try {
      await cambiarPassword(pass.act, pass.nw)
      setPass(vacio)
    } catch (e) {
      failP(null, e.response?.data?.error || 'No se pudo cambiar la contraseña.')
    } finally {
      setGuardandoP(false)
    }
  }

  const dCls = (k, extra = '') => 'input' + extra + (dErr.campo === k ? ' error' : '')
  const pCls = (k) => 'input num' + (pErr.campo === k ? ' error' : '')
  const setD = (k) => (e) => setDatos({ ...datos, [k]: e.target.value })
  const setP = (k) => (e) => setPass({ ...pass, [k]: e.target.value })

  return (
    <div className="app" style={{ maxWidth: 720 }}>
      <Topbar><Brand sub="Mi perfil" /></Topbar>
      <div className="page" style={{ paddingBottom: 32 }}>
        <h3 style={{ margin: '0 0 20px' }}>Mi perfil</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, background: 'var(--color-divider)', border: '1px solid var(--color-divider)' }}>
          <form noValidate onSubmit={guardarDatos} style={formStyle}>
            <div className="k">Datos de la cuenta</div>
            <div className="field"><label htmlFor="pNombre">Nombre</label><input ref={refs.nombre} className={dCls('nombre')} id="pNombre" value={datos.nombre} onChange={setD('nombre')} /></div>
            <div className="field"><label htmlFor="pApe">Apellidos</label><input ref={refs.apellidos} className={dCls('apellidos')} id="pApe" value={datos.apellidos} onChange={setD('apellidos')} /></div>
            <div className="field"><label htmlFor="pCorreo">Correo institucional</label><input ref={refs.correo} className={dCls('correo', ' num')} id="pCorreo" type="email" value={datos.correo} onChange={setD('correo')} /></div>
            <div className="field">
              <label htmlFor="pId">Identificador institucional · solo lectura</label>
              <input className="input num" id="pId" value={user.idInst} readOnly style={{ background: 'var(--color-surface)', color: 'color-mix(in srgb,var(--color-text) 60%,transparent)' }} />
            </div>
            <FieldError msg={dErr.msg} style={{ margin: 0 }} />
            <button type="submit" className="btn btn-primary" disabled={!dirty || guardandoD} style={btnStyle}>Guardar cambios</button>
          </form>
          <form noValidate onSubmit={cambiarPass} style={formStyle}>
            <div className="k">Cambiar contraseña</div>
            <div className="field"><label htmlFor="pAct">Contraseña actual</label><input ref={prefs.act} className={pCls('act')} id="pAct" type="password" autoComplete="current-password" value={pass.act} onChange={setP('act')} /></div>
            <div className="field"><label htmlFor="pNew">Nueva contraseña</label><input ref={prefs.nw} className={pCls('nw')} id="pNew" type="password" autoComplete="new-password" value={pass.nw} onChange={setP('nw')} /></div>
            <div className="field"><label htmlFor="pRep">Confirmar nueva contraseña</label><input ref={prefs.rep} className={pCls('rep')} id="pRep" type="password" autoComplete="new-password" value={pass.rep} onChange={setP('rep')} /></div>
            <FieldError msg={pErr.msg} style={{ margin: 0 }} />
            <button type="submit" className="btn btn-secondary" style={btnStyle} disabled={guardandoP}>Cambiar contraseña</button>
          </form>
        </div>
      </div>
    </div>
  )
}
