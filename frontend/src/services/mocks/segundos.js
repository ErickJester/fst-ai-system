import * as db from './data'

export const CONDUCTAS = ['nado', 'inmovilidad', 'escalamiento', 'activa']
export const DURACION_S = 300

// Sin backend, la conducta de cada segundo (la tabla SEGUNDO) se arma con los totales del
// espécimen, en tramos alternados como la línea de tiempo. Para mostrar el matiz, el
// espécimen 1 tiene unos segundos «no se ve» (null): datos ilustrativos, no salen de un video.
function segundosDeEjemplo(e) {
  const quedan = Object.fromEntries(CONDUCTAS.map((c) => [c, e[c + '_s']]))
  const largo = [9, 4, 7, 3, 11, 5, 2, 8, 6, 3]
  const seq = []
  let k = 0
  while (seq.length < DURACION_S && CONDUCTAS.some((c) => quedan[c] > 0)) {
    const c = CONDUCTAS[(k + e.numeroCilindro) % 4]
    const n = Math.min(largo[k % largo.length], quedan[c], DURACION_S - seq.length)
    k++
    if (!n) continue
    quedan[c] -= n
    for (let i = 0; i < n; i++) seq.push(c)
  }
  while (seq.length < DURACION_S) seq.push(null)
  if (e.numeroCilindro === 1) {
    for (let i = 120; i < 125; i++) seq[i] = null // un bloque entero sin ver
    seq[42] = null
    seq[43] = null
  }
  return seq
}

// Segundos de un espécimen: { conducta, origen } por segundo (0 a 299). Si ya se revisó,
// los guardados en la revisión; si no, los de la máquina.
export function segundosDe(idAnalisis, base) {
  const guardados = db.revisiones[idAnalisis]?.[base.numeroCilindro]
  if (guardados) return guardados.map(([conducta, origen]) => ({ conducta, origen }))
  return segundosDeEjemplo(base).map((conducta) => ({ conducta, origen: 'maquina' }))
}

// Totales por conducta (s) y desglose por minuto a partir de los segundos, como el backend
// recalcula INTERVALO y PRESENTA al guardar correcciones (R-14).
export function totalesDe(segundos) {
  const t = Object.fromEntries(CONDUCTAS.map((c) => [c + '_s', 0]))
  for (const { conducta } of segundos) if (conducta) t[conducta + '_s']++
  return t
}

export function minutosDe(segundos) {
  const minutos = []
  for (let m = 0; m < 5; m++) {
    const segs = []
    for (const { conducta } of segundos.slice(m * 60, m * 60 + 60)) {
      if (!conducta) continue
      if (segs.length && segs[segs.length - 1][0] === conducta) segs[segs.length - 1][1]++
      else segs.push([conducta, 1])
    }
    minutos.push(segs)
  }
  return minutos
}

// Recuadro de cada tubo en el video (cajas.json), en píxeles de un video de 1280 × 720.
export function cajasDe(n) {
  const paso = 1280 / n
  return Array.from({ length: n }, (_, i) => {
    const x0 = Math.round(paso * i + paso / 2 - 110)
    return { cilindro: i + 1, x0, y0: 130, x1: x0 + 220, y1: 640 }
  })
}
