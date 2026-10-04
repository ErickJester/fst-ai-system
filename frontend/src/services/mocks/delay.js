// Simula la latencia de la red y entrega copias, para que las pantallas no muten los datos.
export function reply(value, ms = 120) {
  return new Promise((resolve) => setTimeout(() => resolve(structuredClone(value)), ms))
}

export function fail(status, message, ms = 120) {
  return new Promise((_, reject) =>
    setTimeout(() => reject(Object.assign(new Error(message), { response: { status, data: { error: message } } })), ms)
  )
}
