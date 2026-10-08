// Datos de ejemplo de los mockups v2 con la forma que tendrá la API.
// Las pantallas no los importan: los piden a la capa de servicios (../*.js),
// que los entrega mientras VITE_USE_MOCKS no sea 'false'.
//
// estado: EN_ANALISIS | CARGA_INCOMPLETA | CONCLUIDO
// retencion_dias: días que le quedan al video crudo (null si ya se borró).
// con_detalle: solo el experimento de ejemplo tiene detalle en los mockups;
//   con la API real todos lo tendrán.

export const experiments = [
  { clave: 'EXP-2026-02', titulo: 'Compuesto CSR-14 · curva de dosis', tratamientos: 'Compuesto CSR-14 Fluoxetina Placebo', fecha_inicio: '2026-02-18', n_grupos: 4, n_especimenes: 32, videos_dia2_listos: 4, videos_dia2_cargados: 8, videos_dia2_total: 8, estado: 'EN_ANALISIS', responsable: 'M. Rivera', retencion_dias: 30, videos_borrados: false, con_detalle: true },
  { clave: 'EXP-2026-01', titulo: 'Extracto de Salvia · dosis única', tratamientos: 'Salvia', fecha_inicio: '2026-01-09', n_grupos: 4, n_especimenes: 32, videos_dia2_listos: 8, videos_dia2_cargados: 8, videos_dia2_total: 8, estado: 'CONCLUIDO', responsable: 'J. Domínguez', retencion_dias: 12, videos_borrados: false },
  { clave: 'EXP-2025-11', titulo: 'Extracto de Valeriana officinalis', tratamientos: 'Valeriana officinalis', fecha_inicio: '2025-11-14', n_grupos: 5, n_especimenes: 38, videos_dia2_listos: 10, videos_dia2_cargados: 10, videos_dia2_total: 10, estado: 'CONCLUIDO', responsable: 'M. Rivera', retencion_dias: 4, videos_borrados: false },
  { clave: 'EXP-2025-10', titulo: 'Réplica interanalista · control metodológico', tratamientos: '', fecha_inicio: '2025-10-02', n_grupos: 4, n_especimenes: 24, videos_dia2_listos: 3, videos_dia2_cargados: 3, videos_dia2_total: 6, estado: 'CARGA_INCOMPLETA', responsable: 'C. Reyes', retencion_dias: null, videos_borrados: true },
  { clave: 'EXP-2025-08', titulo: 'Fluoxetina · calibración del clasificador', tratamientos: 'Fluoxetina', fecha_inicio: '2025-08-19', n_grupos: 4, n_especimenes: 32, videos_dia2_listos: 8, videos_dia2_cargados: 8, videos_dia2_total: 8, estado: 'CONCLUIDO', responsable: 'C. Reyes', retencion_dias: null, videos_borrados: true },
]

// Detalle (2c, 2d). Solo el experimento de ejemplo lo tiene.
// tipo: CONTROL | REFERENCIA | EXPERIMENTAL
// estado de tanda = estado del análisis de Día 2: QUEUED | RUNNING | DONE | FAILED,
//   como JobStatus en backend/app/models.py, o SIN_VIDEO si aún no se sube el Día 2.
// Cada tanda ocupa los cilindros P1…Pn; desde es la posición de su primer cilindro en el grupo.
// dia1: el video de Día 1 es opcional (null si no se cargó).
// progreso: etapa (PipelineStage) y avance mientras corre; error: si falló.
const tanda = (letra, estado, extra = {}) => ({
  letra, desde: (letra.charCodeAt(0) - 65) * 4 + 1, n_cilindros: 4,
  fecha_dia1: '2026-02-18', fecha_dia2: '2026-02-19',
  estado, dia1: null, progreso: null, error: null,
  analisis_fecha: estado === 'DONE' ? '2026-02-24' : null,
  ...extra,
})

const ERROR_DET = { codigo: 'E-DET-070', mensaje: 'Confianza de detección 0.54, menor a 0.70: no se detectaron los cuatro cilindros.' }

export const experimentDetail = {
  'EXP-2026-02': {
    clave: 'EXP-2026-02', titulo: 'Compuesto CSR-14 · curva de dosis', fecha_inicio: '2026-02-18', responsable: 'M. Rivera',
    grupos: [
      { id: 'G-01', nombre: 'Control', tipo: 'CONTROL', tratamiento: 'Placebo (solución salina)', n_especimenes: 8,
        tandas: [tanda('A', 'DONE', { dia1: { estado: 'DONE' } }), tanda('B', 'QUEUED')] },
      { id: 'G-02', nombre: 'Referencia', tipo: 'REFERENCIA', tratamiento: 'Fluoxetina 10 mg/kg', n_especimenes: 8,
        tandas: [tanda('A', 'DONE'), tanda('B', 'DONE')] },
      { id: 'G-03', nombre: 'Experimental A', tipo: 'EXPERIMENTAL', tratamiento: 'Compuesto CSR-14, 5 mg/kg', n_especimenes: 8,
        tandas: [tanda('A', 'DONE'), tanda('B', 'RUNNING', { progreso: { etapa: 'TRACKING', pct: 75 } })] },
      { id: 'G-04', nombre: 'Experimental B', tipo: 'EXPERIMENTAL', tratamiento: 'Compuesto CSR-14, 15 mg/kg', n_especimenes: 8,
        tandas: [tanda('A', 'QUEUED'), tanda('B', 'FAILED', { error: ERROR_DET })] },
    ],
  },
}

// Cola de análisis (2e). Un trabajo a la vez, en orden de llegada.
// status: JobStatus; stage: PipelineStage en la que va (o en la que se detuvo);
// confianza: de la detección de cilindros (mínimo 0.70).
export const queue = [
  { job_id: 501, posicion: 1, clave: 'EXP-2026-02', gid: 'G-03', experimento: 'Compuesto CSR-14', grupo: 'Experimental A', tanda: 'B', dia: 'DAY2', n_especimenes: 4, status: 'RUNNING', stage: 'TRACKING', progress_pct: 75, confianza: 0.86, error: null },
  { job_id: 502, posicion: 2, clave: 'EXP-2026-02', gid: 'G-01', experimento: 'Compuesto CSR-14', grupo: 'Control', tanda: 'B', dia: 'DAY2', n_especimenes: 4, status: 'QUEUED', stage: null, progress_pct: 0, confianza: null, error: null },
  { job_id: 503, posicion: 3, clave: 'EXP-2026-02', gid: 'G-04', experimento: 'Compuesto CSR-14', grupo: 'Experimental B', tanda: 'A', dia: 'DAY2', n_especimenes: 4, status: 'QUEUED', stage: null, progress_pct: 0, confianza: null, error: null },
]

// Trabajos que fallaron recientemente: salen de la cola y quedan con su causa.
export const failedJobs = [
  { job_id: 498, experimento: 'Compuesto CSR-14', grupo: 'Experimental B', tanda: 'B', dia: 'DAY2', n_especimenes: 4, status: 'FAILED', stage: 'ROI_DETECTION', progress_pct: 50, confianza: 0.54, error: ERROR_DET },
]

// Resultados de una tanda (2f), por «clave/gid/letra/día». Tiempos en segundos.
// nivel: PRECISO (nado activo, inmovilidad, escalamiento) | AGRUPADO (nado activo y
// escalamiento juntos como «conducta activa»: activa_s en lugar de nado_s y escalamiento_s).
const EXPERIMENTO_EJEMPLO = { clave: 'EXP-2026-02', titulo: 'Compuesto CSR-14 · curva de dosis' }

// Línea de tiempo por minuto del primer cilindro: reparte sus segundos de cada conducta
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
  const cilindros = filas.map((f, i) => ({ cilindro: 'P' + (i + 1), ...f }))
  const { cilindro, ...totales } = cilindros[0]
  const minutos = lineaTiempo(Object.fromEntries(Object.entries(totales).map(([k, v]) => [k.replace('_s', ''), v])))
  return {
    experimento: EXPERIMENTO_EJEMPLO, grupo, letra, dia: 'DAY2', duracion_s: 300, duracion_analizada_s: 300,
    analizado_en, modelo: 'clf-cascada v2.1', nivel, confianza: 0.83, cilindros, linea_tiempo: { cilindro, minutos },
  }
}

const preciso = (nado, inmovilidad, escalamiento) => ({ nado_s: nado, inmovilidad_s: inmovilidad, escalamiento_s: escalamiento })
const agrupado = (activa, inmovilidad) => ({ activa_s: activa, inmovilidad_s: inmovilidad })
const REFERENCIA = { id: 'G-02', nombre: 'Referencia', tipo: 'REFERENCIA', tratamiento: 'Fluoxetina 10 mg/kg' }

export const batchResults = {
  'EXP-2026-02/G-01/A/DAY2': resultados({ id: 'G-01', nombre: 'Control', tipo: 'CONTROL', tratamiento: 'Placebo (solución salina)' }, 'A', 'PRECISO',
    [preciso(127, 145, 28), preciso(113, 162, 25), preciso(99, 171, 30), preciso(82, 196, 22)], '2026-02-23T11:40:00'),
  'EXP-2026-02/G-02/A/DAY2': {
    experimento: EXPERIMENTO_EJEMPLO, grupo: REFERENCIA,
    letra: 'A', dia: 'DAY2', duracion_s: 300, duracion_analizada_s: 300, analizado_en: '2026-02-24T15:02:00', modelo: 'clf-cascada v2.1',
    nivel: 'PRECISO', confianza: 0.83,
    cilindros: [
      { cilindro: 'P1', nado_s: 171, inmovilidad_s: 88, escalamiento_s: 41 },
      { cilindro: 'P2', nado_s: 158, inmovilidad_s: 104, escalamiento_s: 38 },
      { cilindro: 'P3', nado_s: 182, inmovilidad_s: 76, escalamiento_s: 42 },
      { cilindro: 'P4', nado_s: 149, inmovilidad_s: 118, escalamiento_s: 33 },
    ],
    // Desglose por minuto de un cilindro: segmentos [conducta, segundos].
    linea_tiempo: {
      cilindro: 'P1',
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
    [agrupado(217, 83), agrupado(201, 99), agrupado(197, 103), agrupado(180, 120)], '2026-02-24T16:10:00'),
  'EXP-2026-02/G-03/A/DAY2': resultados({ id: 'G-03', nombre: 'Experimental A', tipo: 'EXPERIMENTAL', tratamiento: 'Compuesto CSR-14, 5 mg/kg' }, 'A', 'PRECISO',
    [preciso(164, 98, 38), preciso(151, 114, 35), preciso(139, 128, 33), preciso(125, 145, 30)], '2026-02-24T12:25:00'),
}

// La comparación entre grupos (inmovilidad media ± DE, 2f) ya no es fija: la calcula
// services/results.js a partir de las tandas y sus resultados, como lo hará el servidor.

// Cuentas (2g, 2h, 2i, 2j). role: INVESTIGADOR | ADMIN, como Role en backend/app/models.py.
// must_change_password: la cuenta entró con contraseña temporal y debe cambiarla (2i).
// password: solo en datos de ejemplo (el servidor guarda únicamente el hash). Las cuentas
// sin password entran con cualquier contraseña no vacía; las creadas en Admin, con su
// contraseña temporal, y después con la que pongan en el primer acceso o al restablecerla.
export const users = [
  { id: 1, nombre: 'Mariana', apellidos: 'Rivera Alcántara', email: 'mrivera@ipn.mx', identificador: '2019630871', role: 'INVESTIGADOR', is_active: true, must_change_password: false },
  { id: 2, nombre: 'C. S.', apellidos: 'Reyes López', email: 'creyesl@ipn.mx', identificador: '2008630412', role: 'ADMIN', is_active: true, must_change_password: false },
  { id: 3, nombre: 'J.', apellidos: 'Domínguez Vera', email: 'jdominguez@ipn.mx', identificador: '2015630322', role: 'INVESTIGADOR', is_active: true, must_change_password: false },
  { id: 4, nombre: 'L.', apellidos: 'Ortega Camacho', email: 'lortega@ipn.mx', identificador: '2020630417', role: 'INVESTIGADOR', is_active: true, must_change_password: false },
]

// Estado del sistema (2g): disco en GB, catálogo de conductas y modelo en uso.
export const system = {
  disco: { usado_gb: 412, total_gb: 500, videos_gb: 381, resultados_gb: 2.4, por_liberar_7d_gb: 48 },
  conductas: [
    { nombre: 'Nado activo', minimo_s: 3 },
    { nombre: 'Inmovilidad', minimo_s: 3 },
    { nombre: 'Escalamiento', minimo_s: 3 },
  ],
  modelo: 'clf-cascada v2.1',
}

// Notificaciones de la cuenta (2k). tipo: ANALYSIS_DONE | ANALYSIS_FAILED.
// creada es relativa a hoy para que la barra muestre «15:41» y «ayer» como el mockup.
const hace = (dias, hora) => {
  const d = new Date()
  d.setDate(d.getDate() - dias)
  const [h, m] = hora.split(':').map(Number)
  d.setHours(h, m, 0, 0)
  return d.toISOString()
}

export const notifications = [
  { id: 1, tipo: 'ANALYSIS_FAILED', titulo: 'Error en el análisis', texto: 'Compuesto CSR-14 · Experimental B · Tanda B · Día 2. Confianza de detección 0.54, menor a 0.70.', enlace: '/analisis', creada: hace(0, '15:41'), is_read: false },
  { id: 2, tipo: 'ANALYSIS_DONE', titulo: 'Análisis completado', texto: 'Compuesto CSR-14 · Referencia · Tanda A · Día 2.', enlace: '/experimentos/EXP-2026-02/resultados?grupo=G-02&tanda=A', creada: hace(0, '15:02'), is_read: false },
  { id: 3, tipo: 'ANALYSIS_DONE', titulo: 'Análisis completado', texto: 'Compuesto CSR-14 · Control · Tanda A · Día 2.', enlace: '/experimentos/EXP-2026-02/resultados', creada: hace(1, '18:20'), is_read: true },
]

// Enlaces de recuperación pendientes (2h): token → { userId, vence (ms), usado }.
// Un token que no está aquí, como «vencido», responde igual que un enlace vencido.
export const resetTokens = {}

// Simulación de la cola (mocks/simulador.js): hasta dónde se avanzó y cuántos trabajos terminaron.
export const simulacion = { ultimo: null, terminados: 0 }

// ── persistencia en el navegador ───────────────────────────────────────────
// Los datos de ejemplo se guardan en localStorage para que sobrevivan a la recarga
// (incluidas las contraseñas de ejemplo; con el backend nada de esto existe).
// Si cambia la forma de los datos, subir VERSION_DATOS descarta lo guardado.
const CLAVE = 'fst.demo'
const VERSION_DATOS = 4
const colecciones = { experiments, experimentDetail, queue, failedJobs, batchResults, users, system, notifications, resetTokens, simulacion }
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
