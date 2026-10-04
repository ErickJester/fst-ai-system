import React, { useEffect } from 'react'
import { Link, useParams } from 'react-router-dom'
import Topbar, { Brand } from '../components/Topbar'
import { EstadoTag, TipoTag, Migas, Cargando, NoEncontrado } from '../components/ui'
import { getGroup } from '../services/groups'
import { useAsync } from '../hooks/useAsync'
import { fechaCorta, fechaRango, ETAPA } from '../lib/fst'

const sub = { fontSize: 12, lineHeight: 1.55, marginTop: 3, color: 'color-mix(in srgb,var(--color-text) 70%,transparent)' }
const dur = { fontWeight: 400, fontFamily: 'var(--font-body)', color: 'var(--muted)' }

// Nota del video de Día 2 según el estado de su análisis.
function notaDia2(t) {
  switch (t.estado) {
    case 'DONE': return 'Video principal del sistema. Analizado completo.'
    case 'QUEUED': return 'En espera de turno en la cola: un trabajo a la vez.'
    case 'RUNNING': return ETAPA[t.progreso.etapa] + ' · en curso · ' + t.progreso.pct + ' %'
    case 'FAILED': return t.error.codigo + ' · ' + t.error.mensaje
    case 'SIN_VIDEO': return 'Todavía no se sube el video de Día 2.'
    default: return ''
  }
}

// 2d · Detalle de grupo: tandas, cilindros y la asimetría Día 1 / Día 2.
export default function GrupoPage() {
  const { clave, gid } = useParams()
  const { data: g, error, loading } = useAsync(() => getGroup(clave, gid), [clave, gid])
  const nombreGrupo = g && (g.tipo === 'CONTROL' ? 'Grupo control' : g.nombre)

  useEffect(() => {
    if (nombreGrupo) document.title = 'FST · ' + nombreGrupo
    return () => { document.title = 'FST' }
  }, [nombreGrupo])

  if (loading) return <Cargando />
  if (error) return <NoEncontrado />

  const exp = g.experimento

  return (
    <div className="app">
      <Topbar>
        <Brand inline />
        <Migas items={[{ to: '/experimentos', label: 'Experimentos' }, { to: `/experimentos/${exp.clave}`, label: exp.titulo }, { label: nombreGrupo }]} />
      </Topbar>

      <div className="page">
        <div style={{ marginBottom: 6 }}>
          <h2 style={{ margin: '0 0 9px' }}>
            {nombreGrupo} <TipoTag tipo={g.tipo} style={{ verticalAlign: 'middle', marginLeft: 8 }} />
          </h2>
          <div style={{ display: 'flex', gap: 24, fontSize: 13, color: 'var(--muted-2)' }}>
            <span>{g.tratamiento}</span>
            <span className="num">{g.n_especimenes} especímenes</span>
            <span className="num">{g.tandas.length} tandas · {g.tandas.map((t) => t.n_cilindros).join(' + ')} cilindros</span>
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
                  <EstadoTag estado={t.estado} />
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '410px 1fr' }}>
                  <div style={{ padding: '18px 20px', borderRight: '2px solid var(--color-divider)' }}>
                    <div className="k" style={{ marginBottom: 13 }}>Especímenes · rata n.º (marca en la cola) y cilindro</div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, background: 'var(--color-divider)', border: '1px solid var(--color-divider)' }}>
                      {Array.from({ length: t.n_cilindros }, (_, i) => (
                        <div key={i} style={{ background: 'var(--color-bg)', display: 'flex', alignItems: 'center', gap: 11, padding: '11px 12px' }}>
                          <span className="num nd" style={{ width: 28, height: 28, flex: 'none', border: '2px solid var(--color-accent)', color: 'var(--color-accent-700)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11 }}>{t.desde + i}</span>
                          <div className="nd" style={{ fontSize: 12.5, whiteSpace: 'nowrap' }}>Rata {t.desde + i} · Cilindro P{i + 1}</div>
                        </div>
                      ))}
                    </div>
                  </div>
                  <div style={{ padding: '18px 20px' }}>
                    <div className="k" style={{ marginBottom: 13 }}>Videos de la tanda</div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 2, background: 'var(--color-divider)', border: '1px solid var(--color-divider)' }}>
                      <div style={{ background: d1 ? 'var(--color-bg)' : 'var(--color-surface)', display: 'flex', alignItems: 'center', gap: 16, padding: '14px 15px', opacity: d1 ? 1 : 0.55 }}>
                        <div className="thumb grayscale" style={{ background: 'repeating-linear-gradient(135deg,color-mix(in srgb,var(--color-text) 16%,transparent) 0 5px,transparent 5px 10px)', color: 'color-mix(in srgb,var(--color-text) 60%,transparent)' }}>cuadro<br />día 1</div>
                        <div style={{ flex: 1 }}>
                          <div className="nd" style={{ fontSize: 13.5 }}>Día 1 · sesión de estrés <span style={dur}>20 min</span></div>
                          <div style={sub}>{d1 ? 'Se analizan sus primeros 5 minutos.' : 'Sin video de Día 1 (opcional)'}</div>
                        </div>
                        {d1 && <EstadoTag estado={t.dia1.estado} />}
                      </div>
                      <div style={{ background: 'var(--color-accent-100)', display: 'flex', alignItems: 'center', gap: 16, padding: '14px 15px', borderLeft: '4px solid var(--color-accent)' }}>
                        <div className="thumb grayscale" style={{ background: 'repeating-linear-gradient(135deg,color-mix(in srgb,var(--color-accent) 34%,transparent) 0 5px,transparent 5px 10px)', color: 'var(--color-accent-800)' }}>cuadro<br />día 2</div>
                        <div style={{ flex: 1 }}>
                          <div className="nd" style={{ fontSize: 13.5 }}>Día 2 · evaluación <span style={dur}>5 min · 24 h después</span></div>
                          <div style={sub}>{notaDia2(t)}</div>
                        </div>
                        <EstadoTag estado={t.estado} />
                      </div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 12, fontSize: 11.5, color: 'color-mix(in srgb,var(--color-text) 60%,transparent)' }}>
                      <span>{t.analisis_fecha ? 'Análisis vigente: ' + fechaCorta(t.analisis_fecha) : 'Sin análisis todavía'}</span>
                      <div style={{ flex: 1 }} />
                      {t.estado === 'DONE'
                        ? <Link to={`/experimentos/${exp.clave}/resultados`}>Ver resultados</Link>
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
