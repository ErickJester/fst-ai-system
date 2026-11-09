import React, { useState } from 'react'
import { svgAPng } from '../../../lib/svgAPng'

// Botón que descarga la gráfica apuntada por svgRef como archivo PNG.
export default function BotonDescargarImagen({ svgRef, nombre }) {
  const [estado, setEstado] = useState('listo') // 'listo' | 'preparando' | 'error'

  async function descargar() {
    setEstado('preparando')
    try {
      const blob = await svgAPng(svgRef.current)
      const url = URL.createObjectURL(blob)
      const enlace = document.createElement('a')
      enlace.href = url
      enlace.download = `${nombre}.png`
      document.body.appendChild(enlace)
      enlace.click()
      enlace.remove()
      // Se libera después de un momento: algunos navegadores aún necesitan la URL al hacer clic.
      setTimeout(() => URL.revokeObjectURL(url), 1000)
      setEstado('listo')
    } catch {
      setEstado('error')
    }
  }

  const preparando = estado === 'preparando'

  return (
    <div>
      <button
        type="button"
        className="btn btn-secondary"
        onClick={descargar}
        disabled={preparando}
      >
        {preparando ? 'Preparando…' : 'Descargar imagen'}
      </button>
      {estado === 'error' && (
        <span role="alert" className="hint">
          No se pudo preparar la imagen. Inténtalo de nuevo.
        </span>
      )}
    </div>
  )
}
