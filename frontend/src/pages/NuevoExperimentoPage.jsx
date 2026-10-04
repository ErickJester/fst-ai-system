import React, { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import Topbar, { Brand } from '../components/Topbar'
import { FieldError, Campo, Pasos } from '../components/ui'
import { createExperiment } from '../services/experiments'

const btnLeft = { justifyContent: 'flex-start' }
const mutedSub = { fontSize: 12, color: 'color-mix(in srgb,var(--color-text) 60%,transparent)' }
const MAX_NOMBRE = 120

const hoy = () => {
  const d = new Date()
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
}

// 2b · Paso 1, «Datos generales»: crea el experimento y sigue a cargar video por tanda.
// Los grupos y sus tratamientos se definen en el paso 2, al cargar cada video.
export default function NuevoExperimentoPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ titulo: '', fecha_inicio: hoy(), especie: 'Rata Wistar', notas: '' })
  const [err, setErr] = useState({ msg: '', campo: null })
  const [guardando, setGuardando] = useState(false)
  const refs = { titulo: useRef(), fecha_inicio: useRef() }

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const cls = (k) => 'input' + (err.campo === k ? ' error' : '')

  function fail(campo, msg) {
    setErr({ campo, msg })
    if (campo) refs[campo].current.focus()
  }

  async function continuar(e) {
    e.preventDefault()
    if (guardando) return
    const titulo = form.titulo.trim()
    if (!titulo) return fail('titulo', 'Escribe el nombre del experimento.')
    if (titulo.length > MAX_NOMBRE) return fail('titulo', 'El nombre admite hasta ' + MAX_NOMBRE + ' caracteres.')
    if (!form.fecha_inicio) return fail('fecha_inicio', 'Indica la fecha de inicio.')
    fail(null, '')
    setGuardando(true)
    try {
      const { clave } = await createExperiment({ titulo, fecha_inicio: form.fecha_inicio, especie: form.especie.trim(), notas: form.notas.trim() })
      navigate(`/experimentos/${clave}/cargar`)
    } catch (e) {
      fail(null, e.response?.data?.error || 'No se pudo crear el experimento. Inténtalo de nuevo.')
      setGuardando(false)
    }
  }

  return (
    <div className="app">
      <Topbar>
        <Brand sub="Nuevo experimento" />
        <Link to="/experimentos">Salir</Link>
      </Topbar>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) 430px' }}>
        <form noValidate onSubmit={continuar} style={{ padding: '28px 32px 36px', borderRight: '2px solid var(--color-divider)' }}>
          <Pasos actual={1} />

          <h3 style={{ margin: '0 0 8px' }}>Datos generales</h3>
          <p className="lead" style={{ margin: '0 0 24px', maxWidth: 620 }}>Nombre y fecha del experimento. Los grupos y sus tratamientos se definen en el siguiente paso, al cargar el video de cada tanda.</p>

          <div style={{ borderTop: '2px solid var(--color-divider)' }}>
            <div className="step">
              <span className="step-n num">1</span>
              <Campo id="nTitulo" label="Nombre del experimento">
                <input ref={refs.titulo} className={cls('titulo')} id="nTitulo" autoComplete="off" autoFocus maxLength={MAX_NOMBRE}
                  placeholder="Compuesto CSR-14 · curva de dosis" value={form.titulo} onChange={set('titulo')} />
                <div style={{ marginTop: 6, ...mutedSub }}>Así aparece en la lista de experimentos y en los reportes.</div>
              </Campo>
            </div>

            <div className="step">
              <span className="step-n num">2</span>
              <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
                <Campo id="nFecha" label="Fecha de inicio" style={{ width: 200 }}>
                  <input ref={refs.fecha_inicio} className={cls('fecha_inicio') + ' num'} id="nFecha" type="date" value={form.fecha_inicio} onChange={set('fecha_inicio')} />
                </Campo>
                <Campo id="nEspecie" label="Especie o cepa" style={{ flex: 1, minWidth: 200 }}>
                  <input className="input" id="nEspecie" value={form.especie} onChange={set('especie')} />
                </Campo>
              </div>
            </div>

            <div className="step" style={{ borderBottom: 0, paddingBottom: 0 }}>
              <span className="step-n num">3</span>
              <div>
                <Campo id="nNotas" label="Notas · opcional">
                  <textarea className="input" id="nNotas" placeholder="Condiciones especiales, referencias internas…" value={form.notas} onChange={set('notas')} />
                </Campo>
                <FieldError msg={err.msg} style={{ marginTop: 10 }} />
                <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 14, marginTop: 18 }}>
                  <button type="submit" className="btn btn-primary" style={btnLeft} disabled={guardando}>Crear y cargar videos</button>
                  <span style={{ fontSize: 11.5, color: 'var(--muted)' }}>Responsable: {user.nombre} {user.apellidos}</span>
                </div>
              </div>
            </div>
          </div>
        </form>

        <div style={{ background: 'var(--color-surface)', padding: '28px 24px', display: 'flex', flexDirection: 'column', gap: 20 }}>
          <div style={{ background: 'var(--color-bg)', border: '2px solid var(--color-text)' }}>
            <div className="k" style={{ padding: '10px 14px', borderBottom: '1px solid var(--color-divider)' }}>Qué sigue</div>
            <div style={{ padding: 14, display: 'flex', flexDirection: 'column', gap: 10, fontSize: 12.5, lineHeight: 1.6 }}>
              <div>En el paso 2 cargas un video por tanda: indicas el grupo (o creas uno nuevo con su tipo y tratamiento), confirmas la tanda y sueltas el archivo .mp4.</div>
              <div style={{ color: 'var(--muted-2)' }}>El experimento aparece en la lista como <strong>Carga incompleta</strong> hasta que tenga videos de Día 2.</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
