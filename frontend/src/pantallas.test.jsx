// @vitest-environment jsdom
// Pruebas de las pantallas: la app completa, usada como lo haría una persona
// (escribir, hacer clic, leer lo que aparece). Usan los datos de ejemplo.
import React from 'react'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, cleanup, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from './contexts/AuthContext'
import App from './App'
import { reiniciarDatos, experiments, users, canceledJobs } from './services/mocks/data'
import { createExperiment } from './services/experiments'
import axe from 'axe-core'

// Solo en la prueba del primer acceso: el cambio de contraseña responde lo que diga fallarCambio (si hay); si no, el mock real.
let fallarCambio = null
vi.mock('./services/auth', async (importOriginal) => {
  const real = await importOriginal()
  return { ...real, changePassword: (...args) => (fallarCambio ? fallarCambio() : real.changePassword(...args)) }
})

// Lo que jsdom no trae: desplazar la ventana, URLs de archivos y canvas.
window.scrollTo = vi.fn()
URL.createObjectURL = vi.fn(() => 'blob:prueba')
URL.revokeObjectURL = vi.fn()
HTMLCanvasElement.prototype.getContext = () => null // las líneas de tiempo de la revisión no se dibujan

function abrir(ruta = '/login') {
  const user = userEvent.setup()
  render(
    <MemoryRouter initialEntries={[ruta]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>
  )
  return user
}

async function entrar(user, correo, password = 'x') {
  await user.type(screen.getByLabelText('Correo electrónico'), correo)
  await user.type(screen.getByLabelText('Contraseña'), password)
  await user.click(screen.getByRole('button', { name: 'Entrar' }))
}

// Inicia sesión y abre directamente otra ruta (la sesión queda en localStorage).
async function conSesion(ruta, correo = 'mrivera@ipn.mx') {
  const user = abrir('/login')
  await entrar(user, correo)
  await screen.findByRole('heading', { name: 'Experimentos' })
  cleanup()
  return abrir(ruta)
}

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  reiniciarDatos()
})
afterEach(() => cleanup())

describe('acceso', () => {
  it('entra con una cuenta de ejemplo y llega a Experimentos', async () => {
    const user = abrir('/login')
    await entrar(user, 'mrivera@ipn.mx')
    expect(await screen.findByRole('heading', { name: 'Experimentos' })).toBeTruthy()
    expect(await screen.findByText('Compuesto CSR-14 · curva de dosis')).toBeTruthy() // la lista llega después del título
  })

  it('marca el correo que no tiene forma de correo', async () => {
    const user = abrir('/login')
    await entrar(user, 'alguien@gmail')
    expect(await screen.findByText('Escribe un correo electrónico válido.')).toBeTruthy()
    expect(screen.getByLabelText('Correo electrónico').className).toContain('error')
  })

  it('sin sesión, cualquier ruta lleva al login', async () => {
    abrir('/experimentos/7')
    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeTruthy()
  })

  it('una cuenta nueva pasa por el primer acceso antes de entrar', async () => {
    const user = abrir('/login')
    await entrar(user, 'nueva.cuenta@ipn.mx', 'temporal-1')
    expect(await screen.findByRole('heading', { name: 'Cambiar contraseña' })).toBeTruthy()
    await user.type(screen.getByLabelText('Contraseña temporal'), 'temporal-1')
    await user.type(screen.getByLabelText('Nueva contraseña'), 'mi-clave-123')
    await user.type(screen.getByLabelText('Confirmar nueva contraseña'), 'mi-clave-123')
    await user.click(screen.getByRole('button', { name: 'Guardar y entrar' }))
    expect(await screen.findByRole('heading', { name: 'Experimentos' })).toBeTruthy()
  })

  it('recuperar la contraseña con el enlace y entrar con la nueva', async () => {
    const user = abrir('/login')
    await user.click(screen.getByText('Olvidé mi contraseña'))
    await user.type(screen.getByLabelText('Correo electrónico'), 'mrivera@ipn.mx')
    await user.click(screen.getByRole('button', { name: 'Enviar enlace' }))
    expect(await screen.findByText('Si el correo está registrado, recibirás un enlace.')).toBeTruthy()

    await user.click(screen.getByText('abrir el enlace que llegaría por correo'))
    await user.type(await screen.findByLabelText('Nueva contraseña'), 'clave-nueva-9')
    await user.type(screen.getByLabelText('Confirmar nueva contraseña'), 'clave-nueva-9')
    await user.click(screen.getByRole('button', { name: 'Guardar contraseña' }))
    expect(await screen.findByRole('heading', { name: 'Contraseña actualizada' })).toBeTruthy()

    await user.click(screen.getByRole('link', { name: 'Iniciar sesión' }))
    await entrar(user, 'mrivera@ipn.mx', 'clave-nueva-9')
    expect(await screen.findByRole('heading', { name: 'Experimentos' })).toBeTruthy()
  })
})

describe('administración', () => {
  it('crear y editar cuenta se hacen en un modal que se cierra con Escape (§9.6)', async () => {
    const user = await conSesion('/admin', 'creyesl@ipn.mx')
    await user.click(await screen.findByRole('button', { name: 'Crear cuenta' }))
    const dialogo = screen.getByRole('dialog', { name: 'Crear cuenta' })
    expect(document.activeElement).toBe(within(dialogo).getByLabelText('Nombre'))
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()
    await user.click(screen.getAllByRole('button', { name: 'Editar' })[0])
    expect(screen.getByRole('dialog', { name: /^Editar cuenta/ })).toBeTruthy()
  })

  it('filtra las cuentas por rol y estado, con cuántos experimentos creó cada una (§9.6)', async () => {
    const user = await conSesion('/admin', 'creyesl@ipn.mx')
    await screen.findByRole('columnheader', { name: 'Experimentos' })
    expect(screen.getAllByRole('row')).toHaveLength(5) // encabezado + 4 cuentas
    await user.selectOptions(screen.getByLabelText('Rol'), 'ADMIN')
    const filas = screen.getAllByRole('row')
    expect(filas).toHaveLength(2)
    expect(within(filas[1]).getByText('creyesl@ipn.mx')).toBeTruthy()
    expect(within(filas[1]).getByText('2')).toBeTruthy() // creó dos experimentos de ejemplo
    await user.selectOptions(screen.getByLabelText('Rol'), '')
    await user.selectOptions(screen.getByLabelText('Estado'), 'inactiva')
    expect(screen.getByText('Ninguna cuenta coincide con los filtros.')).toBeTruthy()
    expect(screen.getAllByText('sin duración mínima')).toHaveLength(4) // las cuatro conductas (RF-18)
  })

  // Abre «Editar cuenta» de la fila de este correo.
  async function editar(user, correo) {
    const fila = (await screen.findByText(correo)).closest('tr')
    await user.click(within(fila).getByRole('button', { name: 'Editar' }))
    return screen.getByRole('dialog', { name: /^Editar cuenta/ })
  }

  it('restablecer la temporal de una cuenta activa pide confirmación y la muestra una sola vez', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = await conSesion('/admin', 'creyesl@ipn.mx')
    const dialogo = await editar(user, 'mrivera@ipn.mx')
    expect(within(dialogo).queryByText('Reactiva la cuenta para darle una contraseña temporal nueva.')).toBeNull()
    await user.click(within(dialogo).getByRole('button', { name: 'Restablecer contraseña temporal' }))
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('¿Restablecer la contraseña temporal de'))
    const nueva = await screen.findByRole('dialog', { name: 'Contraseña temporal nueva' })
    expect(within(nueva).getByText(/Dale esta contraseña temporal nueva/)).toBeTruthy()
    window.confirm.mockRestore()
  })

  it('una cuenta desactivada no trae el botón: pide reactivarla', async () => {
    users.find((u) => u.correo === 'lortega@ipn.mx').activo = false
    const user = await conSesion('/admin', 'creyesl@ipn.mx')
    const dialogo = await editar(user, 'lortega@ipn.mx')
    expect(within(dialogo).queryByRole('button', { name: 'Restablecer contraseña temporal' })).toBeNull()
    expect(within(dialogo).getByText('Reactiva la cuenta para darle una contraseña temporal nueva.')).toBeTruthy()
  })

  it('crear una cuenta muestra la contraseña temporal una sola vez', async () => {
    const user = abrir('/login')
    await entrar(user, 'creyesl@ipn.mx')
    await user.click(await screen.findByRole('link', { name: 'Administración' }))
    await user.click(await screen.findByRole('button', { name: 'Crear cuenta' }))
    await user.type(screen.getByLabelText('Nombre'), 'Andrea')
    await user.type(screen.getByLabelText('Apellidos'), 'Barrera Solís')
    await user.type(screen.getByLabelText('Correo electrónico'), 'abarrera@ipn.mx')
    await user.type(screen.getByLabelText(/Identificador institucional/), '2021630154')
    await user.click(screen.getByRole('button', { name: 'Guardar' }))

    const dialogo = await screen.findByRole('dialog', { name: 'Cuenta creada' })
    expect(within(dialogo).getByText(/no se volverá a mostrar/)).toBeTruthy()
    await user.keyboard('{Escape}') // no se cierra con Escape
    expect(screen.getByRole('dialog')).toBeTruthy()
    await user.click(within(dialogo).getByRole('button', { name: 'Listo' }))
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.getByText('A. Barrera Solís')).toBeTruthy()
  })
})

describe('cargar video', () => {
  it('solo quien creó el experimento sube sus videos; un Administrador no (D-107)', async () => {
    await conSesion('/experimentos/7/cargar', 'creyesl@ipn.mx')
    expect(await screen.findByText(/Solo quien creó el experimento sube sus videos/)).toBeTruthy()
    expect(screen.queryByRole('combobox')).toBeNull()
    cleanup()
    abrir('/experimentos/7')
    const boton = await screen.findByRole('button', { name: 'Cargar videos pendientes' })
    expect(boton.getAttribute('aria-disabled')).toBe('true')
  })

  // Elige Referencia con el teclado, confirma la tanda C y adjunta el archivo.
  async function prepararTandaC(user, nombre) {
    await user.type(await screen.findByRole('combobox'), 'fluox')
    await user.keyboard('{ArrowDown}{Enter}')
    await user.click(screen.getByRole('button', { name: 'Sí, es la C' }))
    await user.upload(document.querySelector('input[type=file]'), new File(['video'], nombre, { type: 'video/mp4' }))
    await screen.findByText(nombre)
  }

  it('elegir el grupo con el teclado, confirmar la tanda y encolar el video', async () => {
    const user = await conSesion('/experimentos/7/cargar')
    const grupo = await screen.findByRole('combobox')
    await user.type(grupo, 'fluox')
    await user.keyboard('{ArrowDown}{Enter}')
    expect(grupo.value).toBe('Referencia · Fluoxetina 10 mg/kg')
    expect(screen.getByLabelText('Tanda').value).toBe('C')

    await user.click(screen.getByRole('button', { name: 'Sí, es la C' }))
    const archivo = new File(['video'], 'tanda_C.mp4', { type: 'video/mp4' })
    await user.upload(document.querySelector('input[type=file]'), archivo)
    await screen.findByText('tanda_C.mp4')
    await user.click(screen.getByRole('button', { name: 'Guardar y encolar análisis' }))
    // Con el Día 2, redirige a Progreso (§9.3).
    expect(await screen.findByRole('heading', { name: 'Análisis en curso' })).toBeTruthy()
    expect(screen.getByText(/El análisis está en cola: Referencia · Fluoxetina 10 mg\/kg · Tanda C · Día 2/)).toBeTruthy()
  })

  it('el video de Día 1 se guarda sin encolar análisis y vuelve al experimento (D-71)', async () => {
    const user = await conSesion('/experimentos/7/cargar')
    await screen.findByRole('combobox')
    await user.click(screen.getByRole('button', { name: 'Día 1 (20 min)' }))
    await prepararTandaC(user, 'tanda_C_dia1.mp4')
    await user.click(screen.getByRole('button', { name: 'Guardar video' }))
    expect(await screen.findByRole('heading', { name: 'Compuesto CSR-14 · curva de dosis' })).toBeTruthy()
    expect(screen.getByText(/Video del Día 1 almacenado .*No se analiza\./)).toBeTruthy()
  })

  it('avisa si la tanda ya tiene el video de esa sesión y no deja guardar', async () => {
    const user = await conSesion('/experimentos/7/cargar')
    await user.type(await screen.findByRole('combobox'), 'fluox')
    await user.keyboard('{ArrowDown}{Enter}')
    const campoTanda = screen.getByLabelText('Tanda')
    await user.clear(campoTanda)
    await user.type(campoTanda, 'A')
    await user.click(screen.getByRole('button', { name: 'Sí, es la A' }))
    expect(screen.getByText(/La Tanda A de Referencia · Fluoxetina 10 mg\/kg ya tiene su video de Día 2/)).toBeTruthy()
    await user.upload(document.querySelector('input[type=file]'), new File(['video'], 'repetido.mp4', { type: 'video/mp4' }))
    expect(screen.getByRole('button', { name: 'Guardar y encolar análisis' }).disabled).toBe(true)
    await user.click(screen.getByRole('button', { name: 'Día 1 (20 min)' }))
    expect(screen.queryByText(/ya tiene su video de/)).toBeNull()
  })

  it('rechaza antes de subir un archivo más grande que el límite', async () => {
    const user = await conSesion('/experimentos/7/cargar')
    await screen.findByRole('combobox')
    const grande = new File(['video'], 'enorme.mp4', { type: 'video/mp4' })
    Object.defineProperty(grande, 'size', { value: 3 * 1024 * 1024 * 1024 })
    await user.upload(document.querySelector('input[type=file]'), grande)
    expect(await screen.findByText('El archivo pasa del límite de 2048 MB')).toBeTruthy()
  })

  it('«Terminar» sin subir nada vuelve al experimento', async () => {
    const user = await conSesion('/experimentos/7/cargar')
    await screen.findByRole('combobox')
    await user.click(screen.getByRole('button', { name: 'Terminar' }))
    expect(await screen.findByRole('heading', { name: 'Compuesto CSR-14 · curva de dosis' })).toBeTruthy()
  })

  it('«Agregar otra tanda» pone otro formulario debajo y conserva lo que llevaba el primero', async () => {
    const user = await conSesion('/experimentos/7/cargar')
    const primero = await screen.findByRole('combobox')
    await user.type(primero, 'fluox')
    await user.keyboard('{ArrowDown}{Enter}')
    await user.click(screen.getByRole('button', { name: 'Sí, es la C' }))
    await user.upload(document.querySelector('input[type=file]'), new File(['video'], 'primera.mp4', { type: 'video/mp4' }))
    await screen.findByText('primera.mp4')

    await user.click(screen.getByRole('button', { name: '+ Agregar otra tanda' }))

    const grupos = screen.getAllByRole('combobox')
    expect(grupos).toHaveLength(2)
    // El primero no se tocó: grupo, tanda confirmada y archivo siguen ahí.
    expect(grupos[0].value).toBe('Referencia · Fluoxetina 10 mg/kg')
    expect(screen.getAllByLabelText('Tanda')[0].value).toBe('C')
    expect(screen.getByText('primera.mp4')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Sí, es la C' }).disabled).toBe(true)
    // El segundo viene debajo.
    expect(grupos[0].compareDocumentPosition(grupos[1]) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('«Agregar otra tanda» sigue en el mismo grupo: el formulario nuevo trae el grupo, la tanda que sigue y ya confirmada', async () => {
    const user = await conSesion('/experimentos/7/cargar')
    await user.type(await screen.findByRole('combobox'), 'fluox')
    await user.keyboard('{ArrowDown}{Enter}')
    await user.click(screen.getByRole('button', { name: 'Sí, es la C' }))

    await user.click(screen.getByRole('button', { name: '+ Agregar otra tanda' }))

    const [, segundo] = screen.getAllByRole('combobox')
    expect(segundo.value).toBe('Referencia · Fluoxetina 10 mg/kg')
    expect(screen.getAllByLabelText('Tanda')[1].value).toBe('D')
    // La D ya viene confirmada: solo falta el archivo.
    expect(screen.getByRole('button', { name: 'Sí, es la D' }).disabled).toBe(true)
    expect(screen.getByText(/¿Esta es la tanda D de Referencia/)).toBeTruthy()
    // Con dos formularios hay UN solo botón de guardar, y espera a que los dos tengan su archivo.
    const guardar = screen.getByRole('button', { name: 'Guardar y encolar análisis' })
    expect(guardar.disabled).toBe(true)
    await user.upload(document.querySelectorAll('input[type=file]')[1], new File(['video'], 'tanda_D.mp4', { type: 'video/mp4' }))
    await screen.findByText('tanda_D.mp4')
    expect(guardar.disabled).toBe(true)
    expect(screen.getByText(/Falta completar: el formulario 1\./)).toBeTruthy()
    await user.upload(document.querySelectorAll('input[type=file]')[0], new File(['video'], 'tanda_C.mp4', { type: 'video/mp4' }))
    await screen.findByText('tanda_C.mp4')
    expect(guardar.disabled).toBe(false)
  })
  it('si el formulario anterior aún no tiene grupo, el nuevo nace vacío', async () => {
    const user = await conSesion('/experimentos/7/cargar')
    await screen.findByRole('combobox')
    await user.click(screen.getByRole('button', { name: '+ Agregar otra tanda' }))
    const [uno, dos] = screen.getAllByRole('combobox')
    expect(uno.value).toBe('')
    expect(dos.value).toBe('')
    expect(screen.getAllByLabelText('Tanda').map((c) => c.value)).toEqual(['', ''])
    expect(screen.queryByRole('button', { name: /^Sí, es la/ })).toBeNull()
  })

  it('con un grupo nuevo, la otra tanda hereda el nombre y la B ya confirmada, sin pedir otra vez el tratamiento', async () => {
    const user = await conSesion('/experimentos/7/cargar')
    await user.type(await screen.findByRole('combobox'), 'Acu-YBN')
    await screen.findByText('Grupo nuevo · el nombre no coincide')
    await user.type(screen.getByLabelText('Tratamiento (sustancia y dosis)'), 'prueba')

    await user.click(screen.getByRole('button', { name: '+ Agregar otra tanda' }))

    const [, segundo] = screen.getAllByRole('combobox')
    expect(segundo.value).toBe('Acu-YBN')
    await waitFor(() => expect(screen.getAllByLabelText('Tanda')[1].value).toBe('B'))
    expect(screen.getByRole('button', { name: 'Sí, es la B' }).disabled).toBe(true)
    expect(screen.getAllByLabelText('Tratamiento (sustancia y dosis)')).toHaveLength(1)
    expect(screen.getByText(/se toman del otro formulario/)).toBeTruthy()
  })

  it('si en el formulario heredado se cambia el grupo, deja de heredar la tanda y su confirmación', async () => {
    const user = await conSesion('/experimentos/7/cargar')
    await user.type(await screen.findByRole('combobox'), 'fluox')
    await user.keyboard('{ArrowDown}{Enter}')
    await user.click(screen.getByRole('button', { name: 'Sí, es la C' }))
    await user.click(screen.getByRole('button', { name: '+ Agregar otra tanda' }))

    const segundo = screen.getAllByRole('combobox')[1]
    await user.clear(segundo)
    await user.type(segundo, 'contr')
    await user.keyboard('{ArrowDown}{Enter}')
    // Otro grupo: la tanda es la suya y hay que confirmarla; la del primer formulario sigue confirmada.
    expect(screen.getAllByLabelText('Tanda')[1].value).toBe('C')
    const botones = screen.getAllByRole('button', { name: 'Sí, es la C' })
    expect(botones).toHaveLength(2)
    expect(botones[0].disabled).toBe(true)
    expect(botones[1].disabled).toBe(false)
  })

  it('«Quitar este formulario» deja solo el otro, y con uno solo ya no se ofrece', async () => {
    const user = await conSesion('/experimentos/7/cargar')
    await screen.findByRole('combobox')
    expect(screen.queryByRole('button', { name: 'Quitar este formulario' })).toBeNull()
    await user.click(screen.getByRole('button', { name: '+ Agregar otra tanda' }))
    expect(screen.getAllByRole('button', { name: 'Quitar este formulario' })).toHaveLength(2)
    await user.click(screen.getAllByRole('button', { name: 'Quitar este formulario' })[1])
    expect(screen.getAllByRole('combobox')).toHaveLength(1)
    expect(screen.queryByRole('button', { name: 'Quitar este formulario' })).toBeNull()
  })

  it('dos formularios del mismo grupo existente no proponen la misma tanda', async () => {
    const user = await conSesion('/experimentos/7/cargar')
    await screen.findByRole('combobox')
    await user.click(screen.getByRole('button', { name: '+ Agregar otra tanda' }))
    for (const caja of screen.getAllByRole('combobox')) {
      await user.type(caja, 'fluox')
      await user.keyboard('{ArrowDown}{Enter}')
    }
    const letras = screen.getAllByLabelText('Tanda').map((c) => c.value)
    expect(letras).toEqual(['C', 'D'])
  })

  it('dos tandas de un grupo nuevo: la segunda hereda el nombre y la B, no pide otra vez el tratamiento, y un solo botón las sube en orden', async () => {
    const user = await conSesion('/experimentos/7/cargar')
    await user.type(await screen.findByRole('combobox'), 'Acu-YBN')
    await screen.findByText('Grupo nuevo · el nombre no coincide')
    await user.type(screen.getByLabelText('Tratamiento (sustancia y dosis)'), 'prueba')
    await user.click(screen.getByRole('button', { name: 'Sí, es la A' }))
    await user.upload(document.querySelector('input[type=file]'), new File(['video'], 'Acu-YBN-1.mov', { type: 'video/quicktime' }))
    await screen.findByText('Acu-YBN-1.mov')

    await user.click(screen.getByRole('button', { name: '+ Agregar otra tanda' }))

    // El segundo trae el grupo, la B ya confirmada y no vuelve a pedir tipo ni tratamiento.
    expect(screen.getAllByRole('combobox')[1].value).toBe('Acu-YBN')
    await waitFor(() => expect(screen.getAllByLabelText('Tanda')[1].value).toBe('B'))
    expect(screen.getByRole('button', { name: 'Sí, es la B' }).disabled).toBe(true)
    expect(screen.getAllByLabelText('Tratamiento (sustancia y dosis)')).toHaveLength(1)
    expect(screen.getByText(/se toman del otro formulario/)).toBeTruthy()
    await user.upload(document.querySelectorAll('input[type=file]')[1], new File(['video'], 'Acu-YBN-2.mov', { type: 'video/quicktime' }))
    await screen.findByText('Acu-YBN-2.mov')

    // Un solo botón: la A crea el grupo y la B cae ya en él, en ese orden.
    const botones = screen.getAllByRole('button', { name: 'Guardar y encolar análisis' })
    expect(botones).toHaveLength(1)
    await user.click(botones[0])
    expect(await screen.findByRole('heading', { name: 'Análisis en curso' })).toBeTruthy()
    expect(screen.getByText(/Los análisis están en cola: Acu-YBN · Tanda A · Día 2; Acu-YBN · Tanda B · Día 2\./)).toBeTruthy()
  })
  it('la misma tanda y sesión de un grupo en dos formularios se avisa y el botón no se habilita', async () => {
    const user = await conSesion('/experimentos/7/cargar')
    await screen.findByRole('combobox')
    await user.click(screen.getByRole('button', { name: '+ Agregar otra tanda' }))
    for (const caja of screen.getAllByRole('combobox')) {
      await user.type(caja, 'fluox')
      await user.keyboard('{ArrowDown}{Enter}')
    }
    await user.click(screen.getAllByRole('button', { name: 'Sí, es la C' })[0])
    await user.upload(document.querySelectorAll('input[type=file]')[0], new File(['video'], 'uno.mp4', { type: 'video/mp4' }))
    // La segunda propone la D; se la cambia a la C, que ya usa la primera.
    const segunda = screen.getAllByLabelText('Tanda')[1]
    await user.clear(segunda)
    await user.type(segunda, 'C')
    await user.click(screen.getAllByRole('button', { name: 'Sí, es la C' })[1])
    await user.upload(document.querySelectorAll('input[type=file]')[1], new File(['video'], 'repetida.mp4', { type: 'video/mp4' }))
    await screen.findByText('repetida.mp4')
    expect(screen.getByText(/Otro formulario de esta página ya usa la Tanda C/)).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Guardar y encolar análisis' }).disabled).toBe(true)
  })
  it('con dos formularios hay un solo botón: sube la C y luego la D, en ese orden, y lleva a Progreso', async () => {
    const user = await conSesion('/experimentos/7/cargar')
    await screen.findByRole('combobox')
    await user.type(screen.getByRole('combobox'), 'fluox')
    await user.keyboard('{ArrowDown}{Enter}')
    await user.click(screen.getByRole('button', { name: 'Sí, es la C' }))
    await user.upload(document.querySelector('input[type=file]'), new File(['video'], 'tanda_C.mp4', { type: 'video/mp4' }))
    await screen.findByText('tanda_C.mp4')
    await user.click(screen.getByRole('button', { name: '+ Agregar otra tanda' }))
    await user.upload(document.querySelectorAll('input[type=file]')[1], new File(['video'], 'tanda_D.mp4', { type: 'video/mp4' }))
    await screen.findByText('tanda_D.mp4')

    expect(screen.getAllByRole('button', { name: 'Guardar y encolar análisis' })).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: 'Guardar y encolar análisis' }))

    expect(await screen.findByRole('heading', { name: 'Análisis en curso' })).toBeTruthy()
    expect(screen.getByText(/Los análisis están en cola: Referencia · Fluoxetina 10 mg\/kg · Tanda C · Día 2; Referencia · Fluoxetina 10 mg\/kg · Tanda D · Día 2\./)).toBeTruthy()
  })

  it('si la primera tanda falla, la segunda no se sube; al corregirla el mismo botón sigue y termina', async () => {
    const user = await conSesion('/experimentos/7/cargar')
    await screen.findByRole('combobox')
    await user.type(screen.getByRole('combobox'), 'fluox')
    await user.keyboard('{ArrowDown}{Enter}')
    await user.click(screen.getByRole('button', { name: 'Sí, es la C' }))
    await user.upload(document.querySelector('input[type=file]'), new File(['video'], 'celular_vertical.mp4', { type: 'video/mp4' }))
    await screen.findByText('celular_vertical.mp4')
    await user.click(screen.getByRole('button', { name: '+ Agregar otra tanda' }))
    await user.upload(document.querySelectorAll('input[type=file]')[1], new File(['video'], 'tanda_D.mp4', { type: 'video/mp4' }))
    await screen.findByText('tanda_D.mp4')

    await user.click(screen.getByRole('button', { name: 'Guardar y encolar análisis' }))

    // La C la rechazó el servidor: se queda en la página, la D no se subió y conserva su archivo.
    expect(await screen.findByText('El servidor rechazó el video')).toBeTruthy()
    expect(screen.queryByRole('heading', { name: 'Análisis en curso' })).toBeNull()
    expect(screen.queryByText(/✓/)).toBeNull()
    expect(screen.getByText('tanda_D.mp4')).toBeTruthy()

    // Se corrige la C con otro archivo y el mismo botón termina las dos.
    await user.upload(document.querySelectorAll('input[type=file]')[0], new File(['video'], 'tanda_C_bien.mp4', { type: 'video/mp4' }))
    await screen.findByText('tanda_C_bien.mp4')
    await user.click(screen.getByRole('button', { name: 'Guardar y encolar análisis' }))
    expect(await screen.findByRole('heading', { name: 'Análisis en curso' })).toBeTruthy()
    expect(screen.getByText(/Tanda C · Día 2; .*Tanda D · Día 2\./)).toBeTruthy()
  })
  it('un solo formulario se comporta como antes: al guardar sale a Progreso y la siguiente carga empieza vacía', async () => {
    const user = await conSesion('/experimentos/7/cargar')
    await user.type(await screen.findByRole('combobox'), 'fluox')
    await user.keyboard('{ArrowDown}{Enter}')
    await user.click(screen.getByRole('button', { name: 'Sí, es la C' }))
    await user.upload(document.querySelector('input[type=file]'), new File(['video'], 'sola.mp4', { type: 'video/mp4' }))
    await screen.findByText('sola.mp4')
    await user.click(screen.getByRole('button', { name: 'Guardar y encolar análisis' }))
    expect(await screen.findByRole('heading', { name: 'Análisis en curso' })).toBeTruthy()
    cleanup()
    abrir('/experimentos/7/cargar')
    expect((await screen.findByRole('combobox')).value).toBe('')
    expect(screen.queryByText('sola.mp4')).toBeNull()
  })

  it('el navegador solo revisa la extensión; el servidor rechaza el video vertical (D-64)', async () => {
    const user = await conSesion('/experimentos/7/cargar')
    await screen.findByRole('combobox')
    await user.upload(document.querySelector('input[type=file]'), new File(['x'], 'notas.txt', { type: 'video/mp4' }), { applyAccept: false })
    expect(await screen.findByText('El archivo no es .mp4 ni .mov')).toBeTruthy()

    await prepararTandaC(user, 'celular_vertical.mp4')
    await user.click(screen.getByRole('button', { name: 'Guardar y encolar análisis' }))
    expect(await screen.findByText('El servidor rechazó el video')).toBeTruthy()
    expect(screen.getByText(/El video es vertical/)).toBeTruthy()
  })
})

describe('resultados', () => {
  it('muestra los especímenes de la tanda y la comparación entre grupos', async () => {
    const user = await conSesion('/experimentos/7/resultados?grupo=2&tanda=A')
    expect(await screen.findByRole('heading', { name: 'Resultados · Grupo referencia, Tanda A' })).toBeTruthy()
    expect(screen.getAllByText('Tanda A · Espécimen 1').length).toBeGreaterThan(0)
    await user.click(screen.getByRole('button', { name: 'Comparación entre grupos' }))
    expect(await screen.findByText('Sus tandas se analizaron con distinto detalle y no se pueden promediar juntas. Míralas por separado en el detalle.')).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Por espécimen' }))
    // Cuatro columnas de conducta, también la conducta activa (R-12).
    expect(screen.getAllByRole('columnheader', { name: 'Conducta activa' }).length).toBeGreaterThan(0)
    // «Sin ver» va aparte, al final, en los totales y en el desglose por minuto, con su explicación.
    expect(screen.getAllByRole('columnheader', { name: 'Sin ver' }).length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText(/los segundos en los que no se vio al espécimen o que la revisión marcó como no visibles/)).toBeTruthy()
  })

  it('los botones de reporte no generan archivos: explican lo que hará el backend', async () => {
    const user = await conSesion('/experimentos/7/resultados?grupo=2&tanda=A')
    await user.click(await screen.findByRole('button', { name: 'CSV' }))
    expect((await screen.findByText(/Reporte CSV de la Tanda A./)).parentElement.textContent).toMatch(/Lo prepara el sistema .*Tiempo \(min:seg\)/)
    expect(URL.createObjectURL).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'PDF · reporte de resultados' }))
    expect(screen.getByText(/Reporte PDF del experimento/)).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Entendido' }))
    expect(screen.queryByText(/Reporte PDF del experimento/)).toBeNull()
  })

  it('ver el grupo completo: todas sus tandas completadas, con los estadísticos recalculados (D-93)', async () => {
    const user = await conSesion('/experimentos/7/resultados?grupo=2&tanda=A')
    const filas = async () => (await screen.findAllByRole('row')).filter((f) => /Espécimen \d/.test(f.textContent))
    expect(await filas()).toHaveLength(4)
    const media = () => screen.getByRole('row', { name: /^Media/ }).textContent
    const mediaTanda = media()
    await user.click(screen.getByRole('link', { name: 'Grupo completo · 8 especímenes' }))
    expect(await screen.findByRole('heading', { name: 'Resultados · Grupo referencia, grupo completo' })).toBeTruthy()
    expect(await filas()).toHaveLength(8)
    expect(screen.getByRole('cell', { name: /Tanda B · Espécimen 4/ })).toBeTruthy()
    expect(media()).not.toBe(mediaTanda)
    expect(screen.getByText('8 de 8')).toBeTruthy()
    expect(screen.queryByRole('link', { name: 'Revisar segundo a segundo' })).toBeNull() // la revisión es por tanda
    await user.click(screen.getByRole('button', { name: 'CSV' }))
    expect(screen.getByText(/Reporte CSV del grupo completo\./)).toBeTruthy()
  })

  it('en el grupo completo, las tandas sin análisis completado se señalan como faltantes', async () => {
    await conSesion('/experimentos/7/resultados?grupo=1')
    expect(await screen.findByRole('heading', { name: 'Resultados · Grupo control, grupo completo' })).toBeTruthy()
    expect(screen.getByText(/Faltan Tanda B \(en pausa\)/)).toBeTruthy()
    expect(screen.getByText('4 de 8')).toBeTruthy()
  })

  it('cada conducta con su porcentaje, y el desglose por minuto de cualquier espécimen', async () => {
    const user = await conSesion('/experimentos/7/resultados?grupo=2&tanda=A')
    const fila = await screen.findByRole('row', { name: /^1 Tanda A · Espécimen 1/ })
    expect(fila.textContent).toMatch(/2:51 57 %/) // 171 s de 300 de nado activo
    await user.click(screen.getByRole('button', { name: 'Minuto a minuto' }))
    await user.selectOptions(screen.getByLabelText(/^Desglose por minuto/), 'A-3')
    expect(screen.getByLabelText(/^Desglose por minuto/).closest('div').textContent).toMatch(/Tanda A · Espécimen 3/)
    const minutos = screen.getAllByRole('row').filter((f) => /^\d:\d\d–\d:\d\d/.test(f.textContent))
    expect(minutos).toHaveLength(5)
  })

  it('los bloques de 5 s los calcula el backend: sin él, la sección lo explica', async () => {
    const user = await conSesion('/experimentos/7/resultados?grupo=1&tanda=A')
    await user.click(await screen.findByRole('button', { name: 'Cada 5 segundos' }))
    expect((await screen.findByText(/^Bloques de 5 s\.$/)).parentElement.textContent).toMatch(/Los calcula el sistema/)
  })
})

describe('eliminar experimento (D-63)', () => {
  it('desde el detalle pide solo confirmación; con un análisis procesando, no se puede (D-87)', async () => {
    const user = await conSesion('/experimentos/7')
    await user.click(await screen.findByRole('button', { name: 'Eliminar experimento…' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Eliminar experimento' })
    expect(within(dialogo).getByText(/¿Eliminar «Compuesto CSR-14 · curva de dosis»\?/)).toBeTruthy()
    expect(within(dialogo).queryByRole('textbox')).toBeNull()
    expect(dialogo.querySelector('input[type=password]')).toBeNull()
    expect(document.activeElement).toBe(within(dialogo).getByRole('button', { name: 'Cancelar' }))

    await user.click(within(dialogo).getByRole('button', { name: 'Eliminar experimento' }))
    expect(await within(dialogo).findByText(/No se puede eliminar mientras un análisis está procesando o en pausa/)).toBeTruthy()
  })

  it('desde la lista, solo en los experimentos propios (o como Administrador)', async () => {
    const user = await conSesion('/experimentos')
    await screen.findByRole('button', { name: 'Eliminar Extracto de Valeriana officinalis' })
    expect(screen.queryByRole('button', { name: 'Eliminar Extracto de Salvia · dosis única' })).toBeNull() // lo creó J. Domínguez
    await user.click(screen.getByRole('button', { name: 'Eliminar Extracto de Valeriana officinalis' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Eliminar experimento' })
    await user.click(within(dialogo).getByRole('button', { name: 'Eliminar experimento' }))
    expect(await screen.findByText('Se eliminó «Extracto de Valeriana officinalis».')).toBeTruthy()
    // Ya no está en la tabla (ni en el aviso de vencimiento).
    expect(screen.queryByRole('button', { name: 'Eliminar Extracto de Valeriana officinalis' })).toBeNull()
    expect(screen.queryByText(/Los videos de/)).toBeNull()
  })
})

describe('sesión', () => {
  it('se guarda en localStorage, que sobrevive a cerrar la pestaña (token de larga duración)', async () => {
    await conSesion('/experimentos')
    expect(localStorage.getItem('fst.token')).toMatch(/^mock-token-/)
    expect(sessionStorage.getItem('fst.token')).toBeNull()
  })
})

describe('sesión: versión y borradores', () => {
  it('el menú de usuario muestra la versión y cerrar sesión borra los borradores de la revisión', async () => {
    const user = await conSesion('/experimentos')
    localStorage.setItem('fst.revision.1.7.402', '{}')
    await user.click(await screen.findByRole('button', { name: 'Menú de usuario' }))
    expect(screen.getByText(/^FST v\d+\.\d+\.\d+$/)).toBeTruthy()
    await user.click(screen.getByText('Cerrar sesión'))
    expect(localStorage.getItem('fst.revision.1.7.402')).toBeNull()
  })
})

describe('progreso', () => {
  it('los análisis completados habilitan «Ver resultados» y «Revisar segundo a segundo» (§9.4)', async () => {
    await conSesion('/analisis')
    expect(await screen.findByText('Completados recientemente')).toBeTruthy()
    expect(screen.getAllByRole('link', { name: 'Ver resultados' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Revisar segundo a segundo' }).length).toBeGreaterThan(0)
  })

  it('quien no subió los videos no ve «Revisar segundo a segundo»', async () => {
    await conSesion('/analisis', 'jdominguez@ipn.mx')
    expect(await screen.findByText('Completados recientemente')).toBeTruthy()
    expect(screen.queryByRole('link', { name: 'Revisar segundo a segundo' })).toBeNull()
  })

  it('el análisis cancelado dice el motivo solo si el backend lo manda (D-112)', async () => {
    // Sin entrada en canceledJobs el motivo es null: la tarjeta no supone ninguno.
    await conSesion('/analisis')
    expect(await screen.findByText(/El análisis se canceló\. No se guardó/)).toBeTruthy()
    cleanup()

    canceledJobs.push({ job_id: 505, motivo: 'plazo' })
    abrir('/analisis') // la sesión sigue guardada: no hace falta entrar otra vez
    expect(await screen.findByText(/Pasaron 72 horas en pausa sin respuesta y el sistema canceló el análisis/)).toBeTruthy()
    cleanup()

    canceledJobs[0].motivo = 'usuario'
    abrir('/analisis')
    expect(await screen.findByText(/Se canceló desde la pantalla de pausa/)).toBeTruthy()
  })
})

describe('grupo', () => {
  it('avisa cuando el video de una tanda ya se borró', async () => {
    await conSesion('/experimentos/7/grupos/3')
    expect(await screen.findByText(/El video ya se borró/)).toBeTruthy()
  })

  it('lleva a los resultados del grupo completo o de cada tanda', async () => {
    await conSesion('/experimentos/7/grupos/1')
    expect((await screen.findByRole('link', { name: 'Ver resultados del grupo completo' })).getAttribute('href')).toBe('/experimentos/7/resultados?grupo=1')
    expect(screen.getByText('Reúne las tandas con análisis completado: 1 de 2')).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Ver resultados de esta tanda' }).getAttribute('href')).toBe('/experimentos/7/resultados?grupo=1&tanda=A')
    expect(screen.getByText('Guardado')).toBeTruthy() // el Día 1 se guarda y no se analiza
  })
})

describe('pausa del análisis (R-15)', () => {
  // El trabajo 502 (Control · Tanda B) está en pausa en los datos de ejemplo.
  it('muestra la imagen con el recuadro, el tiempo que queda y ajusta', async () => {
    const user = await conSesion('/experimentos/7/analisis/502/pausa')
    expect(await screen.findByRole('heading', { name: 'No se ve al espécimen del cilindro 2 (segundo 142)' })).toBeTruthy()
    expect(screen.getByRole('timer').textContent).toMatch(/Tiempo para responder: (72 h 0|71 h 59) min/)
    const izquierda = screen.getByLabelText('Izquierda (px)')
    await user.clear(izquierda)
    await user.type(izquierda, '370')
    expect(screen.getByLabelText(/Recuadro del cilindro 2: x 370–/)).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Ajustar y continuar' }))
    expect(await screen.findByRole('heading', { name: 'Análisis en curso' })).toBeTruthy()
    expect(screen.getByText(/Recuadro del cilindro 2 ajustado\. El análisis volvió a la cola/)).toBeTruthy()
  })

  it('cancelar pide confirmación; después la pausa ya no está', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = await conSesion('/experimentos/7/analisis/502/pausa')
    await user.click(await screen.findByRole('button', { name: 'Cancelar análisis' }))
    expect(window.confirm).toHaveBeenCalled()
    expect(await screen.findByText(/Análisis cancelado: Compuesto CSR-14 · Control · Tanda B/)).toBeTruthy()
    expect(screen.getByText(/Se canceló desde la pantalla de pausa/)).toBeTruthy()
    window.confirm.mockRestore()

    cleanup()
    abrir('/experimentos/7/analisis/502/pausa')
    expect(await screen.findByRole('heading', { name: 'Este análisis ya no está en pausa' })).toBeTruthy()
  })
})

describe('pausa: recuadro bien o casi igual (encargo 33)', () => {
  it('«El recuadro está bien, continuar» vuelve el análisis a la cola sin cambiar el recuadro', async () => {
    const user = await conSesion('/experimentos/7/analisis/502/pausa')
    await user.click(await screen.findByRole('button', { name: 'El recuadro está bien, continuar' }))
    expect(await screen.findByRole('heading', { name: 'Análisis en curso' })).toBeTruthy()
    expect(screen.getByText(/sigue desde el segundo 142\. No se volverá a pausar en ese mismo tramo/)).toBeTruthy()
  })

  it('ajustar sin mover el recuadro muestra el 422 en la misma pantalla con los tres botones', async () => {
    const user = await conSesion('/experimentos/7/analisis/502/pausa')
    await user.click(await screen.findByRole('button', { name: 'Ajustar y continuar' }))
    expect(await screen.findByText(/El recuadro casi no cambió: ningún borde se movió más de 3 px/)).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'No se ve al espécimen del cilindro 2 (segundo 142)' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Ajustar y continuar' }).disabled).toBe(false)
    expect(screen.getByRole('button', { name: 'El recuadro está bien, continuar' }).disabled).toBe(false)
    expect(screen.getByRole('button', { name: 'Cancelar análisis' }).disabled).toBe(false)
  })
})

describe('revisión segundo a segundo (R-14)', () => {
  it('en pausa, una tecla cambia solo ese segundo; guardar deja la revisión', async () => {
    const user = await conSesion('/experimentos/7/analisis/402/revision')
    expect(await screen.findByRole('heading', { name: 'Revisión segundo a segundo · Referencia, Tanda A' })).toBeTruthy()
    expect(screen.getByRole('tab', { name: /Espécimen 1/ }).getAttribute('aria-selected')).toBe('true')
    await user.keyboard('3')
    expect(screen.getByText(/Borrador sin guardar/).parentElement.textContent).toMatch(/1 segundo corregido en 1 espécimen/)
    expect(screen.getAllByText('Escalamiento').length).toBeGreaterThan(1)
    await user.click(screen.getByRole('button', { name: 'Guardar correcciones' }))
    expect(await screen.findByText(/Correcciones guardadas/)).toBeTruthy()
  })

  it('reiniciar pide confirmación y lleva a Análisis en curso', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = await conSesion('/experimentos/7/analisis/402/revision')
    await user.click(await screen.findByRole('button', { name: 'Reiniciar el análisis…' }))
    expect(await screen.findByText(/Análisis reiniciado: Referencia · Tanda A volvió a la cola/)).toBeTruthy()
    window.confirm.mockRestore()
  })

  it('sin permiso, lo explica', async () => {
    await conSesion('/experimentos/7/analisis/402/revision', 'jdominguez@ipn.mx')
    expect(await screen.findByRole('heading', { name: 'No puedes revisar este análisis' })).toBeTruthy()
  })
})

describe('experimentos (dashboard)', () => {
  // Las filas de la tabla (sin el encabezado) tardan en cambiar: la lista se pide al servidor con cada filtro.
  const filas = async (n) => waitFor(() => expect(screen.getAllByRole('row')).toHaveLength(n + 1))

  it('sin experimentos invita a crear el primero (§9.2)', async () => {
    experiments.splice(0, experiments.length)
    const user = await conSesion('/experimentos')
    expect(await screen.findByText('Aún no hay experimentos')).toBeTruthy()
    expect(screen.queryByText('Ningún experimento coincide con los filtros.')).toBeNull()
    await user.click(screen.getAllByRole('link', { name: 'Nuevo experimento' }).at(-1))
    expect(await screen.findByRole('heading', { name: 'Datos generales' })).toBeTruthy()
  })

  it('pagina de 20 en 20 con el total del servidor', async () => {
    for (let i = 0; i < 25; i++) await createExperiment({ titulo: 'Prueba ' + String(i).padStart(2, '0'), fecha_inicio: '2026-10-01', notas: '' })
    const user = await conSesion('/experimentos')
    await screen.findByText('Prueba 24')
    await filas(20)
    expect(screen.getByText('30 experimentos')).toBeTruthy() // 5 de ejemplo + 25
    await user.click(screen.getByRole('button', { name: '2' }))
    await filas(10)
    expect(screen.getByText('Prueba 00')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Página siguiente' }).disabled).toBe(true)
    // Un filtro vuelve a la página 1.
    await user.selectOptions(screen.getByLabelText('Fecha'), '2026')
    await screen.findByText('Prueba 24')
    expect(screen.getByRole('button', { name: '1' }).getAttribute('aria-current')).toBe('page')
  })

  it('filtra por estado, fecha y tratamiento, y avisa de los videos por borrarse', async () => {
    const user = await conSesion('/experimentos')
    await screen.findByText('Compuesto CSR-14 · curva de dosis')
    expect(screen.getByText(/Los videos de/).textContent).toMatch(/Extracto de Valeriana officinalis se borran el/)

    await user.selectOptions(screen.getByLabelText('Estado'), 'FAILED')
    await filas(1) // el de ejemplo
    // Los conteos y las opciones salen del resumen de todos, no de lo filtrado.
    expect(screen.getByRole('option', { name: 'Completado · 4' })).toBeTruthy()
    expect(screen.getByRole('option', { name: 'Todos · 5' })).toBeTruthy()
    expect(screen.getByText(/Los videos de/)).toBeTruthy() // el aviso sigue aunque el filtrado no lo incluya
    await user.selectOptions(screen.getByLabelText('Estado'), '')
    await user.selectOptions(screen.getByLabelText('Fecha'), '2025')
    await waitFor(() => expect(screen.queryByText('Compuesto CSR-14 · curva de dosis')).toBeNull())
    await user.selectOptions(screen.getByLabelText('Tratamiento'), 'Fluoxetina 20 mg/kg')
    await filas(1)
    expect(screen.getByText('Fluoxetina · calibración del clasificador')).toBeTruthy()
  })

  it('busca por nombre o tratamiento sin acentos, esperando a que termine de teclear', async () => {
    const user = await conSesion('/experimentos')
    await screen.findByText('Compuesto CSR-14 · curva de dosis')
    const caja = screen.getByLabelText('Buscar por nombre o tratamiento')
    await user.type(caja, 'replica')
    await filas(1)
    expect(screen.getByText('Réplica interanalista · control metodológico')).toBeTruthy()
    await user.clear(caja)
    await user.type(caja, 'SOLUCIÓN') // el tratamiento «Placebo (solución salina)»: todos lo tienen
    await filas(5)
    await user.clear(caja)
    await user.type(caja, '20 mg')
    await filas(2) // Fluoxetina 20 mg/kg y Salvia 20 mg/kg
  })

  it('con filtros que no dejan nada, lo dice; con el resumen intacto', async () => {
    const user = await conSesion('/experimentos')
    await screen.findByText('Compuesto CSR-14 · curva de dosis')
    await user.type(screen.getByLabelText('Buscar por nombre o tratamiento'), 'zzz')
    expect(await screen.findByText('Ningún experimento coincide con los filtros.')).toBeTruthy()
    expect(screen.queryByText('Aún no hay experimentos')).toBeNull()
    expect(screen.getByRole('option', { name: 'Todos · 5' })).toBeTruthy()
  })
})

describe('mensajes de formulario (encargo M11)', () => {
  it('primer acceso: una temporal que no coincide marca su campo', async () => {
    users.push({ idInstitucional: '2030000001', nombre: 'Nueva', apellidos: 'Cuenta', correo: 'nueva.cuenta@ipn.mx', rol: 'Investigador',
      activo: true, cambioRequerido: true, password: 'temporal-1' }) // la temporal que el mock compara
    const user = abrir('/login')
    await entrar(user, 'nueva.cuenta@ipn.mx', 'temporal-1')
    await screen.findByRole('heading', { name: 'Cambiar contraseña' })
    await user.type(screen.getByLabelText('Contraseña temporal'), 'otra-temporal')
    await user.type(screen.getByLabelText('Nueva contraseña'), 'mi-clave-123')
    await user.type(screen.getByLabelText('Confirmar nueva contraseña'), 'mi-clave-123')
    await user.click(screen.getByRole('button', { name: 'Guardar y entrar' }))
    expect(await screen.findByText('La contraseña temporal no es correcta.')).toBeTruthy()
    expect(screen.getByLabelText('Contraseña temporal').className).toMatch(/error/)
  })

  it('primer acceso: un error del servidor distinto de «actual» muestra su texto, no el de la temporal', async () => {
    users.push({ idInstitucional: '2030000002', nombre: 'Nueva', apellidos: 'Cuenta', correo: 'nueva2@ipn.mx', rol: 'Investigador',
      activo: true, cambioRequerido: true, password: 'temporal-2' })
    fallarCambio = () => Promise.reject({ response: { status: 400, data: { error: 'La contraseña nueva no puede pasar de 72 bytes.' } } })
    try {
      const user = abrir('/login')
      await entrar(user, 'nueva2@ipn.mx', 'temporal-2')
      await screen.findByRole('heading', { name: 'Cambiar contraseña' })
      await user.type(screen.getByLabelText('Contraseña temporal'), 'temporal-2')
      await user.type(screen.getByLabelText('Nueva contraseña'), 'mi-clave-123')
      await user.type(screen.getByLabelText('Confirmar nueva contraseña'), 'mi-clave-123')
      await user.click(screen.getByRole('button', { name: 'Guardar y entrar' }))
      expect(await screen.findByText('La contraseña nueva no puede pasar de 72 bytes.')).toBeTruthy()
      expect(screen.queryByText('La contraseña temporal no es correcta.')).toBeNull()
    } finally {
      fallarCambio = null
    }
  })

  it('primer acceso: una contraseña nueva de más de 72 bytes se rechaza antes de enviarla', async () => {
    const user = abrir('/login')
    await entrar(user, 'nueva.cuenta@ipn.mx', 'temporal-1')
    await screen.findByRole('heading', { name: 'Cambiar contraseña' })
    const larga = 'a'.repeat(73)
    await user.type(screen.getByLabelText('Contraseña temporal'), 'temporal-1')
    await user.type(screen.getByLabelText('Nueva contraseña'), larga)
    await user.type(screen.getByLabelText('Confirmar nueva contraseña'), larga)
    await user.click(screen.getByRole('button', { name: 'Guardar y entrar' }))
    expect(await screen.findByText('La nueva contraseña no puede pasar de 72 bytes.')).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'Cambiar contraseña' })).toBeTruthy()
  })

  it('crear cuenta: un identificador repetido marca el campo del identificador, no el del correo', async () => {
    const user = await conSesion('/admin', 'creyesl@ipn.mx')
    await user.click(await screen.findByRole('button', { name: 'Crear cuenta' }))
    const id = screen.getByLabelText(/Identificador institucional/)
    await user.type(screen.getByLabelText('Nombre'), 'Andrea')
    await user.type(screen.getByLabelText('Apellidos'), 'Barrera Solís')
    await user.type(screen.getByLabelText('Correo electrónico'), 'nuevo.id@ipn.mx')
    await user.type(id, '2019630871') // ya es la de mrivera
    await user.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByText('Ya existe una cuenta con ese identificador.')).toBeTruthy()
    expect(id.className).toMatch(/error/)
    expect(screen.getByLabelText('Correo electrónico').className).not.toMatch(/error/)
  })

  it('crear cuenta: el identificador admite como máximo 10 caracteres', async () => {
    const user = await conSesion('/admin', 'creyesl@ipn.mx')
    await user.click(await screen.findByRole('button', { name: 'Crear cuenta' }))
    const id = screen.getByLabelText(/Identificador institucional/)
    expect(id.getAttribute('maxlength')).toBe('10')
    await user.type(id, '12345678901')
    expect(id.value).toBe('1234567890')
  })

  it('perfil: nombre, apellidos y correo se guardan sin espacios de sobra', async () => {
    const user = await conSesion('/perfil')
    const nombre = await screen.findByLabelText('Nombre')
    await user.clear(nombre)
    await user.type(nombre, '  Ana  ')
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }))
    await waitFor(() => expect(users.find((u) => u.idInstitucional === '2019630871').nombre).toBe('Ana'))
  })

  it('grupo: «Revisar segundo a segundo» no aparece a quien no subió el video ni es Administrador', async () => {
    await conSesion('/experimentos/7/grupos/1', 'jdominguez@ipn.mx')
    expect(await screen.findByRole('link', { name: 'Ver resultados de esta tanda' })).toBeTruthy()
    expect(screen.queryByRole('link', { name: 'Revisar segundo a segundo' })).toBeNull()
  })

  it('grupo: quien subió el video ve «Revisar segundo a segundo»', async () => {
    await conSesion('/experimentos/7/grupos/1')
    expect(await screen.findByRole('link', { name: 'Revisar segundo a segundo' })).toBeTruthy()
  })
})

describe('resultados del experimento (pantalla principal, segundo a segundo y descargas)', () => {
  it('sin tanda, el resumen dice qué se logró y qué falta, con el nombre del experimento', async () => {
    await conSesion('/experimentos/7/resultados')
    expect(await screen.findByRole('heading', { name: 'Compuesto CSR-14 · curva de dosis' })).toBeTruthy()
    expect(screen.getByText(/Listos 1 de 4 grupos/)).toBeTruthy()
    expect(screen.getByText(/Falta: Control \(Tanda B en pausa\)/)).toBeTruthy()
    expect(screen.getByRole('heading', { name: '¿Qué pasó? (comparado con el control)' })).toBeTruthy()
  })

  it('¿Qué pasó? compara con el control y dice en palabras comunes los casos de nivel mixto y sin datos', async () => {
    await conSesion('/experimentos/7/resultados')
    expect(await screen.findByText(/^Control: .* s de inmovilidad en promedio\.$/)).toBeTruthy()
    expect(screen.getByText(/^Experimental A: .* que el control/)).toBeTruthy()
    expect(screen.getByText(/^Referencia: sus tandas se analizaron con distinto detalle/)).toBeTruthy()
    expect(screen.getByText(/^Experimental B: todavía no tiene datos del Día 2\.$/)).toBeTruthy()
  })

  it('las dos descargas: en demo no hay archivo y lo dice', async () => {
    const user = await conSesion('/experimentos/7/resultados')
    await user.click(await screen.findByRole('button', { name: 'Reporte completo (PDF), para leer o imprimir' }))
    expect(await screen.findByText('Todavía no está disponible en el modo demo.')).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Datos en Excel, con el segundo a segundo, para hacer tu propia estadística' }))
    expect(await screen.findByText('Todavía no está disponible en el modo demo.')).toBeTruthy()
  })

  it('«Ver el detalle» abre la primera tanda lista en la vista elegida', async () => {
    const user = await conSesion('/experimentos/7/resultados')
    const enlace = await screen.findByRole('link', { name: 'Segundo a segundo' })
    expect(enlace.getAttribute('href')).toBe('/experimentos/7/resultados?grupo=1&tanda=A&vista=segundo')
    await user.click(enlace)
    expect(await screen.findByRole('heading', { name: 'Segundo a segundo · Control, Tanda A' }, { timeout: 5000 })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Segundo a segundo' }).getAttribute('aria-current')).toBe('true')
  })

  it('el segundo a segundo se ve por tanda: con el grupo completo pide elegir una', async () => {
    await conSesion('/experimentos/7/resultados?grupo=1&vista=segundo')
    expect(await screen.findByText(/El segundo a segundo se ve tanda por tanda/)).toBeTruthy()
  })

  it('el resumen del experimento no tiene problemas de accesibilidad (axe)', async () => {
    await conSesion('/experimentos/7/resultados')
    await screen.findByRole('heading', { name: 'Compuesto CSR-14 · curva de dosis' })
    const r = await axe.run(document.body, { rules: { 'color-contrast': { enabled: false } } })
    expect(r.violations.map((v) => v.id)).toEqual([])
  })
})


describe('detalle de resultados: selector de grupo, vista conservada y glosas', () => {
  it('el selector de grupo cambia de grupo y conserva la vista', async () => {
    const user = await conSesion('/experimentos/7/resultados?grupo=1&tanda=A&vista=timeline')
    await user.selectOptions(await screen.findByLabelText('Grupo'), '2')
    expect(await screen.findByRole('heading', { name: 'Resultados · Grupo referencia, Tanda A' }, { timeout: 5000 })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Minuto a minuto' }).getAttribute('aria-current')).toBe('true')
  })

  it('«Grupo completo» conserva la vista activa', async () => {
    const user = await conSesion('/experimentos/7/resultados?grupo=1&tanda=A&vista=timeline')
    await user.click(await screen.findByRole('link', { name: 'Grupo completo · 4 especímenes' }))
    expect(await screen.findByRole('heading', { name: 'Resultados · Grupo control, grupo completo' }, { timeout: 5000 })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Minuto a minuto' }).getAttribute('aria-current')).toBe('true')
  })

  it('segundo a segundo con grupo completo se queda en esa vista y pide elegir una tanda', async () => {
    await conSesion('/experimentos/7/resultados?grupo=1&vista=segundo')
    expect(await screen.findByText(/El segundo a segundo se ve tanda por tanda/, {}, { timeout: 5000 })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Segundo a segundo' }).getAttribute('aria-current')).toBe('true')
  })

  it('el detalle explica qué es una tanda y qué significa el nivel preciso', async () => {
    await conSesion('/experimentos/7/resultados?grupo=1&tanda=A')
    expect(await screen.findByText('Tanda: un video con varios especímenes. El grupo completo reúne sus tandas con análisis completado; cada espécimen lleva el nombre de su tanda.')).toBeTruthy()
    expect(screen.getByText('preciso: el sistema distinguió nado y escalamiento.')).toBeTruthy()
  })

  it('el resumen del experimento dice qué es el Día 2', async () => {
    await conSesion('/experimentos/7/resultados')
    expect(await screen.findByText('Día 2 (la prueba de 5 minutos).')).toBeTruthy()
  })
})
