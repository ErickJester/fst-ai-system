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

// El porcentaje contra el grupo de referencia, en palabras: «(28 % menos)». Sin tiempo de la referencia no hay porcentaje.
function porcentajeTexto(diferencia, mediaReferencia, etiquetaReferencia) {
  if (!mediaReferencia) return ` (${etiquetaReferencia} no tiene tiempo en esta conducta)`
  const pct = Math.round((Math.abs(diferencia) / mediaReferencia) * 100)
  if (pct === 0) return ' (menos de 1 %)'
  return ` (${pct} % ${diferencia < 0 ? 'menos' : 'más'})`
}

// Lo que se dice de un grupo, en palabras comunes. Solo habla de promedios; nunca de significancia.
// `ref` es el grupo contra el que se compara: { id, etiqueta, media }, o null si no hay con qué comparar.
function renglon(grupo, conducta, ref) {
  const nombre = conducta.toLowerCase()
  if (grupo.nivelMixto) return `${grupo.etiqueta}: ${TEXTO_MIXTO}`
  const media = mediaDe(grupo, conducta)
  if (grupo.n === 0 || media == null) return `${grupo.etiqueta}: todavía no tiene datos del Día 2.`
  if (!ref || ref.media == null || grupo.idGrupo === ref.id) return `${grupo.etiqueta}: ${numero(media)} s de ${nombre} en promedio.`
  const diferencia = media - ref.media
  if (Math.abs(diferencia) < 0.05) return `${grupo.etiqueta}: ${numero(media)} s de ${nombre} en promedio, lo mismo que ${ref.etiqueta}.`
  return `${grupo.etiqueta}: ${numero(media)} s de ${nombre}; ${diferenciaTexto(diferencia)} que ${ref.etiqueta}${porcentajeTexto(diferencia, ref.media, ref.etiqueta)}.`
}

// «¿Qué pasó?»: por grupo, sus segundos en la conducta elegida y la diferencia con el grupo que se elija como referencia (por defecto
// el control, si lo hay; si no, el primero con datos). Se puede quitar de la comparación a los grupos que no interesen. Solo usa la media
// de los datos de las gráficas; esta sección no calcula nada nuevo.
export default function QueSePaso({ datos, conductaInicial = 'inmovilidad' }) {
  const id = useId().replace(/[^a-zA-Z0-9]/g, '')
  const [conducta, setConducta] = useState(conductaInicial)
  const [referenciaId, setReferenciaId] = useState(null)   // null: la de por defecto
  const [quitados, setQuitados] = useState([])             // ids de los grupos que no se comparan
  const grupos = datos?.grupos ?? []
  const etiquetaConducta = CONDUCTAS.find((c) => c.clave === conducta)?.etiqueta ?? conducta

  const control = grupos.find((g) => g.tipo === 'control')
  const porDefecto = control ?? grupos.find((g) => !g.nivelMixto && g.n > 0 && mediaDe(g, conducta) != null) ?? null
  const elegida = grupos.find((g) => g.idGrupo === referenciaId) ?? porDefecto
  const ref = elegida && !elegida.nivelMixto && elegida.n > 0 && mediaDe(elegida, conducta) != null
    ? { id: elegida.idGrupo, etiqueta: elegida.etiqueta, media: mediaDe(elegida, conducta) }
    : null
  const visibles = grupos.filter((g) => !quitados.includes(g.idGrupo) || g.idGrupo === elegida?.idGrupo)

  const elegirReferencia = (valor) => {
    const idNuevo = Number(valor)
    setReferenciaId(idNuevo)
    setQuitados((q) => q.filter((x) => x !== idNuevo))   // la referencia siempre se muestra
  }
  const alternar = (idGrupo) => setQuitados((q) => (q.includes(idGrupo) ? q.filter((x) => x !== idGrupo) : [...q, idGrupo]))

  return (
    <section aria-labelledby={`${id}-titulo`} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <h3 id={`${id}-titulo`} style={{ margin: 0, fontSize: 16 }}>
        {ref ? `¿Qué pasó? (comparado con ${ref.etiqueta})` : '¿Qué pasó?'}
      </h3>
      <p className="hint" style={{ margin: 0, maxWidth: 680 }}>
        Cuánto tiempo, en segundos, pasó cada grupo en la conducta que elijas, y cuánto difiere del grupo con el que lo compares. Puedes
        elegir cualquier grupo como referencia y quitar de la comparación a los que no te interesen. Son promedios de los especímenes
        del Día 2; esta pantalla no hace pruebas estadísticas.
      </p>
      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 10 }}>
        <label htmlFor={`${id}-conducta`} style={{ fontSize: 12.5 }}>Conducta </label>
        <select id={`${id}-conducta`} className="input" style={{ width: 190, padding: '4px 6px', fontSize: 12 }} value={conducta}
          onChange={(e) => setConducta(e.target.value)}>
          {CONDUCTAS.map((c) => <option key={c.clave} value={c.clave}>{c.etiqueta}</option>)}
        </select>
        {grupos.length > 1 && (
          <>
            <label htmlFor={`${id}-referencia`} style={{ fontSize: 12.5 }}>Comparar contra </label>
            <select id={`${id}-referencia`} className="input" style={{ width: 220, padding: '4px 6px', fontSize: 12 }} value={elegida?.idGrupo ?? ''}
              onChange={(e) => elegirReferencia(e.target.value)}>
              {grupos.map((g) => <option key={g.idGrupo} value={g.idGrupo}>{g.etiqueta}</option>)}
            </select>
          </>
        )}
      </div>

      {grupos.length > 2 && (
        <fieldset style={{ border: 0, margin: 0, padding: 0, display: 'flex', flexWrap: 'wrap', gap: '4px 16px', alignItems: 'center' }}>
          <legend style={{ padding: 0, marginBottom: 4, fontSize: 12.5 }}>Grupos que se comparan</legend>
          {grupos.map((g) => (
            <label key={g.idGrupo} style={{ fontSize: 12.5, display: 'inline-flex', alignItems: 'center', gap: 6 }}>
              <input type="checkbox" checked={visibles.includes(g)} disabled={g.idGrupo === elegida?.idGrupo}
                onChange={() => alternar(g.idGrupo)} /> {g.etiqueta}
            </label>
          ))}
        </fieldset>
      )}

      {elegida && !ref && (
        <p className="note-bar" role="status" style={{ margin: 0 }}>
          {elegida.etiqueta} no tiene datos del Día 2 en {etiquetaConducta.toLowerCase()}, así que no hay con qué comparar. Elige otro grupo
          en «Comparar contra», o espera a que {elegida.etiqueta} tenga su análisis completado.
        </p>
      )}

      {visibles.length > 0 && (
        <ul style={{ margin: 0, paddingLeft: 18, lineHeight: 1.7 }}>
          {visibles.map((g) => <li key={g.idGrupo}>{renglon(g, conducta, ref)}</li>)}
        </ul>
      )}
    </section>
  )
}
