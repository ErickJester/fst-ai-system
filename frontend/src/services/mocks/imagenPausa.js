// Imagen fija de ejemplo para la pantalla de pausa (con backend es pausa.jpg, un fotograma
// del video original). Un SVG del tamaño de la pausa: fondo gris, agua y un cilindro por
// espécimen, con su espécimen dibujado. El del cilindro en pausa queda donde de verdad está,
// fuera del recuadro corrido que guardó el pipeline.
export function imagenDePausa(pausa, n) {
  const { ancho, alto } = pausa
  const paso = ancho / n
  const tubos = Array.from({ length: n }, (_, i) => {
    const cx = paso * i + paso / 2
    return `<rect x="${cx - 100}" y="140" width="200" height="490" rx="14" fill="#c9d3d8" stroke="#8a969c" stroke-width="5"/>` +
      `<rect x="${cx - 95}" y="300" width="190" height="325" fill="#9fb4c0"/>` +
      `<ellipse cx="${cx - 10 + (i % 2) * 20}" cy="${290 + (i % 3) * 25}" rx="34" ry="18" fill="#3d3a3b"/>`
  }).join('')
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${ancho}" height="${alto}" viewBox="0 0 ${ancho} ${alto}">` +
    `<rect width="${ancho}" height="${alto}" fill="#6f7578"/>${tubos}` +
    `<text x="24" y="${alto - 24}" font-family="monospace" font-size="22" fill="#e9ecee">segundo ${pausa.desde_segundo + 1} · imagen fija</text></svg>`
  return new Blob([svg], { type: 'image/svg+xml' })
}
