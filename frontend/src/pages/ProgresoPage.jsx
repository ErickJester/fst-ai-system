import React, { useEffect, useRef, useState } from 'react'
import Topbar, { Brand } from '../components/Topbar'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { getDiagnostico } from '../services/queue'
import { getQueue } from '../services/progreso'
import { mensajeError } from '../services/api'
import { usePolling } from '../hooks/usePolling'
import { CADA_ACTIVO_MS, CADA_INACTIVO_MS } from '../hooks/useAsync'
import { DIA, ETAPA, PESO_ETAPA, pctDeEtapa, enlacePausa, enlaceResultados, enlaceRevision, fechaCorta, mensajeErrorAnalisis, mmss, download, norm } from '../lib/fst'
import { useAuth } from '../contexts/AuthContext'
import { Cargando, FieldError } from '../components/ui'
import { MUT60, btnLeft } from '../lib/estilos'

const INK = 'var(--color-text)'
const ETAPAS = Object.keys(ETAPA)

// Qué hace cada etapa, para un trabajo con n especímenes.
const DETALLE = {
  PREPROCESSING: () => 'preparación del video',
  ROI_DETECTION: (n) => 'localización de los ' + n + ' cilindros',
  CLASSIFICATION: () => 'nado activo, inmovilidad, escalamiento y conducta activa',
}

const nombreTrabajo = (j) => j.experimento + ' · ' + j.grupo + ' · Tanda ' + j.tanda

// Solo se analiza el Día 2 (D-54): sus últimos 5 min.
const duracion = (j) => DIA[j.dia] + ' · 5 min'

// Si el PDF de diagnóstico aún no existe, se vuelve a pedir tras esta espera.
const REINTENTO_DIAGNOSTICO_MS = 4000

// La cola se consulta cada 5 s mientras haya análisis activos (en cola o procesando);
// sin ninguno, cada 30 s, solo para ver si llega uno nuevo.

// 2e · Progreso de análisis: tres etapas del pipeline, cola secuencial sin controles.
export default function ProgresoPage() {
  const aviso = useLocation().state?.aviso // al llegar después de subir un video de Día 2
  // ?experimento=: solo los análisis de ese experimento (§9.4, GET /experiments/{id}/status).
  const experimento = useSearchParams()[0].get('experimento')
  const [activos, setActivos] = useState(true)
  const { data, error } = usePolling(() => getQueue(experimento), activos ? CADA_ACTIVO_MS : CADA_INACTIVO_MS)
  useEffect(() => {
    if (data) setActivos(data.cola.length > 0 || data.pausas.length > 0)
  }, [data])

  if (!data && !error) return <Cargando />

  const cola = data?.cola || []
  const errores = data?.errores || []
  const pausas = data?.pausas || []
  const cancelados = data?.cancelados || []
  const terminados = data?.terminados || []
  const actual = cola.find((j) => j.status === 'RUNNING')
  const proc = cola.filter((j) => j.status === 'RUNNING').length

  return (
    <div className="app narrow">
      <Topbar><Brand sub="Análisis en curso" /></Topbar>

      <div className="page" style={{ paddingBottom: 34 }}>
        {aviso && <div className="note-bar" role="status" style={{ marginBottom: 18 }}>{aviso}</div>}
        <h3 style={{ margin: '0 0 8px' }}>Análisis en curso</h3>
        {experimento && (
          <p className="hint" style={{ margin: '0 0 10px' }}>
            Solo los análisis de este experimento. <Link to="/analisis">Ver los de todos los experimentos</Link>
          </p>
        )}
        <p className="lead" style={{ margin: '0 0 22px', maxWidth: 600 }}>
          El sistema analiza un video a la vez, en el orden en que se subieron; esta pantalla se actualiza sola. El orden lo fija el sistema al momento de la carga: aquí no se inicia, pausa, cancela ni reinicia. Si el sistema deja de ver a un espécimen, el análisis queda en pausa y se atiende en su pantalla.
        </p>

        {error && <p className="hint" style={{ margin: '0 0 18px', color: 'var(--color-accent-700)' }}>No se pudo consultar la cola. Se vuelve a intentar en unos segundos.</p>}

        {pausas.map((j) => <TrabajoEnPausa key={j.job_id} j={j} />)}
        {actual && <TrabajoActual j={actual} />}
        {errores.map((j) => <TrabajoFallido key={j.job_id} j={j} />)}
        {cancelados.map((j) => <TrabajoCancelado key={j.job_id} j={j} />)}

        <div className="k" style={{ margin: '26px 0 12px' }}>Cola · {proc} procesando, {cola.length - proc} en cola</div>
        <table className="table">
          <tbody>
            {cola.map((j) => (
              <tr key={j.job_id}>
                <td className="num" style={{ width: 30, color: 'color-mix(in srgb,var(--color-text) 50%,transparent)' }}>{j.posicion}</td>
                <td>{nombreTrabajo(j)}</td>
                <td className="num" style={{ width: 70 }}>{DIA[j.dia]}</td>
                <td style={{ width: 130, textAlign: 'right' }}>
                  {j.status === 'RUNNING'
                    ? <span className="tag tag-accent" style={{ whiteSpace: 'nowrap' }}>Procesando</span>
                    : <span className="tag tag-outline" style={{ whiteSpace: 'nowrap' }}>En cola</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && cola.length === 0 && <p className="hint" style={{ margin: '14px 0 0' }}>No hay trabajos en la cola.</p>}

        {terminados.length > 0 && <Terminados lista={terminados} />}
        <p className="hint" style={{ margin: '14px 0 0' }}>
          Al terminar cada trabajo llega una notificación en el sistema con el enlace a resultados. Si un video falla o queda en pausa, la cola sigue con el siguiente.
        </p>
      </div>
    </div>
  )
}

function TrabajoActual({ j }) {
  const iActual = ETAPAS.indexOf(j.stage)

  return (
    <div style={{ border: '2px solid var(--color-text)' }}>
      <div style={{ padding: '18px 20px', borderBottom: '2px solid var(--color-divider)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div className="nd" style={{ fontSize: 17 }}>{nombreTrabajo(j)}</div>
          <div style={{ flex: 1 }} />
          <span className="tag tag-accent" style={{ whiteSpace: 'nowrap' }}>Procesando</span>
        </div>
        <div className="num" style={{ fontSize: 12, marginTop: 4, color: MUT60 }}>{duracion(j)} · {j.n_especimenes} especímenes</div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginTop: 14 }}>
          {/* Una sola barra, de 0 a 100: el ancho es el avance GENERAL. Las marcas finas dicen dónde empieza cada etapa */}
          <div role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={j.progress_pct} aria-label="Avance general del análisis" style={{ flex: 1, position: 'relative', height: 10, background: 'color-mix(in srgb,var(--color-text) 12%,transparent)' }}>
            <div style={{ width: j.progress_pct + '%', height: '100%', background: 'var(--color-accent)', transition: 'width 1s linear' }} />
            {ETAPAS.slice(1).map((s, k) => {
              const inicio = ETAPAS.slice(0, k + 1).reduce((suma, e) => suma + PESO_ETAPA[e], 0)
              return <div key={s} style={{ position: 'absolute', top: 0, bottom: 0, left: inicio + '%', width: 2, background: 'var(--color-bg)' }} />
            })}
          </div>
          <span className="num nd" style={{ fontSize: 15, whiteSpace: 'nowrap' }}>{j.progress_pct} %</span>
        </div>
      </div>

      {ETAPAS.map((s, i) => {
        const hecha = i < iActual
        const enCurso = i === iActual
        const color = hecha || enCurso ? INK : 'var(--muted)'
        const pct = pctDeEtapa(j, s, ETAPAS)
        return (
          <div key={s} style={{ position: 'relative', display: 'flex', alignItems: 'center', gap: 16, padding: '14px 20px', borderBottom: '1px solid var(--color-divider)' }}>
            <span className="num nd" style={{ width: 26, height: 26, flex: 'none', background: hecha ? INK : enCurso ? 'var(--color-accent)' : 'transparent', color: hecha || enCurso ? 'var(--color-bg)' : 'var(--muted)', border: '1px solid var(--color-divider)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11 }}>{hecha ? '✓' : i + 1}</span>
            <div style={{ width: 250 }}>
              <div className="nd" style={{ fontSize: 13.5, color }}>{ETAPA[s]}</div>
              <div style={{ fontSize: 11.5, marginTop: 2, color: 'var(--muted)' }}>{DETALLE[s](j.n_especimenes)}</div>
            </div>
            <div style={{ flex: 1 }} />
            <span style={{ fontSize: 12, whiteSpace: 'nowrap', color }}>{hecha ? 'hecha' : enCurso ? 'en curso' : 'pendiente'}</span>
            <span className="num nd" style={{ width: 56, textAlign: 'right', fontSize: 13, color }}>{pct} %</span>
            {/* barra fina de la propia etapa, pegada al borde de abajo de la fila */}
            <div style={{ position: 'absolute', left: 20, right: 20, bottom: 4, height: 3, background: 'color-mix(in srgb,var(--color-text) 12%,transparent)' }}>
              <div style={{ width: pct + '%', height: '100%', background: hecha ? INK : 'var(--color-accent)', transition: 'width 1s linear' }} />
            </div>
          </div>
        )
      })}

    </div>
  )
}

function TrabajoFallido({ j }) {
  const detalle = duracion(j) + ' · detenido en ' + ETAPA[j.stage] + ' (' + j.progress_pct + ' %)'
  // null | 'pidiendo' | 'preparando' (el PDF aún no existe: se reintenta solo) | 'demo' (sin backend) | 'error'
  const [diag, setDiag] = useState(null)
  const [errDiag, setErrDiag] = useState('')
  const reintento = useRef(null)
  useEffect(() => () => clearTimeout(reintento.current), [])

  async function descargar() {
    clearTimeout(reintento.current)
    setDiag((d) => (d === 'preparando' ? d : 'pidiendo'))
    setErrDiag('')
    try {
      const pdf = await getDiagnostico(j)
      if (!pdf) return setDiag('demo')
      download(['diagnostico', norm(j.experimento), norm(j.grupo), 'tanda' + j.tanda].join('_').replace(/\s+/g, '-') + '.pdf', pdf, 'application/pdf')
      setDiag(null)
    } catch (e) {
      if (e.response?.status === 404) {
        setDiag('preparando')
        reintento.current = setTimeout(descargar, REINTENTO_DIAGNOSTICO_MS)
      } else {
        setDiag('error')
        setErrDiag(mensajeError(e, 'No se pudo descargar el reporte de diagnóstico. Inténtalo de nuevo.'))
      }
    }
  }

  return (
    <div style={{ border: '2px solid var(--color-accent)', marginTop: 18 }}>
      <div style={{ padding: '16px 20px', borderBottom: '1px solid var(--color-divider)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div className="nd" style={{ fontSize: 15 }}>{nombreTrabajo(j)}</div>
          <div style={{ flex: 1 }} />
          <span className="tag tag-accent" style={{ whiteSpace: 'nowrap', background: 'var(--color-accent-800)', color: 'var(--color-bg)' }}>Error</span>
        </div>
        <div className="num" style={{ fontSize: 12, marginTop: 4, color: MUT60 }}>{detalle}</div>
      </div>
      <div style={{ padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div className="nd" style={{ fontSize: 13.5, lineHeight: 1.55 }}>{mensajeErrorAnalisis(j.stage)}</div>
        <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 14 }}>
          <button type="button" className="btn btn-primary" style={btnLeft} disabled={diag === 'pidiendo' || diag === 'preparando'} onClick={descargar}>
            {diag === 'preparando' ? 'Preparando…' : 'Descargar reporte de diagnóstico (PDF)'}
          </button>
          <span className="hint" aria-live="polite">
            {diag === 'preparando' ? 'El reporte todavía se está generando; se descarga solo en cuanto esté listo.' : 'El investigador no puede reiniciar el análisis.'}
          </span>
        </div>
        <FieldError msg={errDiag} />
        {diag === 'demo' && (
          <div className="note-bar" role="status" style={{ lineHeight: 1.55 }}>
            <strong>Reporte de diagnóstico.</strong> El sistema lo prepara cuando el análisis falla y aquí se descarga. Es un PDF con el video, la etapa en que se detuvo y la causa: {mensajeErrorAnalisis(j.stage).toLowerCase()} Todavía no está disponible en el modo demo.
          </div>
        )}
      </div>
    </div>
  )
}

// Análisis en pausa: el sistema espera que se ajuste el recuadro del cilindro (R-15).
function TrabajoEnPausa({ j }) {
  // Cuenta regresiva local a partir de lo que dijo el servidor en la última consulta.
  const [vence] = useState(() => Date.now() + j.segundos_restantes * 1000)
  const [ahora, setAhora] = useState(() => Date.now())
  useEffect(() => {
    const t = setInterval(() => setAhora(Date.now()), 1000)
    return () => clearInterval(t)
  }, [])
  const quedan = Math.max(0, Math.ceil((vence - ahora) / 1000))

  return (
    <div style={{ border: '2px solid var(--color-accent)', marginBottom: 18 }}>
      <div style={{ padding: '16px 20px', display: 'flex', alignItems: 'center', gap: 12 }}>
        <div style={{ flex: 1 }}>
          <div className="nd" style={{ fontSize: 15 }}>{nombreTrabajo(j)}</div>
          <div className="num" style={{ fontSize: 12, marginTop: 4, color: MUT60 }}>{duracion(j)} · pausa en el segundo {j.pausa.desde_segundo}</div>
          <div style={{ fontSize: 13, marginTop: 6 }}>No se ve al espécimen del cilindro {j.pausa.cilindro} (segundo {j.pausa.desde_segundo}).</div>
          <div className="num" style={{ fontSize: 12, marginTop: 4, color: 'var(--color-accent-700)' }}>
            {quedan > 0 ? 'Tiempo para responder: ' + mmss(quedan) : 'Pasaron los 5 minutos: se cancela.'}
          </div>
        </div>
        <span className="tag tag-accent" style={{ whiteSpace: 'nowrap' }}>En pausa</span>
      </div>
      <div style={{ padding: '0 20px 16px' }}>
        <Link className="btn btn-primary" style={btnLeft} to={enlacePausa(j.clave, j.job_id)}>Ajustar o cancelar →</Link>
      </div>
    </div>
  )
}

// Análisis cancelado: por quien lo atendía o porque nadie respondió a la pausa.
function TrabajoCancelado({ j }) {
  return (
    <div style={{ border: '1px solid var(--color-divider)', marginTop: 18, padding: '14px 20px', display: 'flex', alignItems: 'center', gap: 12 }}>
      <div style={{ flex: 1 }}>
        <div className="nd" style={{ fontSize: 14 }}>{nombreTrabajo(j)}</div>
        <div style={{ fontSize: 12, marginTop: 4, color: MUT60 }}>
          {j.motivo === 'plazo' ? 'Pasaron 5 minutos en pausa sin respuesta y el sistema canceló el análisis.' : 'Se canceló desde la pantalla de pausa.'} No se guardó ningún resultado de esta tanda. Para analizarlo otra vez, sube el video de nuevo.
        </div>
      </div>
      <span className="tag tag-accent" style={{ whiteSpace: 'nowrap', background: 'var(--color-accent-800)', color: 'var(--color-bg)' }}>Cancelado</span>
    </div>
  )
}

// Completados recientemente (§9.4): al completarse un análisis se habilitan «Ver resultados» y
// «Revisar segundo a segundo» (este, solo para quien subió el video o un Administrador).
function Terminados({ lista }) {
  const { user } = useAuth()
  return (
    <>
      <div className="k" style={{ margin: '26px 0 12px' }}>Completados recientemente</div>
      <table className="table">
        <tbody>
          {lista.map((j) => (
            <tr key={j.job_id}>
              <td>{nombreTrabajo(j)}</td>
              <td className="num" style={{ width: 70 }}>{DIA[j.dia]}</td>
              <td className="num" style={{ width: 110, fontSize: 12, color: MUT60 }}>{j.analisis_fecha ? fechaCorta(j.analisis_fecha) : ''}</td>
              <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                <span style={{ display: 'inline-flex', gap: 14, fontSize: 12.5 }}>
                  {(user.admin || j.subido_por === user.id) && <Link to={enlaceRevision(j.clave, j.job_id)}>Revisar segundo a segundo</Link>}
                  <Link to={enlaceResultados(j.clave, j.gid, j.tanda)}>Ver resultados</Link>
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}
