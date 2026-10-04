import { useState } from 'react'

// Error de un formulario: mensaje y campo marcado. fail(campo, msg) lo muestra y,
// si hay ref para ese campo, le pasa el foco. clase(campo, base) añade « error» al input marcado.
// refs: { campo: useRef() } de los inputs que reciben el foco; se devuelven para asignarlas.
export function useErrorDeCampo(refs = {}) {
  const [err, setErr] = useState({ msg: '', campo: null })

  function fail(campo, msg) {
    setErr({ campo, msg })
    if (campo) refs[campo]?.current?.focus()
  }

  const clase = (campo, base = 'input') => base + (err.campo === campo ? ' error' : '')

  return { err, fail, clase, refs }
}
