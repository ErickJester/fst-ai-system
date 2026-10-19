import { useEffect, useState, useCallback, useRef } from 'react'

// Cada cuánto se vuelven a consultar los datos de una pantalla: más seguido mientras
// haya análisis activos (en cola o procesando), como pide el documento (3 a 5 s).
export const CADA_ACTIVO_MS = 5000
export const CADA_INACTIVO_MS = 30000

// Ejecuta una función asíncrona al montar y cada vez que cambian `deps`.
// Devuelve { data, error, loading, reload }. reload({ silencioso: true }) vuelve a
// consultar sin pasar por «cargando» ni borrar lo que ya se muestra.
export function useAsync(fn, deps = []) {
  const [state, setState] = useState({ data: null, error: null, loading: true })
  const [tick, setTick] = useState(0)
  const silencioso = useRef(false)

  useEffect(() => {
    let alive = true
    const callado = silencioso.current
    silencioso.current = false
    if (!callado) setState((s) => ({ ...s, loading: true, error: null }))
    fn()
      .then((data) => alive && setState({ data, error: null, loading: false }))
      // Si falla una consulta silenciosa se conservan los datos: se reintenta en la siguiente.
      .catch((error) => alive && !callado && setState({ data: null, error, loading: false }))
    return () => { alive = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick])

  const reload = useCallback((opciones) => {
    silencioso.current = !!opciones?.silencioso
    setTick((t) => t + 1)
  }, [])
  return { ...state, reload }
}

// useAsync que se vuelve a consultar solo, en silencio. intervaloSegun(data) da cada
// cuántos milisegundos (por ejemplo, según haya análisis activos), o null para no repetir.
export function useAsyncVivo(fn, deps, intervaloSegun) {
  const r = useAsync(fn, deps)
  const ms = r.data ? intervaloSegun(r.data) : null
  const { reload } = r
  useEffect(() => {
    if (!ms) return
    const id = setInterval(() => reload({ silencioso: true }), ms)
    return () => clearInterval(id)
  }, [ms, reload])
  return r
}
