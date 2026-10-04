import React, { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import DemoBar from '../components/DemoBar'
import '../styles/pages/resultados.css'

const DEMO_STATES = [
  { key: 'd2only', label: 'Solo Día 2 (un video)' },
  { key: 'both', label: 'Día 1 + Día 2 — comparación' },
  { key: 'minexpand', label: 'Con desglose por minuto' },
]

// Datos de ejemplo del mockup v2
const D2 = [
  { id: 'Espécimen 1', swim: 145, imm: 110, esc: 45, total: 300 },
  { id: 'Espécimen 2', swim: 120, imm: 135, esc: 45, total: 300 },
  { id: 'Espécimen 3', swim: 160, imm: 95, esc: 45, total: 300 },
  { id: 'Espécimen 4', swim: 130, imm: 125, esc: 45, total: 300 },
]
const D1 = [
  { id: 'Espécimen 1', swim: 520, imm: 480, esc: 200, total: 1200 },
  { id: 'Espécimen 2', swim: 480, imm: 530, esc: 190, total: 1200 },
  { id: 'Espécimen 3', swim: 560, imm: 450, esc: 190, total: 1200 },
  { id: 'Espécimen 4', swim: 500, imm: 500, esc: 200, total: 1200 },
]
// [nado, inmovilidad, escape] por minuto, 5 minutos por espécimen
const D2_MIN = [
  [42, 13, 5], [38, 17, 5], [30, 22, 8], [20, 33, 7], [15, 22, 23],
  [35, 18, 7], [30, 22, 8], [25, 27, 8], [16, 34, 10], [14, 34, 12],
  [45, 8, 7], [38, 14, 8], [32, 20, 8], [27, 26, 7], [18, 27, 15],
  [36, 17, 7], [32, 20, 8], [26, 26, 8], [20, 32, 8], [16, 30, 14],
]
const D1_MIN_SAMPLE = [
  [48, 8, 4], [42, 14, 4], [38, 18, 4], [30, 26, 4], [22, 34, 4],
  [48, 8, 4], [40, 16, 4], [35, 21, 4], [28, 28, 4], [20, 36, 4],
  [52, 5, 3], [45, 11, 4], [40, 16, 4], [33, 23, 4], [25, 31, 4],
  [44, 12, 4], [39, 17, 4], [34, 22, 4], [28, 28, 4], [22, 34, 4],
]

const pct = (a, b) => Math.round((a / b) * 100)
const avg = (arr, k) => arr.reduce((s, r) => s + r[k], 0) / arr.length

const TABS = [
  { key: 'resumen', label: 'Resumen' },
  { key: 'detalle', label: 'Tabla detallada' },
  { key: 'minutos', label: 'Por minuto' },
  { key: 'comparacion', label: 'Comparación Día 1 vs 2' },
]

const IconoInfo = () => (
  <svg width="13" height="13" viewBox="0 0 13 13" fill="none" style={{ flexShrink: 0, marginTop: '1px' }}><circle cx="6.5" cy="6.5" r="5.5" stroke="#9ca3af" strokeWidth="1.1"/><path d="M6.5 4.5v3.5" stroke="#9ca3af" strokeWidth="1.2" strokeLinecap="round"/><circle cx="6.5" cy="9.5" r=".6" fill="#9ca3af"/></svg>
)
const IconoAviso = () => (
  <svg width="16" height="16" viewBox="0 0 16 16" fill="none" style={{ flexShrink: 0, marginTop: '1px' }}><path d="M8 1.5L1 6v5l7 3.5 7-3.5V6L8 1.5z" stroke="#d97706" strokeWidth="1.2" strokeLinejoin="round"/></svg>
)

function Leyenda({ corta, style }) {
  const fs = corta ? { fontSize: '11px' } : undefined
  return (
    <div className="legend" style={style}>
      <span className="leg leg-swim" style={fs}><span className="leg-dot" style={{ background: 'var(--b-swim)' }}></span>{corta ? 'Nado' : 'Nado activo'}</span>
      <span className="leg leg-imm" style={fs}><span className="leg-dot" style={{ background: 'var(--b-imm)' }}></span>Inmovilidad</span>
      <span className="leg leg-esc" style={fs}><span className="leg-dot" style={{ background: 'var(--b-esc)' }}></span>Escape</span>
    </div>
  )
}

function CardHeader({ icon, title, sub, children }) {
  return (
    <div className="card-header">
      <div className="ch-left">
        <div className="ch-icon">{icon}</div>
        <div>
          <div className="card-title">{title}</div>
          <div className="card-sub">{sub}</div>
        </div>
      </div>
      {children}
    </div>
  )
}

function Resumen({ hasBoth }) {
  const aSwim = Math.round(avg(D2, 'swim'))
  const aImm = Math.round(avg(D2, 'imm'))
  const aEsc = Math.round(avg(D2, 'esc'))
  return (
    <div>
      {!hasBoth && (
        <div className="one-session-notice">
          <IconoAviso />
          <span>Solo se procesó el video del <strong>Día 2</strong>. Los resultados de comparación (Día 1 vs Día 2) no están disponibles para este experimento. Para habilitarlos, crea un nuevo experimento incluyendo ambos videos.</span>
        </div>
      )}
      <div className="card">
        <CardHeader
          icon={<svg width="15" height="15" viewBox="0 0 15 15" fill="none"><rect x="1.5" y="3" width="12" height="9" rx="1.5" stroke="#4b6490" strokeWidth="1.1"/><path d="M4.5 3V2M10.5 3V2M1.5 6.5h12" stroke="#4b6490" strokeWidth="1.1" strokeLinecap="round"/></svg>}
          title="Resumen por sesión"
          sub="Día 2 — sesión post-tratamiento (5 min / 300 s)"
        >
          <Leyenda />
        </CardHeader>
        <div className="card-body">
          <div className="sum-grid">
            <div className="sum-card sum-swim">
              <div className="sum-label">Nado activo promedio</div>
              <div className="sum-val">{aSwim}s</div>
              <div className="sum-avg">{pct(aSwim, 300)}% del tiempo de sesión (Día 2)</div>
            </div>
            <div className="sum-card sum-imm">
              <div className="sum-label">Inmovilidad promedio</div>
              <div className="sum-val">{aImm}s</div>
              <div className="sum-avg">{pct(aImm, 300)}% del tiempo de sesión (Día 2)</div>
            </div>
            <div className="sum-card sum-esc">
              <div className="sum-label">Escape promedio</div>
              <div className="sum-val">{aEsc}s</div>
              <div className="sum-avg">{pct(aEsc, 300)}% del tiempo de sesión (Día 2)</div>
            </div>
          </div>

          <div className="espécimen-bars">
            {D2.map((r) => (
              <div key={r.id} className="abar-row">
                <div className="abar-label">{r.id}</div>
                <div className="abar-track" title={`Nado:${r.swim}s  Inmovilidad:${r.imm}s  Escape:${r.esc}s`}>
                  <div className="abar-swim" style={{ width: `${pct(r.swim, r.total)}%` }} title={`Nado ${r.swim}s`}></div>
                  <div className="abar-imm" style={{ width: `${pct(r.imm, r.total)}%` }} title={`Inmovilidad ${r.imm}s`}></div>
                  <div className="abar-esc" style={{ width: `${pct(r.esc, r.total)}%` }} title={`Escape ${r.esc}s`}></div>
                </div>
                <div className="abar-total">{r.total}s</div>
              </div>
            ))}
          </div>

          <div className="rn07-note" style={{ marginTop: '14px' }}>
            <IconoInfo />
            Las tres conductas son mutuamente excluyentes por frame — la suma por espécimen es siempre igual a la duración de la sesión.
          </div>
        </div>
      </div>
    </div>
  )
}

function Detalle({ hasBoth }) {
  const sesiones = hasBoth
    ? [{ lbl: 'Día 1 (1200 s)', data: D1 }, { lbl: 'Día 2 (300 s)', data: D2 }]
    : [{ lbl: 'Día 2 (300 s)', data: D2 }]
  return (
    <div className="card">
      <CardHeader
        icon={<svg width="15" height="15" viewBox="0 0 15 15" fill="none"><rect x="1.5" y="1.5" width="12" height="12" rx="1.5" stroke="#4b6490" strokeWidth="1.1"/><path d="M1.5 5.5h12M1.5 9h12M5.5 1.5v12" stroke="#4b6490" strokeWidth="1.1"/></svg>}
        title="Tabla de resultados por espécimen y sesión"
        sub="Tiempo en segundos · conductas mutuamente excluyentes"
      >
        <Leyenda corta style={{ gap: '8px' }} />
      </CardHeader>
      <div className="tbl-wrap">
        <table>
          <thead>
            <tr>
              <th>Espécimen</th>
              <th>Sesión</th>
              <th className="th-swim">Nado activo (s)</th>
              <th className="th-swim">Nado (%)</th>
              <th className="th-imm">Inmovilidad (s)</th>
              <th className="th-imm">Inmovilidad (%)</th>
              <th className="th-esc">Escape (s)</th>
              <th className="th-esc">Escape (%)</th>
              <th>Total (s)</th>
            </tr>
          </thead>
          <tbody>
            {sesiones.map((ses) => {
              const aSwim = Math.round(avg(ses.data, 'swim'))
              const aImm = Math.round(avg(ses.data, 'imm'))
              const aEsc = Math.round(avg(ses.data, 'esc'))
              const aTot = ses.data[0].total
              return (
                <React.Fragment key={ses.lbl}>
                  {ses.data.map((r) => {
                    const sw = pct(r.swim, r.total), im = pct(r.imm, r.total), es = pct(r.esc, r.total)
                    return (
                      <tr key={r.id}>
                        <td className="rat-name">{r.id}</td>
                        <td style={{ fontSize: '12.5px', color: 'var(--c-muted)' }}>{ses.lbl}</td>
                        <td className="val-swim">{r.swim}
                          <span className="mini-bar" style={{ width: `${sw * 0.6}px`, background: 'var(--b-swim-bg)', border: '1px solid #93c5fd' }}></span>
                        </td>
                        <td className="val-pct" style={{ color: 'var(--b-swim)' }}>{sw}%</td>
                        <td className="val-imm">{r.imm}
                          <span className="mini-bar" style={{ width: `${im * 0.6}px`, background: 'var(--b-imm-bg)', border: '1px solid #cbd5e1' }}></span>
                        </td>
                        <td className="val-pct" style={{ color: 'var(--b-imm)' }}>{im}%</td>
                        <td className="val-esc">{r.esc}
                          <span className="mini-bar" style={{ width: `${es * 0.6}px`, background: 'var(--b-esc-bg)', border: '1px solid #fcd34d' }}></span>
                        </td>
                        <td className="val-pct" style={{ color: 'var(--b-esc)' }}>{es}%</td>
                        <td className="val-tot">{r.total}</td>
                      </tr>
                    )
                  })}
                  <tr className="total-row">
                    <td colSpan={2} style={{ fontSize: '12.5px', fontWeight: 700, color: 'var(--c-muted)' }}>Promedio grupo — {ses.lbl}</td>
                    <td className="val-swim" style={{ fontWeight: 800 }}>{aSwim}</td>
                    <td className="val-pct" style={{ color: 'var(--b-swim)' }}>{pct(aSwim, aTot)}%</td>
                    <td className="val-imm" style={{ fontWeight: 800 }}>{aImm}</td>
                    <td className="val-pct" style={{ color: 'var(--b-imm)' }}>{pct(aImm, aTot)}%</td>
                    <td className="val-esc" style={{ fontWeight: 800 }}>{aEsc}</td>
                    <td className="val-pct" style={{ color: 'var(--b-esc)' }}>{pct(aEsc, aTot)}%</td>
                    <td className="val-tot">{aTot}</td>
                  </tr>
                </React.Fragment>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function PorMinuto({ hasBoth, dia, setDia }) {
  const mins = dia === 2 ? 5 : 20
  const datos = dia === 2 ? D2_MIN : D1_MIN_SAMPLE
  return (
    <div className="card">
      <CardHeader
        icon={<svg width="15" height="15" viewBox="0 0 15 15" fill="none"><circle cx="7.5" cy="7.5" r="6" stroke="#4b6490" strokeWidth="1.1"/><path d="M7.5 4.5v3.5l2 2" stroke="#4b6490" strokeWidth="1.1" strokeLinecap="round" strokeLinejoin="round"/></svg>}
        title="Desglose por minuto"
        sub={`Día ${dia} · ${dia === 2 ? 'Sesión post-tratamiento' : 'Sesión basal'} · ${mins} minutos`}
      >
        <div style={{ display: 'flex', gap: '6px' }}>
          {(hasBoth ? [1, 2] : [2]).map((d) => (
            <button key={d} className={`db ${dia === d ? 'active' : ''}`} style={{ padding: '4px 10px', fontSize: '11px' }} onClick={() => setDia(d)}>Día {d}</button>
          ))}
        </div>
      </CardHeader>
      <div className="card-body">
        {D2.map((esp, ri) => {
          const filas = Array.from({ length: mins }, (_, m) => datos[ri * 5 + m] || [60, 0, 0])
          return (
            <div key={esp.id} className="min-espécimen-section">
              <div className="min-espécimen-title">
                <span className="leg leg-swim" style={{ fontSize: '11px' }}>{esp.id}</span>
                <span style={{ fontSize: '11.5px', fontWeight: 400, color: 'var(--c-muted)' }}>· {mins} minutos</span>
              </div>
              <div className="tbl-wrap">
                <table className="min-tbl">
                  <thead>
                    <tr>
                      <th>Minuto</th>
                      <th className="th-swim">Nado (s)</th>
                      <th className="th-imm">Inmovilidad (s)</th>
                      <th className="th-esc">Escape (s)</th>
                      <th>Total</th>
                      <th>Distribución</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filas.map((m, mi) => (
                      <tr key={mi}>
                        <td style={{ fontWeight: 600, color: 'var(--c-muted)' }}>{mi + 1}'</td>
                        <td style={{ color: 'var(--b-swim)', fontWeight: 600 }}>{m[0]}</td>
                        <td style={{ color: 'var(--b-imm)', fontWeight: 600 }}>{m[1]}</td>
                        <td style={{ color: 'var(--b-esc)', fontWeight: 600 }}>{m[2]}</td>
                        <td style={{ color: 'var(--c-muted)', fontSize: '12px' }}>{m[0] + m[1] + m[2]}s</td>
                        <td>
                          <div className="min-bar-cell">
                            <div className="mini-stk">
                              <div style={{ width: `${pct(m[0], 60)}%`, background: 'var(--b-swim)' }}></div>
                              <div style={{ width: `${pct(m[1], 60)}%`, background: 'var(--b-imm)' }}></div>
                              <div style={{ width: `${pct(m[2], 60)}%`, background: 'var(--b-esc)' }}></div>
                            </div>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function DiaComparado({ cls, titulo, data }) {
  return (
    <div className={`comp-day ${cls}`}>
      <div className="comp-day-title">{titulo}</div>
      {data.map((r) => (
        <div key={r.id} className="comp-espécimen">
          <div className="comp-espécimen-name">{r.id}</div>
          <div className="comp-track">
            <div style={{ width: `${pct(r.swim, r.total)}%`, background: 'var(--b-swim)' }}></div>
            <div style={{ width: `${pct(r.imm, r.total)}%`, background: 'var(--b-imm)' }}></div>
            <div style={{ width: `${pct(r.esc, r.total)}%`, background: 'var(--b-esc)' }}></div>
          </div>
          <div className="comp-pct-row">
            <span style={{ color: 'var(--b-swim)' }}>{pct(r.swim, r.total)}% nado</span>
            <span style={{ color: 'var(--b-imm)' }}>{pct(r.imm, r.total)}% inmóvil</span>
            <span style={{ color: 'var(--b-esc)' }}>{pct(r.esc, r.total)}% escape</span>
          </div>
        </div>
      ))}
    </div>
  )
}

function Comparacion({ hasBoth }) {
  if (!hasBoth) {
    return (
      <div className="one-session-notice">
        <IconoAviso />
        <span>La comparación Día 1 vs Día 2 no está disponible porque solo se procesó el video del Día 2. Para habilitar esta vista, sube ambos videos al crear el experimento.</span>
      </div>
    )
  }
  const filas = [
    { lab: 'Nado activo', d1: Math.round(avg(D1, 'swim')), d2: Math.round(avg(D2, 'swim')), pos: true, note: '↑ sugiere efecto antidepresivo' },
    { lab: 'Inmovilidad', d1: Math.round(avg(D1, 'imm')), d2: Math.round(avg(D2, 'imm')), pos: false, note: '↓ sugiere efecto antidepresivo' },
    { lab: 'Escape', d1: Math.round(avg(D1, 'esc')), d2: Math.round(avg(D2, 'esc')), pos: null, note: 'indicador secundario' },
  ]
  const deltaPct = (a, b) => { const v = Math.round(((b - a) / a) * 100); return (v >= 0 ? '+' : '') + v + '%' }
  const deltaS = (a, b) => { const v = b - a; return (v >= 0 ? '+' : '') + v + 's' }

  return (
    <div>
      <div className="card">
        <CardHeader
          icon={<svg width="15" height="15" viewBox="0 0 15 15" fill="none"><path d="M2 11V5l5-3.5L12 5v6l-5 3L2 11z" stroke="#4b6490" strokeWidth="1.1" strokeLinejoin="round"/></svg>}
          title="Comparación Día 1 vs Día 2 por espécimen"
          sub="Distribución de conductas en cada sesión (barras de 100%)"
        >
          <Leyenda />
        </CardHeader>
        <div className="card-body">
          <div className="comp-grid">
            <DiaComparado cls="comp-day-d1" titulo="Día 1 — Sesión basal (1200 s)" data={D1} />
            <DiaComparado cls="comp-day-d2" titulo="Día 2 — Post-tratamiento (300 s)" data={D2} />
          </div>
          <div className="rn03-note">
            <IconoInfo />
            <span>La métrica principal del FST es el Día 2 (post-tratamiento). El Día 1 es la sesión basal de habituación. Esta comparación es informativa — no implica que el Día 1 sea el control experimental.</span>
          </div>
        </div>
      </div>

      <div className="card">
        <CardHeader
          icon={<svg width="15" height="15" viewBox="0 0 15 15" fill="none"><path d="M2 11V4h11v7" stroke="#4b6490" strokeWidth="1.1" strokeLinecap="round"/><path d="M5 11V7.5M8 11V6M11 11V8.5" stroke="#4b6490" strokeWidth="1.1" strokeLinecap="round"/></svg>}
          title="Variación por conducta entre sesiones — promedio del grupo"
          sub="Diferencia absoluta en segundos y dirección del cambio"
        />
        <div className="card-body">
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '16px' }}>
            {filas.map((row) => {
              const diff = row.d2 - row.d1
              const chip = row.pos === true && diff > 0 ? 'd-up' : row.pos === false && diff < 0 ? 'd-down' : 'd-neu'
              return (
                <div key={row.lab} style={{ border: '1px solid var(--c-border)', borderRadius: 'var(--radius)', padding: '14px 16px' }}>
                  <div style={{ fontSize: '12px', fontWeight: 700, color: 'var(--c-muted)', marginBottom: '8px', textTransform: 'uppercase', letterSpacing: '.05em' }}>{row.lab}</div>
                  <div style={{ display: 'flex', alignItems: 'baseline', gap: '12px', marginBottom: '6px' }}>
                    <div><div style={{ fontSize: '10px', color: 'var(--c-muted)' }}>Día 1</div><div style={{ fontSize: '22px', fontWeight: 800, color: 'var(--c-text)' }}>{row.d1}s</div></div>
                    <div style={{ color: 'var(--c-muted)', fontSize: '18px' }}>→</div>
                    <div><div style={{ fontSize: '10px', color: 'var(--c-muted)' }}>Día 2</div><div style={{ fontSize: '22px', fontWeight: 800, color: 'var(--c-text)' }}>{row.d2}s</div></div>
                  </div>
                  <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', alignItems: 'center' }}>
                    <span className={`delta-chip ${chip}`}>{deltaS(row.d1, row.d2)} / {deltaPct(row.d1, row.d2)}</span>
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--c-muted)', marginTop: '6px' }}>{row.note}</div>
                </div>
              )
            })}
          </div>
          <div className="rn03-note" style={{ marginTop: '14px' }}>
            <IconoInfo />
            Los deltas se calculan sobre el tiempo absoluto de cada sesión, que es diferente (Día 1: 1200 s, Día 2: 300 s). La comparación es orientativa.
          </div>
        </div>
      </div>
    </div>
  )
}

export default function ResultsPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [demo, setDemo] = useState('d2only')
  const [tab, setTab] = useState('resumen')
  const [diaMinuto, setDiaMinuto] = useState(2)
  const hasBoth = demo !== 'd2only'

  const cambiarDemo = (k) => {
    setDemo(k)
    setDiaMinuto(2)
    if (k === 'minexpand') setTab('minutos')
    else if (k === 'd2only' && tab === 'comparacion') setTab('resumen')
  }

  return (
    <div className="pg-resultados">
      <DemoBar states={DEMO_STATES} value={demo} onChange={cambiarDemo} />

      <main className="page">
        <nav className="breadcrumb">
          <Link to="/dashboard">Mis experimentos</Link><span className="bsep">›</span>
          <a href="#" onClick={(e) => e.preventDefault()}>FST-2024-036 — Ketamina 30 mg/kg</a><span className="bsep">›</span>
          <span>Resultados</span>
        </nav>

        <div className="page-header">
          <div>
            <div className="page-title">Resultados — FST-2024-036</div>
            <div className="page-sub">Ketamina 30 mg/kg · 4 especímenes · Procesado el 22 mar 2025</div>
          </div>
          <div className="export-btns">
            <button className="btn-export" onClick={() => navigate(`/experiments/${id}/review`)}>
              Revisar segundo a segundo
            </button>
            <button className="btn-export pdf">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M3 2h5.5L11 4.5V12H3V2z" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round"/><path d="M8 2v3h3" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round"/><path d="M5 7.5h4M5 9.5h2.5" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round"/></svg>
              Descargar PDF
            </button>
            <button className="btn-export csv">
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><rect x="1.5" y="1.5" width="11" height="11" rx="1.5" stroke="currentColor" strokeWidth="1.1"/><path d="M1.5 5h11M1.5 8.5h11M5 5v7" stroke="currentColor" strokeWidth="1.1"/></svg>
              Descargar CSV
            </button>
          </div>
        </div>

        <div className="section-tabs">
          {TABS.map((t) => (
            <div key={t.key} className={`stab ${tab === t.key ? 'active' : ''}`} onClick={() => setTab(t.key)}>{t.label}</div>
          ))}
        </div>

        {tab === 'resumen' && <Resumen hasBoth={hasBoth} />}
        {tab === 'detalle' && <Detalle hasBoth={hasBoth} />}
        {tab === 'minutos' && <PorMinuto hasBoth={hasBoth} dia={diaMinuto} setDia={setDiaMinuto} />}
        {tab === 'comparacion' && <Comparacion hasBoth={hasBoth} />}
      </main>
    </div>
  )
}
