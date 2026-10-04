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
// Cada tanda ocupa los cilindros P1…Pn con las ratas desde…desde+n−1.
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
  { job_id: 501, posicion: 1, experimento: 'Compuesto CSR-14', grupo: 'Experimental A', tanda: 'B', dia: 'DAY2', n_especimenes: 4, status: 'RUNNING', stage: 'TRACKING', progress_pct: 75, confianza: 0.86, error: null },
  { job_id: 502, posicion: 2, experimento: 'Compuesto CSR-14', grupo: 'Control', tanda: 'B', dia: 'DAY2', n_especimenes: 4, status: 'QUEUED', stage: null, progress_pct: 0, confianza: null, error: null },
  { job_id: 503, posicion: 3, experimento: 'Compuesto CSR-14', grupo: 'Experimental B', tanda: 'A', dia: 'DAY2', n_especimenes: 4, status: 'QUEUED', stage: null, progress_pct: 0, confianza: null, error: null },
]

// Trabajos que fallaron recientemente: salen de la cola y quedan con su causa.
export const failedJobs = [
  { job_id: 498, experimento: 'Compuesto CSR-14', grupo: 'Experimental B', tanda: 'B', dia: 'DAY2', n_especimenes: 4, status: 'FAILED', stage: 'ROI_DETECTION', progress_pct: 50, confianza: 0.54, error: ERROR_DET },
]

// Resultados de una tanda (2f). Solo la Tanda A del grupo Referencia, Día 2, tiene.
// Tiempos en segundos. nivel: PRECISO (3 conductas) | AGRUPADO (nado activo y
// escalamiento juntos como «conducta activa»).
export const batchResults = {
  'EXP-2026-02/G-02/A/DAY2': {
    experimento: { clave: 'EXP-2026-02', titulo: 'Compuesto CSR-14 · curva de dosis' },
    grupo: { id: 'G-02', nombre: 'Referencia', tipo: 'REFERENCIA', tratamiento: 'Fluoxetina 10 mg/kg' },
    letra: 'A', dia: 'DAY2', duracion_s: 300, duracion_analizada_s: 300, analizado_en: '2026-02-24T15:02:00', modelo: 'clf-cascada v2.1',
    nivel: 'PRECISO', confianza: 0.83,
    especimenes: [
      { rata: 1, cilindro: 'P1', nado_s: 171, inmovilidad_s: 88, escalamiento_s: 41 },
      { rata: 2, cilindro: 'P2', nado_s: 158, inmovilidad_s: 104, escalamiento_s: 38 },
      { rata: 3, cilindro: 'P3', nado_s: 182, inmovilidad_s: 76, escalamiento_s: 42 },
      { rata: 4, cilindro: 'P4', nado_s: 149, inmovilidad_s: 118, escalamiento_s: 33 },
    ],
    // Desglose por minuto de un espécimen: segmentos [conducta, segundos].
    linea_tiempo: {
      rata: 1, cilindro: 'P1',
      minutos: [
        [['nado', 38], ['escalamiento', 8], ['nado', 10], ['inmovilidad', 4]],
        [['nado', 30], ['inmovilidad', 12], ['escalamiento', 6], ['nado', 8], ['inmovilidad', 4]],
        [['inmovilidad', 10], ['nado', 22], ['escalamiento', 9], ['inmovilidad', 8], ['nado', 11]],
        [['nado', 16], ['inmovilidad', 20], ['nado', 12], ['escalamiento', 7], ['inmovilidad', 5]],
        [['inmovilidad', 14], ['nado', 24], ['inmovilidad', 11], ['escalamiento', 11]],
      ],
    },
  },
}

// Inmovilidad media ± DE por grupo, Día 2 (2f). Solo entran los especímenes con
// Día 2 analizado; pendientes dice qué tandas faltan y por qué (JobStatus, o null si no se ha subido).
// Si un grupo mezcla niveles de clasificación, viene por_nivel en vez de una sola media.
export const groupComparison = {
  'EXP-2026-02': [
    { grupo: 'Control · placebo', tipo: 'CONTROL', media_s: 168.4, de_s: 21.3, n: 4, n_total: 8,
      pendientes: [{ tanda: 'B', n: 4, estado: 'QUEUED' }] },
    { grupo: 'Referencia · fluoxetina', tipo: 'REFERENCIA', n: 8, n_total: 8, pendientes: [],
      por_nivel: [
        { tanda: 'A', nivel: 'PRECISO', n: 4, media_s: 96.5, de_s: 18.4 },
        { tanda: 'B', nivel: 'AGRUPADO', n: 4, media_s: 101.3, de_s: 15.2 },
      ] },
    { grupo: 'Experimental A · CSR-14, 5 mg/kg', tipo: 'EXPERIMENTAL', media_s: 121.2, de_s: 19.4, n: 4, n_total: 8,
      pendientes: [{ tanda: 'B', n: 4, estado: 'RUNNING' }] },
    { grupo: 'Experimental B · CSR-14, 15 mg/kg', tipo: 'EXPERIMENTAL', media_s: null, de_s: null, n: 0, n_total: 8,
      pendientes: [{ tanda: 'A', n: 4, estado: 'QUEUED' }, { tanda: 'B', n: 4, estado: 'FAILED' }] },
  ],
}

// Cuentas (2g, 2h, 2i, 2j). role: INVESTIGADOR | ADMIN, como Role en backend/app/models.py.
// must_change_password: la cuenta entró con contraseña temporal y debe cambiarla (2i).
// Sin backend no hay contraseñas reales: cualquier contraseña no vacía entra.
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
