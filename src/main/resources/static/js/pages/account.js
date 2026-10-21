(function () {
  'use strict';

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
      window.history.replaceState(null, '', window.location.pathname + (view === 'history' ? '#clinical-history' : '#my-appointments'));
    }
  }

  tabs.forEach(function (tab) {
    tab.addEventListener('click', function () { show(tab.dataset.accountView); });
  });

  show(window.location.hash === '#clinical-history' ? 'history' : 'appointments');
})();
