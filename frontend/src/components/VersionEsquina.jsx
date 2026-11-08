import React from 'react'
import { version as versionDelPaquete } from '../../package.json'
import { useModo } from '../contexts/ModoContext'

// Versión del sistema, siempre visible, fija en la esquina inferior derecha de TODA ventana (también el login y el
// primer acceso): «v1.1.0». Es la que contesta el backend en GET /health (la del sistema que está corriendo); si no
// contesta o todavía no llega, la de este paquete. No recibe clics: nunca tapa un botón. A diferencia de la franja roja
// de arriba (BannerModoDesarrollador), esta no depende del modo desarrollador.
export default function VersionEsquina() {
  const { version } = useModo()
  // <footer> fuera de <main> es la región «pie de página» (contentinfo): sin ella, un texto suelto rompe la regla de
  // accesibilidad que exige que todo el contenido esté dentro de una región.
  return <footer className="version-esquina" aria-label="Versión del sistema">v{version || versionDelPaquete}</footer>
}
