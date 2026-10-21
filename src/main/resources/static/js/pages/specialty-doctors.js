document.addEventListener('DOMContentLoaded', function () {
  var today = new Date();
  var minDate = [today.getFullYear(), String(today.getMonth() + 1).padStart(2, '0'), String(today.getDate()).padStart(2, '0')].join('-');

  document.querySelectorAll('.specialty-doctor-card').forEach(function (card) {
    var dateInput = card.querySelector('[data-slot-date]');
    var modalityInput = card.querySelector('[data-slot-modality]');
    var checkButton = card.querySelector('[data-check-slots]');
    var status = card.querySelector('[data-slot-status]');
    var slotsBox = card.querySelector('[data-slot-results]');
    var reserveLink = card.querySelector('[data-booking-link]');
    var requestId = 0;

    dateInput.min = minDate;
    if (modalityInput) modalityInput.value = card.dataset.defaultModality;

    function disableReservation() {
      reserveLink.classList.add('is-disabled');
      reserveLink.setAttribute('aria-disabled', 'true');
      reserveLink.setAttribute('tabindex', '-1');
      reserveLink.href = '/doctor/' + encodeURIComponent(card.dataset.doctorId) + '#booking-card';
    }

    function renderSlots(slots, date) {
      slotsBox.replaceChildren();
      var available = slots.filter(function (slot) { return slot.disponible; });
      if (!available.length) {
        status.textContent = 'No hay turnos disponibles para esta fecha y modalidad.';
        return;
      }

      status.textContent = 'Selecciona un horario libre para continuar:';
      available.forEach(function (slot) {
        var button = document.createElement('button');
        button.type = 'button';
        button.className = 'specialty-time-option';
        button.textContent = slot.hora;
        button.addEventListener('click', function () {
          slotsBox.querySelectorAll('.specialty-time-option').forEach(function (item) {
            item.classList.toggle('active', item === button);
          });
          var params = new URLSearchParams({ fecha: date, hora: slot.hora, modalidad: modalityInput.value });
          var bookingUrl = '/doctor/' + encodeURIComponent(card.dataset.doctorId) + '?' + params.toString() + '#booking-card';
          reserveLink.href = bookingUrl;
          reserveLink.classList.remove('is-disabled');
          reserveLink.removeAttribute('aria-disabled');
          reserveLink.removeAttribute('tabindex');
        });
        slotsBox.appendChild(button);
      });
    }

    function resetSelection() {
      requestId++;
      checkButton.disabled = false;
      slotsBox.replaceChildren();
      disableReservation();
      status.textContent = 'Elige una fecha para comprobar turnos libres.';
    }

    dateInput.addEventListener('change', resetSelection);
    if (modalityInput) modalityInput.addEventListener('change', resetSelection);

    checkButton.addEventListener('click', function () {
      if (!dateInput.value) {
        status.textContent = 'Selecciona una fecha para consultar disponibilidad.';
        dateInput.focus();
        return;
      }
      disableReservation();
      var currentRequest = ++requestId;
      slotsBox.replaceChildren();
      status.textContent = 'Consultando horarios…';
      checkButton.disabled = true;
      var params = new URLSearchParams({ date: dateInput.value, modalidad: modalityInput.value });
      fetch(card.dataset.slotsUrl + '?' + params.toString(), { headers: { Accept: 'application/json' } })
        .then(function (response) {
          if (!response.ok) throw new Error('No se pudieron consultar los horarios. Inténtalo de nuevo.');
          return response.json();
        })
        .then(function (slots) {
          if (currentRequest !== requestId) return;
          renderSlots(slots, dateInput.value);
        })
        .catch(function (error) {
          if (currentRequest === requestId) status.textContent = error.message;
        })
        .finally(function () {
          if (currentRequest === requestId) checkButton.disabled = false;
        });
    });
  });
});
