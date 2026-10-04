import React, { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import DemoBar from '../components/DemoBar'
import '../styles/pages/progreso.css'

const DEMO_STATES = [
  { key: 'proc1', label: 'Analizando Día 1' },
  { key: 'proc2', label: 'Analizando Día 2' },
  { key: 'done', label: 'Análisis completado' },
  { key: 'err_quality', label: 'Error — calidad de video' },
  { key: 'err_detect', label: 'Error — detección fallida' },
]

const STAGES = [
  { id: 'pre', name: 'Preprocesamiento del video', desc: 'Decodificación, normalización de frames y corrección de contraste.' },
  { id: 'det', name: 'Detección de cilindros', desc: 'Localización de los cilindros en el encuadre usando detección de contornos.' },
  { id: 'trk', name: 'Seguimiento de especímenes (tracking)', desc: 'Asignación de cada espécimen a su cilindro y seguimiento frame a frame.' },
  { id: 'cls', name: 'Clasificación de conductas', desc: 'Clasificación segundo a segundo: nado activo, inmovilidad, escalamiento o conducta activa.' },
  { id: 'rep', name: 'Generación de resultados', desc: 'Cálculo de métricas por espécimen y por sesión. Exportación de reportes.' },
]

// Estados de ejemplo del mockup v2
const PRESETS = {
  proc1: {
    tab: 1,
    vtab1: { cls: 'active', txt: 'Analizando', pillCls: 'pill-proc' },
    vtab2: { cls: '', txt: 'En espera', pillCls: 'pill-wait' },
    stages: ['done', 'done', 'active', 'wait', 'wait'],
    pcts: [100, 100, 62, 0, 0],
    overall: 52,
    overallSub: 'Seguimiento de especímenes frame a frame…',
    headerPill: 'pill-proc', headerTxt: 'En proceso',
    subtitle: 'El análisis del Día 1 está en curso. Puedes cerrar esta pestaña; el proceso continuará en segundo plano.',
  },
  proc2: {
    tab: 2,
    vtab1: { cls: 'done', txt: 'Completado', pillCls: 'pill-ok' },
    vtab2: { cls: 'active', txt: 'Analizando', pillCls: 'pill-proc' },
    stages: ['done', 'done', 'done', 'active', 'wait'],
    pcts: [100, 100, 100, 34, 0],
    overall: 74,
    overallSub: 'Clasificando conductas — Día 2…',
    headerPill: 'pill-proc', headerTxt: 'En proceso',
    subtitle: 'El análisis del Día 2 está en curso. El Día 1 ya fue procesado correctamente.',
  },
  done: {
    tab: 1,
    vtab1: { cls: 'done', txt: 'Completado', pillCls: 'pill-ok' },
    vtab2: { cls: 'done', txt: 'Completado', pillCls: 'pill-ok' },
    stages: ['done', 'done', 'done', 'done', 'done'],
    pcts: [100, 100, 100, 100, 100],
    overall: 100,
    overallSub: 'Todos los pasos completados correctamente.',
    headerPill: 'pill-ok', headerTxt: 'Completado',
    subtitle: 'El análisis finalizó. Los resultados ya están disponibles.',
    showSuccess: true,
  },
  err_quality: {
    tab: 2,
    vtab1: { cls: 'done', txt: 'Completado', pillCls: 'pill-ok' },
    vtab2: { cls: 'err', txt: 'Error', pillCls: 'pill-err' },
    stages: ['done', 'done', 'error', 'wait', 'wait'],
    pcts: [100, 100, 0, 0, 0],
    overall: 40,
    overallSub: 'El análisis del Día 2 no pudo completarse.',
    headerPill: 'pill-err', headerTxt: 'Error en análisis',
    subtitle: 'Se encontró un error durante el análisis del video Día 2. Consulta el diagnóstico a continuación.',
    error: {
      title: 'Calidad de video insuficiente',
      code: 'ERR-FST-003',
      msg: 'El sistema no pudo detectar los cilindros en el video del Día 2. Esto ocurre cuando el contraste entre el espécimen y el fondo es demasiado bajo o cuando la imagen presenta reflejos excesivos.',
      details: [
        'Contraste promedio detectado: 0.31 (demasiado bajo para distinguir al espécimen del fondo)',
        'Se aplicó corrección CLAHE sin resultados suficientes (38% de frames afectados)',
        'El video fue grabado con iluminación lateral directa que genera reflejos en el agua',
        'Recomendación: repetir la grabación con iluminación difusa y fondo oscuro',
      ],
    },
  },
  err_detect: {
    tab: 2,
    vtab1: { cls: 'done', txt: 'Completado', pillCls: 'pill-ok' },
    vtab2: { cls: 'err', txt: 'Error', pillCls: 'pill-err' },
    stages: ['done', 'error', 'wait', 'wait', 'wait'],
    pcts: [100, 0, 0, 0, 0],
    overall: 20,
    overallSub: 'El pipeline se detuvo en la etapa de detección.',
    headerPill: 'pill-err', headerTxt: 'Error en análisis',
    subtitle: 'Se encontró un error durante el análisis del video Día 2.',
    error: {
      title: 'No se detectaron cilindros',
      code: 'ERR-FST-007',
      msg: 'El modelo de detección no encontró ningún cilindro en el video con suficiente confianza. El encuadre del video puede no corresponder a la configuración esperada (vista lateral con 2 a 4 cilindros visibles).',
      details: [
        'Cilindros detectados: 0 de los esperados',
        'Ningún cilindro encontrado en el video',
        'Posible causa: video grabado desde un ángulo distinto a la vista lateral',
        'Posible causa: cilindros fuera de cuadro o parcialmente visibles',
        'Recomendación: verificar que el video muestre todos los cilindros en vista lateral',
      ],
    },
  },
}

const LOG = [
  ['09:14:02', 'log-info', '[INFO]  Pipeline iniciado — EXP-036 / video: dia2_posttreatment.mp4'],
  ['09:14:03', 'log-ok', '  [OK]    Decodificación de video exitosa — 1800 frames @ 30fps'],
  ['09:14:05', 'log-ok', '  [OK]    Preprocesamiento completado — dimensiones: 1280×720'],
  ['09:14:06', 'log-warn', '[WARN]  Contraste bajo detectado en 38% de los frames (umbral: 25%)'],
  ['09:14:08', 'log-info', '[INFO]  Aplicando CLAHE para mejorar contraste…'],
  ['09:14:11', 'log-err', ' [ERR]   Detección de cilindros fallida — no se encontraron los tubos'],
  ['09:14:11', 'log-err', ' [ERR]   Abortando pipeline — ERR-FST-003'],
  ['09:14:11', 'log-info', '[INFO]  Reporte de diagnóstico generado → diagnostico_EXP-036.pdf'],
]

function StageIcon({ state }) {
  if (state === 'done') return <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M2 7l4 4 6-6" stroke="#10b981" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/></svg>
  if (state === 'active') return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><circle cx="7" cy="7" r="5.5" stroke="#93c5fd" strokeWidth="1.5"/><path d="M7 7l0-4" stroke="#3b82f6" strokeWidth="1.5" strokeLinecap="round"><animateTransform attributeName="transform" type="rotate" from="0 7 7" to="360 7 7" dur=".8s" repeatCount="indefinite"/></path></svg>
  )
  if (state === 'error') return <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M3 3l8 8M11 3l-8 8" stroke="#ef4444" strokeWidth="1.6" strokeLinecap="round"/></svg>
  return <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><circle cx="7" cy="7" r="5.5" stroke="#d1d5db" strokeWidth="1.3"/></svg>
}

const IconoVideo = () => (
  <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><rect x="1" y="2.5" width="9" height="7" rx="1.2" stroke="currentColor" strokeWidth="1.1"/><path d="M10 5.5l2-1.5v5l-2-1.5V5.5z" stroke="currentColor" strokeWidth="1.1" strokeLinejoin="round"/></svg>
)
const IconoFlecha = () => (
  <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><path d="M2 6.5h9M7 2.5l4 4-4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/></svg>
)

function VideoTab({ label, cfg, selected, onClick }) {
  return (
    <div className={`vtab ${cfg.cls} ${selected ? 'active' : ''}`} onClick={onClick}>
      <IconoVideo />
      {label}
      <span className={`status-pill ${cfg.pillCls} ${cfg.cls}`} style={{ padding: '2px 7px', fontSize: '10.5px', marginLeft: '2px' }}>
        <span className="pill-dot"></span><span>{cfg.txt}</span>
      </span>
    </div>
  )
}

export default function ProgressPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [demo, setDemo] = useState('proc1')
  const [tab, setTab] = useState(PRESETS.proc1.tab)
  const [logVisible, setLogVisible] = useState(false)
  const p = PRESETS[demo]

  const cambiarDemo = (k) => { setDemo(k); setTab(PRESETS[k].tab); setLogVisible(false) }

  return (
    <div className="pg-progreso">
      <DemoBar states={DEMO_STATES} value={demo} onChange={cambiarDemo} />

      <main className="page">
        <nav className="breadcrumb">
          <Link to="/dashboard">Mis experimentos</Link>
          <span className="breadcrumb-sep">›</span>
          <a href="#" onClick={(e) => e.preventDefault()}>FST-2024-036 — Ketamina 30 mg/kg</a>
          <span className="breadcrumb-sep">›</span>
          <span>Progreso del análisis</span>
        </nav>

        <div className="page-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
            <div className="page-title">Progreso del análisis</div>
            <div className={`status-pill ${p.headerPill}`}>
              <span className="pill-dot"></span>
              <span>{p.headerTxt}</span>
            </div>
          </div>
          <div className="page-subtitle">{p.subtitle}</div>
        </div>

        <div className="card">
          <div className="meta-strip">
            <div className="meta-item">
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><rect x="1" y="2" width="11" height="10" rx="1.5" stroke="#9ca3af" strokeWidth="1.1"/><path d="M1 5h11M4.5 1v2M8.5 1v2" stroke="#9ca3af" strokeWidth="1.1" strokeLinecap="round"/></svg>
              <span className="meta-label">Experimento:</span>
              <span className="meta-value">FST-2024-036</span>
            </div>
            <div className="meta-item">
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><path d="M3 2h7l2 2v7a1 1 0 01-1 1H3a1 1 0 01-1-1V3a1 1 0 011-1z" stroke="#9ca3af" strokeWidth="1.1"/><path d="M8 2v3H4" stroke="#9ca3af" strokeWidth="1.1" strokeLinecap="round"/></svg>
              <span className="meta-label">Tratamiento:</span>
              <span className="meta-value">Ketamina 30 mg/kg</span>
            </div>
            <div className="meta-item">
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><circle cx="6.5" cy="6.5" r="5.5" stroke="#9ca3af" strokeWidth="1.1"/><path d="M6.5 4v3l1.5 1.5" stroke="#9ca3af" strokeWidth="1.1" strokeLinecap="round"/></svg>
              <span className="meta-label">Iniciado:</span>
              <span className="meta-value">22 mar 2025, 09:14</span>
            </div>
            <div className="meta-item">
              <svg width="13" height="13" viewBox="0 0 13 13" fill="none"><circle cx="4.5" cy="4" r="2" stroke="#9ca3af" strokeWidth="1.1"/><circle cx="9" cy="4" r="2" stroke="#9ca3af" strokeWidth="1.1"/><path d="M1 10c0-2 1.5-3 3.5-3m4-.001c2 0 3.5 1 3.5 3" stroke="#9ca3af" strokeWidth="1.1" strokeLinecap="round"/></svg>
              <span className="meta-label">Especímenes:</span>
              <span className="meta-value">4</span>
            </div>
          </div>

          <div className="video-tabs">
            <VideoTab label="Día 1 — Basal" cfg={p.vtab1} selected={tab === 1} onClick={() => setTab(1)} />
            <VideoTab label="Día 2 — Post-tratamiento" cfg={p.vtab2} selected={tab === 2} onClick={() => setTab(2)} />
          </div>

          <div>
            <div className="pipeline">
              {STAGES.map((s, i) => {
                const st = p.stages[i]
                const pct = p.pcts[i]
                return (
                  <div key={s.id} className={`stage ${st}`}>
                    <div className="stage-connector"></div>
                    <div className="stage-icon"><StageIcon state={st} /></div>
                    <div className="stage-body">
                      <div className="stage-name">{s.name}</div>
                      <div className="stage-desc">{s.desc}</div>
                      {st === 'active' && pct < 100 && (
                        <div className="stage-progress">
                          <div className="stage-progress-bar-wrap"><div className="stage-progress-bar" style={{ width: `${pct}%` }}></div></div>
                          <div className="stage-progress-pct">{pct}%</div>
                        </div>
                      )}
                      {st === 'done' && <div className="stage-time">✓ Completado</div>}
                      {st === 'active' && <div className="stage-time">En ejecución…</div>}
                      {st === 'wait' && <div className="stage-time">En espera</div>}
                      {st === 'error' && <div className="stage-time" style={{ color: '#b91c1c' }}>✗ Falló</div>}
                    </div>
                  </div>
                )
              })}
            </div>

            <div className="overall-bar-wrap">
              <div className="overall-bar-header">
                <div>
                  <div className="overall-bar-label">{demo === 'done' ? 'Análisis completado' : 'Progreso total del análisis'}</div>
                  <div className="overall-bar-sub">{p.overallSub}</div>
                </div>
                <div className="overall-bar-pct">{p.overall}%</div>
              </div>
              <div className="overall-progress">
                <div className="overall-progress-fill" style={{ width: `${p.overall}%` }}></div>
              </div>
            </div>
          </div>

          {p.error && (
            <div className="error-panel">
              <div className="err-header">
                <div className="err-icon">
                  <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
                    <circle cx="11" cy="11" r="9.5" stroke="#f87171" strokeWidth="1.4" fill="#fef2f2"/>
                    <path d="M11 7v5" stroke="#dc2626" strokeWidth="1.8" strokeLinecap="round"/>
                    <circle cx="11" cy="15" r="1" fill="#dc2626"/>
                  </svg>
                </div>
                <div>
                  <div className="err-title">{p.error.title}</div>
                  <div className="err-code">{p.error.code}</div>
                  <div className="err-message">{p.error.msg}</div>
                </div>
              </div>

              <div className="err-detail-title">Detalles del diagnóstico</div>
              <ul className="err-detail-list">
                {p.error.details.map((d) => <li key={d} className="err-detail-item">{d}</li>)}
              </ul>

              <div className="no-control-notice" style={{ marginBottom: '18px' }}>
                <svg width="14" height="14" viewBox="0 0 14 14" fill="none" style={{ flexShrink: 0, marginTop: '1px' }}><circle cx="7" cy="7" r="6" stroke="#9ca3af" strokeWidth="1.1"/><path d="M7 5.5v3" stroke="#9ca3af" strokeWidth="1.2" strokeLinecap="round"/><circle cx="7" cy="10" r=".6" fill="#9ca3af"/></svg>
                <span>El análisis no puede reiniciarse desde esta pantalla. Para reintentar, crea un nuevo experimento desde el dashboard o contacta al administrador del sistema.</span>
              </div>

              <div className="err-actions">
                <button className="btn-err-pdf">
                  <svg width="15" height="15" viewBox="0 0 15 15" fill="none">
                    <path d="M3 2h6.5L12 4.5V13H3V2z" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round"/>
                    <path d="M9 2v3h3" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round"/>
                    <path d="M7.5 6.5v4M5.5 8.5l2 2 2-2" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                  Descargar reporte de diagnóstico (PDF)
                </button>
                <button className="btn-ghost" onClick={() => navigate('/dashboard')}>← Volver al dashboard</button>
              </div>

              <div style={{ marginTop: '20px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div className="err-detail-title" style={{ marginBottom: 0 }}>Registro de ejecución</div>
                <button className="log-toggle" onClick={() => setLogVisible((v) => !v)}>
                  <svg width="12" height="12" viewBox="0 0 12 12" fill="none" style={{ transform: logVisible ? 'rotate(180deg)' : undefined }}><path d="M2 4l4 4 4-4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/></svg>
                  <span>{logVisible ? 'Ocultar log' : 'Mostrar log'}</span>
                </button>
              </div>
              {logVisible && (
                <div className="log-console" style={{ display: 'block' }}>
                  {LOG.map(([ts, cls, txt], i) => (
                    <div key={i} className="log-line"><span className="log-ts">{ts}</span><span className={cls}>{txt}</span></div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>

        {p.showSuccess && (
          <div className="success-banner">
            <svg width="22" height="22" viewBox="0 0 22 22" fill="none"><circle cx="11" cy="11" r="9.5" stroke="#6ee7b7" strokeWidth="1.4" fill="#ecfdf5"/><path d="M6 11l3.5 3.5 6.5-6.5" stroke="#10b981" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>
            <div className="success-banner-text">
              <strong>Análisis completado exitosamente</strong>
              Todos los videos fueron procesados. Los resultados están disponibles.
            </div>
            <button className="btn-ghost" onClick={() => navigate(`/experiments/${id}/review`)}>
              Revisar segundo a segundo
            </button>
            <button className="btn-primary" onClick={() => navigate(`/experiments/${id}/results`)}>
              Ver resultados
              <IconoFlecha />
            </button>
          </div>
        )}

        {!p.error && !p.showSuccess && (
          <div className="no-control-notice">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none" style={{ flexShrink: 0, marginTop: '1px' }}><path d="M7 1.5L1 5v4.5l6 3 6-3V5L7 1.5z" stroke="#9ca3af" strokeWidth="1.1" strokeLinejoin="round"/></svg>
            <span>El análisis es gestionado por el servidor. No es posible pausarlo ni cancelarlo desde esta pantalla. Puedes navegar a otras secciones del sistema sin interrumpir el proceso.</span>
          </div>
        )}
      </main>
    </div>
  )
}
