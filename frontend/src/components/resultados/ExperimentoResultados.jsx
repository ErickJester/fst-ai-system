import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import Topbar, { Brand } from '../Topbar'
import { Migas, Cargando, NoEncontrado } from '../ui'
import { useAsync } from '../../hooks/useAsync'
import { getExperiment } from '../../services/experiments'
import { getGraficas, getReporte, getExcelExperimento } from '../../services/results'
import { mensajeError } from '../../services/api'
import { download, primeraTandaLista, enlaceResultados } from '../../lib/fst'
import { PENDIENTE, TABS } from './constantes'
import GaleriaGraficas from './graficas/GaleriaGraficas'
import QueSePaso from './QueSePaso'

const XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

// Los textos largos de estos botones se parten en renglones: a 390 px no deben empujar la página.
const BOTON_QUE_SE_PARTE = { whiteSpace: 'normal', maxWidth: '100%', textAlign: 'left', lineHeight: 1.35 }

// Un grupo está listo cuando tiene tandas y todas tienen su Día 2 analizado.
const grupoListo = (g) => g.tandas.length > 0 && g.tandas.every((t) => t.estado === 'DONE')

// «Control (Tanda B en pausa); Dosis 1 (sin tandas)» para lo que todavía falta.
function textoDeLoQueFalta(grupos) {
  return grupos.filter((g) => !grupoListo(g)).map((g) => {
    if (g.tandas.length === 0) return `${g.nombre} (sin tandas)`
    const pendientes = g.tandas.filter((t) => t.estado !== 'DONE').map((t) => `Tanda ${t.letra} ${PENDIENTE[t.estado] || 'sin subir'}`)
    return `${g.nombre} (${pendientes.join(', ')})`
  }).join('; ')
}

// La pantalla principal de resultados de un experimento: qué se logró, la galería, las descargas y el acceso al detalle.
export default function ExperimentoResultados({ clave }) {
  const { data: exp, error, loading } = useAsync(() => getExperiment(clave), [clave])
  const graficas = useAsync(() => getGraficas(clave), [clave])
  if (loading) return <Cargando />
  if (error) return <NoEncontrado />

  const listos = exp.grupos.filter(grupoListo).length
  const falta = textoDeLoQueFalta(exp.grupos)
  const hayResultados = exp.grupos.some((g) => g.tandas.some((t) => t.estado === 'DONE'))
  const primera = primeraTandaLista(exp)

  return (
    <div className="app">
      <Topbar>
        <Brand inline />
        <Migas items={[
          { to: '/experimentos', label: 'Experimentos' },
          { to: `/experimentos/${exp.clave}`, label: exp.titulo },
          { label: 'Resultados' },
        ]} />
      </Topbar>

      <div className="page" style={{ display: 'flex', flexDirection: 'column', gap: 28 }}>
        <header>
          <h2 style={{ margin: '0 0 8px' }}>{exp.titulo}</h2>
          <p className="num" style={{ margin: 0, fontSize: 13, color: 'var(--muted-2)' }}>
            Listos {listos} de {exp.grupos.length} grupos{falta ? ' · Falta: ' + falta : ''}
          </p>
          <p className="hint" style={{ margin: '6px 0 0' }}>Día 2 (la prueba de 5 minutos).</p>
        </header>

        {!hayResultados ? (
          <p className="lead" style={{ maxWidth: 640 }}>
            Todavía no hay resultados. Aparecen cuando termina el análisis del Día 2 de una tanda.
          </p>
        ) : (
          <>
            {!graficas.loading && <QueSePaso datos={graficas.data} />}

            {graficas.loading ? <Cargando /> : graficas.error ? (
              <p className="hint" role="alert">No se pudieron cargar las gráficas.</p>
            ) : (
              <section aria-labelledby="galeria-del-experimento" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <h3 id="galeria-del-experimento" style={{ margin: 0, fontSize: 16 }}>Gráficas del experimento</h3>
                <GaleriaGraficas datos={graficas.data} />
              </section>
            )}

            <Descargas clave={clave} />
            <VerDetalle clave={clave} primera={primera} />
          </>
        )}
      </div>
    </div>
  )
}

// Descargar: el reporte completo para leer o imprimir, y los datos en Excel con el segundo a segundo.
function Descargas({ clave }) {
  const [mensaje, setMensaje] = useState(null) // { tipo, pidiendo } | { tipo, error } | { tipo }

  async function pedir(tipo) {
    setMensaje({ tipo, pidiendo: true })
    try {
      const descarga = tipo === 'pdf' ? await getReporte(clave, 'pdf') : await getExcelExperimento(clave)
      if (!descarga) return setMensaje({ tipo })
      const nombre = descarga.nombre || `reporte_experimento_${clave}.${tipo === 'pdf' ? 'pdf' : 'xlsx'}`
      download(nombre, descarga.archivo, tipo === 'pdf' ? 'application/pdf' : XLSX)
      setMensaje(null)
    } catch (e) {
      setMensaje({ tipo, error: mensajeError(e, 'No se pudo descargar. Inténtalo de nuevo.') })
    }
  }

  return (
    <section aria-labelledby="descargar-resultados" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <h3 id="descargar-resultados" style={{ margin: 0, fontSize: 16 }}>Descargar</h3>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
        <button type="button" className="btn btn-primary" style={BOTON_QUE_SE_PARTE} disabled={mensaje?.pidiendo} onClick={() => pedir('pdf')}>
          Reporte completo (PDF), para leer o imprimir
        </button>
        <button type="button" className="btn btn-secondary" style={BOTON_QUE_SE_PARTE} disabled={mensaje?.pidiendo} onClick={() => pedir('excel')}>
          Datos en Excel, con el segundo a segundo, para hacer tu propia estadística
        </button>
      </div>
      {mensaje && !mensaje.pidiendo && (
        <p className="note-bar" role="status" style={{ margin: 0 }}>
          {mensaje.error || 'Todavía no está disponible en el modo demo.'}
        </p>
      )}
    </section>
  )
}

// Ver el detalle: las vistas por tanda. El selector de grupo y tanda vive dentro del detalle.
function VerDetalle({ clave, primera }) {
  return (
    <section aria-labelledby="ver-el-detalle" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <h3 id="ver-el-detalle" style={{ margin: 0, fontSize: 16 }}>Ver el detalle</h3>
      <p className="hint" style={{ margin: 0, maxWidth: 680 }}>
        Tanda por tanda, espécimen por espécimen y segundo por segundo. Dentro del detalle eliges el grupo y la tanda.
      </p>
      {primera ? (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          {TABS.map((t) => (
            <Link key={t.id} className="btn btn-secondary" to={enlaceResultados(clave, primera.gid, primera.letra, t.id)}>{t.label}</Link>
          ))}
        </div>
      ) : (
        <p className="hint" style={{ margin: 0 }}>Todavía no hay una tanda con resultados.</p>
      )}
    </section>
  )
}
