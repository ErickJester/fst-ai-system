import React, { useEffect, useRef, useState } from 'react'
import { FieldError } from './ui'
import { deleteExperiment } from '../services/experiments'
import { mensajeError } from '../services/api'
import { useAtraparFoco } from '../hooks/useAtraparFoco'
import { btnLeft } from '../lib/estilos'

// Solo quien creó el experimento o un Administrador puede eliminarlo (R-08).
export const puedeEliminar = (user, exp) => user.admin || exp.creador_id === user.id

// Diálogo para eliminar un experimento, desde la lista o desde el detalle. Según D-63
// solo pide confirmación, sin nombre ni contraseña. El foco empieza en «Cancelar», la
// opción segura, para que un Enter accidental no borre nada.
export default function EliminarExperimento({ exp, onCerrar, onEliminado }) {
  const [borrando, setBorrando] = useState(false)
  const [error, setError] = useState('')
  const cancelarRef = useRef(null)
  const dlgRef = useRef(null)
  const cerrar = useRef(onCerrar)
  useEffect(() => { cerrar.current = onCerrar })

  // Antes del efecto que enfoca «Cancelar», para recordar el botón que abrió el diálogo.
  useAtraparFoco(dlgRef, true)

  useEffect(() => {
    cancelarRef.current?.focus()
    const onKey = (e) => { if (e.key === 'Escape') cerrar.current() }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [])

  async function eliminar(e) {
    e.preventDefault()
    if (borrando) return
    setBorrando(true)
    setError('')
    try {
      await deleteExperiment(exp.clave)
      onEliminado(exp)
    } catch (err) {
      // 403: no es quien lo creó ni Administrador. 409: hay un análisis procesando o en pausa.
      setError(mensajeError(err, 'No se pudo eliminar el experimento.'))
      setBorrando(false)
    }
  }

  return (
    <div className="dialog-backdrop" onClick={(e) => { if (e.target === e.currentTarget) onCerrar() }}>
      <form ref={dlgRef} className="dialog dlg-del" noValidate role="dialog" aria-modal="true" aria-labelledby="dlgT" onSubmit={eliminar}>
        <div className="dialog-title" id="dlgT">Eliminar experimento</div>
        <div className="dialog-body" style={{ fontSize: 12.5, lineHeight: 1.6 }}>
          ¿Eliminar «{exp.titulo}»? Borra grupos, tandas, videos, análisis y reportes. Es permanente e irreversible.
        </div>
        <FieldError msg={error} style={{ marginBottom: 12 }} />
        <div className="dialog-actions" style={btnLeft}>
          <button type="submit" className="btn btn-primary" disabled={borrando}>Eliminar experimento</button>
          <button ref={cancelarRef} type="button" className="btn btn-secondary" onClick={onCerrar}>Cancelar</button>
        </div>
      </form>
    </div>
  )
}
