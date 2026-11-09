import api from './api'
import { USE_MOCKS, PARTES_REALES } from './config'
import { reply } from './mocks/delay'
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

// Con qué modelo analiza el worker (modo desarrollador): GET /dev/modelo-actual, con sesión. Responde la identidad del
// modelo y su texto, o { archivo: null, ruta } si no se encontró, o { archivo: null, motivo } si el worker no informó.
// Solo se pide con el modo desarrollador prendido (la pantalla de carga decide).
const MODELO_DE_EJEMPLO = {
  archivo: 'fst_cnn_seg.pt',
  version: '5.0.0',
  sha256: '480c5803998562f5a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0',
  tamanoMB: 119.6,
  modificado: '2026-10-19T10:00:00-06:00',
  actualizado: '2026-11-08T12:00:00-06:00',
  texto: 'fst_cnn_seg.pt · red 5.0.0 · SHA-256 480c5803998562f5 · 119.6 MB · archivo del 2026-10-19',
}

export function getModeloActual() {
  if (USE_MOCKS && PARTES_REALES.length === 0) return reply(MODELO_DE_EJEMPLO)
  return api.get('/dev/modelo-actual').then((r) => r.data)
}

