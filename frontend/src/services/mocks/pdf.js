// PDF mínimo de una página con líneas de texto (Helvetica), para el reporte de diagnóstico
// de ejemplo. Con backend, el PDF lo genera el worker al fallar el análisis.
export function pdfDeTexto(lineas) {
  const esc = (s) => s.replace(/[\\()]/g, '\\$&')
  const texto = lineas.map((l, i) => `BT /F1 ${i === 0 ? 18 : 11} Tf 56 ${740 - i * 24} Td (${esc(l)}) Tj ET`).join('\n')
  const objetos = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>',
    `<< /Length ${texto.length} >>\nstream\n${texto}\nendstream`,
  ]
  let pdf = '%PDF-1.4\n'
  const posiciones = objetos.map((o, i) => {
    const pos = pdf.length
    pdf += `${i + 1} 0 obj\n${o}\nendobj\n`
    return pos
  })
  const xref = pdf.length
  pdf += `xref\n0 ${objetos.length + 1}\n0000000000 65535 f \n` + posiciones.map((p) => String(p).padStart(10, '0') + ' 00000 n \n').join('')
  pdf += `trailer\n<< /Size ${objetos.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`
  // Un byte por carácter (Latin-1, compatible con WinAnsi) para que salgan los acentos.
  return new Blob([Uint8Array.from(pdf, (c) => c.charCodeAt(0) & 0xff)], { type: 'application/pdf' })
}
