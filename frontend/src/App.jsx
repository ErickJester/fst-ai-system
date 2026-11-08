import React from 'react'
import { useLocation } from 'react-router-dom'
import AppRouter from './router'
import BannerModoDesarrollador from './components/BannerModoDesarrollador'
import DemoBanner from './components/DemoBanner'
import ErrorBoundary from './components/ErrorBoundary'
import VersionEsquina from './components/VersionEsquina'

export default function App() {
  const { pathname } = useLocation()
  return (
    <>
      <BannerModoDesarrollador />
      <DemoBanner />
      {/* Región principal: los lectores de pantalla saltan directo al contenido. */}
      <main>
        <ErrorBoundary key={pathname}>
          <AppRouter />
        </ErrorBoundary>
      </main>
      <VersionEsquina />
    </>
  )
}
