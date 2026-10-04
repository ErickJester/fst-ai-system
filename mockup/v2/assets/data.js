/* Datos del experimento de ejemplo (Compuesto CSR-14 · curva de dosis),
   tal como aparecen en el mockup v2. */
(function () {
  'use strict';
  const INK = 'var(--color-text)', ACC = 'var(--color-accent)';

  const TIPO_TAG = { control: 'tag-neutral', referencia: 'tag-outline', 'tratamiento experimental': 'tag-accent' };

  const ESTADO = {
    'En cola': { cls: 'tag-outline', bg: 'transparent', fg: INK, fill: 'var(--color-accent-200)' },
    'Procesando': { cls: 'tag-accent', bg: 'var(--color-accent-100)', fg: 'var(--color-accent-700)', fill: ACC },
    'Completado': { cls: 'tag-neutral', bg: 'var(--color-neutral-200)', fg: INK, fill: INK },
    'Error': { cls: 'tag-accent', bg: 'var(--color-accent-800)', fg: 'var(--color-bg)', fill: 'repeating-linear-gradient(135deg, var(--color-accent-800) 0 3px, var(--color-bg) 3px 6px)' }
  };

  const RAW = [
    { id: 'G-01', nombre: 'Control', tipo: 'control', trat: 'Placebo (solución salina)', n: 8, porCuadro: 4, estados: ['Completado', 'En cola'] },
    { id: 'G-02', nombre: 'Referencia', tipo: 'referencia', trat: 'Fluoxetina 10 mg/kg', n: 8, porCuadro: 4, estados: ['Completado', 'Completado'] },
    { id: 'G-03', nombre: 'Experimental A', tipo: 'tratamiento experimental', trat: 'Compuesto CSR-14, 5 mg/kg', n: 8, porCuadro: 4, estados: ['Completado', 'Procesando'] },
    { id: 'G-04', nombre: 'Experimental B', tipo: 'tratamiento experimental', trat: 'Compuesto CSR-14, 15 mg/kg', n: 8, porCuadro: 4, estados: ['En cola', 'Error'] }
  ];

  const groups = RAW.map(g => {
    const nt = Math.ceil(g.n / g.porCuadro);
    const reparto = [];
    for (let i = 0; i < nt; i++) reparto.push(i === nt - 1 ? g.n - g.porCuadro * (nt - 1) : g.porCuadro);
    const tandas = reparto.map((cuantos, i) => {
      const desde = reparto.slice(0, i).reduce((a, b) => a + b, 0) + 1;
      const est = g.estados[i];
      return {
        letra: String.fromCharCode(65 + i),
        nombre: 'Tanda ' + String.fromCharCode(65 + i),
        desde: desde, cuantos: cuantos,
        posiciones: 'Cilindros P1–P' + cuantos,
        estado: est, e: ESTADO[est]
      };
    });
    return Object.assign({}, g, { tipoTag: TIPO_TAG[g.tipo], reparto: reparto, tandas: tandas });
  });

  window.FST_DATA = {
    experimento: { clave: 'EXP-2026-02', titulo: 'Compuesto CSR-14 · curva de dosis', inicio: '18 feb 2026', responsable: 'M. Rivera' },
    groups: groups, ESTADO: ESTADO, TIPO_TAG: TIPO_TAG
  };
})();
