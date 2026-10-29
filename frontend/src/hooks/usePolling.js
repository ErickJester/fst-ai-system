import { useEffect, useState, useRef, useCallback } from 'react'

export function usePolling(fetchFn, interval = 2000, enabled = true) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const savedFn = useRef(fetchFn)

  useEffect(() => {
    savedFn.current = fetchFn
  }, [fetchFn])

  const poll = useCallback(async () => {
    try {
      const result = await savedFn.current()
      setData(result)
      setError(null)
      return result
    } catch (err) {
      setError(err)
      return null
    }
  }, [])

  // Con la pestaña oculta no se consulta; al volver a verla, se consulta en seguida y se retoma el intervalo.
  useEffect(() => {
    if (!enabled) return undefined
    let id = null
    const arrancar = () => { if (id == null) id = setInterval(poll, interval) }
    const parar = () => { clearInterval(id); id = null }
    const alCambiarVisibilidad = () => {
      if (document.visibilityState === 'hidden') {
        parar()
      } else {
        poll()
        arrancar()
      }
    }

    if (document.visibilityState !== 'hidden') {
      poll()
      arrancar()
    }
    document.addEventListener('visibilitychange', alCambiarVisibilidad)
    return () => {
      parar()
      document.removeEventListener('visibilitychange', alCambiarVisibilidad)
    }
  }, [enabled, interval, poll])

  return { data, error }
}
