import React from 'react'

// Tabla del sistema (cabecera en versalitas y reglas entre filas).
export default function Table({ children, style }) {
  return <table className="table" style={style}>{children}</table>
}
