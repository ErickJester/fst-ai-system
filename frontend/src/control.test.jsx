// @vitest-environment jsdom
// La imagen de control en la pantalla de un análisis con error: se pide solo si existe, una sola vez, y se muestra.
import React from 'react'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, cleanup, waitFor } from '@testing-library/react'

vi.mock('./services/queue', () => ({ getControl: vi.fn(), getDiagnostico: vi.fn() }))

import { getControl } from './services/queue'
import { ImagenControl } from './pages/ProgresoPage'

const trabajo = (extra) => ({ clave: 'EXP-1', job_id: 7, control_disponible: false, ...extra })

beforeEach(() => {
  URL.createObjectURL = vi.fn(() => 'blob:control')
  URL.revokeObjectURL = vi.fn()
  getControl.mockReset()
})
afterEach(cleanup)

describe('imagen de control del análisis con error', () => {
  it('con controlDisponible falso no se pide nada y no se muestra', async () => {
    const { container } = render(<ImagenControl j={trabajo()} />)
    await Promise.resolve()
    expect(getControl).not.toHaveBeenCalled()
    expect(container.querySelector('img')).toBeNull()
  })

  it('con controlDisponible verdadero se pide una vez y se muestra con su pie', async () => {
    getControl.mockResolvedValue(new Blob(['jpg'], { type: 'image/jpeg' }))
    const j = trabajo({ control_disponible: true })
    const { rerender } = render(<ImagenControl j={j} />)
    const img = await screen.findByRole('img')
    expect(img.getAttribute('src')).toBe('blob:control')
    expect(screen.getByText(/amarillo = zona medida de cada tubo encontrado, azul = línea de agua, verde = fondo/)).toBeTruthy()
    expect(img.closest('a').getAttribute('target')).toBe('_blank')   // clic: se abre grande en otra pestaña
    // La cola se consulta cada 5 s y trae un objeto nuevo del mismo análisis: no se vuelve a pedir la imagen.
    rerender(<ImagenControl j={{ ...j }} />)
    expect(getControl).toHaveBeenCalledTimes(1)
    expect(getControl).toHaveBeenCalledWith({ clave: 'EXP-1', job_id: 7 })
  })

  it('al salir revoca la URL de objeto', async () => {
    getControl.mockResolvedValue(new Blob(['jpg'], { type: 'image/jpeg' }))
    const { unmount } = render(<ImagenControl j={trabajo({ control_disponible: true })} />)
    await screen.findByRole('img')
    unmount()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:control')
  })

  it('si la imagen no se puede pedir, no se muestra nada ni truena', async () => {
    getControl.mockRejectedValue(new Error('404'))
    const { container } = render(<ImagenControl j={trabajo({ control_disponible: true })} />)
    await waitFor(() => expect(getControl).toHaveBeenCalledTimes(1))
    expect(container.querySelector('img')).toBeNull()
  })
})
