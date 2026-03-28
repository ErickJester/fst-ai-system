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

  useEffect(() => {
    if (!enabled) return

    poll()
    const id = setInterval(poll, interval)
    return () => clearInterval(id)
  }, [enabled, interval, poll])

  return { data, error }
}
