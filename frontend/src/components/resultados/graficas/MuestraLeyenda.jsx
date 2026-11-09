import React, { useId } from 'react'
import Patrones, { patronId } from './Patrones'

// Una muestra de leyenda: el color de la barra y, si la tiene, su patrón. Así la leyenda no depende solo del color
// (los grupos y las conductas se distinguen también en blanco y negro). Usa el mismo patrón que la gráfica, con un
// prefijo propio, porque cada muestra es un <svg> aparte.
export default function MuestraLeyenda({ color, patron, borde = 'var(--color-divider)' }) {
  const prefijo = useId().replace(/[^a-zA-Z0-9]/g, '')
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true" focusable="false" style={{ flex: 'none', border: `1px solid ${borde}` }}>
      <Patrones prefijo={prefijo} />
      <rect width="12" height="12" fill={color} />
      {patron && <rect width="12" height="12" fill={`url(#${patronId(prefijo, patron)})`} />}
    </svg>
  )
}
