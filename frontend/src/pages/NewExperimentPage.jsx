import React, { useState, useRef } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useApi } from '../hooks/useApi'

function StepIndicator({ current }) {
  const labels = ['Datos básicos', 'Videos', 'Confirmar']
  return (
    <div className="steps">
      {labels.map((label, i) => {
        const n = i + 1
        const isDone = n < current
        const isActive = n === current
        return (
          <React.Fragment key={n}>
            <div className={`step ${isDone ? 'done' : ''} ${isActive ? 'active' : ''}`}>
              <div className="step-circle">
                {isDone ? (
                  <svg width="12" height="12" viewBox="0 0 12 12" fill="none"><path d="M2.5 6l2.5 2.5L9.5 4" stroke="#fff" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>
                ) : n}
              </div>
              <span className="step-label">{label}</span>
            </div>
            {i < labels.length - 1 && <div className={`step-line ${isDone ? 'done' : ''}`} />}
          </React.Fragment>
        )
      })}
    </div>
  )
}

function UploadZone({ day, label, required, file, onFile, uploading, progress }) {
  const inputRef = useRef(null)
  const [dragover, setDragover] = useState(false)

  const handleDrop = (e) => {
    e.preventDefault()
    setDragover(false)
    const f = e.dataTransfer.files?.[0]
    if (f) onFile(f)
  }

  const cls = [
    'upload-zone',
    dragover ? 'dragover' : '',
    file ? 'has-file' : '',
    uploading ? 'uploading' : '',
  ].filter(Boolean).join(' ')

  return (
    <div
      className={cls}
      onClick={() => !file && !uploading && inputRef.current?.click()}
      onDragOver={(e) => { e.preventDefault(); setDragover(true) }}
      onDragLeave={() => setDragover(false)}
      onDrop={handleDrop}
    >
      <input
        ref={inputRef}
        type="file"
        accept="video/*"
        style={{ display: 'none' }}
        onChange={(e) => { if (e.target.files?.[0]) onFile(e.target.files[0]) }}
      />

      {required ? (
        <span className="upload-req-tag">Obligatorio</span>
      ) : (
        <span className="upload-optional-tag">Opcional</span>
      )}

      {uploading ? (
        <div style={{ width: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="10" stroke="#93c5fd" strokeWidth="2"/><path d="M12 6v6l3 3" stroke="#3b82f6" strokeWidth="2" strokeLinecap="round"/></svg>
          <div className="upload-progress-bar-wrap">
            <div className="upload-progress-bar" style={{ width: `${progress}%` }} />
          </div>
          <span className="upload-progress-label">Subiendo… {progress}%</span>
        </div>
      ) : file ? (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="10" stroke="#6ee7b7" strokeWidth="2"/><path d="M8 12l3 3 5-5" stroke="#10b981" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>
          <span className="file-name">{file.name}</span>
          <span className="file-size">{(file.size / (1024 * 1024)).toFixed(1)} MB</span>
          <button type="button" className="file-remove" onClick={(e) => { e.stopPropagation(); onFile(null) }}>Eliminar</button>
        </div>
      ) : (
        <>
          <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
            <rect x="2" y="5" width="18" height="14" rx="2" stroke="#9ca3af" strokeWidth="1.5"/>
            <path d="M20 10l6-3v14l-6-3V10z" stroke="#9ca3af" strokeWidth="1.5" strokeLinejoin="round"/>
          </svg>
          <span className="upload-label-day">{day}</span>
          <span className="upload-title">{label}</span>
          <span className="upload-sub">
            Arrastra un archivo o haz clic · <span className="format-chip">.mp4</span> <span className="format-chip">.avi</span>
          </span>
          <span className="upload-cta">Seleccionar archivo</span>
        </>
      )}
    </div>
  )
}

const INITIAL_FORM = {
  name: '',
  treatment: '',
  species: 'Rata Wistar',
  animals: 4,
  duration: 300,
  notes: '',
}

export default function NewExperimentPage() {
  const api = useApi()
  const navigate = useNavigate()
  const [step, setStep] = useState(1)
  const [form, setForm] = useState(INITIAL_FORM)
  const [errors, setErrors] = useState({})
  const [fileDay1, setFileDay1] = useState(null)
  const [fileDay2, setFileDay2] = useState(null)
  const [uploading, setUploading] = useState(false)
  const [uploadProgress, setUploadProgress] = useState({ day1: 0, day2: 0 })
  const [submitting, setSubmitting] = useState(false)

  const set = (key, val) => setForm((f) => ({ ...f, [key]: val }))

  const validateStep1 = () => {
    const errs = {}
    if (!form.name.trim()) errs.name = 'El nombre es obligatorio'
    if (!form.treatment.trim()) errs.treatment = 'El tratamiento es obligatorio'
    if (form.animals < 1 || form.animals > 10) errs.animals = 'Entre 1 y 10 animales'
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  const validateStep2 = () => {
    if (!fileDay1) {
      setErrors({ fileDay1: 'El video del Día 1 es obligatorio' })
      return false
    }
    setErrors({})
    return true
  }

  const nextStep = () => {
    if (step === 1 && !validateStep1()) return
    if (step === 2 && !validateStep2()) return
    setStep((s) => Math.min(s + 1, 3))
  }

  const prevStep = () => setStep((s) => Math.max(s - 1, 1))

  const handleSubmit = async () => {
    setSubmitting(true)
    try {
      const notesJson = JSON.stringify({
        treatment: form.treatment,
        species: form.species,
        animals: form.animals,
        duration: form.duration,
        notes: form.notes,
      })

      const sessionRes = await api.post('/api/sessions', { name: form.name, notes: notesJson })
      const sessionId = sessionRes.data.id

      const uploadVideo = async (file, day, progressKey) => {
        const fd = new FormData()
        fd.append('file', file)
        fd.append('session_id', sessionId)
        fd.append('day', day)
        const res = await api.post('/api/videos/upload', fd, {
          headers: { 'Content-Type': 'multipart/form-data' },
          onUploadProgress: (e) => {
            const pct = Math.round((e.loaded / e.total) * 100)
            setUploadProgress((p) => ({ ...p, [progressKey]: pct }))
          },
        })
        return res.data.video_id
      }

      setUploading(true)
      const videoId1 = await uploadVideo(fileDay1, 'DAY1', 'day1')

      if (fileDay2) {
        await uploadVideo(fileDay2, 'DAY2', 'day2')
      }

      await api.post('/api/jobs', { video_id: videoId1 })

      navigate(`/experiments/${sessionId}/progress`)
    } catch (err) {
      console.error('Error creating experiment:', err)
      setErrors({ submit: 'Error al crear el experimento. Inténtalo de nuevo.' })
    } finally {
      setSubmitting(false)
      setUploading(false)
    }
  }

  return (
    <main className="page page--narrow">
      <nav className="breadcrumb">
        <Link to="/dashboard">Mis experimentos</Link>
        <span className="breadcrumb-sep">›</span>
        <span>Nuevo experimento</span>
      </nav>

      <div className="page-header" style={{ marginBottom: 24 }}>
        <div>
          <div className="page-title">Nuevo experimento</div>
          <div className="page-subtitle">Registra los datos del experimento y sube los videos para análisis.</div>
        </div>
      </div>

      <StepIndicator current={step} />

      {/* Step 1: Metadata */}
      {step === 1 && (
        <div className="card">
          <div className="card-header">
            <div className="card-header-icon">
              <svg width="15" height="15" viewBox="0 0 15 15" fill="none"><rect x="2" y="1.5" width="11" height="12" rx="1.5" stroke="#4b6490" strokeWidth="1.1"/><path d="M5 5h5M5 7.5h5M5 10h3" stroke="#4b6490" strokeWidth="1.1" strokeLinecap="round"/></svg>
            </div>
            <div>
              <div className="card-title">Datos del experimento</div>
              <div className="card-subtitle">Información general del estudio FST</div>
            </div>
          </div>
          <div className="card-body">
            <div className="form-grid">
              <div className="form-group">
                <label className="form-label">Nombre del experimento <span className="req">*</span></label>
                <input
                  className={`form-input form-input--plain ${errors.name ? 'error' : ''}`}
                  placeholder="Ej: Ketamina 30 mg/kg — Grupo A"
                  value={form.name}
                  onChange={(e) => set('name', e.target.value)}
                />
                {errors.name && <span className="field-error">{errors.name}</span>}
              </div>

              <div className="form-group">
                <label className="form-label">Tratamiento / fármaco <span className="req">*</span></label>
                <input
                  className={`form-input form-input--plain ${errors.treatment ? 'error' : ''}`}
                  placeholder="Ej: Ketamina 30 mg/kg"
                  value={form.treatment}
                  onChange={(e) => set('treatment', e.target.value)}
                />
                {errors.treatment && <span className="field-error">{errors.treatment}</span>}
              </div>

              <div className="form-group">
                <label className="form-label">Especie / cepa</label>
                <select className="form-select" value={form.species} onChange={(e) => set('species', e.target.value)}>
                  <option>Rata Wistar</option>
                  <option>Rata Sprague-Dawley</option>
                  <option>Ratón CD-1</option>
                  <option>Ratón C57BL/6</option>
                </select>
              </div>

              <div className="form-group">
                <label className="form-label">Número de animales <span className="req">*</span></label>
                <input
                  className={`form-input form-input--plain ${errors.animals ? 'error' : ''}`}
                  type="number"
                  min="1"
                  max="10"
                  value={form.animals}
                  onChange={(e) => set('animals', parseInt(e.target.value) || 0)}
                />
                {errors.animals && <span className="field-error">{errors.animals}</span>}
              </div>

              <div className="form-group">
                <label className="form-label">Duración de sesión (s)</label>
                <select className="form-select" value={form.duration} onChange={(e) => set('duration', parseInt(e.target.value))}>
                  <option value="300">300 s (5 min) — estándar</option>
                  <option value="360">360 s (6 min)</option>
                  <option value="600">600 s (10 min)</option>
                </select>
                <span className="form-hint">Duración estándar del protocolo FST</span>
              </div>

              <div className="form-group span2">
                <label className="form-label">Notas adicionales</label>
                <textarea
                  className="form-textarea"
                  placeholder="Información complementaria del experimento (opcional)"
                  value={form.notes}
                  onChange={(e) => set('notes', e.target.value)}
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Step 2: Video Upload */}
      {step === 2 && (
        <div className="card">
          <div className="card-header">
            <div className="card-header-icon">
              <svg width="15" height="15" viewBox="0 0 15 15" fill="none"><rect x="1" y="3" width="10" height="8" rx="1.5" stroke="#4b6490" strokeWidth="1.1"/><path d="M11 6l3-2v6l-3-2V6z" stroke="#4b6490" strokeWidth="1.1" strokeLinejoin="round"/></svg>
            </div>
            <div>
              <div className="card-title">Videos del experimento</div>
              <div className="card-subtitle">Sube los videos de las sesiones de nado forzado</div>
            </div>
          </div>
          <div className="card-body">
            <div className="upload-grid">
              <UploadZone
                day="DÍA 1"
                label="Sesión basal (habituación)"
                required
                file={fileDay1}
                onFile={setFileDay1}
                uploading={uploading}
                progress={uploadProgress.day1}
              />
              <UploadZone
                day="DÍA 2"
                label="Sesión post-tratamiento"
                required={false}
                file={fileDay2}
                onFile={setFileDay2}
                uploading={uploading}
                progress={uploadProgress.day2}
              />
            </div>

            {errors.fileDay1 && (
              <div className="error-banner" style={{ marginTop: 12 }}>
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><circle cx="8" cy="8" r="7" stroke="#f87171" strokeWidth="1.4"/></svg>
                <div className="error-banner-text">{errors.fileDay1}</div>
              </div>
            )}

            <div className="format-note" style={{ marginTop: 16 }}>
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none" style={{ flexShrink: 0, marginTop: 1 }}>
                <circle cx="7" cy="7" r="6" stroke="#3b6fb6" strokeWidth="1.1"/>
                <path d="M7 4v4" stroke="#3b6fb6" strokeWidth="1.2" strokeLinecap="round"/>
                <circle cx="7" cy="10" r=".6" fill="#3b6fb6"/>
              </svg>
              <span>Formatos aceptados: <strong>.mp4, .avi, .mov</strong>. Tamaño máximo: 2 GB. El video debe mostrar claramente el tanque de nado con todos los animales visibles.</span>
            </div>
          </div>
        </div>
      )}

      {/* Step 3: Confirm */}
      {step === 3 && (
        <>
          <div className="card">
            <div className="card-header">
              <div className="card-header-icon">
                <svg width="15" height="15" viewBox="0 0 15 15" fill="none"><circle cx="7.5" cy="7.5" r="6" stroke="#4b6490" strokeWidth="1.1"/><path d="M5 7.5l2 2 3.5-3.5" stroke="#4b6490" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round"/></svg>
              </div>
              <div>
                <div className="card-title">Confirmar experimento</div>
                <div className="card-subtitle">Revisa los datos antes de iniciar el análisis</div>
              </div>
            </div>
            <div className="card-body">
              <div className="form-grid">
                <div className="form-group">
                  <span className="form-label">Nombre</span>
                  <span style={{ fontSize: 14, fontWeight: 600 }}>{form.name}</span>
                </div>
                <div className="form-group">
                  <span className="form-label">Tratamiento</span>
                  <span style={{ fontSize: 14, fontWeight: 600 }}>{form.treatment}</span>
                </div>
                <div className="form-group">
                  <span className="form-label">Especie / cepa</span>
                  <span style={{ fontSize: 14 }}>{form.species}</span>
                </div>
                <div className="form-group">
                  <span className="form-label">Animales</span>
                  <span style={{ fontSize: 14 }}>{form.animals}</span>
                </div>
                <div className="form-group">
                  <span className="form-label">Duración</span>
                  <span style={{ fontSize: 14 }}>{form.duration} s ({form.duration / 60} min)</span>
                </div>
                <div className="form-group">
                  <span className="form-label">Videos</span>
                  <span style={{ fontSize: 14 }}>
                    Día 1: {fileDay1?.name || '—'}
                    {fileDay2 && <><br />Día 2: {fileDay2.name}</>}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <div className="auto-notice" style={{ marginBottom: 16 }}>
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none" style={{ flexShrink: 0, marginTop: 1 }}>
              <circle cx="8" cy="8" r="6.5" stroke="#0ea5e9" strokeWidth="1.2"/>
              <path d="M8 5v4" stroke="#0ea5e9" strokeWidth="1.3" strokeLinecap="round"/>
              <circle cx="8" cy="11" r=".6" fill="#0ea5e9"/>
            </svg>
            <div className="auto-notice-text">
              <strong>Análisis automático</strong>
              Al confirmar, el análisis se iniciará automáticamente. Puedes cerrar el navegador; el proceso continuará en segundo plano.
            </div>
          </div>

          {errors.submit && (
            <div className="error-banner">
              <div className="error-banner-text">{errors.submit}</div>
            </div>
          )}
        </>
      )}

      {/* Action bar */}
      <div className="action-bar" style={{ marginTop: 16 }}>
        <div>
          {step > 1 && (
            <button type="button" className="btn-ghost" onClick={prevStep} disabled={submitting}>
              ← Anterior
            </button>
          )}
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button type="button" className="btn-ghost" onClick={() => navigate('/dashboard')} disabled={submitting}>
            Cancelar
          </button>
          {step < 3 ? (
            <button type="button" className="btn-primary" onClick={nextStep}>
              Siguiente →
            </button>
          ) : (
            <button type="button" className="btn-primary" onClick={handleSubmit} disabled={submitting}>
              {submitting ? 'Creando…' : 'Crear y analizar'}
            </button>
          )}
        </div>
      </div>
    </main>
  )
}
