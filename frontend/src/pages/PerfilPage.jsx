import React, { useRef, useState } from 'react'
import { useAuth } from '../contexts/AuthContext'
import Topbar, { Brand } from '../components/Topbar'
import { FieldError, Campo } from '../components/ui'
import { useErrorDeCampo } from '../hooks/useErrorDeCampo'
import { mensajeError } from '../services/api'
import { isIpn } from '../lib/fst'
import { MUT60 } from '../lib/estilos'

const formStyle = { background: 'var(--color-bg)', padding: '18px 20px', display: 'flex', flexDirection: 'column', gap: 14 }
const btnStyle = { alignSelf: 'flex-start', justifyContent: 'flex-start' }
const VACIO = { act: '', nw: '', rep: '' }

// 2j · Mi perfil: datos de la cuenta y cambio de contraseña.
export default function PerfilPage() {
  const { user, actualizarPerfil, cambiarPassword } = useAuth()

  // Datos: «Guardar cambios» se habilita al modificar algo.
  const inicial = { nombre: user.nombre, apellidos: user.apellidos, correo: user.correo }
  const [original, setOriginal] = useState(inicial)
  const [datos, setDatos] = useState(inicial)
  const [guardandoD, setGuardandoD] = useState(false)
  const dirty = Object.keys(datos).some((k) => datos[k] !== original[k])
  const d = useErrorDeCampo({ nombre: useRef(), apellidos: useRef(), correo: useRef() })

  // Contraseña.
  const [pass, setPass] = useState(VACIO)
  const [guardandoP, setGuardandoP] = useState(false)
  const p = useErrorDeCampo({ act: useRef(), nw: useRef(), rep: useRef() })

  async function guardarDatos(e) {
    e.preventDefault()
    if (guardandoD) return
    if (!datos.nombre.trim()) return d.fail('nombre', 'Escribe el nombre.')
    if (!datos.apellidos.trim()) return d.fail('apellidos', 'Escribe los apellidos.')
    if (!isIpn(datos.correo)) return d.fail('correo', 'El correo debe ser institucional (@ipn.mx).')
    d.fail(null, '')
    setGuardandoD(true)
    try {
      await actualizarPerfil(datos)
      setOriginal(datos)
    } catch (e) {
      // El correo repetido lo detecta el servidor.
      d.fail(e.response?.status === 409 ? 'correo' : null, mensajeError(e, 'No se pudieron guardar los cambios.'))
    } finally {
      setGuardandoD(false)
    }
  }

  async function cambiarPass(e) {
    e.preventDefault()
    if (guardandoP) return
    if (!pass.act) return p.fail('act', 'Escribe tu contraseña actual.')
    if (pass.nw.length < 8) return p.fail('nw', 'La nueva contraseña debe tener mínimo 8 caracteres.')
    if (pass.nw === pass.act) return p.fail('nw', 'La nueva contraseña debe ser distinta de la actual.')
    if (pass.nw !== pass.rep) return p.fail('rep', 'Las contraseñas no coinciden.')
    p.fail(null, '')
    setGuardandoP(true)
    try {
      await cambiarPassword(pass.act, pass.nw)
      setPass(VACIO)
    } catch (e) {
      p.fail(null, mensajeError(e, 'No se pudo cambiar la contraseña.'))
    } finally {
      setGuardandoP(false)
    }
  }

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
            <Campo id="pNombre" label="Nombre"><input ref={d.refs.nombre} className={d.clase('nombre')} id="pNombre" value={datos.nombre} onChange={setD('nombre')} /></Campo>
            <Campo id="pApe" label="Apellidos"><input ref={d.refs.apellidos} className={d.clase('apellidos')} id="pApe" value={datos.apellidos} onChange={setD('apellidos')} /></Campo>
            <Campo id="pCorreo" label="Correo institucional"><input ref={d.refs.correo} className={d.clase('correo', 'input num')} id="pCorreo" type="email" value={datos.correo} onChange={setD('correo')} /></Campo>
            <Campo id="pId" label="Identificador institucional · solo lectura">
              <input className="input num" id="pId" value={user.idInst} readOnly style={{ background: 'var(--color-surface)', color: MUT60 }} />
            </Campo>
            <FieldError msg={d.err.msg} style={{ margin: 0 }} />
            <button type="submit" className="btn btn-primary" disabled={!dirty || guardandoD} style={btnStyle}>Guardar cambios</button>
          </form>
          <form noValidate onSubmit={cambiarPass} style={formStyle}>
            <div className="k">Cambiar contraseña</div>
            <Campo id="pAct" label="Contraseña actual"><input ref={p.refs.act} className={p.clase('act', 'input num')} id="pAct" type="password" autoComplete="current-password" value={pass.act} onChange={setP('act')} /></Campo>
            <Campo id="pNew" label="Nueva contraseña"><input ref={p.refs.nw} className={p.clase('nw', 'input num')} id="pNew" type="password" autoComplete="new-password" value={pass.nw} onChange={setP('nw')} /></Campo>
            <Campo id="pRep" label="Confirmar nueva contraseña"><input ref={p.refs.rep} className={p.clase('rep', 'input num')} id="pRep" type="password" autoComplete="new-password" value={pass.rep} onChange={setP('rep')} /></Campo>
            <FieldError msg={p.err.msg} style={{ margin: 0 }} />
            <button type="submit" className="btn btn-secondary" style={btnStyle} disabled={guardandoP}>Cambiar contraseña</button>
          </form>
        </div>
      </div>
    </div>
  )
}
