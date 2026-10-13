import React, { useRef, useState } from 'react'
import { Link, Navigate, useParams, useSearchParams } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import Topbar, { Brand } from '../components/Topbar'
import { getBatchResults, getGroupComparison, getBlocks } from '../services/results'
import { getExperiment } from '../services/experiments'
import { useAsync } from '../hooks/useAsync'
import { mean, variance, fmt, r1, download, toCSV, fechaCorta, norm, primeraTandaLista, enlaceResultados, enlaceRevision, especimen } from '../lib/fst'
import { Migas, Cargando, NoEncontrado } from '../components/ui'
import { MUT60, MUT70, btnLeft } from '../lib/estilos'

const INK = 'var(--color-text)'
const ACC = 'var(--color-accent)'
const N400 = 'var(--color-neutral-400)'
const MUT = 'var(--muted)'
const PEND = 'var(--color-accent-700)'

const ACTIVA = 'var(--color-accent-300)'
// Las cuatro conductas (R-12). En cada segundo el espécimen está en exactamente una; un mismo
// análisis puede mezclar las cuatro.
const CONDUCTAS = [
  { id: 'nado', label: 'Nado activo', color: INK, nota: 'Desplazamiento horizontal sostenido; el buceo cuenta aquí.' },
  { id: 'inmovilidad', label: 'Inmovilidad', color: ACC, nota: 'Solo los movimientos mínimos para mantenerse a flote.' },
  { id: 'escalamiento', label: 'Escalamiento', color: N400, nota: 'Patas delanteras rompiendo la superficie contra la pared del cilindro.' },
  { id: 'activa', label: 'Conducta activa', color: ACTIVA, nota: 'El espécimen no estaba inmóvil, pero el sistema no decidió entre nado activo y escalamiento.' },
]
const KEYS = CONDUCTAS.map((c) => c.id)
const CMAP = Object.fromEntries(CONDUCTAS.map((c) => [c.id, c.color]))
const LBL = Object.fromEntries(CONDUCTAS.map((c) => [c.id, c.label]))
// Orden y nombres de las columnas de los reportes CSV y XLSX (§7 del documento).
const EXPORTA = [['nado', 'Nado'], ['escalamiento', 'Escalamiento'], ['inmovilidad', 'Inmovilidad'], ['activa', 'Conducta activa']]

// El espécimen siempre con su tanda, porque su número se repite en cada tanda (D-55).
const etiqueta = (e, letra) => especimen(e.numeroCilindro, letra)
const nombreGrupo = (g) => (g.tipo === 'CONTROL' ? 'Grupo control' : 'Grupo ' + g.nombre[0].toLowerCase() + g.nombre.slice(1))
const hora = (iso) => iso.slice(11, 16)

function estadisticos(duracion) {
  return [
    { nombre: 'Media', f: (a) => r1(mean(a)) + ' s', nota: 'sobre ' + duracion + ' s de evaluación' },
    { nombre: 'Desviación estándar', f: (a) => r1(Math.sqrt(variance(a))), nota: 'muestral, n − 1' },
    { nombre: 'Varianza', f: (a) => r1(variance(a)), nota: 'RF-31' },
  ]
}

// ── comparación entre grupos ───────────────────────────────────────────────
const DIA2_S = 300
const pct = (v) => (v / DIA2_S) * 100 + '%'
const val = (m, de) => r1(m) + ' ± ' + r1(de) + ' s'
const COLOR_TIPO = { CONTROL: INK, REFERENCIA: ACC, EXPERIMENTAL: N400 }
const COLOR_AGRUPADO = 'var(--color-accent-300)'
// Bloque que se decidió sin ver bien al espécimen (matiz de D-53).
const MATIZ = 'repeating-linear-gradient(135deg, var(--color-accent-300) 0 3px, var(--color-bg) 3px 6px)'
const PENDIENTE = { QUEUED: 'en cola', RUNNING: 'procesando', PAUSED: 'en pausa', FAILED: 'con error', CANCELED: 'cancelada' }

function Barra({ m, de, color, h }) {
  return (
    <div style={{ height: h, background: 'color-mix(in srgb,var(--color-text) 10%,transparent)', position: 'relative' }}>
      <div style={{ height: h, background: color, width: pct(m) }} />
      <div className="bar-de" style={{ height: h + 6, left: pct(Math.max(0, m - de)) }} />
      <div className="bar-de" style={{ height: h + 6, left: pct(Math.min(DIA2_S, m + de)) }} />
    </div>
  )
}

// «4 especímenes pendientes (Tanda B en cola)»; sin datos: «8 de 8 especímenes pendientes (…)».
function notaComparacion(g) {
  const n = 'n = ' + g.n + ' de ' + g.n_total
  if (!g.pendientes.length) return n
  const faltan = g.pendientes.reduce((a, p) => a + p.n, 0)
  const causas = g.pendientes.map((p) => 'Tanda ' + p.tanda + ' ' + (PENDIENTE[p.estado] || 'sin subir')).join(', ')
  const texto = (g.n === 0 ? faltan + ' de ' + g.n_total : faltan) + ' especímenes pendientes (' + causas + ')'
  return g.n === 0 ? texto : n + ' · ' + texto
}

function Comparacion({ g, c }) {
  const notaFg = g.pendientes.length ? PEND : MUT
  let valor, valorFg, body
  if (g.por_nivel) {
    valor = 'por nivel'
    valorFg = MUT
    body = (
      <>
        <div className="note-bar" style={{ fontSize: 11.5, lineHeight: 1.5, marginBottom: 8 }}>Este grupo mezcla niveles de clasificación; se muestran por separado.</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
          {g.por_nivel.map((u) => (
            <div key={u.tanda}>
              <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 10, marginBottom: 4 }}>
                <span style={{ fontSize: 11.5, color: MUT70 }}>{u.nivel.toLowerCase()} · Tanda {u.tanda} · n = {u.n}</span>
                <span className="num nd" style={{ fontSize: 11, whiteSpace: 'nowrap' }}>{val(u.por_conducta[c].media_s, u.por_conducta[c].de_s)}</span>
              </div>
              <Barra m={u.por_conducta[c].media_s} de={u.por_conducta[c].de_s} color={u.nivel === 'AGRUPADO' ? COLOR_AGRUPADO : COLOR_TIPO[g.tipo]} h={8} />
            </div>
          ))}
        </div>
      </>
    )
  } else if (g.por_conducta[c].media_s == null) {
    valor = 'sin datos'
    valorFg = PEND
    body = <div style={{ height: 10, background: 'repeating-linear-gradient(135deg,color-mix(in srgb,var(--color-text) 18%,transparent) 0 3px,transparent 3px 6px)', border: '1px solid var(--color-divider)' }} />
  } else {
    const { media_s, de_s } = g.por_conducta[c]
    valor = val(media_s, de_s)
    valorFg = INK
    body = <Barra m={media_s} de={de_s} color={COLOR_TIPO[g.tipo]} h={10} />
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 10, marginBottom: 5 }}>
        <span style={{ fontSize: 12 }}>{g.grupo}</span>
        <span className="num nd" style={{ fontSize: 11.5, whiteSpace: 'nowrap', color: valorFg }}>{valor}</span>
      </div>
      {body}
      <div className="num" style={{ fontSize: 11, marginTop: 5, color: notaFg }}>{notaComparacion(g)}</div>
    </div>
  )
}

// ── exportación (segundos, no porcentaje) ──────────────────────────────────
function tabla(r) {
  const claves = EXPORTA.map(([k]) => k)
  const encabezados = EXPORTA.map(([, nombre]) => nombre)
  const col = (k) => r.especimenes.map((e) => e[k + '_s'])
  const out = [['Espécimen', ...encabezados.map((n) => n + ' (s)')]]
  r.especimenes.forEach((e) => out.push([etiqueta(e, r.letra), ...claves.map((k) => e[k + '_s'])]))
  estadisticos(r.duracion_s).forEach((s) => out.push([s.nombre, ...claves.map((k) => s.f(col(k)).replace(' s', ''))]))
  out.push([])
  // Desglose por minuto: una fila por minuto, en segundos.
  out.push([etiqueta(r.linea_tiempo, r.letra)])
  out.push(['Tiempo (min:seg)', ...encabezados])
  r.linea_tiempo.minutos.forEach((segs, i) => {
    const sum = (k) => segs.filter((s) => s[0] === k).reduce((a, s) => a + s[1], 0)
    out.push([fmt(i * 60) + '–' + fmt(i * 60 + 60), ...claves.map(sum)])
  })
  return out
}

const archivo = (r) => ['resultados', r.experimento.clave, norm(r.grupo.nombre).replace(/\s+/g, '-'), 'tanda' + r.letra, 'dia2'].join('_')

// El generador de XLSX (SheetJS) viene con la app, pero se carga solo al exportar
// para no agrandar la carga inicial. No depende de internet.
async function exportarXLSX(r) {
  let X
  try {
    X = await import('xlsx')
  } catch {
    alert('No se pudo cargar el generador de XLSX. Recarga la página e inténtalo de nuevo.')
    return
  }
  const wb = X.utils.book_new()
  X.utils.book_append_sheet(wb, X.utils.aoa_to_sheet(tabla(r)), 'Resultados')
  X.writeFile(wb, archivo(r) + '.xlsx')
}

const TABS = [
  { id: 'especimen', label: 'Por espécimen' },
  { id: 'comparacion', label: 'Comparación entre grupos' },
  { id: 'timeline', label: 'Línea de tiempo por minuto' },
  { id: 'bloques', label: 'Bloques de 5 s' },
]

// 2f · Resultados de una tanda, Día 2: /experimentos/:clave/resultados?grupo=&tanda=.
// Sin grupo y tanda abre la primera tanda del experimento con el Día 2 analizado.
export default function ResultadosPage() {
  const { clave } = useParams()
  const [params] = useSearchParams()
  const gid = params.get('grupo')
  const letra = params.get('tanda')
  if (gid && letra) return <ResultadosTanda key={gid + letra} clave={clave} gid={gid} letra={letra} />
  return <PrimeraTanda clave={clave} />
}

function PrimeraTanda({ clave }) {
  const { data: exp, error, loading } = useAsync(() => getExperiment(clave), [clave])
  if (loading) return <Cargando />
  if (error) return <NoEncontrado />
  const t = primeraTandaLista(exp)
  if (!t) return <SinResultados clave={clave} />
  return <Navigate replace to={enlaceResultados(clave, t.gid, t.letra)} />
}

function SinResultados({ clave }) {
  return (
    <div className="app">
      <Topbar><Brand /></Topbar>
      <div className="page">
        <h3 style={{ margin: '0 0 8px' }}>Todavía no hay resultados</h3>
        <p className="lead" style={{ marginBottom: 16, maxWidth: 560 }}>
          Los resultados de una tanda aparecen cuando termina el análisis de su video de Día 2.
        </p>
        <div style={{ display: 'flex', gap: 8 }}>
          <Link className="btn btn-primary" to={`/experimentos/${clave}`}>Volver al experimento</Link>
          <Link className="btn btn-secondary" to="/analisis">Ver progreso del análisis</Link>
        </div>
      </div>
    </div>
  )
}

function ResultadosTanda({ clave, gid, letra }) {
  const { user } = useAuth()
  const [tab, setTab] = useState('especimen')
  const refs = { especimen: useRef(null), comparacion: useRef(null), timeline: useRef(null), bloques: useRef(null) }
  const [conducta, setConducta] = useState('inmovilidad') // la que se compara entre grupos
  const { data: r, error, loading } = useAsync(() => getBatchResults(clave, gid, letra), [clave, gid, letra])
  const { data: comparacion } = useAsync(() => getGroupComparison(clave), [clave])

  if (loading) return <Cargando />
  if (error) return <SinResultados clave={clave} />

  const exp = r.experimento
  const conductas = CONDUCTAS
  const col = (k) => r.especimenes.map((e) => e[k + '_s'])
  const stats = estadisticos(r.duracion_s)

  // Las pestañas llevan a su sección de la vista.
  function irA(id) {
    setTab(id)
    refs[id].current.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return (
    <div className="app">
      <Topbar>
        <Brand inline />
        <Migas items={[
          { to: '/experimentos', label: 'Experimentos' },
          { to: `/experimentos/${exp.clave}`, label: exp.titulo },
          { to: `/experimentos/${exp.clave}/grupos/${r.grupo.id}`, label: r.grupo.nombre },
          { label: `Tanda ${r.letra} · Día 2` },
        ]} />
      </Topbar>

      <div className="page">
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 24 }}>
          <div style={{ flex: 1 }}>
            <h2 style={{ margin: '0 0 9px' }}>Resultados · {nombreGrupo(r.grupo)}, Tanda {r.letra}</h2>
            <div className="num" style={{ fontSize: 12.5, color: 'var(--muted-2)' }}>{r.grupo.tratamiento} · Día 2, {r.duracion_s} s de evaluación · analizado {fechaCorta(r.analizado_en.slice(0, 10))}, {hora(r.analizado_en)} · modelo {r.modelo}</div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 12 }}>
              <span className="tag tag-accent" style={{ whiteSpace: 'nowrap' }}>Nivel: {r.nivel === 'AGRUPADO' ? 'agrupado' : 'preciso'}</span>
              {r.revisado && <span className="tag tag-outline" style={{ whiteSpace: 'nowrap' }}>Con revisión humana</span>}
              {r.nivel === 'AGRUPADO'
                ? <span className="hint">Hay segundos de <strong>conducta activa</strong>: el espécimen no estaba inmóvil, pero el sistema no decidió entre nado activo y escalamiento.</span>
                : <span className="hint">Ningún segundo quedó como conducta activa: cada uno se clasificó como nado activo, inmovilidad o escalamiento.</span>}
            </div>
          </div>
          <div style={{ display: 'flex', gap: 8 }} className="no-print">
            {/* Corregir los segundos: solo quien subió el video o un Administrador (D-57). */}
            {r.id_analisis && (user.admin || r.subido_por === user.id) && (
              <Link className="btn btn-secondary" to={enlaceRevision(clave, r.id_analisis)}>Revisar segundo a segundo</Link>
            )}
            <button type="button" className="btn btn-secondary" onClick={() => download(archivo(r) + '.csv', toCSV(tabla(r)))}>CSV</button>
            <button type="button" className="btn btn-secondary" onClick={() => exportarXLSX(r)}>XLSX</button>
            <button type="button" className="btn btn-primary" style={btnLeft} onClick={() => window.print()}>PDF · reporte de resultados</button>
          </div>
        </div>

        {/* Saltos a secciones de esta misma vista (no cambian el contenido): navegación, no pestañas. */}
        <nav aria-label="Secciones de los resultados" className="no-print" style={{ display: 'flex', borderBottom: '2px solid var(--color-divider)', margin: '22px 0 24px' }}>
          {TABS.map((t) => (
            <button key={t.id} type="button" className={'tab' + (tab === t.id ? ' on' : '')} aria-current={tab === t.id ? 'true' : undefined} onClick={() => irA(t.id)}>{t.label}</button>
          ))}
        </nav>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 330px', gap: 2, background: 'var(--color-divider)' }}>
          <div style={{ background: 'var(--color-bg)', paddingRight: 26 }}>
            <div ref={refs.especimen} style={{ display: 'flex', alignItems: 'center', gap: 18, marginBottom: 14 }}>
              <div className="k">Tiempo por conducta · min:seg</div>
              <div style={{ flex: 1 }} />
              <span style={{ display: 'flex', gap: 18 }}>
                {conductas.map((b) => (
                  <span key={b.id} style={{ display: 'inline-flex', alignItems: 'center', gap: 7, fontSize: 11.5 }}><span className="sw" style={{ background: b.color }} />{b.label}</span>
                ))}
              </span>
            </div>

            <table className="table">
              <thead>
                <tr>
                  <th style={{ width: '24%' }}>Espécimen</th>
                  {conductas.map((c) => <th key={c.id} style={{ textAlign: 'right' }}>{c.label}</th>)}
                  <th style={{ width: '30%' }}>Distribución</th>
                </tr>
              </thead>
              <tbody>
                {r.especimenes.map((e) => (
                  <tr key={e.numeroCilindro}>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        <span className="num nd" style={{ width: 24, height: 24, flex: 'none', border: '2px solid var(--color-accent)', color: 'var(--color-accent-700)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10 }}>{e.numeroCilindro}</span>
                        <span style={{ fontSize: 13 }}>{etiqueta(e, r.letra)}</span>
                      </div>
                    </td>
                    {KEYS.map((k) => <td key={k} className="num" style={{ textAlign: 'right' }}>{fmt(e[k + '_s'])}</td>)}
                    <td>
                      <div style={{ display: 'flex', height: 16, border: '1px solid var(--color-divider)' }}>
                        {KEYS.map((k) => <div key={k} title={LBL[k]} style={{ background: CMAP[k], width: (e[k + '_s'] / r.duracion_s) * 100 + '%' }} />)}
                      </div>
                    </td>
                  </tr>
                ))}
                {stats.map((s) => (
                  <tr key={s.nombre} style={{ background: 'var(--color-surface)' }}>
                    <td className="k" style={{ color: MUT70 }}>{s.nombre}</td>
                    {KEYS.map((k) => <td key={k} className="num nd" style={{ textAlign: 'right' }}>{s.f(col(k))}</td>)}
                    <td style={{ fontSize: 11.5, color: MUT }}>{s.nota}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            <p className="hint" style={{ margin: '14px 0 0' }}>
              Cada segundo recibe una conducta y ninguna se descarta por durar poco (RF-18); el buceo se contabiliza como nado activo. Los tres renglones grises son los estadísticos de grupo de RF-31. El desglose por minuto se almacena y exporta <strong>en segundos</strong>, no en porcentaje.
            </p>

            <div ref={refs.timeline} className="k" style={{ margin: '26px 0 12px' }}>{etiqueta(r.linea_tiempo, r.letra)}</div>
            <div style={{ border: '1px solid var(--color-divider)', padding: '16px 18px' }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {r.linea_tiempo.minutos.map((segs, i) => (
                  <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <span className="num" style={{ width: 74, fontSize: 11.5, color: MUT60 }}>{fmt(i * 60)}–{fmt(i * 60 + 60)}</span>
                    <div style={{ flex: 1, display: 'flex', height: 18, border: '1px solid var(--color-divider)' }}>
                      {segs.map((s, j) => <div key={j} title={LBL[s[0]] + ' · ' + s[1] + ' s'} style={{ background: CMAP[s[0]], width: (s[1] / 60) * 100 + '%' }} />)}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div ref={refs.bloques} className="k" style={{ margin: '26px 0 12px' }}>Bloques de 5 s</div>
            <Bloques clave={clave} gid={gid} letra={letra} />
          </div>

          <div style={{ background: 'var(--color-bg)', paddingLeft: 26, display: 'flex', flexDirection: 'column', gap: 24 }}>
            <div>
              <div className="k" style={{ marginBottom: 12 }}>Análisis</div>
              <div style={{ borderTop: '1px solid var(--color-divider)' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, padding: '8px 0', borderBottom: '1px solid var(--color-divider)', fontSize: 11.5 }}>
                  <span style={{ color: MUT70 }}>Duración analizada</span>
                  <span className="num">{fmt(r.duracion_analizada_s)} / {fmt(r.duracion_s)}</span>
                </div>
              </div>
            </div>

            <div ref={refs.comparacion}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
                <label className="k" htmlFor="cConducta" style={{ flex: 1 }}>Comparación entre grupos · Día 2</label>
                <select className="input" id="cConducta" style={{ width: 150, padding: '4px 6px', fontSize: 12 }} value={conducta} onChange={(e) => setConducta(e.target.value)}>
                  {CONDUCTAS.map((x) => <option key={x.id} value={x.id}>{x.label}</option>)}
                </select>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 15 }}>
                {comparacion ? comparacion.map((g) => <Comparacion key={g.grupo} g={g} c={conducta} />) : <p className="hint">Cargando…</p>}
              </div>
              <p className="hint" style={{ margin: '12px 0 0' }}>
                Promedio por espécimen de cada grupo (media ± DE), en segundos sobre los 300 s del Día 2. Las marcas verticales delimitan ± una DE. Los especímenes sin Día 2 analizado (en cola, procesando, con error o sin subir) se señalan y no entran al promedio.
              </p>
            </div>

            <div>
              <div className="k" style={{ marginBottom: 12 }}>Leyenda de conductas</div>
              <div style={{ borderTop: '1px solid var(--color-divider)' }}>
                {conductas.map((b) => (
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

// Bloques de 5 s (D-53): cada bloque muestra la conducta con más segundos; al abrirlo, sus
// cinco segundos, para notar las conductas breves. Se calculan al pedirlos.
function Bloques({ clave, gid, letra }) {
  const { data, error } = useAsync(() => getBlocks(clave, gid, letra), [clave, gid, letra])
  const [abierto, setAbierto] = useState(null) // { numeroCilindro, i }

  if (error) return <p className="hint">No se pudieron calcular los bloques.</p>
  if (!data) return <p className="hint">Cargando…</p>

  const sel = abierto && data.especimenes.find((e) => e.numeroCilindro === abierto.numeroCilindro)?.bloques[abierto.i]
  const desc = (b) => fmt(b.inicio - 1) + '–' + fmt(b.inicio + 4) + ' · ' + LBL[b.conducta] + (b.matiz ? ' (no se vio bien)' : '')

  return (
    <div style={{ border: '1px solid var(--color-divider)', padding: '16px 18px' }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {data.especimenes.map((e) => (
          <div key={e.numeroCilindro} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <span style={{ width: 132, fontSize: 11.5, color: MUT60 }}>{especimen(e.numeroCilindro, letra)}</span>
            <div role="group" aria-label={'Bloques de ' + especimen(e.numeroCilindro, letra)} style={{ flex: 1, display: 'grid', gridTemplateColumns: 'repeat(60, 1fr)', height: 18, border: '1px solid var(--color-divider)' }}>
              {e.bloques.map((b, i) => {
                const on = abierto?.numeroCilindro === e.numeroCilindro && abierto.i === i
                return (
                  <button key={i} type="button" title={desc(b)} aria-label={desc(b)} aria-pressed={on}
                    onClick={() => setAbierto(on ? null : { numeroCilindro: e.numeroCilindro, i })}
                    style={{ padding: 0, border: 0, minWidth: 0, cursor: 'pointer', background: b.matiz ? MATIZ : CMAP[b.conducta], outline: on ? '2px solid var(--color-text)' : 'none', outlineOffset: -1 }} />
                )
              })}
            </div>
          </div>
        ))}
      </div>
      {sel ? (
        <div role="status" style={{ marginTop: 14, paddingTop: 12, borderTop: '1px solid var(--color-divider)' }}>
          <div className="nd" style={{ fontSize: 13 }}>{especimen(abierto.numeroCilindro, letra)} · {desc(sel)}</div>
          <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
            {sel.segundos.map((c, k) => (
              <div key={k} style={{ flex: 1, padding: '6px 8px', border: '1px solid var(--color-divider)', fontSize: 11.5 }}>
                <div className="num" style={{ color: MUT60 }}>{fmt(sel.inicio - 1 + k)}</div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 3 }}>
                  <span className="sw" style={{ background: c ? CMAP[c] : 'transparent', border: c ? 0 : '1px dashed var(--muted)' }} />
                  {c ? LBL[c] : 'No se ve'}
                </div>
              </div>
            ))}
          </div>
          {sel.matiz && <p className="hint" style={{ margin: '8px 0 0' }}>El modelo no pudo ver bien al espécimen en esta parte del video; el bloque se muestra como conducta activa con este matiz.</p>}
        </div>
      ) : (
        <p className="hint" style={{ margin: '12px 0 0' }}>
          Cada celda es un bloque de 5 s con la conducta que ocupa más de sus segundos; si nado activo y escalamiento empatan, el bloque es conducta activa. Las celdas rayadas no se vieron bien. Haz clic en una para ver sus cinco segundos.
        </p>
      )}
    </div>
  )
}
