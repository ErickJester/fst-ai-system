import { describe, it, expect } from 'vitest'
import { mean, variance, fmt, r1, fechaCorta, fechaRango, norm, esCorreo, primeraTandaLista, enlaceResultados, enHoraLocal, pctDeEtapa, PESO_ETAPA, mmss, plazoRestante, segundosDesde, nombreEquipo, tiempoDeEtapa, textoVideo, nombreDeContentDisposition, nombreDeConductaBD, NOMBRE_CONDUCTA } from './fst'

describe('plazoRestante: lo que queda de las 72 h de la pausa', () => {
  it('en horas y minutos; con menos de una hora solo minutos', () => {
    expect(plazoRestante(72 * 3600)).toBe('72 h 0 min')
    expect(plazoRestante(72 * 3600 - 90)).toBe('71 h 58 min') // los segundos sueltos no se muestran
    expect(plazoRestante(3600)).toBe('1 h 0 min')
    expect(plazoRestante(59 * 60 + 30)).toBe('59 min')
    expect(plazoRestante(60)).toBe('1 min')
  })

  it('con menos de un minuto, vencido o negativo: «menos de 1 min»', () => {
    expect(plazoRestante(59)).toBe('menos de 1 min')
    expect(plazoRestante(0)).toBe('menos de 1 min')
    expect(plazoRestante(-5)).toBe('menos de 1 min')
  })
})

describe('tiempos del análisis', () => {
  it('mmss: m:ss y, pasada la hora, h:mm:ss', () => {
    expect(mmss(0)).toBe('0:00')
    expect(mmss(59.9)).toBe('0:59')
    expect(mmss(65)).toBe('1:05')
    expect(mmss(1205)).toBe('20:05')
    expect(mmss(3599)).toBe('59:59')
    expect(mmss(3600)).toBe('1:00:00')
    expect(mmss(3725)).toBe('1:02:05')
  })

  it('el nombre de una descarga sale de Content-Disposition', () => {
    expect(nombreDeContentDisposition('attachment; filename="acu-yintang2_cnnseg.csv"')).toBe('acu-yintang2_cnnseg.csv')
    expect(nombreDeContentDisposition("attachment; filename*=UTF-8''Prueba%20%C3%B1and%C3%BA_cnnseg.csv")).toBe('Prueba ñandú_cnnseg.csv')
    expect(nombreDeContentDisposition('attachment')).toBeNull()
    expect(nombreDeContentDisposition(undefined)).toBeNull()
  })

  it('el video: su nombre como lo subió el usuario o «nombre no registrado»', () => {
    expect(textoVideo('acu-yintang2.mp4')).toBe('Video: acu-yintang2.mp4')
    expect(textoVideo('Prueba ñandú 25s.MP4')).toBe('Video: Prueba ñandú 25s.MP4')
    expect(textoVideo(null)).toBe('Video: nombre no registrado')
    expect(textoVideo(undefined)).toBe('Video: nombre no registrado')
    expect(textoVideo('')).toBe('Video: nombre no registrado')
  })

  it('segundosDesde nunca es negativo (relojes distintos)', () => {
    const inicio = '2026-10-19T12:00:00-06:00'
    const ms = Date.parse(inicio)
    expect(segundosDesde(inicio, ms + 90_500)).toBe(90)
    expect(segundosDesde(inicio, ms - 3000)).toBe(0)
  })

  it('el equipo es su nombre o, si no tiene, el CPU', () => {
    expect(nombreEquipo({ nombre: 'Laptop Erick', cpu: 'Intel' })).toBe('Laptop Erick')
    expect(nombreEquipo({ nombre: null, cpu: 'Intel i7' })).toBe('Intel i7')
    expect(nombreEquipo(null)).toBeNull()
  })

  it('tiempo de una etapa: hecha fijo, en curso cronometrada, pendiente sin dato', () => {
    const ms = Date.parse('2026-10-19T12:10:00-06:00')
    const j = { tiempos: { etapas: {
      PREPROCESSING: { inicio: '2026-10-19T12:00:00-06:00', fin: '2026-10-19T12:00:01-06:00', segundos: 1 },
      ROI_DETECTION: { inicio: '2026-10-19T12:00:01-06:00', fin: null, segundos: null },
    } } }
    expect(tiempoDeEtapa(j, 'PREPROCESSING', ms)).toBe(1)
    expect(tiempoDeEtapa(j, 'ROI_DETECTION', ms)).toBe(599)
    expect(tiempoDeEtapa(j, 'CLASSIFICATION', ms)).toBeNull()
    expect(tiempoDeEtapa({}, 'PREPROCESSING', ms)).toBeNull()
  })
})

describe('porcentaje de cada etapa en Progreso', () => {
  const ETAPAS = ['PREPROCESSING', 'ROI_DETECTION', 'CLASSIFICATION']

  it('hecha 100, en curso el del backend, pendiente 0', () => {
    const j = { stage: 'ROI_DETECTION', stage_pct: 41 }
    expect(ETAPAS.map((e) => pctDeEtapa(j, e, ETAPAS))).toEqual([100, 41, 0])
  })

  it('en curso sin porcentaje de etapa (backend viejo) usa el de la etapa', () => {
    expect(pctDeEtapa({ stage: 'ROI_DETECTION', stage_pct: null }, 'ROI_DETECTION', ETAPAS)).toBe(67)
  })

  it('los pesos de la barra general suman 100', () => {
    expect(Object.values(PESO_ETAPA).reduce((a, b) => a + b, 0)).toBe(100)
  })
})

describe('estadísticos y formato', () => {
  it('media y varianza muestral (n − 1), como en Resultados', () => {
    const inmovilidad = [88, 104, 76, 118]
    expect(mean(inmovilidad)).toBe(96.5)
    expect(variance(inmovilidad)).toBe(337)
    expect(r1(Math.sqrt(variance(inmovilidad)))).toBe('18.4')
  })

  it('segundos a min:seg', () => {
    expect(fmt(0)).toBe('0:00')
    expect(fmt(171)).toBe('2:51')
    expect(fmt(300)).toBe('5:00')
  })
})

describe('fechas', () => {
  it('una fecha con hora y zona del backend se lee en la hora local', () => {
    process.env.TZ = 'America/Mexico_City'
    expect(fechaCorta('2026-10-19T19:22:13.854102Z')).toBe('19 oct 2026')
    expect(enHoraLocal('2026-10-20T03:00:00Z')).toBe('2026-10-19T21:00') // en México todavía es el 19
    expect(enHoraLocal('2026-02-24T15:02:00')).toBe('2026-02-24T15:02:00') // sin zona, igual
    expect(fechaCorta('2026-02-18')).toBe('18 feb 2026')
  })

  it('fecha corta sin depender de la zona horaria', () => {
    expect(fechaCorta('2026-02-18')).toBe('18 feb 2026')
    expect(fechaCorta('2025-12-01')).toBe('1 dic 2025')
  })

  it('rango del mismo mes, de meses distintos y del mismo día', () => {
    expect(fechaRango('2026-02-18', '2026-02-19')).toBe('18–19 feb 2026')
    expect(fechaRango('2026-02-28', '2026-03-01')).toBe('28 feb 2026 – 1 mar 2026')
    expect(fechaRango('2026-10-04', '2026-10-04')).toBe('4 oct 2026')
  })
})

describe('texto', () => {
  it('búsqueda sin acentos ni mayúsculas', () => {
    expect(norm('Valeriána')).toBe(norm('valeriana'))
  })

  it('correos de cualquier dominio (D-46)', () => {
    expect(esCorreo('mrivera@ipn.mx')).toBe(true)
    expect(esCorreo('  MRivera@IPN.MX ')).toBe(true)
    expect(esCorreo('alguien@gmail.com')).toBe(true)
    expect(esCorreo('sin arroba')).toBe(false)
    expect(esCorreo('sin@dominio')).toBe(false)
  })
})

describe('resultados', () => {
  it('la primera tanda con el Día 2 analizado, o null si no hay', () => {
    const exp = { grupos: [
      { id: 'G-01', tandas: [{ letra: 'A', estado: 'QUEUED' }] },
      { id: 'G-02', tandas: [{ letra: 'A', estado: 'FAILED' }, { letra: 'B', estado: 'DONE' }] },
    ] }
    expect(primeraTandaLista(exp)).toEqual({ gid: 'G-02', letra: 'B' })
    expect(primeraTandaLista({ grupos: [] })).toBeNull()
    expect(enlaceResultados('EXP-2026-02', 'G-02', 'B')).toBe('/experimentos/EXP-2026-02/resultados?grupo=G-02&tanda=B')
  })
})

describe('nombres de las conductas en pantalla', () => {
  it('lo que guarda la base («nado activo») sale con su nombre de pantalla, igual que en Resultados y Revisión', () => {
    expect(nombreDeConductaBD('nado activo')).toBe('Nado activo')
    expect(nombreDeConductaBD('inmovilidad')).toBe('Inmovilidad')
    expect(nombreDeConductaBD('escalamiento')).toBe('Escalamiento')
    expect(nombreDeConductaBD('conducta activa')).toBe('Conducta activa')
    expect(Object.values(NOMBRE_CONDUCTA)).toEqual(['Nado activo', 'Inmovilidad', 'Escalamiento', 'Conducta activa'])
  })

  it('un nombre que no conoce se muestra tal cual, sin romper la pantalla', () => {
    expect(nombreDeConductaBD('nadoactivo')).toBe('nadoactivo')
  })
})
