import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { AuthProvider } from './contexts/AuthContext'
import { ModoProvider } from './contexts/ModoContext'
import App from './App'

// Sistema de diseño de los mockups v2 (Modernist + capa FST) y piezas por pantalla.
// Fuente Archivo incluida en la app (antes venía de Google Fonts).
import '@fontsource/archivo/400.css'
import '@fontsource/archivo/600.css'
import '@fontsource/archivo/800.css'
import './styles/modernist.css'
import './styles/fst.css'
import './styles/app.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <ModoProvider>
        <AuthProvider>
          <App />
        </AuthProvider>
      </ModoProvider>
    </BrowserRouter>
  </React.StrictMode>
)
