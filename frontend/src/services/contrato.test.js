// Los servicios contra un backend simulado que responde como la guía (§6, §6.1), con el
// envoltorio { data, error, status }. Así se prueban los caminos que no usan datos de ejemplo
// (VITE_USE_MOCKS=false) antes de que exista el backend de Spring.
import { describe, it, expect, beforeAll, afterAll, vi } from 'vitest'

const env = (data, status = 200) => ({ data: { data, error: null, status }, status, headers: {}, config: {} })

// Respuestas de ejemplo de §6.1 (y de los campos de más propuestos en PARA_ERICK.md).
const DETALLE = {
  idExperimento: 7, nombre: 'Exp. A', fecha: '2026-10-01', notas: '', estado: 'completado',
  creador: { idInstitucional: '2019630871', nombre: 'Mariana', apellidos: 'Rivera Alcántara' },
  grupos: [{ idGrupo: 1, etiqueta: 'Control', tipo: 'control', tratamiento: 'Salina',
    tandas: [{ idTanda: 1, ordinal: 1, letra: 'A', nCilindros: 4,
      videos: [{ idVideo: 1, sesion: 'Dia 2', duracion: 300.0, videoDisponible: true, idAnalisis: 1, subidoPor: '2019630871' }] }] }],
}
const STATUS = { analisis: [{ idAnalisis: 1, tanda: 'Control · A', sesion: 'Dia 2', estado: 'completado', etapa: 'clasificacion', porcentaje: 100, pausa: null, diagnosticoDisponible: false, fechaAnalisis: '2026-10-02T10:00:00' }] }
const totales = (n, i, e, a) => ({ 'nado activo': { segundos: n, porcentaje: n / 3 }, inmovilidad: { segundos: i, porcentaje: i / 3 }, escalamiento: { segundos: e, porcentaje: e / 3 }, 'conducta activa': { segundos: a, porcentaje: a / 3 } })
const RESULTADOS = { especimenes: [
  { tanda: 'Control · A', ordinal: 1, numeroCilindro: 2, nivelClasif: 'preciso', revisado: false, totales: totales(60, 180, 60, 0) },
  { tanda: 'Control · A', ordinal: 1, numeroCilindro: 1, nivelClasif: 'preciso', revisado: true, totales: totales(100, 150, 50, 0) },
  { tanda: 'Otro · A', ordinal: 1, numeroCilindro: 1, nivelClasif: 'preciso', revisado: false, totales: totales(1, 1, 1, 0) },
] }
const POR_MINUTO = { especimenes: RESULTADOS.especimenes.map((e) => ({ ...e, minutos: [1, 2, 3, 4, 5].map((minuto) => ({ minuto, 'nado activo': 20, inmovilidad: 40, escalamiento: 0, 'conducta activa': 0 })) })) }

let api, auth, experiments, results, revision, notifications, groups
const llamadas = []

beforeAll(async () => {
  vi.stubEnv('VITE_USE_MOCKS', 'false')
  vi.resetModules()
  globalThis.localStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} }
  api = (await import('./api')).default
  auth = await import('./auth')
  experiments = await import('./experiments')
  results = await import('./results')
  revision = await import('./revision')
  notifications = await import('./notifications')
  groups = await import('./groups')
  // Pasa por el interceptor de api.js (que desenvuelve), como una respuesta de verdad.
  const desenvolver = (r) => api.interceptors.response.handlers[0].fulfilled(r)
  const responder = (metodo) => vi.spyOn(api, metodo).mockImplementation(async (url, cuerpo) => {
    llamadas.push([metodo, url, cuerpo])
    const ruta = url.split('?')[0]
    if (ruta === '/auth/login') return desenvolver(env({ token: 'jwt', rol: 'Investigador', nombre: 'Mariana', cambioRequerido: false, idInstitucional: '2019630871', apellidos: 'Rivera Alcántara', correo: 'mrivera@ipn.mx' }))
    if (ruta === '/experiments') return desenvolver(env({ pagina: 1, total: 1, items: [{ idExperimento: 7, nombre: 'Exp. A', fecha: '2026-10-01', nGrupos: 3, estado: 'completado', avisoVencimiento: { fechaBorrado: '2026-11-12' } }], resumen: { total: 30, porEstado: { completado: 1, 'en espera del Día 2': 4, 'sin videos': 25 }, anios: ['2026', '2025'], tratamientos: ['Ácido Fólico', 'Control'], avisos: [{ idExperimento: 7, nombre: 'Exp. A', fechaBorrado: '2026-11-12' }] } }))
    if (ruta === '/experiments/7') return desenvolver(env(DETALLE))
    if (ruta === '/experiments/7/status') return desenvolver(env(STATUS))
    if (ruta === '/experiments/7/results') return desenvolver(env(RESULTADOS))
    if (ruta === '/experiments/7/results/byminute') return desenvolver(env(POR_MINUTO))
    if (ruta === '/experiments/7/analyses/1/review') return desenvolver(env({ puedeCorregir: true, videoUrl: '/experiments/7/analyses/1/video', especimenes: [{ numeroCilindro: 1, tanda: 'Control · A', recuadro: { x0: 245, y0: 483, x1: 577, y1: 927 }, segundos: [{ segundo: 1, conducta: 'nado activo', origen: 'maquina' }] }] }))
    if (ruta === '/experiments/7/analyses/1/video-token') return desenvolver(env({ token: 'vt1' }))
    if (ruta === '/notifications') return desenvolver(env([{ idNotificacion: 3, tipo: 'analisis en pausa', mensaje: 'x', leido: false, fechaCreacion: '2026-10-02T10:00:00', idExperimento: 7 }]))
    if (ruta === '/experiments/7/groups/Control/videos') return desenvolver(env({ idVideo: 9, idAnalisis: 4, estadoAnalisis: 'en cola' }, 201))
    throw new Error('Ruta no simulada: ' + metodo + ' ' + url)
  })
  responder('get')
  responder('post')
  responder('patch')
})

afterAll(() => {
  vi.unstubAllEnvs()
  vi.restoreAllMocks()
})

describe('servicios contra un backend que responde como la guía', () => {
  it('login: manda {correo, contrasena} y entiende la respuesta', async () => {
    const r = await auth.login('mrivera@ipn.mx', 'x')
    expect(llamadas.at(-1)).toEqual(['post', '/auth/login', { correo: 'mrivera@ipn.mx', contrasena: 'x' }])
    expect(r).toMatchObject({ token: 'jwt', user: { id: '2019630871', role: 'INVESTIGADOR', must_change_password: false } })
  })

  it('lista y detalle con /status', async () => {
    const lista = await experiments.listExperiments()
    expect(lista.items[0]).toMatchObject({ clave: '7', titulo: 'Exp. A', estado: 'DONE', aviso_vencimiento: { fecha_borrado: '2026-11-12' } })
    expect(lista).toMatchObject({ pagina: 1, total: 1, porPagina: 20 })
    // El resumen, con la clave del frontend en los conteos y la forma de la pantalla en los avisos.
    expect(lista.resumen).toEqual({
      total: 30, por_estado: { DONE: 1, ESPERA_DIA2: 4, SIN_VIDEOS: 25 }, anios: ['2026', '2025'], tratamientos: ['Ácido Fólico', 'Control'],
      avisos: [{ clave: '7', titulo: 'Exp. A', fecha_borrado: '2026-11-12' }],
    })
    const e = await experiments.getExperiment('7')
    expect(e.grupos[0]).toMatchObject({ id: '1', nombre: 'Control', tipo: 'CONTROL' })
    expect(e.grupos[0].tandas[0]).toMatchObject({ letra: 'A', estado: 'DONE', id_analisis: 1, analisis_fecha: '2026-10-02T10:00:00', subido_por: '2019630871' })
  })

  it('la lista manda al servidor los filtros con los valores de la guía y no manda los vacíos', async () => {
    await experiments.listExperiments()
    expect(llamadas.at(-1)).toEqual(['get', '/experiments', { params: { pagina: 1 } }])
    await experiments.listExperiments({ estado: 'ESPERA_DIA2', anio: '2026', tratamiento: 'Ácido Fólico', q: '  solucion ', pagina: 2 })
    expect(llamadas.at(-1)).toEqual(['get', '/experiments', { params: { pagina: 2, estado: 'en espera del Día 2', fecha: '2026', tratamiento: 'Ácido Fólico', q: 'solucion' } }])
    await experiments.listExperiments({ estado: '', anio: '', tratamiento: '', q: '   ' })
    expect(llamadas.at(-1)[2]).toEqual({ params: { pagina: 1 } })
    // Cada estado del frontend tiene su valor de la guía (incluidos «sin videos» y «en espera del Día 2»).
    for (const [estado, guia] of [['SIN_VIDEOS', 'sin videos'], ['ESPERA_DIA2', 'en espera del Día 2'], ['QUEUED', 'en cola'], ['RUNNING', 'procesando'], ['PAUSED', 'en pausa'], ['DONE', 'completado'], ['FAILED', 'error'], ['CANCELED', 'cancelado']]) {
      await experiments.listExperiments({ estado })
      expect(llamadas.at(-1)[2].params.estado).toBe(guia)
    }
  })

  it('resultados de una tanda: filtra por «etiqueta · letra» y ordena los especímenes', async () => {
    const r = await results.getBatchResults('7', '1', 'A')
    expect(r.especimenes).toEqual([
      { numeroCilindro: 1, nado_s: 100, inmovilidad_s: 150, escalamiento_s: 50, activa_s: 0 },
      { numeroCilindro: 2, nado_s: 60, inmovilidad_s: 180, escalamiento_s: 60, activa_s: 0 },
    ])
    expect(r).toMatchObject({ nivel: 'PRECISO', revisado: true, id_analisis: 1 })
    expect(r.lineas_tiempo[0].minutos[0]).toEqual([['nado', 20], ['inmovilidad', 40]])
  })

  it('revisión: completa lo que la guía no trae y pone el token del video', async () => {
    const d = await revision.getRevision('7', '1')
    expect(d).toMatchObject({ experimento: { clave: '7' }, grupo: { id: '1', nombre: 'Control' }, tanda: 'A', duracion: 300, videoDisponible: true })
    expect(d.videoUrl).toMatch(/\/experiments\/7\/analyses\/1\/video\?vt=vt1$/)
  })

  it('notificaciones: título y enlace con tipo e idExperimento', async () => {
    expect((await notifications.listNotifications())[0]).toMatchObject({ id: 3, titulo: 'Análisis en pausa', enlace: '/analisis?experimento=7', is_read: false })
  })

  it('subir video: un solo POST multipart con los campos de §6.1', async () => {
    const r = await groups.subirVideo('7', { grupo: 'Control', nuevo: false, letra: 'B', sesion: 'DAY2', nCilindros: 3, file: new Blob(['x']) })
    const [, url, fd] = llamadas.at(-1)
    expect(url).toBe('/experiments/7/groups/Control/videos')
    expect(Object.fromEntries([...fd.entries()].filter(([k]) => k !== 'archivo'))).toEqual({ sesion: 'Dia 2', nCilindros: '3', ordinal: '2' })
    expect(r).toMatchObject({ job_id: 4, estado: 'QUEUED' })
  })
})
