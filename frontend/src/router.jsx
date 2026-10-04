import React from 'react'
import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './contexts/AuthContext'

import LoginPage from './pages/LoginPage'
import RecuperarPage from './pages/RecuperarPage'
import PrimerAccesoPage from './pages/PrimerAccesoPage'
import PerfilPage from './pages/PerfilPage'
import ExperimentosPage from './pages/ExperimentosPage'
import ExperimentoPage from './pages/ExperimentoPage'
import CargarVideoPage from './pages/CargarVideoPage'
import GrupoPage from './pages/GrupoPage'
import ResultadosPage from './pages/ResultadosPage'
import AnalisisPage from './pages/AnalisisPage'
import AdminPage from './pages/AdminPage'

// Exige sesión; con role="ADMIN" exige además el rol de administrador.
function ProtectedRoute({ children, role }) {
  const { user } = useAuth()
  const location = useLocation()

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (role && user.role !== role) {
    return <Navigate to="/experimentos" replace />
  }

  return children
}

const p = (el, role) => <ProtectedRoute role={role}>{el}</ProtectedRoute>

export default function AppRouter() {
  return (
    <Routes>
      {/* Acceso (2h, 2i) */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/recuperar" element={<RecuperarPage />} />
      <Route path="/primer-acceso" element={p(<PrimerAccesoPage />)} />

      {/* Investigador y administrador */}
      <Route path="/experimentos" element={p(<ExperimentosPage />)} />
      <Route path="/experimentos/:id" element={p(<ExperimentoPage />)} />
      <Route path="/experimentos/:id/cargar" element={p(<CargarVideoPage />)} />
      <Route path="/experimentos/:id/grupos/:gid" element={p(<GrupoPage />)} />
      <Route path="/experimentos/:id/grupos/:gid/tandas/:tid/resultados" element={p(<ResultadosPage />)} />
      <Route path="/analisis" element={p(<AnalisisPage />)} />
      <Route path="/perfil" element={p(<PerfilPage />)} />

      {/* Solo administrador (2g) */}
      <Route path="/admin" element={p(<AdminPage />, 'ADMIN')} />

      <Route path="*" element={<Navigate to="/experimentos" replace />} />
    </Routes>
  )
}
