import React, { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Topbar, { Brand } from '../components/Topbar'
import { Cargando, FieldError } from '../components/ui'
import RecuadroEditable from '../components/RecuadroEditable'
import { getPausa, getImagenPausa, ajustarRecuadro, cancelarAnalisis } from '../services/queue'
import { getQueue } from '../services/progreso'
import { mensajeError } from '../services/api'
import { useAsync, useAsyncVivo, CADA_ACTIVO_MS } from '../hooks/useAsync'
import { DIA, plazoRestante } from '../lib/fst'
import { MUT60, btnLeft } from '../lib/estilos'

const CAMPOS = [['x0', 'Izquierda'], ['y0', 'Arriba'], ['x1', 'Derecha'], ['y1', 'Abajo']]

// 2e′ · Pantalla de pausa (R-15, D-89): el pipeline dejó de ver a un espécimen 3 s seguidos.
// Muestra la imagen fija de ese instante y el recuadro de su cilindro para corregirlo.
// «Ajustar y continuar» devuelve el análisis a la cola; «Cancelar análisis» lo termina. Sin
// respuesta en 5 minutos, el sistema lo cancela. Solo quien subió el video o un Administrador.
export default function PausaPage() {
  const { clave, id } = useParams()
  const navigate = useNavigate()
  // Se vuelve a pedir cada 5 s para tener al día el tiempo que queda.
  const { data: p, error } = useAsyncVivo(() => getPausa(clave, id), [clave, id], () => CADA_ACTIVO_MS)
  const { data: cola } = useAsync(() => getQueue(clave), [clave])
  const [img, setImg] = useState(null)
  const [recuadro, setRecuadro] = useState(null)
  const [vence, setVence] = useState(null) // hora local en que se acaba el plazo
  const [ahora, setAhora] = useState(() => Date.now())
  const [enviando, setEnviando] = useState(false)
  const [errAccion, setErrAccion] = useState('')

  // Imagen fija: se descarga una vez y se libera al salir.
  useEffect(() => {
    let url = null
    let vivo = true
    getImagenPausa(clave, id).then((b) => { if (vivo && b) setImg((url = URL.createObjectURL(b))) }).catch(() => {})
    return () => { vivo = false; if (url) URL.revokeObjectURL(url) }
  }, [clave, id])

  // El recuadro editable empieza en el que tenía el cilindro; el plazo se toma del servidor.
  useEffect(() => {
    if (!p) return
    setRecuadro((r) => r || p.recuadros.find((x) => x.cilindro === p.cilindro))
    setVence(Date.now() + p.segundosRestantes * 1000)
  }, [p])

  // Cuenta regresiva en pantalla, de segundo en segundo.
  useEffect(() => {
    const t = setInterval(() => setAhora(Date.now()), 1000)
    return () => clearInterval(t)
  }, [])

  if (error) return <NoEnPausa error={error} />
  if (!p || !recuadro) return <Cargando />

  const job = cola?.pausas.find((j) => j.job_id === Number(id))
  const titulo = job ? job.experimento + ' · ' + job.grupo + ' · Tanda ' + job.tanda + ' · ' + DIA[job.dia] : 'Análisis en pausa'
  const quedan = Math.min(p.segundosRestantes, Math.max(0, Math.ceil((vence - ahora) / 1000)))
  const vencido = quedan === 0

  async function ajustar() {
    setEnviando(true)
    setErrAccion('')
    try {
      const { cilindro, x0, y0, x1, y1 } = recuadro
      await ajustarRecuadro(clave, id, { cilindro, x0, y0, x1, y1 })
      navigate('/analisis?experimento=' + clave, { state: { aviso: 'Recuadro del cilindro ' + cilindro + ' ajustado. El análisis volvió a la cola, por delante de los que llegaron después, y sigue desde el segundo ' + p.desdeSegundo + '.' } })
    } catch (e) {
      setErrAccion(e.response?.status === 409 ? 'El análisis ya no está en pausa: pasaron las 72 horas y se canceló.' : mensajeError(e, 'No se pudo ajustar el recuadro. Inténtalo de nuevo.'))
      setEnviando(false)
    }
  }

  async function cancelar() {
    if (!window.confirm('¿Cancelar el análisis? No se puede reanudar: habrá que subir el video otra vez.')) return
    setEnviando(true)
    setErrAccion('')
    try {
      await cancelarAnalisis(clave, id)
      navigate('/analisis?experimento=' + clave, { state: { aviso: 'Análisis cancelado: ' + titulo + '.' } })
    } catch (e) {
      setErrAccion(e.response?.status === 409 ? 'El análisis ya no está en pausa.' : mensajeError(e, 'No se pudo cancelar el análisis. Inténtalo de nuevo.'))
      setEnviando(false)
    }
  }

  const setCampo = (k) => (e) => {
    const v = Number(e.target.value)
    if (Number.isFinite(v)) setRecuadro((r) => ({ ...r, [k]: v }))
  }

  return (
    <div className="app narrow">
      <Topbar><Brand sub="Análisis en pausa" /></Topbar>

      <div className="page" style={{ paddingBottom: 34 }}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, marginBottom: 6 }}>
          <span className="tag tag-accent" style={{ whiteSpace: 'nowrap' }}>En pausa</span>
          <span className="num" style={{ fontSize: 12, color: MUT60 }}>{titulo}</span>
        </div>
        <h3 style={{ margin: '0 0 8px' }}>No se ve al espécimen del cilindro {p.cilindro} (segundo {p.desdeSegundo})</h3>
        <p className="lead" style={{ margin: '0 0 16px', maxWidth: 640 }}>
          El sistema dejó de verlo tres segundos seguidos. Mueve o cambia el tamaño del recuadro hasta que encierre el cilindro {p.cilindro} y pulsa «Ajustar y continuar»: el análisis sigue desde ese segundo. Mientras tanto, la cola atiende los demás videos.
        </p>

        <div role="timer" aria-live="off" className="num nd" style={{ fontSize: 15, marginBottom: 14, color: vencido || quedan <= 60 ? 'var(--color-accent-700)' : 'inherit' }}>
          {vencido ? 'Pasaron las 72 horas: el análisis se cancela.' : 'Tiempo para responder: ' + plazoRestante(quedan)}
        </div>

        <RecuadroEditable src={img} ancho={p.ancho} alto={p.alto} recuadro={recuadro} onChange={setRecuadro}
          otros={p.recuadros.filter((x) => x.cilindro !== p.cilindro)} etiqueta={'Recuadro del cilindro ' + p.cilindro}
          sinImagen={img ? null : 'Aquí aparecerá la imagen del instante en que el sistema dejó de ver al espécimen. Todavía no está disponible en el modo demo. Los recuadros sí se pueden ajustar.'} />

        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 10, marginTop: 14, flexWrap: 'wrap' }}>
          {CAMPOS.map(([k, label]) => (
            <div key={k} className="field" style={{ margin: 0, width: 110 }}>
              <label htmlFor={'r' + k}>{label} (px)</label>
              <input className="input num" id={'r' + k} type="number" value={recuadro[k]} onChange={setCampo(k)} />
            </div>
          ))}
          <span className="hint" style={{ marginLeft: 6, marginBottom: 10 }}>Píxeles de la imagen ({p.ancho} × {p.alto}). Con el foco en el recuadro: flechas para moverlo, Alt + flechas para el tamaño.</span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 20 }}>
          <button type="button" className="btn btn-primary" style={btnLeft} disabled={enviando || vencido} onClick={ajustar}>Ajustar y continuar</button>
          <button type="button" className="btn btn-secondary" style={btnLeft} disabled={enviando || vencido} onClick={cancelar}>Cancelar análisis</button>
          <div style={{ flex: 1 }} />
          <Link to="/analisis" style={{ fontSize: 12.5 }}>Volver a Análisis en curso</Link>
        </div>
        <FieldError msg={errAccion} style={{ marginTop: 10 }} />
        <p className="hint" style={{ marginTop: 10 }}>
          {vencido
            ? 'El análisis ya se canceló: pasaron las 72 horas sin respuesta y el sistema lo cerró. No se guardó ningún resultado de esta tanda; para analizarlo otra vez, sube el video de nuevo.'
            : 'Si pasan 72 horas sin respuesta, el sistema cancela el análisis. Solo quien subió el video o un Administrador puede ajustar o cancelar.'}
        </p>
      </div>
    </div>
  )
}

// La pausa ya se atendió (o venció), o la cuenta no puede atenderla.
function NoEnPausa({ error }) {
  const sinPermiso = error.response?.status === 403
  return (
    <div className="app narrow">
      <Topbar><Brand sub="Análisis en pausa" /></Topbar>
      <div className="page">
        <h3 style={{ margin: '0 0 8px' }}>{sinPermiso ? 'No puedes atender esta pausa' : 'Este análisis ya no está en pausa'}</h3>
        <p className="lead" style={{ marginBottom: 16, maxWidth: 560 }}>
          {sinPermiso
            ? 'Solo quien subió el video o un Administrador puede ajustar el recuadro o cancelar el análisis.'
            : 'Ya se ajustó el recuadro, se canceló o pasaron las 72 horas sin respuesta. Su estado está en Análisis en curso.'}
        </p>
        <Link className="btn btn-primary" to="/analisis">Ver análisis en curso</Link>
      </div>
    </div>
  )
}
