import React from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { AccessShell } from '../components/ui'
import Pendiente from './Pendiente'

// 2i · Cambiar contraseña · primer acceso
export default function PrimerAccesoPage() {
  const { logout } = useAuth()
  const navigate = useNavigate()
  return (
    <AccessShell header="brand">
      <h3 style={{ margin: '0 0 10px' }}>Cambiar contraseña</h3>
      <p style={{ margin: '0 0 22px', fontSize: 13, lineHeight: 1.6, color: 'color-mix(in srgb,var(--color-text) 70%,transparent)' }}>
        Entraste con una contraseña temporal. Debes cambiarla antes de usar el sistema; hasta entonces no hay acceso a ninguna otra pantalla.
      </p>
      <Pendiente pantalla="2i" fase={2} />
      <hr className="hr" />
      <button type="button" className="linkbtn" style={{ fontSize: 12.5 }} onClick={async () => { await logout(); navigate('/login') }}>Cerrar sesión</button>
    </AccessShell>
  )
}
