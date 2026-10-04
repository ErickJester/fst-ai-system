import React from 'react'
import { ESTADO } from '../data/mock'

// Segmentado (.seg): opciones [{ v, label }], una activa.
export function Seg({ options, value, onChange, id }) {
  return (
    <span className="seg" id={id} style={{ display: 'inline-flex' }}>
      {options.map((o) => (
        <button
          key={o.v}
          type="button"
          className={'seg-opt' + (o.v === value ? ' on' : '')}
          aria-pressed={o.v === value}
          onClick={() => onChange(o.v)}
        >
          {o.label}
        </button>
      ))}
    </span>
  )
}

// Etiqueta del estado de una tanda: En cola, Procesando, Completado o Error.
export function EstadoTag({ estado }) {
  const e = ESTADO[estado]
  return (
    <span className={'tag ' + e.cls} style={{ whiteSpace: 'nowrap', background: e.bg, color: e.fg }}>
      {estado}
    </span>
  )
}

// Mensaje de error bajo un formulario (vacío = oculto).
export function FieldError({ msg, style }) {
  if (!msg) return null
  return <div className="field-error" style={style}>{msg}</div>
}
