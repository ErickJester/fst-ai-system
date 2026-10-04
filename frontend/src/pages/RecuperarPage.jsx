import React from 'react'
import { Link } from 'react-router-dom'
import { AccessShell } from '../components/ui'
import Pendiente from './Pendiente'

// 2h · Recuperar contraseña
export default function RecuperarPage() {
  return (
    <AccessShell header="none">
      <div className="nd" style={{ fontSize: 24, color: 'var(--color-accent)', marginBottom: 26 }}>FST</div>
      <h3 style={{ margin: '0 0 10px' }}>Recuperar contraseña</h3>
      <p style={{ margin: '0 0 22px', fontSize: 13, lineHeight: 1.6, color: 'color-mix(in srgb,var(--color-text) 70%,transparent)' }}>
        Escribe el correo de tu cuenta y te enviamos un enlace de restablecimiento válido por 30 minutos.
      </p>
      <Pendiente pantalla="2h" fase={2} />
      <Link to="/login" style={{ fontSize: 12.5 }}>Volver a iniciar sesión</Link>
    </AccessShell>
  )
}
