import React, { useRef, useState } from 'react'
import { useAuth } from '../contexts/AuthContext'
import Topbar, { Brand } from '../components/Topbar'
import { FieldError, Campo, Cargando } from '../components/ui'
import { listUsers, createUser, setUserActive, getSystem } from '../services/admin'
import { getQueue } from '../services/queue'
import { useAsync } from '../hooks/useAsync'
import { useErrorDeCampo } from '../hooks/useErrorDeCampo'
import { mensajeError } from '../services/api'
import { isIpn, DIA } from '../lib/fst'
import { MUT60, MUT70, btnLeft } from '../lib/estilos'

const VACIO = { nombre: '', apellidos: '', correo: '', id: '' }
const ROL = { INVESTIGADOR: 'Investigador', ADMIN: 'Administrador' }
const AVISO_DISCO_PCT = 80

// «Mariana», «Rivera Alcántara» → «M. Rivera Alcántara».
const nombreCorto = (u) => u.nombre.split(/\s+/).map((p) => p[0] + '.').join(' ') + ' ' + u.apellidos
const gb = (v) => v + ' GB'

// 2g · Administración: usuarios, cola y disco.
export default function AdminPage() {
  const { user } = useAuth()
  const { data: usuarios, error: errUsuarios, reload } = useAsync(listUsers)
  const { data: sistema } = useAsync(getSystem)
  const { data: cola } = useAsync(getQueue)
  const [errAccion, setErrAccion] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [crear, setCrear] = useState(false)
  const [okCrear, setOkCrear] = useState(false)
  const [form, setForm] = useState(VACIO)
  const { err, fail, clase, refs } = useErrorDeCampo({ nombre: useRef(), apellidos: useRef(), correo: useRef(), id: useRef() })

  if (errUsuarios) {
    return (
      <div className="app mid">
        <Topbar><Brand sub="Administración" /></Topbar>
        <div className="page"><p className="hint" style={{ color: 'var(--color-accent-700)' }}>No se pudo cargar la lista de cuentas.</p></div>
      </div>
    )
  }
  if (!usuarios) return <Cargando />

  const adminsActivos = usuarios.filter((u) => u.role === 'ADMIN' && u.is_active).length

  async function desactivar(u) {
    if (!window.confirm('¿Desactivar la cuenta de ' + nombreCorto(u) + '?')) return
    setErrAccion('')
    try {
      await setUserActive(u.id, false)
      reload()
    } catch (e) {
      setErrAccion(mensajeError(e, 'No se pudo desactivar la cuenta.'))
    }
  }

  function abrir() {
    setOkCrear(false)
    setCrear(true)
    setTimeout(() => refs.nombre.current?.focus())
  }

  function cancelar() {
    setForm(VACIO)
    setCrear(false)
    fail(null, '')
  }

  async function guardar(e) {
    e.preventDefault()
    if (guardando) return
    const v = Object.fromEntries(Object.entries(form).map(([k, x]) => [k, x.trim()]))
    if (!v.nombre) return fail('nombre', 'Escribe el nombre.')
    if (!v.apellidos) return fail('apellidos', 'Escribe los apellidos.')
    if (!isIpn(v.correo)) return fail('correo', 'El correo debe ser institucional (@ipn.mx).')
    if (!/^\d+$/.test(v.id)) return fail('id', 'El identificador institucional es numérico (boleta o número de empleado).')
    fail(null, '')
    setGuardando(true)
    try {
      await createUser({ nombre: v.nombre, apellidos: v.apellidos, email: v.correo, identificador: v.id })
      setForm(VACIO)
      setCrear(false)
      setOkCrear(true)
      reload()
    } catch (e) {
      // El correo repetido lo detecta el servidor.
      fail(e.response?.status === 409 ? 'correo' : null, mensajeError(e, 'No se pudo crear la cuenta.'))
    } finally {
      setGuardando(false)
    }
  }

  const cls = (k, extra = '') => clase(k, 'input' + extra)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  return (
    <div className="app mid">
      <Topbar><Brand sub="Administración" /></Topbar>

      <div className="page" style={{ paddingBottom: 34 }}>
        <h3 style={{ margin: '0 0 8px' }}>Administración</h3>
        <p className="lead" style={{ margin: '0 0 24px', maxWidth: 640 }}>
          La cuenta de administrador conserva todo lo que puede hacer un investigador —subir video, definir experimentos, consultar resultados— y añade estas tres áreas.
        </p>

        <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 12 }}>
          <div className="k">Cuentas · {usuarios.filter((u) => u.is_active).length} activas</div>
          <div style={{ flex: 1 }} />
          <button type="button" className="btn btn-primary" style={btnLeft} onClick={abrir}>Crear cuenta</button>
        </div>
        <table className="table">
          <thead><tr><th>Persona</th><th>Correo</th><th>Rol</th><th>Estado</th><th style={{ textAlign: 'right' }}>Acción</th></tr></thead>
          <tbody>
            {usuarios.map((u) => {
              const ultimoAdmin = u.role === 'ADMIN' && u.is_active && adminsActivos <= 1
              const yo = u.email === user.correo
              return (
                <tr key={u.id}>
                  <td className="nd" style={{ fontSize: 13 }}>{nombreCorto(u)}</td>
                  <td className="num" style={{ fontSize: 12.5, color: MUT70 }}>{u.email}</td>
                  <td style={{ fontSize: 12.5 }}>{ROL[u.role]}</td>
                  <td><span className="tag tag-neutral">{u.is_active ? 'Activa' : 'Inactiva'}{yo ? ' · tú' : ''}</span></td>
                  <td style={{ textAlign: 'right' }}>
                    {u.is_active && ultimoAdmin && (
                      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 3 }}>
                        <button type="button" className="btn btn-ghost" disabled>Desactivar</button>
                        <span style={{ fontSize: 11, color: 'var(--color-accent-700)', whiteSpace: 'nowrap' }}>Debe existir al menos un Administrador activo</span>
                      </div>
                    )}
                    {u.is_active && !ultimoAdmin && <button type="button" className="btn btn-ghost" onClick={() => desactivar(u)}>Desactivar</button>}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
        <FieldError msg={errAccion} style={{ marginTop: 10 }} />

        {crear && (
          <form noValidate onSubmit={guardar} style={{ marginTop: 18, border: '2px solid var(--color-text)' }}>
            <div className="nd" style={{ padding: '12px 16px', borderBottom: '1px solid var(--color-divider)', fontSize: 14 }}>Crear cuenta</div>
            <div style={{ padding: 16, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
              <Campo id="cNombre" label="Nombre"><input ref={refs.nombre} className={cls('nombre')} id="cNombre" placeholder="Andrea" value={form.nombre} onChange={set('nombre')} /></Campo>
              <Campo id="cApe" label="Apellidos"><input ref={refs.apellidos} className={cls('apellidos')} id="cApe" placeholder="Barrera Solís" value={form.apellidos} onChange={set('apellidos')} /></Campo>
              <Campo id="cCorreo" label="Correo institucional (@ipn.mx)"><input ref={refs.correo} className={cls('correo', ' num')} id="cCorreo" type="email" placeholder="abarrera@ipn.mx" value={form.correo} onChange={set('correo')} /></Campo>
              <Campo id="cId" label="Identificador institucional (boleta o número de empleado)"><input ref={refs.id} className={cls('id', ' num')} id="cId" inputMode="numeric" placeholder="2021630154" value={form.id} onChange={set('id')} /></Campo>
              <div style={{ display: 'flex', alignItems: 'flex-end', fontSize: 11.5, lineHeight: 1.5, color: MUT60 }}>La cuenta se crea como Investigador.</div>
              <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8 }}>
                <button type="submit" className="btn btn-primary" style={btnLeft} disabled={guardando}>Guardar</button>
                <button type="button" className="btn btn-secondary" style={btnLeft} onClick={cancelar}>Cancelar</button>
              </div>
            </div>
            <FieldError msg={err.msg} style={{ padding: '0 16px 14px', margin: 0 }} />
          </form>
        )}
        {okCrear && (
          <div className="ok-bar" style={{ marginTop: 18, border: '2px solid var(--color-text)', borderLeft: '4px solid var(--color-text)' }}>
            <strong>Cuenta creada · se envió la contraseña temporal al correo</strong>
          </div>
        )}

        <hr className="hr" />

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, background: 'var(--color-divider)', border: '1px solid var(--color-divider)' }}>
          <div style={{ background: 'var(--color-bg)', padding: '18px 20px' }}>
            <div className="k" style={{ marginBottom: 12 }}>Cola de trabajos</div>
            {cola ? <ColaAdmin cola={cola.cola} /> : <p className="hint">Cargando…</p>}
            <p style={{ margin: '12px 0 0', fontSize: 11.5, lineHeight: 1.6, color: 'var(--muted-2)' }}>
              <span className="trace">T-06</span> La cola se atiende en orden de llegada; nadie la reordena. Reanalizar reemplaza el resultado anterior.
            </p>
          </div>

          <div style={{ background: 'var(--color-bg)', padding: '18px 20px' }}>
            <div className="k" style={{ marginBottom: 12 }}>Almacenamiento</div>
            {sistema ? <Disco d={sistema.disco} /> : <p className="hint">Cargando…</p>}
          </div>

          <div style={{ background: 'var(--color-bg)', padding: '18px 20px', gridColumn: '1 / -1', borderTop: '1px solid var(--color-divider)' }}>
            <div className="k" style={{ marginBottom: 14 }}>Conductas y modelo</div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, background: 'var(--color-divider)' }}>
              {sistema && catalogos(sistema).map((c) => (
                <div key={c.titulo} style={{ background: 'var(--color-bg)', paddingRight: 18 }}>
                  <div className="nd" style={{ fontSize: 13, marginBottom: 10 }}>{c.titulo}</div>
                  <div style={{ borderTop: '1px solid var(--color-divider)' }}>
                    {c.items.map((i) => (
                      <div key={i.n} style={{ display: 'flex', justifyContent: 'space-between', gap: 12, padding: '7px 0', borderBottom: '1px solid var(--color-divider)', fontSize: 12 }}>
                        <span>{i.n}</span><span className="num" style={{ fontSize: 11, color: 'var(--muted)' }}>{i.m}</span>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

function ColaAdmin({ cola }) {
  const proc = cola.filter((j) => j.status === 'RUNNING').length
  return (
    <>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, marginBottom: 14 }}>
        <span className="num nd" style={{ fontSize: 32, lineHeight: 1 }}>{proc}</span>
        <span style={{ fontSize: 12, color: 'var(--muted-2)' }}>procesando · {cola.length - proc} en cola</span>
      </div>
      <div style={{ borderTop: '1px solid var(--color-divider)' }}>
        {cola.map((j) => (
          <div key={j.job_id} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 0', borderBottom: '1px solid var(--color-divider)', fontSize: 12.5 }}>
            <span style={{ width: 8, height: 8, flex: 'none', background: j.status === 'RUNNING' ? 'var(--color-accent)' : 'transparent', border: '1px solid var(--color-divider)' }} />
            <span style={{ flex: 1 }}>{j.experimento} · {j.grupo} · Tanda {j.tanda} · {DIA[j.dia]}</span>
            <span className="num" style={{ fontSize: 11.5, color: 'var(--muted)' }}>{j.status === 'RUNNING' ? 'Procesando' : 'En cola'}</span>
          </div>
        ))}
      </div>
    </>
  )
}

// Al pasar del 80 % el número y la barra se resaltan y aparece el aviso.
function Disco({ d }) {
  const pct = Math.round((d.usado_gb / d.total_gb) * 100)
  const aviso = pct >= AVISO_DISCO_PCT
  const filas = [
    { k: 'Videos crudos (se borran a los 30 días)', v: gb(d.videos_gb) },
    { k: 'Resultados y reportes (permanentes)', v: gb(d.resultados_gb) },
    { k: 'Se liberan en los próximos 7 días', v: gb(d.por_liberar_7d_gb) },
  ]
  return (
    <>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, marginBottom: 11 }}>
        <span className="num nd" style={{ fontSize: 32, lineHeight: 1, color: aviso ? 'var(--color-accent-700)' : 'inherit' }}>{d.usado_gb}</span>
        <span className="num" style={{ fontSize: 12, color: 'var(--muted-2)' }}>GB de {d.total_gb} · {pct} %</span>
      </div>
      <div style={{ height: 10, background: 'color-mix(in srgb,var(--color-text) 12%,transparent)', marginBottom: 13 }}><div style={{ height: 10, background: aviso ? 'var(--color-accent)' : 'var(--color-text)', width: pct + '%' }} /></div>
      {aviso && (
        <div style={{ padding: '12px 13px', border: '1px solid var(--color-accent)', fontSize: 11.5, lineHeight: 1.6 }}>
          <strong>Aviso activo.</strong> Al llegar al {AVISO_DISCO_PCT} % el sistema avisa al administrador. Nunca borra videos por su cuenta: la retención de 30 días es el único mecanismo automático, y avisa antes.
        </div>
      )}
      <div style={{ marginTop: 13, borderTop: '1px solid var(--color-divider)' }}>
        {filas.map((f) => (
          <div key={f.k} style={{ display: 'flex', justifyContent: 'space-between', gap: 12, padding: '8px 0', borderBottom: '1px solid var(--color-divider)', fontSize: 11.5 }}>
            <span style={{ color: MUT70 }}>{f.k}</span><span className="num">{f.v}</span>
          </div>
        ))}
      </div>
    </>
  )
}

const catalogos = (s) => [
  { titulo: 'Conductas', items: s.conductas.map((c) => ({ n: c.nombre, m: '≥ ' + c.minimo_s + ' s' })) },
  { titulo: 'Modelo del clasificador', items: [{ n: 'Modelo en uso: ' + s.modelo, m: '' }] },
]
