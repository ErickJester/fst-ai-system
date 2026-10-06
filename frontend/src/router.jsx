import React, { useEffect } from 'react'
import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './contexts/AuthContext'

import LoginPage from './pages/LoginPage'
import PrimerAccesoPage from './pages/PrimerAccesoPage'
import RestablecerPage from './pages/RestablecerPage'
import PerfilPage from './pages/PerfilPage'
import ExperimentosPage from './pages/ExperimentosPage'
import ExperimentoPage from './pages/ExperimentoPage'
import NuevoExperimentoPage from './pages/NuevoExperimentoPage'
import GrupoPage from './pages/GrupoPage'
import CargarVideoPage from './pages/CargarVideoPage'
import ProgresoPage from './pages/ProgresoPage'
import ResultadosPage from './pages/ResultadosPage'
import AdminPage from './pages/AdminPage'

// Sin sesión → login. Con contraseña temporal → solo primer acceso (2i).
function Protegida({ children, admin }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (user.temporal) return <Navigate to="/primer-acceso" replace />
  if (admin && !user.admin) return <Navigate to="/experimentos" replace />
  return children
}

// Pantalla de inicio de cada cuenta: sin sesión, el login; con temporal, el primer acceso.
function destino(user) {
  if (!user) return '/login'
  return user.temporal ? '/primer-acceso' : '/experimentos'
}

// Con la sesión abierta, el login no se vuelve a mostrar.
function SinSesion({ children }) {
  const { user } = useAuth()
  if (user) return <Navigate to={destino(user)} replace />
  return children
}

// Rutas que no existen (o «/»): a la pantalla de inicio de la cuenta.
function Inicio() {
  const { user } = useAuth()
  return <Navigate to={destino(user)} replace />
}

function SoloTemporal({ children }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (!user.temporal) return <Navigate to="/experimentos" replace />
  return children
}

// Cada pantalla empieza arriba, como al abrir un mockup.
function ScrollArriba() {
  const { pathname } = useLocation()
  useEffect(() => { window.scrollTo(0, 0) }, [pathname])
  return null
}

const p = (el, admin) => <Protegida admin={admin}>{el}</Protegida>

export default function AppRouter() {
  return (
    <>
      <ScrollArriba />
      <Routes>
        <Route path="/login" element={<SinSesion><LoginPage /></SinSesion>} />
        <Route path="/restablecer" element={<RestablecerPage />} />
        <Route path="/primer-acceso" element={<SoloTemporal><PrimerAccesoPage /></SoloTemporal>} />

        <Route path="/experimentos" element={p(<ExperimentosPage />)} />
        <Route path="/experimentos/nuevo" element={p(<NuevoExperimentoPage />)} />
        <Route path="/experimentos/:clave" element={p(<ExperimentoPage />)} />
        <Route path="/experimentos/:clave/grupos/:gid" element={p(<GrupoPage />)} />
        <Route path="/experimentos/:clave/cargar" element={p(<CargarVideoPage />)} />
        <Route path="/experimentos/:clave/resultados" element={p(<ResultadosPage />)} />
        <Route path="/analisis" element={p(<ProgresoPage />)} />
        <Route path="/perfil" element={p(<PerfilPage />)} />
        <Route path="/admin" element={p(<AdminPage />, true)} />

        <Route path="*" element={<Inicio />} />
      </Routes>
    </>
  )
}
