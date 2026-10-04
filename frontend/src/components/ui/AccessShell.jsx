import React from 'react'

// Contenedor de las pantallas de acceso (2h, 2i): tarjeta centrada sin barra superior.
export default function AccessShell({ header = 'full', children }) {
  return (
    <div className="acc-body">
      <div className="acc-card">
        {header === 'full' && (
          <div className="acc-head">
            <div className="nd" style={{ fontSize: 24 }}>FST</div>
            <div style={{ fontSize: 11, lineHeight: 1.5, marginTop: 4, color: 'color-mix(in srgb,#fff 78%,transparent)' }}>
              Análisis de la prueba de nado forzado (<em>Forced Swim Test</em>, FST)<br />
              Laboratorio de Bioquímica Estructural · ENMyH-IPN
            </div>
          </div>
        )}
        {header === 'brand' && (
          <div className="acc-head"><div className="nd" style={{ fontSize: 24 }}>FST</div></div>
        )}
        <div style={{ padding: '30px 34px 32px' }}>{children}</div>
      </div>
    </div>
  )
}
