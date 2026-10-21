/**
 * Visor de fotos ampliadas (perfil público y privado).
 *   <a href="/doctor/foto/5" data-lightbox>   o   <button data-lightbox data-src="/doctor/foto/5">
 * Cierra con clic fuera de la foto, con la X o con Esc.
 */
(function () {
  'use strict';
  var box = null;
  var img = null;

  function build() {
    box = document.createElement('div');
    box.className = 'lightbox';
    box.setAttribute('role', 'dialog');
    box.setAttribute('aria-modal', 'true');
    box.setAttribute('aria-label', 'Fotografía ampliada');
    var close = document.createElement('button');
    close.type = 'button';
    close.className = 'lightbox-close';
    close.setAttribute('aria-label', 'Cerrar');
    close.innerHTML = '&times;';
    img = document.createElement('img');
    img.alt = 'Fotografía ampliada';
    box.appendChild(close);
    box.appendChild(img);
    document.body.appendChild(box);
    box.addEventListener('click', function (e) {
      if (e.target !== img) closeBox();
    });
  }

  function openBox(src) {
    if (!box) build();
    img.src = src;
    box.classList.add('active');
  }

  function closeBox() {
    if (!box) return;
    box.classList.remove('active');
    img.removeAttribute('src');
  }

  document.addEventListener('click', function (e) {
    var t = e.target.closest('[data-lightbox]');
    if (!t) return;
    var src = t.dataset.src || t.getAttribute('href');
    if (!src) return;
    e.preventDefault();
    openBox(src);
  });

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') closeBox();
  });
})();
