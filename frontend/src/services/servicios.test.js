// Pruebas de la capa de servicios con datos de ejemplo (USE_MOCKS). Cubren las reglas
// que el backend también tendrá que cumplir: contraseñas, administradores y experimentos.
import { describe, it, expect, beforeEach, vi } from 'vitest'
import api from './api'
import * as auth from './auth'
import { mensajeError, esEnvoltorio } from './api'
import * as admin from './admin'
import { createExperiment, deleteExperiment, getExperiment, listExperiments, deFila, deDetalle } from './experiments'
import { subirVideo, sugerirGrupos } from './groups'

// Sube el video de una tanda (por defecto, la A del grupo nuevo «Control», Día 2).
const subir = (clave, extra = {}, alAvanzar) => subirVideo(clave, { grupo: 'Control', nuevo: true, tipo: 'CONTROL', tratamiento: 'Solución salina', letra: 'A', sesion: 'DAY2', nCilindros: 4, file: null, ...extra }, alAvanzar)
import { getDiagnostico, getPausa, getImagenPausa, ajustarRecuadro, cancelarAnalisis } from './queue'
import { getQueue } from './progreso'
import { getBatchResults, getGroupComparison, getBlocks, getReporte } from './results'
import { reiniciarDatos, resetTokens } from './mocks/data'
import { listNotifications, markAllRead, deNotificacion } from './notifications'
import { getRevision, guardarSegundos, reiniciarAnalisis, urlDeVideo } from './revision'

const status = (promesa) => promesa.then(() => 'ok', (e) => e.response.status)

// En Node no hay localStorage: uno en memoria para elegir la cuenta de la sesión.
const almacen = new Map()
globalThis.localStorage = {
  getItem: (k) => almacen.get(k) ?? null,
  setItem: (k, v) => almacen.set(k, String(v)),
  removeItem: (k) => almacen.delete(k),
}
// Cuentas de ejemplo por número: 1 M. Rivera, 2 C. Reyes (Administrador), 3 J. Domínguez.
const ID_INSTITUCIONAL = { 1: '2019630871', 2: '2008630412', 3: '2015630322' }
const sesion = (n) => localStorage.setItem('fst.token', 'mock-token-' + ID_INSTITUCIONAL[n])

beforeEach(() => {
  reiniciarDatos()
  almacen.clear()
})

describe('lista de experimentos con la forma de §6.1', () => {
  it('cada fila se pasa a la forma de las pantallas', () => {
    const e = { idExperimento: 7, nombre: 'Exp. A', fecha: '2026-10-01', nGrupos: 3, estado: 'en espera del Día 2', avisoVencimiento: { fechaBorrado: '2026-11-12' },
      creador: { idInstitucional: '2019630871', nombre: 'Mariana', apellidos: 'Rivera Alcántara' } }
    expect(deFila(e)).toMatchObject({ clave: '7', titulo: 'Exp. A', fecha_inicio: '2026-10-01', n_grupos: 3, estado: 'ESPERA_DIA2',
      aviso_vencimiento: { fecha_borrado: '2026-11-12' }, responsable: 'M. Rivera', creador_id: '2019630871', tratamientos: [], con_detalle: true })
  })
})

describe('detalle con la forma de §6.1 y el estado de /status', () => {
  it('el ejemplo de la guía se pasa a la forma de las pantallas', () => {
    const d = { idExperimento: 7, nombre: 'Exp. A', fecha: '2026-10-01', notas: '', estado: 'procesando',
      grupos: [{ idGrupo: 1, etiqueta: 'Control', tipo: 'control', tratamiento: 'Salina',
        tandas: [
          { idTanda: 2, ordinal: 2, letra: 'B', nCilindros: 3, videos: [{ idVideo: 3, sesion: 'Dia 1', duracion: 300, videoDisponible: true, idAnalisis: null }] },
          { idTanda: 1, ordinal: 1, letra: 'A', nCilindros: 4, videos: [{ idVideo: 1, sesion: 'Dia 2', duracion: 300, videoDisponible: false, idAnalisis: 1, subidoPor: '2019630871' }] },
        ] }] }
    const analisis = [{ idAnalisis: 1, tanda: 'Control · A', sesion: 'Dia 2', estado: 'procesando', etapa: 'cilindros', porcentaje: 67, pausa: null, diagnosticoDisponible: false }]
    const e = deDetalle(d, analisis)
    expect(e).toMatchObject({ clave: '7', titulo: 'Exp. A', estado: 'RUNNING' })
    expect(e.grupos[0]).toMatchObject({ id: '1', nombre: 'Control', tipo: 'CONTROL', n_especimenes: 7 })
    const [a, b] = e.grupos[0].tandas
    expect(a).toMatchObject({ letra: 'A', desde: 1, estado: 'RUNNING', progreso: { etapa: 'ROI_DETECTION', pct: 67 }, id_analisis: 1, subido_por: '2019630871', video_disponible: false })
    expect(b).toMatchObject({ letra: 'B', desde: 5, estado: 'SIN_VIDEO', dia1: { cargado: true }, id_analisis: null })
  })
})

describe('notificaciones con los campos de la guía', () => {
  it('el título y el enlace salen del tipo y del experimento', () => {
    const n = (tipo, idExperimento = 7) => deNotificacion({ idNotificacion: 1, tipo, mensaje: 'x', leido: false, fechaCreacion: '2026-10-15T15:41:00', idExperimento })
    expect(n('analisis completado')).toMatchObject({ id: 1, tipo: 'ANALYSIS_DONE', titulo: 'Análisis completado', enlace: '/experimentos/7/resultados', texto: 'x', is_read: false })
    expect(n('error del pipeline').enlace).toBe('/analisis?experimento=7')
    expect(n('video borrado').enlace).toBe('/experimentos/7')
    expect(n('alerta de disco', null).enlace).toBe('/admin')
  })

  it('marcar todas como leídas usa PATCH por cada una (no hay ruta para todas)', async () => {
    sesion(1)
    await markAllRead()
    expect((await listNotifications()).every((n) => n.is_read)).toBe(true)
  })
})

describe('video de la revisión con token (?vt=)', () => {
  it('pide el token con la sesión y lo pone en la URL del video, no el JWT', async () => {
    localStorage.setItem('fst.token', 'jwt-de-sesion')
    const post = vi.spyOn(api, 'post').mockResolvedValue({ data: { token: 'tok/1' } })
    const url = await urlDeVideo(7, 1, '/experiments/7/analyses/1/video')
    expect(post).toHaveBeenCalledWith('/experiments/7/analyses/1/video-token')
    expect(url).toBe(api.defaults.baseURL.replace(/\/$/, '') + '/experiments/7/analyses/1/video?vt=tok%2F1')
    expect(url).not.toContain('jwt-de-sesion')
    post.mockRestore()
  })
})

describe('inicio de sesión', () => {
  it('la cuenta llega con la forma de §6.1 y se pasa a la de las pantallas', () => {
    const c = { idInstitucional: '2019630871', nombre: 'Mariana', apellidos: 'Rivera', correo: 'mrivera@ipn.mx', rol: 'Administrador', activo: false, cambioRequerido: true, nExperimentos: 2 }
    expect(auth.deCuenta(c)).toEqual({ id: '2019630871', identificador: '2019630871', nombre: 'Mariana', apellidos: 'Rivera', email: 'mrivera@ipn.mx', role: 'ADMIN', is_active: false, must_change_password: true, n_experimentos: 2 })
  })

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
  it('crear asigna el id siguiente y empieza sin grupos', async () => {
    const { clave } = await createExperiment({ titulo: 'Ketamina · dosis única', fecha_inicio: '2026-10-01', notas: '' })
    expect(clave).toBe('8')
    const exp = await getExperiment(clave)
    expect(exp.grupos).toEqual([])
    expect((await listExperiments())[0]).toMatchObject({ clave, estado: 'SIN_VIDEOS', n_grupos: 0 })
  })

  it('el nombre es único en todo el sistema (EXPERIMENTO.nombre UNIQUE)', async () => {
    expect(await status(createExperiment({ titulo: ' compuesto CSR-14 · CURVA de dosis ', fecha_inicio: '2026-10-01', notas: '' }))).toBe(409)
  })

  it('subir el primer video crea la tanda, la encola y actualiza la lista', async () => {
    const { clave } = await createExperiment({ titulo: 'Prueba', fecha_inicio: '2026-10-01', notas: '' })
    const avances = []
    const r = await subir(clave, {}, (p) => avances.push(p))

    expect(avances).toEqual([25, 50, 75, 100])
    expect(r).toMatchObject({ estado: 'QUEUED' })
    expect((await getQueue()).cola.at(-1)).toMatchObject({ job_id: r.job_id, grupo: 'Control', tanda: 'A', status: 'QUEUED', posicion: 3 })
    const tanda = (await getExperiment(clave)).grupos[0].tandas[0]
    expect(tanda).toMatchObject({ letra: 'A', desde: 1, n_cilindros: 4, estado: 'QUEUED' })
    const fila = (await listExperiments()).find((e) => e.clave === clave)
    expect(fila).toMatchObject({ n_grupos: 1, n_especimenes: 4, videos_dia2_total: 1, videos_dia2_cargados: 1, estado: 'QUEUED' })
  })

  it('el video de Día 1 se guarda pero no se analiza (D-54)', async () => {
    const { clave } = await createExperiment({ titulo: 'Prueba', fecha_inicio: '2026-10-01', notas: '' })
    const r = await subir(clave, { sesion: 'DAY1', nCilindros: 3 })

    expect(r).toMatchObject({ job_id: null, estado: null })
    expect((await getQueue()).cola.some((j) => j.clave === clave)).toBe(false)
    const tanda = (await getExperiment(clave)).grupos[0].tandas[0]
    expect(tanda).toMatchObject({ letra: 'A', n_cilindros: 3, estado: 'SIN_VIDEO', dia1: { cargado: true } })
    // Solo con el Día 1, el experimento queda «en espera del Día 2» (D-83).
    expect((await listExperiments()).find((e) => e.clave === clave).estado).toBe('ESPERA_DIA2')
  })

  it('una tanda lleva un solo video por sesión: el segundo de ese día responde 409', async () => {
    const existente = (grupo, sesion) => subir('7', { grupo, nuevo: false, sesion, nCilindros: undefined })
    expect(await status(existente('Referencia', 'DAY2'))).toBe(409)
    expect(await status(existente('Control', 'DAY1'))).toBe(409) // ya tiene su Día 1
    expect(await status(existente('Referencia', 'DAY1'))).toBe('ok') // su Día 1 faltaba
  })

  it('el servidor rechaza un video vertical o que no se puede reproducir (D-64)', async () => {
    const { clave } = await createExperiment({ titulo: 'Prueba', fecha_inicio: '2026-10-01', notas: '' })
    const conArchivo = (name) => subir(clave, { file: { name }, nCilindros: 2 })
    expect(await status(conArchivo('celular_vertical.mp4'))).toBe(422)
    expect(await status(conArchivo('dañado.mov'))).toBe(422)
    expect((await getQueue()).cola.some((j) => j.clave === clave)).toBe(false)
    expect(await status(conArchivo('tanda_A.mp4'))).toBe('ok')
  })

  it('solo quien creó el experimento sube videos; un Administrador no (D-107)', async () => {
    sesion(2) // Administrador; el experimento 7 lo creó M. Rivera
    expect(await status(subir('7', { grupo: 'Referencia', nuevo: false, letra: 'C' }))).toBe(403)
    sesion(1)
    expect(await status(subir('7', { grupo: 'Referencia', nuevo: false, letra: 'C' }))).toBe('ok')
  })

  it('un grupo nuevo necesita tipo y tratamiento; uno existente sigue con su siguiente tanda', async () => {
    expect(await status(subir('7', { grupo: 'Nuevo', tipo: undefined }))).toBe(422)
    expect(await sugerirGrupos('7', 'fluox')).toEqual([{ id: '2', nombre: 'Referencia', tratamiento: 'Fluoxetina 10 mg/kg', tipo: 'REFERENCIA', siguiente: 'C' }])
    expect(await sugerirGrupos('7', 'nada parecido')).toEqual([])
  })

  it('el estado del experimento sigue la precedencia del documento', async () => {
    // El de ejemplo tiene una tanda con error: «Con error» manda sobre las demás.
    expect((await listExperiments()).find((e) => e.clave === '7').estado).toBe('FAILED')
  })

  it('eliminar: solo quien lo creó o un Administrador, sin pedir nombre ni contraseña (D-63, R-08)', async () => {
    sesion(1) // M. Rivera
    expect(await status(deleteExperiment('6'))).toBe(403) // lo creó J. Domínguez
    expect(await status(deleteExperiment('5'))).toBe('ok')
    expect((await listExperiments()).some((e) => e.clave === '5')).toBe(false)
    expect(await status(deleteExperiment('5'))).toBe(404)
    sesion(2) // Administrador
    expect(await status(deleteExperiment('6'))).toBe('ok')
  })

  it('no se elimina con un análisis procesando; los que están en cola salen de la cola (D-87)', async () => {
    sesion(1)
    expect(await status(deleteExperiment('7'))).toBe(409) // Experimental A · Tanda B se procesa
    expect(await status(getExperiment('7'))).toBe('ok')

    const { clave } = await createExperiment({ titulo: 'Prueba', fecha_inicio: '2026-10-01', notas: '' })
    await subir(clave)
    expect((await getQueue()).cola.at(-1).clave).toBe(clave)
    expect(await status(deleteExperiment(clave))).toBe('ok')
    const { cola } = await getQueue()
    expect(cola.some((j) => j.clave === clave)).toBe(false)
    expect(cola.map((j) => j.posicion)).toEqual([1, 2])
  })

  it('avisa con la fecha exacta los videos que se borran en 7 días o menos (R-05)', async () => {
    const lista = await listExperiments()
    const valeriana = lista.find((e) => e.clave === '5') // le quedan 4 días
    const d = new Date()
    d.setDate(d.getDate() + 4)
    expect(valeriana.aviso_vencimiento.fecha_borrado).toBe(d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0'))
    expect(lista.find((e) => e.clave === '6').aviso_vencimiento).toBeNull() // 12 días
    expect(lista.find((e) => e.clave === '4').aviso_vencimiento).toBeNull() // ya se borraron
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
    const control = await getBatchResults('7', '1', 'A')
    expect(control.grupo.nombre).toBe('Control')
    expect(control.especimenes.map((e) => e.numeroCilindro)).toEqual([1, 2, 3, 4])
    const refB = await getBatchResults('7', '2', 'B')
    expect(refB.letra).toBe('B')
    expect(refB.especimenes.map((e) => e.numeroCilindro)).toEqual([1, 2, 3, 4])
  })

  it('una tanda en cola o con error todavía no tiene resultados', async () => {
    expect(await status(getBatchResults('7', '1', 'B'))).toBe(404)
    expect(await status(getBatchResults('7', '4', 'B'))).toBe(404)
  })
})

describe('cola de análisis (la avanza el worker)', () => {
  it('sin backend queda fija: uno procesando, uno en cola, uno en pausa y uno con error', async () => {
    const { cola, pausas, errores } = await getQueue()
    expect(cola.map((j) => [j.job_id, j.status, j.posicion])).toEqual([[501, 'RUNNING', 1], [503, 'QUEUED', 2]])
    expect(pausas.map((j) => j.job_id)).toEqual([502])
    expect(errores[0]).toMatchObject({ job_id: 498, status: 'FAILED', stage: 'ROI_DETECTION' })
  })

  it('trae los análisis completados recientemente, del más nuevo al más viejo', async () => {
    const { terminados } = await getQueue()
    expect(terminados.map((j) => j.job_id).sort()).toEqual([401, 402, 403, 404])
    expect(terminados.length).toBeGreaterThan(0)
    expect(terminados[0]).toMatchObject({ clave: '7', dia: 'DAY2' })
    expect(terminados.every((j, i) => i === 0 || (terminados[i - 1].analisis_fecha || '') >= (j.analisis_fecha || ''))).toBe(true)
  })

  it('el PDF de diagnóstico lo genera el worker: sin backend no hay archivo', async () => {
    const { errores } = await getQueue()
    expect(await getDiagnostico(errores[0])).toBeNull()
  })
})

describe('pausa del análisis (R-15, D-89)', () => {
  const tandaB = async () => (await getExperiment('7')).grupos.find((g) => g.id === '1').tandas[1]

  it('el análisis en pausa está fuera de la cola y avisa', async () => {
    expect((await tandaB()).estado).toBe('PAUSED')
    const { pausas } = await getQueue()
    expect(pausas[0]).toMatchObject({ job_id: 502, status: 'PAUSED', pausa: { cilindro: 2, desde_segundo: 142 } })
    expect((await listNotifications()).find((n) => n.tipo === 'ANALYSIS_PAUSED')).toMatchObject({ titulo: 'Análisis en pausa', enlace: '/analisis?experimento=7' })
  })

  it('sin video (ya se borró) la revisión se consulta pero no se corrige ni se reinicia', async () => {
    sesion(1)
    expect(await getRevision('7', 404)).toMatchObject({ videoDisponible: false })
    expect(await status(guardarSegundos('7', 404, [{ numeroCilindro: 1, segundos: [{ segundo: 1, conducta: null, origen: 'humano' }] }]))).toBe(409)
    expect(await status(reiniciarAnalisis('7', 404))).toBe(409)
    expect((await getBatchResults('7', '3', 'A')).video_disponible).toBe(false)
  })

  it('solo quien subió el video o un Administrador ve la pausa', async () => {
    sesion(3)
    expect(await status(getPausa('7', 502))).toBe(403)
    sesion(1)
    const p = await getPausa('7', 502)
    expect(p).toMatchObject({ cilindro: 2, desdeSegundo: 142, ancho: 1280, alto: 720 })
    expect(p.recuadros).toHaveLength(4)
    expect(p.segundosRestantes).toBeGreaterThan(72 * 3600 - 10) // empieza al consultarla: quedan casi 72 h
    expect(p.segundosRestantes).toBeLessThanOrEqual(72 * 3600)
    expect(await getImagenPausa('7', 502)).toBeNull() // la imagen la entrega el servidor
    sesion(2)
    expect(await status(getPausa('7', 502))).toBe('ok')
  })

  it('ajustar devuelve el análisis a la cola por delante de los que llegaron después', async () => {
    sesion(1)
    expect(await status(ajustarRecuadro('7', 502, { cilindro: 2, x0: 370, y0: 130, x1: 590, y1: 640 }))).toBe('ok')
    expect((await getQueue()).cola.map((j) => [j.job_id, j.posicion])).toEqual([[501, 1], [502, 2], [503, 3]])
    expect((await tandaB()).estado).toBe('QUEUED')
    expect(await status(ajustarRecuadro('7', 502, { cilindro: 2, x0: 0, y0: 0, x1: 99, y1: 99 }))).toBe(409)
  })

  it('cancelar lo termina; una segunda vez responde 409', async () => {
    sesion(1)
    expect(await status(cancelarAnalisis('7', 502))).toBe('ok')
    expect(await status(cancelarAnalisis('7', 502))).toBe(409)
    expect((await getQueue()).cancelados[0]).toMatchObject({ job_id: 502, motivo: 'usuario' })
    expect((await tandaB()).estado).toBe('CANCELED')
  })
})

describe('reportes (RF-22)', () => {
  it('los genera el backend: sin él no hay archivo', async () => {
    for (const f of ['csv', 'xlsx', 'pdf']) expect(await getReporte('7', f)).toBeNull()
  })
})

describe('revisión segundo a segundo (R-14)', () => {
  it('solo de un análisis completado y para quien subió el video o un Administrador', async () => {
    sesion(3)
    expect(await status(getRevision('7', 402))).toBe(403)
    sesion(1)
    expect(await status(getRevision('7', 503))).toBe(409) // en cola
    const r = await getRevision('7', 402)
    expect(r).toMatchObject({ puedeCorregir: true, tanda: 'A', duracion: 300 })
    expect(r.especimenes).toHaveLength(4)
    expect(r.especimenes[0].segundos).toHaveLength(300)
    expect(r.especimenes[0].segundos[0]).toMatchObject({ segundo: 1, origen: 'maquina' })
    expect(r.especimenes[0].segundos[0].conducta).toMatch(/^(nado activo|inmovilidad|escalamiento|conducta activa)$/)
    sesion(2)
    expect(await status(getRevision('7', 402))).toBe('ok')
  })

  it('guardar cambia los segundos, recalcula los totales y marca la revisión', async () => {
    sesion(1)
    const antes = (await getBatchResults('7', '2', 'A')).especimenes[0]
    const primero = (await getRevision('7', 402)).especimenes[0].segundos[0]
    expect(primero.conducta).not.toBe('escalamiento')
    await guardarSegundos('7', 402, [{ numeroCilindro: 1, segundos: [{ segundo: 1, conducta: 'escalamiento', origen: 'corregida' }, { segundo: 2, conducta: null, origen: 'dudosa' }] }])
    const r = await getRevision('7', 402)
    expect(r.especimenes[0].segundos.slice(0, 2)).toEqual([
      { segundo: 1, conducta: 'escalamiento', origen: 'corregida' },
      { segundo: 2, conducta: null, origen: 'dudosa' },
    ])
    const despues = await getBatchResults('7', '2', 'A')
    expect(despues.revisado).toBe(true)
    expect(despues.especimenes[0].escalamiento_s).toBeGreaterThan(antes.escalamiento_s - 1)
  })

  it('reiniciar borra la revisión y vuelve a encolar el análisis (D-60, D-68)', async () => {
    sesion(1)
    await guardarSegundos('7', 402, [{ numeroCilindro: 1, segundos: [{ segundo: 1, conducta: 'escalamiento', origen: 'corregida' }] }])
    expect(await status(reiniciarAnalisis('7', 402))).toBe('ok')
    expect((await getQueue()).cola.at(-1)).toMatchObject({ job_id: 402, grupo: 'Referencia', tanda: 'A', status: 'QUEUED' })
    expect(await status(getBatchResults('7', '2', 'A'))).toBe(404)
    expect(await status(getRevision('7', 402))).toBe(409)
  })
})

describe('comparación entre grupos calculada', () => {

  it('sale de las tandas: medias, pendientes y grupos que mezclan niveles', async () => {
    const [control, referencia, expA, expB] = await getGroupComparison('7')
    expect(control).toMatchObject({ grupo: 'Control · Placebo (solución salina)', n: 4, n_total: 8,
      pendientes: [{ tanda: 'B', n: 4, estado: 'PAUSED' }] })
    // Promedio por espécimen de cada conducta (no suma).
    expect(control.por_conducta).toEqual({
      nado: { media_s: 105.3, de_s: 19.3 }, inmovilidad: { media_s: 168.5, de_s: 21.3 },
      escalamiento: { media_s: 26.3, de_s: 3.5 }, activa: { media_s: 0, de_s: 0 },
    })
    expect(referencia.por_nivel.map((u) => [u.tanda, u.nivel, u.n, u.por_conducta.inmovilidad])).toEqual([
      ['A', 'PRECISO', 4, { media_s: 96.5, de_s: 18.4 }],
      ['B', 'AGRUPADO', 4, { media_s: 101.3, de_s: 15.2 }],
    ])
    expect(expA).toMatchObject({ n: 4, pendientes: [{ tanda: 'B', estado: 'RUNNING' }] })
    expect(expB).toMatchObject({ n: 0, por_conducta: { inmovilidad: { media_s: null } }, pendientes: [{ tanda: 'A', estado: 'QUEUED' }, { tanda: 'B', estado: 'FAILED' }] })
  })

  it('cada espécimen trae las cuatro conductas; con conducta activa el nivel es agrupado (R-12)', async () => {
    const r = await getBatchResults('7', '2', 'B')
    expect(r.nivel).toBe('AGRUPADO')
    expect(r.especimenes[0]).toEqual({ numeroCilindro: 1, nado_s: 40, inmovilidad_s: 83, escalamiento_s: 12, activa_s: 165 })
    const preciso = await getBatchResults('7', '1', 'A')
    expect(preciso.nivel).toBe('PRECISO')
    expect(preciso.especimenes.every((e) => e.activa_s === 0)).toBe(true)
  })

  it('los bloques de 5 s los calcula el backend: sin él no hay bloques', async () => {
    expect(await getBlocks('7', '1', 'A')).toBeNull()
  })

  it('el desglose por minuto (results/byminute) suma a lo más 60 s por minuto', async () => {
    const r = await getBatchResults('7', '1', 'A')
    expect(r.lineas_tiempo.map((l) => l.numeroCilindro)).toEqual([1, 2, 3, 4])
    const porMinuto = (l) => l.minutos.map((m) => m.reduce((a, [, s]) => a + s, 0))
    // El espécimen 2 se vio todo el tiempo; el 1 tiene segundos «no se ve», que no cuentan.
    expect(porMinuto(r.lineas_tiempo[1])).toEqual([60, 60, 60, 60, 60])
    expect(porMinuto(r.lineas_tiempo[0]).every((s) => s <= 60)).toBe(true)
  })
})

describe('cancelar la subida de un video', () => {
  it('no registra la tanda ni la encola, y falla como cancelación', async () => {
    const control = new AbortController()
    const subida = subir('7', { grupo: 'Referencia', nuevo: false, letra: 'C', signal: control.signal })
    control.abort()
    const error = await subida.catch((e) => e)
    expect(error.code).toBe('ERR_CANCELED')
    expect((await getQueue()).cola).toHaveLength(2)
    const referencia = (await getExperiment('7')).grupos.find((g) => g.id === '2')
    expect(referencia.tandas.map((t) => t.letra)).toEqual(['A', 'B'])
  })
})

describe('envoltorio y mensajes de error del servidor (§6)', () => {
  const con = (data) => ({ response: { status: 409, data } })
  it('el error se lee del campo «error» del envoltorio', () => {
    expect(mensajeError(con({ data: null, error: 'No existe ese grupo.', status: 404 }), 'x')).toBe('No existe ese grupo.')
    expect(mensajeError(con({ data: null, error: null, status: 500 }), 'respaldo')).toBe('respaldo')
    expect(mensajeError(con(new Blob(['x'])), 'respaldo')).toBe('respaldo')
    expect(mensajeError(new Error('sin respuesta'), 'respaldo')).toBe('respaldo')
  })

  it('se desenvuelve la respuesta JSON y no los archivos', () => {
    expect(esEnvoltorio({ data: { idExperimento: 7 }, error: null, status: 200 })).toBe(true)
    expect(esEnvoltorio(new Blob(['%PDF']))).toBe(false)
    expect(esEnvoltorio({ idExperimento: 7 })).toBe(false)
  })
})
