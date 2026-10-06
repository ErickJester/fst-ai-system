import React from 'react'
import { Link } from 'react-router-dom'

// Si una pantalla falla al dibujarse, muestra un aviso en lugar de dejar la página en
// blanco. App le pone key={ruta}: al cambiar de pantalla vuelve a intentarlo.
export default class ErrorBoundary extends React.Component {
  state = { error: null }

  static getDerivedStateFromError(error) {
    return { error }
  }

  componentDidCatch(error, info) {
    console.error('Error en la pantalla:', error, info.componentStack)
  }

  render() {
    if (!this.state.error) return this.props.children
    return (
      <div className="app">
        <div className="page">
          <h3 style={{ margin: '0 0 8px' }}>Algo salió mal en esta pantalla</h3>
          <p className="lead" style={{ marginBottom: 16, maxWidth: 560 }}>
            No se perdió nada de lo guardado. Vuelve a experimentos o recarga la página; si se repite, avisa al equipo con el detalle de abajo.
          </p>
          <div style={{ display: 'flex', gap: 8, marginBottom: 18 }}>
            <Link className="btn btn-primary" to="/experimentos">Volver a experimentos</Link>
            <button type="button" className="btn btn-secondary" onClick={() => window.location.reload()}>Recargar</button>
          </div>
          <div className="num hint">Detalle: {String(this.state.error.message || this.state.error)}</div>
        </div>
      </div>
    )
  }
}
