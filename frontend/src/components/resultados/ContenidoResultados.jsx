import React from 'react'
import { getSegundos } from '../../services/results'
import { mensajeError } from '../../services/api'
import { useAsync } from '../../hooks/useAsync'
import { Cargando } from '../ui'
import TablaEspecimenes from './TablaEspecimenes'
import DesglosePorMinuto from './DesglosePorMinuto'
import Bloques from './Bloques'
import ComparacionGrupos from './ComparacionGrupos'
import SegundoASegundo from './SegundoASegundo'

// Lo que muestra cada vista del detalle de una tanda (?vista=): solo su contenido.
export default function ContenidoResultados({ vista, clave, gid, rs, r, especimenes, grupoCompleto, faltan, lineas, linea, setLineaSel, comparacion, conducta, setConducta }) {
  if (vista === 'comparacion') return <ComparacionGrupos comparacion={comparacion} conducta={conducta} setConducta={setConducta} />
  if (vista === 'timeline') return <DesglosePorMinuto lineas={lineas} linea={linea} setLineaSel={setLineaSel} />
  if (vista === 'segundo') return <VistaSegundos clave={clave} gid={gid} letra={grupoCompleto ? null : r.letra} />
  if (vista === 'bloques') {
    return (
      <>
        <div className="k" style={{ margin: '0 0 12px' }}>Bloques de 5 s</div>
        <Bloques clave={clave} gid={gid} letras={rs.map((x) => x.letra)} />
      </>
    )
  }
  return <TablaEspecimenes especimenes={especimenes} duracion={r.duracion_analizada_s} grupoCompleto={grupoCompleto} faltan={faltan} />
}

// El segundo a segundo se ve por tanda: el grupo completo pide que se elija una.
function VistaSegundos({ clave, gid, letra }) {
  const { data, error, loading } = useAsync(() => (letra ? getSegundos(clave, gid, letra) : Promise.resolve(null)), [clave, gid, letra])
  if (!letra) return <p className="hint">El segundo a segundo se ve tanda por tanda: elige una tanda en «Ver», arriba.</p>
  if (loading) return <Cargando />
  if (error) return <p className="hint" role="alert">{mensajeError(error, 'No se pudo cargar el segundo a segundo.')}</p>
  return <SegundoASegundo datos={data} />
}
