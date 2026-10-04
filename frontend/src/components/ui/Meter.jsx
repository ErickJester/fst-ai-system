import React from 'react'

// Barra de avance. value en porcentaje (0–100).
export default function Meter({ value, height = 6, color, label }) {
  const pct = Math.max(0, Math.min(100, value))
  return (
    <div className="meter" style={{ height }} role="meter" aria-valuemin={0} aria-valuemax={100} aria-valuenow={pct} aria-label={label}>
      <div style={{ width: `${pct}%`, background: color }} />
    </div>
  )
}
