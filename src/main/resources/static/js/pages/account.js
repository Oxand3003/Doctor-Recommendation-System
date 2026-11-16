(function () {
  'use strict';

  // Las citas se mudaron de "Mi cuenta" a /mis-citas: los enlaces antiguos (/account#my-appointments, correos ya
  // enviados) siguen funcionando. El servidor no recibe el #, por eso la redirección se hace aquí.
  if (/^#(my-appointments|clinical-history)$/.test(window.location.hash) && /\/account\/?$/.test(window.location.pathname)) {
    window.location.replace(window.location.pathname.replace(/\/account\/?$/, '/mis-citas') + window.location.hash);
    return;
  }

  var tabs = Array.prototype.slice.call(document.querySelectorAll('[data-account-view]'));
  var panels = Array.prototype.slice.call(document.querySelectorAll('[data-account-panel]'));
  if (!tabs.length || !panels.length) return;

  function show(view) {
    tabs.forEach(function (tab) {
      var active = tab.dataset.accountView === view;
      tab.classList.toggle('is-active', active);
      tab.setAttribute('aria-selected', String(active));
    });
    panels.forEach(function (panel) {
      panel.hidden = panel.dataset.accountPanel !== view;
    });
    if (window.history && window.history.replaceState) {
      window.history.replaceState(null, '', window.location.pathname + (view === 'history' ? '#clinical-history' : ''));
    }
  }

  tabs.forEach(function (tab) {
    tab.addEventListener('click', function () { show(tab.dataset.accountView); });
  });

  show(window.location.hash === '#clinical-history' ? 'history' : 'appointments');
})();
