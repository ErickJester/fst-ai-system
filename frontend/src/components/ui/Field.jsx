import React from 'react'

// Campo con etiqueta y mensaje de error. El control va como hijo (con clase "input").
export default function Field({ label, htmlFor, error, style, children }) {
  return (
    <div className="field" style={style}>
      {label && <label htmlFor={htmlFor}>{label}</label>}
      {children}
      {error && <div className="field-error" role="alert">{error}</div>}
    </div>
  )
}
