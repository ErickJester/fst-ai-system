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
