import React, { useEffect, useRef } from 'react'

// Diálogo modal. Se cierra con Escape o al hacer clic fuera.
export default function Dialog({ open, title, onClose, children, actions }) {
  const ref = useRef(null)

  useEffect(() => {
    if (!open) return
    const onKey = (e) => { if (e.key === 'Escape') onClose?.() }
    document.addEventListener('keydown', onKey)
    ref.current?.querySelector('input, button, textarea, select')?.focus()
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null
  return (
    <div className="dialog-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose?.() }}>
      <div className="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title" ref={ref} style={{ width: 'min(520px, 100%)' }}>
        <div className="dialog-title" id="dialog-title">{title}</div>
        {children}
        {actions && <div className="dialog-actions" style={{ justifyContent: 'flex-start' }}>{actions}</div>}
      </div>
    </div>
  )
}
