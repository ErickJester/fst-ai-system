import React from 'react'
import { Link } from 'react-router-dom'

// Ruta de migas de la barra superior. items: [{ label, to }]; el último es la página actual.
export default function Breadcrumbs({ items }) {
  return (
    <nav className="crumbs" aria-label="Ruta">
      {items.map((it, i) => {
        const last = i === items.length - 1
        return (
          <React.Fragment key={i}>
            {i > 0 && ' / '}
            {last || !it.to
              ? <span className={last ? 'here' : undefined} aria-current={last ? 'page' : undefined}>{it.label}</span>
              : <Link to={it.to}>{it.label}</Link>}
          </React.Fragment>
        )
      })}
    </nav>
  )
}
