import React, { useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Topbar, { Brand } from '../components/Topbar'
import { EXPERIMENTO as exp, BEHAVIORS, RESULTADOS as raw, PATRON_RATA1 as patron } from '../data/mock'
import { mean, variance, fmt, r1, download, toCSV, loadScript } from '../lib/fst'
import { NoEncontrado } from './ExperimentoPage'

const INK = 'var(--color-text)'
const ACC = 'var(--color-accent)'
const N400 = 'var(--color-neutral-400)'
const MUT = 'var(--muted)'
const PEND = 'var(--color-accent-700)'
const MUT60 = 'color-mix(in srgb,var(--color-text) 60%,transparent)'
const MUT70 = 'color-mix(in srgb,var(--color-text) 70%,transparent)'
const XLSX_SRC = 'https://cdnjs.cloudflare.com/ajax/libs/xlsx/0.18.5/xlsx.full.min.js'

const KEYS = ['nado', 'inmov', 'escal']
const CMAP = { nado: INK, inmov: ACC, escal: N400 }
const LBL = { nado: 'Nado activo', inmov: 'Inmovilidad', escal: 'Escalamiento' }
const col = (k) => raw.map((r) => r[k])

const STATS = [
  { nombre: 'Media', f: (a) => r1(mean(a)) + ' s', nota: 'sobre 300 s de evaluación' },
  { nombre: 'Desviación estándar', f: (a) => r1(Math.sqrt(variance(a))), nota: 'muestral, n − 1' },
  { nombre: 'Varianza', f: (a) => r1(variance(a)), nota: '' },
]

// ── comparación entre grupos ───────────────────────────────────────────────
const pct = (v) => (v / 300) * 100 + '%'
const val = (m, de) => r1(m) + ' ± ' + r1(de) + ' s'

function Barra({ m, de, color, h }) {
  return (
    <div style={{ height: h, background: 'color-mix(in srgb,var(--color-text) 10%,transparent)', position: 'relative' }}>
      <div style={{ height: h, background: color, width: pct(m) }} />
      <div className="bar-de" style={{ height: h + 6, left: pct(Math.max(0, m - de)) }} />
      <div className="bar-de" style={{ height: h + 6, left: pct(Math.min(300, m + de)) }} />
    </div>
  )
}

const inmM = mean(col('inmov'))
const inmDE = Math.sqrt(variance(col('inmov')))

const COMPARACION = [
  { nombre: 'Control · placebo', valor: val(168.4, 21.3), valorFg: INK, body: <Barra m={168.4} de={21.3} color={INK} h={10} />, nota: 'n = 4 de 8 · 4 especímenes pendientes (Tanda B en cola)', notaFg: PEND },
  {
    nombre: 'Referencia · fluoxetina', valor: 'por nivel', valorFg: MUT, nota: 'n = 8 de 8', notaFg: MUT,
    body: (
      <>
        <div className="note-bar" style={{ fontSize: 11.5, lineHeight: 1.5, marginBottom: 8 }}>Este grupo mezcla niveles de clasificación; se muestran por separado.</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
          {[{ n: 'preciso · Tanda A · n = 4', m: inmM, de: inmDE, c: ACC }, { n: 'agrupado · Tanda B · n = 4', m: 101.3, de: 15.2, c: 'var(--color-accent-300)' }].map((u) => (
            <div key={u.n}>
              <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 10, marginBottom: 4 }}>
                <span style={{ fontSize: 11.5, color: MUT70 }}>{u.n}</span>
                <span className="num nd" style={{ fontSize: 11, whiteSpace: 'nowrap' }}>{val(u.m, u.de)}</span>
              </div>
              <Barra m={u.m} de={u.de} color={u.c} h={8} />
            </div>
          ))}
        </div>
      </>
    ),
  },
  { nombre: 'Experimental A · CSR-14, 5 mg/kg', valor: val(121.2, 19.4), valorFg: INK, body: <Barra m={121.2} de={19.4} color={N400} h={10} />, nota: 'n = 4 de 8 · 4 especímenes pendientes (Tanda B procesando)', notaFg: PEND },
  {
    nombre: 'Experimental B · CSR-14, 15 mg/kg', valor: 'sin datos', valorFg: PEND, notaFg: PEND,
    body: <div style={{ height: 10, background: 'repeating-linear-gradient(135deg,color-mix(in srgb,var(--color-text) 18%,transparent) 0 3px,transparent 3px 6px)', border: '1px solid var(--color-divider)' }} />,
    nota: '8 de 8 especímenes pendientes (Tanda A en cola, Tanda B con error)',
  },
]

// ── exportación (segundos, no porcentaje) ──────────────────────────────────
function tabla() {
  const out = [['Cilindro', 'Nado activo (s)', 'Inmovilidad (s)', 'Escalamiento (s)']]
  raw.forEach((r) => out.push([r.label, r.nado, r.inmov, r.escal]))
  STATS.forEach((s) => out.push([s.nombre, ...KEYS.map((k) => s.f(col(k)).replace(' s', ''))]))
  out.push([])
  out.push(['Cilindro P1 · minuto', 'Nado activo (s)', 'Inmovilidad (s)', 'Escalamiento (s)'])
  patron.forEach((segs, i) => {
    const sum = (k) => segs.filter((s) => s[0] === k).reduce((a, s) => a + s[1], 0)
    out.push([fmt(i * 60) + '–' + fmt(i * 60 + 60), sum('nado'), sum('inmov'), sum('escal')])
  })
  return out
}
const BASE = 'resultados_CSR-14_referencia_tandaA_dia2'

async function exportarXLSX() {
  try {
    await loadScript(XLSX_SRC)
  } catch {
    alert('No se pudo cargar el generador de XLSX (requiere conexión).')
    return
  }
  const X = window.XLSX
  const wb = X.utils.book_new()
  X.utils.book_append_sheet(wb, X.utils.aoa_to_sheet(tabla()), 'Resultados')
  X.writeFile(wb, BASE + '.xlsx')
}

const TABS = [
  { id: 'especimen', label: 'Por cilindro' },
  { id: 'comparacion', label: 'Comparación entre grupos' },
  { id: 'timeline', label: 'Línea de tiempo por minuto' },
]

// 2f · Resultados: conductas y estadísticos de Grupo referencia, Tanda A, Día 2.
export default function ResultadosPage() {
  const { clave } = useParams()
  const [tab, setTab] = useState('especimen')
  const refs = { especimen: useRef(null), comparacion: useRef(null), timeline: useRef(null) }

  if (clave !== exp.clave) return <NoEncontrado />

  // Las pestañas llevan a su sección de la vista.
  function irA(id) {
    setTab(id)
    refs[id].current.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return (
    <div className="app">
      <Topbar>
        <Brand inline />
        <span className="crumbs">
          <Link to="/experimentos">Experimentos</Link> / <Link to={`/experimentos/${exp.clave}`}>{exp.titulo}</Link> / <Link to={`/experimentos/${exp.clave}/grupos/G-02`}>Referencia</Link> / <span className="here">Tanda A · Día 2</span>
        </span>
      </Topbar>

      <div className="page">
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 24 }}>
          <div style={{ flex: 1 }}>
            <h2 style={{ margin: '0 0 9px' }}>Resultados · Grupo referencia, Tanda A</h2>
            <div className="num" style={{ fontSize: 12.5, color: 'var(--muted-2)' }}>Fluoxetina 10 mg/kg · Día 2, 300 s de evaluación · analizado 24 feb 2026, 15:02 · modelo clf-cascada v2.1</div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 12 }}>
              <span className="tag tag-accent" style={{ whiteSpace: 'nowrap' }}>Nivel: preciso (3 conductas)</span>
              <span className="hint">El otro nivel posible es <strong>agrupado</strong>: nado activo y escalamiento se juntan en «conducta activa».</span>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 8 }} className="no-print">
            <button type="button" className="btn btn-secondary" onClick={() => download(BASE + '.csv', toCSV(tabla()))}>CSV</button>
            <button type="button" className="btn btn-secondary" onClick={exportarXLSX}>XLSX</button>
            <button type="button" className="btn btn-primary" style={{ justifyContent: 'flex-start' }} onClick={() => window.print()}>PDF · resumen ejecutivo</button>
          </div>
        </div>

        <div className="no-print" style={{ display: 'flex', borderBottom: '2px solid var(--color-divider)', margin: '22px 0 24px' }}>
          {TABS.map((t) => (
            <button key={t.id} type="button" className={'tab' + (tab === t.id ? ' on' : '')} onClick={() => irA(t.id)}>{t.label}</button>
          ))}
          <button type="button" className="tab" disabled>Episodios</button>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 330px', gap: 2, background: 'var(--color-divider)' }}>
          <div style={{ background: 'var(--color-bg)', paddingRight: 26 }}>
            <div ref={refs.especimen} style={{ display: 'flex', alignItems: 'center', gap: 18, marginBottom: 14 }}>
              <div className="k">Tiempo por conducta · min:seg</div>
              <div style={{ flex: 1 }} />
              <span style={{ display: 'flex', gap: 18 }}>
                {BEHAVIORS.map((b) => (
                  <span key={b.id} style={{ display: 'inline-flex', alignItems: 'center', gap: 7, fontSize: 11.5 }}><span className="sw" style={{ background: b.color }} />{b.label}</span>
                ))}
              </span>
            </div>

            <table className="table">
              <thead>
                <tr>
                  <th style={{ width: '24%' }}>Cilindro</th><th style={{ textAlign: 'right' }}>Nado activo</th><th style={{ textAlign: 'right' }}>Inmovilidad</th>
                  <th style={{ textAlign: 'right' }}>Escalamiento</th><th style={{ width: '30%' }}>Distribución</th>
                </tr>
              </thead>
              <tbody>
                {raw.map((r) => (
                  <tr key={r.pos}>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        <span className="num nd" style={{ width: 24, height: 24, flex: 'none', border: '2px solid var(--color-accent)', color: 'var(--color-accent-700)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10 }}>{r.pos}</span>
                        <span style={{ fontSize: 13 }}>{r.label}</span>
                      </div>
                    </td>
                    {KEYS.map((k) => <td key={k} className="num" style={{ textAlign: 'right' }}>{fmt(r[k])}</td>)}
                    <td>
                      <div style={{ display: 'flex', height: 16, border: '1px solid var(--color-divider)' }}>
                        {KEYS.map((k) => <div key={k} title={LBL[k]} style={{ background: CMAP[k], width: r[k] / 3 + '%' }} />)}
                      </div>
                    </td>
                  </tr>
                ))}
                {STATS.map((s) => (
                  <tr key={s.nombre} style={{ background: 'var(--color-surface)' }}>
                    <td className="k" style={{ color: MUT70 }}>{s.nombre}</td>
                    {KEYS.map((k) => <td key={k} className="num nd" style={{ textAlign: 'right' }}>{s.f(col(k))}</td>)}
                    <td style={{ fontSize: 11.5, color: MUT }}>{s.nota}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            <p className="hint" style={{ margin: '14px 0 0' }}>
              Un episodio requiere 3 segundos consecutivos de la misma conducta; el buceo se contabiliza como nado activo. Los tres renglones grises son los estadísticos de grupo. El desglose por minuto se almacena y exporta <strong>en segundos</strong>, no en porcentaje.
            </p>

            <div ref={refs.timeline} className="k" style={{ margin: '26px 0 12px' }}>Cilindro P1</div>
            <div style={{ border: '1px solid var(--color-divider)', padding: '16px 18px' }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {patron.map((segs, i) => (
                  <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <span className="num" style={{ width: 74, fontSize: 11.5, color: MUT60 }}>{fmt(i * 60)}–{fmt(i * 60 + 60)}</span>
                    <div style={{ flex: 1, display: 'flex', height: 18, border: '1px solid var(--color-divider)' }}>
                      {segs.map((s, j) => <div key={j} title={LBL[s[0]] + ' · ' + s[1] + ' s'} style={{ background: CMAP[s[0]], width: (s[1] / 60) * 100 + '%' }} />)}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div style={{ background: 'var(--color-bg)', paddingLeft: 26, display: 'flex', flexDirection: 'column', gap: 24 }}>
            <div>
              <div className="k" style={{ marginBottom: 12 }}>Calidad del análisis</div>
              <div className="num nd" style={{ fontSize: 20, lineHeight: 1.2, marginBottom: 9 }}>Confianza de detección: 0.83</div>
              <div style={{ height: 8, background: 'color-mix(in srgb,var(--color-text) 12%,transparent)', marginBottom: 9 }}><div style={{ height: 8, background: INK, width: '83%' }} /></div>
              <p style={{ margin: 0, fontSize: 11.5, lineHeight: 1.6, color: MUT70 }}>Es la confianza de la detección de cilindros, por arriba del mínimo de 0.70.</p>
              <div style={{ marginTop: 13, borderTop: '1px solid var(--color-divider)' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, padding: '8px 0', borderBottom: '1px solid var(--color-divider)', fontSize: 11.5 }}>
                  <span style={{ color: MUT70 }}>Duración analizada</span>
                  <span className="num">5:00 / 5:00</span>
                </div>
              </div>
            </div>

            <div ref={refs.comparacion}>
              <div className="k" style={{ marginBottom: 12 }}>Inmovilidad por grupo · Día 2 (5 min)</div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 15 }}>
                {COMPARACION.map((c) => (
                  <div key={c.nombre}>
                    <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 10, marginBottom: 5 }}>
                      <span style={{ fontSize: 12 }}>{c.nombre}</span>
                      <span className="num nd" style={{ fontSize: 11.5, whiteSpace: 'nowrap', color: c.valorFg }}>{c.valor}</span>
                    </div>
                    {c.body}
                    <div className="num" style={{ fontSize: 11, marginTop: 5, color: c.notaFg }}>{c.nota}</div>
                  </div>
                ))}
              </div>
              <p className="hint" style={{ margin: '12px 0 0' }}>
                Promedio por espécimen de cada grupo (media ± DE), en segundos sobre los 300 s del Día 2. Las marcas verticales delimitan ± una DE. Los especímenes sin Día 2 analizado (en cola, procesando, con error o sin subir) se señalan y no entran al promedio.
              </p>
            </div>

            <div>
              <div className="k" style={{ marginBottom: 12 }}>Leyenda de conductas</div>
              <div style={{ borderTop: '1px solid var(--color-divider)' }}>
                {BEHAVIORS.map((b) => (
                  <div key={b.id} style={{ display: 'flex', alignItems: 'flex-start', gap: 10, padding: '9px 0', borderBottom: '1px solid var(--color-divider)' }}>
                    <span className="sw" style={{ marginTop: 4, background: b.color }} />
                    <div>
                      <div className="nd" style={{ fontSize: 12.5 }}>{b.label}</div>
                      <div style={{ fontSize: 11.5, lineHeight: 1.5, color: MUT60 }}>{b.nota}</div>
                    </div>
                  </div>
                ))}
              </div>
              <p className="hint" style={{ margin: '11px 0 0' }}>El buceo cuenta como nado activo.</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
