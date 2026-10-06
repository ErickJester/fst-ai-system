// Pruebas de la capa de servicios con datos de ejemplo (USE_MOCKS). Cubren las reglas
// que el backend también tendrá que cumplir: contraseñas, administradores y experimentos.
import { describe, it, expect, beforeEach } from 'vitest'
import * as auth from './auth'
import * as admin from './admin'
import { createExperiment, deleteExperiment, getExperiment, listExperiments } from './experiments'
import { createGroup, uploadBatchVideo } from './groups'
import { getQueue } from './queue'
import { getBatchResults } from './results'
import { reiniciarDatos, resetTokens, queue } from './mocks/data'
import { avanzarSimulacion } from './mocks/simulador'
import { listNotifications } from './notifications'

const status = (promesa) => promesa.then(() => 'ok', (e) => e.response.status)

beforeEach(() => reiniciarDatos())

describe('inicio de sesión', () => {
  it('las cuentas de ejemplo entran con cualquier contraseña no vacía', async () => {
    const r = await auth.login('mrivera@ipn.mx', 'x')
    expect(r.user.email).toBe('mrivera@ipn.mx')
    expect(r.user).not.toHaveProperty('password')
    expect(await status(auth.login('mrivera@ipn.mx', ''))).toBe(401)
  })

  it('una cuenta desactivada no entra', async () => {
    const lortega = (await admin.listUsers()).find((u) => u.email === 'lortega@ipn.mx')
    await admin.setUserActive(lortega.id, false)
    expect(await status(auth.login('lortega@ipn.mx', 'x'))).toBe(401)
  })
})

describe('contraseña temporal', () => {
  it('la cuenta creada entra solo con su temporal y debe cambiarla', async () => {
    const u = await admin.createUser({ nombre: 'Andrea', apellidos: 'Barrera Solís', email: 'ABarrera@ipn.mx', identificador: '2021630154' })
    expect(u.email).toBe('abarrera@ipn.mx')
    expect(u.password_temporal).toMatch(/^[A-Za-z2-9]{12}$/)
    expect(u).not.toHaveProperty('password')

    expect(await status(auth.login('abarrera@ipn.mx', 'otra'))).toBe(401)
    const r = await auth.login('abarrera@ipn.mx', u.password_temporal)
    expect(r.user.must_change_password).toBe(true)

    expect(await status(auth.changePassword(u.id, 'equivocada', 'mi-clave-123'))).toBe(400)
    const cambiado = await auth.changePassword(u.id, u.password_temporal, 'mi-clave-123')
    expect(cambiado.must_change_password).toBe(false)
    expect(await status(auth.login('abarrera@ipn.mx', u.password_temporal))).toBe(401)
    expect(await status(auth.login('abarrera@ipn.mx', 'mi-clave-123'))).toBe('ok')
  })

  it('restablecer la temporal invalida la contraseña anterior', async () => {
    const u = await admin.createUser({ nombre: 'Luis', apellidos: 'Pérez', email: 'lperez@ipn.mx', identificador: '1' })
    await auth.changePassword(u.id, u.password_temporal, 'mi-clave-123')
    const { password_temporal } = await admin.resetTemporal(u.id)
    expect(await status(auth.login('lperez@ipn.mx', 'mi-clave-123'))).toBe(401)
    const r = await auth.login('lperez@ipn.mx', password_temporal)
    expect(r.user.must_change_password).toBe(true)
  })

  it('no permite dos cuentas con el mismo correo', async () => {
    expect(await status(admin.createUser({ nombre: 'X', apellidos: 'Y', email: 'MRIVERA@ipn.mx', identificador: '1' }))).toBe(409)
  })
})

describe('recuperar contraseña', () => {
  it('responde igual exista o no la cuenta; solo la existente recibe enlace', async () => {
    const nadie = await auth.forgotPassword('nadie@ipn.mx')
    expect(nadie).toEqual({ ok: true })
    const alguien = await auth.forgotPassword('mrivera@ipn.mx')
    expect(alguien.ok).toBe(true)
    expect(alguien.demo_enlace).toMatch(/^\/restablecer\?token=\w+$/)
  })

  it('el enlace sirve una sola vez y deja la nueva contraseña', async () => {
    const { demo_enlace } = await auth.forgotPassword('mrivera@ipn.mx')
    const token = demo_enlace.split('token=')[1]
    expect(await status(auth.resetPassword(token, 'nueva-clave-9'))).toBe('ok')
    expect(await status(auth.resetPassword(token, 'otra-clave-10'))).toBe(400)
    expect(await status(auth.login('mrivera@ipn.mx', 'x'))).toBe(401)
    expect(await status(auth.login('mrivera@ipn.mx', 'nueva-clave-9'))).toBe('ok')
  })

  it('un enlace vencido o inventado no sirve', async () => {
    const { demo_enlace } = await auth.forgotPassword('mrivera@ipn.mx')
    const token = demo_enlace.split('token=')[1]
    resetTokens[token].vence = Date.now() - 1
    expect(await status(auth.resetPassword(token, 'nueva-clave-9'))).toBe(400)
    expect(await status(auth.resetPassword('vencido', 'nueva-clave-9'))).toBe(400)
  })
})

describe('administradores', () => {
  const buscar = async (email) => (await admin.listUsers()).find((u) => u.email === email)

  it('no se puede desactivar ni quitar el rol al único Administrador activo', async () => {
    const creyes = await buscar('creyesl@ipn.mx')
    expect(await status(admin.setUserActive(creyes.id, false))).toBe(409)
    expect(await status(admin.updateUser(creyes.id, { ...creyes, role: 'INVESTIGADOR' }))).toBe(409)
  })

  it('con dos administradores, uno sí puede dejar de serlo', async () => {
    const creyes = await buscar('creyesl@ipn.mx')
    const jd = await buscar('jdominguez@ipn.mx')
    await admin.updateUser(jd.id, { ...jd, role: 'ADMIN' })
    expect(await status(admin.updateUser(creyes.id, { ...creyes, role: 'INVESTIGADOR' }))).toBe('ok')
  })

  it('editar no permite el correo de otra cuenta', async () => {
    const jd = await buscar('jdominguez@ipn.mx')
    expect(await status(admin.updateUser(jd.id, { ...jd, email: 'lortega@ipn.mx' }))).toBe(409)
    expect(await status(admin.updateUser(jd.id, { ...jd, nombre: 'Javier' }))).toBe('ok')
  })
})

describe('experimentos', () => {
  it('crear asigna la clave del año y empieza sin grupos', async () => {
    const { clave } = await createExperiment({ titulo: 'Ketamina · dosis única', fecha_inicio: '2026-10-01', especie: 'Rata Wistar', notas: '' })
    expect(clave).toBe('EXP-2026-03')
    const exp = await getExperiment(clave)
    expect(exp.grupos).toEqual([])
    expect((await listExperiments())[0]).toMatchObject({ clave, estado: 'CARGA_INCOMPLETA', n_grupos: 0 })
  })

  it('subir el primer video crea la tanda, la encola y actualiza la lista', async () => {
    const { clave } = await createExperiment({ titulo: 'Prueba', fecha_inicio: '2026-10-01', especie: '', notas: '' })
    const g = await createGroup(clave, { nombre: 'Control', tipo: 'CONTROL', tratamiento: 'Solución salina' })
    const avances = []
    const r = await uploadBatchVideo(clave, g.id, 'A', { file: null, dia: 'DAY2', nCilindros: 4 }, (p) => avances.push(p))

    expect(avances).toEqual([25, 50, 75, 100])
    expect(r.posicion_cola).toBe(4)
    expect((await getQueue()).cola.at(-1)).toMatchObject({ grupo: 'Control', tanda: 'A', status: 'QUEUED' })
    const tanda = (await getExperiment(clave)).grupos[0].tandas[0]
    expect(tanda).toMatchObject({ letra: 'A', desde: 1, n_cilindros: 4, estado: 'QUEUED' })
    const fila = (await listExperiments()).find((e) => e.clave === clave)
    expect(fila).toMatchObject({ n_grupos: 1, n_especimenes: 4, videos_dia2_total: 1, videos_dia2_cargados: 1, estado: 'EN_ANALISIS' })
  })

  it('eliminar exige el nombre exacto', async () => {
    expect(await status(deleteExperiment('EXP-2026-02', { titulo: 'otro nombre', password: 'x' }))).toBe(400)
    expect(await status(deleteExperiment('EXP-2026-02', { titulo: 'Compuesto CSR-14 · curva de dosis', password: 'x' }))).toBe('ok')
    expect(await status(getExperiment('EXP-2026-02'))).toBe(404)
  })
})

describe('resultados por tanda', () => {
  it('cada tanda terminada tiene los suyos, con su grupo y sus especímenes', async () => {
    const control = await getBatchResults('EXP-2026-02', 'G-01', 'A')
    expect(control.grupo.nombre).toBe('Control')
    expect(control.especimenes.map((e) => e.especimen)).toEqual([1, 2, 3, 4])
    const refB = await getBatchResults('EXP-2026-02', 'G-02', 'B')
    expect(refB.letra).toBe('B')
    expect(refB.especimenes.map((e) => e.especimen)).toEqual([5, 6, 7, 8])
  })

  it('una tanda en cola o con error todavía no tiene resultados', async () => {
    expect(await status(getBatchResults('EXP-2026-02', 'G-01', 'B'))).toBe(404)
    expect(await status(getBatchResults('EXP-2026-02', 'G-04', 'B'))).toBe(404)
  })
})

describe('simulación de la cola (modo demo)', () => {
  const T0 = Date.UTC(2026, 9, 5, 15, 0, 0)
  const s = (n) => n * 1000

  it('los trabajos avanzan por etapas, terminan, notifican y dejan correr al siguiente', async () => {
    avanzarSimulacion(T0) // el trabajo inicial va en 75 %: le faltan 8 s
    expect((await getQueue()).cola[0]).toMatchObject({ job_id: 501, status: 'RUNNING' })

    avanzarSimulacion(T0 + s(9))
    const cola = (await getQueue()).cola
    expect(cola.map((j) => j.job_id)).toEqual([502, 503])
    expect(cola[0]).toMatchObject({ posicion: 1, status: 'RUNNING' })
    const tanda = (await getExperiment('EXP-2026-02')).grupos.find((g) => g.id === 'G-03').tandas[1]
    expect(tanda).toMatchObject({ letra: 'B', estado: 'DONE', progreso: null })
    const [aviso] = await listNotifications()
    expect(aviso).toMatchObject({ tipo: 'ANALYSIS_DONE', is_read: false, enlace: '/experimentos/EXP-2026-02/resultados?grupo=G-03&tanda=B' })
    expect((await listExperiments()).find((e) => e.clave === 'EXP-2026-02').videos_dia2_listos).toBe(5)
    expect(await status(getBatchResults('EXP-2026-02', 'G-03', 'B'))).toBe('ok')

    // 12 s después del inicio de Control · Tanda B: segunda etapa
    avanzarSimulacion(T0 + s(8 + 12))
    const enCurso = (await getQueue()).cola[0]
    expect(enCurso).toMatchObject({ job_id: 502, stage: 'ROI_DETECTION', progress_pct: 37 })
    const control = (await getExperiment('EXP-2026-02')).grupos.find((g) => g.id === 'G-01').tandas[1]
    expect(control).toMatchObject({ estado: 'RUNNING', progreso: { etapa: 'ROI_DETECTION', pct: 37 } })
  })

  it('uno de cada tres trabajos falla en la detección de cilindros', async () => {
    avanzarSimulacion(T0)
    avanzarSimulacion(T0 + s(8 + 32 + 16)) // 501 y 502 terminan; 503 falla a la mitad
    const { cola, errores } = await getQueue()
    expect(cola).toEqual([])
    expect(errores[0]).toMatchObject({ job_id: 503, status: 'FAILED', stage: 'ROI_DETECTION', progress_pct: 50 })
    expect(errores[0].error.codigo).toBe('E-DET-070')
    const tanda = (await getExperiment('EXP-2026-02')).grupos.find((g) => g.id === 'G-04').tandas[0]
    expect(tanda.estado).toBe('FAILED')
    expect((await listNotifications())[0]).toMatchObject({ tipo: 'ANALYSIS_FAILED', enlace: '/analisis' })
  })

  it('un video subido con la cola vacía empieza cuando se sube, no antes', async () => {
    avanzarSimulacion(T0)
    avanzarSimulacion(T0 + s(60)) // cola vacía
    const { clave } = await createExperiment({ titulo: 'Prueba', fecha_inicio: '2026-10-05', especie: '', notas: '' })
    const g = await createGroup(clave, { nombre: 'Control', tipo: 'CONTROL', tratamiento: 'x' })
    await uploadBatchVideo(clave, g.id, 'A', { file: null, dia: 'DAY2', nCilindros: 4 })
    queue[0].creado_ms = T0 + s(100) // fijamos el momento de la subida
    avanzarSimulacion(T0 + s(90))
    expect((await getQueue()).cola[0].status).toBe('QUEUED')
    avanzarSimulacion(T0 + s(100 + 8))
    expect((await getQueue()).cola[0]).toMatchObject({ status: 'RUNNING', stage: 'ROI_DETECTION', progress_pct: 25 })
  })
})

describe('eliminar experimento con la contraseña de la cuenta', () => {
  // La sesión simulada vive en sessionStorage, que Node no tiene.
  const sesion = new Map()
  globalThis.sessionStorage = { getItem: (k) => sesion.get(k) ?? null, setItem: (k, v) => sesion.set(k, String(v)), removeItem: (k) => sesion.delete(k) }

  it('pide la contraseña real si la cuenta tiene una', async () => {
    const u = await admin.createUser({ nombre: 'Ana', apellidos: 'Luna', email: 'aluna@ipn.mx', identificador: '1' })
    await auth.changePassword(u.id, u.password_temporal, 'clave-de-ana-1')
    sesion.set('fst.token', 'mock-token-' + u.id)
    const datos = { titulo: 'Compuesto CSR-14 · curva de dosis' }
    expect(await status(deleteExperiment('EXP-2026-02', { ...datos, password: 'otra' }))).toBe(400)
    expect(await status(deleteExperiment('EXP-2026-02', { ...datos, password: 'clave-de-ana-1' }))).toBe('ok')
    sesion.clear()
  })
})
