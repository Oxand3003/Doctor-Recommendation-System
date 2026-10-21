/**
 * Gestión privada del médico: /medico/horarios, /medico/sedes (y comodidades en tarifas/opiniones).
 * Los formularios hacen POST real con CSRF (th:action) y vuelven con Post/Redirect/Get; este script solo:
 *  - pide confirmación antes de eliminar
 *  - rellena los modales de edición (franja y sede) y cambia la acción del formulario
 *  - valida en el navegador que "hasta" sea posterior a "desde" (el servidor lo valida de todos modos)
 */
(function () {
  'use strict';

  function $(sel, root) { return (root || document).querySelector(sel); }
  function $$(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }

  // ---------- Confirmación antes de eliminar ----------
  document.addEventListener('submit', function (e) {
    var form = e.target;
    var msg = (e.submitter && e.submitter.dataset.confirm) || (form.dataset && form.dataset.confirm);
    if (msg && !window.confirm(msg)) e.preventDefault();
  });

  // ---------- Horarios: hora fin posterior a hora inicio ----------
  function validarRango(form, ini, fin) {
    form.addEventListener('submit', function (e) {
      if (ini.value && fin.value && fin.value <= ini.value) {
        e.preventDefault();
        fin.setCustomValidity('La hora de fin debe ser posterior a la de inicio.');
        fin.reportValidity();
      } else {
        fin.setCustomValidity('');
      }
    });
    [ini, fin].forEach(function (i) { i.addEventListener('input', function () { fin.setCustomValidity(''); }); });
  }
  var formNueva = $('form[action$="/medico/horarios"]:not(#form-horario)');
  if (formNueva) {
    validarRango(formNueva, $('#h-inicio', formNueva), $('#h-fin', formNueva));
    formNueva.addEventListener('submit', function (e) {
      if (!$$('input[name="dias"]:checked', formNueva).length) {
        e.preventDefault();
        window.alert('Elige al menos un día de la semana.');
      }
    });
  }

  // ---------- Modal de edición de franja ----------
  var formHorario = $('#form-horario');
  if (formHorario) {
    validarRango(formHorario, $('#eh-inicio'), $('#eh-fin'));
    $$('[data-edit-horario]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var d = btn.dataset;
        formHorario.action = formHorario.dataset.base + '/' + d.id;
        $('#eh-dia').value = d.dia;
        $('#eh-inicio').value = d.inicio;
        $('#eh-fin').value = d.fin;
        $('#eh-dur').value = d.duracion;
        $('#eh-mod').value = d.modalidad;
        $('#eh-activo').checked = d.activo === 'true';
        window.Modal.open($('#horario-modal'));
      });
    });
  }

  // ---------- Modal de sede (agregar / editar) ----------
  var formSede = $('#form-sede');
  if (formSede) {
    var abrirSede = function (datos) {
      var editando = !!(datos && datos.id);
      formSede.reset();
      formSede.action = editando ? formSede.dataset.base + '/' + datos.id : formSede.dataset.base;
      $('#sede-title').textContent = editando ? 'Editar sede' : 'Agregar sede';
      if (editando) {
        $('#se-nombre').value = datos.nombre || '';
        $('#se-direccion').value = datos.direccion || '';
        $('#se-referencia').value = datos.referencia || '';
        $('#se-distrito').value = datos.distrito || '';
      }
      window.Modal.open($('#sede-modal'));
      $('#se-nombre').focus();
    };
    var nueva = $('#btn-nueva-sede');
    if (nueva) nueva.addEventListener('click', function () { abrirSede(null); });
    $$('[data-edit-sede]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        abrirSede({ id: btn.dataset.id, nombre: btn.dataset.nombre, direccion: btn.dataset.direccion,
                    referencia: btn.dataset.referencia, distrito: btn.dataset.distrito });
      });
    });
  }
})();
