import React, { useId } from 'react'
import BotonDescargarImagen from './BotonDescargarImagen'
import { NOTA_DE, NOTA_ORIGEN } from './estilos'

// El marco de una gráfica: título, subtítulo, controles, el dibujo (un <svg> que recibe svgRef), la leyenda, las notas
// obligatorias, la tabla con los mismos números y el botón «Descargar imagen». Lo comparten las seis gráficas.
export default function PanelGrafica({ titulo, subtitulo, nombre, svgRef, controles, leyenda, notas = [], tabla, children }) {
  const id = useId().replace(/[^a-zA-Z0-9]/g, '')
  return (
    <section aria-labelledby={`${id}-titulo`} style={{ display: 'flex', flexDirection: 'column', gap: 14, minWidth: 0 }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 10 }}>
        <div style={{ minWidth: 0, flex: '1 1 220px' }}>
          <h3 id={`${id}-titulo`} style={{ margin: '0 0 4px', fontSize: 16 }}>{titulo}</h3>
          {subtitulo && <p className="hint" style={{ margin: 0 }}>{subtitulo}</p>}
        </div>
        <BotonDescargarImagen svgRef={svgRef} nombre={nombre} />
      </div>
      {controles && <div className="no-print" style={{ display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'center' }}>{controles}</div>}
      <div style={{ maxWidth: '100%', overflow: 'hidden' }}>{children}</div>
      {leyenda && <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px 16px', fontSize: 12 }}>{leyenda}</div>}
      <ul className="hint" style={{ margin: 0, paddingLeft: 18, lineHeight: 1.55 }}>
        {notas.map((texto) => <li key={texto}>{texto}</li>)}
        <li>{NOTA_DE}</li>
        <li>{NOTA_ORIGEN}</li>
      </ul>
      <div style={{ overflowX: 'auto' }}>{tabla}</div>
    </section>
  )
}
