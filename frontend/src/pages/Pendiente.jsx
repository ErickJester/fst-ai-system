import React from 'react'

// Aviso de pantalla en construcción. Se quita al construir la pantalla en su fase.
export default function Pendiente({ pantalla, fase }) {
  return (
    <div className="note-bar" style={{ margin: '0 0 22px' }}>
      Pantalla {pantalla} del mockup v2 · se construye en la fase {fase} del plan de migración. Datos de ejemplo.
    </div>
  )
}
