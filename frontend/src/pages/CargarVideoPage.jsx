import React, { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Topbar, { Brand } from '../components/Topbar'
import { Seg, FieldError, Campo, TipoTag, Pasos, Cargando, NoEncontrado } from '../components/ui'
import { getExperiment } from '../services/experiments'
import { sugerirGrupos, subirVideo } from '../services/groups'
import { mensajeError, esCancelacion } from '../services/api'
import { MAX_UPLOAD_MB } from '../services/config'
import { useAsync } from '../hooks/useAsync'
import { norm } from '../lib/fst'
import { MUT60, btnLeft, mutedSub } from '../lib/estilos'

// Texto de una sugerencia del autocompletado: grupo y tratamiento.
const etiqueta = (g) => g.nombre + ' · ' + g.tratamiento

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
  const navigate = useNavigate()
  const { data: exp, error } = useAsync(() => getExperiment(clave), [clave])
  const [q, setQ] = useState('')
  const [elegido, setElegido] = useState(null) // sugerencia elegida (grupo existente)
  const [sug, setSug] = useState({ q: '', lista: [] }) // sugerencias del servidor para q
  const [sugOpen, setSugOpen] = useState(false)
  const [activa, setActiva] = useState(-1) // sugerencia resaltada con el teclado
  const [tanda, setTanda] = useState('')
  const [cil, setCil] = useState('4')
  const [sesion, setSesion] = useState('2')
  const [tipo, setTipo] = useState('EXPERIMENTAL')
  const [trat, setTrat] = useState('')
  const [confirmada, setConfirmada] = useState(false)
  const [archivo, setArchivo] = useState(null)
  const [rechazo, setRechazo] = useState(null)
  const [subiendo, setSubiendo] = useState(null) // avance en % mientras se sube
  const [errGuardar, setErrGuardar] = useState('')
  const [cancelada, setCancelada] = useState(false)
  const subidaRef = useRef(null) // AbortController de la subida en curso
  const [over, setOver] = useState(false)
  const fileRef = useRef(null)
  const tandaRef = useRef(null)
  const qt = q.trim()

  // Sugerencias del servidor (GET .../groups?q=) mientras se escribe un grupo que no se ha
  // elegido. Si no hay ninguna, es un grupo nuevo y su primera tanda es la A.
  useEffect(() => {
    if (!qt || elegido) return undefined
    let vigente = true
    sugerirGrupos(clave, qt).then(
      (lista) => {
        if (!vigente) return
        setSug({ q: qt, lista })
        setSugOpen(lista.length > 0)
        setTanda(lista.length ? '' : 'A')
      },
      () => vigente && setSug({ q: qt, lista: [] }),
    )
    return () => { vigente = false }
  }, [clave, qt, elegido])

  if (error) return <NoEncontrado />
  if (!exp) return <Cargando />

  const grupo = elegido
  const respondido = !!qt && sug.q === qt
  const matches = respondido && !grupo ? sug.lista : []
  const nuevo = !grupo && respondido && sug.lista.length === 0
  const grupoNombre = grupo ? etiqueta(grupo) : qt
  const showConfirm = (grupo || nuevo) && /^[A-Z]$/.test(tanda)
  const grupoOk = grupo || (nuevo && trat.trim())
  // Una tanda lleva un solo video por sesión: si ya tiene el de ese día, no se puede subir otro.
  const tandaExistente = grupo ? exp.grupos.find((x) => x.id === grupo.id)?.tandas.find((t) => t.letra === tanda) : null
  const yaTiene = !!tandaExistente && (sesion === '1' ? !!tandaExistente.dia1 : tandaExistente.estado !== 'SIN_VIDEO')
  const canSave = !!(grupoOk && confirmada && archivo) && subiendo == null && !yaTiene

  // ── 1 · grupo ─────────────────────────────────────────────────────────────
  function onGrupo(v) {
    setQ(v)
    setElegido(null)
    setSugOpen(false)
    setActiva(-1)
    setTanda('')
    setConfirmada(false)
  }

  function pick(g) {
    setElegido(g)
    setQ(etiqueta(g))
    setSugOpen(false)
    setActiva(-1)
    setTanda(g.siguiente)
    setConfirmada(false)
  }

  // Flechas para recorrer las sugerencias, Enter para elegir, Escape para cerrar.
  function onTeclaGrupo(e) {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      if (!matches.length) return
      e.preventDefault()
      setSugOpen(true)
      const paso = e.key === 'ArrowDown' ? 1 : -1
      // Sin resaltada, ↓ va a la primera y ↑ a la última; después da la vuelta.
      setActiva((i) => (i < 0 ? (paso > 0 ? 0 : matches.length - 1) : (i + paso + matches.length) % matches.length))
    } else if (e.key === 'Enter' && sugOpen && activa >= 0) {
      e.preventDefault()
      pick(matches[activa])
    } else if (e.key === 'Escape' && sugOpen) {
      setSugOpen(false)
      setActiva(-1)
    }
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
  }

  // En el navegador solo se revisa la extensión (.mp4 o .mov). La orientación y que el
  // video se pueda reproducir las valida el servidor al recibirlo (D-64).
  function takeFile(f) {
    setRechazo(null)
    if (!/\.(mp4|mov)$/i.test(f.name)) return reject('El archivo no es .mp4 ni .mov', f.name + ' · solo se aceptan archivos .mp4 o .mov.')
    // El tamaño se revisa antes de subir, para no esperar a que el servidor lo rechace (413).
    if (f.size > MAX_UPLOAD_MB * 1024 * 1024) {
      return reject('El archivo pasa del límite de ' + MAX_UPLOAD_MB + ' MB', f.name + ' · ' + Math.ceil(f.size / 1024 / 1024) + ' MB.')
    }
    setArchivo(f)
  }

  function onDrop(e) {
    e.preventDefault()
    setOver(false)
    const f = e.dataTransfer.files[0]
    if (f) takeFile(f)
  }

  // ── guardar ───────────────────────────────────────────────────────────────
  // Deja el formulario listo para otra tanda (otro grupo o la siguiente letra).
  function otraTanda() {
    setQ('')
    setElegido(null)
    setSugOpen(false)
    setTanda('')
    setConfirmada(false)
    setTrat('')
    clearFile()
    setErrGuardar('')
    setCancelada(false)
    window.scrollTo(0, 0)
  }

  async function guardar() {
    setErrGuardar('')
    setCancelada(false)
    setSubiendo(0)
    const control = new AbortController()
    subidaRef.current = control
    try {
      // Una sola llamada: el servidor crea el grupo y la tanda si no existen (§6.1).
      const dia = sesion === '1' ? 'DAY1' : 'DAY2'
      await subirVideo(clave, {
        grupo: grupo ? grupo.nombre : qt, nuevo: !grupo, tipo, tratamiento: trat.trim(),
        letra: tanda, sesion: dia, nCilindros: tandaExistente ? undefined : Number(cil), file: archivo, signal: control.signal,
      }, setSubiendo)
      // Día 2: a Progreso. Día 1: se guarda sin analizar y se vuelve al experimento (§9.3).
      const donde = grupoNombre + ' · Tanda ' + tanda
      if (dia === 'DAY2') navigate('/analisis?experimento=' + clave, { state: { aviso: 'Video almacenado. El análisis está en cola: ' + donde + ' · Día 2.' } })
      else navigate(`/experimentos/${clave}`, { state: { aviso: 'Video del Día 1 almacenado (' + donde + '). No se analiza.' } })
    } catch (err) {
      // Cancelar no es un error: el archivo queda elegido para volver a intentarlo.
      if (esCancelacion(err)) setCancelada(true)
      // 422: el servidor rechazó el video (vertical o que no se puede reproducir).
      else if (err.response?.status === 422) reject('El servidor rechazó el video', archivo.name + ' · ' + mensajeError(err, 'El video no se puede procesar.'))
      else if (err.response?.status === 413) reject('El archivo pasa del límite de ' + MAX_UPLOAD_MB + ' MB', archivo.name)
      else setErrGuardar(mensajeError(err, 'No se pudo guardar el video. Inténtalo de nuevo.'))
      setSubiendo(null)
      subidaRef.current = null
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
                    role="combobox" aria-autocomplete="list" aria-controls="sugGrupos" aria-expanded={sugOpen}
                    aria-activedescendant={sugOpen && activa >= 0 ? 'sug-' + activa : undefined}
                    style={qt ? { borderColor: 'var(--color-accent)' } : undefined}
                    value={q} onChange={(e) => onGrupo(e.target.value)} onKeyDown={onTeclaGrupo} />
                </Campo>
                {sugOpen && (
                  <div style={{ border: '2px solid var(--color-text)', borderTop: 0, background: 'var(--color-bg)' }}>
                    <div id="sugGrupos" role="listbox" aria-label="Grupos de este experimento">
                    {matches.map((g, i) => (
                      <div key={g.nombre} id={'sug-' + i} role="option" aria-selected={i === activa}
                        className={'sug' + (i === activa ? ' active' : '')}
                        onMouseEnter={() => setActiva(i)} onMouseDown={(e) => e.preventDefault()} onClick={() => pick(g)}>
                        <span className="nd" style={{ fontSize: 13 }}><Hi label={etiqueta(g)} q={qt} /></span>
                        <TipoTag tipo={g.tipo} style={{ whiteSpace: 'nowrap', flex: 'none' }} />
                        <div style={{ flex: 1 }} />
                        <span className="num" style={{ whiteSpace: 'nowrap', flex: 'none', fontSize: 11.5, color: MUT60 }}>
                          tandas cargadas: {exp.grupos.find((x) => x.id === g.id)?.tandas.map((t) => t.letra).join(', ') || '—'}
                        </span>
                      </div>
                    ))}
                    </div>
                    <div style={{ padding: '8px 12px', fontSize: 11.5, color: 'var(--muted)' }}>Solo se sugieren los {exp.grupos.length} grupos de este experimento, nunca los de otros experimentos.</div>
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
                  <Campo label="Especímenes en esta tanda">
                    <Seg options={[{ v: '2', label: '2' }, { v: '3', label: '3' }, { v: '4', label: '4' }]} value={cil} onChange={setCil} />
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
              <div className="hint" style={{ marginTop: 8 }}>Solo el Día 2 se analiza. El Día 1 se guarda para dejar constancia de la sesión de habituación.</div>
              {yaTiene && (
                <div className="note-bar" role="status" style={{ marginTop: 10 }}>
                  La Tanda {tanda} de {grupoNombre} ya tiene su video de {sesion === '1' ? 'Día 1' : 'Día 2'}. Cada tanda lleva un solo video por sesión: elige la otra sesión o una tanda nueva.
                </div>
              )}
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
                    <div className="nd" style={{ fontSize: 15 }}>{archivo ? archivo.name : 'Suelta aquí el archivo .mp4 o .mov'}</div>
                    <div style={{ marginTop: 4, ...mutedSub }}>Un solo archivo por tanda y sesión, de hasta {MAX_UPLOAD_MB} MB. Aquí se revisan el formato y el tamaño; al recibirlo, el servidor comprueba que se pueda reproducir y que sea horizontal.</div>
                  </div>
                  <button type="button" className="btn btn-secondary" style={btnLeft} onClick={() => fileRef.current.click()}>Elegir archivo</button>
                  <input ref={fileRef} type="file" accept="video/mp4,video/quicktime,.mp4,.mov" className="hidden" aria-label="Archivo de video"
                    onChange={(e) => { if (e.target.files[0]) takeFile(e.target.files[0]) }} />
                </div>
                {subiendo != null && (
                  <div style={{ marginTop: 18 }}>
                    <div className="num" style={{ fontSize: 12, marginBottom: 5 }}>Subiendo {archivo?.name} · {subiendo} %</div>
                    <div className="meter" role="progressbar" aria-label="Avance de la subida" aria-valuenow={subiendo} aria-valuemin={0} aria-valuemax={100}>
                      <div style={{ width: subiendo + '%' }} />
                    </div>
                    <button type="button" className="btn btn-ghost" style={{ ...btnLeft, marginTop: 8 }} onClick={() => subidaRef.current?.abort()}>Cancelar subida</button>
                  </div>
                )}
                {cancelada && <div className="hint" style={{ marginTop: 10 }}>Subida cancelada: el video no se guardó. Puedes volver a intentarlo con el mismo archivo o elegir otro.</div>}
                <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 14, marginTop: 18 }}>
                  <button type="button" className="btn btn-primary" style={btnLeft} disabled={!canSave} onClick={guardar}>
                    {subiendo != null ? 'Subiendo…' : sesion === '1' ? 'Guardar video' : 'Guardar y encolar análisis'}
                  </button>
                  <span style={{ fontSize: 11.5, color: 'var(--muted)' }}>Se habilita al confirmar la tanda y adjuntar el archivo.</span>
                </div>
                {/* §9.3: otra tanda sin salir de aquí, o terminar y volver al experimento. */}
                <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 10, marginTop: 14 }}>
                  <button type="button" className="btn btn-secondary" style={btnLeft} disabled={subiendo != null} onClick={otraTanda}>+ Agregar otra tanda</button>
                  <button type="button" className="btn btn-ghost" style={btnLeft} disabled={subiendo != null} onClick={() => navigate(`/experimentos/${clave}`)}>Terminar</button>
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

        </div>
      </div>
    </div>
  )
}
