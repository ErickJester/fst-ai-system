import { describe, it, expect } from 'vitest'
import { tiempoEnPalabras, reloj, valorEnUnidad, textoMediaDeEnUnidad, marcasDelEje, fraseDeComparacion } from './explicacion'

const grupo = (etiqueta, media, de, over = {}) => ({
  etiqueta, n: 4, nivelMixto: false, duracionMediaS: 300,
  porConducta: { inmovilidad: { media, de } }, ...over,
})

describe('tiempo en palabras y en reloj', () => {
  it('dice minutos y segundos, y calla lo que vale cero', () => {
    expect(tiempoEnPalabras(45)).toBe('45 s')
    expect(tiempoEnPalabras(142.6)).toBe('2 min 23 s')
    expect(tiempoEnPalabras(120)).toBe('2 min')
    expect(tiempoEnPalabras(300)).toBe('5 min')
  })

  it('el reloj lleva los segundos con dos cifras', () => {
    expect(reloj(142.6)).toBe('2:23')
    expect(reloj(65)).toBe('1:05')
    expect(reloj(0)).toBe('0:00')
  })
})

describe('valores en la unidad elegida', () => {
  it('segundos, reloj y porcentaje del tiempo', () => {
    expect(valorEnUnidad(120, 's')).toBe('120,0 s')
    expect(valorEnUnidad(120, 'min')).toBe('2:00')
    expect(valorEnUnidad(120, 'pct')).toBe('40 %')
    expect(valorEnUnidad(null, 's')).toBeNull()
  })

  it('el porcentaje usa la duración que se le da como el 100 %', () => {
    expect(valorEnUnidad(150, 'pct', 150)).toBe('100 %')
  })

  it('media ± DE en cada unidad; sin DE dice «no aplica»; sin media, «sin datos»', () => {
    expect(textoMediaDeEnUnidad(120, 10, 's')).toBe('120,0 ± 10,0 s')
    expect(textoMediaDeEnUnidad(120, 10, 'min')).toBe('2:00 ± 0:10')
    expect(textoMediaDeEnUnidad(120, 15, 'pct')).toBe('40 ± 5 %')
    expect(textoMediaDeEnUnidad(42, null, 's')).toBe('42,0 s ± no aplica')
    expect(textoMediaDeEnUnidad(60, null, 'pct')).toBe('20 % ± no aplica')
    expect(textoMediaDeEnUnidad(null, null, 's')).toBe('sin datos')
  })

  it('las marcas del eje siguen la unidad', () => {
    expect(marcasDelEje(300, 's').map((m) => m.texto)).toEqual(['0', '60', '120', '180', '240', '300'])
    expect(marcasDelEje(300, 'min').map((m) => m.texto)).toEqual(['0:00', '1:00', '2:00', '3:00', '4:00', '5:00'])
    expect(marcasDelEje(300, 'pct').map((m) => m.texto)).toEqual(['0 %', '20 %', '40 %', '60 %', '80 %', '100 %'])
  })
})

describe('la frase de comparación', () => {
  it('con dos grupos cuenta los promedios, su diferencia, la variación y que las líneas se traslapan', () => {
    const frases = fraseDeComparacion([grupo('Acu-YBN', 122.4, 37.2), grupo('Acu yn', 142.6, 46.5)], 'inmovilidad')
    expect(frases[0]).toBe('En promedio, en Acu-YBN, cada espécimen pasó 2 min 2 s inmóvil (41 % del tiempo); en Acu yn, 2 min 23 s (48 % del tiempo).')
    expect(frases[1]).toBe('La diferencia entre los dos promedios es de 20 s; el promedio de Acu yn es el más alto.')
    expect(frases[2]).toContain('varían 37 s en Acu-YBN y 47 s en Acu yn')
    expect(frases[2]).toContain('se traslapan')
    expect(frases.at(-1)).toBe('Esto describe lo medido; no es una prueba estadística.')
  })

  it('si las líneas no se tocan, lo dice', () => {
    const frases = fraseDeComparacion([grupo('Control', 200, 10), grupo('Dosis', 100, 10)], 'inmovilidad')
    expect(frases.join(' ')).toContain('no se tocan')
  })

  it('con dos promedios casi iguales no inventa una diferencia', () => {
    const frases = fraseDeComparacion([grupo('A', 100, 5), grupo('B', 100.2, 5)], 'inmovilidad')
    expect(frases[1]).toBe('Los dos promedios son prácticamente iguales.')
  })

  it('con un solo espécimen por grupo no hay variación que comparar', () => {
    const frases = fraseDeComparacion([grupo('A', 100, null), grupo('B', 150, null)], 'inmovilidad')
    expect(frases.join(' ')).toContain('Con un solo espécimen no hay variación que comparar.')
    expect(frases.join(' ')).not.toContain('se traslapan')
  })

  it('con tres o más grupos manda a comparar las líneas, sin diferencias de dos en dos', () => {
    const frases = fraseDeComparacion([grupo('A', 100, 5), grupo('B', 150, 5), grupo('C', 200, 5)], 'inmovilidad')
    expect(frases[1]).toMatch(/Compara las líneas/)
    expect(frases.join(' ')).not.toContain('La diferencia entre los dos')
  })

  it('un grupo sin datos o con tandas de distinto nivel no entra a la frase', () => {
    const frases = fraseDeComparacion([
      grupo('A', 100, 5),
      grupo('Sin datos', null, null, { n: 0 }),
      grupo('Mixto', 150, 5, { nivelMixto: true, porConducta: null }),
    ], 'inmovilidad')
    expect(frases.join(' ')).not.toContain('Sin datos')
    expect(frases.join(' ')).not.toContain('Mixto')
    expect(frases.length).toBe(1) // solo el promedio de A: con un grupo no hay nada que comparar
  })

  it('sin una conducta única (o sin grupos con datos) no hay frase', () => {
    expect(fraseDeComparacion([grupo('A', 100, 5)], 'tres')).toEqual([])
    expect(fraseDeComparacion([grupo('A', null, null, { n: 0 })], 'inmovilidad')).toEqual([])
    expect(fraseDeComparacion([], 'inmovilidad')).toEqual([])
  })

  it('cada conducta lleva su verbo', () => {
    const g = (clave) => ({ ...grupo('A', 60, 5), porConducta: { [clave]: { media: 60, de: 5 } } })
    expect(fraseDeComparacion([g('nado activo')], 'nado activo')[0]).toContain('nadando')
    expect(fraseDeComparacion([g('escalamiento')], 'escalamiento')[0]).toContain('escalando')
  })
})
