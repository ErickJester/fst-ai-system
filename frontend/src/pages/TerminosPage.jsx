import React from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Acceso } from '../components/ui'

// Términos y condiciones de uso: visibles con o sin sesión. Solo el texto.
export default function TerminosPage() {
  const navigate = useNavigate()

  // Con historial, vuelve a la pantalla anterior; si no hay, va al inicio de sesión.
  function volver(e) {
    if (window.history.length > 1) {
      e.preventDefault()
      window.history.back()
    } else {
      e.preventDefault()
      navigate('/login')
    }
  }

  return (
    <Acceso encabezado sub="Análisis de la prueba de nado forzado (Forced Swim Test, FST)">
      <div className="narrow" style={{ padding: '24px 34px 28px', display: 'flex', flexDirection: 'column', gap: 18 }}>
        <h2 style={{ margin: 0, fontSize: 22 }}>Términos y condiciones de uso</h2>

        <section style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <h3 style={{ margin: 0, fontSize: 16 }}>Uso del sistema</h3>
          <ul style={{ margin: 0, paddingLeft: 20, lineHeight: 1.6 }}>
            <li>El sistema es una herramienta de apoyo a la investigación.</li>
            <li>No emite diagnóstico clínico.</li>
          </ul>
        </section>

        <section style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <h3 style={{ margin: 0, fontSize: 16 }}>Resultados</h3>
          <ul style={{ margin: 0, paddingLeft: 20, lineHeight: 1.6 }}>
            <li>Los resultados los calcula el sistema de forma automática y pueden equivocarse.</li>
            <li>Se pueden revisar y corregir segundo a segundo.</li>
          </ul>
        </section>

        <section style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <h3 style={{ margin: 0, fontSize: 16 }}>Videos y conservación</h3>
          <ul style={{ margin: 0, paddingLeft: 20, lineHeight: 1.6 }}>
            <li>Los videos se borran 30 días después de un análisis exitoso, y antes si el disco del sistema pasa del 90 %. El borrado no se puede deshacer.</li>
            <li>Los resultados y los reportes se conservan.</li>
          </ul>
        </section>

        <Link to="/login" onClick={volver} style={{ alignSelf: 'flex-start' }}>Volver</Link>
      </div>
    </Acceso>
  )
}
