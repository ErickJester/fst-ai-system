// @vitest-environment jsdom
import React from 'react'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import BotonDescargarImagen from './BotonDescargarImagen'
import { svgAPng } from '../../../lib/svgAPng'

vi.mock('../../../lib/svgAPng', () => ({ svgAPng: vi.fn() }))

const svgRef = { current: document.createElementNS('http://www.w3.org/2000/svg', 'svg') }

beforeEach(() => {
  vi.mocked(svgAPng).mockReset()
  URL.createObjectURL = vi.fn(() => 'blob:falso')
  URL.revokeObjectURL = vi.fn()
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('BotonDescargarImagen', () => {
  it('estado normal: botón habilitado con el texto «Descargar imagen» y sin alerta', () => {
    render(<BotonDescargarImagen svgRef={svgRef} nombre="grafica" />)
    const boton = screen.getByRole('button', { name: 'Descargar imagen' })
    expect(boton.disabled).toBe(false)
    expect(boton.classList.contains('btn') && boton.classList.contains('btn-secondary')).toBe(true)
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('mientras prepara, queda deshabilitado con «Preparando…»', async () => {
    let resolver
    vi.mocked(svgAPng).mockReturnValue(new Promise((r) => { resolver = r }))
    render(<BotonDescargarImagen svgRef={svgRef} nombre="grafica" />)

    fireEvent.click(screen.getByRole('button', { name: 'Descargar imagen' }))

    const boton = await screen.findByRole('button', { name: 'Preparando…' })
    expect(boton.disabled).toBe(true)

    resolver(new Blob(['png'], { type: 'image/png' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Descargar imagen' }).disabled).toBe(false))
  })

  it('éxito: descarga nombre.png con un enlace temporal y libera la URL', async () => {
    vi.mocked(svgAPng).mockResolvedValue(new Blob(['png'], { type: 'image/png' }))
    const clic = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    render(<BotonDescargarImagen svgRef={svgRef} nombre="barras-conducta" />)

    fireEvent.click(screen.getByRole('button', { name: 'Descargar imagen' }))

    await waitFor(() => expect(clic).toHaveBeenCalledTimes(1))
    expect(svgAPng).toHaveBeenCalledWith(svgRef.current)
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
    expect(clic.mock.contexts[0].download).toBe('barras-conducta.png')
    expect(clic.mock.contexts[0].href).toContain('blob:falso')
    expect(document.querySelector('a[download]')).toBeNull()
    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:falso'), { timeout: 3000 })
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('error: muestra el aviso debajo y el botón vuelve a quedar habilitado', async () => {
    vi.mocked(svgAPng).mockRejectedValue(new Error('falla'))
    render(<BotonDescargarImagen svgRef={svgRef} nombre="grafica" />)

    fireEvent.click(screen.getByRole('button', { name: 'Descargar imagen' }))

    const aviso = await screen.findByRole('alert')
    expect(aviso.textContent).toBe('No se pudo preparar la imagen. Inténtalo de nuevo.')
    expect(aviso.classList.contains('hint')).toBe(true)
    expect(screen.getByRole('button', { name: 'Descargar imagen' }).disabled).toBe(false)
    expect(URL.createObjectURL).not.toHaveBeenCalled()
  })
})
