// Equivalencias entre los valores de la guía (INSTRUCCIONES-WEB.md §5, §6.1), que manda el
// backend, y los que usan las pantallas. Las pantallas siguen con QUEUED, DONE, ADMIN…: los
// servicios traducen con estas tablas en los dos sentidos (PARA_VANESA_API.md §7).
//
// Uso: ESTADO.aFront['en cola'] → 'QUEUED'; ESTADO.aGuia.QUEUED → 'en cola'.

function par(aFront) {
  return Object.freeze({
    aFront: Object.freeze(aFront),
    aGuia: Object.freeze(Object.fromEntries(Object.entries(aFront).map(([g, f]) => [f, g]))),
  })
}

// Estado del experimento y del análisis (§4.8).
export const ESTADO = par({
  'sin videos': 'SIN_VIDEOS',
  'en espera del Día 2': 'ESPERA_DIA2',
  'en cola': 'QUEUED',
  procesando: 'RUNNING',
  'en pausa': 'PAUSED',
  completado: 'DONE',
  error: 'FAILED',
  cancelado: 'CANCELED',
})

// Etapa del análisis.
export const ETAPA = par({ preprocesamiento: 'PREPROCESSING', cilindros: 'ROI_DETECTION', clasificacion: 'CLASSIFICATION' })

// Tipo de grupo.
export const TIPO_GRUPO = par({ control: 'CONTROL', referencia: 'REFERENCIA', 'tratamiento experimental': 'EXPERIMENTAL' })

// Sesión del video (sin acento en la base).
export const SESION = par({ 'Dia 1': 'DAY1', 'Dia 2': 'DAY2' })

// Rol de la cuenta.
export const ROL = par({ Investigador: 'INVESTIGADOR', Administrador: 'ADMIN' })

// Tipo de notificación.
export const NOTIFICACION = par({
  'analisis completado': 'ANALYSIS_DONE',
  'error del pipeline': 'ANALYSIS_FAILED',
  'analisis en pausa': 'ANALYSIS_PAUSED',
  'analisis cancelado': 'ANALYSIS_CANCELED',
  'alerta de disco': 'DISK_ALERT',
  'video borrado': 'VIDEO_DELETED',
})

// Conductas: nombre en la base ↔ clave corta de las pantallas.
export const CONDUCTA = par({ 'nado activo': 'nado', inmovilidad: 'inmovilidad', escalamiento: 'escalamiento', 'conducta activa': 'activa' })

// Nivel de clasificación.
export const NIVEL = par({ preciso: 'PRECISO', agrupado: 'AGRUPADO' })

// Letra de la tanda ↔ ordinal de la base (A = 1).
export const ordinalDeLetra = (letra) => letra.charCodeAt(0) - 64
export const letraDeOrdinal = (n) => String.fromCharCode(64 + n)
