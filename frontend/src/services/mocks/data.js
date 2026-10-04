// Datos de ejemplo del mockup v2 (mockup/v2/), con la forma que tendrá la API.
// Los valores de estado usan los mismos enums que backend/app/models.py:
// JobStatus (QUEUED, RUNNING, DONE, FAILED), Day (DAY1, DAY2), Role (INVESTIGADOR, ADMIN),
// PipelineStage (PREPROCESSING, ROI_DETECTION, TRACKING, CLASSIFICATION).
//
// Credenciales de prueba (solo con VITE_USE_MOCKS): mrivera@ipn.mx o creyesl@ipn.mx,
// contraseña «password».

export const MOCK_PASSWORD = 'password'

export const users = [
  { id: 1, nombre: 'Mariana', apellidos: 'Rivera Alcántara', email: 'mrivera@ipn.mx', identificador: '2019630871', role: 'INVESTIGADOR', is_active: true, must_change_password: false },
  { id: 2, nombre: 'C. S.', apellidos: 'Reyes López', email: 'creyesl@ipn.mx', identificador: '2008120045', role: 'ADMIN', is_active: true, must_change_password: false },
  { id: 3, nombre: 'J.', apellidos: 'Domínguez Vera', email: 'jdominguez@ipn.mx', identificador: '2015630322', role: 'INVESTIGADOR', is_active: true, must_change_password: false },
  { id: 4, nombre: 'L.', apellidos: 'Ortega Camacho', email: 'lortega@ipn.mx', identificador: '2020630417', role: 'INVESTIGADOR', is_active: true, must_change_password: false },
]

export const experiments = [
  { id: 202602, nombre: 'Compuesto CSR-14 · curva de dosis', tratamientos: 'Compuesto CSR-14 · Fluoxetina · Placebo', fecha_inicio: '2026-02-18', n_grupos: 4, n_especimenes: 32, videos_dia2_listos: 4, videos_dia2_total: 8, videos_dia2_cargados: 8, estado: 'EN_ANALISIS', responsable: 'M. Rivera', retencion_dias: 30, videos_borrados: false },
  { id: 202601, nombre: 'Extracto de Salvia · dosis única', tratamientos: 'Salvia', fecha_inicio: '2026-01-09', n_grupos: 4, n_especimenes: 32, videos_dia2_listos: 8, videos_dia2_total: 8, videos_dia2_cargados: 8, estado: 'CONCLUIDO', responsable: 'J. Domínguez', retencion_dias: 12, videos_borrados: false },
  { id: 202511, nombre: 'Extracto de Valeriana officinalis', tratamientos: 'Valeriana officinalis', fecha_inicio: '2025-11-14', n_grupos: 5, n_especimenes: 38, videos_dia2_listos: 10, videos_dia2_total: 10, videos_dia2_cargados: 10, estado: 'CONCLUIDO', responsable: 'M. Rivera', retencion_dias: 4, videos_borrados: false },
  { id: 202510, nombre: 'Réplica interanalista · control metodológico', tratamientos: '', fecha_inicio: '2025-10-02', n_grupos: 4, n_especimenes: 24, videos_dia2_listos: 3, videos_dia2_total: 6, videos_dia2_cargados: 3, estado: 'CARGA_INCOMPLETA', responsable: 'C. Reyes', retencion_dias: null, videos_borrados: true },
  { id: 202508, nombre: 'Fluoxetina · calibración del clasificador', tratamientos: 'Fluoxetina', fecha_inicio: '2025-08-19', n_grupos: 4, n_especimenes: 32, videos_dia2_listos: 8, videos_dia2_total: 8, videos_dia2_cargados: 8, estado: 'CONCLUIDO', responsable: 'C. Reyes', retencion_dias: null, videos_borrados: true },
]

// Detalle disponible solo para el experimento de ejemplo del v2.
const ratas = (desde, n) => Array.from({ length: n }, (_, i) => ({ rata: desde + i, cilindro: 'P' + (i + 1) }))
const tanda = (id, letra, desde, estado, extra = {}) => ({
  id, letra, n_cilindros: 4, fechas: '18–19 feb 2026', estado,
  especimenes: ratas(desde, 4),
  dia1: null,
  dia2: { estado },
  analisis_fecha: estado === 'DONE' ? '2026-02-24' : null,
  ...extra,
})

export const groups = [
  { id: 1, experimento_id: 202602, nombre: 'Control', tipo: 'CONTROL', tratamiento: 'Placebo (solución salina)', n_especimenes: 8,
    tandas: [tanda(11, 'A', 1, 'DONE', { dia1: { estado: 'DONE' } }), tanda(12, 'B', 5, 'QUEUED')] },
  { id: 2, experimento_id: 202602, nombre: 'Referencia', tipo: 'REFERENCIA', tratamiento: 'Fluoxetina 10 mg/kg', n_especimenes: 8,
    tandas: [tanda(21, 'A', 1, 'DONE'), tanda(22, 'B', 5, 'DONE')] },
  { id: 3, experimento_id: 202602, nombre: 'Experimental A', tipo: 'TRATAMIENTO', tratamiento: 'Compuesto CSR-14, 5 mg/kg', n_especimenes: 8,
    tandas: [tanda(31, 'A', 1, 'DONE'), tanda(32, 'B', 5, 'RUNNING')] },
  { id: 4, experimento_id: 202602, nombre: 'Experimental B', tipo: 'TRATAMIENTO', tratamiento: 'Compuesto CSR-14, 15 mg/kg', n_especimenes: 8,
    tandas: [tanda(41, 'A', 1, 'QUEUED'), tanda(42, 'B', 5, 'FAILED')] },
]

export const experimentDetail = {
  202602: { id: 202602, nombre: 'Compuesto CSR-14 · curva de dosis', fecha_inicio: '2026-02-18', responsable: 'M. Rivera' },
}

export const STAGES = ['PREPROCESSING', 'ROI_DETECTION', 'TRACKING', 'CLASSIFICATION']

export const queue = [
  { job_id: 501, posicion: 1, experimento: 'Compuesto CSR-14', grupo: 'Experimental A', tanda: 'B', dia: 'DAY2', n_especimenes: 4, status: 'RUNNING', stage: 'TRACKING', progress_pct: 75, confianza: 0.86, error: null },
  { job_id: 502, posicion: 2, experimento: 'Compuesto CSR-14', grupo: 'Control', tanda: 'B', dia: 'DAY2', n_especimenes: 4, status: 'QUEUED', stage: null, progress_pct: 0, confianza: null, error: null },
  { job_id: 503, posicion: 3, experimento: 'Compuesto CSR-14', grupo: 'Experimental B', tanda: 'A', dia: 'DAY2', n_especimenes: 4, status: 'QUEUED', stage: null, progress_pct: 0, confianza: null, error: null },
]

export const failedJobs = [
  { job_id: 498, experimento: 'Compuesto CSR-14', grupo: 'Experimental B', tanda: 'B', dia: 'DAY2', n_especimenes: 4, status: 'FAILED', stage: 'ROI_DETECTION', progress_pct: 50, confianza: 0.54,
    error: { codigo: 'E-DET-070', mensaje: 'Confianza de detección 0.54, menor a 0.70: no se detectaron los cuatro cilindros.' } },
]

// Resultados de la Tanda A del grupo Referencia, Día 2 (300 s).
export const batchResults = {
  21: {
    tanda_id: 21, experimento: { id: 202602, nombre: 'Compuesto CSR-14 · curva de dosis' }, grupo: { id: 2, nombre: 'Referencia', tratamiento: 'Fluoxetina 10 mg/kg' },
    letra: 'A', dia: 'DAY2', duracion_s: 300, duracion_analizada_s: 300, analizado_en: '2026-02-24T15:02:00', modelo: 'clf-cascada v2.1',
    nivel: 'PRECISO', confianza: 0.83,
    especimenes: [
      { rata: 1, cilindro: 'P1', nado_s: 171, inmovilidad_s: 88, escalamiento_s: 41 },
      { rata: 2, cilindro: 'P2', nado_s: 158, inmovilidad_s: 104, escalamiento_s: 38 },
      { rata: 3, cilindro: 'P3', nado_s: 182, inmovilidad_s: 76, escalamiento_s: 42 },
      { rata: 4, cilindro: 'P4', nado_s: 149, inmovilidad_s: 118, escalamiento_s: 33 },
    ],
    // Desglose por minuto de la Rata 1: segmentos [conducta, segundos].
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

export const groupComparison = {
  202602: [
    { grupo: 'Control · placebo', media_s: 168.4, de_s: 21.3, n: 4, n_total: 8, pendientes: '4 especímenes pendientes (Tanda B en cola)' },
    { grupo: 'Referencia · fluoxetina', n: 8, n_total: 8, por_nivel: [
      { etiqueta: 'preciso · Tanda A · n = 4', nivel: 'PRECISO', media_s: 96.5, de_s: 18.4 },
      { etiqueta: 'agrupado · Tanda B · n = 4', nivel: 'AGRUPADO', media_s: 101.3, de_s: 15.2 },
    ] },
    { grupo: 'Experimental A · CSR-14, 5 mg/kg', media_s: 121.2, de_s: 19.4, n: 4, n_total: 8, pendientes: '4 especímenes pendientes (Tanda B procesando)' },
    { grupo: 'Experimental B · CSR-14, 15 mg/kg', media_s: null, de_s: null, n: 0, n_total: 8, pendientes: '8 de 8 especímenes pendientes (Tanda A en cola, Tanda B con error)' },
  ],
}

export const notifications = [
  { id: 1, tipo: 'ANALYSIS_FAILED', titulo: 'Error en el análisis', texto: 'Compuesto CSR-14 · Experimental B · Tanda B · Día 2. Confianza de detección 0.54, menor a 0.70.', enlace: '/analisis', creada: '15:41', is_read: false },
  { id: 2, tipo: 'ANALYSIS_DONE', titulo: 'Análisis completado', texto: 'Compuesto CSR-14 · Referencia · Tanda A · Día 2.', enlace: '/experimentos/202602/grupos/2/tandas/21/resultados', creada: '15:02', is_read: false },
  { id: 3, tipo: 'ANALYSIS_DONE', titulo: 'Análisis completado', texto: 'Compuesto CSR-14 · Control · Tanda A · Día 2.', enlace: '/experimentos/202602/grupos/1/tandas/11/resultados', creada: 'ayer', is_read: true },
]

export const system = {
  disco: { usado_gb: 412, total_gb: 500, uso_pct: 82, videos_gb: 381, resultados_gb: 2.4, por_liberar_7d_gb: 48 },
  conductas: [
    { nombre: 'Nado activo', minimo_s: 3 },
    { nombre: 'Inmovilidad', minimo_s: 3 },
    { nombre: 'Escalamiento', minimo_s: 3 },
  ],
  modelo: 'clf-cascada v2.1',
}
