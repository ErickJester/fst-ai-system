// FST · utilidades compartidas (equivalen a mockup/v2/assets/app.js).

// ── estadísticos y formato (idénticos al mockup) ───────────────────────────
export const mean = (a) => a.reduce((x, y) => x + y, 0) / a.length
export const variance = (a) => {
  const m = mean(a)
  return a.reduce((s, v) => s + (v - m) * (v - m), 0) / (a.length - 1)
}
export const fmt = (s) => Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0')
export const r1 = (n) => (Math.round(n * 10) / 10).toFixed(1)

// '2026-02-18' → '18 feb 2026' (sin pasar por Date, para no depender de la zona horaria).
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']
export const fechaCorta = (iso) => {
  const [y, m, d] = iso.split('-').map(Number)
  return d + ' ' + MESES[m - 1] + ' ' + y
}

// Dos fechas del mismo mes → '18–19 feb 2026'.
export const fechaRango = (desde, hasta) => {
  const [a, b] = [fechaCorta(desde), fechaCorta(hasta)]
  if (a === b) return a
  return a.slice(a.indexOf(' ')) === b.slice(b.indexOf(' ')) ? a.split(' ')[0] + '–' + b : a + ' – ' + b
}

// ── valores de la API → etiquetas de los mockups ───────────────────────────
export const ESTADO_TANDA = { SIN_VIDEO: 'Sin video', QUEUED: 'En cola', RUNNING: 'Procesando', PAUSED: 'En pausa', DONE: 'Completado', FAILED: 'Error', CANCELED: 'Cancelado' }
export const TIPO_GRUPO = { CONTROL: 'control', REFERENCIA: 'referencia', EXPERIMENTAL: 'tratamiento experimental' }
export const DIA = { DAY1: 'Día 1', DAY2: 'Día 2' }
// En el orden en que las recorre el pipeline: tres etapas, un tercio del avance cada una.
export const ETAPA = {
  PREPROCESSING: 'Preprocesamiento',
  ROI_DETECTION: 'Cilindros',
  CLASSIFICATION: 'Clasificación',
}
// Porcentaje que informa el backend en cada etapa (33, 67 y 100; 0 en cola).
export const PCT_ETAPA = { PREPROCESSING: 33, ROI_DETECTION: 67, CLASSIFICATION: 100 }

// Estado de un experimento: no se guarda, se calcula con sus tandas (§4.8 del documento).
export const ESTADO_EXPERIMENTO = {
  SIN_VIDEOS: 'Sin videos',
  ESPERA_DIA2: 'En espera del Día 2',
  QUEUED: 'En cola',
  RUNNING: 'Procesando',
  PAUSED: 'En pausa',
  DONE: 'Completado',
  FAILED: 'Con error',
  CANCELED: 'Cancelado',
}

// ── Bloques de 5 s (D-53, RN-15) ─────────────────────────────────────────
// Conducta de un bloque a partir de sus cinco segundos ('nado', 'inmovilidad',
// 'escalamiento', 'activa' o null si no se ve). No usa la red: es mayoría sobre los cinco.
// - Gana la conducta con más segundos (2 de nado y 1 de escalamiento: nado).
// - Empate entre conductas activas (2 de nado y 2 de escalamiento): conducta activa.
// - Empate de inmovilidad con una activa (2 y 2): decide el quinto; si es escalamiento o no
//   se ve, conducta activa. Cualquier otro empate con inmovilidad también queda activa.
// - Los cinco sin ver: conducta activa con matiz (el modelo no pudo ver bien esa parte).
// matiz también marca un bloque que se decidió con algún segundo sin ver.
export function bloqueDe(segundos) {
  const cuenta = {}
  for (const c of segundos) if (c) cuenta[c] = (cuenta[c] || 0) + 1
  const sinVer = segundos.filter((c) => !c).length
  const tope = Math.max(0, ...Object.values(cuenta))
  if (!tope) return { conducta: 'activa', matiz: true }
  const ganan = Object.keys(cuenta).filter((c) => cuenta[c] === tope)
  if (ganan.length === 1) return { conducta: ganan[0], matiz: false }
  return { conducta: 'activa', matiz: sinVer > 0 }
}

// Pantalla de pausa de un análisis (R-15): ahí se ajusta el recuadro o se cancela.
export const enlacePausa = (clave, idAnalisis) => `/experimentos/${clave}/analisis/${idAnalisis}/pausa`

// «3:41» a partir de segundos.
export const mmss = (s) => Math.floor(s / 60) + ':' + String(Math.floor(s % 60)).padStart(2, '0')

// Un espécimen se distingue por su posición en el cuadro; como el número se repite en cada
// tanda del grupo, donde se juntan tandas va con la tanda: «Tanda A · Espécimen 2» (D-55).
export const especimen = (n, letra) => (letra ? 'Tanda ' + letra + ' · ' : '') + 'Espécimen ' + n

// Un análisis con error no trae código ni descripción: el mensaje se arma con la etapa en
// que se detuvo. Hay dos errores definidos: el video no se pudo abrir o no se hallaron los tubos.
const ERROR_POR_ETAPA = {
  PREPROCESSING: 'No se pudo abrir el video.',
  ROI_DETECTION: 'No se encontraron los tubos en el video.',
}
export const mensajeErrorAnalisis = (etapa) => ERROR_POR_ETAPA[etapa] || 'El análisis se detuvo en la etapa «' + (ETAPA[etapa] || etapa) + '».'

// Primera tanda con el Día 2 analizado de un experimento (detalle de la API), o null.
export function primeraTandaLista(exp) {
  for (const g of exp.grupos) {
    const t = g.tandas.find((x) => x.estado === 'DONE')
    if (t) return { gid: g.id, letra: t.letra }
  }
  return null
}

// Enlace a los resultados de una tanda.
export const enlaceResultados = (clave, gid, letra) => `/experimentos/${clave}/resultados?grupo=${gid}&tanda=${letra}`

// Búsqueda sin acentos ni mayúsculas.
export const norm = (s) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

// Correo de cualquier dominio (D-46): solo se revisa que tenga forma de correo.
export const esCorreo = (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.trim())

// ── descargas ──────────────────────────────────────────────────────────────
export function download(name, text, type) {
  const blob = new Blob([text], { type: type || 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(a.href), 1000)
}

export function toCSV(rows) {
  return '﻿' + rows.map((r) => r.map((v) => {
    const s = String(v)
    return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s
  }).join(',')).join('\n')
}
