// Datos de ejemplo de los mockups v2 con la forma que tendrá la API.
// Las pantallas no los importan: los piden a la capa de servicios (../*.js),
// que los entrega mientras VITE_USE_MOCKS no sea 'false'.
//
// Lista de experimentos (2a) con la forma de §6.1: { idExperimento, nombre, fecha, nGrupos,
// estado, avisoVencimiento } y, de más sobre la guía, creador (PARA_VANESA_API.md §4) y los
// campos que pide la maqueta de la lista (propuestos, por confirmar con Erick):
// nEspecimenes, tratamientos (los de sus grupos, para el filtro), videosDia2 { analizados,
// cargados, total }, diasRetencion (días que le quedan al video crudo; null sin videos o ya
// borrados) y videosBorrados. avisoVencimiento lo calcula services/experiments.js.
// estado: el de la guía ('sin videos', 'en espera del Día 2', 'en cola', 'procesando',
// 'en pausa', 'completado', 'error' o 'cancelado'), calculado con las tandas.
// idExperimento e idGrupo: números, como en la base (paso 10).
// conDetalle: solo en datos de ejemplo; solo el experimento de ejemplo tiene detalle.
const RIVERA = { idInstitucional: '2019630871', nombre: 'Mariana', apellidos: 'Rivera Alcántara' }
const REYES = { idInstitucional: '2008630412', nombre: 'C. S.', apellidos: 'Reyes López' }
const DOMINGUEZ = { idInstitucional: '2015630322', nombre: 'J.', apellidos: 'Domínguez Vera' }
const fila = (idExperimento, nombre, fecha, creador, nGrupos, nEspecimenes, tratamientos, [analizados, cargados, total], estado, diasRetencion, videosBorrados, conDetalle = false) =>
  ({ idExperimento, nombre, fecha, nGrupos, estado, creador, nEspecimenes, tratamientos, videosDia2: { analizados, cargados, total }, diasRetencion, videosBorrados, conDetalle })

export const experiments = [
  fila(7, 'Compuesto CSR-14 · curva de dosis', '2026-02-18', RIVERA, 4, 32, ['Placebo (solución salina)', 'Fluoxetina 10 mg/kg', 'Compuesto CSR-14, 5 mg/kg', 'Compuesto CSR-14, 15 mg/kg'], [4, 8, 8], 'error', 30, false, true),
  fila(6, 'Extracto de Salvia · dosis única', '2026-01-09', DOMINGUEZ, 4, 32, ['Placebo (solución salina)', 'Fluoxetina 10 mg/kg', 'Extracto de Salvia, 20 mg/kg'], [8, 8, 8], 'completado', 12, false),
  fila(5, 'Extracto de Valeriana officinalis', '2025-11-14', RIVERA, 5, 38, ['Placebo (solución salina)', 'Fluoxetina 10 mg/kg', 'Valeriana officinalis, 50 mg/kg'], [10, 10, 10], 'completado', 4, false),
  fila(4, 'Réplica interanalista · control metodológico', '2025-10-02', REYES, 4, 24, ['Placebo (solución salina)', 'Fluoxetina 10 mg/kg'], [3, 3, 6], 'completado', null, true),
  fila(3, 'Fluoxetina · calibración del clasificador', '2025-08-19', REYES, 4, 32, ['Placebo (solución salina)', 'Fluoxetina 10 mg/kg', 'Fluoxetina 20 mg/kg'], [8, 8, 8], 'completado', null, true),
]

// Detalle (2c, 2d). Solo el experimento de ejemplo lo tiene.
// tipo: CONTROL | REFERENCIA | EXPERIMENTAL
// estado de tanda = estado del análisis de Día 2: QUEUED | RUNNING | DONE | FAILED,
//   como JobStatus en backend/app/models.py, o SIN_VIDEO si aún no se sube el Día 2.
// Cada tanda tiene los especímenes 1…n (su posición en el cuadro); desde es la posición de su
// primer espécimen contando todas las tandas del grupo.
// dia1: { cargado: true } si se subió el video de Día 1 (opcional; null si no). No se
//   analiza: solo confirma que hubo sesión de habituación (D-54).
// progreso: etapa (PipelineStage) y avance mientras corre; error: { etapa } en la que se
//   detuvo, si falló (sin código: la pantalla arma el mensaje con la etapa).
// video_disponible: false si el video del Día 2 ya se borró (30 días después del análisis o
//   por falta de disco); resultados y reportes se conservan.
const tanda = (letra, estado, extra = {}) => ({
  letra, desde: (letra.charCodeAt(0) - 65) * 4 + 1, n_cilindros: 4,
  fecha_dia1: '2026-02-18', fecha_dia2: '2026-02-19',
  estado, dia1: null, progreso: null, error: null, id_analisis: null, subido_por: '2019630871',
  analisis_fecha: estado === 'DONE' ? '2026-02-24' : null,
  ...extra,
})

export const experimentDetail = {
  7: {
    clave: 7, titulo: 'Compuesto CSR-14 · curva de dosis', fecha_inicio: '2026-02-18', responsable: 'M. Rivera', creador_id: '2019630871',
    grupos: [
      { id: 1, nombre: 'Control', tipo: 'CONTROL', tratamiento: 'Placebo (solución salina)', n_especimenes: 8,
        tandas: [tanda('A', 'DONE', { id_analisis: 401, dia1: { cargado: true } }), tanda('B', 'PAUSED', { id_analisis: 502 })] },
      { id: 2, nombre: 'Referencia', tipo: 'REFERENCIA', tratamiento: 'Fluoxetina 10 mg/kg', n_especimenes: 8,
        tandas: [tanda('A', 'DONE', { id_analisis: 402 }), tanda('B', 'DONE', { id_analisis: 403 })] },
      { id: 3, nombre: 'Experimental A', tipo: 'EXPERIMENTAL', tratamiento: 'Compuesto CSR-14, 5 mg/kg', n_especimenes: 8,
        tandas: [tanda('A', 'DONE', { id_analisis: 404, video_disponible: false }), tanda('B', 'RUNNING', { id_analisis: 501, progreso: { etapa: 'CLASSIFICATION', pct: 100 } })] },
      { id: 4, nombre: 'Experimental B', tipo: 'EXPERIMENTAL', tratamiento: 'Compuesto CSR-14, 15 mg/kg', n_especimenes: 8,
        tandas: [tanda('A', 'QUEUED', { id_analisis: 503 }), tanda('B', 'FAILED', { id_analisis: 498, error: { etapa: 'ROI_DETECTION' } })] },
    ],
  },
}

// Cola de análisis (2e). Un trabajo a la vez, en orden de llegada. La avanza el worker
// (equipo de backend): sin backend queda fija, como en un momento dado.
// status: JobStatus; stage: PipelineStage en la que va (o en la que se detuvo).
// Solo se analiza el Día 2 (D-54). subido_por: cuenta que subió el video (puede ajustar o
// cancelar su pausa, igual que un Administrador).
export const queue = [
  { job_id: 501, posicion: 1, encolado: '2026-02-24T16:00:00', clave: 7, gid: 3, experimento: 'Compuesto CSR-14', grupo: 'Experimental A', tanda: 'B', dia: 'DAY2', n_especimenes: 4, subido_por: '2019630871', status: 'RUNNING', stage: 'CLASSIFICATION', progress_pct: 100, error: null },
  { job_id: 503, posicion: 2, encolado: '2026-02-24T16:05:00', clave: 7, gid: 4, experimento: 'Compuesto CSR-14', grupo: 'Experimental B', tanda: 'A', dia: 'DAY2', n_especimenes: 4, subido_por: '2019630871', status: 'QUEUED', stage: null, progress_pct: 0, error: null },
]

// Trabajos que fallaron recientemente: salen de la cola y quedan con su causa.
export const failedJobs = [
  { job_id: 498, clave: 7, gid: 4, experimento: 'Compuesto CSR-14', grupo: 'Experimental B', tanda: 'B', dia: 'DAY2', n_especimenes: 4, subido_por: '2019630871', status: 'FAILED', stage: 'ROI_DETECTION', progress_pct: 67 },
]

// Análisis en pausa (R-15): salen de la cola mientras esperan el ajuste del recuadro. pausa:
// { cilindro, desde_segundo, hora_ms, ancho, alto, recuadros, ajuste }, como pausa.json.
// La pausa la decide el worker; el de ejemplo ya está en pausa. hora_ms null: sin backend,
// la pausa empieza la primera vez que se consulta (services/queue.js), para ver el plazo.
export const pausedJobs = [
  { job_id: 502, encolado: '2026-02-24T16:01:00', clave: 7, gid: 1, experimento: 'Compuesto CSR-14', grupo: 'Control', tanda: 'B', dia: 'DAY2', n_especimenes: 4, subido_por: '2019630871', status: 'PAUSED', stage: 'CLASSIFICATION', progress_pct: 100,
    pausa: { cilindro: 2, desde_segundo: 142, hora_ms: null, ancho: 1280, alto: 720, ajuste: null,
      // El recuadro del cilindro 2 quedó corrido: por eso el pipeline dejó de ver a su espécimen.
      recuadros: [{ cilindro: 1, x0: 50, y0: 130, x1: 270, y1: 640 }, { cilindro: 2, x0: 450, y0: 200, x1: 670, y1: 710 },
        { cilindro: 3, x0: 690, y0: 130, x1: 910, y1: 640 }, { cilindro: 4, x0: 1010, y0: 130, x1: 1230, y1: 640 }] } },
]

// Análisis cancelados: por el usuario o porque pasaron 5 minutos sin respuesta (motivo).
export const canceledJobs = []

// Correcciones guardadas en la revisión segundo a segundo (la tabla SEGUNDO revisada), por
// id de análisis y número de cilindro: 300 pares [conducta, origen]. origen: maquina |
// confirmada | corregida | dudosa (D-80).
export const revisiones = {}

// Resultados de una tanda (2f), por «clave/gid/letra/día». Tiempos en segundos.
// Cada espécimen trae los segundos de las cuatro conductas (R-12): nado_s, inmovilidad_s,
// escalamiento_s y activa_s (conducta activa: no inmóvil, sin decidir entre nado y
// escalamiento). Un mismo análisis puede mezclar las cuatro.
// nivel (nivelClasif): AGRUPADO si algún segundo quedó como conducta activa; si no, PRECISO.
const EXPERIMENTO_EJEMPLO = { clave: 7, titulo: 'Compuesto CSR-14 · curva de dosis' }

// Línea de tiempo por minuto del primer espécimen: reparte sus segundos de cada conducta
// en tramos alternados de hasta 12 s, minuto por minuto.
function lineaTiempo(totales) {
  const quedan = { ...totales }
  const orden = Object.keys(quedan)
  const minutos = []
  let k = 0
  for (let m = 0; m < 5; m++) {
    const segs = []
    let libre = 60
    while (libre > 0 && orden.some((c) => quedan[c] > 0)) {
      const c = orden[k++ % orden.length]
      const s = Math.min(12, quedan[c], libre)
      if (!s) continue
      quedan[c] -= s
      libre -= s
      if (segs.length && segs[segs.length - 1][0] === c) segs[segs.length - 1][1] += s
      else segs.push([c, s])
    }
    minutos.push(segs)
  }
  return minutos
}

function resultados(grupo, letra, nivel, filas, analizado_en) {
  const especimenes = filas.map((f, i) => ({ numeroCilindro: i + 1, ...f }))
  const { numeroCilindro, ...totales } = especimenes[0]
  const minutos = lineaTiempo(Object.fromEntries(Object.entries(totales).map(([k, v]) => [k.replace('_s', ''), v])))
  return {
    experimento: EXPERIMENTO_EJEMPLO, grupo, letra, dia: 'DAY2', duracion_s: 300, duracion_analizada_s: 300,
    analizado_en, modelo: 'clf-cascada v2.1', nivel, especimenes, linea_tiempo: { numeroCilindro, minutos },
  }
}

const preciso = (nado, inmovilidad, escalamiento) => ({ nado_s: nado, inmovilidad_s: inmovilidad, escalamiento_s: escalamiento, activa_s: 0 })
const mixto = (nado, inmovilidad, escalamiento, activa) => ({ nado_s: nado, inmovilidad_s: inmovilidad, escalamiento_s: escalamiento, activa_s: activa })
const REFERENCIA = { id: 2, nombre: 'Referencia', tipo: 'REFERENCIA', tratamiento: 'Fluoxetina 10 mg/kg' }

export const batchResults = {
  '7/1/A/DAY2': resultados({ id: 1, nombre: 'Control', tipo: 'CONTROL', tratamiento: 'Placebo (solución salina)' }, 'A', 'PRECISO',
    [preciso(127, 145, 28), preciso(113, 162, 25), preciso(99, 171, 30), preciso(82, 196, 22)], '2026-02-23T11:40:00'),
  '7/2/A/DAY2': {
    experimento: EXPERIMENTO_EJEMPLO, grupo: REFERENCIA,
    letra: 'A', dia: 'DAY2', duracion_s: 300, duracion_analizada_s: 300, analizado_en: '2026-02-24T15:02:00', modelo: 'clf-cascada v2.1',
    nivel: 'PRECISO',
    especimenes: [
      { numeroCilindro: 1, nado_s: 171, inmovilidad_s: 88, escalamiento_s: 41, activa_s: 0 },
      { numeroCilindro: 2, nado_s: 158, inmovilidad_s: 104, escalamiento_s: 38, activa_s: 0 },
      { numeroCilindro: 3, nado_s: 182, inmovilidad_s: 76, escalamiento_s: 42, activa_s: 0 },
      { numeroCilindro: 4, nado_s: 149, inmovilidad_s: 118, escalamiento_s: 33, activa_s: 0 },
    ],
    // Desglose por minuto de un espécimen: segmentos [conducta, segundos].
    linea_tiempo: {
      numeroCilindro: 1,
      minutos: [
        [['nado', 38], ['escalamiento', 8], ['nado', 10], ['inmovilidad', 4]],
        [['nado', 30], ['inmovilidad', 12], ['escalamiento', 6], ['nado', 8], ['inmovilidad', 4]],
        [['inmovilidad', 10], ['nado', 22], ['escalamiento', 9], ['inmovilidad', 8], ['nado', 11]],
        [['nado', 16], ['inmovilidad', 20], ['nado', 12], ['escalamiento', 7], ['inmovilidad', 5]],
        [['inmovilidad', 14], ['nado', 24], ['inmovilidad', 11], ['escalamiento', 11]],
      ],
    },
  },
  '7/2/B/DAY2': resultados(REFERENCIA, 'B', 'AGRUPADO',
    [mixto(40, 83, 12, 165), mixto(38, 99, 9, 154), mixto(35, 103, 11, 151), mixto(30, 120, 8, 142)], '2026-02-24T16:10:00'),
  '7/3/A/DAY2': resultados({ id: 3, nombre: 'Experimental A', tipo: 'EXPERIMENTAL', tratamiento: 'Compuesto CSR-14, 5 mg/kg' }, 'A', 'PRECISO',
    [preciso(164, 98, 38), preciso(151, 114, 35), preciso(139, 128, 33), preciso(125, 145, 30)], '2026-02-24T12:25:00'),
}

// La comparación entre grupos (media ± DE de cada conducta, 2f) ya no es fija: la calcula
// services/results.js a partir de las tandas y sus resultados, como lo hará el servidor.

// Cuentas (2g, 2h, 2i, 2j), con la forma de §6.1: idInstitucional (boleta o número de
// empleado) es el id de la cuenta; rol: Investigador | Administrador. cambioRequerido: la
// cuenta entró con contraseña temporal y debe cambiarla (2i).
// password: solo en datos de ejemplo (el servidor guarda únicamente el hash). Las cuentas
// sin password entran con cualquier contraseña no vacía; las creadas en Admin, con su
// contraseña temporal, y después con la que pongan en el primer acceso o al restablecerla.
export const users = [
  { idInstitucional: '2019630871', nombre: 'Mariana', apellidos: 'Rivera Alcántara', correo: 'mrivera@ipn.mx', rol: 'Investigador', activo: true, cambioRequerido: false },
  { idInstitucional: '2008630412', nombre: 'C. S.', apellidos: 'Reyes López', correo: 'creyesl@ipn.mx', rol: 'Administrador', activo: true, cambioRequerido: false },
  { idInstitucional: '2015630322', nombre: 'J.', apellidos: 'Domínguez Vera', correo: 'jdominguez@ipn.mx', rol: 'Investigador', activo: true, cambioRequerido: false },
  { idInstitucional: '2020630417', nombre: 'L.', apellidos: 'Ortega Camacho', correo: 'lortega@ipn.mx', rol: 'Investigador', activo: true, cambioRequerido: false },
]

// Estado del sistema (2g): disco en GB y catálogo de conductas.
export const system = {
  disco: { usado_gb: 412, total_gb: 500, videos_gb: 381, resultados_gb: 2.4, por_liberar_7d_gb: 48 },
  // Las cuatro conductas de la tabla CONDUCTA; ninguna tiene duración mínima (RF-18).
  conductas: ['Nado activo', 'Inmovilidad', 'Escalamiento', 'Conducta activa'],
}

// Notificaciones de la cuenta (2k), con los campos de la guía. tipo: los seis de la base
// (services/notifications.js arma el título y el enlace).
// fechaCreacion es relativa a hoy para que la barra muestre «15:41» y «ayer» como el mockup.
const hace = (dias, hora) => {
  const d = new Date()
  d.setDate(d.getDate() - dias)
  const [h, m] = hora.split(':').map(Number)
  d.setHours(h, m, 0, 0)
  return d.toISOString()
}

export const notifications = [
  { idNotificacion: 1, tipo: 'error del pipeline', mensaje: 'Compuesto CSR-14 · Experimental B · Tanda B · Día 2. No se encontraron los tubos en el video.', idExperimento: 7, fechaCreacion: hace(0, '15:41'), leido: false },
  { idNotificacion: 2, tipo: 'analisis completado', mensaje: 'Compuesto CSR-14 · Referencia · Tanda A · Día 2.', idExperimento: 7, fechaCreacion: hace(0, '15:02'), leido: false },
  { idNotificacion: 4, tipo: 'alerta de disco', mensaje: 'El disco está al 82 %, por encima del 80 %. Al pasar del 90 % se borran primero los videos más antiguos.', idExperimento: null, fechaCreacion: hace(0, '09:12'), leido: false, soloAdmin: true },
  { idNotificacion: 5, tipo: 'video borrado', mensaje: 'Réplica interanalista · control metodológico: los videos se borraron al cumplir 30 días. Resultados y reportes se conservan.', idExperimento: 4, fechaCreacion: hace(1, '03:00'), leido: true },
  { idNotificacion: 6, tipo: 'analisis en pausa', mensaje: 'Compuesto CSR-14 · Control · Tanda B · Día 2. No se ve al espécimen del cilindro 2 (segundo 142). Ajusta el recuadro en 5 minutos o se cancela.', idExperimento: 7, fechaCreacion: hace(0, '15:50'), leido: false },
  { idNotificacion: 3, tipo: 'analisis completado', mensaje: 'Compuesto CSR-14 · Control · Tanda A · Día 2.', idExperimento: 7, fechaCreacion: hace(1, '18:20'), leido: true },
]

// Enlaces de recuperación pendientes (2h): token → { idInstitucional, vence (ms), usado }.
// Un token que no está aquí, como «vencido», responde igual que un enlace vencido.
export const resetTokens = {}

// ── persistencia en el navegador ───────────────────────────────────────────
// Los datos de ejemplo se guardan en localStorage para que sobrevivan a la recarga
// (incluidas las contraseñas de ejemplo; con el backend nada de esto existe).
// Si cambia la forma de los datos, subir VERSION_DATOS descarta lo guardado.
const CLAVE = 'fst.demo'
const VERSION_DATOS = 19
const colecciones = { experiments, experimentDetail, queue, failedJobs, pausedJobs, canceledJobs, revisiones, batchResults, users, system, notifications, resetTokens }
const inicial = structuredClone(colecciones)

// Reemplaza el contenido de cada colección sin cambiar el objeto, porque los
// servicios importan estas mismas referencias.
function restaurar(origen) {
  for (const [k, destino] of Object.entries(colecciones)) {
    const valor = structuredClone(origen[k])
    if (Array.isArray(destino)) destino.splice(0, destino.length, ...valor)
    else {
      for (const key of Object.keys(destino)) delete destino[key]
      Object.assign(destino, valor)
    }
  }
}

try {
  const guardado = JSON.parse(localStorage.getItem(CLAVE))
  if (guardado?.version === VERSION_DATOS) restaurar(guardado.datos)
} catch {
  /* sin almacenamiento o datos dañados: se usan los de ejemplo */
}

export function guardarDatos() {
  try {
    localStorage.setItem(CLAVE, JSON.stringify({ version: VERSION_DATOS, datos: colecciones }))
  } catch {
    /* sin almacenamiento: los cambios duran hasta recargar */
  }
}

// Vuelve a los datos de ejemplo originales y olvida lo guardado.
export function reiniciarDatos() {
  restaurar(inicial)
  try { localStorage.removeItem(CLAVE) } catch { /* sin almacenamiento */ }
}
