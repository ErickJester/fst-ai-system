import React from 'react'
import { useModo } from '../contexts/ModoContext'
import { btnLeft } from '../lib/estilos'

// «Salida del modelo»: descarga lo que decidió el modelo en cada segundo, TAL CUAL (un CSV por tanda o, con el grupo
// completo, un ZIP). Es una herramienta de desarrollo, sin documentar: solo se ve con el modo desarrollador prendido,
// porque trae las probabilidades del modelo y NO las correcciones de la revisión. No se llama «Segundos» para no
// confundirla con los segundos del producto (la revisión y los resultados por segundo). Sin el modo no pinta nada
// (ni un hueco).
export default function BotonSalidaModelo({ grupoCompleto, ocupado, onClick }) {
  const { modoDesarrollador } = useModo()
  if (!modoDesarrollador) return null
  return (
    <button type="button" className="btn btn-secondary" style={btnLeft} disabled={ocupado} onClick={onClick}
      title="Lo que decidió el modelo en cada segundo, sin correcciones (formato fst_auto)">
      {grupoCompleto ? 'Salida del modelo (ZIP)' : 'Salida del modelo (CSV)'}
    </button>
  )
}
