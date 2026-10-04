import { useEffect, useState } from 'react'
import { AVISO_KEY } from '../services/config'

// Aviso que deja api.js antes de redirigir ('sesion-caducada', 'sin-permiso').
// Devuelve true si es el del tipo pedido y lo borra, para que se vea una sola vez.
// Se borra en un efecto y no al leerlo: en desarrollo React inicializa el estado dos veces.
export function useAviso(tipo) {
  const [ver] = useState(() => {
    try {
      return sessionStorage.getItem(AVISO_KEY) === tipo
    } catch {
      return false
    }
  })
  useEffect(() => {
    if (!ver) return
    try { sessionStorage.removeItem(AVISO_KEY) } catch { /* sin almacenamiento */ }
  }, [ver])
  return ver
}
