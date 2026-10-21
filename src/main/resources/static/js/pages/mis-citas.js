/* Mis citas (perfil del paciente): alterna entre "Pendientes" e "Historial". */
(function () {
  'use strict';

  var root = document.getElementById('my-appointments');
  if (!root) return;

  var tabs = Array.prototype.slice.call(root.querySelectorAll('[data-appt-tab]'));

  function show(name, focus) {
    tabs.forEach(function (tab) {
      var on = tab.dataset.apptTab === name;
      tab.classList.toggle('is-active', on);
      tab.setAttribute('aria-selected', on ? 'true' : 'false');
      tab.tabIndex = on ? 0 : -1;
      var panel = document.getElementById(tab.getAttribute('aria-controls'));
      if (panel) panel.hidden = !on;
      if (on && focus) tab.focus();
    });
  }

  tabs.forEach(function (tab, i) {
    tab.addEventListener('click', function () { show(tab.dataset.apptTab); });
    // Flechas izquierda/derecha entre pestañas (patrón de accesibilidad de tabs)
    tab.addEventListener('keydown', function (e) {
      if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return;
      var next = tabs[(i + (e.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length];
      show(next.dataset.apptTab, true);
    });
  });

  // Enlace directo al historial: /account#historial-citas
  if (window.location.hash === '#historial-citas') show('historial');
})();
