import React from 'react'
import { useLocation } from 'react-router-dom'
import AppRouter from './router'
import DemoBanner from './components/DemoBanner'
import ErrorBoundary from './components/ErrorBoundary'

export default function App() {
  const { pathname } = useLocation()
  return (
    <>
      <DemoBanner />
      {/* Región principal: los lectores de pantalla saltan directo al contenido. */}
      <main>
        <ErrorBoundary key={pathname}>
          <AppRouter />
        </ErrorBoundary>
      </main>
    </>
  )
}
