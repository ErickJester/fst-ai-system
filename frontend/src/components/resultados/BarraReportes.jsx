import React from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../contexts/AuthContext'
import BotonSalidaModelo from '../BotonSalidaModelo'
import { enlaceRevision } from '../../lib/fst'
import { btnLeft } from '../../lib/estilos'
import { REPORTES } from './constantes'

// Acciones de la cabecera: «Revisar segundo a segundo» (quien subió el video o un Administrador, D-57), los botones
// de reporte y la salida del modelo (solo modo desarrollador).
export default function BarraReportes({ clave, r, grupoCompleto, reporte, pedirReporte, pedirSalidaModelo }) {
  const { user } = useAuth()
  return (
    <div style={{ display: 'flex', gap: 8 }} className="no-print">
      {/* Corregir los segundos: solo quien subió el video o un Administrador (D-57). */}
      {/* Con el video ya borrado (30 días, R-05) el botón queda en gris con el motivo. */}
      {!grupoCompleto && r.id_analisis && (user.admin || r.subido_por === user.id) && (r.video_disponible === false
        ? <button type="button" className="btn btn-secondary" aria-disabled="true" title="Video no disponible">Revisar segundo a segundo</button>
        : <Link className="btn btn-secondary" to={enlaceRevision(clave, r.id_analisis)}>Revisar segundo a segundo</Link>
      )}
      {Object.entries(REPORTES).map(([formato, x]) => (
        <button key={formato} type="button" className={'btn ' + (formato === 'pdf' ? 'btn-primary' : 'btn-secondary')} style={btnLeft}
          disabled={reporte?.pidiendo} onClick={() => pedirReporte(formato)}>{x.boton}</button>
      ))}
      <BotonSalidaModelo grupoCompleto={grupoCompleto} ocupado={reporte?.pidiendo} onClick={pedirSalidaModelo} />
    </div>
  )
}

// Lo que pasó al pedir un reporte: el error, o que todavía no hay backend y qué hará el botón.
export function AvisoReporte({ reporte, setReporte, vistaTexto }) {
  return (
    <div className="note-bar" role="status" style={{ display: 'flex', alignItems: 'flex-start', gap: 14, marginTop: 16 }}>
      <span style={{ flex: 1, lineHeight: 1.55 }}>
        {reporte.error || (
          <>
            <strong>Reporte {REPORTES[reporte.formato].boton.split(' ·')[0]} {reporte.formato === 'pdf' ? 'del experimento' : vistaTexto}.</strong> Lo prepara el sistema y aquí se descarga. Tendrá {REPORTES[reporte.formato].hara} Todavía no está disponible en el modo demo.
          </>
        )}
      </span>
      <button type="button" className="linkbtn" style={{ fontSize: 12 }} onClick={() => setReporte(null)}>Entendido</button>
    </div>
  )
}
