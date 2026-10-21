/**
 * Generic modal behaviour.
 *   <button data-modal-open="my-modal">   opens #my-modal
 *   <button data-modal-close>             closes the modal that contains it
 * Clicking the dark overlay or pressing Esc also closes the open modal.
 */
(function () {
  function openModal(modal) {
    modal.classList.add('active');
    modal.setAttribute('aria-hidden', 'false');
  }

  function closeModal(modal) {
    modal.classList.remove('active');
    modal.setAttribute('aria-hidden', 'true');
  }

  document.addEventListener('click', function (event) {
    var opener = event.target.closest('[data-modal-open]');
    if (opener) {
      var target = document.getElementById(opener.dataset.modalOpen);
      if (target) openModal(target);
      return;
    }

    var closer = event.target.closest('[data-modal-close]');
    if (closer) {
      var parent = closer.closest('.modal-overlay');
      if (parent) closeModal(parent);
      return;
    }

    if (event.target.classList && event.target.classList.contains('modal-overlay')) {
      closeModal(event.target);
    }
  });

  document.addEventListener('keydown', function (event) {
    if (event.key !== 'Escape') return;
    document.querySelectorAll('.modal-overlay.active').forEach(closeModal);
  });

  // Exposed so page scripts can open/close modals programmatically
  window.Modal = { open: openModal, close: closeModal };
})();
