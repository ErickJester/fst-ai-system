import { listExperiments, detalleConStatus } from './experiments'
import { ESTADO, ETAPA, SESION } from '../lib/contrato'

// «Análisis en curso» (2e) sin una cola global: la guía solo da el progreso por experimento,
// GET /experiments/{id}/status (§6.1; PARA_VANESA_API.md §3, decisión 6). Con un experimento,
// se usa solo el suyo; sin él, se juntan los experimentos que tienen algo activo, con error o
// cancelado según la lista. La cola de Administración sale de /admin/system (services/admin.js).

// Experimentos de la lista que pueden tener análisis que mostrar.
const CON_ANALISIS = ['QUEUED', 'RUNNING', 'PAUSED', 'FAILED', 'CANCELED']
const TERMINADOS = 5

// Un análisis de /status, con su tanda del detalle → la forma que usan las pantallas.
function trabajo(exp, g, t, a) {
  return {
    job_id: a.idAnalisis,
    clave: exp.clave,
    gid: g.id,
    experimento: exp.titulo.split(' · ')[0],
    grupo: g.nombre,
    tanda: t.letra,
    dia: SESION.aFront[a.sesion] || 'DAY2',
    n_especimenes: t.n_cilindros,
    subido_por: t.subido_por,
    status: ESTADO.aFront[a.estado],
    stage: a.etapa ? ETAPA.aFront[a.etapa] : null,
    progress_pct: a.porcentaje ?? 0,
    analisis_fecha: a.fechaAnalisis ?? null,
    motivo: a.motivoCancelacion ?? null,
    ...(a.pausa && { pausa: { cilindro: a.pausa.cilindro, desde_segundo: a.pausa.desdeSegundo }, segundos_restantes: a.pausa.segundosRestantes }),
  }
}

// → { cola, pausas, errores, cancelados, terminados }, la forma que usan Progreso y Pausa.
// La cola va en orden de llegada: mientras un análisis no termina, fechaAnalisis es la hora
// en que entró a la cola (D-79). Uno ajustado tras una pausa conserva la suya y vuelve por
// delante de los que llegaron después; uno reiniciado entra con la hora del reinicio.
export async function getQueue(clave = null) {
  const claves = clave ? [clave] : (await listExperiments()).filter((e) => CON_ANALISIS.includes(e.estado)).map((e) => e.clave)
  const todos = []
  for (const c of claves) {
    let r
    try {
      r = await detalleConStatus(c)
    } catch (e) {
      if (clave || e?.response?.status !== 404) throw e
      continue // sin detalle (datos de ejemplo): no tiene análisis que mostrar
    }
    const porId = new Map(r.analisis.map((a) => [a.idAnalisis, a]))
    for (const g of r.exp.grupos) {
      for (const t of g.tandas) {
        const a = porId.get(t.id_analisis)
        if (a) todos.push(trabajo(r.exp, g, t, a))
      }
    }
  }
  const de = (s) => todos.filter((j) => j.status === s)
  const llegada = (a, b) => (a.analisis_fecha || '').localeCompare(b.analisis_fecha || '') || a.job_id - b.job_id
  const cola = [...de('RUNNING'), ...de('QUEUED').sort(llegada)].map((j, i) => ({ ...j, posicion: i + 1 }))
  const terminados = de('DONE').sort((a, b) => (b.analisis_fecha || '').localeCompare(a.analisis_fecha || '')).slice(0, TERMINADOS)
  return { cola, pausas: de('PAUSED'), errores: de('FAILED'), cancelados: de('CANCELED'), terminados }
}
