import React from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Acceso } from '../components/ui'

// Aviso de privacidad: visible con o sin sesión. Sin datos de la cuenta; solo el texto.
export default function AvisoPrivacidadPage() {
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
        <h2 style={{ margin: 0, fontSize: 22 }}>Aviso de privacidad</h2>

        <section style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <h3 style={{ margin: 0, fontSize: 16 }}>Qué datos personales se tratan</h3>
          <ul style={{ margin: 0, paddingLeft: 20, lineHeight: 1.6 }}>
            <li>Identificador institucional.</li>
            <li>Nombre.</li>
            <li>Apellidos.</li>
            <li>Correo electrónico.</li>
          </ul>
        </section>

        <section style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <h3 style={{ margin: 0, fontSize: 16 }}>Para qué se usan</h3>
          <ul style={{ margin: 0, paddingLeft: 20, lineHeight: 1.6 }}>
            <li>Entrar al sistema.</li>
            <li>Saber qué persona creó cada experimento y sus videos.</li>
            <li>Enviarle por correo los avisos de su cuenta: la cuenta nueva y la recuperación de contraseña.</li>
          </ul>
        </section>

        <section style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <h3 style={{ margin: 0, fontSize: 16 }}>Cómo se protegen</h3>
          <ul style={{ margin: 0, paddingLeft: 20, lineHeight: 1.6 }}>
            <li>La contraseña se guarda con hash y sal; nunca en texto plano.</li>
            <li>Para usar el sistema hay que iniciar sesión.</li>
          </ul>
        </section>

        <Link to="/login" onClick={volver} style={{ alignSelf: 'flex-start' }}>Volver</Link>
      </div>
    </Acceso>
  )
}
