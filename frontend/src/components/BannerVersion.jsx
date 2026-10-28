import React from 'react'
import { version as versionDelPaquete } from '../../package.json'
import { useModo } from '../contexts/ModoContext'

// Franja roja fija arriba de TODA ventana (también el login y el primer acceso) mientras el sistema corre en modo
// desarrollador, con solo la versión: «v0.22.0». Va en el flujo de la página, no encima: la empuja hacia abajo y no tapa nada. Si
// también está la franja del modo demo, se apilan (ver app.css).
export default function BannerVersion() {
  const { modoDesarrollador, version } = useModo()
  if (!modoDesarrollador) return null
  return <div className="version-banner" role="status">v{version || versionDelPaquete}</div>
}
