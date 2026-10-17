import api from './api'
import { usaEjemplo } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'
import { resumir } from './mocks/resumen'
import { usuarioDeLaSesion } from './mocks/sesion'
import { getExperiment } from './experiments'
import { ESTADO, SESION, TIPO_GRUPO, ordinalDeLetra, letraDeOrdinal } from '../lib/contrato'
import { norm } from '../lib/fst'

// Datos de ejemplo, salvo que esta parte ya esté conectada (VITE_API_REAL).
const USE_MOCKS = usaEjemplo('videos')

// Grupo con sus tandas y videos (pantalla 2d). La guía no tiene ruta de grupo: sale del árbol
// de GET /experiments/:clave (§6.1).
export async function getGroup(clave, gid) {
  const exp = await getExperiment(clave)
  const grupo = exp.grupos.find((g) => g.id === gid)
  if (!grupo) return fail(404, 'No existe ese grupo.')
  return { experimento: { clave: exp.clave, titulo: exp.titulo }, ...grupo }
}

// GET /experiments/:clave/groups?q= → grupos del experimento con nombre parecido y la tanda
// que sigue (§6, ruta propuesta por la guía). La forma no está en §6.1; propuesta:
// [{ idGrupo, etiqueta, tipo, tratamiento, siguienteOrdinal }]. Devuelve las sugerencias con
// la forma de la pantalla: { id, nombre, tratamiento, tipo, siguiente } (siguiente: letra).
export async function sugerirGrupos(clave, q) {
  let d
  if (USE_MOCKS) {
    const exp = db.experimentDetail[clave]
    if (!exp) return fail(404, 'No existe ese experimento.')
    const nq = norm(q.trim())
    d = await reply(exp.grupos
      .filter((g) => nq && norm(g.nombre + ' · ' + g.tratamiento).includes(nq))
      .map((g) => ({ idGrupo: g.id, etiqueta: g.nombre, tipo: TIPO_GRUPO.aGuia[g.tipo], tratamiento: g.tratamiento, siguienteOrdinal: Math.max(0, ...g.tandas.map((t) => ordinalDeLetra(t.letra))) + 1 })), 0)
  } else {
    d = (await api.get(`/experiments/${clave}/groups`, { params: { q } })).data
  }
  return d.map((g) => ({ id: g.idGrupo, nombre: g.etiqueta, tratamiento: g.tratamiento, tipo: TIPO_GRUPO.aFront[g.tipo], siguiente: letraDeOrdinal(g.siguienteOrdinal) }))
}

// Simula la subida, la validación del servidor y lo que guarda (sin backend).
async function subirDeEjemplo(clave, grupo, { archivo, sesion, tipo, tratamiento, nCilindros, ordinal, signal }, onProgress) {
  const exp = db.experimentDetail[clave]
  if (!exp) return fail(404, 'No existe ese experimento.')
  const cancelada = () => Object.assign(new Error('Subida cancelada.'), { code: 'ERR_CANCELED' })
  for (let p = 25; p <= 100; p += 25) {
    if (signal?.aborted) throw cancelada()
    await reply(null, 80)
    if (signal?.aborted) throw cancelada()
    onProgress?.(p)
  }
  // El servidor valida el video al recibirlo (D-64). Sin backend, el nombre del archivo
  // simula los rechazos, como hará worker_fake con «falla» y «pausa».
  const nombre = archivo?.name || ''
  if (/vertical/i.test(nombre)) return fail(422, 'El video es vertical: se necesita un video horizontal, con los cilindros de lado a lado.')
  if (/da[ñn]ado/i.test(nombre)) return fail(422, 'El video no se puede reproducir: el archivo está dañado o incompleto.')
  // El grupo se crea con su primera tanda si no existe (tipo y tratamiento, solo entonces).
  let g = exp.grupos.find((x) => x.nombre === grupo)
  if (!g) {
    if (!tipo || !tratamiento) return fail(422, 'Un grupo nuevo necesita tipo y tratamiento.')
    g = { id: 'G-' + String(exp.grupos.length + 1).padStart(2, '0'), nombre: grupo, tipo: TIPO_GRUPO.aFront[tipo], tratamiento, n_especimenes: 0, tandas: [] }
    exp.grupos.push(g)
  }
  const letra = letraDeOrdinal(ordinal || Math.max(0, ...g.tandas.map((t) => ordinalDeLetra(t.letra))) + 1)
  const dia1 = sesion === 'Dia 1'
  let t = g.tandas.find((x) => x.letra === letra)
  // Una tanda lleva un solo video por sesión: 409 si ya tiene el de ese día (§6.1).
  if (t && (dia1 ? t.dia1 : t.estado !== 'SIN_VIDEO')) {
    return fail(409, 'La Tanda ' + letra + ' ya tiene su video de ' + (dia1 ? 'Día 1' : 'Día 2') + '. Cada tanda lleva un solo video por sesión.')
  }
  if (!t) {
    const desde = g.tandas.reduce((a, x) => a + x.n_cilindros, 0) + 1
    const hoy = new Date().toISOString().slice(0, 10)
    t = { letra, desde, n_cilindros: nCilindros, fecha_dia1: hoy, fecha_dia2: hoy, estado: 'SIN_VIDEO', dia1: null, progreso: null, error: null, analisis_fecha: null }
    g.tandas.push(t)
    g.n_especimenes += nCilindros
  }
  const idVideo = Date.now()
  const subido_por = usuarioDeLaSesion()?.idInstitucional ?? null
  if (dia1) {
    Object.assign(t, { dia1: { cargado: true }, subido_por: t.subido_por ?? subido_por })
    resumir(clave)
    return reply({ idVideo, idAnalisis: null, estadoAnalisis: null })
  }
  // Día 2: su análisis entra al final de la cola.
  const job_id = idVideo + 1
  Object.assign(t, { estado: 'QUEUED', id_analisis: job_id, subido_por, progreso: null, error: null, analisis_fecha: null })
  db.queue.push({
    job_id, posicion: db.queue.length + 1, clave, gid: g.id, creado_ms: Date.now(), experimento: exp.titulo.split(' · ')[0],
    grupo: g.nombre, tanda: letra, dia: 'DAY2', n_especimenes: t.n_cilindros, subido_por, status: 'QUEUED', stage: null, progress_pct: 0,
  })
  resumir(clave)
  return reply({ idVideo, idAnalisis: job_id, estadoAnalisis: 'en cola' })
}

// POST /experiments/:clave/groups/:grupo/videos (multipart, §6.1): archivo, sesion ('Dia 1' o
// 'Dia 2'), tipo y tratamiento (solo si el grupo es nuevo), nCilindros (solo si la tanda es
// nueva) y ordinal (la tanda confirmada). Crea el grupo y la tanda si no existen y, si es el
// Día 2, encola el análisis. :grupo es la etiqueta del grupo (por confirmar con Erick, que lo
// ve probable porque el grupo puede no existir todavía).
// Respuesta 201 { idVideo, idAnalisis, estadoAnalisis }; con el Día 1 los dos últimos son null.
// Rechazos: 422 (formato, vertical, no reproducible) y 409 (la tanda ya tiene ese video).
// signal (AbortController) cancela la subida: no se registra nada y la promesa falla con
// code 'ERR_CANCELED', igual que axios.
export async function subirVideo(clave, { grupo, nuevo, tipo, tratamiento, letra, sesion, nCilindros, file, signal }, onProgress) {
  const campos = {
    archivo: file,
    sesion: SESION.aGuia[sesion],
    ...(nuevo && { tipo: TIPO_GRUPO.aGuia[tipo], tratamiento }),
    nCilindros,
    ordinal: ordinalDeLetra(letra),
  }
  let d
  if (USE_MOCKS) {
    d = await subirDeEjemplo(clave, grupo, { ...campos, signal }, onProgress)
  } else {
    const fd = new FormData()
    for (const [k, v] of Object.entries(campos)) if (v != null) fd.append(k, v instanceof Blob ? v : String(v))
    d = (await api.post(`/experiments/${clave}/groups/${encodeURIComponent(grupo)}/videos`, fd, {
      signal,
      onUploadProgress: (e) => e.total && onProgress?.(Math.round((e.loaded / e.total) * 100)),
    })).data
  }
  return { idVideo: d.idVideo, job_id: d.idAnalisis, estado: d.estadoAnalisis ? ESTADO.aFront[d.estadoAnalisis] : null }
}
