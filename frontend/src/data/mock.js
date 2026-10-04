// FST · datos de ejemplo de los mockups v2 (mockup/v2). No hay backend:
// cada pantalla muestra exactamente lo que muestra su mockup.

const INK = 'var(--color-text)'
const ACC = 'var(--color-accent)'

// ── cuentas (barra superior 2k, perfil 2j, administración 2g) ──────────────
export const USERS = [
  { ini: 'MR', nombre: 'Mariana', apellidos: 'Rivera Alcántara', correo: 'mrivera@ipn.mx', idInst: '2019630871', rol: 'Investigador', admin: false },
  { ini: 'CR', nombre: 'C. S.', apellidos: 'Reyes López', correo: 'creyesl@ipn.mx', idInst: '2008630412', rol: 'Administrador', admin: true },
]

// ── notificaciones (2k) ────────────────────────────────────────────────────
export const NOTIFS = [
  { id: 'n1', titulo: 'Error en el análisis', texto: 'Compuesto CSR-14 · Experimental B · Tanda B · Día 2. Confianza de detección 0.54, menor a 0.70.', enlace: 'Ver detalle del error', to: '/analisis', hora: '15:41', leida: false },
  { id: 'n2', titulo: 'Análisis completado', texto: 'Compuesto CSR-14 · Referencia · Tanda A · Día 2.', enlace: 'Ver resultados', to: '/experimentos/EXP-2026-02/resultados', hora: '15:02', leida: false },
  { id: 'n3', titulo: 'Análisis completado', texto: 'Compuesto CSR-14 · Control · Tanda A · Día 2.', enlace: 'Ver resultados', to: '/experimentos/EXP-2026-02/resultados', hora: 'ayer', leida: true },
]

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
