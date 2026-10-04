import React, { useEffect, useState } from 'react'
import Topbar, { Brand } from '../components/Topbar'
import { getQueue } from '../services/queue'
import { usePolling } from '../hooks/usePolling'
import { DIA, ETAPA } from '../lib/fst'
import { Cargando } from '../components/ui'
import { MUT60, btnLeft } from '../lib/estilos'

const INK = 'var(--color-text)'
const CONFIANZA_MIN = 0.7
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

// Día 1 dura 20 min, pero de los dos días se analizan 5 min.
const duracion = (j) => DIA[j.dia] + ' · 5 min'

// Reporte de diagnóstico: se imprime o guarda como PDF desde el navegador.
function descargarDiagnostico(j, detalle) {
  const w = window.open('', '_blank')
  if (!w) return
  w.document.write(
    '<!DOCTYPE html><html lang="es"><head><meta charset="utf-8"><title>Reporte de diagnóstico · ' + j.error.codigo + '</title>' +
    '<style>body{font-family:Archivo,system-ui,sans-serif;color:#231f20;padding:32px;line-height:1.55}h3{font-weight:800;font-size:25px;margin:0 0 12px}</style></head>' +
    '<body><h3>Reporte de diagnóstico</h3>' +
    '<p><strong>' + nombreTrabajo(j) + '</strong><br>' + detalle + '</p>' +
    '<p><strong>' + j.error.codigo + '</strong> · ' + j.error.mensaje + '</p>' +
    '<script>onload=()=>print()<\/script></body></html>'
  )
  w.document.close()
}

// La cola se consulta cada 5 s mientras haya análisis activos (en cola o procesando);
// sin ninguno, cada 30 s, solo para ver si llega uno nuevo.
const CADA_ACTIVO_MS = 5000
const CADA_INACTIVO_MS = 30000

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

      {j.confianza != null && (
        <div style={{ padding: '14px 20px', background: 'var(--color-surface)', fontSize: 12 }}>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
            <span>Confianza de detección:</span>
            <span className="num nd" style={{ fontSize: 15 }}>{j.confianza.toFixed(2)}</span>
            <span className="num" style={{ color: MUT60 }}>(mínimo {CONFIANZA_MIN.toFixed(2)})</span>
          </div>
          <div className="hint" style={{ marginTop: 4 }}>Es la confianza de la detección de cilindros. Por debajo de {CONFIANZA_MIN.toFixed(2)}, el análisis se detiene completo.</div>
        </div>
      )}
    </div>
  )
}

function TrabajoFallido({ j }) {
  const detalle = duracion(j) + ' · detenido en ' + ETAPA[j.stage] + ' (' + j.progress_pct + ' %)'

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
        <div style={{ display: 'flex', gap: 14, alignItems: 'baseline' }}>
          <span className="num nd" style={{ fontSize: 13, color: 'var(--color-accent-700)', whiteSpace: 'nowrap' }}>{j.error.codigo}</span>
          <span style={{ fontSize: 13, lineHeight: 1.55 }}>{j.error.mensaje}</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 14 }}>
          <button type="button" className="btn btn-primary" style={btnLeft} onClick={() => descargarDiagnostico(j, detalle)}>Descargar reporte de diagnóstico (PDF)</button>
          <span className="hint">El investigador no puede reiniciar el análisis.</span>
        </div>
      </div>
    </div>
  )
}
