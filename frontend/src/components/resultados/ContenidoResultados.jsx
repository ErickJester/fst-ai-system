import React from 'react'
import { getGraficas } from '../../services/results'
import { useAsync } from '../../hooks/useAsync'
import { Cargando } from '../ui'
import TablaEspecimenes from './TablaEspecimenes'
import DesglosePorMinuto from './DesglosePorMinuto'
import Bloques from './Bloques'
import ComparacionGrupos from './ComparacionGrupos'
import GaleriaGraficas from './graficas/GaleriaGraficas'

// Lo que muestra cada pestaña de resultados (?vista=): solo su contenido.
export default function ContenidoResultados({ vista, clave, gid, rs, r, especimenes, grupoCompleto, faltan, lineas, linea, setLineaSel, comparacion, conducta, setConducta }) {
  if (vista === 'comparacion') return <ComparacionGrupos comparacion={comparacion} conducta={conducta} setConducta={setConducta} />
  if (vista === 'timeline') return <DesglosePorMinuto lineas={lineas} linea={linea} setLineaSel={setLineaSel} />
  if (vista === 'bloques') {
    return (
      <>
        <div className="k" style={{ margin: '0 0 12px' }}>Bloques de 5 s</div>
        <Bloques clave={clave} gid={gid} letras={rs.map((x) => x.letra)} />
      </>
    )
  }
  if (vista === 'graficas') return <VistaGraficas clave={clave} />
  return <TablaEspecimenes especimenes={especimenes} duracion={r.duracion_analizada_s} grupoCompleto={grupoCompleto} faltan={faltan} />
}

// Las gráficas del experimento (todos sus grupos del Día 2), con la galería.
function VistaGraficas({ clave }) {
  const { data, error, loading } = useAsync(() => getGraficas(clave), [clave])
  if (loading) return <Cargando />
  if (error) return <p className="hint" role="alert">No se pudieron cargar las gráficas.</p>
  return <GaleriaGraficas datos={data} />
}
