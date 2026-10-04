import React from 'react'
import { listUsers } from '../services/admin'
import { useAsync } from '../hooks/useAsync'
import { PageShell, Table, Tag } from '../components/ui'
import Pendiente from './Pendiente'

// 2g · Administración
export default function AdminPage() {
  const { data } = useAsync(listUsers)

  return (
    <PageShell width={1040} topbar={{ sub: 'Administración' }}>
      <h3 style={{ margin: '0 0 8px' }}>Administración</h3>
      <p className="lead" style={{ margin: '0 0 24px', maxWidth: 640 }}>
        La cuenta de administrador conserva todo lo que puede hacer un investigador —subir video, definir experimentos, consultar resultados— y añade estas tres áreas.
      </p>
      <Pendiente pantalla="2g" fase={8} />
      {data && (
        <Table>
          <thead><tr><th>Persona</th><th>Correo</th><th>Rol</th><th>Estado</th></tr></thead>
          <tbody>
            {data.map((u) => (
              <tr key={u.id}>
                <td className="nd" style={{ fontSize: 13 }}>{u.nombre} {u.apellidos}</td>
                <td className="num" style={{ fontSize: 12.5 }}>{u.email}</td>
                <td style={{ fontSize: 12.5 }}>{u.role === 'ADMIN' ? 'Administrador' : 'Investigador'}</td>
                <td><Tag variant="neutral">{u.is_active ? 'Activa' : 'Inactiva'}</Tag></td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </PageShell>
  )
}
