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
import { createExperiment } from './services/experiments'

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

  it('avisa si la tanda ya tiene el video de esa sesión y no deja guardar', async () => {
    const user = await conSesion('/experimentos/EXP-2026-02/cargar')
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
    const user = await conSesion('/experimentos/EXP-2026-02/cargar')
    await screen.findByRole('combobox')
    const grande = new File(['video'], 'enorme.mp4', { type: 'video/mp4' })
    Object.defineProperty(grande, 'size', { value: 3 * 1024 * 1024 * 1024 })
    await user.upload(document.querySelector('input[type=file]'), grande)
    expect(await screen.findByText('El archivo pasa del límite de 2048 MB')).toBeTruthy()
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

  it('los botones de reporte no generan archivos: explican lo que hará el backend', async () => {
    const user = await conSesion('/experimentos/EXP-2026-02/resultados?grupo=G-02&tanda=A')
    await user.click(await screen.findByRole('button', { name: 'CSV' }))
    expect((await screen.findByText(/Reporte CSV de la Tanda A./)).parentElement.textContent).toMatch(/Lo genera el servidor .*Tiempo \(min:seg\)/)
    expect(URL.createObjectURL).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'PDF · reporte de resultados' }))
    expect(screen.getByText(/Reporte PDF del experimento/)).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Entendido' }))
    expect(screen.queryByText(/Reporte PDF del experimento/)).toBeNull()
  })

  it('ver el grupo completo: todas sus tandas completadas, con los estadísticos recalculados (D-93)', async () => {
    const user = await conSesion('/experimentos/EXP-2026-02/resultados?grupo=G-02&tanda=A')
    const filas = async () => (await screen.findAllByRole('row')).filter((f) => /Espécimen \d/.test(f.textContent))
    expect(await filas()).toHaveLength(4)
    const media = () => screen.getByRole('row', { name: /^Media/ }).textContent
    const mediaTanda = media()
    await user.click(screen.getByRole('link', { name: 'Grupo completo · 8 especímenes' }))
    expect(await screen.findByRole('heading', { name: 'Resultados · Grupo referencia, grupo completo' })).toBeTruthy()
    expect(await filas()).toHaveLength(8)
    expect(screen.getByText('Tanda B · Espécimen 4')).toBeTruthy()
    expect(media()).not.toBe(mediaTanda)
    expect(screen.getByText('8 de 8')).toBeTruthy()
    expect(screen.queryByRole('link', { name: 'Revisar segundo a segundo' })).toBeNull() // la revisión es por tanda
    await user.click(screen.getByRole('button', { name: 'CSV' }))
    expect(screen.getByText(/Reporte CSV del grupo completo\./)).toBeTruthy()
  })

  it('en el grupo completo, las tandas sin análisis completado se señalan como faltantes', async () => {
    await conSesion('/experimentos/EXP-2026-02/resultados?grupo=G-01')
    expect(await screen.findByRole('heading', { name: 'Resultados · Grupo control, grupo completo' })).toBeTruthy()
    expect(screen.getByText(/Faltan Tanda B \(en pausa\)/)).toBeTruthy()
    expect(screen.getByText('4 de 8')).toBeTruthy()
  })

  it('los bloques de 5 s los calcula el backend: sin él, la sección lo explica', async () => {
    await conSesion('/experimentos/EXP-2026-02/resultados?grupo=G-01&tanda=A')
    expect((await screen.findByText(/^Bloques de 5 s\.$/)).parentElement.textContent).toMatch(/Los calcula el servidor/)
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

describe('sesión', () => {
  it('se guarda en localStorage, que sobrevive a cerrar la pestaña (token de larga duración)', async () => {
    await conSesion('/experimentos')
    expect(localStorage.getItem('fst.token')).toMatch(/^mock-token-/)
    expect(sessionStorage.getItem('fst.token')).toBeNull()
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
})

describe('grupo', () => {
  it('lleva a los resultados del grupo completo o de cada tanda', async () => {
    await conSesion('/experimentos/EXP-2026-02/grupos/G-01')
    expect((await screen.findByRole('link', { name: 'Ver resultados del grupo completo' })).getAttribute('href')).toBe('/experimentos/EXP-2026-02/resultados?grupo=G-01')
    expect(screen.getByText('Reúne las tandas con análisis completado: 1 de 2')).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Ver resultados de esta tanda' }).getAttribute('href')).toBe('/experimentos/EXP-2026-02/resultados?grupo=G-01&tanda=A')
    expect(screen.getByText('Guardado')).toBeTruthy() // el Día 1 se guarda y no se analiza
  })
})

describe('pausa del análisis (R-15)', () => {
  // El trabajo 502 (Control · Tanda B) está en pausa en los datos de ejemplo.
  it('muestra la imagen con el recuadro, el tiempo que queda y ajusta', async () => {
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

describe('revisión segundo a segundo (R-14)', () => {
  it('en pausa, una tecla cambia solo ese segundo; guardar deja la revisión', async () => {
    const user = await conSesion('/experimentos/EXP-2026-02/analisis/402/revision')
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
    const user = await conSesion('/experimentos/EXP-2026-02/analisis/402/revision')
    await user.click(await screen.findByRole('button', { name: 'Reiniciar el análisis…' }))
    expect(await screen.findByText(/Análisis reiniciado: Referencia · Tanda A volvió a la cola/)).toBeTruthy()
    window.confirm.mockRestore()
  })

  it('sin permiso, lo explica', async () => {
    await conSesion('/experimentos/EXP-2026-02/analisis/402/revision', 'jdominguez@ipn.mx')
    expect(await screen.findByRole('heading', { name: 'No puedes revisar este análisis' })).toBeTruthy()
  })
})

describe('experimentos (dashboard)', () => {
  it('pagina de 10 en 10', async () => {
    for (let i = 0; i < 7; i++) await createExperiment({ titulo: 'Prueba ' + i, fecha_inicio: '2026-10-0' + (i + 1), notas: '' })
    const user = await conSesion('/experimentos')
    await screen.findByText('Prueba 0')
    expect(screen.getAllByRole('row')).toHaveLength(11) // encabezado + 10
    await user.click(screen.getByRole('button', { name: '2' }))
    expect(screen.getAllByRole('row')).toHaveLength(3) // los 2 que faltan de 12
    expect(screen.getByRole('button', { name: 'Página siguiente' }).disabled).toBe(true)
  })

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
