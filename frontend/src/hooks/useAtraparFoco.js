import { useEffect } from 'react'

const ENFOCABLES = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

// Mientras el diálogo de `ref` está activo, Tab y Mayús+Tab no salen de él (dan la
// vuelta). Al cerrarlo, el foco vuelve a donde estaba antes de abrirlo, si sigue en la página.
export function useAtraparFoco(ref, activo) {
  useEffect(() => {
    if (!activo) return
    const previo = document.activeElement

    function onKey(e) {
      if (e.key !== 'Tab' || !ref.current) return
      const els = [...ref.current.querySelectorAll(ENFOCABLES)]
      if (!els.length) return
      const primero = els[0]
      const ultimo = els[els.length - 1]
      const dentro = ref.current.contains(document.activeElement)
      if (!dentro || (e.shiftKey && document.activeElement === primero)) {
        e.preventDefault()
        ;(e.shiftKey ? ultimo : primero).focus()
      } else if (!e.shiftKey && document.activeElement === ultimo) {
        e.preventDefault()
        primero.focus()
      }
    }

    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      if (previo?.isConnected) previo.focus()
    }
  }, [activo, ref])
}
