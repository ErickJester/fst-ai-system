import React from 'react'

// Control segmentado. options: [{ value, label }]
export default function Seg({ options, value, onChange, ariaLabel, style }) {
  return (
    <span className="seg" role="group" aria-label={ariaLabel} style={{ display: 'inline-flex', ...style }}>
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          className={`seg-opt${o.value === value ? ' on' : ''}`}
          aria-pressed={o.value === value}
          onClick={() => onChange?.(o.value)}
        >
          {o.label}
        </button>
      ))}
    </span>
  )
}
