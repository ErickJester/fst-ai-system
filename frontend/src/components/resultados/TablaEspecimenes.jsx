import React from 'react'
import { mean, variance, fmt, r1, porcentajesDeFila } from '../../lib/fst'
import { MUT70 } from '../../lib/estilos'
import { MUT, CONDUCTAS, SIN_VER, TODAS, CMAP, LBL, PENDIENTE, etiqueta } from './constantes'

// Media, desviación estándar y varianza de lo que se ve (RF-31).
function estadisticos(duracion) {
  return [
    { nombre: 'Media', f: (a) => r1(mean(a)) + ' s', nota: 'sobre ' + duracion + ' s de evaluación' },
    { nombre: 'Desviación estándar', f: (a) => r1(Math.sqrt(variance(a))), nota: 'muestral, n − 1' },
    { nombre: 'Varianza', f: (a) => r1(variance(a)), nota: 'muestral' },
  ]
}

// Tiempo por conducta de cada espécimen que se ve, con su distribución y los tres renglones estadísticos.
export default function TablaEspecimenes({ especimenes, duracion, grupoCompleto, faltan, innerRef }) {
  const stats = estadisticos(duracion)
  const col = (k) => especimenes.map((e) => e[k + '_s'] ?? 0)
  // Cada fila suma 100 %: su denominador son los segundos guardados del espécimen (sus cinco columnas), no la duración del video.
  const pctsDe = (e) => porcentajesDeFila(TODAS.map((k) => e[k + '_s'] ?? 0))
  const anchoDe = (e, k) => {
    const total = TODAS.reduce((a, x) => a + (e[x + '_s'] ?? 0), 0)
    return total ? ((e[k + '_s'] ?? 0) / total) * 100 : 0
  }

  return (
    <>
      <div ref={innerRef} style={{ display: 'flex', alignItems: 'center', gap: 18, marginBottom: 14 }}>
        <div className="k">Tiempo por conducta · min:seg</div>
        <div style={{ flex: 1 }} />
        <span style={{ display: 'flex', gap: 18 }}>
          {CONDUCTAS.map((b) => (
            <span key={b.id} style={{ display: 'inline-flex', alignItems: 'center', gap: 7, fontSize: 11.5 }}><span className="sw" style={{ background: b.color }} />{b.label}</span>
          ))}
        </span>
      </div>

      {/* Si la pantalla es angosta, la tabla se desplaza de lado en vez de empujar la página. */}
      <div style={{ overflowX: 'auto' }}>
      <table className="table">
        <thead>
          <tr>
            <th style={{ width: '27%' }}>Espécimen</th>
            {CONDUCTAS.map((c) => <th key={c.id} style={{ textAlign: 'right' }}>{c.label}</th>)}
            <th style={{ textAlign: 'right', fontWeight: 400, color: MUT }}>{SIN_VER.label}</th>
            <th style={{ width: '30%' }}>Distribución</th>
          </tr>
        </thead>
        <tbody>
          {especimenes.map((e) => (
            <tr key={e.letra + e.numeroCilindro}>
              <td>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <span className="num nd" style={{ width: 24, height: 24, flex: 'none', border: '2px solid var(--color-accent)', color: 'var(--color-accent-700)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10 }}>{e.numeroCilindro}</span>
                  <span style={{ fontSize: 13, whiteSpace: 'nowrap' }}>{etiqueta(e, e.letra)}</span>
                </div>
              </td>
              {TODAS.map((k, i) => (
                <td key={k} className="num" style={{ textAlign: 'right', whiteSpace: 'nowrap', ...(k === SIN_VER.id && { color: MUT }) }}>
                  {fmt(e[k + '_s'] ?? 0)} <span style={{ fontSize: 11, color: MUT }}>{pctsDe(e)[i]} %</span>
                </td>
              ))}
              <td>
                <div style={{ display: 'flex', height: 16, border: '1px solid var(--color-divider)' }}>
                  {TODAS.map((k) => <div key={k} title={LBL[k]} style={{ background: CMAP[k], width: anchoDe(e, k) + '%' }} />)}
                </div>
              </td>
            </tr>
          ))}
          {stats.map((s) => (
            <tr key={s.nombre} style={{ background: 'var(--color-surface)' }}>
              <td className="k" style={{ color: MUT70 }}>{s.nombre}</td>
              {TODAS.map((k) => <td key={k} className="num nd" style={{ textAlign: 'right', ...(k === SIN_VER.id && { color: MUT }) }}>{s.f(col(k))}</td>)}
              <td style={{ fontSize: 11.5, color: MUT }}>{s.nota}</td>
            </tr>
          ))}
        </tbody>
      </table>
      </div>

      <p className="hint" style={{ margin: '14px 0 0' }}>
        Cada segundo recibe una conducta y ninguna se descarta por durar poco; el buceo se contabiliza como nado activo. <strong>Sin ver</strong> son los segundos en los que no se vio al animal o que la revisión marcó como no visibles: no cuentan en ninguna conducta, y con las cuatro suman el total de segundos. Los tres renglones grises son la media, la desviación estándar y la varianza de lo que se ve ({grupoCompleto ? 'el grupo completo' : 'la tanda'}); el CSV y el XLSX descargan esos mismos.{grupoCompleto && faltan.length > 0 && <> Faltan {faltan.map((t) => 'Tanda ' + t.letra + ' (' + (PENDIENTE[t.estado] || 'sin subir') + ')').join(', ')}: no entran hasta tener su análisis completado.</>} El desglose por minuto se almacena y exporta <strong>en segundos</strong>, no en porcentaje.
      </p>
    </>
  )
}
