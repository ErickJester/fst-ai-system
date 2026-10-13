import api from './api'
import { USE_MOCKS } from './config'
import { reply, fail } from './mocks/delay'
import * as db from './mocks/data'
import { simular } from './mocks/simulador'
import { resumir } from './mocks/resumen'
import { usuarioDeLaSesion } from './mocks/sesion'
import { segundosDe, totalesDe, cajasDe } from './mocks/segundos'
import { CONDUCTA_BD, DE_BD } from '../lib/fst'

// Revisión segundo a segundo (R-14, §9.7). Las formas siguen §6.1 del documento: camelCase
// y las conductas con su nombre de la base ('nado activo', … o null).

// Tanda de un análisis, con su experimento y grupo (sin backend se busca en los datos).
function buscar(clave, id) {
  const exp = db.experimentDetail[clave]
  for (const g of exp?.grupos || []) {
    const t = g.tandas.find((x) => x.id_analisis === Number(id))
    if (t) return { exp, g, t }
  }
  return null
}

// Solo un análisis completado, y solo quien subió el video o un Administrador (D-57).
function revisable(clave, id) {
  simular()
  const r = buscar(clave, id)
  if (!r) return { error: fail(404, 'No existe ese análisis.') }
  const u = usuarioDeLaSesion()
  if (!u || (u.role !== 'ADMIN' && r.t.subido_por !== u.id)) {
    return { error: fail(403, 'Solo quien subió el video o un Administrador puede revisar este análisis.') }
  }
  if (r.t.estado !== 'DONE') return { error: fail(409, 'Solo se revisa un análisis completado.') }
  return r
}

// Especímenes de ejemplo de la tanda (los mismos de sus resultados).
function especimenesDe({ exp, g, t }) {
  const propios = db.batchResults[[exp.clave, g.id, t.letra, 'DAY2'].join('/')]
  return (propios || db.batchResults['EXP-2026-02/G-02/A/DAY2']).especimenes.slice(0, t.n_cilindros)
}

// GET /experiments/:clave/analyses/:id/review → { puedeCorregir, videoUrl, tanda, duracion,
// especimenes: [{ numeroCilindro, recuadro, segundos: [{ segundo, conducta, origen }] }] }.
// videoUrl apunta a la copia H.264 (web.mp4, con Range); sin backend no hay video (null).
export async function getRevision(clave, id) {
  if (USE_MOCKS) {
    const r = revisable(clave, id)
    if (r.error) return r.error
    const cajas = cajasDe(r.t.n_cilindros)
    return reply({
      puedeCorregir: true,
      videoUrl: null,
      experimento: { clave, titulo: r.exp.titulo },
      grupo: { id: r.g.id, nombre: r.g.nombre, tratamiento: r.g.tratamiento },
      tanda: r.t.letra,
      duracion: 300,
      ancho: 1280,
      alto: 720,
      especimenes: especimenesDe(r).map((e) => ({
        numeroCilindro: e.numeroCilindro,
        recuadro: cajas[e.numeroCilindro - 1],
        segundos: segundosDe(r.t.id_analisis, e).map((x, i) => ({ segundo: i + 1, conducta: x.conducta ? CONDUCTA_BD[x.conducta] : null, origen: x.origen })),
      })),
    })
  }
  return (await api.get(`/experiments/${clave}/analyses/${id}/review`)).data
}

// PUT /experiments/:clave/analyses/:id/segundos { especimenes: [{ numeroCilindro, segundos:
// [{ segundo, conducta, origen }] }] } con solo los segundos que cambiaron. El backend
// recalcula los totales por minuto y el nivel, y borra los reportes guardados.
export async function guardarSegundos(clave, id, especimenes) {
  if (USE_MOCKS) {
    const r = revisable(clave, id)
    if (r.error) return r.error
    const rev = (db.revisiones[r.t.id_analisis] ||= {})
    for (const e of especimenes) {
      const base = especimenesDe(r).find((x) => x.numeroCilindro === e.numeroCilindro)
      if (!base) return fail(400, 'No existe el espécimen ' + e.numeroCilindro + '.')
      const actual = rev[e.numeroCilindro] || segundosDe(r.t.id_analisis, base).map((x) => [x.conducta, x.origen])
      for (const s of e.segundos) actual[s.segundo - 1] = [s.conducta ? DE_BD[s.conducta] : null, s.origen]
      rev[e.numeroCilindro] = actual
    }
    resumir(clave)
    return reply({
      especimenes: especimenesDe(r).map((e) => ({ numeroCilindro: e.numeroCilindro, ...totalesDe(segundosDe(r.t.id_analisis, e)) })),
    })
  }
  return (await api.put(`/experiments/${clave}/analyses/${id}/segundos`, { especimenes })).data
}

// POST /experiments/:clave/analyses/:id/restart → borra el resultado anterior al empezar y
// deja el análisis en cola (D-60, D-68).
export async function reiniciarAnalisis(clave, id) {
  if (USE_MOCKS) {
    const r = revisable(clave, id)
    if (r.error) return r.error
    const { exp, g, t } = r
    delete db.revisiones[t.id_analisis]
    Object.assign(t, { estado: 'QUEUED', progreso: null, error: null, analisis_fecha: null })
    db.queue.push({
      job_id: t.id_analisis, posicion: db.queue.length + 1, clave, gid: g.id, creado_ms: Date.now(),
      experimento: exp.titulo.split(' · ')[0], grupo: g.nombre, tanda: t.letra, dia: 'DAY2', n_especimenes: t.n_cilindros,
      subido_por: t.subido_por, status: 'QUEUED', stage: null, progress_pct: 0,
    })
    resumir(clave)
    return reply({ ok: true })
  }
  return (await api.post(`/experiments/${clave}/analyses/${id}/restart`)).data
}
