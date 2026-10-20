import api from './api'
import { USE_MOCKS, PARTES_REALES } from './config'
import { version as versionDelPaquete } from '../../package.json'

// Modo desarrollador y versión del sistema, según el backend: GET /health (público) →
// { estado, modoDesarrollador, version }. Se pregunta UNA sola vez al cargar la app (ModoProvider) y se recuerda, así
// que cambiar MODO_DESARROLLADOR en el .env y reiniciar la api basta para prender o apagar las herramientas de
// desarrollo, sin recompilar la web.
// Con datos de ejemplo (sin backend) y si el backend no contesta, el modo queda apagado.
const APAGADO = { modoDesarrollador: false, version: versionDelPaquete }

let pedido = null

export function getModo() {
  if (pedido) return pedido
  if (USE_MOCKS && PARTES_REALES.length === 0) {
    pedido = Promise.resolve(APAGADO)
    return pedido
  }
  pedido = api.get('/health')
    .then((r) => ({
      modoDesarrollador: r.data?.modoDesarrollador === true,
      version: r.data?.version && r.data.version !== 'desconocida' ? r.data.version : versionDelPaquete,
    }))
    .catch(() => {
      pedido = null // no se recuerda un fallo: la próxima vez se vuelve a preguntar
      return APAGADO
    })
  return pedido
}
