import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'

// GET /experiments/:clave/groups/:gid/batches/:letra/results?day= → por espécimen (s),
// línea de tiempo, nivel y calidad del análisis (2f)
export async function getBatchResults(clave, gid, letra, dia = 'DAY2') {
  if (USE_MOCKS) {
    const r = db.batchResults[[clave, gid, letra, dia].join('/')]
    if (r) return reply(r)
    // Sin backend solo hay resultados de una tanda. Las demás tandas terminadas muestran
    // esos mismos números con su grupo, letra y especímenes, para poder recorrerlas.
    const exp = db.experimentDetail[clave]
    const g = exp?.grupos.find((x) => x.id === gid)
    const t = g?.tandas.find((x) => x.letra === letra)
    if (!t || t.estado !== 'DONE' || dia !== 'DAY2') return fail(404, 'Esta tanda todavía no tiene resultados.')
    const base = db.batchResults['EXP-2026-02/G-02/A/DAY2']
    return reply({
      ...base,
      experimento: { clave: exp.clave, titulo: exp.titulo },
      grupo: { id: g.id, nombre: g.nombre, tipo: g.tipo, tratamiento: g.tratamiento },
      letra,
      especimenes: base.especimenes.slice(0, t.n_cilindros).map((e, i) => ({ ...e, especimen: t.desde + i })),
      linea_tiempo: { ...base.linea_tiempo, especimen: t.desde },
    })
  }
  return (await api.get(`/experiments/${clave}/groups/${gid}/batches/${letra}/results`, { params: { day: dia } })).data
}

// GET /experiments/:clave/group-comparison → inmovilidad media ± DE por grupo, Día 2
export async function getGroupComparison(clave) {
  if (USE_MOCKS) return reply(db.groupComparison[clave] || [])
  return (await api.get(`/experiments/${clave}/group-comparison`)).data
}
