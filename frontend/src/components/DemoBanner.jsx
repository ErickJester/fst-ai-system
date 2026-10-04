import React from 'react'
import { version } from '../../package.json'

// Franja fija arriba de todas las pantallas mientras el sistema corre con datos
// de ejemplo. La versión sale de package.json.
export default function DemoBanner() {
  return (
    <div className="demo-banner" role="status">
      <span>Modo demo</span>
      <span>·</span>
      <span style={{ textTransform: 'none' }}>v{version}</span>
    </div>
  )
}
