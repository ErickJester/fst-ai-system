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
export const ESTADO_TANDA = { SIN_VIDEO: 'Sin video', ESPERA_DIA2: 'En espera del Día 2', QUEUED: 'En cola', RUNNING: 'Procesando', PAUSED: 'En pausa', DONE: 'Completado', FAILED: 'Error', CANCELED: 'Cancelado' }
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
// Cuánto pesa cada etapa en la barra general (suman 100): son los pesos que MIDIÓ el worker con un video real
// (worker_fake/progreso.py). Solo sirven para dibujar dónde empieza cada etapa en la barra.
export const PESO_ETAPA = { PREPROCESSING: 1, ROI_DETECTION: 9, CLASSIFICATION: 90 }

// Porcentaje propio de una etapa de un análisis en curso: hecha 100; en curso, el que informa el backend
// (stage_pct) o, si no lo trae, el de la etapa; pendiente 0.
export function pctDeEtapa(j, etapa, etapas) {
  const i = etapas.indexOf(etapa)
  const iActual = etapas.indexOf(j.stage)
  if (i < iActual) return 100
  if (i > iActual) return 0
  return j.stage_pct ?? PCT_ETAPA[etapa]
}

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

// Nombres de las conductas en la base (CONDUCTA.nombre) ↔ claves del frontend.
export const CONDUCTA_BD = { nado: 'nado activo', inmovilidad: 'inmovilidad', escalamiento: 'escalamiento', activa: 'conducta activa' }
export const DE_BD = Object.fromEntries(Object.entries(CONDUCTA_BD).map(([k, v]) => [v, k]))

// Revisión segundo a segundo de un análisis completado (R-14).
export const enlaceRevision = (clave, idAnalisis) => `/experimentos/${clave}/analisis/${idAnalisis}/revision`

// Estado que se muestra de una tanda: con el Día 1 guardado y sin Día 2, «en espera del Día 2».
export const estadoDeTanda = (t) => (t.estado === 'SIN_VIDEO' && t.dia1 ? 'ESPERA_DIA2' : t.estado)

// Pantalla de pausa de un análisis (R-15): ahí se ajusta el recuadro o se cancela.
export const enlacePausa = (clave, idAnalisis) => `/experimentos/${clave}/analisis/${idAnalisis}/pausa`

// «3:41» a partir de segundos.
// m:ss, o h:mm:ss si pasa de una hora.
export function mmss(s) {
  const t = Math.floor(s)
  const h = Math.floor(t / 3600)
  const m = Math.floor((t % 3600) / 60)
  const seg = String(t % 60).padStart(2, '0')
  return h > 0 ? h + ':' + String(m).padStart(2, '0') + ':' + seg : m + ':' + seg
}

// Segundos que pasaron desde una hora ISO hasta ahoraMs (nunca negativos: el reloj del navegador y el del
// worker pueden diferir unos segundos).
export const segundosDesde = (iso, ahoraMs) => Math.max(0, Math.floor((ahoraMs - Date.parse(iso)) / 1000))

// El equipo de un análisis, como se muestra: su nombre o, si no tiene, el modelo del procesador.
export const nombreEquipo = (equipo) => equipo?.nombre || equipo?.cpu || null

// El video de un análisis, con el nombre del archivo como lo subió el usuario («Video: acu-yintang2.mp4»). Los videos
// anteriores al 20-oct-2026 no lo tienen: «Video: nombre no registrado». Siempre visible: no depende del modo desarrollador.
export const textoVideo = (nombre) => 'Video: ' + (nombre || 'nombre no registrado')

// Segundos de una etapa de un análisis en curso (j.tiempos, de services/progreso.js): hecha → los que midió el worker;
// en curso → los que lleva, contados desde su inicio; sin empezar (o sin registro) → null, que se muestra «—».
export function tiempoDeEtapa(j, etapa, ahoraMs) {
  const e = j.tiempos?.etapas?.[etapa]
  if (!e) return null
  if (e.segundos != null) return e.segundos
  return e.fin ? Math.max(0, Math.round((Date.parse(e.fin) - Date.parse(e.inicio)) / 1000)) : segundosDesde(e.inicio, ahoraMs)
}

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

// Código del grupo como en la maqueta: «G-01» (el id de la base es un número).
export const codigoGrupo = (id) => (/^\d+$/.test(String(id)) ? 'G-' + String(id).padStart(2, '0') : String(id))

// Enlace a los resultados de una tanda.
export const enlaceResultados = (clave, gid, letra) => `/experimentos/${clave}/resultados?grupo=${gid}&tanda=${letra}`

// Búsqueda sin acentos ni mayúsculas.
export const norm = (s) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

// Correo de cualquier dominio (D-46): solo se revisa que tenga forma de correo.
export const esCorreo = (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.trim())

// ── descargas ──────────────────────────────────────────────────────────────
export function download(name, text, type) {
  const blob = new Blob([text], { type })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(a.href), 1000)
}

