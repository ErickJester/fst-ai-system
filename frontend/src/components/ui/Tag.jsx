import React from 'react'

// Etiqueta pequeña del sistema Modernist. variant: 'accent' | 'neutral' | 'outline'
export default function Tag({ variant = 'neutral', style, children }) {
  return <span className={`tag tag-${variant}`} style={{ whiteSpace: 'nowrap', ...style }}>{children}</span>
}
