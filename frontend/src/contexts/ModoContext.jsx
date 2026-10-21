import React, { createContext, useContext, useEffect, useState } from 'react'
import { getModo } from '../services/modo'

// Modo desarrollador y versión, para mostrar u ocultar las herramientas de desarrollo (cronómetro y tiempos de cada
// análisis, banner BETA). Se pide al backend UNA vez al cargar la app. Sin proveedor (por ejemplo en una prueba de una
// pantalla suelta) el modo está apagado.
export const ModoContext = createContext({ modoDesarrollador: false, version: null })

export function ModoProvider({ children }) {
  const [modo, setModo] = useState({ modoDesarrollador: false, version: null })
  useEffect(() => {
    let vivo = true
    getModo().then((m) => { if (vivo) setModo(m) })
    return () => { vivo = false }
  }, [])
  return <ModoContext.Provider value={modo}>{children}</ModoContext.Provider>
}

export const useModo = () => useContext(ModoContext)
