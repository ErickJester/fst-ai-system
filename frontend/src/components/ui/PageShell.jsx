import React from 'react'
import Topbar from './Topbar'

// Contenedor de las pantallas con barra superior. width: 1240 | 1040 | 860 | 720 (anchos del v2).
export default function PageShell({ width = 1240, topbar = {}, padded = true, children }) {
  return (
    <div className={`app${width === 1240 ? '' : ` w-${width}`}`}>
      <Topbar {...topbar} />
      <main className={padded ? 'page' : undefined}>{children}</main>
    </div>
  )
}
