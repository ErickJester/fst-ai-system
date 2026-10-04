import React from 'react'
import { Link } from 'react-router-dom'
import Topbar, { Brand } from './Topbar'
import { ESTADO_TANDA, TIPO_GRUPO } from '../lib/fst'

// ── apariencia de estados y tipos (mockups v2) ─────────────────────────────
const INK = 'var(--color-text)'

// Por etiqueta de estado de tanda: clase, colores de la etiqueta y relleno de la barra de avance.
const ESTADO = {
  'Sin video': { cls: 'tag-outline', bg: 'transparent', fg: 'var(--muted)', fill: 'transparent' },
  'En cola': { cls: 'tag-outline', bg: 'transparent', fg: INK, fill: 'var(--color-accent-200)' },
  'Procesando': { cls: 'tag-accent', bg: 'var(--color-accent-100)', fg: 'var(--color-accent-700)', fill: 'var(--color-accent)' },
  'Completado': { cls: 'tag-neutral', bg: 'var(--color-neutral-200)', fg: INK, fill: INK },
  'Error': { cls: 'tag-accent', bg: 'var(--color-accent-800)', fg: 'var(--color-bg)', fill: 'repeating-linear-gradient(135deg, var(--color-accent-800) 0 3px, var(--color-bg) 3px 6px)' },
}

const TIPO_TAG = { control: 'tag-neutral', referencia: 'tag-outline', 'tratamiento experimental': 'tag-accent' }

// Relleno de la barra de avance para un estado de tanda de la API (QUEUED, DONE…).
export const rellenoTanda = (estado) => ESTADO[ESTADO_TANDA[estado]].fill

// Etiqueta del estado de una tanda, a partir del valor de la API (QUEUED, RUNNING, DONE, FAILED, SIN_VIDEO).
export function EstadoTag({ estado }) {
  const label = ESTADO_TANDA[estado]
  const e = ESTADO[label]
  return (
    <span className={'tag ' + e.cls} style={{ whiteSpace: 'nowrap', background: e.bg, color: e.fg }}>
      {label}
    </span>
  )
}

// Etiqueta del tipo de grupo, a partir del valor de la API (CONTROL, REFERENCIA, EXPERIMENTAL).
export function TipoTag({ tipo, style }) {
  const label = TIPO_GRUPO[tipo]
  return <span className={'tag ' + TIPO_TAG[label]} style={style}>{label}</span>
}

// ── formularios ────────────────────────────────────────────────────────────
// Segmentado (.seg): opciones [{ v, label }], una activa.
export function Seg({ options, value, onChange, id }) {
  return (
    <span className="seg" id={id} style={{ display: 'inline-flex' }}>
      {options.map((o) => (
        <button
          key={o.v}
          type="button"
          className={'seg-opt' + (o.v === value ? ' on' : '')}
          aria-pressed={o.v === value}
          onClick={() => onChange(o.v)}
        >
          {o.label}
        </button>
      ))}
    </span>
  )
}

// Campo con su etiqueta; el control (input, Seg…) va como hijo.
export function Campo({ id, label, style, children }) {
  return (
    <div className="field" style={style}>
      <label htmlFor={id}>{label}</label>
      {children}
    </div>
  )
}

// Mensaje de error bajo un formulario (vacío = oculto).
export function FieldError({ msg, style }) {
  if (!msg) return null
  return <div className="field-error" style={style}>{msg}</div>
}

// ── navegación ─────────────────────────────────────────────────────────────
// Migas de pan de la barra superior: [{ to, label }, …, { label }]; la última es la pantalla actual.
export function Migas({ items }) {
  return (
    <span className="crumbs">
      {items.map((it, i) => (
        <React.Fragment key={i}>
          {i > 0 && ' / '}
          {it.to ? <Link to={it.to}>{it.label}</Link> : <span className="here">{it.label}</span>}
        </React.Fragment>
      ))}
    </span>
  )
}

const PASOS = ['Datos generales', 'Cargar video por tanda']

// Cabecera de pasos de «Nuevo experimento»; actual empieza en 1.
export function Pasos({ actual }) {
  return (
    <div style={{ display: 'flex', alignItems: 'stretch', border: '1px solid var(--color-divider)', marginBottom: 28 }}>
      {PASOS.map((nombre, i) => {
        const activo = i + 1 === actual
        const style = { flex: 1, padding: '11px 14px', background: activo ? 'var(--color-accent)' : 'var(--color-surface)' }
        if (activo) style.color = 'var(--color-bg)'
        if (i < PASOS.length - 1) style.borderRight = '1px solid var(--color-divider)'
        return (
          <div key={nombre} style={style}>
            <div className="k" style={activo ? { color: 'color-mix(in srgb,#fff 70%,transparent)' } : undefined}>Paso {i + 1}</div>
            <div className="nd" style={activo ? { fontSize: 13, marginTop: 3 } : { fontSize: 13, marginTop: 3, color: 'var(--muted)' }}>{nombre}</div>
          </div>
        )
      })}
    </div>
  )
}

// ── pantallas completas ────────────────────────────────────────────────────
// Tarjeta de las pantallas de acceso. Con encabezado: franja de color con «FST» y el subtítulo.
export function Acceso({ encabezado, sub, children }) {
  return (
    <div className="acc-page">
      <div className="acc-card">
        {encabezado && (
          <div style={{ background: 'var(--color-accent)', color: 'var(--color-bg)', padding: '22px 34px' }}>
            <div className="nd" style={{ fontSize: 24 }}>FST</div>
            {sub && <div style={{ fontSize: 11, lineHeight: 1.5, marginTop: 4, color: 'color-mix(in srgb,#fff 78%,transparent)' }}>{sub}</div>}
          </div>
        )}
        {children}
      </div>
    </div>
  )
}

export function Cargando() {
  return (
    <div className="app">
      <Topbar><Brand /></Topbar>
      <div className="page"><p className="hint">Cargando…</p></div>
    </div>
  )
}

export function NoEncontrado() {
  return (
    <div className="app">
      <Topbar><Brand /></Topbar>
      <div className="page">
        <h3 style={{ margin: '0 0 8px' }}>No encontrado</h3>
        <p className="lead" style={{ marginBottom: 16 }}>Solo el experimento de ejemplo tiene detalle mientras no hay backend.</p>
        <Link className="btn btn-secondary" to="/experimentos">Volver a experimentos</Link>
      </div>
    </div>
  )
}
