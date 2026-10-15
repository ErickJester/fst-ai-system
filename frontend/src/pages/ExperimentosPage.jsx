import React, { useState } from 'react'
import { Link, NavLink, useSearchParams } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import Topbar, { Brand } from '../components/Topbar'
import EliminarExperimento, { puedeEliminar } from '../components/EliminarExperimento'
import { listExperiments } from '../services/experiments'
import { useAsync } from '../hooks/useAsync'
import { useAviso } from '../hooks/useAviso'
import { norm, fechaCorta, ESTADO_EXPERIMENTO } from '../lib/fst'
import { btnLeft } from '../lib/estilos'

// Apariencia de cada estado calculado del experimento (ESTADO_EXPERIMENTO).
const TAG = {
  SIN_VIDEOS: { cls: 'tag-outline', style: { color: 'var(--muted)' } },
  ESPERA_DIA2: { cls: 'tag-outline' },
  QUEUED: { cls: 'tag-outline' },
  RUNNING: { cls: 'tag-accent' },
  PAUSED: { cls: 'tag-accent' },
  DONE: { cls: 'tag-neutral' },
  FAILED: { cls: 'tag-accent', style: { background: 'var(--color-accent-800)', color: 'var(--color-bg)' } },
  CANCELED: { cls: 'tag-neutral', style: { color: 'var(--muted)' } },
}

// Tandas con el Día 2 ya analizado, de las que tiene el experimento.
function videosDia2(e) {
  return {
    texto: e.videos_dia2_listos + ' / ' + e.videos_dia2_total,
    pct: (e.videos_dia2_total ? Math.round((e.videos_dia2_listos / e.videos_dia2_total) * 100) : 0) + '%',
  }
}

// Retención del video crudo: la última semana se resalta.
function retencion(e) {
  if (e.videos_borrados) return { texto: 'Videos borrados', color: 'var(--muted)' }
  if (e.retencion_dias == null) return { texto: '—', color: 'var(--muted)' } // todavía sin videos
  return { texto: e.retencion_dias + ' d', color: e.retencion_dias <= 7 ? 'var(--color-accent-700)' : 'inherit' }
}

// Valores distintos, ordenados, para las opciones de un filtro.
const opciones = (valores) => [...new Set(valores)].sort((a, b) => a.localeCompare(b, 'es'))

// Los reportes llevan a los resultados del experimento; sin ningún Día 2 analizado no hay
// nada que exportar. Sin backend, solo el experimento de ejemplo tiene detalle.
const conReportes = (e) => e.con_detalle && e.videos_dia2_listos > 0
const chipInactivo = { opacity: 0.4, cursor: 'default' }
const POR_PAGINA = 10

// 2a · Experimentos (dashboard): todos los del laboratorio, con estado calculado, filtros
// por estado, año y tratamiento, aviso de videos por borrarse y eliminar (R-08, D-63).
export default function ExperimentosPage() {
  const { user } = useAuth()
  const [params] = useSearchParams()
  const sinPermiso = useAviso('sin-permiso')
  const [filtro, setFiltro] = useState({ estado: '', anio: '', tratamiento: '' })
  const [q, setQ] = useState('')
  const [pagina, setPagina] = useState(1)
  const [borrar, setBorrar] = useState(null) // experimento del diálogo de eliminar
  const [eliminados, setEliminados] = useState([]) // títulos eliminados desde esta lista
  const { data, error, loading, reload } = useAsync(listExperiments)

  // Tras «Eliminar experimento…» en el detalle, el experimento ya no aparece.
  const eliminado = params.get('eliminado')
  const experiments = (data || []).filter((e) => e.clave !== eliminado)
  const anios = opciones(experiments.map((e) => e.fecha_inicio.slice(0, 4))).reverse()
  const tratamientos = opciones(experiments.flatMap((e) => e.tratamientos))
  const avisos = experiments.filter((e) => e.aviso_vencimiento)

  const nq = norm(q.trim())
  const list = experiments
    .filter((e) => !filtro.estado || e.estado === filtro.estado)
    .filter((e) => !filtro.anio || e.fecha_inicio.startsWith(filtro.anio))
    // Basta con que algún grupo tenga ese tratamiento.
    .filter((e) => !filtro.tratamiento || e.tratamientos.includes(filtro.tratamiento))
    .filter((e) => !nq || norm(e.titulo + ' ' + e.tratamientos.join(' ')).includes(nq))
  const filtrar = (campo) => (ev) => { setFiltro((f) => ({ ...f, [campo]: ev.target.value })); setPagina(1) }
  const paginas = Math.max(1, Math.ceil(list.length / POR_PAGINA))
  const actual = Math.min(pagina, paginas)
  const enPagina = list.slice((actual - 1) * POR_PAGINA, actual * POR_PAGINA)

  function alEliminar(exp) {
    setBorrar(null)
    setEliminados((l) => [...l, exp.titulo])
    reload()
  }

  return (
    <div className="app">
      <Topbar>
        <Brand sub="Laboratorio de Bioquímica Estructural · ENMyH-IPN" />
        <NavLink to="/experimentos" end>Experimentos</NavLink>
        <NavLink to="/analisis">Análisis en curso</NavLink>
        {user.admin && <NavLink to="/admin">Administración</NavLink>}
      </Topbar>

      <div className="page" style={{ paddingBottom: 34 }}>
        {sinPermiso && <div className="note-bar" style={{ marginBottom: 18 }}>No tienes permiso para esa sección.</div>}
        {eliminados.map((t) => <div key={t} className="note-bar" role="status" style={{ marginBottom: 18 }}>Se eliminó «{t}».</div>)}
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 28, marginBottom: 22 }}>
          <div style={{ flex: 1 }}>
            <h2 style={{ margin: '0 0 8px' }}>Experimentos</h2>
            <p className="lead" style={{ maxWidth: 620 }}>
              Prueba de nado forzado (<em>Forced Swim Test</em>, FST). El archivo completo del laboratorio, visible para cualquier cuenta activa.
            </p>
          </div>
          <Link className="btn btn-primary" to="/experimentos/nuevo" style={btnLeft}>Nuevo experimento</Link>
        </div>

        {/* Aviso de vencimiento (R-05): videos que se borran en 7 días o menos, con la fecha exacta. */}
        {avisos.length > 0 && (
          <div style={{ padding: '13px 16px', marginBottom: 22, background: 'var(--color-accent-100)', borderLeft: '4px solid var(--color-accent)', fontSize: 12.5, lineHeight: 1.55 }}>
            {avisos.map((e) => (
              <div key={e.clave}>Los videos de <strong>{e.titulo}</strong> se borran el <strong className="num">{fechaCorta(e.aviso_vencimiento.fecha_borrado)}</strong>.</div>
            ))}
            <div style={{ color: 'var(--muted-2)', marginTop: 4 }}>El borrado es irreversible. Resultados y reportes se conservan siempre.</div>
          </div>
        )}

        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 10, marginBottom: 16 }}>
          <div className="field" style={{ margin: 0 }}>
            <label htmlFor="fEstado">Estado</label>
            <select className="input" id="fEstado" style={{ width: 200 }} value={filtro.estado} onChange={filtrar('estado')}>
              <option value="">Todos · {experiments.length}</option>
              {Object.entries(ESTADO_EXPERIMENTO).map(([v, label]) => (
                <option key={v} value={v}>{label} · {experiments.filter((e) => e.estado === v).length}</option>
              ))}
            </select>
          </div>
          <div className="field" style={{ margin: 0 }}>
            <label htmlFor="fAnio">Fecha</label>
            <select className="input" id="fAnio" style={{ width: 130 }} value={filtro.anio} onChange={filtrar('anio')}>
              <option value="">Cualquiera</option>
              {anios.map((a) => <option key={a} value={a}>{a}</option>)}
            </select>
          </div>
          <div className="field" style={{ margin: 0 }}>
            <label htmlFor="fTrat">Tratamiento</label>
            <select className="input" id="fTrat" style={{ width: 240 }} value={filtro.tratamiento} onChange={filtrar('tratamiento')}>
              <option value="">Todos</option>
              {tratamientos.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
          <div style={{ flex: 1 }} />
          <input className="input" type="search" aria-label="Buscar por nombre o tratamiento" placeholder="Buscar por nombre o tratamiento" style={{ width: 250 }} value={q} onChange={(e) => { setQ(e.target.value); setPagina(1) }} />
        </div>

        <table className="table">
          <thead>
            <tr>
              <th style={{ width: '27%' }}>Experimento</th><th>Grupos</th><th>Especímenes</th><th style={{ width: '13%' }}>Día 2 analizados</th>
              <th>Estado</th><th>Responsable</th><th>Retención</th><th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            {enPagina.map((e) => {
              const videos = videosDia2(e)
              const ret = retencion(e)
              return (
                <tr key={e.clave}>
                  <td>
                    {e.con_detalle
                      ? <Link to={`/experimentos/${e.clave}`} className="nd" style={{ fontSize: 14, color: 'var(--color-text)', textDecoration: 'none' }}>{e.titulo}</Link>
                      : <div className="nd" style={{ fontSize: 14 }}>{e.titulo}</div>}
                    <div className="num" style={{ fontSize: 11, color: 'var(--muted)', marginTop: 2 }}>{fechaCorta(e.fecha_inicio)}</div>
                    {/* Sin backend, algunos experimentos de ejemplo solo traen su fila (vienen de las
                        maquetas): no se pueden abrir. Con el backend todos tienen detalle. */}
                    {!e.con_detalle && <div className="hint" style={{ fontSize: 11, marginTop: 3 }}>Sin detalle en el modo demo: solo se muestra su fila.</div>}
                  </td>
                  <td className="num">{e.n_grupos}</td>
                  <td className="num">{e.n_especimenes}</td>
                  <td>
                    <div className="num" style={{ fontSize: 12, marginBottom: 5 }}>{videos.texto}</div>
                    <div className="meter"><div style={{ width: videos.pct }} /></div>
                  </td>
                  <td><span className={'tag ' + TAG[e.estado].cls} style={{ whiteSpace: 'nowrap', ...TAG[e.estado].style }}>{ESTADO_EXPERIMENTO[e.estado]}</span></td>
                  <td style={{ fontSize: 13 }}>{e.responsable}</td>
                  <td className="num" style={{ fontSize: 12, color: ret.color }}>{ret.texto}</td>
                  <td>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                      {['CSV', 'XLSX', 'PDF'].map((x) => conReportes(e)
                        ? <Link key={x} className="chip" to={`/experimentos/${e.clave}/resultados`}>{x}</Link>
                        : <span key={x} className="chip" style={chipInactivo} aria-disabled="true" title={e.con_detalle ? 'Todavía no hay resultados' : 'Sin detalle en el modo demo'}>{x}</span>)}
                      {puedeEliminar(user, e) && (
                        <button type="button" className="linkbtn" style={{ fontSize: 12, marginLeft: 6, whiteSpace: 'nowrap' }}
                          aria-label={'Eliminar ' + e.titulo} onClick={() => setBorrar(e)}>Eliminar…</button>
                      )}
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
        {paginas > 1 && (
          <nav aria-label="Páginas de experimentos" style={{ display: 'flex', alignItems: 'center', gap: 4, marginTop: 14 }}>
            <button type="button" className="btn btn-ghost" disabled={actual === 1} aria-label="Página anterior" onClick={() => setPagina(actual - 1)}>‹</button>
            {Array.from({ length: paginas }, (_, i) => i + 1).map((n) => (
              <button key={n} type="button" className={'btn ' + (n === actual ? 'btn-primary' : 'btn-ghost')} aria-current={n === actual ? 'page' : undefined} onClick={() => setPagina(n)}>{n}</button>
            ))}
            <button type="button" className="btn btn-ghost" disabled={actual === paginas} aria-label="Página siguiente" onClick={() => setPagina(actual + 1)}>›</button>
            <span className="hint" style={{ marginLeft: 8 }}>{list.length} experimentos</span>
          </nav>
        )}
        {loading && <p className="hint" style={{ margin: '14px 0 0' }}>Cargando experimentos…</p>}
        {error && <p className="hint" style={{ margin: '14px 0 0', color: 'var(--color-accent-700)' }}>No se pudo cargar la lista de experimentos.</p>}
        {/* Sin ningún experimento (§9.2): se invita a crear el primero. Con filtros, otro aviso. */}
        {data && experiments.length === 0 && (
          <div style={{ marginTop: 18, padding: '28px 24px', border: '2px dashed var(--color-divider)', textAlign: 'center' }}>
            <div className="nd" style={{ fontSize: 16, marginBottom: 8 }}>Aún no hay experimentos</div>
            <p className="hint" style={{ margin: '0 0 16px' }}>Crea el primero: solo pide nombre, fecha y notas. Los grupos y los videos se agregan después.</p>
            <Link className="btn btn-primary" to="/experimentos/nuevo">Nuevo experimento</Link>
          </div>
        )}
        {data && experiments.length > 0 && list.length === 0 && <p className="hint" style={{ margin: '14px 0 0' }}>Ningún experimento coincide con los filtros.</p>}
        <p style={{ margin: '14px 0 0', fontSize: 11.5, color: 'var(--muted)' }}>
          Eliminar un experimento es permanente e irreversible. Solo puede hacerlo quien lo creó o un Administrador, y no mientras un análisis esté procesando o en pausa.
        </p>
      </div>

      {borrar && <EliminarExperimento exp={borrar} onCerrar={() => setBorrar(null)} onEliminado={alEliminar} />}
    </div>
  )
}
