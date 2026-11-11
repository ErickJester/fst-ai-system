import React from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { textoVideo, mmss, enlaceResultados } from '../../lib/fst'
import { fechaYHora, nombreGrupo } from './constantes'

// Título de la vista, datos del análisis, el selector de grupo y de tanda (o el grupo completo), y las etiquetas de nivel.
// Cambiar grupo, tanda o «Grupo completo» conserva la vista (?vista=). Solo ofrece los grupos con alguna tanda lista.
export default function CabeceraResultados({ clave, gid, r, rs, grupos, vista, grupoCompleto, analizado, tiempos, completadas, nCompletos, agrupado, revisado }) {
  const navegar = useNavigate()
  const enlaceGrupo = `/experimentos/${clave}/resultados?grupo=${gid}&vista=${vista}`
  const gruposConTandas = grupos.filter((g) => g.tandas.some((t) => t.estado === 'DONE'))

  // Al cambiar de grupo: si se veía el grupo completo, se queda en el grupo completo; si no, abre su primera tanda lista.
  function cambiarGrupo(nuevo) {
    const g = gruposConTandas.find((x) => String(x.id) === nuevo)
    if (!g) return
    if (grupoCompleto) return navegar(`/experimentos/${clave}/resultados?grupo=${g.id}&vista=${vista}`)
    const primera = g.tandas.find((t) => t.estado === 'DONE')
    navegar(enlaceResultados(clave, g.id, primera.letra, vista))
  }

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
      {/* Elegir grupo y tanda (o el grupo completo): solo dentro del detalle (RF-19, D-93). */}
      <nav aria-label="Elegir grupo y tanda" className="no-print" style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 8, marginTop: 12 }}>
        <label htmlFor="selector-grupo" className="k" style={{ marginRight: 4 }}>Grupo</label>
        <select id="selector-grupo" className="input" value={String(gid)} onChange={(e) => cambiarGrupo(e.target.value)}
          style={{ width: 200, padding: '4px 6px', fontSize: 12 }}>
          {gruposConTandas.map((g) => <option key={g.id} value={String(g.id)}>{nombreGrupo(g)}</option>)}
        </select>
        <span className="k" style={{ marginLeft: 6 }}>Tanda</span>
        {completadas.map((t) => {
          const on = !grupoCompleto && t.letra === r.letra
          return (
            <Link key={t.letra} to={enlaceResultados(clave, gid, t.letra, vista)} className={'tab' + (on ? ' on' : '')} aria-current={on ? 'page' : undefined}
              style={{ whiteSpace: 'nowrap', marginBottom: 0, padding: '6px 13px', border: '1px solid ' + (on ? 'var(--color-accent)' : 'var(--color-divider)'), textDecoration: 'none' }}>
              Tanda {t.letra} · {t.n_cilindros} especímenes
            </Link>
          )
        })}
        <Link to={enlaceGrupo} className={'tab' + (grupoCompleto ? ' on' : '')} aria-current={grupoCompleto ? 'page' : undefined}
          style={{ whiteSpace: 'nowrap', marginBottom: 0, padding: '6px 13px', border: '1px solid ' + (grupoCompleto ? 'var(--color-accent)' : 'var(--color-divider)'), textDecoration: 'none' }}>
          Grupo completo · {nCompletos} especímenes
        </Link>
      </nav>
      <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 12, marginTop: 12 }}>
        {revisado && <span className="tag tag-outline" style={{ whiteSpace: 'nowrap' }}>Con revisión humana</span>}
        {agrupado
          ? <span className="hint">Hay segundos de <strong>conducta activa</strong>: el espécimen no estaba inmóvil, pero el sistema no decidió entre nado activo y escalamiento.</span>
          : <span className="hint">Ningún segundo quedó como conducta activa: cada uno se clasificó como nado activo, inmovilidad o escalamiento.</span>}
      </div>
    </div>
  )
}
