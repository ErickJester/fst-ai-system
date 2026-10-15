import React, { useEffect } from 'react'
import { Link, useParams } from 'react-router-dom'
import Topbar, { Brand } from '../components/Topbar'
import { EstadoTag, TipoTag, Migas, Cargando, NoEncontrado } from '../components/ui'
import { getGroup } from '../services/groups'
import { useAsyncVivo, CADA_ACTIVO_MS, CADA_INACTIVO_MS } from '../hooks/useAsync'
import { fechaCorta, fechaRango, ETAPA, enlaceResultados, enlacePausa, enlaceRevision, mensajeErrorAnalisis, especimen, estadoDeTanda } from '../lib/fst'
import { MUT60, MUT70 } from '../lib/estilos'

const sub = { fontSize: 12, lineHeight: 1.55, marginTop: 3, color: MUT70 }
const dur = { fontWeight: 400, fontFamily: 'var(--font-body)', color: 'var(--muted)' }

const ACTIVOS = ['QUEUED', 'RUNNING', 'PAUSED']

// Nota del video de Día 2 según el estado de su análisis.
function notaDia2(t) {
  switch (estadoDeTanda(t)) {
    case 'DONE': return 'Video principal del sistema. Analizado completo.'
    case 'QUEUED': return 'En espera de turno en la cola: un trabajo a la vez.'
    case 'RUNNING': return ETAPA[t.progreso.etapa] + ' · en curso · ' + t.progreso.pct + ' %'
    case 'FAILED': return mensajeErrorAnalisis(t.error.etapa)
    case 'PAUSED': return 'En pausa: el sistema dejó de ver a un espécimen. Hay 5 minutos para ajustar el recuadro.'
    case 'CANCELED': return 'Análisis cancelado.'
    case 'ESPERA_DIA2': return 'Aún no se sube el video del Día 2. El Día 1 ya está guardado; el análisis empieza cuando se cargue el del Día 2.'
    case 'SIN_VIDEO': return 'Todavía no se sube el video de Día 2.'
    default: return ''
  }
}

// 2d · Detalle de grupo: tandas, especímenes y la asimetría Día 1 / Día 2.
export default function GrupoPage() {
  const { clave, gid } = useParams()
  // Se vuelve a consultar sola: cada 5 s mientras alguna tanda se analiza, si no cada 30 s.
  const { data: g, error, loading } = useAsyncVivo(() => getGroup(clave, gid), [clave, gid], (grupo) =>
    grupo.tandas.some((t) => ACTIVOS.includes(t.estado)) ? CADA_ACTIVO_MS : CADA_INACTIVO_MS)
  const nombreGrupo = g && (g.tipo === 'CONTROL' ? 'Grupo control' : g.nombre)

  useEffect(() => {
    if (nombreGrupo) document.title = 'FST · ' + nombreGrupo
    return () => { document.title = 'FST' }
  }, [nombreGrupo])

  if (loading) return <Cargando />
  if (error) return <NoEncontrado />

  const exp = g.experimento
  const completadas = g.tandas.filter((t) => t.estado === 'DONE').length

  return (
    <div className="app">
      <Topbar>
        <Brand inline />
        <Migas items={[{ to: '/experimentos', label: 'Experimentos' }, { to: `/experimentos/${exp.clave}`, label: exp.titulo }, { label: nombreGrupo }]} />
      </Topbar>

      <div className="page">
        <div style={{ marginBottom: 6, display: 'flex', alignItems: 'flex-end', gap: 24 }}>
          <div style={{ flex: 1 }}>
            <h2 style={{ margin: '0 0 9px' }}>
              {nombreGrupo} <TipoTag tipo={g.tipo} style={{ verticalAlign: 'middle', marginLeft: 8 }} />
            </h2>
            <div style={{ display: 'flex', gap: 24, fontSize: 13, color: 'var(--muted-2)' }}>
              <span>{g.tratamiento}</span>
              <span className="num">{g.n_especimenes} especímenes</span>
              <span className="num">{g.tandas.length} tandas · {g.tandas.map((t) => t.n_cilindros).join(' + ')} especímenes</span>
            </div>
          </div>
          {/* Resultados del grupo completo (RF-19, D-93): reúne las tandas con análisis completado. */}
          <div style={{ textAlign: 'right' }}>
            {completadas
              ? <Link className="btn btn-primary" to={`/experimentos/${exp.clave}/resultados?grupo=${g.id}`}>Ver resultados del grupo completo</Link>
              : <button type="button" className="btn btn-primary" disabled title="Ninguna tanda tiene el Día 2 analizado">Ver resultados del grupo completo</button>}
            <div className="hint" style={{ marginTop: 6 }}>Reúne las tandas con análisis completado: {completadas} de {g.tandas.length}</div>
          </div>
        </div>

        <hr className="hr" />

        <div style={{ display: 'flex', flexDirection: 'column', gap: 2, background: 'var(--color-divider)', border: '1px solid var(--color-divider)' }}>
          {g.tandas.map((t) => {
            const d1 = !!t.dia1
            return (
              <div key={t.letra} style={{ background: 'var(--color-bg)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '13px 20px', background: 'var(--color-surface)', borderBottom: '1px solid var(--color-divider)' }}>
                  <span className="nd" style={{ fontSize: 16 }}>Tanda {t.letra}</span>
                  <span className="num" style={{ fontSize: 11.5, color: 'var(--muted)' }}>{fechaRango(t.fecha_dia1, t.fecha_dia2)}</span>
                  <div style={{ flex: 1 }} />
                  <EstadoTag estado={estadoDeTanda(t)} />
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '410px 1fr' }}>
                  <div style={{ padding: '18px 20px', borderRight: '2px solid var(--color-divider)' }}>
                    <div className="k" style={{ marginBottom: 13 }}>Especímenes de la tanda</div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, background: 'var(--color-divider)', border: '1px solid var(--color-divider)' }}>
                      {Array.from({ length: t.n_cilindros }, (_, i) => (
                        <div key={i} style={{ background: 'var(--color-bg)', display: 'flex', alignItems: 'center', gap: 11, padding: '11px 12px' }}>
                          <span className="num nd" style={{ width: 28, height: 28, flex: 'none', border: '2px solid var(--color-accent)', color: 'var(--color-accent-700)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11 }}>{i + 1}</span>
                          <div className="nd" style={{ fontSize: 12.5, whiteSpace: 'nowrap' }}>{especimen(i + 1)}</div>
                        </div>
                      ))}
                    </div>
                  </div>
                  <div style={{ padding: '18px 20px' }}>
                    <div className="k" style={{ marginBottom: 13 }}>Videos de la tanda</div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 2, background: 'var(--color-divider)', border: '1px solid var(--color-divider)' }}>
                      <div style={{ background: d1 ? 'var(--color-bg)' : 'var(--color-surface)', display: 'flex', alignItems: 'center', gap: 16, padding: '14px 15px', opacity: d1 ? 1 : 0.55 }}>
                        <div className="thumb grayscale" style={{ background: 'repeating-linear-gradient(135deg,color-mix(in srgb,var(--color-text) 16%,transparent) 0 5px,transparent 5px 10px)', color: MUT60 }}>cuadro<br />día 1</div>
                        <div style={{ flex: 1 }}>
                          <div className="nd" style={{ fontSize: 13.5 }}>Día 1 · sesión de estrés <span style={dur}>20 min</span></div>
                          <div style={sub}>{d1 ? 'Se guarda y no se analiza (habituación).' : 'Sin video de Día 1 (opcional)'}</div>
                        </div>
                        {d1 && <span className="tag" style={{ whiteSpace: 'nowrap' }}>Guardado</span>}
                      </div>
                      <div style={{ background: 'var(--color-accent-100)', display: 'flex', alignItems: 'center', gap: 16, padding: '14px 15px', borderLeft: '4px solid var(--color-accent)' }}>
                        <div className="thumb grayscale" style={{ background: 'repeating-linear-gradient(135deg,color-mix(in srgb,var(--color-accent) 34%,transparent) 0 5px,transparent 5px 10px)', color: 'var(--color-accent-800)' }}>cuadro<br />día 2</div>
                        <div style={{ flex: 1 }}>
                          <div className="nd" style={{ fontSize: 13.5 }}>Día 2 · evaluación <span style={dur}>5 min · 24 h después</span></div>
                          <div style={sub}>{notaDia2(t)}</div>
                        </div>
                        <EstadoTag estado={estadoDeTanda(t)} />
                      </div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 12, fontSize: 11.5, color: MUT60 }}>
                      <span>{t.analisis_fecha ? 'Análisis vigente: ' + fechaCorta(t.analisis_fecha) : 'Sin análisis todavía'}</span>
                      <div style={{ flex: 1 }} />
                      {t.estado === 'DONE'
                        ? (
                          <span style={{ display: 'flex', gap: 14 }}>
                            {t.id_analisis && <Link to={enlaceRevision(exp.clave, t.id_analisis)}>Revisar segundo a segundo</Link>}
                            <Link to={enlaceResultados(exp.clave, g.id, t.letra)}>Ver resultados de esta tanda</Link>
                          </span>
                        )
                        : t.estado === 'PAUSED'
                          ? <Link to={enlacePausa(exp.clave, t.id_analisis)}>Ajustar o cancelar →</Link>
                          : t.estado === 'SIN_VIDEO'
                            ? <Link to={`/experimentos/${exp.clave}/cargar`}>Cargar el video del Día 2 →</Link>
                            : <Link to="/analisis">Ver progreso del análisis →</Link>}
                    </div>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
