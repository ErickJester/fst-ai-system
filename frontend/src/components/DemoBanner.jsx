import React from 'react'
import { version } from '../../package.json'
import { USE_MOCKS, TOKEN_KEY } from '../services/config'
import { reiniciarDatos } from '../services/mocks/data'

// Franja fija arriba de todas las pantallas mientras el sistema corre con datos
// de ejemplo. La versión sale de package.json.
export default function DemoBanner() {
  // Vuelve a los datos de ejemplo originales. La sesión se cierra porque la cuenta
  // en uso puede no existir en ellos (por ejemplo, una creada en Administración).
  function reiniciar() {
    if (!window.confirm('¿Reiniciar los datos de ejemplo? Se pierden las cuentas, experimentos y videos agregados, y se cierra la sesión.')) return
    reiniciarDatos()
    try {
      sessionStorage.removeItem(TOKEN_KEY)
      sessionStorage.removeItem('fst.user')
    } catch {
      /* sin almacenamiento */
    }
    window.location.href = '/login'
  }

  return (
    <div className="demo-banner" role="status">
      <span>Modo demo</span>
      <span>·</span>
      <span style={{ textTransform: 'none' }}>v{version}</span>
      {USE_MOCKS && (
        <>
          <span>·</span>
          <button type="button" onClick={reiniciar}
            style={{ background: 'none', border: 0, padding: 0, color: 'inherit', font: 'inherit', letterSpacing: 'inherit', textTransform: 'inherit', textDecoration: 'underline', cursor: 'pointer' }}>
            Reiniciar datos
          </button>
        </>
      )}
    </div>
  )
}
