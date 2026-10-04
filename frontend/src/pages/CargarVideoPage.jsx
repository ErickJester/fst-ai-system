import React from 'react'
import { Link, useParams } from 'react-router-dom'
import { getExperiment } from '../services/experiments'
import { useAsync } from '../hooks/useAsync'
import { PageShell, StepHeader } from '../components/ui'
import Pendiente from './Pendiente'

// 2b · Cargar video por tanda
export default function CargarVideoPage() {
  const { id } = useParams()
  const { data } = useAsync(() => getExperiment(id), [id])

  return (
    <PageShell topbar={{ sub: data?.nombre, actions: <Link to={`/experimentos/${id}`}>Salir</Link> }}>
      <StepHeader steps={['Datos generales', 'Cargar video por tanda']} current={2} />
      <h3 style={{ margin: '0 0 8px' }}>Cargar video por tanda</h3>
      <p className="lead" style={{ margin: '0 0 24px', maxWidth: 620 }}>
        Un video corresponde a una tanda de un solo grupo. Indica el grupo, confirma qué tanda es y suelta el archivo.
      </p>
      <Pendiente pantalla="2b" fase={4} />
    </PageShell>
  )
}
