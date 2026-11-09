import React from 'react'

// Patrones de relleno de una gráfica. Viven en el <defs> del mismo <svg>, para que el PNG que se descarga
// los conserve. Las líneas van en el color de fondo sobre el relleno del grupo o de la conducta.
// El id lleva el prefijo de la gráfica: con «Ver varias» hay más de una en la misma página.
export function patronId(prefijo, nombre) {
  return `${prefijo}-${nombre}`
}

export default function Patrones({ prefijo }) {
  return (
    <defs>
      <pattern id={patronId(prefijo, 'rayado')} patternUnits="userSpaceOnUse" width="6" height="6" patternTransform="rotate(45)">
        <line x1="0" y1="0" x2="0" y2="6" stroke="var(--color-bg)" strokeWidth="2" />
      </pattern>
      <pattern id={patronId(prefijo, 'punteado')} patternUnits="userSpaceOnUse" width="6" height="6">
        <circle cx="3" cy="3" r="1.4" fill="var(--color-bg)" />
      </pattern>
      <pattern id={patronId(prefijo, 'vertical')} patternUnits="userSpaceOnUse" width="5" height="5">
        <line x1="2.5" y1="0" x2="2.5" y2="5" stroke="var(--color-bg)" strokeWidth="1.5" />
      </pattern>
      <pattern id={patronId(prefijo, 'horizontal')} patternUnits="userSpaceOnUse" width="5" height="5">
        <line x1="0" y1="2.5" x2="5" y2="2.5" stroke="var(--color-bg)" strokeWidth="1.5" />
      </pattern>
    </defs>
  )
}
