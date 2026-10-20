// @vitest-environment jsdom
// Eliminar una tanda: quién puede, la confirmación y los errores del servidor.
import React from 'react'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, cleanup, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

vi.mock('./services/experiments', () => ({ deleteTanda: vi.fn() }))

import { deleteTanda } from './services/experiments'
import EliminarTanda, { puedeEliminarTanda } from './components/EliminarTanda'

const tanda = { letra: 'A', id_tanda: 21 }

// Con llaves: un beforeEach que devuelve algo lo toma vitest como su limpieza y lo ejecutaría (aquí, el mock) al terminar.
beforeEach(() => { deleteTanda.mockReset() })
afterEach(cleanup)

describe('quién puede eliminar una tanda', () => {
  const exp = { creador_id: '2019630177' }

  it('el creador del experimento y un Administrador sí; otra cuenta no', () => {
    expect(puedeEliminarTanda({ id: '2019630177', admin: false }, exp, tanda)).toBe(true)
    expect(puedeEliminarTanda({ id: '2021000001', admin: true }, exp, tanda)).toBe(true)
    expect(puedeEliminarTanda({ id: '2021000001', admin: false }, exp, tanda)).toBe(false)
  })

  it('sin id de tanda (datos de ejemplo) no se ofrece, ni al Administrador', () => {
    expect(puedeEliminarTanda({ id: '1', admin: true }, exp, { letra: 'A', id_tanda: null })).toBe(false)
  })
})

describe('diálogo para eliminar una tanda', () => {
  const abrir = (props) => render(<EliminarTanda clave="7" grupo="Control" tanda={tanda} onCerrar={() => {}} onEliminada={() => {}} {...props} />)

  it('pide confirmación explícita y no borra nada hasta confirmar', () => {
    abrir()
    expect(screen.getByText(/Se borrarán el video del Día 1, el del Día 2 y sus resultados\. No se puede deshacer\./)).toBeTruthy()
    expect(screen.getByText(/Tanda A de «Control»/)).toBeTruthy()
    expect(deleteTanda).not.toHaveBeenCalled()
  })

  it('el foco empieza en «Cancelar», la opción segura, y Cancelar no borra', async () => {
    const onCerrar = vi.fn()
    abrir({ onCerrar })
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Cancelar' }))
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(onCerrar).toHaveBeenCalledTimes(1)
    expect(deleteTanda).not.toHaveBeenCalled()
  })

  it('al confirmar pide eliminar esa tanda y avisa', async () => {
    deleteTanda.mockResolvedValue(undefined)
    const onEliminada = vi.fn()
    abrir({ onEliminada })
    await userEvent.click(screen.getByRole('button', { name: 'Eliminar tanda' }))
    await waitFor(() => expect(onEliminada).toHaveBeenCalledWith(tanda))
    expect(deleteTanda).toHaveBeenCalledWith('7', 21)
  })

  it('muestra el 409 del servidor y no cierra', async () => {
    // Con mockImplementation el rechazo se crea al llamar (con mockRejectedValue se crearía antes y vitest lo daría por no atendido).
    deleteTanda.mockImplementation(() => Promise.reject({ response: { status: 409, data: { error: 'No se puede eliminar mientras su análisis está procesando o en pausa' } } }))
    const onEliminada = vi.fn()
    abrir({ onEliminada })
    await userEvent.click(screen.getByRole('button', { name: 'Eliminar tanda' }))
    expect(await screen.findByText(/No se puede eliminar mientras su análisis está procesando o en pausa/)).toBeTruthy()
    expect(onEliminada).not.toHaveBeenCalled()
  })

  it('muestra el 403 del servidor', async () => {
    deleteTanda.mockImplementation(() => Promise.reject({ response: { status: 403, data: { error: 'Solo quien creó el experimento o un Administrador puede eliminar una tanda' } } }))
    abrir()
    await userEvent.click(screen.getByRole('button', { name: 'Eliminar tanda' }))
    expect(await screen.findByText(/Solo quien creó el experimento o un Administrador puede eliminar una tanda/)).toBeTruthy()
  })
})
