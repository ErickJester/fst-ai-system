// FST · datos de ejemplo de los mockups v2 (mockup/v2). No hay backend:
// cada pantalla muestra exactamente lo que muestra su mockup.

const INK = 'var(--color-text)'
const ACC = 'var(--color-accent)'
const MUT = 'var(--muted)'

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
  'En cola': { cls: 'tag-outline', bg: 'transparent', fg: INK, fill: 'var(--color-accent-200)' },
  'Procesando': { cls: 'tag-accent', bg: 'var(--color-accent-100)', fg: 'var(--color-accent-700)', fill: ACC },
  'Completado': { cls: 'tag-neutral', bg: 'var(--color-neutral-200)', fg: INK, fill: INK },
  'Error': { cls: 'tag-accent', bg: 'var(--color-accent-800)', fg: 'var(--color-bg)', fill: 'repeating-linear-gradient(135deg, var(--color-accent-800) 0 3px, var(--color-bg) 3px 6px)' },
}

export const TIPO_TAG = { control: 'tag-neutral', referencia: 'tag-outline', 'tratamiento experimental': 'tag-accent' }

// ── experimento de ejemplo: Compuesto CSR-14 · curva de dosis (2c, 2d) ─────
export const EXPERIMENTO = { clave: 'EXP-2026-02', titulo: 'Compuesto CSR-14 · curva de dosis', inicio: '18 feb 2026', responsable: 'M. Rivera' }

const RAW = [
  { id: 'G-01', nombre: 'Control', tipo: 'control', trat: 'Placebo (solución salina)', n: 8, porCuadro: 4, estados: ['Completado', 'En cola'] },
  { id: 'G-02', nombre: 'Referencia', tipo: 'referencia', trat: 'Fluoxetina 10 mg/kg', n: 8, porCuadro: 4, estados: ['Completado', 'Completado'] },
  { id: 'G-03', nombre: 'Experimental A', tipo: 'tratamiento experimental', trat: 'Compuesto CSR-14, 5 mg/kg', n: 8, porCuadro: 4, estados: ['Completado', 'Procesando'] },
  { id: 'G-04', nombre: 'Experimental B', tipo: 'tratamiento experimental', trat: 'Compuesto CSR-14, 15 mg/kg', n: 8, porCuadro: 4, estados: ['En cola', 'Error'] },
]

export const GROUPS = RAW.map((g) => {
  const nt = Math.ceil(g.n / g.porCuadro)
  const reparto = []
  for (let i = 0; i < nt; i++) reparto.push(i === nt - 1 ? g.n - g.porCuadro * (nt - 1) : g.porCuadro)
  const tandas = reparto.map((cuantos, i) => {
    const desde = reparto.slice(0, i).reduce((a, b) => a + b, 0) + 1
    const letra = String.fromCharCode(65 + i)
    return {
      letra,
      nombre: 'Tanda ' + letra,
      desde,
      cuantos,
      posiciones: 'Cilindros P1–P' + cuantos,
      estado: g.estados[i],
    }
  })
  return { ...g, tipoTag: TIPO_TAG[g.tipo], reparto, tandas }
})

// Notas de Día 2 según el estado de la tanda (2d).
export const D2_NOTA = {
  'Completado': { nota: 'Video principal del sistema. Analizado completo.', analisis: 'Análisis vigente: 24 feb 2026' },
  'En cola': { nota: 'En espera de turno en la cola: un trabajo a la vez.', analisis: 'Sin análisis todavía' },
  'Procesando': { nota: 'Seguimiento de especímenes · en curso · 75 %', analisis: 'Sin análisis todavía' },
  'Error': { nota: 'E-DET-070 · Confianza de detección 0.54, menor a 0.70: no se detectaron los cuatro cilindros.', analisis: 'Sin análisis todavía' },
}

// ── lista de experimentos (2a) ─────────────────────────────────────────────
export const EXPERIMENTOS = [
  { clave: 'EXP-2026-02', titulo: 'Compuesto CSR-14 · curva de dosis', trat: 'Compuesto CSR-14 Fluoxetina Placebo', fecha: '18 feb 2026', grupos: 4, especimenes: 32, videosTexto: '4 / 8', pct: '50%', estado: 'En análisis', tagClass: 'tag-accent', responsable: 'M. Rivera', retencion: '30 d', retFg: 'inherit', detalle: true },
  { clave: 'EXP-2026-01', titulo: 'Extracto de Salvia · dosis única', trat: 'Salvia', fecha: '9 ene 2026', grupos: 4, especimenes: 32, videosTexto: '8 / 8', pct: '100%', estado: 'Concluido', tagClass: 'tag-neutral', responsable: 'J. Domínguez', retencion: '12 d', retFg: 'inherit' },
  { clave: 'EXP-2025-11', titulo: 'Extracto de Valeriana officinalis', trat: 'Valeriana officinalis', fecha: '14 nov 2025', grupos: 5, especimenes: 38, videosTexto: '10 / 10', pct: '100%', estado: 'Concluido', tagClass: 'tag-neutral', responsable: 'M. Rivera', retencion: '4 d', retFg: 'var(--color-accent-700)' },
  { clave: 'EXP-2025-10', titulo: 'Réplica interanalista · control metodológico', trat: '', fecha: '2 oct 2025', grupos: 4, especimenes: 24, videosTexto: '3 / 6 cargados', pct: '50%', estado: 'Carga incompleta', tagClass: 'tag-outline', responsable: 'C. Reyes', retencion: 'Videos borrados', retFg: MUT },
  { clave: 'EXP-2025-08', titulo: 'Fluoxetina · calibración del clasificador', trat: 'Fluoxetina', fecha: '19 ago 2025', grupos: 4, especimenes: 32, videosTexto: '8 / 8', pct: '100%', estado: 'Concluido', tagClass: 'tag-neutral', responsable: 'C. Reyes', retencion: 'Videos borrados', retFg: MUT },
]

// ── cargar video por tanda (2b) ────────────────────────────────────────────
export const GRUPOS_CARGA = [
  { nombre: 'Control · placebo', tipo: 'control', tag: 'tag-neutral', cargadas: ['A', 'B'] },
  { nombre: 'Referencia · fluoxetina', tipo: 'referencia', tag: 'tag-outline', cargadas: ['A'] },
  { nombre: 'Experimental A · CSR-14, 5 mg/kg', tipo: 'tratamiento experimental', tag: 'tag-accent', cargadas: ['A', 'B'] },
  { nombre: 'Experimental B · CSR-14, 15 mg/kg', tipo: 'tratamiento experimental', tag: 'tag-accent', cargadas: ['A', 'B'] },
]

// ── progreso de análisis (2e) y cola (2g) ──────────────────────────────────
export const ETAPAS = [
  { nombre: 'Preprocesamiento', detalle: 'CLAHE (realce local de contraste)', icono: '✓', dotBg: INK, dotFg: 'var(--color-bg)', titleFg: INK, estado: 'hecha', pct: '25 %' },
  { nombre: 'Detección de cilindros', detalle: '4 cilindros detectados', icono: '✓', dotBg: INK, dotFg: 'var(--color-bg)', titleFg: INK, estado: 'hecha', pct: '50 %' },
  { nombre: 'Seguimiento de especímenes', detalle: 'tracking (seguimiento) de 4 especímenes', icono: '3', dotBg: ACC, dotFg: 'var(--color-bg)', titleFg: INK, estado: 'en curso', pct: '75 %' },
  { nombre: 'Clasificación de conducta', detalle: 'nado activo, inmovilidad, escalamiento', icono: '4', dotBg: 'transparent', dotFg: MUT, titleFg: MUT, estado: 'pendiente', pct: '100 %' },
]

export const COLA = [
  { n: '1', nombre: 'Compuesto CSR-14 · Experimental A · Tanda B', dia: 'Día 2', estado: 'Procesando', tagClass: 'tag-accent' },
  { n: '2', nombre: 'Compuesto CSR-14 · Control · Tanda B', dia: 'Día 2', estado: 'En cola', tagClass: 'tag-outline' },
  { n: '3', nombre: 'Compuesto CSR-14 · Experimental B · Tanda A', dia: 'Día 2', estado: 'En cola', tagClass: 'tag-outline' },
]

export const ERROR_ANALISIS = {
  titulo: 'Compuesto CSR-14 · Experimental B · Tanda B',
  detalle: 'Día 2 · 5 min · detenido en Detección de cilindros (50 %)',
  codigo: 'E-DET-070',
  causa: 'Confianza de detección 0.54, menor a 0.70: no se detectaron los cuatro cilindros.',
}

// ── resultados (2f): Grupo referencia, Tanda A, Día 2 ──────────────────────
export const BEHAVIORS = [
  { id: 'nado', label: 'Nado activo', color: INK, nota: 'Desplazamiento horizontal sostenido; el buceo cuenta aquí.' },
  { id: 'inmov', label: 'Inmovilidad', color: ACC, nota: 'Solo los movimientos mínimos para mantenerse a flote.' },
  { id: 'escal', label: 'Escalamiento', color: 'var(--color-neutral-400)', nota: 'Patas delanteras rompiendo la superficie contra la pared del cilindro.' },
]

export const RESULTADOS = [
  { pos: '1', label: 'Cilindro P1', nado: 171, inmov: 88, escal: 41 },
  { pos: '2', label: 'Cilindro P2', nado: 158, inmov: 104, escal: 38 },
  { pos: '3', label: 'Cilindro P3', nado: 182, inmov: 76, escal: 42 },
  { pos: '4', label: 'Cilindro P4', nado: 149, inmov: 118, escal: 33 },
]

// Línea de tiempo por minuto del Cilindro P1: [conducta, segundos].
export const PATRON_RATA1 = [
  [['nado', 38], ['escal', 8], ['nado', 10], ['inmov', 4]],
  [['nado', 30], ['inmov', 12], ['escal', 6], ['nado', 8], ['inmov', 4]],
  [['inmov', 10], ['nado', 22], ['escal', 9], ['inmov', 8], ['nado', 11]],
  [['nado', 16], ['inmov', 20], ['nado', 12], ['escal', 7], ['inmov', 5]],
  [['inmov', 14], ['nado', 24], ['inmov', 11], ['escal', 11]],
]

// ── administración (2g) ────────────────────────────────────────────────────
export const CUENTAS = [
  { nombre: 'M. Rivera Alcántara', correo: 'mrivera@ipn.mx', rol: 'Investigador', activa: true },
  { nombre: 'C. S. Reyes López', correo: 'creyesl@ipn.mx', rol: 'Administrador', activa: true },
  { nombre: 'J. Domínguez Vera', correo: 'jdominguez@ipn.mx', rol: 'Investigador', activa: true },
  { nombre: 'L. Ortega Camacho', correo: 'lortega@ipn.mx', rol: 'Investigador', activa: true },
]

export const COLA_ADMIN = [
  { nombre: 'Compuesto CSR-14 · Experimental A · Tanda B · Día 2', t: 'Procesando', dot: ACC },
  { nombre: 'Compuesto CSR-14 · Control · Tanda B · Día 2', t: 'En cola', dot: 'transparent' },
  { nombre: 'Compuesto CSR-14 · Experimental B · Tanda A · Día 2', t: 'En cola', dot: 'transparent' },
]

export const DISCO = [
  { k: 'Videos crudos (se borran a los 30 días)', v: '381 GB' },
  { k: 'Resultados y reportes (permanentes)', v: '2.4 GB' },
  { k: 'Se liberan en los próximos 7 días', v: '48 GB' },
]

export const CATALOGOS = [
  { titulo: 'Conductas', items: [{ n: 'Nado activo', m: '≥ 3 s' }, { n: 'Inmovilidad', m: '≥ 3 s' }, { n: 'Escalamiento', m: '≥ 3 s' }] },
  { titulo: 'Modelo del clasificador', items: [{ n: 'Modelo en uso: clf-cascada v2.1', m: '' }] },
]
