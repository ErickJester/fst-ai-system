import React from 'react'
import { useModo } from '../contexts/ModoContext'

// Franja roja fija arriba de TODA ventana (también el login y el primer acceso) mientras el sistema corre en modo
// desarrollador, con solo el aviso «Modo desarrollador». La versión ya no va aquí: sale siempre abajo a la derecha
// (VersionEsquina). Va en el flujo de la página, no encima: la empuja hacia abajo y no tapa nada. Si también está la
// franja del modo demo, se apilan (ver app.css).
export default function BannerModoDesarrollador() {
  const { modoDesarrollador } = useModo()
  if (!modoDesarrollador) return null
  return <div className="modo-banner" role="status">Modo desarrollador</div>
}
