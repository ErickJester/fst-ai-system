import React, { useEffect, useRef, useState } from 'react'
import Topbar, { Brand } from '../components/Topbar'
import { getQueue, getDiagnostico } from '../services/queue'
import { mensajeError } from '../services/api'
import { usePolling } from '../hooks/usePolling'
import { CADA_ACTIVO_MS, CADA_INACTIVO_MS } from '../hooks/useAsync'
import { DIA, ETAPA, mensajeErrorAnalisis, download, norm } from '../lib/fst'
import { Cargando, FieldError } from '../components/ui'
import { MUT60, btnLeft } from '../lib/estilos'

const INK = 'var(--color-text)'
const ETAPAS = Object.keys(ETAPA)

// Qué hace cada etapa, para un trabajo con n especímenes.
const DETALLE = {
  PREPROCESSING: () => 'CLAHE (realce local de contraste)',
  ROI_DETECTION: (n) => n + ' cilindros detectados',
  TRACKING: (n) => 'tracking (seguimiento) de ' + n + ' especímenes',
  CLASSIFICATION: () => 'nado activo, inmovilidad, escalamiento',
}

// Cada etapa cierra un cuarto del análisis.
const pctEtapa = (i) => (i + 1) * 25 + ' %'

const nombreTrabajo = (j) => j.experimento + ' · ' + j.grupo + ' · Tanda ' + j.tanda

// Solo se analiza el Día 2 (D-54): sus últimos 5 min.
const duracion = (j) => DIA[j.dia] + ' · 5 min'

// Si el PDF de diagnóstico aún no existe, se vuelve a pedir tras esta espera.
const REINTENTO_DIAGNOSTICO_MS = 4000

// La cola se consulta cada 5 s mientras haya análisis activos (en cola o procesando);
// sin ninguno, cada 30 s, solo para ver si llega uno nuevo.

// 2e · Progreso de análisis: cuatro etapas del pipeline, cola secuencial sin controles.
export default function ProgresoPage() {
  const [activos, setActivos] = useState(true)
  const { data, error } = usePolling(getQueue, activos ? CADA_ACTIVO_MS : CADA_INACTIVO_MS)
  useEffect(() => {
    if (data) setActivos(data.cola.length > 0)
  }, [data])

  if (!data && !error) return <Cargando />

  const cola = data?.cola || []
  const errores = data?.errores || []
  const actual = cola.find((j) => j.status === 'RUNNING')
  const proc = cola.filter((j) => j.status === 'RUNNING').length

  return (
    <div className="app narrow">
      <Topbar><Brand sub="Análisis en curso" /></Topbar>

      <div className="page" style={{ paddingBottom: 34 }}>
        <h3 style={{ margin: '0 0 8px' }}>Análisis en curso</h3>
        <p className="lead" style={{ margin: '0 0 22px', maxWidth: 600 }}>
          El <em>pipeline</em> (cadena de procesamiento) atiende un trabajo a la vez. El orden lo fija el sistema al momento de la carga: no hay iniciar, pausar, cancelar ni reiniciar desde aquí.
        </p>

        {error && <p className="hint" style={{ margin: '0 0 18px', color: 'var(--color-accent-700)' }}>No se pudo consultar la cola. Se vuelve a intentar en unos segundos.</p>}

        {actual && <TrabajoActual j={actual} />}
        {errores.map((j) => <TrabajoFallido key={j.job_id} j={j} />)}

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
        <p className="hint" style={{ margin: '14px 0 0' }}>
          Al terminar cada trabajo llega una notificación en el sistema con el enlace a resultados. Si un video falla, el trabajo se marca con la causa y la cola sigue con el siguiente.
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
          <div style={{ flex: 1, display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 2 }}>
            {ETAPAS.map((s, i) => (
              <div key={s} style={{ height: 10, background: i < iActual ? 'var(--color-text)' : i === iActual ? 'var(--color-accent)' : 'color-mix(in srgb,var(--color-text) 12%,transparent)' }} />
            ))}
          </div>
          <span className="num nd" style={{ fontSize: 15, whiteSpace: 'nowrap' }}>{j.progress_pct} %</span>
        </div>
      </div>

      {ETAPAS.map((s, i) => {
        const hecha = i < iActual
        const enCurso = i === iActual
        const color = hecha || enCurso ? INK : 'var(--muted)'
        return (
          <div key={s} style={{ display: 'flex', alignItems: 'center', gap: 16, padding: '14px 20px', borderBottom: '1px solid var(--color-divider)' }}>
            <span className="num nd" style={{ width: 26, height: 26, flex: 'none', background: hecha ? INK : enCurso ? 'var(--color-accent)' : 'transparent', color: hecha || enCurso ? 'var(--color-bg)' : 'var(--muted)', border: '1px solid var(--color-divider)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11 }}>{hecha ? '✓' : i + 1}</span>
            <div style={{ width: 250 }}>
              <div className="nd" style={{ fontSize: 13.5, color }}>{ETAPA[s]}</div>
              <div style={{ fontSize: 11.5, marginTop: 2, color: 'var(--muted)' }}>{DETALLE[s](j.n_especimenes)}</div>
            </div>
            <div style={{ flex: 1 }} />
            <span style={{ fontSize: 12, whiteSpace: 'nowrap', color }}>{hecha ? 'hecha' : enCurso ? 'en curso' : 'pendiente'}</span>
            <span className="num nd" style={{ width: 56, textAlign: 'right', fontSize: 13, color }}>{pctEtapa(i)}</span>
          </div>
        )
      })}

    </div>
  )
}

function TrabajoFallido({ j }) {
  const detalle = duracion(j) + ' · detenido en ' + ETAPA[j.stage] + ' (' + j.progress_pct + ' %)'
  // null | 'pidiendo' | 'preparando' (el PDF aún no existe: se reintenta solo) | 'error'
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
      </div>
    </div>
  )
}
