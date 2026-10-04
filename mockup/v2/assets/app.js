/* FST · lógica compartida de los mockups v2.
   Barra superior (menú de usuario y notificaciones, pantalla 2k), utilidades
   numéricas y descargas. Los datos son los del mockup; no hay backend. */
(function () {
  'use strict';

  // ── almacenamiento local seguro (puede no estar disponible) ──────────────
  const store = {
    get(k, d) { try { const v = localStorage.getItem('fst.' + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
    set(k, v) { try { localStorage.setItem('fst.' + k, JSON.stringify(v)); } catch (e) { /* sin almacenamiento */ } }
  };

  // ── utilidades de los estadísticos (idénticas al mockup) ────────────────
  const mean = a => a.reduce((x, y) => x + y, 0) / a.length;
  const variance = a => { const m = mean(a); return a.reduce((s, v) => s + (v - m) * (v - m), 0) / (a.length - 1); };
  const fmt = s => Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0');
  const r1 = n => (Math.round(n * 10) / 10).toFixed(1);
  const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  // ── usuarios de la barra superior ────────────────────────────────────────
  const USERS = {
    MR: { ini: 'MR', nombre: 'Mariana Rivera Alcántara', correo: 'mrivera@ipn.mx', rol: 'Investigador', admin: false },
    CR: { ini: 'CR', nombre: 'C. S. Reyes López', correo: 'creyesl@ipn.mx', rol: 'Administrador', admin: true }
  };

  // ── notificaciones (2k) ──────────────────────────────────────────────────
  const NOTIF_BASE = [
    { id: 'n1', titulo: 'Error en el análisis', texto: 'Compuesto CSR-14 · Experimental B · Tanda B · Día 2. Confianza de detección 0.54, menor a 0.70.', enlace: 'Ver detalle del error', href: 'progreso.html', hora: '15:41', leida: false },
    { id: 'n2', titulo: 'Análisis completado', texto: 'Compuesto CSR-14 · Referencia · Tanda A · Día 2.', enlace: 'Ver resultados', href: 'resultados.html', hora: '15:02', leida: false },
    { id: 'n3', titulo: 'Análisis completado', texto: 'Compuesto CSR-14 · Control · Tanda A · Día 2.', enlace: 'Ver resultados', href: 'resultados.html', hora: 'ayer', leida: true }
  ];
  function notifs() {
    const leidas = store.get('leidas', null);
    return NOTIF_BASE.map(n => Object.assign({}, n, { leida: leidas ? !!leidas[n.id] : n.leida }));
  }
  function saveNotifs(list) {
    const m = {}; list.forEach(n => { m[n.id] = n.leida; }); store.set('leidas', m);
  }

  const BELL = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="square" aria-hidden="true"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"></path><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"></path></svg>';

  function mountTopbar() {
    const nav = document.querySelector('.nav[data-user]');
    if (!nav) return;
    const user = USERS[nav.dataset.user] || USERS.MR;

    const bell = document.createElement('button');
    bell.className = 'tb-btn'; bell.type = 'button';
    bell.setAttribute('aria-label', 'Notificaciones'); bell.setAttribute('aria-expanded', 'false');
    const av = document.createElement('button');
    av.className = 'tb-btn'; av.type = 'button';
    av.setAttribute('aria-label', 'Menú de usuario'); av.setAttribute('aria-expanded', 'false');
    av.innerHTML = '<span class="avatar' + (user.admin ? ' admin' : '') + '">' + user.ini + '</span><span class="caret" style="font-size:10px">▾</span>';
    nav.appendChild(bell); nav.appendChild(av);

    const pNotif = document.createElement('div');
    pNotif.className = 'dropdown hidden'; pNotif.style.width = '460px';
    const pUser = document.createElement('div');
    pUser.className = 'dropdown hidden'; pUser.style.width = '230px';
    pUser.innerHTML =
      '<div style="padding:12px 14px;border-bottom:1px solid var(--color-divider)">' +
        '<div class="nd" style="font-size:13px">' + esc(user.nombre) + '</div>' +
        '<div class="num" style="font-size:11.5px;color:color-mix(in srgb,var(--color-text) 60%,transparent)">' + esc(user.correo) + ' · ' + esc(user.rol) + '</div>' +
      '</div>' +
      '<a class="menu-item" href="perfil.html" style="border-bottom:1px solid var(--color-divider)">Mi perfil</a>' +
      '<a class="menu-item" href="login.html">Cerrar sesión</a>';
    nav.appendChild(pNotif); nav.appendChild(pUser);

    function renderBell() {
      const unread = notifs().filter(n => !n.leida).length;
      bell.innerHTML = BELL + (unread ? '<span class="tb-dot"></span>' : '');
    }
    function renderNotif() {
      const list = notifs();
      const unread = list.filter(n => !n.leida).length;
      pNotif.innerHTML =
        '<div style="display:flex;align-items:center;gap:10px;padding:12px 14px;border-bottom:2px solid var(--color-divider)">' +
          '<span class="nd" style="font-size:14px">Notificaciones</span>' +
          (unread ? '<span class="tag tag-accent">' + unread + ' sin leer</span>' : '') +
          '<div style="flex:1"></div>' +
          '<button type="button" class="linkbtn" data-all style="font-size:12px">Marcar todas como leídas</button>' +
        '</div>' +
        list.map(n =>
          '<div class="notif" style="background:' + (n.leida ? 'var(--color-bg)' : 'var(--color-accent-100)') + '">' +
            '<span style="width:8px;height:8px;margin-top:5px;background:' + (n.leida ? 'transparent' : 'var(--color-accent)') + ';border:1px solid var(--color-divider)"></span>' +
            '<div>' +
              '<div class="nd" style="font-size:13px">' + esc(n.titulo) + '</div>' +
              '<div style="font-size:12px;line-height:1.5;margin-top:2px;color:color-mix(in srgb,var(--color-text) 70%,transparent)">' + esc(n.texto) + '</div>' +
              '<a href="' + n.href + '" data-open="' + n.id + '" style="display:inline-block;margin-top:5px">' + esc(n.enlace) + '</a>' +
            '</div>' +
            '<div style="display:flex;flex-direction:column;align-items:flex-end;gap:5px">' +
              '<span class="num" style="font-size:11px;white-space:nowrap;color:var(--muted)">' + esc(n.hora) + '</span>' +
              '<button type="button" class="linkbtn" data-toggle="' + n.id + '" style="font-size:11px;white-space:nowrap">' + (n.leida ? 'Marcar como no leída' : 'Marcar como leída') + '</button>' +
            '</div>' +
          '</div>').join('');
    }
    pNotif.addEventListener('click', e => {
      const t = e.target;
      let list = notifs();
      if (t.matches('[data-all]')) { list.forEach(n => { n.leida = true; }); }
      else if (t.matches('[data-toggle]')) { const n = list.find(x => x.id === t.dataset.toggle); n.leida = !n.leida; }
      else if (t.matches('[data-open]')) { const n = list.find(x => x.id === t.dataset.open); n.leida = true; saveNotifs(list); return; }
      else return;
      saveNotifs(list); renderNotif(); renderBell();
    });

    function open(which) {
      const isN = which === 'n', isU = which === 'u';
      pNotif.classList.toggle('hidden', !isN); bell.setAttribute('aria-expanded', String(isN));
      pUser.classList.toggle('hidden', !isU); av.setAttribute('aria-expanded', String(isU));
      av.querySelector('.caret').textContent = isU ? '▴' : '▾';
      if (isN) renderNotif();
    }
    bell.addEventListener('click', e => { e.stopPropagation(); open(pNotif.classList.contains('hidden') ? 'n' : null); });
    av.addEventListener('click', e => { e.stopPropagation(); open(pUser.classList.contains('hidden') ? 'u' : null); });
    [pNotif, pUser].forEach(p => p.addEventListener('click', e => e.stopPropagation()));
    document.addEventListener('click', () => open(null));
    document.addEventListener('keydown', e => { if (e.key === 'Escape') open(null); });
    renderBell();
  }

  // ── segmentados (.seg con .seg-opt) ──────────────────────────────────────
  function bindSeg(seg, onChange) {
    seg.addEventListener('click', e => {
      const o = e.target.closest('.seg-opt');
      if (!o || !seg.contains(o)) return;
      seg.querySelectorAll('.seg-opt').forEach(x => x.classList.toggle('on', x === o));
      if (onChange) onChange(o.dataset.v, o);
    });
  }
  function segValue(seg) { const o = seg.querySelector('.seg-opt.on'); return o ? o.dataset.v : null; }

  // ── descargas ────────────────────────────────────────────────────────────
  function download(name, text, type) {
    const blob = new Blob([text], { type: type || 'text/csv;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob); a.download = name;
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  }
  function toCSV(rows) {
    return '﻿' + rows.map(r => r.map(v => {
      const s = String(v);
      return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
    }).join(',')).join('\n');
  }

  window.FST = { store, mean, variance, fmt, r1, esc, bindSeg, segValue, download, toCSV, USERS };
  document.addEventListener('DOMContentLoaded', mountTopbar);
})();
