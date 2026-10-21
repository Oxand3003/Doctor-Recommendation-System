(function () {
  'use strict';

  const card = document.getElementById('booking-card');
  if (!card) return;

  const slotsUrl = card.dataset.slotsUrl;
  const doctorName = card.dataset.doctorName;
  const specialty = card.dataset.specialty;

  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

  const hoursBox = $('#hours-container');
  const dateLabel = $('#selected-date-label');
  const summaryDate = $('#summary-datetime');
  const summaryMod = $('#summary-modality');
  const modalSummary = $('#modal-summary-text');
  const btnReservar = $('#btn-reservar');

  const state = { modality: null, modalityLabel: '', date: null, dateLabel: '', hour: null };
  const bookingParams = new URLSearchParams(window.location.search);
  const requestedDate = bookingParams.get('fecha');
  const requestedHour = bookingParams.get('hora');
  const requestedModality = bookingParams.get('modalidad');
  let requestId = 0;

  // ---------- Estado inicial desde el HTML ----------
  function readInitialState() {
    const mod = (requestedModality && $$('.mod-btn', card).find(button => button.dataset.modality === requestedModality && !button.disabled))
      || $('.mod-btn.active', card) || $('.mod-btn:not([disabled])', card);
    if (mod) {
      $$('.mod-btn', card).forEach(b => b.classList.toggle('active', b === mod));
      state.modality = mod.dataset.modality;
      state.modalityLabel = mod.dataset.label;
    }
    const day = requestedDate
      ? $$('.day-slot', card).find(button => button.dataset.date === requestedDate && !button.disabled)
      : $('.day-slot.active', card);
    if (day) {
      $$('.day-slot', card).forEach(b => b.classList.toggle('active', b === day));
      state.date = day.dataset.date;
      state.dateLabel = day.dataset.label;
    } else if (requestedDate && /^\d{4}-\d{2}-\d{2}$/.test(requestedDate)) {
      // La búsqueda permite fechas posteriores a los cinco días visibles en los accesos rápidos.
      state.date = requestedDate;
      state.dateLabel = requestedDate;
    }
  }

  // ---------- Precio según la modalidad elegida ----------
  const priceAmt = $('#booking-price-amt');
  const confirmBtn = $('#btn-confirmar-reserva');
  function currentPrice() {
    const p = state.modality === 'TELECONSULTA' ? card.dataset.priceTele : card.dataset.pricePresencial;
    return p || card.dataset.price || '—';
  }
  function updatePrice() {
    const p = currentPrice();
    if (priceAmt) priceAmt.textContent = p;
    if (confirmBtn) confirmBtn.textContent = 'Confirmar reserva (S/ ' + p + ')';
  }

  // ---------- Resumen ----------
  function updateSummary() {
    updatePrice();
    dateLabel.textContent = state.dateLabel || '—';
    summaryDate.textContent = state.date && state.hour ? `${state.dateLabel} · ${state.hour}` : '—';
    summaryMod.textContent = state.modalityLabel || '—';
    if (modalSummary) {
      modalSummary.textContent = state.hour
        ? `${specialty} • ${state.dateLabel} a las ${state.hour} (${state.modalityLabel})`
        : specialty;
    }
    btnReservar.disabled = !state.hour;
  }

  // ---------- Turnos ----------
  function message(text) {
    hoursBox.innerHTML = '';
    const p = document.createElement('span');
    p.className = 'sec-text';
    p.textContent = text;
    hoursBox.appendChild(p);
  }

  function renderGroup(title, slots, spaced) {
    if (!slots.length) return;
    const t = document.createElement('span');
    t.className = 'hours-group-title' + (spaced ? ' hours-group-title--spaced' : '');
    t.textContent = title;
    const grid = document.createElement('div');
    grid.className = 'hours-grid';
    slots.forEach(s => {
      const b = document.createElement('button');
      b.type = 'button';
      b.className = 'hour-btn' + (s.disponible ? '' : ' disabled');
      b.textContent = s.hora;
      b.dataset.hour = s.hora;
      b.disabled = !s.disponible;
      grid.appendChild(b);
    });
    hoursBox.appendChild(t);
    hoursBox.appendChild(grid);
  }

  function renderSlots(slots) {
    hoursBox.innerHTML = '';
    if (!slots.length || !slots.some(s => s.disponible)) {
      state.hour = null;
      message('No hay horarios disponibles para este día y modalidad.');
      updateSummary();
      return;
    }
    const morning = slots.filter(s => s.hora < '13:00');
    const afternoon = slots.filter(s => s.hora >= '13:00');
    renderGroup('Turno mañana', morning, false);
    renderGroup('Turno tarde', afternoon, morning.length > 0);

    const availableHours = $$('.hour-btn:not([disabled])', hoursBox);
    const selected = requestedHour ? availableHours.find(button => button.dataset.hour === requestedHour) : null;
    const first = selected || availableHours[0];
    first.classList.add('active');
    state.hour = first.dataset.hour;
    updateSummary();
  }

  async function loadSlots() {
    if (!state.date || !state.modality) {
      state.hour = null;
      message('Este especialista aún no tiene horarios disponibles.');
      updateSummary();
      return;
    }
    const current = ++requestId;
    state.hour = null;
    message('Cargando horarios…');
    updateSummary();
    try {
      const url = `${slotsUrl}?date=${encodeURIComponent(state.date)}&modalidad=${encodeURIComponent(state.modality)}`;
      const res = await fetch(url, { headers: { Accept: 'application/json' } });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const slots = await res.json();
      if (current !== requestId) return; // llegó una respuesta más nueva
      renderSlots(slots);
    } catch (e) {
      if (current !== requestId) return;
      message('No se pudieron cargar los horarios. Intenta nuevamente.');
      updateSummary();
    }
  }

  // ---------- Eventos ----------
  $$('.mod-btn', card).forEach(btn => btn.addEventListener('click', () => {
    if (btn.disabled) return;
    $$('.mod-btn', card).forEach(b => b.classList.toggle('active', b === btn));
    state.modality = btn.dataset.modality;
    state.modalityLabel = btn.dataset.label;
    loadSlots();
  }));

  $$('.day-slot', card).forEach(btn => btn.addEventListener('click', () => {
    if (btn.disabled) return;
    $$('.day-slot', card).forEach(b => b.classList.toggle('active', b === btn));
    state.date = btn.dataset.date;
    state.dateLabel = btn.dataset.label;
    loadSlots();
  }));

  hoursBox.addEventListener('click', e => {
    const btn = e.target.closest('.hour-btn');
    if (!btn || btn.disabled) return;
    $$('.hour-btn', hoursBox).forEach(b => b.classList.toggle('active', b === btn));
    state.hour = btn.dataset.hour;
    updateSummary();
  });

  // ---------- Reserva real: POST /appointments ----------
  // El paciente sale de la sesión en el servidor; aquí solo viajan médico, día, hora, modalidad y motivo.
  const bookingForm = $('#booking-form');
  const bookingStatus = $('#booking-status');
  let booking = false;

  function bookingMessage(text, isError) {
    if (!bookingStatus) return;
    bookingStatus.textContent = text || '';
    bookingStatus.classList.toggle('is-error', !!text && isError !== false);
  }

  function setBooking(on) {
    booking = on;
    if (!confirmBtn) return;
    confirmBtn.disabled = on;
    if (on) confirmBtn.textContent = 'Reservando…';
    else updatePrice();
  }

  function showSuccess(cita, correoEnviado) {
    const mail = $('#ticket-mail');
    if (mail) mail.hidden = !correoEnviado;
    const code = $('#ticket-code');
    if (code) code.textContent = cita.codigo;
    const detail = $('#ticket-detail');
    if (detail) detail.textContent = `${cita.fecha} · ${cita.hora} (${cita.modalidad})`;
    const reason = $('#patient-reason');
    if (reason) reason.value = '';
    const close = $('#booking-modal [data-modal-close]');
    if (close) close.click();
    const open = $('#open-success');
    if (open) setTimeout(() => open.click(), 150);
  }

  if (bookingForm) {
    bookingForm.addEventListener('submit', async e => {
      e.preventDefault();
      if (booking) return; // evita el doble clic: una sola petición a la vez
      if (bookingForm.dataset.auth !== 'PACIENTE') return;
      if (!state.date || !state.hour || !state.modality) {
        bookingMessage('Elige un día y un horario disponibles antes de confirmar.');
        return;
      }
      bookingMessage('');
      setBooking(true);
      try {
        const body = new URLSearchParams();
        body.set('medicoId', card.dataset.doctorId);
        body.set('fecha', state.date);
        body.set('hora', state.hour);
        body.set('modalidad', state.modality);
        const reason = $('#patient-reason');
        body.set('motivo', reason ? reason.value.trim() : '');
        const csrf = bookingForm.querySelector('input[name="_csrf"]');
        if (csrf) body.set('_csrf', csrf.value);

        const res = await fetch(bookingForm.getAttribute('action'), {
          method: 'POST',
          credentials: 'same-origin',
          headers: {
            Accept: 'application/json',
            'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
            'X-Requested-With': 'XMLHttpRequest'
          },
          body: body.toString()
        });
        let data = null;
        try { data = await res.json(); } catch (_) { /* la respuesta no era JSON */ }

        if (res.ok && data && data.ok) {
          showSuccess(data.cita, data.correoEnviado);
          loadSlots(); // el turno recién reservado deja de aparecer como libre
        } else if (res.status === 409) {
          bookingMessage((data && data.message) || 'Ese horario ya no está disponible.');
          loadSlots(); // muestra los turnos que sí quedan libres
        } else if (res.status === 401 || res.status === 403 || res.redirected) {
          bookingMessage('Tu sesión expiró o no es de paciente. Inicia sesión como paciente e inténtalo de nuevo.');
        } else {
          bookingMessage((data && data.message) || 'No se pudo registrar la cita. Intenta nuevamente.');
        }
      } catch (err) {
        bookingMessage('No se pudo conectar con el servidor. Revisa tu conexión e intenta nuevamente.');
      } finally {
        setBooking(false);
      }
    });
  }

  // Al abrir la ventana se limpia cualquier aviso anterior
  if (btnReservar) btnReservar.addEventListener('click', () => bookingMessage(''));

  // ---------- Opinión (DEMO) ----------
  const starInput = $('#star-input');
  const reviewForm = $('#review-form');
  const reviewStatus = $('#review-status');
  let rating = 0;

  function paintStars(n) {
    $$('button', starInput).forEach(b => b.classList.toggle('active', Number(b.dataset.value) <= n));
  }
  if (starInput) {
    starInput.addEventListener('click', e => {
      const b = e.target.closest('button[data-value]');
      if (!b) return;
      rating = Number(b.dataset.value);
      paintStars(rating);
    });
  }
  if (reviewForm) {
    reviewForm.addEventListener('submit', e => {
      e.preventDefault();
      if (!rating) {
        reviewStatus.textContent = 'Selecciona una valoración de 1 a 5 estrellas.';
        return;
      }
      if (!reviewForm.checkValidity()) {
        reviewForm.reportValidity();
        return;
      }
      reviewStatus.textContent = 'Solo los pacientes con una cita completada podrán publicar opiniones (próximamente).';
    });
  }

  // ---------- Inicio ----------
  readInitialState();
  updateSummary();
  loadSlots();
})();
