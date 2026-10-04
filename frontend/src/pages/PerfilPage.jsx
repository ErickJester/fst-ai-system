import React from 'react'
import { useAuth } from '../contexts/AuthContext'
import { PageShell } from '../components/ui'
import Pendiente from './Pendiente'

// 2j · Mi perfil
export default function PerfilPage() {
  const { user } = useAuth()
  return (
    <PageShell width={720} topbar={{ sub: 'Mi perfil' }}>
      <h3 style={{ margin: '0 0 20px' }}>Mi perfil</h3>
      <Pendiente pantalla="2j" fase={2} />
      <div style={{ display: 'grid', gap: 8, fontSize: 13 }}>
        <div><div className="k">Nombre</div>{user.nombre} {user.apellidos}</div>
        <div><div className="k">Correo institucional</div><span className="num">{user.email}</span></div>
        <div><div className="k">Identificador institucional</div><span className="num">{user.identificador}</span></div>
      </div>
    </PageShell>
  )
}
