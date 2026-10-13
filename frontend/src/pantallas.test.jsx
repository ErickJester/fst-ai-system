// @vitest-environment jsdom
// Pruebas de las pantallas: la app completa, usada como lo haría una persona
// (escribir, hacer clic, leer lo que aparece). Usan los datos de ejemplo.
import React from 'react'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, cleanup, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from './contexts/AuthContext'
import App from './App'
import { reiniciarDatos } from './services/mocks/data'
import { avanzarSimulacion } from './services/mocks/simulador'

// Lo que jsdom no trae: desplazar la ventana y URLs de archivos.
window.scrollTo = vi.fn()
URL.createObjectURL = vi.fn(() => 'blob:prueba')
URL.revokeObjectURL = vi.fn()

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

// Inicia sesión y abre directamente otra ruta (la sesión queda en sessionStorage).
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
    expect(screen.getByText('Compuesto CSR-14 · curva de dosis')).toBeTruthy()
  })

  it('marca el correo que no tiene forma de correo', async () => {
    const user = abrir('/login')
    await entrar(user, 'alguien@gmail')
    expect(await screen.findByText('Escribe un correo electrónico válido.')).toBeTruthy()
    expect(screen.getByLabelText('Correo electrónico').className).toContain('error')
  })

  it('sin sesión, cualquier ruta lleva al login', async () => {
    abrir('/experimentos/EXP-2026-02')
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
  // Elige Referencia con el teclado, confirma la tanda C y adjunta el archivo.
  async function prepararTandaC(user, nombre) {
    await user.type(await screen.findByRole('combobox'), 'fluox')
    await user.keyboard('{ArrowDown}{Enter}')
    await user.click(screen.getByRole('button', { name: 'Sí, es la C' }))
    await user.upload(document.querySelector('input[type=file]'), new File(['video'], nombre, { type: 'video/mp4' }))
    await screen.findByText(nombre)
  }

  it('elegir el grupo con el teclado, confirmar la tanda y encolar el video', async () => {
    const user = await conSesion('/experimentos/EXP-2026-02/cargar')
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
    const user = await conSesion('/experimentos/EXP-2026-02/cargar')
    await screen.findByRole('combobox')
    await user.click(screen.getByRole('button', { name: 'Día 1 (20 min)' }))
    await prepararTandaC(user, 'tanda_C_dia1.mp4')
    await user.click(screen.getByRole('button', { name: 'Guardar video' }))
    expect(await screen.findByRole('heading', { name: 'Compuesto CSR-14 · curva de dosis' })).toBeTruthy()
    expect(screen.getByText(/Video del Día 1 almacenado .*No se analiza\./)).toBeTruthy()
  })

  it('el navegador solo revisa la extensión; el servidor rechaza el video vertical (D-64)', async () => {
    const user = await conSesion('/experimentos/EXP-2026-02/cargar')
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
    await conSesion('/experimentos/EXP-2026-02/resultados?grupo=G-02&tanda=A')
    expect(await screen.findByRole('heading', { name: 'Resultados · Grupo referencia, Tanda A' })).toBeTruthy()
    expect(screen.getAllByText('Tanda A · Espécimen 1').length).toBeGreaterThan(0)
    expect(await screen.findByText('Este grupo mezcla niveles de clasificación; se muestran por separado.')).toBeTruthy()
    // Cuatro columnas de conducta, también la conducta activa (R-12).
    expect(screen.getByRole('columnheader', { name: 'Conducta activa' })).toBeTruthy()
  })

  it('los bloques de 5 s se abren para ver sus cinco segundos', async () => {
    const user = await conSesion('/experimentos/EXP-2026-02/resultados?grupo=G-01&tanda=A')
    const fila = await screen.findByRole('group', { name: 'Bloques de Tanda A · Espécimen 1' })
    await user.click(within(fila).getByRole('button', { name: '2:00–2:05 · Conducta activa (no se vio bien)' }))
    expect(screen.getAllByText('No se ve')).toHaveLength(5)
    expect(screen.getByText(/El modelo no pudo ver bien al espécimen/)).toBeTruthy()
  })
})

describe('eliminar experimento (D-63)', () => {
  it('desde el detalle pide solo confirmación; con un análisis procesando, no se puede (D-87)', async () => {
    const user = await conSesion('/experimentos/EXP-2026-02')
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

describe('pausa del análisis (R-15)', () => {
  // El trabajo 502 (Control · Tanda B) se acaba de pausar: queda casi todo el plazo.
  function pausarAhora() {
    const ahora = Date.now()
    avanzarSimulacion(ahora - 25000)
    avanzarSimulacion(ahora)
  }

  it('muestra la imagen con el recuadro, el tiempo que queda y ajusta', async () => {
    pausarAhora()
    const user = await conSesion('/experimentos/EXP-2026-02/analisis/502/pausa')
    expect(await screen.findByRole('heading', { name: 'No se ve al espécimen del cilindro 2 (segundo 142)' })).toBeTruthy()
    expect(screen.getByRole('timer').textContent).toMatch(/Tiempo para responder: [45]:\d\d/)
    const izquierda = screen.getByLabelText('Izquierda (px)')
    await user.clear(izquierda)
    await user.type(izquierda, '370')
    expect(screen.getByLabelText(/Recuadro del cilindro 2: x 370–/)).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Ajustar y continuar' }))
    expect(await screen.findByRole('heading', { name: 'Análisis en curso' })).toBeTruthy()
    expect(screen.getByText(/Recuadro del cilindro 2 ajustado\. El análisis volvió a la cola/)).toBeTruthy()
  })

  it('cancelar pide confirmación; después la pausa ya no está', async () => {
    pausarAhora()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = await conSesion('/experimentos/EXP-2026-02/analisis/502/pausa')
    await user.click(await screen.findByRole('button', { name: 'Cancelar análisis' }))
    expect(window.confirm).toHaveBeenCalled()
    expect(await screen.findByText(/Análisis cancelado: Compuesto CSR-14 · Control · Tanda B/)).toBeTruthy()
    expect(screen.getByText(/Se canceló desde la pantalla de pausa/)).toBeTruthy()
    window.confirm.mockRestore()

    cleanup()
    abrir('/experimentos/EXP-2026-02/analisis/502/pausa')
    expect(await screen.findByRole('heading', { name: 'Este análisis ya no está en pausa' })).toBeTruthy()
  })
})

describe('experimentos (dashboard)', () => {
  it('filtra por estado, fecha y tratamiento, y avisa de los videos por borrarse', async () => {
    const user = await conSesion('/experimentos')
    await screen.findByText('Compuesto CSR-14 · curva de dosis')
    expect(screen.getByText(/Los videos de/).textContent).toMatch(/Extracto de Valeriana officinalis se borran el/)

    await user.selectOptions(screen.getByLabelText('Estado'), 'FAILED')
    expect(screen.getAllByRole('row')).toHaveLength(2) // encabezado + el de ejemplo
    await user.selectOptions(screen.getByLabelText('Estado'), '')
    await user.selectOptions(screen.getByLabelText('Fecha'), '2025')
    expect(screen.queryByText('Compuesto CSR-14 · curva de dosis')).toBeNull()
    await user.selectOptions(screen.getByLabelText('Tratamiento'), 'Fluoxetina 20 mg/kg')
    expect(screen.getAllByRole('row')).toHaveLength(2)
    expect(screen.getByText('Fluoxetina · calibración del clasificador')).toBeTruthy()
  })
})
