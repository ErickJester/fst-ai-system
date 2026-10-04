import React, { useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Topbar, { Brand } from '../components/Topbar'
import { Seg, FieldError, Campo, TipoTag, Pasos, Cargando, NoEncontrado } from '../components/ui'
import { getExperiment } from '../services/experiments'
import { createGroup, uploadBatchVideo } from '../services/groups'
import { useAsync } from '../hooks/useAsync'
import { norm } from '../lib/fst'

const nextLetter = (arr) => (arr.length ? String.fromCharCode(arr[arr.length - 1].charCodeAt(0) + 1) : 'A')
const mutedSub = { fontSize: 12, color: 'color-mix(in srgb,var(--color-text) 60%,transparent)' }
const btnLeft = { justifyContent: 'flex-start' }

// Grupo del experimento → sugerencia del autocompletado.
const sugerencia = (g) => ({
  id: g.id,
  nombre: g.nombre + ' · ' + g.tratamiento,
  tipo: g.tipo,
  cargadas: g.tandas.map((t) => t.letra),
})

// Resalta en la sugerencia la parte que coincide con lo escrito.
function Hi({ label, q }) {
  const i = norm(label).indexOf(norm(q))
  if (!q || i < 0) return label
  return (
    <>
      {label.slice(0, i)}
      <span style={{ color: 'var(--color-accent-700)' }}>{label.slice(i, i + q.length)}</span>
      {label.slice(i + q.length)}
    </>
  )
}

// 2b · Cargar video por tanda: autocompletado de grupo, confirmación de tanda
// y validación del archivo.
export default function CargarVideoPage() {
  const { clave } = useParams()
  const { data: exp, error, reload } = useAsync(() => getExperiment(clave), [clave])
  const [q, setQ] = useState('')
  const [sel, setSel] = useState(null)
  const [sugOpen, setSugOpen] = useState(false)
  const [tanda, setTanda] = useState('')
  const [cil, setCil] = useState('4')
  const [sesion, setSesion] = useState('2')
  const [tipo, setTipo] = useState('EXPERIMENTAL')
  const [trat, setTrat] = useState('')
  const [confirmada, setConfirmada] = useState(false)
  const [archivo, setArchivo] = useState(null)
  const [rechazo, setRechazo] = useState(null)
  const [ok, setOk] = useState('')
  const [subiendo, setSubiendo] = useState(null) // avance en % mientras se sube
  const [errGuardar, setErrGuardar] = useState('')
  const [over, setOver] = useState(false)
  const fileRef = useRef(null)
  const tandaRef = useRef(null)

  if (error) return <NoEncontrado />
  if (!exp) return <Cargando />

  const grupos = exp.grupos.map(sugerencia)
  const qt = q.trim()
  const grupo = sel != null ? grupos.find((g) => g.id === sel) : null
  const matches = qt ? grupos.filter((g) => norm(g.nombre).includes(norm(qt))) : []
  const nuevo = !grupo && !!qt && matches.length === 0
  const grupoNombre = grupo ? grupo.nombre : qt
  const showConfirm = (grupo || nuevo) && /^[A-Z]$/.test(tanda)
  const grupoOk = grupo || (nuevo && trat.trim())
  const canSave = !!(grupoOk && confirmada && archivo) && subiendo == null

  // ── 1 · grupo ─────────────────────────────────────────────────────────────
  function onGrupo(v) {
    const t = v.trim()
    const m = t ? grupos.filter((g) => norm(g.nombre).includes(norm(t))) : []
    setQ(v)
    setSel(null)
    setSugOpen(m.length > 0)
    setTanda(t && m.length === 0 ? 'A' : '')
    setConfirmada(false)
  }

  function pick(g) {
    setSel(g.id)
    setQ(g.nombre)
    setSugOpen(false)
    setTanda(nextLetter(g.cargadas))
    setConfirmada(false)
  }

  // ── 2 · tanda ─────────────────────────────────────────────────────────────
  function onTanda(v) {
    setTanda(v.toUpperCase().replace(/[^A-Z]/g, '').slice(0, 1))
    setConfirmada(false)
  }

  function corregir() {
    setConfirmada(false)
    tandaRef.current.focus()
    tandaRef.current.select()
  }

  // ── 4 · archivo ───────────────────────────────────────────────────────────
  function clearFile() {
    setArchivo(null)
    setRechazo(null)
    if (fileRef.current) fileRef.current.value = ''
  }

  function reject(titulo, detalle) {
    setArchivo(null)
    setRechazo({ titulo, detalle })
    setOk('')
  }

  function takeFile(f) {
    setRechazo(null)
    if (!/\.mp4$/i.test(f.name)) return reject('El archivo no es .mp4', f.name + ' · solo se aceptan archivos .mp4 (RF-08).')
    const v = document.createElement('video')
    const url = URL.createObjectURL(f)
    const dañado = () => reject('El video no se puede reproducir', f.name + ' · el archivo está dañado o incompleto.')
    v.preload = 'metadata'
    v.onloadedmetadata = () => {
      URL.revokeObjectURL(url)
      if (!isFinite(v.duration) || v.duration <= 0) return dañado()
      setArchivo(f)
    }
    v.onerror = () => { URL.revokeObjectURL(url); dañado() }
    v.src = url
  }

  function onDrop(e) {
    e.preventDefault()
    setOver(false)
    const f = e.dataTransfer.files[0]
    if (f) takeFile(f)
  }

  // ── guardar ───────────────────────────────────────────────────────────────
  // Un grupo nuevo se crea antes de subir su primera tanda.
  async function guardar() {
    setOk('')
    setErrGuardar('')
    setSubiendo(0)
    try {
      let gid = grupo?.id
      if (!gid) {
        const g = await createGroup(clave, { nombre: qt, tipo, tratamiento: trat.trim() })
        gid = g.id
        setSel(gid)
        setQ(sugerencia(g).nombre)
      }
      const dia = sesion === '1' ? 'DAY1' : 'DAY2'
      const r = await uploadBatchVideo(clave, gid, tanda, { file: archivo, dia, nCilindros: Number(cil) }, setSubiendo)
      setOk(grupoNombre + ' · Tanda ' + tanda + ' · ' + (dia === 'DAY1' ? 'Día 1' : 'Día 2') + ' · posición ' + r.posicion_cola + ' en la cola.')
      setArchivo(null)
      if (fileRef.current) fileRef.current.value = ''
      reload()
    } catch (err) {
      setErrGuardar(err.response?.data?.error || 'No se pudo guardar el video. Inténtalo de nuevo.')
    } finally {
      setSubiendo(null)
    }
  }

  return (
    <div className="app">
      <Topbar>
        <Brand sub={exp.titulo} />
        <Link to="/experimentos">Salir</Link>
      </Topbar>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) 430px' }}>
        <div style={{ padding: '28px 32px 36px', borderRight: '2px solid var(--color-divider)' }}>
          <Pasos actual={2} />

          <h3 style={{ margin: '0 0 8px' }}>Cargar video por tanda</h3>
          <p className="lead" style={{ margin: '0 0 24px', maxWidth: 620 }}>Un video corresponde a una tanda de un solo grupo. Indica el grupo, confirma qué tanda es y suelta el archivo.</p>

          <div style={{ borderTop: '2px solid var(--color-divider)' }}>
            {/* 1 · grupo */}
            <div className="step">
              <span className="step-n num">1</span>
              <div>
                <Campo id="grupo" label="Grupo o tratamiento">
                  <input className="input" id="grupo" autoComplete="off" placeholder="Escribe el grupo o el tratamiento"
                    style={qt ? { borderColor: 'var(--color-accent)' } : undefined}
                    value={q} onChange={(e) => onGrupo(e.target.value)} />
                </Campo>
                {sugOpen && (
                  <div style={{ border: '2px solid var(--color-text)', borderTop: 0, background: 'var(--color-bg)' }}>
                    {matches.map((g) => (
                      <div key={g.nombre} className="sug" onClick={() => pick(g)}>
                        <span className="nd" style={{ fontSize: 13 }}><Hi label={g.nombre} q={qt} /></span>
                        <TipoTag tipo={g.tipo} style={{ whiteSpace: 'nowrap', flex: 'none' }} />
                        <div style={{ flex: 1 }} />
                        <span className="num" style={{ whiteSpace: 'nowrap', flex: 'none', fontSize: 11.5, color: 'color-mix(in srgb,var(--color-text) 60%,transparent)' }}>
                          tandas cargadas: {g.cargadas.join(', ') || '—'}
                        </span>
                      </div>
                    ))}
                    <div style={{ padding: '8px 12px', fontSize: 11.5, color: 'var(--muted)' }}>Solo se sugieren los {grupos.length} grupos de este experimento, nunca los de otros experimentos.</div>
                  </div>
                )}
              </div>
            </div>

            {/* 2 · tanda */}
            <div className="step">
              <span className="step-n num">2</span>
              <div>
                <div style={{ display: 'flex', alignItems: 'flex-end', gap: 16 }}>
                  <Campo id="tanda" label="Tanda" style={{ width: 120 }}>
                    <input ref={tandaRef} className="input num" id="tanda" maxLength={1}
                      style={{ fontFamily: 'var(--font-heading)', fontWeight: 800, fontSize: 16, textTransform: 'uppercase' }}
                      value={tanda} onChange={(e) => onTanda(e.target.value)} />
                  </Campo>
                  <Campo label="Cilindros en el cuadro">
                    <Seg options={[{ v: '3', label: '3' }, { v: '4', label: '4' }]} value={cil} onChange={setCil} />
                  </Campo>
                  <p style={{ margin: '0 0 10px', ...mutedSub }}>Precargada: siguiente letra después de la última tanda del grupo. Editable.</p>
                </div>
                {showConfirm && (
                  <div style={{ marginTop: 14, border: '2px solid ' + (confirmada ? 'var(--color-text)' : 'var(--color-accent)'), padding: '14px 16px' }}>
                    <div className="nd" style={{ fontSize: 15, marginBottom: 12 }}>¿Esta es la tanda {tanda} de {grupoNombre}?</div>
                    <div style={{ display: 'flex', gap: 8 }}>
                      <button type="button" className="btn btn-primary" style={btnLeft} disabled={confirmada} onClick={() => setConfirmada(true)}>Sí, es la {tanda}</button>
                      <button type="button" className="btn btn-secondary" style={btnLeft} onClick={corregir}>Corregir</button>
                    </div>
                    {!confirmada && <div style={{ marginTop: 11, fontSize: 11.5, color: 'var(--color-accent-700)' }}>No se puede guardar sin confirmar la tanda.</div>}
                  </div>
                )}
              </div>
            </div>

            {/* 3 · sesión */}
            <div className="step">
              <span className="step-n num">3</span>
              <Campo label="Sesión">
                <Seg options={[{ v: '1', label: 'Día 1 (20 min)' }, { v: '2', label: 'Día 2 (5 min)' }]} value={sesion} onChange={setSesion} />
              </Campo>
            </div>

            {/* 4 · archivo */}
            <div className="step" style={{ borderBottom: 0, paddingBottom: 0 }}>
              <span className="step-n num">4</span>
              <div>
                <div className="k" style={{ marginBottom: 8 }}>Archivo de video</div>
                <div className={'drop' + (over ? ' over' : '')}
                  onDragEnter={(e) => { e.preventDefault(); setOver(true) }}
                  onDragOver={(e) => { e.preventDefault(); setOver(true) }}
                  onDragLeave={(e) => { e.preventDefault(); setOver(false) }}
                  onDrop={onDrop}>
                  <div style={{ flex: 1 }}>
                    <div className="nd" style={{ fontSize: 15 }}>{archivo ? archivo.name : 'Suelta aquí el archivo .mp4'}</div>
                    <div style={{ marginTop: 4, ...mutedSub }}>Un solo archivo por tanda y sesión. Se valida el formato y que el video se pueda reproducir.</div>
                  </div>
                  <button type="button" className="btn btn-secondary" style={btnLeft} onClick={() => fileRef.current.click()}>Elegir archivo</button>
                  <input ref={fileRef} type="file" accept="video/mp4,.mp4" className="hidden"
                    onChange={(e) => { if (e.target.files[0]) takeFile(e.target.files[0]) }} />
                </div>
                <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 14, marginTop: 18 }}>
                  <button type="button" className="btn btn-primary" style={btnLeft} disabled={!canSave} onClick={guardar}>
                    {subiendo == null ? 'Guardar y encolar análisis' : 'Subiendo… ' + subiendo + ' %'}
                  </button>
                  <span style={{ fontSize: 11.5, color: 'var(--muted)' }}>Se habilita al confirmar la tanda y adjuntar el archivo.</span>
                </div>
                <FieldError msg={errGuardar} style={{ marginTop: 10 }} />
              </div>
            </div>
          </div>
        </div>

        {/* estados del flujo */}
        <div style={{ background: 'var(--color-surface)', padding: '28px 24px', display: 'flex', flexDirection: 'column', gap: 20 }}>
          {nuevo && (
            <div style={{ background: 'var(--color-bg)', border: '2px solid var(--color-text)' }}>
              <div className="k" style={{ padding: '10px 14px', borderBottom: '1px solid var(--color-divider)' }}>Grupo nuevo · el nombre no coincide</div>
              <div style={{ padding: 14, display: 'flex', flexDirection: 'column', gap: 12 }}>
                <Campo label="Grupo o tratamiento"><div className="input">{qt}</div></Campo>
                <div style={{ fontSize: 12, color: 'var(--muted-2)' }}>Sin coincidencias en este experimento. Completa los datos del grupo nuevo:</div>
                <Campo label="Tipo">
                  <Seg
                    options={[{ v: 'CONTROL', label: 'control' }, { v: 'REFERENCIA', label: 'referencia' }, { v: 'EXPERIMENTAL', label: 'tratamiento exp.' }]}
                    value={tipo} onChange={setTipo} />
                </Campo>
                <Campo id="trat" label="Tratamiento"><input className="input" id="trat" placeholder="Compuesto CSR-14, 30 mg/kg" value={trat} onChange={(e) => setTrat(e.target.value)} /></Campo>
                <div className="note-bar">La primera tanda de un grupo nuevo siempre es A.</div>
              </div>
            </div>
          )}

          {rechazo && (
            <div style={{ background: 'var(--color-bg)', border: '2px solid var(--color-accent)' }}>
              <div className="nd" style={{ padding: '10px 14px', background: 'var(--color-accent)', color: 'var(--color-bg)', fontSize: 14 }}>Video rechazado</div>
              <div style={{ padding: '12px 14px', borderBottom: '1px solid var(--color-divider)' }}>
                <div className="nd" style={{ fontSize: 13 }}>{rechazo.titulo}</div>
                <div className="num" style={{ fontSize: 11.5, marginTop: 3, color: 'var(--muted-2)' }}>{rechazo.detalle}</div>
              </div>
              <div style={{ padding: '12px 14px', display: 'flex', alignItems: 'center', gap: 12 }}>
                <button type="button" className="btn btn-secondary" style={btnLeft} onClick={() => { clearFile(); fileRef.current.click() }}>Reintentar</button>
                {tanda && <span style={{ fontSize: 11.5, color: 'var(--color-accent-700)' }}>La tanda {tanda} no avanza.</span>}
              </div>
            </div>
          )}

          {ok && (
            <div style={{ background: 'var(--color-bg)', border: '2px solid var(--color-text)', borderLeft: '6px solid var(--color-text)' }}>
              <div style={{ padding: 14 }}>
                <div className="nd" style={{ fontSize: 14 }}>Video almacenado · análisis en cola</div>
                <div className="num" style={{ fontSize: 12, marginTop: 4, color: 'var(--muted-2)' }}>{ok}</div>
                <Link to="/analisis" style={{ display: 'inline-block', marginTop: 10, fontSize: 12.5, whiteSpace: 'nowrap' }}>Ver progreso del análisis →</Link>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
