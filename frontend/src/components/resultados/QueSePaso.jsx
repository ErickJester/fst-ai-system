import React, { useId, useState } from 'react'
import { numero } from './graficas/estilos'

// Las conductas que se pueden comparar, con su nombre de la base y el texto que ve la persona.
const CONDUCTAS = [
  { clave: 'inmovilidad', etiqueta: 'Inmovilidad' },
  { clave: 'nado activo', etiqueta: 'Nado activo' },
  { clave: 'escalamiento', etiqueta: 'Escalamiento' },
  { clave: 'conducta activa', etiqueta: 'Conducta activa' },
]

const TEXTO_MIXTO = 'sus tandas se analizaron con distinto detalle y no se pueden promediar juntas. Míralas por separado en el detalle.'

const mediaDe = (grupo, conducta) => grupo?.porConducta?.[conducta]?.media ?? null

// «47,2 s menos» o «4,1 s más», y «lo mismo» si la diferencia es mínima.
function diferenciaTexto(diferencia) {
  if (Math.abs(diferencia) < 0.05) return 'lo mismo'
  return `${numero(Math.abs(diferencia))} s ${diferencia > 0 ? 'más' : 'menos'}`
}

// El porcentaje contra el control, en palabras: «(28 % menos)». Sin tiempo del control no hay porcentaje.
function porcentajeTexto(diferencia, mediaControl) {
  if (!mediaControl) return ' (el control no tiene tiempo en esta conducta)'
  const pct = Math.round((Math.abs(diferencia) / mediaControl) * 100)
  if (pct === 0) return ' (menos de 1 %)'
  return ` (${pct} % ${diferencia < 0 ? 'menos' : 'más'})`
}

// Lo que se dice de un grupo, en palabras comunes. Solo habla de promedios; nunca de significancia.
function renglon(grupo, conducta, mediaControl) {
  const nombre = conducta.toLowerCase()
  if (grupo.nivelMixto) return `${grupo.etiqueta}: ${TEXTO_MIXTO}`
  const media = mediaDe(grupo, conducta)
  if (grupo.n === 0 || media == null) return `${grupo.etiqueta}: todavía no tiene datos del Día 2.`
  if (grupo.tipo === 'control' || mediaControl == null) return `${grupo.etiqueta}: ${numero(media)} s de ${nombre} en promedio.`
  const diferencia = media - mediaControl
  if (Math.abs(diferencia) < 0.05) return `${grupo.etiqueta}: ${numero(media)} s de ${nombre} en promedio, lo mismo que el control.`
  return `${grupo.etiqueta}: ${numero(media)} s de ${nombre}; ${diferenciaTexto(diferencia)} que el control${porcentajeTexto(diferencia, mediaControl)}.`
}

// «¿Qué pasó?»: por grupo, sus segundos en la conducta elegida y la diferencia con el control. Solo usa la media de los datos
// de las gráficas; esta sección no calcula nada nuevo.
export default function QueSePaso({ datos, conductaInicial = 'inmovilidad' }) {
  const id = useId().replace(/[^a-zA-Z0-9]/g, '')
  const [conducta, setConducta] = useState(conductaInicial)
  const grupos = datos?.grupos ?? []
  const control = grupos.find((g) => g.tipo === 'control')
  const mediaControl = control && !control.nivelMixto ? mediaDe(control, conducta) : null
  const etiquetaConducta = CONDUCTAS.find((c) => c.clave === conducta)?.etiqueta ?? conducta

  return (
    <section aria-labelledby={`${id}-titulo`} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <h3 id={`${id}-titulo`} style={{ margin: 0, fontSize: 16 }}>¿Qué pasó? (comparado con el control)</h3>
      <p className="hint" style={{ margin: 0, maxWidth: 680 }}>
        Cuánto tiempo, en segundos, pasó cada grupo en la conducta que elijas, y cuánto difiere del control. Son promedios de los
        especímenes del Día 2; esta pantalla no hace pruebas estadísticas.
      </p>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <label htmlFor={`${id}-conducta`} style={{ fontSize: 12.5 }}>Conducta </label>
        <select id={`${id}-conducta`} className="input" style={{ width: 190, padding: '4px 6px', fontSize: 12 }} value={conducta}
          onChange={(e) => setConducta(e.target.value)}>
          {CONDUCTAS.map((c) => <option key={c.clave} value={c.clave}>{c.etiqueta}</option>)}
        </select>
      </div>

      {!control || control.nivelMixto || mediaControl == null ? (
        <p className="note-bar" role="status" style={{ margin: 0 }}>
          No hay control con datos del Día 2 en {etiquetaConducta.toLowerCase()}, así que no hay con qué comparar. Cuando el control
          tenga su análisis completado, aquí aparecerá la diferencia.
        </p>
      ) : null}

      {grupos.length > 0 && (
        <ul style={{ margin: 0, paddingLeft: 18, lineHeight: 1.7 }}>
          {grupos.map((g) => <li key={g.idGrupo}>{renglon(g, conducta, mediaControl)}</li>)}
        </ul>
      )}
    </section>
  )
}
