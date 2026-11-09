// @vitest-environment jsdom
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { svgAPng } from './svgAPng'

// jsdom no dibuja imágenes ni tiene canvas: se simulan ambos.
let dibujos
let imagenesCreadas

class ImagenFalsa {
  constructor() {
    imagenesCreadas.push(this)
  }
  set src(valor) {
    this._src = valor
    setTimeout(() => this.onload && this.onload(), 0)
  }
  get src() {
    return this._src
  }
}

function crearSvg(html, viewBox = '0 0 200 100') {
  const contenedor = document.createElement('div')
  contenedor.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${viewBox}">${html}</svg>`
  document.body.appendChild(contenedor)
  return contenedor.querySelector('svg')
}

beforeEach(() => {
  dibujos = []
  imagenesCreadas = []
  vi.stubGlobal('Image', ImagenFalsa)
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(() => ({
    fillStyle: '',
    fillRect: vi.fn(),
    drawImage: (imagen, x, y, w, h) => dibujos.push({ imagen, x, y, w, h }),
  }))
  vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(function (cb, tipo) {
    cb(new Blob(['png'], { type: tipo }))
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  document.body.innerHTML = ''
})

describe('svgAPng', () => {
  it('rechaza con un mensaje en español si la entrada es nula', async () => {
    await expect(svgAPng(null)).rejects.toThrow(Error)
    await expect(svgAPng(null)).rejects.toThrow('No hay ninguna gráfica para preparar.')
  })

  it('rechaza si el elemento no es un svg', async () => {
    const div = document.createElement('div')
    await expect(svgAPng(div)).rejects.toThrow('No hay ninguna gráfica para preparar.')
  })

  it('resuelve un Blob image/png', async () => {
    const svg = crearSvg('<rect width="10" height="10" fill="red"/>')
    const blob = await svgAPng(svg)
    expect(blob).toBeInstanceOf(Blob)
    expect(blob.type).toBe('image/png')
  })

  it('no modifica el SVG original', async () => {
    const svg = crearSvg('<rect width="10" height="10" fill="red"/>')
    const antes = svg.outerHTML
    await svgAPng(svg)
    expect(svg.outerHTML).toBe(antes)
    expect(svg.getAttribute('width')).toBeNull()
    expect(svg.getAttribute('height')).toBeNull()
  })

  it('el tamaño sale del viewBox y se multiplica por la escala', async () => {
    const svg = crearSvg('', '0 0 200 100')
    const canvasCreados = []
    const crear = document.createElement.bind(document)
    vi.spyOn(document, 'createElement').mockImplementation((etiqueta, ...resto) => {
      const elemento = crear(etiqueta, ...resto)
      if (etiqueta === 'canvas') canvasCreados.push(elemento)
      return elemento
    })

    await svgAPng(svg, { escala: 3 })
    expect(canvasCreados[0].width).toBe(600)
    expect(canvasCreados[0].height).toBe(300)

    await svgAPng(svg)
    expect(canvasCreados[1].width).toBe(400)
    expect(canvasCreados[1].height).toBe(200)
  })

  it('dibuja la imagen sobre el canvas y usa el fondo indicado', async () => {
    const svg = crearSvg('', '0 0 50 40')
    const fondos = []
    HTMLCanvasElement.prototype.getContext.mockImplementation(() => ({
      set fillStyle(valor) {
        fondos.push(valor)
      },
      fillRect: vi.fn(),
      drawImage: (imagen, x, y, w, h) => dibujos.push({ imagen, x, y, w, h }),
    }))

    await svgAPng(svg, { fondo: '#101010' })
    expect(fondos).toContain('#101010')
    expect(dibujos[0]).toMatchObject({ x: 0, y: 0, w: 100, h: 80 })
  })

  it('copia los estilos calculados al clon, sin tocar el original', async () => {
    const svg = crearSvg('<rect width="10" height="10"/>')
    const rect = svg.querySelector('rect')
    rect.style.fill = 'rgb(255, 0, 0)'
    rect.style.stroke = 'rgb(0, 0, 255)'

    await svgAPng(svg)
    const xml = decodeURIComponent(imagenesCreadas[0].src.split(',')[1])
    expect(xml).toContain('fill: rgb(255, 0, 0)')
    expect(xml).toContain('stroke: rgb(0, 0, 255)')
    expect(rect.style.fill).toBe('rgb(255, 0, 0)')
  })

  it('rechaza si el navegador no da contexto de canvas', async () => {
    const svg = crearSvg('')
    HTMLCanvasElement.prototype.getContext.mockImplementation(() => null)
    await expect(svgAPng(svg)).rejects.toThrow('Este navegador no permite preparar la imagen.')
  })
})
