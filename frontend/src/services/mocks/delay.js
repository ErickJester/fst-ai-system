import { guardarDatos } from './data'

// En las pruebas (npm test) no se simula la espera de la red.
const SIN_ESPERA = import.meta.env.MODE === 'test'

// Simula la latencia de la red y entrega copias, para que las pantallas no muten los datos.
// Cada respuesta guarda los datos de ejemplo: así sobrevive lo que cambió la petición.
export function reply(value, ms = 120) {
  guardarDatos()
  return new Promise((resolve) => setTimeout(() => resolve(structuredClone(value)), SIN_ESPERA ? 0 : ms))
}

export function fail(status, message, ms = 120) {
  return new Promise((_, reject) =>
    setTimeout(() => reject(Object.assign(new Error(message), { response: { status, data: { error: message } } })), SIN_ESPERA ? 0 : ms)
  )
}
