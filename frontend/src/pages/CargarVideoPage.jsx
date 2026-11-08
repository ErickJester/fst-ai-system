import React, { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Topbar, { Brand } from '../components/Topbar'
import { Seg, FieldError, Campo, TipoTag, Pasos, Cargando, NoEncontrado } from '../components/ui'
import { getExperiment } from '../services/experiments'
import { sugerirGrupos, subirVideo } from '../services/groups'
import { mensajeError, esCancelacion } from '../services/api'
import { MAX_UPLOAD_MB } from '../services/config'
import { useAsync } from '../hooks/useAsync'
import { useAuth } from '../contexts/AuthContext'
import { norm, puedeSubirVideo, SOLO_CREADOR_SUBE } from '../lib/fst'
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

// Letra libre a partir de `desde`: la primera que ningún otro formulario de la página usa ya en ese grupo y esa
// sesión. Así dos formularios del mismo grupo no proponen la misma tanda.
function letraLibre(desde, grupoKey, sesion, otras) {
  if (!/^[A-Z]$/.test(desde)) return desde
  const usadas = new Set(otras.filter((o) => o.grupoKey === grupoKey && o.sesion === sesion && o.tanda).map((o) => o.tanda))
  let c = desde.charCodeAt(0)
  while (usadas.has(String.fromCharCode(c)) && c < 90) c += 1
  return String.fromCharCode(c)
}

// La letra que sigue a la de la tanda anterior ('' si no hay o ya es la Z).
const letraSiguiente = (t) => (/^[A-Y]$/.test(t) ? String.fromCharCode(t.charCodeAt(0) + 1) : '')

// Avisos con los que se llega a Progreso (Día 2: en cola) o al experimento (Día 1: guardado sin analizar).
const avisoCola = (lista) => (lista.length > 1 ? 'Videos almacenados. Los análisis están en cola: ' : 'Video almacenado. El análisis está en cola: ')
  + lista.map((g) => g.donde + ' · Día 2').join('; ') + '.'
const avisoDia1 = (lista) => (lista.length > 1 ? 'Videos del Día 1 almacenados (' : 'Video del Día 1 almacenado (')
  + lista.map((g) => g.donde).join('; ') + '). No se ' + (lista.length > 1 ? 'analizan.' : 'analiza.')

// Un formulario por tanda: autocompletado de grupo, confirmación de tanda y validación del archivo. La página puede
// tener varios a la vez (§9.3): cada uno conserva su propio avance y su propia subida.
//
// plantilla: con «Agregar otra tanda» el formulario nace con lo que ya se sabe: el mismo grupo, la tanda que sigue y
// la tanda ya confirmada (agregar otra tanda es seguir en el mismo grupo). Solo queda el archivo y, si hace falta,
// la sesión y los especímenes. Si el usuario cambia el grupo, deja de heredar y se comporta como siempre.
function FormularioTanda({ clave, exp, id, posicion, unico, resumenes, plantilla, autoFoco, guardando, registrar, onResumen, onGuardada, onQuitar }) {
  const navigate = useNavigate()
  const [q, setQ] = useState(plantilla?.q ?? '')
  const [elegido, setElegido] = useState(plantilla?.elegido ?? null) // sugerencia elegida (grupo existente)
  const [sug, setSug] = useState({ q: '', lista: [] }) // sugerencias del servidor para q
  const [sugOpen, setSugOpen] = useState(false)
  const [activa, setActiva] = useState(-1) // sugerencia resaltada con el teclado
  const [tanda, setTanda] = useState(() => {
    if (!plantilla?.tanda) return ''
    const key = norm(plantilla.elegido ? plantilla.elegido.nombre : plantilla.q.trim())
    return letraLibre(plantilla.tanda, key, '2', Object.values(resumenes).filter((r) => r.id !== id))
  })
  const [cil, setCil] = useState('4')
  const [sesion, setSesion] = useState('2')
  const [tipo, setTipo] = useState('EXPERIMENTAL')
  const [trat, setTrat] = useState('')
  const [confirmada, setConfirmada] = useState(!!plantilla?.tanda)
  const [archivo, setArchivo] = useState(null)
  const [rechazo, setRechazo] = useState(null)
  const [subiendo, setSubiendo] = useState(null) // avance en % mientras se sube
  const [errGuardar, setErrGuardar] = useState('')
  const [cancelada, setCancelada] = useState(false)
  const [guardada, setGuardada] = useState(null) // { donde, dia, nombre } cuando el video ya se almacenó (con varios formularios)
  const subidaRef = useRef(null) // AbortController de la subida en curso
  const [over, setOver] = useState(false)
  const fileRef = useRef(null)
  const tandaRef = useRef(null)
  const filaRef = useRef(null)
  const heredada = useRef(!!plantilla) // la tanda y su confirmación vienen de la plantilla, no de la respuesta del servidor
  const qt = q.trim()

  // Lo que dicen los demás formularios de la página (grupo, tanda, sesión y si ya se guardaron). Los ref guardan
  // el valor más reciente para las respuestas del servidor y para el final de la subida, que llegan tarde.
  const otras = Object.values(resumenes).filter((r) => r.id !== id)
  const otrasRef = useRef(otras)
  const unicoRef = useRef(unico)
  const sesionRef = useRef(sesion)
  useEffect(() => {
    otrasRef.current = otras
    unicoRef.current = unico
    sesionRef.current = sesion
  })

  // Un formulario agregado después se muestra y recibe el foco, sin mover los anteriores.
  useEffect(() => {
    if (autoFoco) filaRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
  }, [autoFoco])

  // Sugerencias del servidor (GET .../groups?q=) mientras se escribe un grupo que no se ha
  // elegido. Si no hay ninguna, es un grupo nuevo y su primera tanda es la A.
  useEffect(() => {
    if (!qt || elegido) return undefined
    let vigente = true
    sugerirGrupos(clave, qt).then(
      (lista) => {
        if (!vigente) return
        setSug({ q: qt, lista })
        if (heredada.current) {
          // El grupo heredado ya existe en el servidor (la tanda anterior se guardó): se elige solo, con su tanda.
          const igual = lista.find((g) => norm(g.nombre) === norm(qt))
          if (igual) {
            setElegido(igual)
            setQ(etiqueta(igual))
            return
          }
          // Sin coincidencia exacta pero con sugerencias: se comporta como siempre.
          if (lista.length) {
            heredada.current = false
            setConfirmada(false)
          }
        }
        if (!heredada.current) {
          setSugOpen(lista.length > 0)
          setTanda(lista.length ? '' : letraLibre('A', norm(qt), sesionRef.current, otrasRef.current))
        }
      },
      () => vigente && setSug({ q: qt, lista: [] }),
    )
    return () => { vigente = false }
  }, [clave, qt, elegido])

  const grupo = elegido
  const respondido = !!qt && sug.q === qt
  const matches = respondido && !grupo ? sug.lista : []
  const nuevo = !grupo && respondido && sug.lista.length === 0
  const grupoNombre = grupo ? etiqueta(grupo) : qt
  // Mismo grupo en otro formulario de esta página: la tanda y la sesión no pueden repetirse. Un grupo nuevo lo crea
  // el primero que se sube (el servidor lo crea al terminar la subida) y la página los sube en orden, uno por uno.
  const grupoKey = grupo || nuevo ? norm(grupo ? grupo.nombre : qt) : ''
  const hermanas = grupoKey ? otras.filter((o) => o.grupoKey === grupoKey) : []
  const hermanaNueva = nuevo && hermanas.some((o) => o.nuevo && o.id < id) // solo el posterior toma los datos del anterior
  // El choque lo lleva el formulario posterior (o cualquiera si el otro ya se guardó): el primero no se bloquea.
  const choque = !!grupoKey && /^[A-Z]$/.test(tanda) && hermanas.some((o) => o.tanda === tanda && o.sesion === sesion && (o.id < id || o.guardada))
  const showConfirm = (grupo || nuevo) && /^[A-Z]$/.test(tanda)
  // Si otro formulario ya trae los datos del mismo grupo nuevo, aquí no se piden otra vez.
  const grupoOk = grupo || (nuevo && (trat.trim() || hermanaNueva))
  // Una tanda lleva un solo video por sesión: si ya tiene el de ese día, no se puede subir otro.
  const tandaExistente = grupo ? exp.grupos.find((x) => x.id === grupo.id)?.tandas.find((t) => t.letra === tanda) : null
  const yaTiene = !!tandaExistente && (sesion === '1' ? !!tandaExistente.dia1 : tandaExistente.estado !== 'SIN_VIDEO')
  // lista: la tanda ya se puede subir (la página junta las de todos los formularios en un solo botón);
  // canSave: además no se está subiendo ahora (el botón de un formulario único).
  const lista = !!(grupoOk && confirmada && archivo) && !yaTiene && !choque
  const canSave = lista && subiendo == null

  // La página necesita saber esto de cada formulario para no repetir tandas ni crear dos veces un grupo nuevo.
  const firma = JSON.stringify({ id, grupoKey, nuevo: !!nuevo, tanda, sesion, guardada: !!guardada, subiendo: subiendo != null, q, elegido, lista })
  useEffect(() => { onResumen(id, JSON.parse(firma)) }, [id, firma, onResumen])

  // ── 1 · grupo ─────────────────────────────────────────────────────────────
  function onGrupo(v) {
    heredada.current = false
    setQ(v)
    setElegido(null)
    setSugOpen(false)
    setActiva(-1)
    setTanda('')
    setConfirmada(false)
  }

  function pick(g) {
    heredada.current = false
    setElegido(g)
    setQ(etiqueta(g))
    setSugOpen(false)
    setActiva(-1)
    setTanda(letraLibre(g.siguiente, norm(g.nombre), sesion, otras))
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
      const donde = grupoNombre + ' · Tanda ' + tanda
      // Un solo formulario: Día 2 a Progreso, Día 1 se guarda sin analizar y se vuelve al experimento (§9.3).
      if (unicoRef.current) {
        if (dia === 'DAY2') navigate('/analisis?experimento=' + clave, { state: { aviso: avisoCola([{ donde }]) } })
        else navigate(`/experimentos/${clave}`, { state: { aviso: avisoDia1([{ donde }]) } })
        return true
      }
      // Varios formularios: no se sale; el botón de la página sigue con el siguiente y al final navega.
      setSubiendo(null)
      subidaRef.current = null
      setGuardada({ donde, dia, nombre: archivo.name })
      onGuardada(id, { donde, dia })
      return true
    } catch (err) {
      // Cancelar no es un error: el archivo queda elegido para volver a intentarlo.
      if (esCancelacion(err)) setCancelada(true)
      // 422: el servidor rechazó el video (vertical o que no se puede reproducir).
      else if (err.response?.status === 422) reject('El servidor rechazó el video', archivo.name + ' · ' + mensajeError(err, 'El video no se puede procesar.'))
      else if (err.response?.status === 413) reject('El archivo pasa del límite de ' + MAX_UPLOAD_MB + ' MB', archivo.name)
      else setErrGuardar(mensajeError(err, 'No se pudo guardar el video. Inténtalo de nuevo.'))
      setSubiendo(null)
      subidaRef.current = null
      return false
    }
  }

  // La página llama a guardar() de cada formulario, en orden, desde su único botón (varios formularios).
  useEffect(() => { registrar(id, guardar) })

  if (guardada) {
    return (
      <div ref={filaRef} role="status" style={{ padding: '18px 32px', borderTop: '2px solid var(--color-divider)' }}>
        <div className="k" style={{ marginBottom: 6 }}>Formulario {posicion}</div>
        <div className="nd" style={{ fontSize: 15 }}>✓ {guardada.donde} · {guardada.dia === 'DAY2' ? 'Día 2' : 'Día 1'}</div>
        <div style={{ marginTop: 4, ...mutedSub }}>{guardada.nombre} · almacenado. {guardada.dia === 'DAY2' ? 'El análisis está en cola.' : 'No se analiza.'}</div>
      </div>
    )
  }

  const idGrupo = 'grupo-' + id
  const idTanda = 'tanda-' + id
  const idSug = 'sugGrupos-' + id

  return (
    <div ref={filaRef} style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) 430px', borderTop: '2px solid var(--color-divider)' }}>
      <div style={{ padding: '24px 32px 28px', borderRight: '2px solid var(--color-divider)' }}>
        {!unico && (
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
            <span className="k">Formulario {posicion}</span>
            <button type="button" className="btn btn-ghost" style={btnLeft} disabled={guardando || subiendo != null} onClick={() => onQuitar(id)}>Quitar este formulario</button>
          </div>
        )}

        <div>
          {/* 1 · grupo */}
          <div className="step">
            <span className="step-n num">1</span>
            <div>
              <Campo id={idGrupo} label="Nombre del grupo">
                <input className="input" id={idGrupo} autoComplete="off" autoFocus={autoFoco && !plantilla} placeholder="Escribe el nombre del grupo"
                  role="combobox" aria-autocomplete="list" aria-controls={idSug} aria-expanded={sugOpen}
                  aria-activedescendant={sugOpen && activa >= 0 ? 'sug-' + id + '-' + activa : undefined}
                  style={qt ? { borderColor: 'var(--color-accent)' } : undefined}
                  value={q} onChange={(e) => onGrupo(e.target.value)} onKeyDown={onTeclaGrupo} />
              </Campo>
              {sugOpen && (
                <div style={{ border: '2px solid var(--color-text)', borderTop: 0, background: 'var(--color-bg)' }}>
                  <div id={idSug} role="listbox" aria-label="Grupos de este experimento">
                  {matches.map((g, i) => (
                    <div key={g.nombre} id={'sug-' + id + '-' + i} role="option" aria-selected={i === activa}
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
                <Campo id={idTanda} label="Tanda" style={{ width: 120 }}>
                  <input ref={tandaRef} className="input num" id={idTanda} maxLength={1}
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
            <div>
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
              {/* Con un solo formulario, su propio botón. Con varios, un solo botón al final de la página las sube en orden. */}
              {unico && (
                <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 14, marginTop: 18 }}>
                  <button type="button" className="btn btn-primary" style={btnLeft} disabled={!canSave} onClick={guardar}>
                    {subiendo != null ? 'Subiendo…' : sesion === '1' ? 'Guardar video' : 'Guardar y encolar análisis'}
                  </button>
                  <span style={{ fontSize: 11.5, color: 'var(--muted)' }}>Se habilita al confirmar la tanda y adjuntar el archivo.</span>
                </div>
              )}
              {!unico && guardando && subiendo == null && (
                <div className="hint" style={{ marginTop: 14 }}>En espera: esta tanda se sube después de las de arriba.</div>
              )}
              {choque && (
                <div className="note-bar" role="status" style={{ marginTop: 10 }}>
                  Otro formulario de esta página ya usa la Tanda {tanda} de {grupoNombre} en el {sesion === '1' ? 'Día 1' : 'Día 2'}. Cambia la tanda o la sesión.
                </div>
              )}
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
              <Campo label="Grupo"><div className="input">{qt}</div></Campo>
              {hermanaNueva ? (
                <div className="note-bar">El tipo y el tratamiento de este grupo nuevo se toman del otro formulario que lo trae.</div>
              ) : (
                <>
                  <div style={{ fontSize: 12, color: 'var(--muted-2)' }}>Sin coincidencias en este experimento. Completa los datos del grupo nuevo:</div>
                  <Campo label="Tipo">
                    <Seg
                      options={[{ v: 'CONTROL', label: 'control' }, { v: 'REFERENCIA', label: 'referencia' }, { v: 'EXPERIMENTAL', label: 'tratamiento exp.' }]}
                      value={tipo} onChange={setTipo} />
                  </Campo>
                  <Campo id={'trat-' + id} label="Tratamiento (sustancia y dosis)"><input className="input" id={'trat-' + id} placeholder="Compuesto CSR-14, 30 mg/kg" value={trat} onChange={(e) => setTrat(e.target.value)} /></Campo>
                </>
              )}
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
  )
}

// 2b · Cargar video por tanda. «Agregar otra tanda» suma un formulario debajo de los que ya están, sin tocarlos:
// se pueden llenar y subir varias tandas desde la misma página.
export default function CargarVideoPage() {
  const { clave } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const { data: exp, error } = useAsync(() => getExperiment(clave), [clave])
  const [formularios, setFormularios] = useState([{ id: 1, plantilla: null }]) // en el orden en que se agregaron
  const [resumenes, setResumenes] = useState({}) // id → lo que cada formulario cuenta de sí mismo
  const [foco, setFoco] = useState(null) // formulario recién agregado
  const siguienteId = useRef(2)
  const [guardando, setGuardando] = useState(false) // el botón único está subiendo las tandas en orden
  const guardadores = useRef({}) // id → guardar() de cada formulario, siempre el más reciente
  const guardadasRef = useRef({}) // id → { donde, dia } de las tandas ya almacenadas, al día dentro del ciclo de subida

  const onResumen = useCallback((id, r) => setResumenes((prev) => (
    prev[id] && JSON.stringify(prev[id]) === JSON.stringify(r) ? prev : { ...prev, [id]: r }
  )), [])
  const onGuardada = useCallback((id, info) => { guardadasRef.current = { ...guardadasRef.current, [id]: info } }, [])
  const registrar = useCallback((id, fn) => { guardadores.current[id] = fn }, [])
  const quitar = useCallback((id) => {
    delete guardadores.current[id]
    setFormularios((f) => f.filter((x) => x.id !== id))
    setResumenes((prev) => {
      const resto = { ...prev }
      delete resto[id]
      return resto
    })
  }, [])

  // Otra tanda es seguir en el mismo grupo: el formulario nuevo hereda el grupo del último, con la tanda que sigue ya
  // confirmada. Si el último no tiene grupo todavía, nace vacío.
  function agregar() {
    const id = siguienteId.current
    siguienteId.current += 1
    const ultima = resumenes[formularios[formularios.length - 1].id]
    const plantilla = ultima?.grupoKey ? { q: ultima.q, elegido: ultima.elegido, tanda: letraSiguiente(ultima.tanda) } : null
    setFormularios((f) => [...f, { id, plantilla }])
    setFoco(id)
  }

  // El botón único: sube las tandas en el orden en que están, una por una, así la primera crea el grupo y las
  // siguientes caen ya en él. Si una falla (el servidor la rechaza, se cancela), se detiene ahí: las que ya se
  // guardaron quedan guardadas y, al corregir, el mismo botón sigue con las que faltan.
  async function guardarTodas() {
    setGuardando(true)
    let todas = true
    for (const { id } of formularios) {
      if (guardadasRef.current[id]) continue
      if (!(await guardadores.current[id]?.())) {
        todas = false
        break
      }
    }
    setGuardando(false)
    if (todas) terminar()
  }

  // Cierra la carga: a Progreso si quedó alguna tanda de Día 2 en la cola; si no, de vuelta al experimento.
  function terminar() {
    const lista = Object.values(guardadasRef.current)
    const dia2 = lista.filter((g) => g.dia === 'DAY2')
    const dia1 = lista.filter((g) => g.dia === 'DAY1')
    if (dia2.length) {
      const extra = dia1.length ? ' ' + avisoDia1(dia1) : ''
      navigate('/analisis?experimento=' + clave, { state: { aviso: avisoCola(dia2) + extra } })
    } else if (dia1.length) navigate(`/experimentos/${clave}`, { state: { aviso: avisoDia1(dia1) } })
    else navigate(`/experimentos/${clave}`)
  }

  if (error) return <NoEncontrado />
  if (!exp) return <Cargando />
  if (!puedeSubirVideo(user, exp)) {
    return (
      <div className="app narrow">
        <Topbar><Brand sub={exp.titulo} /></Topbar>
        <div className="page">
          <h3 style={{ margin: '0 0 10px' }}>Cargar video por tanda</h3>
          <p className="lead" style={{ margin: '0 0 18px', maxWidth: 560 }}>{SOLO_CREADOR_SUBE} Este lo creó {exp.responsable || 'otra cuenta'}.</p>
          <Link className="btn btn-secondary" style={btnLeft} to={`/experimentos/${exp.clave}`}>Volver al experimento</Link>
        </div>
      </div>
    )
  }

  const subiendoAlguna = Object.values(resumenes).some((r) => r.subiendo)
  const unico = formularios.length === 1
  // Estado del botón único: cada formulario sin guardar debe estar listo (grupo, tanda confirmada y archivo).
  const pendientes = formularios.map((f, i) => ({ ...f, posicion: i + 1, r: resumenes[f.id] })).filter((f) => !f.r?.guardada)
  const faltan = pendientes.filter((f) => !f.r?.lista).map((f) => f.posicion)
  const hayDia2 = pendientes.some((f) => f.r?.sesion !== '1')

  return (
    <div className="app">
      <Topbar>
        <Brand sub={exp.titulo} />
        <Link to="/experimentos">Salir</Link>
      </Topbar>

      <div style={{ padding: '28px 32px 24px' }}>
        <Pasos actual={2} />

        <h3 style={{ margin: '0 0 8px' }}>Cargar video por tanda</h3>
        <p className="lead" style={{ margin: 0, maxWidth: 620 }}>Un video corresponde a una tanda de un solo grupo. Indica el grupo, confirma qué tanda es y suelta el archivo. Con «Agregar otra tanda» puedes preparar varias en esta misma página.</p>
      </div>

      {formularios.map(({ id, plantilla }, i) => (
        <FormularioTanda key={id} clave={clave} exp={exp} id={id} posicion={i + 1} unico={unico} resumenes={resumenes}
          plantilla={plantilla} autoFoco={id === foco} guardando={guardando} registrar={registrar}
          onResumen={onResumen} onGuardada={onGuardada} onQuitar={quitar} />
      ))}

      {/* §9.3: otra tanda sin salir de aquí, o terminar y volver al experimento. */}
      <div style={{ borderTop: '2px solid var(--color-divider)', padding: '18px 32px 36px', display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 10 }}>
        {!unico && (
          <button type="button" className="btn btn-primary" style={btnLeft} disabled={guardando || subiendoAlguna || pendientes.length === 0 || faltan.length > 0} onClick={guardarTodas}>
            {guardando || subiendoAlguna ? 'Subiendo…' : hayDia2 ? 'Guardar y encolar análisis' : 'Guardar videos'}
          </button>
        )}
        <button type="button" className="btn btn-secondary" style={btnLeft} disabled={guardando} onClick={agregar}>+ Agregar otra tanda</button>
        <button type="button" className="btn btn-ghost" style={btnLeft} disabled={guardando || subiendoAlguna} onClick={terminar}>Terminar</button>
        {!unico && !guardando && faltan.length > 0 && (
          <span style={{ fontSize: 11.5, color: 'var(--muted)' }}>
            Se habilita cuando cada tanda tenga su grupo, la tanda confirmada y su archivo. Falta completar: {faltan.length > 1 ? 'los formularios ' : 'el formulario '}{faltan.join(', ')}.
          </span>
        )}
        {!unico && (guardando || subiendoAlguna) && <span style={{ fontSize: 11.5, color: 'var(--muted)' }}>Se suben en orden, una por una: la primera crea el grupo.</span>}
      </div>
    </div>
  )
}
