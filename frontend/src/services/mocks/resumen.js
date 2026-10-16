import * as db from './data'
import { ESTADO } from '../../lib/contrato'

// Precedencia del documento (§4.8): error > cancelado > en pausa > procesando > en cola > completado.
const PRECEDENCIA = ['FAILED', 'CANCELED', 'PAUSED', 'RUNNING', 'QUEUED', 'DONE']

// Sin ningún Día 2 subido: «sin videos», o «en espera del Día 2» si ya hay algún Día 1 (D-83).
function estadoExperimento(tandas) {
  const conDia2 = tandas.filter((t) => t.estado !== 'SIN_VIDEO')
  if (!conDia2.length) return tandas.some((t) => t.dia1) ? 'ESPERA_DIA2' : 'SIN_VIDEOS'
  return PRECEDENCIA.find((e) => conDia2.some((t) => t.estado === e))
}

// Sin backend, la fila del experimento en la lista (2a, forma de §6.1) se recalcula a partir
// de sus grupos y tandas, como lo haría el servidor.
export function resumir(clave) {
  const exp = db.experimentDetail[clave]
  const fila = db.experiments.find((e) => e.idExperimento === clave)
  if (!exp || !fila) return
  const tandas = exp.grupos.flatMap((g) => g.tandas)
  const total = tandas.length
  Object.assign(fila, {
    nGrupos: exp.grupos.length,
    nEspecimenes: exp.grupos.reduce((a, g) => a + g.n_especimenes, 0),
    tratamientos: [...new Set(exp.grupos.map((g) => g.tratamiento))],
    videosDia2: {
      analizados: tandas.filter((t) => t.estado === 'DONE').length,
      cargados: tandas.filter((t) => t.estado !== 'SIN_VIDEO').length,
      total,
    },
    estado: ESTADO.aGuia[estadoExperimento(tandas)],
    diasRetencion: fila.diasRetencion ?? (total ? 30 : null),
  })
}
