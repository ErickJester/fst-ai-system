import React, { useState, useRef, useEffect } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import DemoBar from '../components/DemoBar'
import '../styles/pages/nuevo.css'

const DEMO_STATES = [
  { key: 'empty', label: 'Formulario vacío' },
  { key: 'filled', label: 'Datos ingresados' },
  { key: 'uploading', label: 'Subiendo video' },
  { key: 'fileerror', label: 'Error de formato' },
  { key: 'success', label: 'Carga exitosa' },
  { key: 'validation', label: 'Errores de validación' },
]

const FORM_VACIO = { nombre: '', fecha: '2025-03-22', especimenes: '', tratamiento: '', notas: '' }
const FORM_LLENO = {
  nombre: 'Ketamina 30 mg/kg — Grupo A',
  fecha: '2025-03-22',
  especimenes: '4',
  tratamiento: 'Ketamina sub-anestésica 30 mg/kg i.p., 30 min antes de la sesión Día 2.',
  notas: '',
}
const ZONA_VACIA = { estado: 'idle' }
const SIN_ERRORES = { nombre: false, especimenes: false, tratamiento: false }

const FORMATO_VALIDO = /\.(mp4|mov)$/i

// Lee ancho y alto del video en el navegador para rechazar videos en vertical.
function leerOrientacion(file) {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const v = document.createElement('video')
    v.preload = 'metadata'
    v.onloadedmetadata = () => { URL.revokeObjectURL(url); resolve(v.videoWidth >= v.videoHeight ? 'horizontal' : 'vertical') }
    v.onerror = () => { URL.revokeObjectURL(url); resolve('desconocida') }
    v.src = url
  })
}

const IconoError = () => (
  <svg width="12" height="12" viewBox="0 0 12 12" fill="none"><circle cx="6" cy="6" r="5" stroke="currentColor" strokeWidth="1.1"/><path d="M6 4v3" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round"/><circle cx="6" cy="8.5" r=".6" fill="currentColor"/></svg>
)
const IconoFlecha = () => (
  <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><path d="M2 6.5h9M7 2.5l4 4-4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/></svg>
)

function UploadZone({ dia, titulo, zona, opcional, onPick, onRemove }) {
  const inputRef = useRef(null)
  const [drag, setDrag] = useState(false)
  const clases = ['upload-zone']
  if (opcional) clases.push('optional')
  if (drag) clases.push('dragover')
  if (zona.estado === 'uploading') clases.push('uploading')
  if (zona.estado === 'ok') clases.push('has-file')
  if (zona.estado === 'error') clases.push('file-error')

  const abrir = () => { if (zona.estado === 'idle') inputRef.current?.click() }

  return (
    <div
      className={clases.join(' ')}
      onClick={abrir}
      onDragOver={(e) => { e.preventDefault(); setDrag(true) }}
      onDragLeave={() => setDrag(false)}
      onDrop={(e) => { e.preventDefault(); setDrag(false); const f = e.dataTransfer.files[0]; if (f) onPick(f) }}
    >
      <input
        ref={inputRef}
        type="file"
        accept=".mp4,.mov,video/mp4,video/quicktime"
        style={{ display: 'none' }}
        onChange={(e) => { const f = e.target.files[0]; if (f) onPick(f); e.target.value = '' }}
      />
      {opcional ? <span className="upload-optional-tag">Opcional</span> : <span className="upload-req-tag">Opcional</span>}

      {zona.estado === 'idle' && (
        <div>
          <div className="upload-icon">
            <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
              <rect x="4" y="6" width="24" height="20" rx="3" stroke="#9ca3af" strokeWidth="1.5"/>
              <path d="M13 22l3-3 3 3M16 19v-6" stroke="#9ca3af" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
              <path d="M13 14h-2a1 1 0 00-1 1v4a1 1 0 001 1h10a1 1 0 001-1v-4a1 1 0 00-1-1h-2" stroke="#9ca3af" strokeWidth="1" strokeLinecap="round" strokeDasharray="2 1.5"/>
            </svg>
          </div>
          <div className="upload-label-day">{dia}</div>
          <div className="upload-title">{titulo}</div>
          <div className="upload-sub">Arrastra o haz clic para seleccionar<br /><span className="format-chip">.mp4</span> o <span className="format-chip">.mov</span></div>
          <div className="upload-cta">Seleccionar archivo</div>
        </div>
      )}

      {zona.estado === 'uploading' && (
        <div className="upload-progress" style={{ display: 'flex' }}>
          <svg width="28" height="28" viewBox="0 0 28 28" fill="none"><circle cx="14" cy="14" r="12" stroke="#93c5fd" strokeWidth="2"/><path d="M14 14 l0-7" stroke="#3b82f6" strokeWidth="2" strokeLinecap="round"><animateTransform attributeName="transform" type="rotate" from="0 14 14" to="360 14 14" dur="1s" repeatCount="indefinite"/></path></svg>
          <div className="progress-label">Subiendo… {Math.round(zona.pct)}%</div>
          <div className="progress-bar-wrap" style={{ width: '100%' }}>
            <div className="progress-bar" style={{ width: `${zona.pct}%` }}></div>
          </div>
          <div style={{ fontSize: '11.5px', color: 'var(--c-text-muted)' }}>{zona.nombre}</div>
        </div>
      )}

      {zona.estado === 'ok' && (
        <div className="file-info" style={{ display: 'flex', flexDirection: 'column' }}>
          <svg width="28" height="28" viewBox="0 0 28 28" fill="none"><circle cx="14" cy="14" r="12" stroke="#6ee7b7" strokeWidth="1.5" fill="#ecfdf5"/><path d="M8 14l4 4 8-8" stroke="#10b981" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>
          <div className="upload-label-day" style={{ color: '#065f46' }}>{dia} cargado</div>
          <div className="file-name">{zona.nombre}</div>
          <div className="file-size">{zona.tamano}</div>
          <button className="file-remove" onClick={(e) => { e.stopPropagation(); onRemove() }}>Eliminar y subir otro</button>
        </div>
      )}

      {zona.estado === 'error' && (
        <div className="file-error-msg" style={{ display: 'flex', flexDirection: 'column' }}>
          <svg width="28" height="28" viewBox="0 0 28 28" fill="none"><circle cx="14" cy="14" r="12" stroke="#fca5a5" strokeWidth="1.5" fill="#fef2f2"/><path d="M9 9l10 10M19 9L9 19" stroke="#ef4444" strokeWidth="1.8" strokeLinecap="round"/></svg>
          <div className="file-error-title">{zona.vertical ? 'Video en vertical' : 'Formato no válido'}</div>
          <div className="file-error-detail">
            {zona.vertical ? (
              <>El archivo <strong>{zona.nombre}</strong> está en vertical.<br />Solo se pueden procesar videos en horizontal.</>
            ) : (
              <>El archivo <strong>{zona.nombre}</strong> no es un .mp4 ni .mov válido.<br />Convierte el video antes de subirlo.</>
            )}
          </div>
          <button className="file-retry" onClick={(e) => { e.stopPropagation(); onRemove() }}>Intentar con otro archivo</button>
        </div>
      )}
    </div>
  )
}

export default function NewExperimentPage() {
  const navigate = useNavigate()
  const [demo, setDemo] = useState('empty')
  const [form, setForm] = useState(FORM_VACIO)
  const [errores, setErrores] = useState(SIN_ERRORES)
  const [zona1, setZona1] = useState(ZONA_VACIA)
  const [zona2, setZona2] = useState(ZONA_VACIA)
  const [exito, setExito] = useState(false)
  const timers = useRef([])

  useEffect(() => () => timers.current.forEach(clearInterval), [])

  const set = (campo) => (e) => {
    setForm((f) => ({ ...f, [campo]: e.target.value }))
    setErrores((er) => ({ ...er, [campo]: false }))
  }

  const simularSubida = (setZona, nombre, tamano) => {
    let pct = 0
    setZona({ estado: 'uploading', nombre, tamano, pct })
    const iv = setInterval(() => {
      pct = Math.min(pct + Math.random() * 12 + 4, 100)
      if (pct >= 100) {
        clearInterval(iv)
        setZona({ estado: 'ok', nombre, tamano })
      } else {
        setZona({ estado: 'uploading', nombre, tamano, pct })
      }
    }, 160)
    timers.current.push(iv)
  }

  const elegir = (setZona) => async (file) => {
    if (!FORMATO_VALIDO.test(file.name)) {
      setZona({ estado: 'error', nombre: file.name })
      return
    }
    if (await leerOrientacion(file) === 'vertical') {
      setZona({ estado: 'error', nombre: file.name, vertical: true })
      return
    }
    simularSubida(setZona, file.name, `${(file.size / 1024 / 1024).toFixed(0)} MB`)
  }

  const cambiarDemo = (s) => {
    timers.current.forEach(clearInterval)
    setDemo(s)
    setExito(false)
    setErrores(SIN_ERRORES)
    setZona1(ZONA_VACIA)
    setZona2(ZONA_VACIA)
    setForm(FORM_VACIO)
    if (s === 'filled') setForm(FORM_LLENO)
    if (s === 'uploading') {
      setForm(FORM_LLENO)
      setZona1({ estado: 'uploading', nombre: 'dia1_baseline_20min.mp4', tamano: '312 MB', pct: 47 })
      setZona2({ estado: 'uploading', nombre: 'dia2_posttreatment_5min.mp4', tamano: '78 MB', pct: 8 })
    }
    if (s === 'fileerror') {
      setForm(FORM_LLENO)
      setZona1({ estado: 'ok', nombre: 'dia1_baseline_20min.mp4', tamano: '312 MB' })
      setZona2({ estado: 'error', nombre: 'grabacion_dia2.avi' })
    }
    if (s === 'success') setExito(true)
    if (s === 'validation') {
      setForm({ ...FORM_VACIO, especimenes: '7' })
      setErrores({ nombre: true, especimenes: true, tratamiento: true })
    }
  }

  const enviar = () => {
    const n = Number(form.especimenes)
    const er = {
      nombre: !form.nombre.trim(),
      especimenes: !(n >= 2 && n <= 4),
      tratamiento: !form.tratamiento.trim(),
    }
    setErrores(er)
    if (!er.nombre && !er.especimenes && !er.tratamiento) setExito(true)
  }

  const paso = exito ? 3 : 1
  const Paso = ({ n, label }) => (
    <div className={`step ${n < paso ? 'done' : n === paso ? 'active' : ''}`}>
      <div className="step-circle">
        {n < paso ? (
          <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><path d="M2 6.5l3.5 3.5 5.5-5.5" stroke="white" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>
        ) : n}
      </div>
      <div className="step-label">{label}</div>
    </div>
  )

  return (
    <div className="pg-nuevo">
      <DemoBar states={DEMO_STATES} value={demo} onChange={cambiarDemo} />

      <main className="page">
        <nav className="breadcrumb">
          <Link to="/dashboard">Mis experimentos</Link>
          <span className="breadcrumb-sep">›</span>
          <span>Nuevo experimento</span>
        </nav>

        <div className="page-header">
          <div className="page-title">Nuevo experimento</div>
          <div className="page-subtitle">Ingresa los metadatos y sube los videos del FST. El análisis comenzará automáticamente.</div>
        </div>

        <div className="steps">
          <Paso n={1} label="Metadatos" />
          <div className={`step-line ${paso > 1 ? 'done' : ''}`}></div>
          <Paso n={2} label="Videos" />
          <div className={`step-line ${paso > 2 ? 'done' : ''}`}></div>
          <Paso n={3} label="Confirmación" />
        </div>

        {exito ? (
          <div className="card">
            <div className="success-panel">
              <div className="success-icon">
                <svg width="26" height="26" viewBox="0 0 26 26" fill="none">
                  <path d="M5 13l6 6 10-10" stroke="#10b981" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </div>
              <div className="success-title">Experimento creado exitosamente</div>
              <div className="success-sub">
                Los videos se cargaron correctamente. El sistema ha iniciado el análisis conductual de forma automática — no es necesaria ninguna acción adicional.
              </div>
              <div className="success-chip">
                <span className="chip-dot"></span>
                Análisis en proceso — FST-2024-036 "{form.nombre || 'Ketamina 30 mg/kg'}"
              </div>
              <div style={{ display: 'flex', gap: '10px', marginTop: '8px', flexWrap: 'wrap', justifyContent: 'center' }}>
                <button className="btn-ghost" onClick={() => navigate('/dashboard')}>← Volver al dashboard</button>
                <button className="btn-primary" onClick={() => navigate('/experiments/36/progress')}>
                  Ver progreso del análisis
                  <IconoFlecha />
                </button>
              </div>
            </div>
          </div>
        ) : (
          <div>
            <div className="card">
              <div className="card-header">
                <div className="card-header-icon">
                  <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                    <rect x="2" y="2" width="12" height="12" rx="2" stroke="#4b6490" strokeWidth="1.2"/>
                    <path d="M5 6h6M5 9h4" stroke="#4b6490" strokeWidth="1.2" strokeLinecap="round"/>
                  </svg>
                </div>
                <div>
                  <div className="card-title">Información del experimento</div>
                  <div className="card-subtitle">Todos los campos marcados con * son obligatorios.</div>
                </div>
              </div>
              <div className="card-body">
                <div className="form-grid">
                  <div className="form-group span2">
                    <label className="form-label" htmlFor="expName">Nombre del experimento <span className="req">*</span></label>
                    <div className="input-wrap">
                      <span className="input-icon">
                        <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M2 4h10M2 7h7M2 10h5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round"/></svg>
                      </span>
                      <input id="expName" className={`form-input ${errores.nombre ? 'error' : ''}`} type="text" placeholder="Ej. Ketamina 30 mg/kg — Grupo A" value={form.nombre} onChange={set('nombre')} />
                    </div>
                    {errores.nombre && <div className="field-error" style={{ display: 'flex' }}><IconoError />Este campo es obligatorio.</div>}
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor="expFecha">Fecha del experimento <span className="req">*</span></label>
                    <div className="input-wrap">
                      <span className="input-icon">
                        <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><rect x="1.5" y="2.5" width="11" height="10" rx="1.5" stroke="currentColor" strokeWidth="1.2"/><path d="M1.5 6h11M5 1v3M9 1v3" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round"/></svg>
                      </span>
                      <input id="expFecha" className="form-input" type="date" value={form.fecha} onChange={set('fecha')} />
                    </div>
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor="expEspecimenes">Especímenes de la primera tanda (2 a 4) <span className="req">*</span></label>
                    <div className="input-wrap">
                      <span className="input-icon">
                        <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><circle cx="5" cy="5" r="2" stroke="currentColor" strokeWidth="1.2"/><path d="M1.5 12.5c0-2.2 1.6-3.5 3.5-3.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round"/><circle cx="10" cy="5" r="2" stroke="currentColor" strokeWidth="1.2"/><path d="M12.5 12.5c0-2.2-1.6-3.5-3.5-3.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round"/></svg>
                      </span>
                      <input id="expEspecimenes" className={`form-input form-input-plain ${errores.especimenes ? 'error' : ''}`} type="number" min="2" max="4" placeholder="4" style={{ paddingLeft: '34px' }} value={form.especimenes} onChange={set('especimenes')} />
                    </div>
                    <div className="form-hint">Entre 2 y 4, uno por cada cilindro visible en el video.</div>
                    {errores.especimenes && <div className="field-error" style={{ display: 'flex' }}><IconoError />Ingresa un número entre 2 y 4.</div>}
                  </div>

                  <div className="form-group span2">
                    <label className="form-label" htmlFor="expTrat">Tratamiento / condición experimental <span className="req">*</span></label>
                    <div className="input-wrap">
                      <span className="input-icon" style={{ top: '14px', transform: 'none' }}>
                        <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M5 2h4l1 4H4L5 2z" stroke="currentColor" strokeWidth="1.1" strokeLinejoin="round"/><rect x="2" y="6" width="10" height="6" rx="1.5" stroke="currentColor" strokeWidth="1.2"/><path d="M5 9h4" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round"/></svg>
                      </span>
                      <textarea id="expTrat" className={`form-textarea ${errores.tratamiento ? 'error' : ''}`} style={{ paddingLeft: '34px' }} placeholder="Ej. Ketamina sub-anestésica 30 mg/kg i.p., 30 min antes de la sesión Día 2. Grupo control recibió vehículo (solución salina 0.9%)." value={form.tratamiento} onChange={set('tratamiento')}></textarea>
                    </div>
                    {errores.tratamiento && <div className="field-error" style={{ display: 'flex' }}><IconoError />Describe el tratamiento o condición experimental.</div>}
                  </div>

                  <div className="form-group span2">
                    <label className="form-label" htmlFor="expNotas">Notas adicionales <span style={{ fontWeight: '400', color: 'var(--c-text-muted)' }}>(opcional)</span></label>
                    <textarea id="expNotas" className="form-textarea" placeholder="Observaciones, condiciones especiales, referencias internas…" value={form.notas} onChange={set('notas')}></textarea>
                  </div>
                </div>
              </div>
            </div>

            <div className="card">
              <div className="card-header">
                <div className="card-header-icon">
                  <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                    <rect x="1.5" y="3.5" width="11" height="9" rx="1.5" stroke="#4b6490" strokeWidth="1.2"/>
                    <path d="M12.5 7l2.5-2v6l-2.5-2V7z" stroke="#4b6490" strokeWidth="1.2" strokeLinejoin="round"/>
                  </svg>
                </div>
                <div>
                  <div className="card-title">Videos del experimento</div>
                  <div className="card-subtitle">Puedes subir uno o ambos videos; el análisis usará los que proporciones.</div>
                </div>
              </div>
              <div className="card-body">
                <div className="upload-grid">
                  <div>
                    <UploadZone dia="Día 1" titulo="Sesión basal (20 min)" zona={zona1} opcional onPick={elegir(setZona1)} onRemove={() => setZona1(ZONA_VACIA)} />
                  </div>
                  <div>
                    <UploadZone dia="Día 2" titulo="Sesión post-tratamiento (5 min)" zona={zona2} onPick={elegir(setZona2)} onRemove={() => setZona2(ZONA_VACIA)} />
                  </div>
                </div>

                <div className="format-note">
                  <svg width="16" height="16" viewBox="0 0 16 16" fill="none" style={{ flexShrink: 0, marginTop: '1px' }}><circle cx="8" cy="8" r="6.5" stroke="#3b6fb6" strokeWidth="1.2"/><path d="M8 7v4" stroke="#3b6fb6" strokeWidth="1.3" strokeLinecap="round"/><circle cx="8" cy="5.5" r=".7" fill="#3b6fb6"/></svg>
                  <span><strong>Solo se aceptan archivos .mp4 o .mov en horizontal.</strong> Si tu video está en otro formato o en vertical, conviértelo antes de subirlo. El sistema validará el formato antes de iniciar la carga.</span>
                </div>
              </div>
            </div>

            <div className="card">
              <div className="card-body">
                <div className="auto-notice">
                  <svg width="18" height="18" viewBox="0 0 18 18" fill="none" style={{ flexShrink: 0, marginTop: '1px' }}>
                    <path d="M9 2L2 7v9h5v-4h4v4h5V7L9 2z" stroke="#0284c7" strokeWidth="1.3" strokeLinejoin="round"/>
                    <path d="M11 4l4 2.5" stroke="#0284c7" strokeWidth="1.1" strokeLinecap="round"/>
                  </svg>
                  <div className="auto-notice-text">
                    <strong>El análisis inicia automáticamente</strong>
                    Una vez completada la carga, el sistema procesará los videos sin necesidad de ninguna acción adicional. Puedes cerrar esta pantalla y revisar el progreso desde el dashboard.
                  </div>
                </div>
              </div>
            </div>

            <div className="action-bar">
              <button className="btn-ghost" onClick={() => navigate('/dashboard')}>← Cancelar</button>
              <button className="btn-primary" onClick={enviar}>
                Crear experimento y cargar videos
                <IconoFlecha />
              </button>
            </div>
          </div>
        )}
      </main>
    </div>
  )
}
