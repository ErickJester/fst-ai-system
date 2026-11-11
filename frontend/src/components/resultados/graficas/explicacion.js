// Los textos «en palabras» de la gráfica de grupos (G1): el tiempo en minutos y segundos, en porcentaje, y una frase que
// cuenta lo que se ve sin pretender ser una prueba estadística. Funciones puras, sin React, para probarlas aparte.
import { ESCALA_SEGUNDOS, numero } from './estilos'

export const UNIDADES = [
  { clave: 's', etiqueta: 'Segundos' },
  { clave: 'min', etiqueta: 'Minutos y segundos' },
  { clave: 'pct', etiqueta: 'Porcentaje del tiempo' },
]

const entero = (v) => String(Math.round(v))

// «2 min 23 s», «45 s», «5 min». Para frases; los segundos se redondean al entero.
export function tiempoEnPalabras(segundos) {
  const total = Math.round(segundos)
  const m = Math.floor(total / 60)
  const s = total % 60
  if (m === 0) return `${s} s`
  return s === 0 ? `${m} min` : `${m} min ${s} s`
}

// «2:23», el formato de reloj de los reportes. Para los ejes y los valores de la gráfica.
export function reloj(segundos) {
  const total = Math.round(segundos)
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`
}

// Un valor en segundos, en la unidad elegida. `base` es la duración que se toma como el 100 %.
export function valorEnUnidad(segundos, unidad, base = ESCALA_SEGUNDOS) {
  if (segundos == null) return null
  if (unidad === 'min') return reloj(segundos)
  if (unidad === 'pct') return `${entero((segundos / base) * 100)} %`
  return `${numero(segundos)} s`
}

// «120,0 ± 10,0 s» en segundos; «2:00 ± 0:10» en reloj; «40 ± 3 %» en porcentaje. Lo que no existe no se escribe como cero.
export function textoMediaDeEnUnidad(media, de, unidad, base = ESCALA_SEGUNDOS) {
  if (media == null) return 'sin datos'
  if (unidad === 'min') return de == null ? `${reloj(media)} ± no aplica` : `${reloj(media)} ± ${reloj(de)}`
  if (unidad === 'pct') {
    const m = entero((media / base) * 100)
    return de == null ? `${m} % ± no aplica` : `${m} ± ${entero((de / base) * 100)} %`
  }
  return de == null ? `${numero(media)} s ± no aplica` : `${numero(media)} ± ${numero(de)} s`
}

// Las marcas del eje, en la unidad elegida: [{ segundos, texto }].
export function marcasDelEje(escala, unidad, base = ESCALA_SEGUNDOS) {
  return Array.from({ length: 6 }, (_, k) => {
    const segundos = (escala / 5) * k
    return { segundos, texto: unidad === 'min' ? reloj(segundos) : unidad === 'pct' ? `${entero((segundos / base) * 100)} %` : entero(segundos) }
  })
}

const VERBOS = {
  inmovilidad: 'inmóvil',
  'nado activo': 'nadando',
  escalamiento: 'escalando',
}

// Los grupos con media de esa conducta, listos para contarse: { etiqueta, media, de, base }.
function comparables(grupos, clave) {
  return grupos
    .filter((g) => !g.nivelMixto && g.porConducta && g.n > 0 && g.porConducta[clave]?.media != null)
    .map((g) => ({ etiqueta: g.etiqueta, media: g.porConducta[clave].media, de: g.porConducta[clave].de, base: g.duracionMediaS || ESCALA_SEGUNDOS }))
}

// La frase de arriba de la gráfica. Solo cuenta lo que se ve: los promedios, su diferencia, cuánto varían los especímenes de cada grupo
// y si las líneas de ± desviación estándar se tocan. Sin conducta única (o con menos de dos grupos con datos) no hay frase de comparación.
// Devuelve una lista de oraciones (cada una, un renglón) o [] si no hay nada que decir.
export function fraseDeComparacion(grupos, clave) {
  const verbo = VERBOS[clave]
  if (!verbo) return []
  const gs = comparables(grupos, clave)
  if (!gs.length) return []

  const frases = []
  const cuentas = gs.map((g, i) => {
    const pct = `${entero((g.media / g.base) * 100)} % del tiempo`
    return i === 0
      ? `en ${g.etiqueta}, cada espécimen pasó ${tiempoEnPalabras(g.media)} ${verbo} (${pct})`
      : `en ${g.etiqueta}, ${tiempoEnPalabras(g.media)} (${pct})`
  })
  frases.push(`En promedio, ${cuentas.join('; ')}.`)

  if (gs.length < 2) return frases

  if (gs.length === 2) {
    const [a, b] = gs
    const dif = Math.abs(b.media - a.media)
    const mayor = b.media > a.media ? b : a
    frases.push(dif < 0.5
      ? 'Los dos promedios son prácticamente iguales.'
      : `La diferencia entre los dos promedios es de ${tiempoEnPalabras(dif)}; el promedio de ${mayor.etiqueta} es el más alto.`)
    if (a.de != null && b.de != null) {
      const seTocan = a.media - a.de <= b.media + b.de && b.media - b.de <= a.media + a.de
      frases.push(`Los especímenes de un mismo grupo no son iguales entre sí: varían ${tiempoEnPalabras(a.de)} en ${a.etiqueta} y ${tiempoEnPalabras(b.de)} en ${b.etiqueta}. ${seTocan
        ? 'Las líneas de ± desviación estándar se traslapan: con estos datos los grupos se ven parecidos.'
        : 'Las líneas de ± desviación estándar no se tocan: con estos datos los grupos se ven separados.'}`)
    } else {
      frases.push('Con un solo espécimen no hay variación que comparar.')
    }
  } else {
    frases.push('Compara las líneas de ± desviación estándar: si las de dos grupos no se tocan, esos grupos se ven separados.')
  }

  frases.push('Esto describe lo medido; no es una prueba estadística.')
  return frases
}
