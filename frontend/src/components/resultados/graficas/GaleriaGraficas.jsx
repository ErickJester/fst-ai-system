import React, { useEffect, useId, useState } from 'react'
import G1BarrasGrupos from './G1BarrasGrupos'
import G2CurvaPorMinuto from './G2CurvaPorMinuto'
import G3BarrasPorMinuto from './G3BarrasPorMinuto'
import G4ApiladaPorGrupo from './G4ApiladaPorGrupo'
import G5Variabilidad from './G5Variabilidad'
import G6MapaPorMinuto from './G6MapaPorMinuto'

// La galería de gráficas (M1A): una grande por defecto, selector, flechas (botones y teclado) y «Ver varias».
const GRAFICAS = [
  { id: 'g1', nombre: '¿Cuánto hizo cada grupo?', disponible: true },
  { id: 'g2', nombre: '¿Cómo cambió minuto a minuto?', disponible: true, Componente: G2CurvaPorMinuto },
  { id: 'g3', nombre: '¿Cómo se compara cada minuto?', disponible: true, Componente: G3BarrasPorMinuto },
  { id: 'g4', nombre: '¿En qué se fue el tiempo?', disponible: true, Componente: G4ApiladaPorGrupo },
  { id: 'g5', nombre: '¿Qué tan parecidos son los animales?', disponible: true, Componente: G5Variabilidad },
  { id: 'g6', nombre: '¿En qué minuto cambia?', disponible: true, Componente: G6MapaPorMinuto },
]
const HABILITADAS = GRAFICAS.filter((g) => g.disponible).map((g) => g.id)

// Punto de corte de la maquetación de resultados (app.css, .rejilla-lateral): por debajo, una gráfica a la vez.
const ANCHO_ANGOSTO = '(max-width: 1100px)'
const CLAVE_MEMORIA = 'fst.resultados.graficas'
const ESTADO_INICIAL = { vista: 'g1', varias: false, marcadas: ['g1'], conducta: 'inmovilidad', puntos: true }

// La elección se recuerda en el navegador. Si no hay almacenamiento o el valor no sirve, sale el inicial.
function leerMemoria() {
  try {
    const guardado = JSON.parse(localStorage.getItem(CLAVE_MEMORIA) || 'null')
    if (!guardado || typeof guardado !== 'object') return ESTADO_INICIAL
    return {
      ...ESTADO_INICIAL,
      vista: HABILITADAS.includes(guardado.vista) ? guardado.vista : ESTADO_INICIAL.vista,
      varias: typeof guardado.varias === 'boolean' ? guardado.varias : ESTADO_INICIAL.varias,
      marcadas: Array.isArray(guardado.marcadas) ? guardado.marcadas.filter((id) => HABILITADAS.includes(id)) : ESTADO_INICIAL.marcadas,
      conducta: typeof guardado.conducta === 'string' ? guardado.conducta : ESTADO_INICIAL.conducta,
      puntos: typeof guardado.puntos === 'boolean' ? guardado.puntos : ESTADO_INICIAL.puntos,
    }
  } catch {
    return ESTADO_INICIAL
  }
}

function guardarMemoria(estado) {
  try {
    localStorage.setItem(CLAVE_MEMORIA, JSON.stringify(estado))
  } catch {
    // Sin almacenamiento la elección solo dura esta visita; todo lo demás funciona igual.
  }
}

const esAngosta = () => typeof window !== 'undefined' && !!window.matchMedia?.(ANCHO_ANGOSTO).matches

// Las teclas ← → cambian de gráfica, salvo cuando el foco está en un campo de texto o de selección.
const enCampo = (elemento) => elemento instanceof HTMLElement && (['INPUT', 'SELECT', 'TEXTAREA'].includes(elemento.tagName) || elemento.isContentEditable)

export default function GaleriaGraficas({ datos }) {
  const prefijo = useId().replace(/[^a-zA-Z0-9]/g, '')
  const [estado, setEstado] = useState(leerMemoria)
  const [angosta, setAngosta] = useState(esAngosta)
  const maximo = angosta ? 1 : 3

  useEffect(() => {
    const medio = window.matchMedia?.(ANCHO_ANGOSTO)
    if (!medio) return undefined
    const cambio = () => setAngosta(medio.matches)
    medio.addEventListener('change', cambio)
    return () => medio.removeEventListener('change', cambio)
  }, [])

  useEffect(() => {
    guardarMemoria(estado)
  }, [estado])

  // Las flechas (botones y teclado) dan la vuelta: después de la última, la primera, y al revés.
  const mover = (direccion) => setEstado((s) => {
    const n = HABILITADAS.length
    const j = (HABILITADAS.indexOf(s.vista) + direccion + n) % n
    return { ...s, vista: HABILITADAS[j] }
  })

  useEffect(() => {
    function alTeclear(e) {
      if ((e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') || enCampo(e.target)) return
      mover(e.key === 'ArrowRight' ? 1 : -1)
    }
    window.addEventListener('keydown', alTeclear)
    return () => window.removeEventListener('keydown', alTeclear)
  }, [])

  const cambiar = (cambios) => setEstado((s) => ({ ...s, ...cambios }))

  // En «Ver varias» se marca cada gráfica; no se pasa del máximo del ancho de la pantalla.
  function alternarMarca(id) {
    setEstado((s) => {
      if (s.marcadas.includes(id)) return { ...s, marcadas: s.marcadas.filter((x) => x !== id) }
      if (s.marcadas.length >= maximo) return s
      return { ...s, marcadas: [...s.marcadas, id] }
    })
  }

  const visibles = estado.varias ? HABILITADAS.filter((id) => estado.marcadas.includes(id)).slice(0, maximo) : [estado.vista]
  const sinGrupos = !datos.grupos.length

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18, minWidth: 0 }}>
      <div role="group" aria-label="Elegir gráfica" style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
        {GRAFICAS.map((g) => {
          const activa = estado.varias ? estado.marcadas.includes(g.id) : estado.vista === g.id
          return (
            <button key={g.id} type="button" className={'tab' + (activa ? ' on' : '')} disabled={!g.disponible}
              aria-pressed={g.disponible ? activa : undefined}
              onClick={() => (estado.varias ? alternarMarca(g.id) : cambiar({ vista: g.id }))}
              style={{ border: '1px solid var(--color-divider)', padding: '6px 12px', fontSize: 12.5 }}>
              {g.nombre}{!g.disponible && <span className="hint" style={{ marginLeft: 6 }}>Disponible pronto</span>}
            </button>
          )
        })}
      </div>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'center', justifyContent: 'space-between' }}>
        {!estado.varias ? (
          <div style={{ display: 'flex', gap: 8 }}>
            <button type="button" className="btn btn-secondary" aria-label="Gráfica anterior" disabled={HABILITADAS.length < 2}
              onClick={() => mover(-1)}>←</button>
            <button type="button" className="btn btn-secondary" aria-label="Gráfica siguiente" disabled={HABILITADAS.length < 2}
              onClick={() => mover(1)}>→</button>
          </div>
        ) : (
          <span className="hint">Marca las gráficas que quieres ver juntas (hasta {maximo}).</span>
        )}
        <label style={{ fontSize: 12.5, display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <input type="checkbox" checked={estado.varias} onChange={(e) => cambiar({ varias: e.target.checked })} /> Ver varias
        </label>
      </div>

      {sinGrupos && <p className="hint" role="status">Todavía no hay grupos con su Día 2 analizado.</p>}
      {estado.varias && !visibles.length && <p className="hint" role="status">Marca al menos una gráfica.</p>}

      {visibles.map((id) => {
        const Componente = GRAFICAS.find((g) => g.id === id).Componente
        return (
          <div key={id} style={{ borderTop: '1px solid var(--color-divider)', paddingTop: 18, minWidth: 0 }}>
            {id === 'g1' ? (
              <G1BarrasGrupos datos={datos} conducta={estado.conducta} setConducta={(conducta) => cambiar({ conducta })}
                puntos={estado.puntos} setPuntos={(puntos) => cambiar({ puntos })} prefijo={`${prefijo}-g1`} />
            ) : <Componente datos={datos} prefijo={`${prefijo}-${id}`} />}
          </div>
        )
      })}
    </div>
  )
}
