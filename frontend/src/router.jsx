import React from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from './contexts/AuthContext'

import MainLayout from './components/layout/MainLayout'
import AdminLayout from './components/layout/AdminLayout'

import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import NewExperimentPage from './pages/NewExperimentPage'
import ProgressPage from './pages/ProgressPage'
import ResultsPage from './pages/ResultsPage'
import AdminPage from './pages/AdminPage'

function ProtectedRoute({ children, role }) {
  const { user } = useAuth()

  if (!user) {
    return <Navigate to="/login" replace />
  }

  if (role && user.role !== role) {
    return <Navigate to="/dashboard" replace />
  }

  return children
}

export default function AppRouter() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route element={<ProtectedRoute><MainLayout /></ProtectedRoute>}>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/experiments/new" element={<NewExperimentPage />} />
        <Route path="/experiments/:id/progress" element={<ProgressPage />} />
        <Route path="/experiments/:id/results" element={<ResultsPage />} />
      </Route>

      <Route element={<ProtectedRoute role="admin"><AdminLayout /></ProtectedRoute>}>
        <Route path="/admin" element={<AdminPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  )
}
