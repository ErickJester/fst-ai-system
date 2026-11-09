import React, { useId } from 'react'
import BotonDescargarImagen from './BotonDescargarImagen'
import { NOTA_DE, NOTA_ORIGEN } from './estilos'

// El marco de una gráfica: pregunta (título), alcance y subtítulo, controles, el dibujo (un <svg> que recibe svgRef),
// la leyenda, los avisos de grupo (visibles), y dentro de «Ver números y detalles» las notas y la tabla con los mismos números.
// El botón «Descargar imagen» va en la cabecera. Lo comparten las seis gráficas.
export default function PanelGrafica({ titulo, subtitulo, nombre, svgRef, controles, leyenda, avisos = [], notas = [], tabla, children }) {
  const id = useId().replace(/[^a-zA-Z0-9]/g, '')
  return (
    <section aria-labelledby={`${id}-titulo`} style={{ display: 'flex', flexDirection: 'column', gap: 14, minWidth: 0 }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 10 }}>
        <div style={{ minWidth: 0, flex: '1 1 220px' }}>
          <h3 id={`${id}-titulo`} style={{ margin: '0 0 4px', fontSize: 16 }}>{titulo}</h3>
          <p className="hint" style={{ margin: 0 }}>Día 2 · todos los grupos del experimento</p>
          {subtitulo && <p className="hint" style={{ margin: '4px 0 0' }}>{subtitulo}</p>}
        </div>
        <BotonDescargarImagen svgRef={svgRef} nombre={nombre} />
      </div>
      {controles && <div className="no-print" style={{ display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'center' }}>{controles}</div>}
      <div style={{ maxWidth: '100%', overflow: 'hidden' }}>{children}</div>
      {leyenda && <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px 16px', fontSize: 12 }}>{leyenda}</div>}
      {avisos.length > 0 && (
        <ul className="hint" style={{ margin: 0, paddingLeft: 18, lineHeight: 1.55 }}>
          {avisos.map((texto) => <li key={texto}>{texto}</li>)}
        </ul>
      )}
      <details>
        <summary style={{ cursor: 'pointer', fontSize: 13 }}>Ver números y detalles</summary>
        <ul className="hint" style={{ margin: '10px 0 0', paddingLeft: 18, lineHeight: 1.55 }}>
          {notas.map((texto) => <li key={texto}>{texto}</li>)}
          <li>{NOTA_DE}</li>
          <li>{NOTA_ORIGEN}</li>
        </ul>
        <div style={{ overflowX: 'auto', marginTop: 10 }}>{tabla}</div>
      </details>
    </section>
  )
}
