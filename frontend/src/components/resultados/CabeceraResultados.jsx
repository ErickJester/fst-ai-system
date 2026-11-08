import React from 'react'
import { Link } from 'react-router-dom'
import { textoVideo, mmss, enlaceResultados } from '../../lib/fst'
import { fechaYHora, nombreGrupo } from './constantes'

// Título de la vista, datos del análisis, el selector «Ver» (una tanda o el grupo completo) y las etiquetas de nivel.
export default function CabeceraResultados({ clave, gid, r, rs, grupoCompleto, analizado, tiempos, completadas, nCompletos, agrupado, revisado }) {
  const enlaceGrupo = `/experimentos/${clave}/resultados?grupo=${gid}`
  return (
    <div style={{ flex: 1 }}>
      <h2 style={{ margin: '0 0 9px' }}>Resultados · {nombreGrupo(r.grupo)}, {grupoCompleto ? 'grupo completo' : 'Tanda ' + r.letra}</h2>
      <div className="num" style={{ fontSize: 12.5, color: 'var(--muted-2)' }}>{r.grupo.tratamiento} · Día 2, {r.duracion_analizada_s} s analizados de {r.duracion_s} s de video · analizado {fechaYHora(analizado)}</div>
      {/* El nombre del archivo del video, como lo subió el usuario; en el grupo completo, uno por tanda. */}
      <div className="num" style={{ fontSize: 12.5, color: 'var(--muted-2)', marginTop: 4 }}>
        {grupoCompleto ? rs.map((x) => 'Tanda ' + x.letra + ' · ' + textoVideo(x.nombre_video)).join('; ') : textoVideo(r.nombre_video)}
      </div>
      {tiempos.length > 0 && (
        <div className="num" style={{ fontSize: 12.5, color: 'var(--muted-2)', marginTop: 4 }}>
          Tiempo de análisis: {tiempos.map((x) => (grupoCompleto ? 'Tanda ' + x.letra + ' ' : '') + mmss(x.tiempo_analisis.total_s) + (x.tiempo_analisis.equipo ? ' · ' + x.tiempo_analisis.equipo : '')).join('; ')}
        </div>
      )}
      {/* Ver una tanda o el grupo completo (RF-19, D-93). */}
      <nav aria-label="Ver" className="no-print" style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 8, marginTop: 12 }}>
        <span className="k" style={{ marginRight: 4 }}>Ver</span>
        {completadas.map((t) => {
          const on = !grupoCompleto && t.letra === r.letra
          return (
            <Link key={t.letra} to={enlaceResultados(clave, gid, t.letra)} className={'tab' + (on ? ' on' : '')} aria-current={on ? 'page' : undefined}
              style={{ whiteSpace: 'nowrap', marginBottom: 0, padding: '6px 13px', border: '1px solid ' + (on ? 'var(--color-accent)' : 'var(--color-divider)'), textDecoration: 'none' }}>
              Tanda {t.letra} · {t.n_cilindros} especímenes
            </Link>
          )
        })}
        <Link to={enlaceGrupo} className={'tab' + (grupoCompleto ? ' on' : '')} aria-current={grupoCompleto ? 'page' : undefined}
          style={{ whiteSpace: 'nowrap', marginBottom: 0, padding: '6px 13px', border: '1px solid ' + (grupoCompleto ? 'var(--color-accent)' : 'var(--color-divider)'), textDecoration: 'none' }}>
          Grupo completo · {nCompletos} especímenes
        </Link>
        <span className="hint" style={{ maxWidth: 360 }}>El grupo completo reúne sus tandas con análisis completado; cada espécimen lleva el nombre de su tanda.</span>
      </nav>
      {/* D-78: el sistema solo entrega la conducta de cada segundo; conviene revisarla. */}
      <p className="hint" style={{ margin: '10px 0 0', maxWidth: 680 }}>
        La conducta de cada segundo la asigna el sistema de forma automática y no es 100 % exacta: conviene revisarla con «Revisar segundo a segundo».
      </p>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 12 }}>
        <span className="tag tag-accent" style={{ whiteSpace: 'nowrap' }}>Nivel: {agrupado ? 'agrupado' : 'preciso'}</span>
        {revisado && <span className="tag tag-outline" style={{ whiteSpace: 'nowrap' }}>Con revisión humana</span>}
        {agrupado
          ? <span className="hint">Hay segundos de <strong>conducta activa</strong>: el espécimen no estaba inmóvil, pero el sistema no decidió entre nado activo y escalamiento.</span>
          : <span className="hint">Ningún segundo quedó como conducta activa: cada uno se clasificó como nado activo, inmovilidad o escalamiento.</span>}
      </div>
    </div>
  )
}
