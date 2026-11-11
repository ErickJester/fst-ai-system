import React from 'react'

// Una marca de punto con la forma de su grupo o de su conducta. Así los puntos se distinguen también en blanco y negro,
// no solo por el color (ESPECIFICACION.md §2). `hueco` la dibuja sin relleno.
export default function Marca({ forma = 'circulo', cx, cy, r = 6, fill, stroke = 'var(--color-bg)', sw = 1.4, hueco = false, ...resto }) {
  const marca = hueco
    ? { fill: 'var(--color-bg)', stroke: fill, strokeWidth: 2 }
    : { fill, stroke, strokeWidth: sw }
  if (forma === 'cruz') {
    const a = r * 0.95
    return (
      <g stroke={fill} strokeWidth="2.4" strokeLinecap="round" {...resto}>
        <line x1={cx - a} y1={cy - a} x2={cx + a} y2={cy + a} />
        <line x1={cx - a} y1={cy + a} x2={cx + a} y2={cy - a} />
      </g>
    )
  }
  if (forma === 'rombo') return <polygon points={`${cx},${cy - r * 1.3} ${cx + r * 1.3},${cy} ${cx},${cy + r * 1.3} ${cx - r * 1.3},${cy}`} {...marca} {...resto} />
  if (forma === 'triangulo') return <polygon points={`${cx},${cy - r * 1.25} ${cx + r * 1.15},${cy + r * 0.95} ${cx - r * 1.15},${cy + r * 0.95}`} {...marca} {...resto} />
  if (forma === 'cuadrado') return <rect x={cx - r} y={cy - r} width={2 * r} height={2 * r} {...marca} {...resto} />
  return <circle cx={cx} cy={cy} r={r} {...marca} {...resto} />
}
