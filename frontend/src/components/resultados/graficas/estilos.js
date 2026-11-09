// Estilos comunes de las gráficas de resultados (ESPECIFICACION.md §2): colores de app.css, formas y
// patrones que distinguen grupos y conductas también en blanco y negro, y los textos obligatorios.

// Las conductas que se dibujan (sin «sin ver», que no es una conducta). Las claves son las de la base y del backend.
export const CONDUCTAS_GRAFICA = [
  { clave: 'nado activo', etiqueta: 'Nado activo', color: 'var(--color-text)', patron: null },
  { clave: 'inmovilidad', etiqueta: 'Inmovilidad', color: 'var(--color-accent)', patron: null },
  { clave: 'escalamiento', etiqueta: 'Escalamiento', color: 'var(--color-neutral-400)', patron: null },
  { clave: 'conducta activa', etiqueta: 'Conducta activa', color: 'var(--color-accent-300)', patron: 'rayado' },
]

export const SIN_VER = { clave: 'sin ver', etiqueta: 'Sin ver', color: 'var(--color-divider)', patron: null }

// Las tres conductas que G1 dibuja como barras; la activa va aparte, como nota.
export const CONDUCTAS_BARRAS = CONDUCTAS_GRAFICA.filter((c) => c.clave !== 'conducta activa')

export const ESCALA_SEGUNDOS = 300   // la ventana del Día 2
export const SEGUNDOS_MINUTO = 60    // el máximo de un minuto

export const NOTA_DE = 'La barra ± DE muestra la dispersión entre especímenes; no indica si una diferencia es significativa.'
export const NOTA_ORIGEN = 'Los datos son sobre la salida del modelo; su exactitud todavía no está medida.'
export const NOTA_MIXTO = 'Mezcla niveles de clasificación; no se promedia junto.'

// Número en español: un decimal con coma. Lo que no existe no se escribe como cero.
export function numero(valor) {
  return valor == null ? null : valor.toFixed(1).replace('.', ',')
}

// «12,3 ± 2,1 s»; sin DE, «12,3 s ± no aplica»; sin media, «sin datos».
export function textoMediaDe(media, de, unidad = ' s') {
  if (media == null) return 'sin datos'
  return de == null ? `${numero(media)}${unidad} ± no aplica` : `${numero(media)} ± ${numero(de)}${unidad}`
}

export function porcentaje(fraccion) {
  return fraccion == null ? 'no aplica' : `${Math.round(fraccion * 100)} %`
}

// «n = 3» o, con tandas pendientes, «n = 2 de 4».
export function textoN(grupo) {
  return grupo.n < grupo.nTotal ? `n = ${grupo.n} de ${grupo.nTotal}` : `n = ${grupo.n}`
}

// Forma y patrón de cada grupo. El control es sólido; la referencia lleva rayado; los experimentales
// rotan entre tres patrones (y su forma de marcador en las líneas).
const FORMAS = ['triangulo', 'cuadrado', 'cruz']
const PATRONES_EXPERIMENTALES = ['punteado', 'vertical', 'horizontal']

// Un mapa idGrupo → estilo, en el orden en que se muestran los grupos.
export function estilosDeGrupos(grupos) {
  let experimentales = 0
  return new Map(grupos.map((g) => {
    if (g.tipo === 'control') return [g.idGrupo, { color: 'var(--color-text)', patron: null, forma: 'circulo', trazo: 'solida' }]
    if (g.tipo === 'referencia') return [g.idGrupo, { color: 'var(--color-accent)', patron: 'rayado', forma: 'rombo', trazo: 'discontinua' }]
    const k = experimentales++
    return [g.idGrupo, { color: 'var(--color-neutral-400)', patron: PATRONES_EXPERIMENTALES[k % 3], forma: FORMAS[k % 3], trazo: ['punteada', 'trazo-punto', 'larga'][k % 3] }]
  }))
}
