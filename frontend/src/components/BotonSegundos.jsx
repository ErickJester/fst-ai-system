import React from 'react'
import { useModo } from '../contexts/ModoContext'
import { btnLeft } from '../lib/estilos'

// «Segundos»: descarga la salida por segundo del modelo TAL CUAL (un CSV por tanda o, con el grupo completo, un ZIP). Es
// una herramienta de desarrollo: solo se ve con el modo desarrollador prendido, porque trae las probabilidades del
// modelo y NO las correcciones de la revisión. Sin el modo no pinta nada (ni un hueco).
export default function BotonSegundos({ grupoCompleto, ocupado, onClick }) {
  const { modoDesarrollador } = useModo()
  if (!modoDesarrollador) return null
  return (
    <button type="button" className="btn btn-secondary" style={btnLeft} disabled={ocupado} onClick={onClick}
      title="Salida por segundo del modelo, tal cual (solo modo desarrollador): sin las correcciones de la revisión">
      {grupoCompleto ? 'Segundos (ZIP)' : 'Segundos (CSV)'}
    </button>
  )
}
