import React from 'react'

// Encabezado de pasos de un asistente (2b). current es 1-based.
export default function StepHeader({ steps, current }) {
  return (
    <div className="steps" style={{ marginBottom: 28 }}>
      {steps.map((label, i) => {
        const n = i + 1
        const cls = n === current ? 'on' : n > current ? 'todo' : ''
        return (
          <div key={label} className={cls} aria-current={n === current ? 'step' : undefined}>
            <div className="k">Paso {n}</div>
            <div className="nd" style={{ fontSize: 13, marginTop: 3, color: n === current ? undefined : 'var(--muted)' }}>{label}</div>
          </div>
        )
      })}
    </div>
  )
}
