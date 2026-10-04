import React, { useState } from 'react'
import { Link, NavLink, useSearchParams } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import Topbar, { Brand } from '../components/Topbar'
import { Seg } from '../components/ui'
import { EXPERIMENTOS, EXPERIMENTO } from '../data/mock'
import { norm } from '../lib/fst'

const FILTROS = [
  { v: 'todos', label: 'Todos', test: () => true },
  { v: 'analisis', label: 'En análisis', test: (e) => e.estado === 'En análisis' },
  { v: 'incompleta', label: 'Carga incompleta', test: (e) => e.estado === 'Carga incompleta' },
  { v: 'concluidos', label: 'Concluidos', test: (e) => e.estado === 'Concluido' },
]

// Los reportes del mockup apuntan todos a los resultados de ejemplo.
const RESULTADOS = `/experimentos/${EXPERIMENTO.clave}/resultados`

// 2a · Experimentos: lista a nivel experimento con estado agregado.
export default function ExperimentosPage() {
  const { user } = useAuth()
  const [params] = useSearchParams()
  const [filtro, setFiltro] = useState('todos')
  const [q, setQ] = useState('')

  // Tras «Eliminar experimento…» en el detalle, el experimento ya no aparece.
  const eliminado = params.get('eliminado')
  const experiments = EXPERIMENTOS.filter((e) => e.clave !== eliminado)

  const f = FILTROS.find((x) => x.v === filtro)
  const nq = norm(q.trim())
  const list = experiments.filter(f.test).filter((e) => !nq || norm(e.titulo + ' ' + e.trat).includes(nq))

  return (
    <div className="app">
      <Topbar>
        <Brand sub="Laboratorio de Bioquímica Estructural · ENMyH-IPN" />
        <NavLink to="/experimentos" end>Experimentos</NavLink>
        <NavLink to="/analisis">Análisis en curso</NavLink>
        {user.admin && <NavLink to="/admin">Administración</NavLink>}
      </Topbar>

      <div className="page" style={{ paddingBottom: 34 }}>
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 28, marginBottom: 22 }}>
          <div style={{ flex: 1 }}>
            <h2 style={{ margin: '0 0 8px' }}>Experimentos</h2>
            <p className="lead" style={{ maxWidth: 620 }}>
              Prueba de nado forzado (<em>Forced Swim Test</em>, FST). El archivo completo del laboratorio, visible para cualquier cuenta activa.
            </p>
          </div>
          <Link className="btn btn-primary" to={`/experimentos/${EXPERIMENTO.clave}/cargar`} style={{ justifyContent: 'flex-start' }}>Nuevo experimento</Link>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 16, padding: '13px 16px', marginBottom: 22, background: 'var(--color-accent-100)', borderLeft: '4px solid var(--color-accent)' }}>
          <span className="trace">RN-06</span>
          <span style={{ flex: 1, fontSize: 12.5, lineHeight: 1.55 }}>
            Los 10 videos crudos de <strong>Extracto de <em>Valeriana officinalis</em></strong> se eliminan en 4 días (retención de 30 días). Resultados y reportes se conservan indefinidamente; el video crudo no.
          </span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
          <Seg
            options={FILTROS.map((x) => ({ v: x.v, label: x.label + ' · ' + experiments.filter(x.test).length }))}
            value={filtro}
            onChange={setFiltro}
          />
          <div style={{ flex: 1 }} />
          <input className="input" type="search" placeholder="Buscar por nombre o tratamiento" style={{ width: 250 }} value={q} onChange={(e) => setQ(e.target.value)} />
        </div>

        <table className="table">
          <thead>
            <tr>
              <th style={{ width: '29%' }}>Experimento</th><th>Grupos</th><th>Especímenes</th><th style={{ width: '14%' }}>Videos Día 2</th>
              <th>Estado</th><th>Responsable</th><th>Retención</th><th>Reporte</th>
            </tr>
          </thead>
          <tbody>
            {list.map((e) => (
              <tr key={e.clave}>
                <td>
                  {e.detalle
                    ? <Link to={`/experimentos/${e.clave}`} className="nd" style={{ fontSize: 14, color: 'var(--color-text)', textDecoration: 'none' }}>{e.titulo}</Link>
                    : <div className="nd" style={{ fontSize: 14 }}>{e.titulo}</div>}
                  <div className="num" style={{ fontSize: 11, color: 'var(--muted)', marginTop: 2 }}>{e.fecha}</div>
                </td>
                <td className="num">{e.grupos}</td>
                <td className="num">{e.especimenes}</td>
                <td>
                  <div className="num" style={{ fontSize: 12, marginBottom: 5 }}>{e.videosTexto}</div>
                  <div className="meter"><div style={{ width: e.pct }} /></div>
                </td>
                <td><span className={'tag ' + e.tagClass}>{e.estado}</span></td>
                <td style={{ fontSize: 13 }}>{e.responsable}</td>
                <td className="num" style={{ fontSize: 12, color: e.retFg }}>{e.retencion}</td>
                <td>
                  <div style={{ display: 'flex', gap: 4 }}>
                    {['CSV', 'XLSX', 'PDF'].map((x) => <Link key={x} className="chip" to={RESULTADOS}>{x}</Link>)}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {list.length === 0 && <p className="hint" style={{ margin: '14px 0 0' }}>Ningún experimento coincide con la búsqueda.</p>}
        <p style={{ margin: '14px 0 0', fontSize: 11.5, color: 'var(--muted)' }}>
          Eliminar un experimento es permanente e irreversible: la acción vive dentro del detalle del experimento, nunca en esta lista.
        </p>
      </div>
    </div>
  )
}
