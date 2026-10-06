import React, { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Topbar, { Brand } from '../components/Topbar'
import { EstadoTag, TipoTag, FieldError, Campo, Migas, Cargando, NoEncontrado, rellenoTanda } from '../components/ui'
import { getExperiment, deleteExperiment } from '../services/experiments'
import { mensajeError } from '../services/api'
import { useAsync } from '../hooks/useAsync'
import { useAtraparFoco } from '../hooks/useAtraparFoco'
import { fechaCorta, ESTADO_TANDA, primeraTandaLista, enlaceResultados } from '../lib/fst'
import { btnLeft } from '../lib/estilos'

const posiciones = (t) => 'Cilindros P1–P' + t.n_cilindros

const LEGEND = [
  { label: 'En cola', style: { background: 'var(--color-accent-200)', border: '1px solid var(--color-divider)' } },
  { label: 'Procesando', style: { background: 'var(--color-accent)' } },
  { label: 'Completado', style: { background: 'var(--color-text)' } },
  { label: 'Error', style: { background: 'repeating-linear-gradient(135deg,var(--color-accent-800) 0 2px,var(--color-bg) 2px 4px)', border: '1px solid var(--color-accent-800)' } },
]

// 2c · Detalle de experimento: grupos con progreso de tandas.
export default function ExperimentoPage() {
  const { clave } = useParams()
  const navigate = useNavigate()
  const [dlg, setDlg] = useState(false)
  const [nombre, setNombre] = useState('')
  const [pass, setPass] = useState('')
  const [borrando, setBorrando] = useState(false)
  const [errBorrar, setErrBorrar] = useState('')
  const nombreRef = useRef(null)
  const dlgRef = useRef(null)
  const { data: exp, error, loading } = useAsync(() => getExperiment(clave), [clave])

  function cerrar() {
    setDlg(false)
    setNombre('')
    setPass('')
    setErrBorrar('')
  }

  // Antes del efecto que enfoca el nombre, para recordar el botón que abrió el diálogo.
  useAtraparFoco(dlgRef, dlg)

  useEffect(() => {
    if (!dlg) return
    nombreRef.current?.focus()
    const onKey = (e) => { if (e.key === 'Escape') cerrar() }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [dlg])

  if (loading) return <Cargando />
  if (error) return <NoEncontrado />

  const groups = exp.grupos
  const tandas = groups.flatMap((g) => g.tandas)
  const completas = tandas.filter((t) => t.estado === 'DONE').length
  const lista = primeraTandaLista(exp)
  const esp = groups.reduce((a, g) => a + g.n_especimenes, 0)
  const puedeBorrar = nombre === exp.titulo && pass.length > 0 && !borrando

  async function eliminar(e) {
    e.preventDefault()
    if (!puedeBorrar) return
    setBorrando(true)
    setErrBorrar('')
    try {
      await deleteExperiment(exp.clave, { titulo: nombre, password: pass })
      navigate('/experimentos?eliminado=' + encodeURIComponent(exp.clave))
    } catch (err) {
      setErrBorrar(mensajeError(err, 'No se pudo eliminar el experimento.'))
      setBorrando(false)
    }
  }

  return (
    <div className="app">
      <Topbar>
        <Brand inline />
        <Migas items={[{ to: '/experimentos', label: 'Experimentos' }, { label: exp.titulo }]} />
      </Topbar>

      <div className="page">
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 28 }}>
          <div style={{ flex: 1 }}>
            <h2 style={{ margin: '0 0 10px' }}>{exp.titulo}</h2>
            <div style={{ display: 'flex', gap: 26, fontSize: 13, color: 'var(--muted-2)' }}>
              <span>Inicio <strong style={{ color: 'var(--color-text)' }}>{fechaCorta(exp.fecha_inicio)}</strong></span>
              <span className="num">{groups.length} grupos · {esp} especímenes · {tandas.length} tandas</span>
              <span>Responsable <strong style={{ color: 'var(--color-text)' }}>{exp.responsable}</strong></span>
              {exp.especie && <span>Especie <strong style={{ color: 'var(--color-text)' }}>{exp.especie}</strong></span>}
            </div>
            {exp.notas && <p style={{ margin: '10px 0 0', maxWidth: 720, fontSize: 13, lineHeight: 1.6, whiteSpace: 'pre-line', color: 'var(--muted-2)' }}>{exp.notas}</p>}
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <Link className="btn btn-secondary" to={`/experimentos/${exp.clave}/cargar`}>Cargar videos pendientes</Link>
            {lista
              ? <Link className="btn btn-primary" to={enlaceResultados(exp.clave, lista.gid, lista.letra)}>Ver resultados</Link>
              : <button type="button" className="btn btn-primary" disabled title="Todavía no hay tandas con el Día 2 analizado">Ver resultados</button>}
          </div>
        </div>

        <hr className="hr" />

        <div style={{ display: 'flex', alignItems: 'center', gap: 22, marginBottom: 22 }}>
          <div style={{ flex: 1 }}>
            <div className="k" style={{ marginBottom: 8 }}>Avance · {completas} de {tandas.length} tandas con Día 2 completado</div>
            <div style={{ display: 'flex', gap: 2 }}>
              {tandas.map((t, i) => (
                <div key={i} title={ESTADO_TANDA[t.estado]} style={{ flex: 1, height: 12, background: rellenoTanda(t.estado), border: '1px solid var(--color-divider)' }} />
              ))}
            </div>
          </div>
          <div style={{ display: 'flex', gap: 18, fontSize: 11.5, color: 'var(--muted-2)' }}>
            {LEGEND.map((l) => <span key={l.label} className="legend"><i style={l.style} />{l.label}</span>)}
          </div>
        </div>

        {groups.length === 0 && (
          <div className="note-bar" style={{ marginBottom: 22 }}>
            Todavía no hay grupos. Se crean al cargar el primer video de cada tanda: <Link to={`/experimentos/${exp.clave}/cargar`}>cargar videos</Link>.
          </div>
        )}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2, background: 'var(--color-divider)', border: '1px solid var(--color-divider)' }}>
          {groups.map((g) => (
            <div key={g.id} style={{ background: 'var(--color-bg)', padding: '18px 20px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 11, marginBottom: 14 }}>
                <span className="nd" style={{ fontSize: 17 }}>{g.nombre}</span>
                <TipoTag tipo={g.tipo} />
                <div style={{ flex: 1 }} />
                <span className="num" style={{ fontSize: 11, color: 'color-mix(in srgb,var(--color-text) 50%,transparent)' }}>{g.id}</span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1.6fr .4fr', gap: 14, paddingBottom: 14, borderBottom: '1px solid var(--color-divider)' }}>
                <div><div className="k">Tratamiento</div><div style={{ fontSize: 13, marginTop: 3 }}>{g.tratamiento}</div></div>
                <div><div className="k">n</div><div className="num" style={{ fontSize: 13, marginTop: 3 }}>{g.n_especimenes}</div></div>
              </div>
              {g.tandas.map((t) => (
                <div key={t.letra} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '11px 0', borderBottom: '1px solid var(--color-divider)' }}>
                  <span className="nd" style={{ fontSize: 13 }}>Tanda {t.letra}</span>
                  <span className="num" style={{ fontSize: 11.5, color: 'var(--muted)' }}>{posiciones(t)}</span>
                  <div style={{ flex: 1 }} />
                  <EstadoTag estado={t.estado} />
                </div>
              ))}
              <Link className="btn btn-ghost" to={`/experimentos/${exp.clave}/grupos/${g.id}`} style={{ marginTop: 12 }}>Abrir grupo →</Link>
            </div>
          ))}
        </div>

        <div style={{ marginTop: 26, border: '2px solid var(--color-accent)', display: 'flex', alignItems: 'center', gap: 20, padding: '16px 18px' }}>
          <div style={{ flex: 1, fontSize: 12.5, lineHeight: 1.6 }}>
            <strong>Eliminar este experimento</strong> borra grupos, tandas, videos, análisis y reportes. Es permanente e irreversible: pide escribir el nombre del experimento, «{exp.titulo}», y la contraseña de la cuenta.
          </div>
          <button type="button" className="btn btn-primary" onClick={() => setDlg(true)}>Eliminar experimento…</button>
        </div>
      </div>

      {dlg && (
        <div className="dialog-backdrop" onClick={(e) => { if (e.target === e.currentTarget) cerrar() }}>
          <form ref={dlgRef} className="dialog dlg-del" noValidate role="dialog" aria-modal="true" aria-labelledby="dlgT" onSubmit={eliminar}>
            <div className="dialog-title" id="dlgT">Eliminar experimento</div>
            <div className="dialog-body" style={{ fontSize: 12.5, lineHeight: 1.6 }}>Borra grupos, tandas, videos, análisis y reportes. Es permanente e irreversible.</div>
            <Campo id="dNombre" label={<>Escribe el nombre del experimento, «{exp.titulo}»</>}>
              <input ref={nombreRef} className="input" id="dNombre" autoComplete="off" value={nombre} onChange={(e) => setNombre(e.target.value)} />
            </Campo>
            <Campo id="dPass" label="Contraseña de la cuenta">
              <input className="input num" id="dPass" type="password" autoComplete="current-password" value={pass} onChange={(e) => setPass(e.target.value)} />
            </Campo>
            <FieldError msg={errBorrar} style={{ marginBottom: 12 }} />
            <div className="dialog-actions" style={btnLeft}>
              <button type="submit" className="btn btn-primary" disabled={!puedeBorrar}>Eliminar experimento</button>
              <button type="button" className="btn btn-secondary" onClick={cerrar}>Cancelar</button>
            </div>
          </form>
        </div>
      )}
    </div>
  )
}
