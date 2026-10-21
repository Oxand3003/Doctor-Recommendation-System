/**
 * Navbar: menú móvil, sombra al hacer scroll, enlace activo e indicador que se desliza entre enlaces.
 */
document.addEventListener('DOMContentLoaded', function () {
  'use strict';

  document.querySelectorAll('body > nav').forEach(function (nav) {
    var toggle = nav.querySelector('.nav-toggle');
    var links = nav.querySelector('.nav-links');
    if (!links) return;

    // ---------- Menú móvil ----------
    function setOpen(open) {
      nav.classList.toggle('nav-open', open);
      if (toggle) toggle.setAttribute('aria-expanded', String(open));
    }
    if (toggle) {
      toggle.addEventListener('click', function () { setOpen(!nav.classList.contains('nav-open')); });
      links.querySelectorAll('a').forEach(function (a) { a.addEventListener('click', function () { setOpen(false); }); });
      document.addEventListener('keydown', function (e) { if (e.key === 'Escape') setOpen(false); });
      document.addEventListener('click', function (e) { if (!nav.contains(e.target)) setOpen(false); });
    }

    // ---------- Sombra al hacer scroll ----------
    function onScroll() { nav.classList.toggle('is-scrolled', window.scrollY > 8); }
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });

    // ---------- Enlace activo ----------
    var plain = Array.prototype.slice.call(links.querySelectorAll(':scope > a:not(.btn)'));
    var here = location.pathname.replace(/\/+$/, '') || '/';
    function path(a) { return a.pathname.replace(/\/+$/, '') || '/'; }
    var exact = plain.filter(function (a) { return path(a) === here && a.hash && a.hash === location.hash; });
    var samePage = plain.filter(function (a) { return path(a) === here && !a.hash; });
    var active = exact[0] || samePage[0] || null;
    if (active) { active.classList.add('active'); active.setAttribute('aria-current', 'page'); }

    // ---------- Indicador deslizante (escritorio) ----------
    var bar = document.createElement('span');
    bar.className = 'nav-indicator';
    bar.setAttribute('aria-hidden', 'true');
    links.insertBefore(bar, links.firstChild);

    function moveTo(a) {
      if (!a || window.matchMedia('(max-width:860px)').matches) { bar.classList.remove('is-on'); return; }
      bar.style.width = a.offsetWidth + 'px';
      bar.style.transform = 'translate3d(' + a.offsetLeft + 'px,-50%,0)';
      bar.classList.add('is-on');
    }
    function rest() { moveTo(active); }

    plain.forEach(function (a) {
      ['mouseenter', 'focus'].forEach(function (ev) {
        a.addEventListener(ev, function () {
          if (!bar.classList.contains('is-on')) { // aparece en su sitio, sin recorrer la barra
            bar.style.transition = 'none';
            moveTo(a);
            void bar.offsetWidth;
            bar.style.transition = '';
          } else moveTo(a);
        });
      });
      ['mouseleave', 'blur'].forEach(function (ev) { a.addEventListener(ev, rest); });
    });
    links.addEventListener('mouseleave', rest);

    if (active) { bar.style.transition = 'none'; rest(); void bar.offsetWidth; bar.style.transition = ''; }
    window.addEventListener('resize', rest);
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(rest);
  });
});
