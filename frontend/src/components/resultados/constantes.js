import { enHoraLocal, fechaCorta } from '../../lib/fst'

export const INK = 'var(--color-text)'
export const ACC = 'var(--color-accent)'
export const N400 = 'var(--color-neutral-400)'
export const MUT = 'var(--muted)'
export const PEND = 'var(--color-accent-700)'

export const ACTIVA = 'var(--color-accent-300)'
// Las cuatro conductas (R-12). En cada segundo el espécimen está en exactamente una; un mismo
// análisis puede mezclar las cuatro.
export const CONDUCTAS = [
  { id: 'nado', label: 'Nado activo', color: INK, nota: 'Desplazamiento horizontal sostenido; el buceo cuenta aquí.' },
  { id: 'inmovilidad', label: 'Inmovilidad', color: ACC, nota: 'Solo los movimientos mínimos para mantenerse a flote.' },
  { id: 'escalamiento', label: 'Escalamiento', color: N400, nota: 'Patas delanteras rompiendo la superficie contra la pared del cilindro.' },
  { id: 'activa', label: 'Conducta activa', color: ACTIVA, nota: 'El espécimen no estaba inmóvil, pero el sistema no decidió entre nado activo y escalamiento.' },
]
const KEYS = CONDUCTAS.map((c) => c.id)
// «Sin ver»: los segundos sin conducta (no se vio al espécimen o la revisión los marcó como no visibles). No es una conducta: va
// aparte, discreta, al final de los totales y de cada minuto; con las cuatro suma el total de segundos.
export const SIN_VER = { id: 'sin_ver', label: 'Sin ver', color: 'var(--color-divider)' }
export const TODAS = [...KEYS, SIN_VER.id]
export const CMAP = Object.fromEntries([...CONDUCTAS, SIN_VER].map((c) => [c.id, c.color]))
export const LBL = Object.fromEntries([...CONDUCTAS, SIN_VER].map((c) => [c.id, c.label]))

// Bloque que se decidió sin ver bien al espécimen (matiz de D-53).
export const MATIZ = 'repeating-linear-gradient(135deg, var(--color-accent-300) 0 3px, var(--color-bg) 3px 6px)'
export const COLOR_TIPO = { CONTROL: INK, REFERENCIA: ACC, EXPERIMENTAL: N400 }
export const COLOR_AGRUPADO = 'var(--color-accent-300)'
export const DIA2_S = 300
export const PENDIENTE = { QUEUED: 'en cola', RUNNING: 'procesando', PAUSED: 'en pausa', FAILED: 'con error', CANCELED: 'cancelada' }

// Etiquetas: el espécimen siempre con su tanda, porque su número se repite en cada tanda (D-55).
export const etiquetaAnimal = (numero, letra) => (letra ? 'Tanda ' + letra + ' · ' : '') + 'Espécimen ' + numero
export const etiqueta = (e, letra) => etiquetaAnimal(e.numeroCilindro, letra)
export const nombreGrupo = (g) => (g.tipo === 'CONTROL' ? 'Grupo control' : 'Grupo ' + g.nombre[0].toLowerCase() + g.nombre.slice(1))
export const hora = (iso) => enHoraLocal(iso).slice(11, 16)
export const fechaYHora = (analizado) => fechaCorta(analizado.slice(0, 10)) + (analizado.length > 10 ? ', ' + hora(analizado) : '')

// ── reportes ────────────────────────────────────────────────────────────────
// Los genera el equipo de backend (GET /experiments/:clave/report/:formato); aquí solo se
// descargan. Mientras no haya backend, cada botón explica qué hará.
export const REPORTES = {
  csv: {
    boton: 'CSV', tipo: 'text/csv',
    hara: 'las columnas Grupo, Tanda, Espécimen, Tiempo (min:seg), Nado, Escalamiento, Inmovilidad y Conducta activa, con una fila por espécimen y por minuto, y al final la media, la desviación estándar y la varianza de lo que se ve.',
  },
  xlsx: {
    boton: 'XLSX', tipo: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    hara: 'el mismo contenido que el CSV, en un libro de Excel.',
  },
  pdf: {
    boton: 'PDF · reporte de resultados', tipo: 'application/pdf',
    hara: 'el reporte de resultados que se entrega al terminar, con los fragmentos «no se ve» marcados. Es distinto del reporte de diagnóstico.',
  },
}

// Las vistas del detalle de una tanda (?vista=). Las gráficas están en el resumen del experimento, no aquí.
export const TABS = [
  { id: 'especimen', label: 'Por espécimen' },
  { id: 'segundo', label: 'Segundo a segundo' },
  { id: 'timeline', label: 'Minuto a minuto' },
  { id: 'bloques', label: 'Cada 5 segundos' },
  { id: 'comparacion', label: 'Comparación entre grupos' },
]
