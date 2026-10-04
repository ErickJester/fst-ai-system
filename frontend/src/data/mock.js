// FST · apariencia de los mockups v2 (mockup/v2): colores y clases de cada
// estado y tipo. Los datos salen de la capa de servicios (services/).

const INK = 'var(--color-text)'
const ACC = 'var(--color-accent)'

// ── estados de una tanda ───────────────────────────────────────────────────
export const ESTADO = {
  'Sin video': { cls: 'tag-outline', bg: 'transparent', fg: 'var(--muted)', fill: 'transparent' },
  'En cola': { cls: 'tag-outline', bg: 'transparent', fg: INK, fill: 'var(--color-accent-200)' },
  'Procesando': { cls: 'tag-accent', bg: 'var(--color-accent-100)', fg: 'var(--color-accent-700)', fill: ACC },
  'Completado': { cls: 'tag-neutral', bg: 'var(--color-neutral-200)', fg: INK, fill: INK },
  'Error': { cls: 'tag-accent', bg: 'var(--color-accent-800)', fg: 'var(--color-bg)', fill: 'repeating-linear-gradient(135deg, var(--color-accent-800) 0 3px, var(--color-bg) 3px 6px)' },
}

export const TIPO_TAG = { control: 'tag-neutral', referencia: 'tag-outline', 'tratamiento experimental': 'tag-accent' }

// ── experimento de ejemplo (enlaces de 2a) ───────────────────────────────────
export const EXPERIMENTO = { clave: 'EXP-2026-02', titulo: 'Compuesto CSR-14 · curva de dosis', inicio: '18 feb 2026', responsable: 'M. Rivera' }
