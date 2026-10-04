import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'

// GET /experiments/:clave/groups/:gid → grupo con sus tandas y videos (pantalla 2d)
export async function getGroup(clave, gid) {
  if (USE_MOCKS) {
    const exp = db.experimentDetail[clave]
    const grupo = exp?.grupos.find((g) => g.id === gid)
    if (!grupo) return fail(404, 'No existe ese grupo.')
    return reply({ experimento: { clave: exp.clave, titulo: exp.titulo }, ...grupo })
  }
  return (await api.get(`/experiments/${clave}/groups/${gid}`)).data
}
