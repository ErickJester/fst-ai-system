import React from 'react'
import { Link } from 'react-router-dom'
import Topbar, { Brand } from '../Topbar'

// Aviso cuando todavía no hay tandas con el Día 2 analizado.
export default function SinResultados({ clave }) {
  return (
    <div className="app">
      <Topbar><Brand /></Topbar>
      <div className="page">
        <h3 style={{ margin: '0 0 8px' }}>Todavía no hay resultados</h3>
        <p className="lead" style={{ marginBottom: 16, maxWidth: 560 }}>
          Los resultados de una tanda aparecen cuando termina el análisis de su video de Día 2.
        </p>
        <div style={{ display: 'flex', gap: 8 }}>
          <Link className="btn btn-primary" to={`/experimentos/${clave}`}>Volver al experimento</Link>
          <Link className="btn btn-secondary" to={'/analisis?experimento=' + clave}>Ver progreso del análisis</Link>
        </div>
      </div>
    </div>
  )
}
