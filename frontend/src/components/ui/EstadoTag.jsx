import React from 'react'

// Estados de un trabajo de análisis (JobStatus del backend) tal como se ven en el v2.
export const ESTADOS = {
  QUEUED: { label: 'En cola', cls: 'tag-outline', bg: 'transparent', fg: 'var(--color-text)', fill: 'var(--color-accent-200)' },
  RUNNING: { label: 'Procesando', cls: 'tag-accent', bg: 'var(--color-accent-100)', fg: 'var(--color-accent-700)', fill: 'var(--color-accent)' },
  DONE: { label: 'Completado', cls: 'tag-neutral', bg: 'var(--color-neutral-200)', fg: 'var(--color-text)', fill: 'var(--color-text)' },
  FAILED: { label: 'Error', cls: 'tag-accent', bg: 'var(--color-accent-800)', fg: 'var(--color-bg)', fill: 'repeating-linear-gradient(135deg, var(--color-accent-800) 0 3px, var(--color-bg) 3px 6px)' },
}

export default function EstadoTag({ status }) {
  const e = ESTADOS[status]
  if (!e) return null
  return <span className={`tag ${e.cls}`} style={{ whiteSpace: 'nowrap', background: e.bg, color: e.fg }}>{e.label}</span>
}
