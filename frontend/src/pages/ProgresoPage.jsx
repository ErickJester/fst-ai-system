import React from 'react'
import Topbar, { Brand } from '../components/Topbar'
import { ETAPAS, COLA, ERROR_ANALISIS as err } from '../data/mock'

const MUT60 = 'color-mix(in srgb,var(--color-text) 60%,transparent)'

// Reporte de diagnóstico: se imprime o guarda como PDF desde el navegador.
function descargarDiagnostico() {
  const w = window.open('', '_blank')
  if (!w) return
  w.document.write(
    '<!DOCTYPE html><html lang="es"><head><meta charset="utf-8"><title>Reporte de diagnóstico · ' + err.codigo + '</title>' +
    '<style>body{font-family:Archivo,system-ui,sans-serif;color:#231f20;padding:32px;line-height:1.55}h3{font-weight:800;font-size:25px;margin:0 0 12px}</style></head>' +
    '<body><h3>Reporte de diagnóstico</h3>' +
    '<p><strong>' + err.titulo + '</strong><br>' + err.detalle + '</p>' +
    '<p><strong>' + err.codigo + '</strong> · ' + err.causa + '</p>' +
    '<script>onload=()=>print()<\/script></body></html>'
  )
  w.document.close()
}

// 2e · Progreso de análisis: cuatro etapas del pipeline, cola secuencial sin controles.
export default function ProgresoPage() {
  const actual = ETAPAS.find((s) => s.estado === 'en curso')
  const proc = COLA.filter((c) => c.estado === 'Procesando').length

  return (
    <div className="app narrow">
      <Topbar><Brand sub="Análisis en curso" /></Topbar>

      <div className="page" style={{ paddingBottom: 34 }}>
        <h3 style={{ margin: '0 0 8px' }}>Análisis en curso</h3>
        <p className="lead" style={{ margin: '0 0 22px', maxWidth: 600 }}>
          El <em>pipeline</em> (cadena de procesamiento) atiende un trabajo a la vez. El orden lo fija el sistema al momento de la carga: no hay iniciar, pausar, cancelar ni reiniciar desde aquí.
        </p>

        <div style={{ border: '2px solid var(--color-text)' }}>
          <div style={{ padding: '18px 20px', borderBottom: '2px solid var(--color-divider)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <div className="nd" style={{ fontSize: 17 }}>{COLA[0].nombre}</div>
              <div style={{ flex: 1 }} />
              <span className="tag tag-accent" style={{ whiteSpace: 'nowrap' }}>Procesando</span>
            </div>
            <div className="num" style={{ fontSize: 12, marginTop: 4, color: MUT60 }}>Día 2 · 5 min · 4 especímenes</div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginTop: 14 }}>
              <div style={{ flex: 1, display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 2 }}>
                {ETAPAS.map((s) => (
                  <div key={s.nombre} style={{ height: 10, background: s.estado === 'hecha' ? 'var(--color-text)' : s.estado === 'en curso' ? 'var(--color-accent)' : 'color-mix(in srgb,var(--color-text) 12%,transparent)' }} />
                ))}
              </div>
              <span className="num nd" style={{ fontSize: 15, whiteSpace: 'nowrap' }}>{actual ? actual.pct : '100 %'}</span>
            </div>
          </div>

          {ETAPAS.map((s) => (
            <div key={s.nombre} style={{ display: 'flex', alignItems: 'center', gap: 16, padding: '14px 20px', borderBottom: '1px solid var(--color-divider)' }}>
              <span className="num nd" style={{ width: 26, height: 26, flex: 'none', background: s.dotBg, color: s.dotFg, border: '1px solid var(--color-divider)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11 }}>{s.icono}</span>
              <div style={{ width: 250 }}>
                <div className="nd" style={{ fontSize: 13.5, color: s.titleFg }}>{s.nombre}</div>
                <div style={{ fontSize: 11.5, marginTop: 2, color: 'var(--muted)' }}>{s.detalle}</div>
              </div>
              <div style={{ flex: 1 }} />
              <span style={{ fontSize: 12, whiteSpace: 'nowrap', color: s.titleFg }}>{s.estado}</span>
              <span className="num nd" style={{ width: 56, textAlign: 'right', fontSize: 13, color: s.titleFg }}>{s.pct}</span>
            </div>
          ))}

          <div style={{ padding: '14px 20px', background: 'var(--color-surface)', fontSize: 12 }}>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
              <span>Confianza de detección:</span>
              <span className="num nd" style={{ fontSize: 15 }}>0.86</span>
              <span className="num" style={{ color: MUT60 }}>(mínimo 0.70)</span>
            </div>
            <div className="hint" style={{ marginTop: 4 }}>Es la confianza de la detección de cilindros. Por debajo de 0.70, el análisis se detiene completo.</div>
          </div>
        </div>

        <div style={{ border: '2px solid var(--color-accent)', marginTop: 18 }}>
          <div style={{ padding: '16px 20px', borderBottom: '1px solid var(--color-divider)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <div className="nd" style={{ fontSize: 15 }}>{err.titulo}</div>
              <div style={{ flex: 1 }} />
              <span className="tag tag-accent" style={{ whiteSpace: 'nowrap', background: 'var(--color-accent-800)', color: 'var(--color-bg)' }}>Error</span>
            </div>
            <div className="num" style={{ fontSize: 12, marginTop: 4, color: MUT60 }}>{err.detalle}</div>
          </div>
          <div style={{ padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ display: 'flex', gap: 14, alignItems: 'baseline' }}>
              <span className="num nd" style={{ fontSize: 13, color: 'var(--color-accent-700)', whiteSpace: 'nowrap' }}>{err.codigo}</span>
              <span style={{ fontSize: 13, lineHeight: 1.55 }}>{err.causa}</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 14 }}>
              <button type="button" className="btn btn-primary" style={{ justifyContent: 'flex-start' }} onClick={descargarDiagnostico}>Descargar reporte de diagnóstico (PDF)</button>
              <span className="hint">El investigador no puede reiniciar el análisis.</span>
            </div>
          </div>
        </div>

        <div className="k" style={{ margin: '26px 0 12px' }}>Cola · {proc} procesando, {COLA.length - proc} en cola</div>
        <table className="table">
          <tbody>
            {COLA.map((c) => (
              <tr key={c.n}>
                <td className="num" style={{ width: 30, color: 'color-mix(in srgb,var(--color-text) 50%,transparent)' }}>{c.n}</td>
                <td>{c.nombre}</td>
                <td className="num" style={{ width: 70 }}>{c.dia}</td>
                <td style={{ width: 130, textAlign: 'right' }}><span className={'tag ' + c.tagClass} style={{ whiteSpace: 'nowrap' }}>{c.estado}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="hint" style={{ margin: '14px 0 0' }}>
          Al terminar cada trabajo llega una notificación en el sistema con el enlace a resultados. Si un video falla, el trabajo se marca con la causa y la cola sigue con el siguiente.
        </p>
      </div>
    </div>
  )
}
