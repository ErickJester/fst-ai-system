// Datos de ejemplo de los mockups v2 con la forma que tendrá la API.
// Las pantallas no los importan: los piden a la capa de servicios (../*.js),
// que los entrega mientras VITE_USE_MOCKS no sea 'false'.
//
// estado: se calcula con las tandas (ESTADO_EXPERIMENTO en lib/fst.js): SIN_VIDEOS |
//   ESPERA_DIA2 | QUEUED | RUNNING | PAUSED | DONE | FAILED | CANCELED
// retencion_dias: días que le quedan al video crudo (null si ya se borró).
// con_detalle: solo el experimento de ejemplo tiene detalle en los mockups;
//   con la API real todos lo tendrán.
// tratamientos: los de sus grupos (para el filtro). creador_id: idInstitucional de quien lo creó; solo esa
//   cuenta o un Administrador puede eliminarlo.

export const experiments = [
  { clave: 'EXP-2026-02', titulo: 'Compuesto CSR-14 · curva de dosis', tratamientos: ['Placebo (solución salina)', 'Fluoxetina 10 mg/kg', 'Compuesto CSR-14, 5 mg/kg', 'Compuesto CSR-14, 15 mg/kg'], creador_id: '2019630871', fecha_inicio: '2026-02-18', n_grupos: 4, n_especimenes: 32, videos_dia2_listos: 4, videos_dia2_cargados: 8, videos_dia2_total: 8, estado: 'FAILED', responsable: 'M. Rivera', retencion_dias: 30, videos_borrados: false, con_detalle: true },
  { clave: 'EXP-2026-01', titulo: 'Extracto de Salvia · dosis única', tratamientos: ['Placebo (solución salina)', 'Fluoxetina 10 mg/kg', 'Extracto de Salvia, 20 mg/kg'], creador_id: '2015630322', fecha_inicio: '2026-01-09', n_grupos: 4, n_especimenes: 32, videos_dia2_listos: 8, videos_dia2_cargados: 8, videos_dia2_total: 8, estado: 'DONE', responsable: 'J. Domínguez', retencion_dias: 12, videos_borrados: false },
  { clave: 'EXP-2025-11', titulo: 'Extracto de Valeriana officinalis', tratamientos: ['Placebo (solución salina)', 'Fluoxetina 10 mg/kg', 'Valeriana officinalis, 50 mg/kg'], creador_id: '2019630871', fecha_inicio: '2025-11-14', n_grupos: 5, n_especimenes: 38, videos_dia2_listos: 10, videos_dia2_cargados: 10, videos_dia2_total: 10, estado: 'DONE', responsable: 'M. Rivera', retencion_dias: 4, videos_borrados: false },
  { clave: 'EXP-2025-10', titulo: 'Réplica interanalista · control metodológico', tratamientos: ['Placebo (solución salina)', 'Fluoxetina 10 mg/kg'], creador_id: '2008630412', fecha_inicio: '2025-10-02', n_grupos: 4, n_especimenes: 24, videos_dia2_listos: 3, videos_dia2_cargados: 3, videos_dia2_total: 6, estado: 'DONE', responsable: 'C. Reyes', retencion_dias: null, videos_borrados: true },
  { clave: 'EXP-2025-08', titulo: 'Fluoxetina · calibración del clasificador', tratamientos: ['Placebo (solución salina)', 'Fluoxetina 10 mg/kg', 'Fluoxetina 20 mg/kg'], creador_id: '2008630412', fecha_inicio: '2025-08-19', n_grupos: 4, n_especimenes: 32, videos_dia2_listos: 8, videos_dia2_cargados: 8, videos_dia2_total: 8, estado: 'DONE', responsable: 'C. Reyes', retencion_dias: null, videos_borrados: true },
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
  'EXP-2026-02': {
    clave: 'EXP-2026-02', titulo: 'Compuesto CSR-14 · curva de dosis', fecha_inicio: '2026-02-18', responsable: 'M. Rivera', creador_id: '2019630871',
    grupos: [
      { id: 'G-01', nombre: 'Control', tipo: 'CONTROL', tratamiento: 'Placebo (solución salina)', n_especimenes: 8,
        tandas: [tanda('A', 'DONE', { id_analisis: 401, dia1: { cargado: true } }), tanda('B', 'PAUSED', { id_analisis: 502 })] },
      { id: 'G-02', nombre: 'Referencia', tipo: 'REFERENCIA', tratamiento: 'Fluoxetina 10 mg/kg', n_especimenes: 8,
        tandas: [tanda('A', 'DONE', { id_analisis: 402 }), tanda('B', 'DONE', { id_analisis: 403 })] },
      { id: 'G-03', nombre: 'Experimental A', tipo: 'EXPERIMENTAL', tratamiento: 'Compuesto CSR-14, 5 mg/kg', n_especimenes: 8,
        tandas: [tanda('A', 'DONE', { id_analisis: 404, video_disponible: false }), tanda('B', 'RUNNING', { id_analisis: 501, progreso: { etapa: 'CLASSIFICATION', pct: 100 } })] },
      { id: 'G-04', nombre: 'Experimental B', tipo: 'EXPERIMENTAL', tratamiento: 'Compuesto CSR-14, 15 mg/kg', n_especimenes: 8,
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
  { job_id: 501, posicion: 1, clave: 'EXP-2026-02', gid: 'G-03', experimento: 'Compuesto CSR-14', grupo: 'Experimental A', tanda: 'B', dia: 'DAY2', n_especimenes: 4, subido_por: '2019630871', status: 'RUNNING', stage: 'CLASSIFICATION', progress_pct: 100, error: null },
  { job_id: 503, posicion: 2, clave: 'EXP-2026-02', gid: 'G-04', experimento: 'Compuesto CSR-14', grupo: 'Experimental B', tanda: 'A', dia: 'DAY2', n_especimenes: 4, subido_por: '2019630871', status: 'QUEUED', stage: null, progress_pct: 0, error: null },
]

// Trabajos que fallaron recientemente: salen de la cola y quedan con su causa.
export const failedJobs = [
  { job_id: 498, clave: 'EXP-2026-02', gid: 'G-04', experimento: 'Compuesto CSR-14', grupo: 'Experimental B', tanda: 'B', dia: 'DAY2', n_especimenes: 4, subido_por: '2019630871', status: 'FAILED', stage: 'ROI_DETECTION', progress_pct: 67 },
]

// Análisis en pausa (R-15): salen de la cola mientras esperan el ajuste del recuadro. pausa:
// { cilindro, desde_segundo, hora_ms, ancho, alto, recuadros, ajuste }, como pausa.json.
// La pausa la decide el worker; el de ejemplo ya está en pausa. hora_ms null: sin backend,
// la pausa empieza la primera vez que se consulta (services/queue.js), para ver el plazo.
export const pausedJobs = [
  { job_id: 502, clave: 'EXP-2026-02', gid: 'G-01', experimento: 'Compuesto CSR-14', grupo: 'Control', tanda: 'B', dia: 'DAY2', n_especimenes: 4, subido_por: '2019630871', status: 'PAUSED', stage: 'CLASSIFICATION', progress_pct: 100,
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
const EXPERIMENTO_EJEMPLO = { clave: 'EXP-2026-02', titulo: 'Compuesto CSR-14 · curva de dosis' }

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
const REFERENCIA = { id: 'G-02', nombre: 'Referencia', tipo: 'REFERENCIA', tratamiento: 'Fluoxetina 10 mg/kg' }

export const batchResults = {
  'EXP-2026-02/G-01/A/DAY2': resultados({ id: 'G-01', nombre: 'Control', tipo: 'CONTROL', tratamiento: 'Placebo (solución salina)' }, 'A', 'PRECISO',
    [preciso(127, 145, 28), preciso(113, 162, 25), preciso(99, 171, 30), preciso(82, 196, 22)], '2026-02-23T11:40:00'),
  'EXP-2026-02/G-02/A/DAY2': {
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
  'EXP-2026-02/G-02/B/DAY2': resultados(REFERENCIA, 'B', 'AGRUPADO',
    [mixto(40, 83, 12, 165), mixto(38, 99, 9, 154), mixto(35, 103, 11, 151), mixto(30, 120, 8, 142)], '2026-02-24T16:10:00'),
  'EXP-2026-02/G-03/A/DAY2': resultados({ id: 'G-03', nombre: 'Experimental A', tipo: 'EXPERIMENTAL', tratamiento: 'Compuesto CSR-14, 5 mg/kg' }, 'A', 'PRECISO',
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

// Notificaciones de la cuenta (2k). tipo: los seis de services/notifications.js.
// creada es relativa a hoy para que la barra muestre «15:41» y «ayer» como el mockup.
const hace = (dias, hora) => {
  const d = new Date()
  d.setDate(d.getDate() - dias)
  const [h, m] = hora.split(':').map(Number)
  d.setHours(h, m, 0, 0)
  return d.toISOString()
}

export const notifications = [
  { id: 1, tipo: 'ANALYSIS_FAILED', titulo: 'Error en el análisis', texto: 'Compuesto CSR-14 · Experimental B · Tanda B · Día 2. No se encontraron los tubos en el video.', enlace: '/analisis', creada: hace(0, '15:41'), is_read: false },
  { id: 2, tipo: 'ANALYSIS_DONE', titulo: 'Análisis completado', texto: 'Compuesto CSR-14 · Referencia · Tanda A · Día 2.', enlace: '/experimentos/EXP-2026-02/resultados?grupo=G-02&tanda=A', creada: hace(0, '15:02'), is_read: false },
  { id: 4, tipo: 'DISK_ALERT', titulo: 'Alerta de disco', texto: 'El disco está al 82 %, por encima del 80 %. Al pasar del 90 % se borran primero los videos más antiguos.', enlace: '/admin', creada: hace(0, '09:12'), is_read: false, solo_admin: true },
  { id: 5, tipo: 'VIDEO_DELETED', titulo: 'Videos borrados', texto: 'Réplica interanalista · control metodológico: los videos se borraron al cumplir 30 días. Resultados y reportes se conservan.', enlace: '/experimentos', creada: hace(1, '03:00'), is_read: true },
  { id: 6, tipo: 'ANALYSIS_PAUSED', titulo: 'Análisis en pausa', texto: 'Compuesto CSR-14 · Control · Tanda B · Día 2. No se ve al espécimen del cilindro 2 (segundo 142). Ajusta el recuadro en 5 minutos o se cancela.', enlace: '/experimentos/EXP-2026-02/analisis/502/pausa', creada: hace(0, '15:50'), is_read: false },
  { id: 3, tipo: 'ANALYSIS_DONE', titulo: 'Análisis completado', texto: 'Compuesto CSR-14 · Control · Tanda A · Día 2.', enlace: '/experimentos/EXP-2026-02/resultados', creada: hace(1, '18:20'), is_read: true },
]

// Enlaces de recuperación pendientes (2h): token → { idInstitucional, vence (ms), usado }.
// Un token que no está aquí, como «vencido», responde igual que un enlace vencido.
export const resetTokens = {}

// ── persistencia en el navegador ───────────────────────────────────────────
// Los datos de ejemplo se guardan en localStorage para que sobrevivan a la recarga
// (incluidas las contraseñas de ejemplo; con el backend nada de esto existe).
// Si cambia la forma de los datos, subir VERSION_DATOS descarta lo guardado.
const CLAVE = 'fst.demo'
const VERSION_DATOS = 15
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
