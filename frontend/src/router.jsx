import React, { Suspense, lazy, useEffect } from 'react'
import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './contexts/AuthContext'

// La entrada (login y lista de experimentos) va en la carga inicial; las demás pantallas se
// descargan al abrirlas por primera vez, para que la app arranque más rápido.
import LoginPage from './pages/LoginPage'
import ExperimentosPage from './pages/ExperimentosPage'
const PrimerAccesoPage = lazy(() => import('./pages/PrimerAccesoPage'))
const RestablecerPage = lazy(() => import('./pages/RestablecerPage'))
const PerfilPage = lazy(() => import('./pages/PerfilPage'))
const ExperimentoPage = lazy(() => import('./pages/ExperimentoPage'))
const NuevoExperimentoPage = lazy(() => import('./pages/NuevoExperimentoPage'))
const GrupoPage = lazy(() => import('./pages/GrupoPage'))
const CargarVideoPage = lazy(() => import('./pages/CargarVideoPage'))
const ProgresoPage = lazy(() => import('./pages/ProgresoPage'))
const PausaPage = lazy(() => import('./pages/PausaPage'))
const RevisionPage = lazy(() => import('./pages/RevisionPage'))
const ResultadosPage = lazy(() => import('./pages/ResultadosPage'))
const AdminPage = lazy(() => import('./pages/AdminPage'))

// Mientras se descarga una pantalla. Sin barra superior: también la ven las pantallas públicas
// (restablecer contraseña, primer acceso), donde no hay sesión de la que leer la cuenta.
const Descargando = () => <div className="page"><p className="hint">Cargando…</p></div>

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
      <Suspense fallback={<Descargando />}>
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
        <Route path="/experimentos/:clave/analisis/:id/pausa" element={p(<PausaPage />)} />
        <Route path="/experimentos/:clave/analisis/:id/revision" element={p(<RevisionPage />)} />
        <Route path="/perfil" element={p(<PerfilPage />)} />
        <Route path="/admin" element={p(<AdminPage />, true)} />

        <Route path="*" element={<Inicio />} />
      </Routes>
      </Suspense>
    </>
  )
}
