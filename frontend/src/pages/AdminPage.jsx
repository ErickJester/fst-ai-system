import React, { useRef, useState } from 'react'
import { useAuth } from '../contexts/AuthContext'
import Topbar, { Brand } from '../components/Topbar'
import { FieldError } from '../components/ui'
import { CUENTAS, COLA_ADMIN, DISCO, CATALOGOS } from '../data/mock'
import { isIpn } from '../lib/fst'

const MUT70 = 'color-mix(in srgb,var(--color-text) 70%,transparent)'
const btnLeft = { justifyContent: 'flex-start' }
const VACIO = { nombre: '', apellidos: '', correo: '', id: '' }

// 2g · Administración: usuarios, cola y disco.
export default function AdminPage() {
  const { user } = useAuth()
  const [usuarios, setUsuarios] = useState(CUENTAS)
  const [crear, setCrear] = useState(false)
  const [okCrear, setOkCrear] = useState(false)
  const [form, setForm] = useState(VACIO)
  const [err, setErr] = useState({ msg: '', campo: null })
  const refs = { nombre: useRef(), apellidos: useRef(), correo: useRef(), id: useRef() }

  const adminsActivos = usuarios.filter((u) => u.rol === 'Administrador' && u.activa).length

  function desactivar(u) {
    if (!window.confirm('¿Desactivar la cuenta de ' + u.nombre + '?')) return
    setUsuarios(usuarios.map((x) => (x === u ? { ...x, activa: false } : x)))
  }

  function fail(campo, msg) {
    setErr({ campo, msg })
    if (campo) refs[campo].current.focus()
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

  function guardar(e) {
    e.preventDefault()
    const v = Object.fromEntries(Object.entries(form).map(([k, x]) => [k, x.trim()]))
    if (!v.nombre) return fail('nombre', 'Escribe el nombre.')
    if (!v.apellidos) return fail('apellidos', 'Escribe los apellidos.')
    if (!isIpn(v.correo)) return fail('correo', 'El correo debe ser institucional (@ipn.mx).')
    if (usuarios.some((u) => u.correo.toLowerCase() === v.correo.toLowerCase())) return fail('correo', 'Ya existe una cuenta con ese correo.')
    if (!/^\d+$/.test(v.id)) return fail('id', 'El identificador institucional es numérico (boleta o número de empleado).')
    fail(null, '')
    const ini = v.nombre.split(/\s+/).map((p) => p[0] + '.').join(' ')
    setUsuarios([...usuarios, { nombre: ini + ' ' + v.apellidos, correo: v.correo.toLowerCase(), rol: 'Investigador', activa: true }])
    setForm(VACIO)
    setCrear(false)
    setOkCrear(true)
  }

  const cls = (k, extra = '') => 'input' + extra + (err.campo === k ? ' error' : '')
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
          <div className="k">Cuentas · {usuarios.filter((u) => u.activa).length} activas</div>
          <div style={{ flex: 1 }} />
          <button type="button" className="btn btn-primary" style={btnLeft} onClick={abrir}>Crear cuenta</button>
        </div>
        <table className="table">
          <thead><tr><th>Persona</th><th>Correo</th><th>Rol</th><th>Estado</th><th style={{ textAlign: 'right' }}>Acción</th></tr></thead>
          <tbody>
            {usuarios.map((u) => {
              const ultimoAdmin = u.rol === 'Administrador' && u.activa && adminsActivos <= 1
              const yo = u.correo === user.correo
              return (
                <tr key={u.correo}>
                  <td className="nd" style={{ fontSize: 13 }}>{u.nombre}</td>
                  <td className="num" style={{ fontSize: 12.5, color: MUT70 }}>{u.correo}</td>
                  <td style={{ fontSize: 12.5 }}>{u.rol}</td>
                  <td><span className="tag tag-neutral">{u.activa ? 'Activa' : 'Inactiva'}{yo ? ' · tú' : ''}</span></td>
                  <td style={{ textAlign: 'right' }}>
                    {u.activa && ultimoAdmin && (
                      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 3 }}>
                        <button type="button" className="btn btn-ghost" disabled>Desactivar</button>
                        <span style={{ fontSize: 11, color: 'var(--color-accent-700)', whiteSpace: 'nowrap' }}>Debe existir al menos un Administrador activo</span>
                      </div>
                    )}
                    {u.activa && !ultimoAdmin && <button type="button" className="btn btn-ghost" onClick={() => desactivar(u)}>Desactivar</button>}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>

        {crear && (
          <form noValidate onSubmit={guardar} style={{ marginTop: 18, border: '2px solid var(--color-text)' }}>
            <div className="nd" style={{ padding: '12px 16px', borderBottom: '1px solid var(--color-divider)', fontSize: 14 }}>Crear cuenta</div>
            <div style={{ padding: 16, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
              <div className="field"><label htmlFor="cNombre">Nombre</label><input ref={refs.nombre} className={cls('nombre')} id="cNombre" placeholder="Andrea" value={form.nombre} onChange={set('nombre')} /></div>
              <div className="field"><label htmlFor="cApe">Apellidos</label><input ref={refs.apellidos} className={cls('apellidos')} id="cApe" placeholder="Barrera Solís" value={form.apellidos} onChange={set('apellidos')} /></div>
              <div className="field"><label htmlFor="cCorreo">Correo institucional (@ipn.mx)</label><input ref={refs.correo} className={cls('correo', ' num')} id="cCorreo" type="email" placeholder="abarrera@ipn.mx" value={form.correo} onChange={set('correo')} /></div>
              <div className="field"><label htmlFor="cId">Identificador institucional (boleta o número de empleado)</label><input ref={refs.id} className={cls('id', ' num')} id="cId" inputMode="numeric" placeholder="2021630154" value={form.id} onChange={set('id')} /></div>
              <div style={{ display: 'flex', alignItems: 'flex-end', fontSize: 11.5, lineHeight: 1.5, color: 'color-mix(in srgb,var(--color-text) 60%,transparent)' }}>La cuenta se crea como Investigador.</div>
              <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8 }}>
                <button type="submit" className="btn btn-primary" style={btnLeft}>Guardar</button>
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
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, marginBottom: 14 }}>
              <span className="num nd" style={{ fontSize: 32, lineHeight: 1 }}>1</span>
              <span style={{ fontSize: 12, color: 'var(--muted-2)' }}>procesando · 2 en cola</span>
            </div>
            <div style={{ borderTop: '1px solid var(--color-divider)' }}>
              {COLA_ADMIN.map((c) => (
                <div key={c.nombre} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 0', borderBottom: '1px solid var(--color-divider)', fontSize: 12.5 }}>
                  <span style={{ width: 8, height: 8, flex: 'none', background: c.dot, border: '1px solid var(--color-divider)' }} />
                  <span style={{ flex: 1 }}>{c.nombre}</span>
                  <span className="num" style={{ fontSize: 11.5, color: 'var(--muted)' }}>{c.t}</span>
                </div>
              ))}
            </div>
            <p style={{ margin: '12px 0 0', fontSize: 11.5, lineHeight: 1.6, color: 'var(--muted-2)' }}>
              La cola se atiende en orden de llegada; nadie la reordena. Reanalizar reemplaza el resultado anterior.
            </p>
          </div>

          <div style={{ background: 'var(--color-bg)', padding: '18px 20px' }}>
            <div className="k" style={{ marginBottom: 12 }}>Almacenamiento</div>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, marginBottom: 11 }}>
              <span className="num nd" style={{ fontSize: 32, lineHeight: 1, color: 'var(--color-accent-700)' }}>412</span>
              <span className="num" style={{ fontSize: 12, color: 'var(--muted-2)' }}>GB de 500 · 82 %</span>
            </div>
            <div style={{ height: 10, background: 'color-mix(in srgb,var(--color-text) 12%,transparent)', marginBottom: 13 }}><div style={{ height: 10, background: 'var(--color-accent)', width: '82%' }} /></div>
            <div style={{ padding: '12px 13px', border: '1px solid var(--color-accent)', fontSize: 11.5, lineHeight: 1.6 }}>
              <strong>Aviso activo.</strong> Al llegar al 80 % el sistema avisa al administrador. Nunca borra videos por su cuenta: la retención de 30 días es el único mecanismo automático, y avisa antes.
            </div>
            <div style={{ marginTop: 13, borderTop: '1px solid var(--color-divider)' }}>
              {DISCO.map((d) => (
                <div key={d.k} style={{ display: 'flex', justifyContent: 'space-between', gap: 12, padding: '8px 0', borderBottom: '1px solid var(--color-divider)', fontSize: 11.5 }}>
                  <span style={{ color: MUT70 }}>{d.k}</span><span className="num">{d.v}</span>
                </div>
              ))}
            </div>
          </div>

          <div style={{ background: 'var(--color-bg)', padding: '18px 20px', gridColumn: '1 / -1', borderTop: '1px solid var(--color-divider)' }}>
            <div className="k" style={{ marginBottom: 14 }}>Conductas y modelo</div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, background: 'var(--color-divider)' }}>
              {CATALOGOS.map((c) => (
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
