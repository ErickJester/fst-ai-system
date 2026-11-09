// Convierte un <svg> de la pantalla en una imagen PNG, para descargarla.
// El SVG original no se modifica: se trabaja sobre un clon.

// Propiedades cuyo valor calculado se copia al clon. Así las variables CSS (var(--...))
// quedan resueltas y el PNG se ve igual que en pantalla, aunque el archivo no tenga hojas de estilo.
const PROPIEDADES = [
  'fill',
  'stroke',
  'stroke-width',
  'stroke-dasharray',
  'opacity',
  'font-family',
  'font-size',
  'font-weight',
  'text-anchor',
]

function dimensiones(svg) {
  const viewBox = (svg.getAttribute('viewBox') || '').trim().split(/[\s,]+/).map(Number)
  if (viewBox.length === 4 && viewBox[2] > 0 && viewBox[3] > 0) {
    return { ancho: viewBox[2], alto: viewBox[3] }
  }
  const caja = svg.getBoundingClientRect()
  return { ancho: caja.width, alto: caja.height }
}

function copiarEstilos(original, clon) {
  const origenes = [original, ...original.querySelectorAll('*')]
  const copias = [clon, ...clon.querySelectorAll('*')]
  origenes.forEach((elemento, i) => {
    const copia = copias[i]
    if (!copia) return
    const calculado = window.getComputedStyle(elemento)
    PROPIEDADES.forEach((propiedad) => {
      const valor = calculado.getPropertyValue(propiedad)
      if (valor) copia.style.setProperty(propiedad, valor)
    })
  })
}

function cargarImagen(src) {
  return new Promise((resolver, rechazar) => {
    const imagen = new Image()
    imagen.onload = () => resolver(imagen)
    imagen.onerror = () => rechazar(new Error('No se pudo cargar el SVG como imagen.'))
    imagen.src = src
  })
}

function aBlobPng(canvas) {
  return new Promise((resolver, rechazar) => {
    canvas.toBlob((blob) => {
      if (blob) resolver(blob)
      else rechazar(new Error('No se pudo generar el PNG.'))
    }, 'image/png')
  })
}

/**
 * Genera un PNG a partir de un elemento <svg>.
 * @param {SVGSVGElement} svg elemento original (no se modifica)
 * @param {{ escala?: number, fondo?: string }} opciones escala del PNG y color de fondo
 * @returns {Promise<Blob>} blob image/png
 */
export async function svgAPng(svg, { escala = 2, fondo = '#ffffff' } = {}) {
  if (!svg || typeof svg.cloneNode !== 'function' || svg.nodeName.toLowerCase() !== 'svg') {
    throw new Error('No hay ninguna gráfica para preparar.')
  }

  const { ancho, alto } = dimensiones(svg)
  if (!(ancho > 0) || !(alto > 0)) {
    throw new Error('La gráfica no tiene tamaño para convertirla en imagen.')
  }

  const clon = svg.cloneNode(true)
  copiarEstilos(svg, clon)
  clon.setAttribute('xmlns', 'http://www.w3.org/2000/svg')
  clon.setAttribute('width', String(ancho))
  clon.setAttribute('height', String(alto))

  const xml = new XMLSerializer().serializeToString(clon)
  const imagen = await cargarImagen(`data:image/svg+xml;charset=utf-8,${encodeURIComponent(xml)}`)

  const canvas = document.createElement('canvas')
  canvas.width = Math.round(ancho * escala)
  canvas.height = Math.round(alto * escala)
  const contexto = canvas.getContext('2d')
  if (!contexto) {
    throw new Error('Este navegador no permite preparar la imagen.')
  }
  if (fondo) {
    contexto.fillStyle = fondo
    contexto.fillRect(0, 0, canvas.width, canvas.height)
  }
  contexto.drawImage(imagen, 0, 0, canvas.width, canvas.height)

  return aBlobPng(canvas)
}
