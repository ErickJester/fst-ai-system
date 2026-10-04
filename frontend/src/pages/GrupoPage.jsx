import React, { useEffect } from 'react'
import { Link, useParams } from 'react-router-dom'
import Topbar, { Brand } from '../components/Topbar'
import { EstadoTag } from '../components/ui'
import { EXPERIMENTO as exp, GROUPS as groups, D2_NOTA } from '../data/mock'
import { NoEncontrado } from './ExperimentoPage'

const sub = { fontSize: 12, lineHeight: 1.55, marginTop: 3, color: 'color-mix(in srgb,var(--color-text) 70%,transparent)' }
const dur = { fontWeight: 400, fontFamily: 'var(--font-body)', color: 'var(--muted)' }

// Día 1 solo existe en la Tanda A del grupo control (mockup 2d).
const conD1 = (g, t) => g.id === 'G-01' && t.letra === 'A'

// 2d · Detalle de grupo: tandas, cilindros y la asimetría Día 1 / Día 2.
export default function GrupoPage() {
  const { clave, gid } = useParams()
  const g = groups.find((x) => x.id === gid)
  const nombreGrupo = g && (g.tipo === 'control' ? 'Grupo control' : g.nombre)

  useEffect(() => {
    if (nombreGrupo) document.title = 'FST · ' + nombreGrupo
    return () => { document.title = 'FST' }
  }, [nombreGrupo])

  if (clave !== exp.clave || !g) return <NoEncontrado />

  return (
    <div className="app">
      <Topbar>
        <Brand inline />
        <span className="crumbs">
          <Link to="/experimentos">Experimentos</Link> / <Link to={`/experimentos/${exp.clave}`}>{exp.titulo}</Link> / <span className="here">{nombreGrupo}</span>
        </span>
      </Topbar>

      <div className="page">
        <div style={{ marginBottom: 6 }}>
          <h2 style={{ margin: '0 0 9px' }}>
            {nombreGrupo} <span className={'tag ' + g.tipoTag} style={{ verticalAlign: 'middle', marginLeft: 8 }}>{g.tipo}</span>
          </h2>
          <div style={{ display: 'flex', gap: 24, fontSize: 13, color: 'var(--muted-2)' }}>
            <span>{g.trat}</span>
            <span className="num">{g.n} especímenes</span>
            <span className="num">{g.tandas.length} tandas · {g.reparto.join(' + ')} cilindros</span>
          </div>
        </div>

        <hr className="hr" />

        <div style={{ display: 'flex', flexDirection: 'column', gap: 2, background: 'var(--color-divider)', border: '1px solid var(--color-divider)' }}>
          {g.tandas.map((t) => {
            const d1 = conD1(g, t)
            const d2 = D2_NOTA[t.estado]
            return (
              <div key={t.letra} style={{ background: 'var(--color-bg)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '13px 20px', background: 'var(--color-surface)', borderBottom: '1px solid var(--color-divider)' }}>
                  <span className="nd" style={{ fontSize: 16 }}>{t.nombre}</span>
                  <span className="num" style={{ fontSize: 11.5, color: 'var(--muted)' }}>18–19 feb 2026</span>
                  <div style={{ flex: 1 }} />
                  <EstadoTag estado={t.estado} />
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '410px 1fr' }}>
                  <div style={{ padding: '18px 20px', borderRight: '2px solid var(--color-divider)' }}>
                    <div className="k" style={{ marginBottom: 13 }}>Cilindros de la tanda</div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, background: 'var(--color-divider)', border: '1px solid var(--color-divider)' }}>
                      {Array.from({ length: t.cuantos }, (_, i) => (
                        <div key={i} style={{ background: 'var(--color-bg)', display: 'flex', alignItems: 'center', gap: 11, padding: '11px 12px' }}>
                          <span className="num nd" style={{ width: 28, height: 28, flex: 'none', border: '2px solid var(--color-accent)', color: 'var(--color-accent-700)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11 }}>P{i + 1}</span>
                          <div className="nd" style={{ fontSize: 12.5, whiteSpace: 'nowrap' }}>Cilindro P{i + 1}</div>
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
                        {d1 && <EstadoTag estado="Completado" />}
                      </div>
                      <div style={{ background: 'var(--color-accent-100)', display: 'flex', alignItems: 'center', gap: 16, padding: '14px 15px', borderLeft: '4px solid var(--color-accent)' }}>
                        <div className="thumb grayscale" style={{ background: 'repeating-linear-gradient(135deg,color-mix(in srgb,var(--color-accent) 34%,transparent) 0 5px,transparent 5px 10px)', color: 'var(--color-accent-800)' }}>cuadro<br />día 2</div>
                        <div style={{ flex: 1 }}>
                          <div className="nd" style={{ fontSize: 13.5 }}>Día 2 · evaluación <span style={dur}>5 min · 24 h después</span></div>
                          <div style={sub}>{d2.nota}</div>
                        </div>
                        <EstadoTag estado={t.estado} />
                      </div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 12, fontSize: 11.5, color: 'color-mix(in srgb,var(--color-text) 60%,transparent)' }}>
                      <span>{d2.analisis}</span>
                      <div style={{ flex: 1 }} />
                      {t.estado === 'Completado'
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
