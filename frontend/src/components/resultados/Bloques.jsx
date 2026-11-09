import React, { useState } from 'react'
import { getBlocks } from '../../services/results'
import { useAsync } from '../../hooks/useAsync'
import { fmt, finDeBloque, columnasDeBloques } from '../../lib/fst'
import { MUT60 } from '../../lib/estilos'
import { CMAP, LBL, MATIZ, etiquetaAnimal } from './constantes'

// Bloques de 5 s (D-53): cada bloque muestra la conducta con más segundos; al abrirlo, sus
// cinco segundos, para notar las conductas breves. Se calculan al pedirlos.
export default function Bloques({ clave, gid, letras }) {
  // Los de cada tanda que se ve; en el grupo completo, una fila por animal de cada tanda.
  const { data, error, loading } = useAsync(async () => {
    const todos = await Promise.all(letras.map((l) => getBlocks(clave, gid, l)))
    if (todos.every((x) => !x)) return null
    return { especimenes: todos.flatMap((x, i) => (x ? x.especimenes.map((e) => ({ ...e, letra: letras[i] })) : [])) }
  }, [clave, gid, letras.join()])
  const [abierto, setAbierto] = useState(null) // { letra, numeroCilindro, i }

  if (error) return <p className="hint">No se pudieron cargar los bloques.</p>
  if (loading) return <p className="hint">Cargando…</p>
  // Los calcula el backend; sin él no hay bloques y se explica qué mostrará esta sección.
  if (!data) {
    return (
      <div className="note-bar" role="status" style={{ lineHeight: 1.55 }}>
        <strong>Bloques de 5 s.</strong> Los calcula el sistema con la conducta de cada segundo: por cada animal, bloques de cinco segundos con la conducta que ocupa más segundos (si hay empate arriba, conducta activa). Aquí se verá una fila por animal; al abrir un bloque, sus segundos, y rayados los bloques en los que algún segundo no se vio bien. Todavía no está disponible.
      </div>
    )
  }

  const sel = abierto && data.especimenes.find((e) => e.letra === abierto.letra && e.numeroCilindro === abierto.numeroCilindro)?.bloques[abierto.i]
  // Las columnas son las del espécimen con más bloques: el mismo segundo queda a la misma altura en todas las filas y las más
  // cortas terminan antes (un video más corto, o un último bloque con menos de 5 s, no estira las celdas).
  const columnas = columnasDeBloques(data.especimenes)
  const desc = (b) => fmt(b.inicio - 1) + '–' + fmt(finDeBloque(b)) + ' · ' + LBL[b.conducta] + (b.matiz ? ' (no se vio bien)' : '')

  return (
    <div style={{ border: '1px solid var(--color-divider)', padding: '16px 18px' }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {data.especimenes.map((e) => (
          <div key={e.letra + e.numeroCilindro} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <span style={{ width: 132, fontSize: 11.5, color: MUT60 }}>{etiquetaAnimal(e.numeroCilindro, e.letra)}</span>
            <div role="group" aria-label={'Bloques de ' + etiquetaAnimal(e.numeroCilindro, e.letra)} style={{ flex: 1, display: 'grid', gridTemplateColumns: `repeat(${columnas}, 1fr)`, height: 18, border: '1px solid var(--color-divider)' }}>
              {e.bloques.map((b, i) => {
                const on = abierto?.letra === e.letra && abierto.numeroCilindro === e.numeroCilindro && abierto.i === i
                return (
                  <button key={i} type="button" title={desc(b)} aria-label={desc(b)} aria-pressed={on}
                    onClick={() => setAbierto(on ? null : { letra: e.letra, numeroCilindro: e.numeroCilindro, i })}
                    style={{ padding: 0, border: 0, minWidth: 0, cursor: 'pointer', background: b.matiz ? MATIZ : CMAP[b.conducta], outline: on ? '2px solid var(--color-text)' : 'none', outlineOffset: -1 }} />
                )
              })}
            </div>
          </div>
        ))}
      </div>
      {sel ? (
        <div role="status" style={{ marginTop: 14, paddingTop: 12, borderTop: '1px solid var(--color-divider)' }}>
          <div className="nd" style={{ fontSize: 13 }}>{etiquetaAnimal(abierto.numeroCilindro, abierto.letra)} · {desc(sel)}</div>
          <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
            {sel.segundos.map((c, k) => (
              <div key={k} style={{ flex: 1, padding: '6px 8px', border: '1px solid var(--color-divider)', fontSize: 11.5 }}>
                <div className="num" style={{ color: MUT60 }}>{fmt(sel.inicio - 1 + k)}</div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 3 }}>
                  <span className="sw" style={{ background: c ? CMAP[c] : 'transparent', border: c ? 0 : '1px dashed var(--muted)' }} />
                  {c ? LBL[c] : 'No se ve'}
                </div>
              </div>
            ))}
          </div>
          {sel.matiz && <p className="hint" style={{ margin: '8px 0 0' }}>El modelo no pudo ver bien al animal en esta parte del video; el bloque se muestra como conducta activa con este matiz.</p>}
        </div>
      ) : (
        <p className="hint" style={{ margin: '12px 0 0' }}>
          Cada celda es un bloque de 5 s con la conducta que ocupa más de sus segundos; si hay empate arriba, el bloque es conducta activa. Las celdas rayadas tienen algún segundo que no se vio bien. Haz clic en una para ver sus segundos.
        </p>
      )}
    </div>
  )
}
