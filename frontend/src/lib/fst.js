// FST · utilidades compartidas (equivalen a mockup/v2/assets/app.js).

// ── almacenamiento local seguro (puede no estar disponible) ────────────────
export const store = {
  get(k, d) {
    try {
      const v = localStorage.getItem('fst.' + k)
      return v == null ? d : JSON.parse(v)
    } catch {
      return d
    }
  },
  set(k, v) {
    try {
      if (v == null) localStorage.removeItem('fst.' + k)
      else localStorage.setItem('fst.' + k, JSON.stringify(v))
    } catch {
      /* sin almacenamiento */
    }
  },
}

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
export const ESTADO_TANDA = { SIN_VIDEO: 'Sin video', QUEUED: 'En cola', RUNNING: 'Procesando', DONE: 'Completado', FAILED: 'Error' }
export const TIPO_GRUPO = { CONTROL: 'control', REFERENCIA: 'referencia', EXPERIMENTAL: 'tratamiento experimental' }
export const DIA = { DAY1: 'Día 1', DAY2: 'Día 2' }
// En el orden en que las recorre el pipeline.
export const ETAPA = {
  PREPROCESSING: 'Preprocesamiento',
  ROI_DETECTION: 'Detección de cilindros',
  TRACKING: 'Seguimiento de especímenes',
  CLASSIFICATION: 'Clasificación de conducta',
}

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

export const isIpn = (v) => /^[^\s@]+@ipn\.mx$/i.test(v.trim())

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

// Carga un script externo una sola vez (el generador de XLSX).
const scripts = {}
export function loadScript(src) {
  if (!scripts[src]) {
    scripts[src] = new Promise((resolve, reject) => {
      const s = document.createElement('script')
      s.src = src
      s.onload = resolve
      s.onerror = () => { delete scripts[src]; reject(new Error('No se pudo cargar ' + src)) }
      document.head.appendChild(s)
    })
  }
  return scripts[src]
}
