import React, { useEffect, useRef, useState } from 'react'
import { FieldError } from './ui'
import { deleteTanda } from '../services/experiments'
import { mensajeError } from '../services/api'
import { useAtraparFoco } from '../hooks/useAtraparFoco'
import { btnLeft } from '../lib/estilos'

// Solo quien creó el experimento o un Administrador puede eliminar una tanda (como el experimento, R-08). exp trae
// creador_id; sin id de tanda (datos de ejemplo) no se ofrece.
export const puedeEliminarTanda = (user, exp, tanda) => tanda.id_tanda != null && (user.admin || exp.creador_id === user.id)

// Diálogo para eliminar una tanda, desde el detalle del grupo: pide confirmación explícita. El foco empieza en
// «Cancelar», la opción segura. Muestra tal cual el 403 y el 409 del servidor (por ejemplo, si el análisis está
// procesando o en pausa). Regla nueva (no está en la guía): sirve para dejar libre la letra si la tanda se subió mal.
export default function EliminarTanda({ clave, grupo, tanda, onCerrar, onEliminada }) {
  const [borrando, setBorrando] = useState(false)
  const [error, setError] = useState('')
  const cancelarRef = useRef(null)
  const dlgRef = useRef(null)
  const cerrar = useRef(onCerrar)
  useEffect(() => { cerrar.current = onCerrar })

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
      await deleteTanda(clave, tanda.id_tanda)
      onEliminada(tanda)
    } catch (err) {
      setError(mensajeError(err, 'No se pudo eliminar la tanda.'))
      setBorrando(false)
    }
  }

  return (
    <div className="dialog-backdrop" onClick={(e) => { if (e.target === e.currentTarget) onCerrar() }}>
      <form ref={dlgRef} className="dialog dlg-del" noValidate role="dialog" aria-modal="true" aria-labelledby="dlgTanda" onSubmit={eliminar}>
        <div className="dialog-title" id="dlgTanda">Eliminar tanda</div>
        <div className="dialog-body" style={{ fontSize: 12.5, lineHeight: 1.6 }}>
          ¿Eliminar la Tanda {tanda.letra} de «{grupo}»? Se borrarán el video del Día 1, el del Día 2 y sus resultados. No se puede deshacer.
        </div>
        <FieldError msg={error} style={{ marginBottom: 12 }} />
        <div className="dialog-actions" style={btnLeft}>
          <button type="submit" className="btn btn-primary" disabled={borrando}>Eliminar tanda</button>
          <button ref={cancelarRef} type="button" className="btn btn-secondary" onClick={onCerrar}>Cancelar</button>
        </div>
      </form>
    </div>
  )
}
