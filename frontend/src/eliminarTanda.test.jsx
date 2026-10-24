// @vitest-environment jsdom
// Eliminar una tanda: quién puede, la confirmación y los errores del servidor.
import React from 'react'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, cleanup, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'

vi.mock('./services/experiments', () => ({ deleteTanda: vi.fn() }))
// Para probar la pantalla del grupo: sus datos, la sesión y el encabezado se simulan.
vi.mock('./services/groups', () => ({ getGroup: vi.fn() }))
vi.mock('./contexts/AuthContext', () => ({ useAuth: () => ({ user: { id: '2019630177', admin: false } }) }))
vi.mock('./components/Topbar', () => ({ default: ({ children }) => <div>{children}</div>, Brand: () => null }))

import { deleteTanda } from './services/experiments'
import { getGroup } from './services/groups'
import EliminarTanda, { puedeEliminarTanda } from './components/EliminarTanda'
import GrupoPage from './pages/GrupoPage'

const tanda = { letra: 'A', id_tanda: 21 }

// Con llaves: un beforeEach que devuelve algo lo toma vitest como su limpieza y lo ejecutaría (aquí, el mock) al terminar.
beforeEach(() => { deleteTanda.mockReset(); getGroup.mockReset() })
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

  it('si es la última tanda del grupo, avisa que el grupo también se elimina; si no, no', () => {
    abrir({ ultima: true })
    expect(screen.getByText('Es la única tanda de este grupo: el grupo también se eliminará.')).toBeTruthy()
    cleanup()
    abrir()
    expect(screen.queryByText(/el grupo también se eliminará/)).toBeNull()
  })
})

// La pantalla del grupo: con una sola tanda, al eliminarla el grupo ya no existe y se vuelve al experimento; con más, se
// recarga el mismo grupo.
describe('eliminar desde el detalle del grupo', () => {
  const tandaDe = (letra, id) => ({
    letra, id_tanda: id, n_cilindros: 4, estado: 'SIN_VIDEO', dia1: true, fecha_dia1: '2026-10-20', fecha_dia2: '2026-10-21',
  })
  const grupo = (tandas) => ({
    id: 3, nombre: 'Control', tipo: 'CONTROL', tratamiento: 'Salina', n_especimenes: 4 * tandas.length,
    experimento: { clave: '7', titulo: 'Experimento de prueba', creador_id: '2019630177' }, tandas,
  })
  const abrirGrupo = (tandas) => {
    getGroup.mockResolvedValue(grupo(tandas))
    render(
      <MemoryRouter initialEntries={['/experimentos/7/grupos/3']}>
        <Routes>
          <Route path="/experimentos/:clave/grupos/:gid" element={<GrupoPage />} />
          <Route path="/experimentos/:clave" element={<div>Detalle del experimento</div>} />
        </Routes>
      </MemoryRouter>
    )
  }

  it('con una sola tanda: el diálogo avisa que el grupo se elimina y, al eliminar, navega al experimento sin recargar el grupo', async () => {
    deleteTanda.mockResolvedValue(undefined)
    abrirGrupo([tandaDe('A', 21)])
    await userEvent.click(await screen.findByRole('button', { name: 'Eliminar tanda' }))
    const dialogo = screen.getByRole('dialog')
    expect(within(dialogo).getByText('Es la única tanda de este grupo: el grupo también se eliminará.')).toBeTruthy()
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Eliminar tanda' }))
    expect(await screen.findByText('Detalle del experimento')).toBeTruthy()
    expect(deleteTanda).toHaveBeenCalledWith('7', 21)
    expect(getGroup).toHaveBeenCalledTimes(1)
  })

  it('con más tandas: sin aviso del grupo y, al eliminar, se recarga el mismo grupo', async () => {
    deleteTanda.mockResolvedValue(undefined)
    abrirGrupo([tandaDe('A', 21), tandaDe('B', 22)])
    await userEvent.click((await screen.findAllByRole('button', { name: 'Eliminar tanda' }))[0])
    const dialogo = screen.getByRole('dialog')
    expect(within(dialogo).queryByText(/el grupo también se eliminará/)).toBeNull()
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Eliminar tanda' }))
    await waitFor(() => expect(getGroup).toHaveBeenCalledTimes(2))
    expect(screen.queryByText('Detalle del experimento')).toBeNull()
  })
})
