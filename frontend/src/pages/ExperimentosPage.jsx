import React, { useState } from 'react'
import { Link, NavLink, useSearchParams } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import Topbar, { Brand } from '../components/Topbar'
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

// Los reportes llevan a los resultados del experimento; sin ningún Día 2 analizado no hay
// nada que exportar. Sin backend, solo el experimento de ejemplo tiene detalle.
const conReportes = (e) => e.con_detalle && e.videos_dia2_listos > 0
const chipInactivo = { opacity: 0.4, cursor: 'default' }

// 2a · Experimentos: lista a nivel experimento con estado agregado.
export default function ExperimentosPage() {
  const { user } = useAuth()
  const [params] = useSearchParams()
  const sinPermiso = useAviso('sin-permiso')
  const [filtro, setFiltro] = useState('')
  const [q, setQ] = useState('')
  const { data, error, loading } = useAsync(listExperiments)

  // Tras «Eliminar experimento…» en el detalle, el experimento ya no aparece.
  const eliminado = params.get('eliminado')
  const experiments = (data || []).filter((e) => e.clave !== eliminado)

  const nq = norm(q.trim())
  const list = experiments.filter((e) => !filtro || e.estado === filtro).filter((e) => !nq || norm(e.titulo + ' ' + e.tratamientos).includes(nq))

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
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 28, marginBottom: 22 }}>
          <div style={{ flex: 1 }}>
            <h2 style={{ margin: '0 0 8px' }}>Experimentos</h2>
            <p className="lead" style={{ maxWidth: 620 }}>
              Prueba de nado forzado (<em>Forced Swim Test</em>, FST). El archivo completo del laboratorio, visible para cualquier cuenta activa.
            </p>
          </div>
          <Link className="btn btn-primary" to="/experimentos/nuevo" style={btnLeft}>Nuevo experimento</Link>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 16, padding: '13px 16px', marginBottom: 22, background: 'var(--color-accent-100)', borderLeft: '4px solid var(--color-accent)' }}>
          <span className="trace">RN-06</span>
          <span style={{ flex: 1, fontSize: 12.5, lineHeight: 1.55 }}>
            Los 10 videos crudos de <strong>Extracto de <em>Valeriana officinalis</em></strong> se eliminan en 4 días (retención de 30 días). Resultados y reportes se conservan indefinidamente; el video crudo no.
          </span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
          <label className="k" htmlFor="fEstado">Estado</label>
          <select className="input" id="fEstado" style={{ width: 230 }} value={filtro} onChange={(e) => setFiltro(e.target.value)}>
            <option value="">Todos · {experiments.length}</option>
            {Object.entries(ESTADO_EXPERIMENTO).map(([v, label]) => (
              <option key={v} value={v}>{label} · {experiments.filter((e) => e.estado === v).length}</option>
            ))}
          </select>
          <div style={{ flex: 1 }} />
          <input className="input" type="search" placeholder="Buscar por nombre o tratamiento" style={{ width: 250 }} value={q} onChange={(e) => setQ(e.target.value)} />
        </div>

        <table className="table">
          <thead>
            <tr>
              <th style={{ width: '29%' }}>Experimento</th><th>Grupos</th><th>Especímenes</th><th style={{ width: '14%' }}>Día 2 analizados</th>
              <th>Estado</th><th>Responsable</th><th>Retención</th><th>Reporte</th>
            </tr>
          </thead>
          <tbody>
            {list.map((e) => {
              const videos = videosDia2(e)
              const ret = retencion(e)
              return (
                <tr key={e.clave}>
                  <td>
                    {e.con_detalle
                      ? <Link to={`/experimentos/${e.clave}`} className="nd" style={{ fontSize: 14, color: 'var(--color-text)', textDecoration: 'none' }}>{e.titulo}</Link>
                      : <div className="nd" style={{ fontSize: 14 }}>{e.titulo}</div>}
                    <div className="num" style={{ fontSize: 11, color: 'var(--muted)', marginTop: 2 }}>{fechaCorta(e.fecha_inicio)}</div>
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
                    <div style={{ display: 'flex', gap: 4 }}>
                      {['CSV', 'XLSX', 'PDF'].map((x) => conReportes(e)
                        ? <Link key={x} className="chip" to={`/experimentos/${e.clave}/resultados`}>{x}</Link>
                        : <span key={x} className="chip" style={chipInactivo} aria-disabled="true" title="Todavía no hay resultados">{x}</span>)}
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
        {loading && <p className="hint" style={{ margin: '14px 0 0' }}>Cargando experimentos…</p>}
        {error && <p className="hint" style={{ margin: '14px 0 0', color: 'var(--color-accent-700)' }}>No se pudo cargar la lista de experimentos.</p>}
        {data && list.length === 0 && <p className="hint" style={{ margin: '14px 0 0' }}>Ningún experimento coincide con la búsqueda.</p>}
        <p style={{ margin: '14px 0 0', fontSize: 11.5, color: 'var(--muted)' }}>
          Eliminar un experimento es permanente e irreversible: la acción vive dentro del detalle del experimento, nunca en esta lista.
        </p>
      </div>
    </div>
  )
}
