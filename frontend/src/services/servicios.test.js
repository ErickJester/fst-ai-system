// Pruebas de la capa de servicios con datos de ejemplo (USE_MOCKS). Cubren las reglas
// que el backend también tendrá que cumplir: contraseñas, administradores y experimentos.
import { describe, it, expect, beforeEach } from 'vitest'
import * as auth from './auth'
import * as admin from './admin'
import { createExperiment, deleteExperiment, getExperiment, listExperiments } from './experiments'
import { createGroup, uploadBatchVideo } from './groups'
import { getQueue, getDiagnostico, getPausa, ajustarRecuadro, cancelarAnalisis } from './queue'
import { getBatchResults, getGroupComparison } from './results'
import { reiniciarDatos, resetTokens, queue } from './mocks/data'
import { avanzarSimulacion } from './mocks/simulador'
import { listNotifications } from './notifications'

const status = (promesa) => promesa.then(() => 'ok', (e) => e.response.status)

// En Node no hay sessionStorage: uno en memoria para elegir la cuenta de la sesión.
const almacen = new Map()
globalThis.sessionStorage = {
  getItem: (k) => almacen.get(k) ?? null,
  setItem: (k, v) => almacen.set(k, String(v)),
  removeItem: (k) => almacen.delete(k),
}
const sesion = (id) => sessionStorage.setItem('fst.token', 'mock-token-' + id) // 1 M. Rivera, 2 C. Reyes (Administrador)

beforeEach(() => {
  reiniciarDatos()
  almacen.clear()
})

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
    expect((await listExperiments())[0]).toMatchObject({ clave, estado: 'SIN_VIDEOS', n_grupos: 0 })
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
    expect(fila).toMatchObject({ n_grupos: 1, n_especimenes: 4, videos_dia2_total: 1, videos_dia2_cargados: 1, estado: 'QUEUED' })
  })

  it('el video de Día 1 se guarda pero no se analiza (D-54)', async () => {
    const { clave } = await createExperiment({ titulo: 'Prueba', fecha_inicio: '2026-10-01', especie: '', notas: '' })
    const g = await createGroup(clave, { nombre: 'Control', tipo: 'CONTROL', tratamiento: 'Solución salina' })
    const r = await uploadBatchVideo(clave, g.id, 'A', { file: null, dia: 'DAY1', nCilindros: 3 })

    expect(r).toEqual({ job_id: null, posicion_cola: null })
    expect((await getQueue()).cola.some((j) => j.clave === clave)).toBe(false)
    const tanda = (await getExperiment(clave)).grupos[0].tandas[0]
    expect(tanda).toMatchObject({ letra: 'A', n_cilindros: 3, estado: 'SIN_VIDEO', dia1: { cargado: true } })
    // Solo con el Día 1, el experimento queda «en espera del Día 2» (D-83).
    expect((await listExperiments()).find((e) => e.clave === clave).estado).toBe('ESPERA_DIA2')
  })

  it('el servidor rechaza un video vertical o que no se puede reproducir (D-64)', async () => {
    const { clave } = await createExperiment({ titulo: 'Prueba', fecha_inicio: '2026-10-01', especie: '', notas: '' })
    const g = await createGroup(clave, { nombre: 'Control', tipo: 'CONTROL', tratamiento: 'Solución salina' })
    const subir = (name) => uploadBatchVideo(clave, g.id, 'A', { file: { name }, dia: 'DAY2', nCilindros: 2 })
    expect(await status(subir('celular_vertical.mp4'))).toBe(422)
    expect(await status(subir('dañado.mov'))).toBe(422)
    expect((await getQueue()).cola.some((j) => j.clave === clave)).toBe(false)
    expect(await status(subir('tanda_A.mp4'))).toBe('ok')
  })

  it('el estado del experimento sigue la precedencia del documento', async () => {
    // El de ejemplo tiene una tanda con error: «Con error» manda sobre las demás.
    expect((await listExperiments()).find((e) => e.clave === 'EXP-2026-02').estado).toBe('FAILED')
  })

  it('eliminar: solo quien lo creó o un Administrador, sin pedir nombre ni contraseña (D-63, R-08)', async () => {
    sesion(1) // M. Rivera
    expect(await status(deleteExperiment('EXP-2026-01'))).toBe(403) // lo creó J. Domínguez
    expect(await status(deleteExperiment('EXP-2025-11'))).toBe('ok')
    expect((await listExperiments()).some((e) => e.clave === 'EXP-2025-11')).toBe(false)
    expect(await status(deleteExperiment('EXP-2025-11'))).toBe(404)
    sesion(2) // Administrador
    expect(await status(deleteExperiment('EXP-2026-01'))).toBe('ok')
  })

  it('no se elimina con un análisis procesando; los que están en cola salen de la cola (D-87)', async () => {
    sesion(1)
    expect(await status(deleteExperiment('EXP-2026-02'))).toBe(409) // Experimental A · Tanda B se procesa
    expect(await status(getExperiment('EXP-2026-02'))).toBe('ok')

    const { clave } = await createExperiment({ titulo: 'Prueba', fecha_inicio: '2026-10-01', especie: '', notas: '' })
    const g = await createGroup(clave, { nombre: 'Control', tipo: 'CONTROL', tratamiento: 'Solución salina' })
    await uploadBatchVideo(clave, g.id, 'A', { file: null, dia: 'DAY2', nCilindros: 4 })
    expect((await getQueue()).cola.at(-1).clave).toBe(clave)
    expect(await status(deleteExperiment(clave))).toBe('ok')
    const { cola } = await getQueue()
    expect(cola.some((j) => j.clave === clave)).toBe(false)
    expect(cola.map((j) => j.posicion)).toEqual([1, 2, 3])
  })

  it('avisa con la fecha exacta los videos que se borran en 7 días o menos (R-05)', async () => {
    const lista = await listExperiments()
    const valeriana = lista.find((e) => e.clave === 'EXP-2025-11') // le quedan 4 días
    const d = new Date()
    d.setDate(d.getDate() + 4)
    expect(valeriana.aviso_vencimiento.fecha_borrado).toBe(d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0'))
    expect(lista.find((e) => e.clave === 'EXP-2026-01').aviso_vencimiento).toBeNull() // 12 días
    expect(lista.find((e) => e.clave === 'EXP-2025-10').aviso_vencimiento).toBeNull() // ya se borraron
  })

  it('la alerta de disco solo llega a los Administradores', async () => {
    sesion(1)
    expect((await listNotifications()).some((n) => n.tipo === 'DISK_ALERT')).toBe(false)
    sesion(2)
    expect((await listNotifications()).some((n) => n.tipo === 'DISK_ALERT')).toBe(true)
  })
})

describe('resultados por tanda', () => {
  it('cada tanda terminada tiene los suyos, con su grupo y sus especímenes', async () => {
    const control = await getBatchResults('EXP-2026-02', 'G-01', 'A')
    expect(control.grupo.nombre).toBe('Control')
    expect(control.especimenes.map((e) => e.numeroCilindro)).toEqual([1, 2, 3, 4])
    const refB = await getBatchResults('EXP-2026-02', 'G-02', 'B')
    expect(refB.letra).toBe('B')
    expect(refB.especimenes.map((e) => e.numeroCilindro)).toEqual([1, 2, 3, 4])
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
    avanzarSimulacion(T0) // el trabajo inicial va a la mitad de la clasificación: le faltan 4 s
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

    // 12 s después del inicio de Control · Tanda B: segunda etapa (cilindros, 67 %)
    avanzarSimulacion(T0 + s(4 + 12))
    const enCurso = (await getQueue()).cola[0]
    expect(enCurso).toMatchObject({ job_id: 502, stage: 'ROI_DETECTION', progress_pct: 67 })
    const control = (await getExperiment('EXP-2026-02')).grupos.find((g) => g.id === 'G-01').tandas[1]
    expect(control).toMatchObject({ estado: 'RUNNING', progreso: { etapa: 'ROI_DETECTION', pct: 67 } })
  })

  it('uno de cada tres trabajos falla en la etapa de cilindros', async () => {
    avanzarSimulacion(T0)
    avanzarSimulacion(T0 + s(4 + 24 + 16)) // 501 y 502 terminan; 503 falla al acabar la etapa de cilindros
    const { cola, errores } = await getQueue()
    expect(cola).toEqual([])
    expect(errores[0]).toMatchObject({ job_id: 503, status: 'FAILED', stage: 'ROI_DETECTION', progress_pct: 67 })
    const tanda = (await getExperiment('EXP-2026-02')).grupos.find((g) => g.id === 'G-04').tandas[0]
    expect(tanda.estado).toBe('FAILED')
    expect((await listNotifications())[0]).toMatchObject({ tipo: 'ANALYSIS_FAILED', enlace: '/analisis' })
    // El PDF de diagnóstico tarda unos segundos: mientras tanto, 404 («preparando…»).
    expect(await status(getDiagnostico(errores[0]))).toBe(Date.now() - errores[0].fallo_ms < 6000 ? 404 : 'ok')
    expect(await status(getDiagnostico({ ...errores[0], fallo_ms: Date.now() }))).toBe(404)
    const pdf = await getDiagnostico({ ...errores[0], fallo_ms: Date.now() - 60000 })
    expect(pdf.type).toBe('application/pdf')
    // Sin código de error: el mensaje se arma con la etapa en que se detuvo.
    expect((await listNotifications())[0].texto).toMatch(/No se encontraron los tubos en el video\.$/)
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
    expect((await getQueue()).cola[0]).toMatchObject({ status: 'RUNNING', stage: 'ROI_DETECTION', progress_pct: 67 })
  })
})

describe('pausa del análisis (R-15, D-89)', () => {
  // El trabajo 502 (Control · Tanda B) se pausa a la mitad de la clasificación: 4 s después de
  // empezar la simulación termina el 501, y 20 s después de eso se pausa el 502.
  const T0 = Date.now()
  const s = (n) => n * 1000
  const pausar = () => { avanzarSimulacion(T0); avanzarSimulacion(T0 + s(4 + 20)) }
  const tandaB = async () => (await getExperiment('EXP-2026-02')).grupos.find((g) => g.id === 'G-01').tandas[1]

  it('el análisis queda en pausa, sale de la cola y avisa', async () => {
    pausar()
    const { cola, pausas } = await getQueue()
    expect(cola.map((j) => j.job_id)).toEqual([503]) // la cola sigue con el siguiente
    expect(pausas[0]).toMatchObject({ job_id: 502, status: 'PAUSED', pausa: { cilindro: 2, desde_segundo: 142 } })
    expect((await tandaB()).estado).toBe('PAUSED')
    expect((await listNotifications())[0]).toMatchObject({ tipo: 'ANALYSIS_PAUSED', enlace: '/experimentos/EXP-2026-02/analisis/502/pausa' })
  })

  it('solo quien subió el video o un Administrador ve la pausa', async () => {
    pausar()
    sesion(3)
    expect(await status(getPausa('EXP-2026-02', 502))).toBe(403)
    sesion(1)
    const p = await getPausa('EXP-2026-02', 502)
    expect(p).toMatchObject({ cilindro: 2, desdeSegundo: 142, ancho: 1280, alto: 720 })
    expect(p.recuadros).toHaveLength(4)
    expect(p.segundosRestantes).toBeGreaterThan(290)
    expect(p.segundosRestantes).toBeLessThanOrEqual(330)
    sesion(2)
    expect(await status(getPausa('EXP-2026-02', 502))).toBe('ok')
  })

  it('ajustar devuelve el análisis a la cola por delante de los nuevos y termina', async () => {
    pausar()
    sesion(1)
    expect(await status(ajustarRecuadro('EXP-2026-02', 502, { cilindro: 2, x0: 370, y0: 130, x1: 590, y1: 640 }))).toBe('ok')
    expect((await getQueue()).cola.map((j) => [j.job_id, j.posicion])).toEqual([[503, 1], [502, 2]])
    expect((await tandaB()).estado).toBe('QUEUED')
    expect(await status(ajustarRecuadro('EXP-2026-02', 502, { cilindro: 2, x0: 0, y0: 0, x1: 99, y1: 99 }))).toBe(409)
    avanzarSimulacion(T0 + s(60)) // 503 falla en la etapa de cilindros; 502 sigue desde la pausa y termina
    expect((await tandaB()).estado).toBe('DONE')
  })

  it('cancelar lo termina sin aviso; si pasan 5 minutos, se cancela solo y avisa', async () => {
    pausar()
    sesion(1)
    expect(await status(cancelarAnalisis('EXP-2026-02', 502))).toBe('ok')
    expect(await status(cancelarAnalisis('EXP-2026-02', 502))).toBe(409)
    expect((await getQueue()).cancelados[0]).toMatchObject({ job_id: 502, motivo: 'usuario' })
    expect((await tandaB()).estado).toBe('CANCELED')
    expect((await listNotifications()).some((n) => n.tipo === 'ANALYSIS_CANCELED')).toBe(false)

    reiniciarDatos()
    pausar()
    avanzarSimulacion(T0 + s(4 + 20) + 5 * 60 * 1000)
    expect((await getQueue()).cancelados[0]).toMatchObject({ job_id: 502, motivo: 'plazo' })
    expect((await listNotifications()).some((n) => n.tipo === 'ANALYSIS_CANCELED')).toBe(true)
  })
})

describe('comparación entre grupos calculada', () => {
  const T0 = Date.UTC(2026, 9, 7, 15, 0, 0)

  it('sale de las tandas: medias, pendientes y grupos que mezclan niveles', async () => {
    const [control, referencia, expA, expB] = await getGroupComparison('EXP-2026-02')
    expect(control).toMatchObject({ grupo: 'Control · Placebo (solución salina)', n: 4, n_total: 8, media_s: 168.5, de_s: 21.3,
      pendientes: [{ tanda: 'B', n: 4, estado: 'QUEUED' }] })
    expect(referencia.por_nivel).toEqual([
      { tanda: 'A', nivel: 'PRECISO', n: 4, media_s: 96.5, de_s: 18.4 },
      { tanda: 'B', nivel: 'AGRUPADO', n: 4, media_s: 101.3, de_s: 15.2 },
    ])
    expect(expA).toMatchObject({ n: 4, pendientes: [{ tanda: 'B', estado: 'RUNNING' }] })
    expect(expB).toMatchObject({ n: 0, media_s: null, pendientes: [{ tanda: 'A', estado: 'QUEUED' }, { tanda: 'B', estado: 'FAILED' }] })
  })

  it('se actualiza cuando la simulación termina un trabajo', async () => {
    avanzarSimulacion(T0)
    avanzarSimulacion(T0 + 9000) // termina Experimental A · Tanda B
    const expA = (await getGroupComparison('EXP-2026-02'))[2]
    expect(expA).toMatchObject({ n: 8, pendientes: [] })
  })

  it('la tanda con nivel agrupado trae conducta activa e inmovilidad', async () => {
    const r = await getBatchResults('EXP-2026-02', 'G-02', 'B')
    expect(r.nivel).toBe('AGRUPADO')
    expect(r.especimenes[0]).toEqual({ numeroCilindro: 1, activa_s: 217, inmovilidad_s: 83 })
  })

  it('la línea de tiempo suma 60 s por minuto y coincide con los totales del espécimen', async () => {
    const r = await getBatchResults('EXP-2026-02', 'G-01', 'A')
    const { minutos } = r.linea_tiempo
    expect(minutos.map((m) => m.reduce((a, [, s]) => a + s, 0))).toEqual([60, 60, 60, 60, 60])
    const total = (c) => minutos.flat().filter(([k]) => k === c).reduce((a, [, s]) => a + s, 0)
    expect([total('nado'), total('inmovilidad'), total('escalamiento')]).toEqual([127, 145, 28])
  })
})

describe('cancelar la subida de un video', () => {
  it('no registra la tanda ni la encola, y falla como cancelación', async () => {
    const control = new AbortController()
    const subida = uploadBatchVideo('EXP-2026-02', 'G-02', 'C', { file: null, dia: 'DAY2', nCilindros: 4, signal: control.signal })
    control.abort()
    const error = await subida.catch((e) => e)
    expect(error.code).toBe('ERR_CANCELED')
    expect((await getQueue()).cola).toHaveLength(3)
    const referencia = (await getExperiment('EXP-2026-02')).grupos.find((g) => g.id === 'G-02')
    expect(referencia.tandas.map((t) => t.letra)).toEqual(['A', 'B'])
  })
})
