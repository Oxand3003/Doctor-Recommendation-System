/**
 * Perfil privado del médico (/medico/perfil).
 * Los formularios hacen POST real; este script solo agrega comodidades:
 *  - envía solo la foto al elegirla (validando tipo y tamaño antes de subirla)
 *  - pide confirmación antes de eliminar
 *  - rellena el modal de formación para agregar o editar
 *  - contador de caracteres de la descripción y reapertura del modal tras un error
 */
(function () {
  'use strict';

  var MAX_BYTES = 5 * 1024 * 1024;
  var TIPOS = ['image/jpeg', 'image/png', 'image/webp'];

  function $(sel, root) { return (root || document).querySelector(sel); }
  function $$(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }

  function avisar(texto) {
    var main = $('main');
    var prev = $('.mp-alert.js', main);
    if (prev) prev.remove();
    var div = document.createElement('div');
    div.className = 'mp-alert err js';
    div.setAttribute('role', 'alert');
    div.textContent = texto;
    main.insertBefore(div, main.firstChild);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  // ---------- Subida automática de fotos ----------
  $$('input[type="file"][data-auto-submit]').forEach(function (input) {
    input.addEventListener('change', function () {
      var archivos = Array.prototype.slice.call(input.files || []);
      if (!archivos.length) return;
      for (var i = 0; i < archivos.length; i++) {
        var a = archivos[i];
        if (TIPOS.indexOf(a.type) === -1) {
          input.value = '';
          avisar('"' + a.name + '" no es una imagen JPG, PNG o WEBP.');
          return;
        }
        if (a.size > MAX_BYTES) {
          input.value = '';
          avisar('"' + a.name + '" supera el tamaño máximo de 5 MB.');
          return;
        }
      }
      input.form.submit();
    });
  });

  // Galería llena: el botón "Subir fotos" no abre el selector
  var etiquetaGaleria = $('label[for="input-galeria"]');
  if (etiquetaGaleria) {
    etiquetaGaleria.addEventListener('click', function (e) {
      if (etiquetaGaleria.classList.contains('disabled')) e.preventDefault();
    });
  }

  // ---------- Confirmación antes de eliminar ----------
  document.addEventListener('submit', function (e) {
    var form = e.target;
    var msg = (e.submitter && e.submitter.dataset.confirm) || (form.dataset && form.dataset.confirm);
    if (msg && !window.confirm(msg)) e.preventDefault();
  });

  // ---------- Modal de formación (agregar / editar) ----------
  var formFormacion = $('#form-formacion');
  var tituloModal = $('#formacion-title');

  function abrirFormacion(datos) {
    var editando = !!(datos && datos.id);
    formFormacion.reset();
    formFormacion.action = editando ? formFormacion.dataset.base + '/' + datos.id : formFormacion.dataset.base;
    tituloModal.textContent = editando ? 'Editar formación' : 'Agregar formación';
    if (editando) {
      $('#fm-tipo').value = datos.tipo || 'TITULO';
      $('#fm-titulo').value = datos.titulo || '';
      $('#fm-institucion').value = datos.institucion || '';
      $('#fm-anio').value = datos.anio || '';
    }
    window.Modal.open($('#formacion-modal'));
    $('#fm-titulo').focus();
  }

  var btnNueva = $('#btn-nueva-formacion');
  if (btnNueva) btnNueva.addEventListener('click', function () { abrirFormacion(null); });

  $$('[data-edit-formacion]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      abrirFormacion({
        id: btn.dataset.id, tipo: btn.dataset.tipo, titulo: btn.dataset.titulo,
        institucion: btn.dataset.institucion, anio: btn.dataset.anio
      });
    });
  });

  // ---------- Contador de la descripción ----------
  var bio = $('#f-bio');
  var contador = $('#bio-counter');
  function actualizarContador() {
    if (!bio || !contador) return;
    var n = bio.value.length;
    contador.textContent = n + ' / ' + bio.maxLength;
    contador.classList.toggle('over', n >= bio.maxLength);
  }
  if (bio) {
    bio.addEventListener('input', actualizarContador);
    actualizarContador();
  }

  // ---------- Si el servidor rechazó el formulario, se vuelve a abrir con lo escrito ----------
  var reabrir = document.body.dataset.reabrir;
  if (reabrir === 'editar') {
    window.Modal.open($('#edit-modal'));
  }
  // Cambio de credenciales: tras pedir el código o equivocarse, la ventana vuelve a abrirse en el paso que toca.
  if (reabrir === 'password' || reabrir === 'email') {
    var ventana = $(reabrir === 'password' ? '#password-modal' : '#email-modal');
    if (ventana) {
      window.Modal.open(ventana);
      var campo = reabrir === 'password' ? $('#pw-codigo') : ($('#em-codigo') || $('#em-nuevo'));
      if (campo) campo.focus();
    }
  }
})();
