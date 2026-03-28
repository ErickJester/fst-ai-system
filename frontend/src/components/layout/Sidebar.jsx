import React from 'react'

const items = [
  {
    section: 'Principal',
    entries: [
      { key: 'sistema', label: 'Estado del sistema', icon: <svg width="15" height="15" viewBox="0 0 15 15" fill="none"><rect x="1.5" y="1.5" width="5" height="5" rx="1" stroke="currentColor" strokeWidth="1.1"/><rect x="8.5" y="1.5" width="5" height="5" rx="1" stroke="currentColor" strokeWidth="1.1"/><rect x="1.5" y="8.5" width="5" height="5" rx="1" stroke="currentColor" strokeWidth="1.1"/><rect x="8.5" y="8.5" width="5" height="5" rx="1" stroke="currentColor" strokeWidth="1.1"/></svg> },
      { key: 'usuarios', label: 'Usuarios', icon: <svg width="15" height="15" viewBox="0 0 15 15" fill="none"><circle cx="5.5" cy="5" r="2.5" stroke="currentColor" strokeWidth="1.1"/><path d="M1 13c0-2.5 2-4 4.5-4s4.5 1.5 4.5 4" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round"/></svg>, badge: '8' },
      { key: 'experimentos', label: 'Todos los experimentos', icon: <svg width="15" height="15" viewBox="0 0 15 15" fill="none"><path d="M5 1v5L1.5 12a1 1 0 00.9 1.5h10.2a1 1 0 00.9-1.5L10 6V1" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round" strokeLinejoin="round"/><path d="M4.5 1h6" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round"/></svg>, badge: '42' },
    ],
  },
  {
    section: 'Sistema',
    entries: [
      { key: 'logs', label: 'Logs del servidor', icon: <svg width="15" height="15" viewBox="0 0 15 15" fill="none"><rect x="2" y="1.5" width="11" height="12" rx="1.5" stroke="currentColor" strokeWidth="1.1"/><path d="M5 5h5M5 7.5h5M5 10h3" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round"/></svg> },
      { key: 'config', label: 'Configuración', icon: <svg width="15" height="15" viewBox="0 0 15 15" fill="none"><circle cx="7.5" cy="7.5" r="2" stroke="currentColor" strokeWidth="1.1"/><path d="M7.5 1.5v1.5M7.5 12v1.5M1.5 7.5H3M12 7.5h1.5M3.4 3.4l1 1M10.6 10.6l1 1M3.4 11.6l1-1M10.6 4.4l1-1" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round"/></svg> },
    ],
  },
]

export default function Sidebar({ activeKey, onSelect }) {
  return (
    <aside className="sidebar">
      {items.map((group, gi) => (
        <React.Fragment key={gi}>
          {gi > 0 && <div className="sdivider" />}
          <div className="sidebar-section">{group.section}</div>
          {group.entries.map((item) => (
            <div
              key={item.key}
              className={`sitem ${activeKey === item.key ? 'active' : ''}`}
              onClick={() => onSelect(item.key)}
            >
              {item.icon}
              {item.label}
              {item.badge && <span className="sitem-badge">{item.badge}</span>}
            </div>
          ))}
        </React.Fragment>
      ))}
    </aside>
  )
}
