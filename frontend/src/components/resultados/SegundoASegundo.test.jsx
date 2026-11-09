// @vitest-environment jsdom
import React from 'react'
import { describe, it, expect, afterEach } from 'vitest'
import { render, screen, fireEvent, cleanup } from '@testing-library/react'
import axe from 'axe-core'
import SegundoASegundo from './SegundoASegundo'

// Datos fijos: un espécimen con siete segundos (uno sin ver, los cuatro orígenes conocidos, un origen
// desconocido y uno sin origen; falta el 4) y otro con dos.
const esp1 = {
  numeroCilindro: 1,
  segundos: [
    { s: 1, conducta: 'inmovilidad', origen: 'maquina' },
    { s: 2, conducta: null, origen: 'maquina' },
    { s: 3, conducta: 'escalamiento', origen: 'corregida' },
    { s: 5, conducta: 'nado activo', origen: 'confirmada' },
    { s: 6, conducta: 'nado activo', origen: 'otro-valor' },
    { s: 7, conducta: 'nado activo', origen: null },
    { s: 61, conducta: 'conducta activa', origen: 'dudosa' },
  ],
}
const esp2 = {
  numeroCilindro: 2,
  segundos: [
    { s: 1, conducta: 'escalamiento', origen: 'maquina' },
    { s: 2, conducta: 'nado activo', origen: 'maquina' },
  ],
}

const datos = { duracionS: 300, grupo: { id: 1, etiqueta: 'Control' }, letra: 'A', especimenes: [esp1, esp2] }

afterEach(() => {
  cleanup()
})

describe('SegundoASegundo', () => {
  it('cambio de espécimen: el selector muestra las filas del que se elige', () => {
    const { container } = render(<SegundoASegundo datos={datos} />)
    expect(container.querySelectorAll('tbody tr').length).toBe(7)
    const selector = screen.getByRole('combobox', { name: 'Espécimen' })
    expect(selector.value).toBe('0')
    expect(screen.getByRole('option', { name: 'Tanda A · Espécimen 2' })).toBeTruthy()
    fireEvent.change(selector, { target: { value: '1' } })
    expect(container.querySelectorAll('tbody tr').length).toBe(2)
    expect(container.querySelector('caption').textContent).toContain('espécimen 2')
  })

  it('un segundo con conducta null es «Sin ver»: en la tabla y con su borde en la franja', () => {
    const { container } = render(<SegundoASegundo datos={datos} />)
    expect(screen.getByRole('cell', { name: 'Sin ver' })).toBeTruthy()
    const rect = container.querySelector('[data-s="2"]')
    expect(rect.getAttribute('data-conducta')).toBe('sin ver')
    expect(rect.querySelector('rect[stroke]')).not.toBeNull()
  })

  it('cada origen dice su texto: maquina Sistema; confirmada, corregida y dudosa los de la revisión', () => {
    render(<SegundoASegundo datos={datos} />)
    expect(screen.getAllByRole('cell', { name: 'Sistema' }).length).toBe(2)
    expect(screen.getByRole('cell', { name: 'Confirmado en revisión' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: 'Corregido en revisión' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: 'Marcado como no se ve' })).toBeTruthy()
  })

  it('un origen desconocido o null se dice «Sin dato», nunca «Corregido»', () => {
    render(<SegundoASegundo datos={datos} />)
    expect(screen.getAllByRole('cell', { name: 'Sin dato' }).length).toBe(2)
    expect(screen.queryByText(/Corregido por revisión/)).toBeNull()
  })

  it('la leyenda de «Sin ver» lleva borde de texto, y el resto, el borde por defecto', () => {
    const { container } = render(<SegundoASegundo datos={datos} />)
    const muestras = [...container.querySelectorAll('div > span > svg')]
    const sinVer = muestras.find((s) => s.getAttribute('style')?.includes('var(--color-text)'))
    expect(sinVer).toBeTruthy()
    expect(muestras.length).toBe(5)
  })

  it('un segundo que no viene queda vacío en la franja y no aparece en la tabla', () => {
    const { container } = render(<SegundoASegundo datos={datos} />)
    expect(container.querySelector('[data-s="4"]')).toBeNull()
    expect(screen.getByRole('cell', { name: '0:02' })).toBeTruthy()
    expect(screen.queryByRole('cell', { name: '0:03' })).toBeNull()
    expect(screen.getByRole('cell', { name: '0:04' })).toBeTruthy()
  })

  it('el tiempo es m:ss desde el segundo 1 = 0:00, y pasado el minuto 1:00', () => {
    render(<SegundoASegundo datos={datos} />)
    expect(screen.getByRole('cell', { name: '0:00' })).toBeTruthy()
    expect(screen.getByRole('cell', { name: '1:00' })).toBeTruthy()
  })

  it('sin especímenes muestra el aviso y no dibuja nada', () => {
    const { container } = render(<SegundoASegundo datos={{ ...datos, especimenes: [] }} />)
    expect(screen.getByText('No hay segundos para mostrar.')).toBeTruthy()
    expect(container.querySelector('svg')).toBeNull()
    expect(container.querySelector('table')).toBeNull()
  })

  it('la caja de la tabla es enfocable y tiene desplazamiento vertical', () => {
    const { container } = render(<SegundoASegundo datos={datos} />)
    const caja = container.querySelector('[tabindex="0"]')
    expect(caja).not.toBeNull()
    expect(caja.style.maxHeight).toBe('360px')
    expect(caja.style.overflowY).toBe('auto')
  })

  it('sin fallos de axe-core', async () => {
    const { container } = render(<SegundoASegundo datos={datos} />)
    const resultado = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
    expect(resultado.violations.map((v) => v.id)).toEqual([])
  })
})
