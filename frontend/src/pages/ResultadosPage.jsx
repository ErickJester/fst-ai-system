import React, { useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { useModo } from '../contexts/ModoContext'
import Topbar, { Brand } from '../components/Topbar'
import { getBatchResults, getGroupComparison, getReporte, getSalidaModelo } from '../services/results'
import { mensajeError } from '../services/api'
import { getExperiment } from '../services/experiments'
import { useAsync } from '../hooks/useAsync'
import { download, enlaceResultadosExperimento } from '../lib/fst'
import { Migas, Cargando } from '../components/ui'
import PanelLateral from '../components/resultados/PanelLateral'
import CabeceraResultados from '../components/resultados/CabeceraResultados'
import BarraReportes, { AvisoReporte } from '../components/resultados/BarraReportes'
import SinResultados from '../components/resultados/SinResultados'
import ContenidoResultados from '../components/resultados/ContenidoResultados'
import ExperimentoResultados from '../components/resultados/ExperimentoResultados'
import { REPORTES, TABS } from '../components/resultados/constantes'

// Resultados de un experimento (pantalla del experimento). Sin ?grupo: el resumen del experimento, con lo que se logró, las
// gráficas, las descargas y el acceso al detalle. Con ?grupo= (y ?tanda=): el detalle de esa tanda o del grupo completo
// (RF-19, D-93). La vista del detalle va en ?vista=.
export default function ResultadosPage() {
  const { clave } = useParams()
  const [params] = useSearchParams()
  const gid = params.get('grupo')
  const letra = params.get('tanda')
  if (gid) return <ResultadosTanda key={gid + (letra || '*')} clave={clave} gid={gid} letra={letra} />
  return <ExperimentoResultados clave={clave} />
}

const VISTAS = TABS.map((t) => t.id)

function ResultadosTanda({ clave, gid, letra }) {
  const { modoDesarrollador } = useModo()
  const [params, setParams] = useSearchParams()
  const vista = VISTAS.includes(params.get('vista')) ? params.get('vista') : 'especimen'
  const cambiarVista = (id) => setParams((p) => { p.set('vista', id); return p }, { replace: true })
  const [conducta, setConducta] = useState('inmovilidad') // la que se compara entre grupos
  const [reporte, setReporte] = useState(null) // { formato, error } del último botón de reporte
  const [lineaSel, setLineaSel] = useState('') // espécimen del desglose por minuto: «A-1»
  // Una tanda (letra) o el grupo completo (letra null). El grupo completo reúne solo las tandas
  // con análisis completado; las demás se señalan como faltantes (RN-12).
  const { data, error, loading } = useAsync(async () => {
    const e = await getExperiment(clave)
    const g = e.grupos.find((x) => x.id === gid)
    if (!g) throw new Error('No existe ese grupo.')
    const listas = g.tandas.filter((t) => t.estado === 'DONE' && (!letra || t.letra === letra))
    if (!listas.length) throw new Error('Todavía no hay resultados.')
    return { g, grupos: e.grupos, rs: await Promise.all(listas.map((t) => getBatchResults(clave, gid, t.letra))) }
  }, [clave, gid, letra])
  const { data: comparacion } = useAsync(() => getGroupComparison(clave), [clave])

  if (loading) return <Cargando />
  if (error) return <SinResultados clave={clave} />

  const { g, rs, grupos } = data
  const r = rs[0]
  const exp = r.experimento
  const grupoCompleto = !letra
  // Los especímenes que se ven, cada uno con su tanda; los estadísticos (RF-31) se calculan
  // sobre ellos: los de la tanda o los del grupo completo.
  const especimenes = rs.flatMap((x) => x.especimenes.map((e) => ({ ...e, letra: x.letra })))
  const completadas = g.tandas.filter((t) => t.estado === 'DONE')
  const faltan = g.tandas.filter((t) => t.estado !== 'DONE')
  const nCompletos = completadas.reduce((a, t) => a + t.n_cilindros, 0)
  const agrupado = rs.some((x) => x.nivel === 'AGRUPADO')
  const revisado = rs.some((x) => x.revisado)
  const analizado = rs.map((x) => x.analizado_en).sort().at(-1)
  // Cuánto tardó el análisis de cada tanda que se ve y en qué computadora (los análisis de antes no lo tienen).
  const tiempos = modoDesarrollador ? rs.filter((x) => x.tiempo_analisis) : []   // solo en modo desarrollador
  const vistaTexto = grupoCompleto ? 'del grupo completo' : 'de la Tanda ' + r.letra
  // Desglose por minuto de cada espécimen que se ve; se elige uno.
  const lineas = rs.flatMap((x) => (x.lineas_tiempo || [x.linea_tiempo]).map((l) => ({ ...l, letra: x.letra, clave: x.letra + '-' + l.numeroCilindro })))
  const linea = lineas.find((l) => l.clave === lineaSel) || lineas[0]

  // Pide el reporte al backend y lo descarga; sin backend, explica qué hará. CSV y XLSX son de
  // lo que se ve (el grupo completo o la tanda); el PDF, del experimento (D-93).
  async function pedirReporte(formato) {
    setReporte({ formato, pidiendo: true })
    const alcance = formato === 'pdf' ? null : grupoCompleto ? { grupo: gid } : { grupo: gid, tanda: r.letra }
    try {
      const descarga = await getReporte(exp.clave, formato, alcance)
      if (!descarga) return setReporte({ formato })
      // El nombre que manda el servidor; si no viene, uno armado aquí.
      const nombre = ['reporte', exp.clave, alcance ? (grupoCompleto ? 'grupo-' + gid : 'tanda' + r.letra) : null].filter(Boolean).join('_')
      download(descarga.nombre || nombre + '.' + formato, descarga.archivo, REPORTES[formato].tipo)
      setReporte(null)
    } catch (e) {
      setReporte({ formato, error: mensajeError(e, 'No se pudo descargar el reporte. Inténtalo de nuevo.') })
    }
  }

  // «Salida del modelo» (solo modo desarrollador): lo que decidió el modelo en cada segundo, tal cual. Con una tanda
  // pide grupo y tanda; con el grupo completo, solo grupo.
  async function pedirSalidaModelo() {
    setReporte({ formato: 'salida-modelo', pidiendo: true })
    try {
      const salida = await getSalidaModelo(exp.clave, grupoCompleto ? { grupo: gid } : { grupo: gid, tanda: r.letra })
      if (salida) download(salida.nombre, salida.archivo, salida.tipo)
      setReporte(null)
    } catch (e) {
      setReporte({ formato: 'salida-modelo', error: mensajeError(e, 'No se pudo descargar la salida por segundo del modelo. Inténtalo de nuevo.') })
    }
  }

  return (
    <div className="app">
      <Topbar>
        <Brand inline />
        <Migas items={[
          { to: '/experimentos', label: 'Experimentos' },
          { to: `/experimentos/${exp.clave}`, label: exp.titulo },
          { to: enlaceResultadosExperimento(exp.clave), label: 'Resultados' },
          { to: `/experimentos/${exp.clave}/grupos/${r.grupo.id}`, label: r.grupo.nombre },
          { label: grupoCompleto ? 'Grupo completo · Día 2' : `Tanda ${r.letra} · Día 2` },
        ]} />
      </Topbar>

      <div className="page">
        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'flex-end', gap: 24 }}>
          <CabeceraResultados clave={clave} gid={gid} r={r} rs={rs} grupos={grupos} vista={vista} grupoCompleto={grupoCompleto} analizado={analizado} tiempos={tiempos}
            completadas={completadas} nCompletos={nCompletos} agrupado={agrupado} revisado={revisado} />
          <BarraReportes clave={clave} r={r} grupoCompleto={grupoCompleto} reporte={reporte} pedirReporte={pedirReporte} pedirSalidaModelo={pedirSalidaModelo} />
        </div>

        {reporte && !reporte.pidiendo && <AvisoReporte reporte={reporte} setReporte={setReporte} vistaTexto={vistaTexto} />}

        {/* Las pestañas: cada una muestra solo su contenido; la activa va en ?vista= (M1A). */}
        <nav aria-label="Secciones de los resultados" className="no-print" style={{ display: 'flex', overflowX: 'auto', borderBottom: '2px solid var(--color-divider)', margin: '22px 0 24px' }}>
          {TABS.map((t) => (
            <button key={t.id} type="button" className={'tab' + (vista === t.id ? ' on' : '')} aria-current={vista === t.id ? 'true' : undefined}
              onClick={() => cambiarVista(t.id)} style={{ whiteSpace: 'nowrap' }}>{t.label}</button>
          ))}
        </nav>

        <div className="rejilla-lateral" style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) 330px', gap: 2, background: 'var(--color-divider)' }}>
          <div style={{ background: 'var(--color-bg)', paddingRight: 26, minWidth: 0 }}>
            <ContenidoResultados vista={vista} clave={clave} gid={gid} rs={rs} r={r} especimenes={especimenes} grupoCompleto={grupoCompleto}
              faltan={faltan} lineas={lineas} linea={linea} setLineaSel={setLineaSel} comparacion={comparacion}
              conducta={conducta} setConducta={setConducta} />
          </div>

          <PanelLateral r={r} especimenes={especimenes} g={g} grupoCompleto={grupoCompleto} />
        </div>
      </div>
    </div>
  )
}
