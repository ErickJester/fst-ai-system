import * as db from './data'

// Sin backend, la fila del experimento en la lista (2a) se recalcula a partir de sus
// grupos y tandas, como lo haría el servidor.
export function resumir(clave) {
  const exp = db.experimentDetail[clave]
  const fila = db.experiments.find((e) => e.clave === clave)
  if (!exp || !fila) return
  const tandas = exp.grupos.flatMap((g) => g.tandas)
  const total = tandas.length
  const cargados = tandas.filter((t) => t.estado !== 'SIN_VIDEO').length
  const listos = tandas.filter((t) => t.estado === 'DONE').length
  Object.assign(fila, {
    n_grupos: exp.grupos.length,
    n_especimenes: exp.grupos.reduce((a, g) => a + g.n_especimenes, 0),
    tratamientos: [...new Set(exp.grupos.map((g) => g.tratamiento))].join(' '),
    videos_dia2_total: total,
    videos_dia2_cargados: cargados,
    videos_dia2_listos: listos,
    estado: !total || cargados < total ? 'CARGA_INCOMPLETA' : listos === total ? 'CONCLUIDO' : 'EN_ANALISIS',
    retencion_dias: fila.retencion_dias ?? (total ? 30 : null),
  })
}
