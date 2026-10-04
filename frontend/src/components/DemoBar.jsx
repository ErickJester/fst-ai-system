import React from 'react'

// Barra para alternar los estados de una pantalla mientras no hay backend,
// igual que la barra "Estado demo" de los mockups. Se apaga con MOSTRAR_DEMO.
export const MOSTRAR_DEMO = true

export default function DemoBar({ states, value, onChange }) {
  if (!MOSTRAR_DEMO) return null
  return (
    <div className="demo-bar">
      {/* demo-* y dl/db: cada mockup nombra distinto estas clases */}
      <span className="demo-label dl">Estado demo:</span>
      {states.map((s) => (
        <button
          key={s.key}
          className={`demo-btn db ${value === s.key ? 'active' : ''}`}
          onClick={() => onChange(s.key)}
        >
          {s.label}
        </button>
      ))}
    </div>
  )
}
