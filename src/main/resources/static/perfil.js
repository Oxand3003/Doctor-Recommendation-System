const API_BASE_URL = window.MEDICERCA_API_URL || `${window.location.origin}/api/v1`;
const HORARIOS_MANANA = ['09:00', '09:40', '10:20', '11:00'];
const HORARIOS_TARDE = ['15:00', '15:40', '16:20', '17:00'];

document.addEventListener('DOMContentLoaded', () => {
  const query = new URLSearchParams(window.location.search);
  const doctorId = query.get('doctorId') || '1';
  let doctor;
  let cuenta;
  let modalidad = 'PRESENCIAL';
  let fechaSeleccionada;
  let horaSeleccionada;
  let disponibilidadRequest = 0;

  const profileStatus = document.querySelector('#profile-status');
  const profileContent = document.querySelector('#profile-content');
  const bookingStatus = document.querySelector('#booking-status');
  const bookingModal = document.querySelector('#modal-reserva');
  const successModal = document.querySelector('#modal-exito');
  const bookingForm = document.querySelector('#form-reserva');
  const submitButton = document.querySelector('#btn-confirmar-reserva');

  function initials(name) {
    return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase();
  }

  function openModal(element) {
    element.classList.add('active');
    element.setAttribute('aria-hidden', 'false');
  }

  function closeModal(element) {
    element.classList.remove('active');
    element.setAttribute('aria-hidden', 'true');
  }

  function localIsoDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  function formatDate(dateString) {
    return new Date(`${dateString}T12:00:00`).toLocaleDateString('es-PE', {
      weekday: 'short', day: 'numeric', month: 'short'
    });
  }

  async function drawHours() {
    const dateForRequest = fechaSeleccionada;
    const requestId = ++disponibilidadRequest;
    const now = new Date();
    const isToday = localIsoDate(now) === fechaSeleccionada;
    const inFuture = (hour) => !isToday || (() => {
      const [hours, minutes] = hour.split(':').map(Number);
      return hours * 60 + minutes > now.getHours() * 60 + now.getMinutes();
    })();
    let occupiedHours;

    bookingStatus.textContent = 'Consultando horarios...';
    document.querySelectorAll('.hour-btn').forEach((button) => { button.disabled = true; });
    try {
      const response = await fetch(`${API_BASE_URL}/appointments/availability?doctorId=${encodeURIComponent(doctorId)}&fecha=${encodeURIComponent(dateForRequest)}`);
      const payload = await response.json();
      if (!response.ok) throw new Error(payload.message || 'No se pudo consultar la disponibilidad.');
      if (requestId !== disponibilidadRequest) return;
      occupiedHours = new Set(payload);
    } catch (error) {
      if (requestId === disponibilidadRequest) bookingStatus.textContent = error.message;
      return;
    }

    for (const [containerId, hours] of [
      ['morning-hours', HORARIOS_MANANA],
      ['afternoon-hours', HORARIOS_TARDE]
    ]) {
      const container = document.getElementById(containerId);
      container.replaceChildren();
      hours.forEach((hour) => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'hour-btn';
        button.textContent = hour;
        button.disabled = !inFuture(hour) || occupiedHours.has(hour);
        if (occupiedHours.has(hour)) {
          button.classList.add('disabled');
          button.title = 'Este horario ya fue reservado';
        }
        if (button.disabled) button.classList.add('disabled');
        if (!button.disabled && !horaSeleccionada) horaSeleccionada = hour;
        button.dataset.hour = hour;
        button.addEventListener('click', () => {
          horaSeleccionada = hour;
          document.querySelectorAll('.hour-btn').forEach((item) => {
            item.classList.toggle('active', item === button);
          });
          updateSummary();
        });
        if (hour === horaSeleccionada) button.classList.add('active');
        container.append(button);
      });
    }

    if (requestId !== disponibilidadRequest) return;
    if (horaSeleccionada && (!inFuture(horaSeleccionada) || occupiedHours.has(horaSeleccionada))) horaSeleccionada = undefined;
    if (!horaSeleccionada) {
      const next = [...document.querySelectorAll('.hour-btn:not(:disabled)')][0];
      horaSeleccionada = next?.dataset.hour;
      next?.classList.add('active');
    }
    if (!horaSeleccionada) bookingStatus.textContent = 'No quedan horarios disponibles para este día. Selecciona otra fecha.';
    else bookingStatus.textContent = '';
    document.querySelector('#lbl-selected-date').textContent = formatDate(fechaSeleccionada);
    document.querySelector('#btn-abrir-reserva').disabled = !horaSeleccionada || !doctor?.disponible
      || (cuenta && cuenta.rol !== 'CLIENTE');
    updateSummary();
  }

  function updateSummary() {
    if (!doctor || !fechaSeleccionada) return;
    const formattedTime = horaSeleccionada || 'Sin horario';
    document.querySelector('#summary-doctor').textContent = `${doctor.nombres} ${doctor.apellidos}`;
    document.querySelector('#summary-datetime').textContent = `${formatDate(fechaSeleccionada)} · ${formattedTime}`;
    document.querySelector('#summary-modality').textContent = modalidad === 'PRESENCIAL' ? 'Presencial' : 'Teleconsulta';
    document.querySelector('#modal-doctor-name').textContent = `${doctor.nombres} ${doctor.apellidos}`;
    document.querySelector('#modal-avatar').textContent = initials(`${doctor.nombres} ${doctor.apellidos}`);
    document.querySelector('#modal-summary-text').textContent = `${doctor.especialidad} · ${formatDate(fechaSeleccionada)} a las ${formattedTime} · ${modalidad === 'PRESENCIAL' ? 'Presencial' : 'Teleconsulta'}`;
  }

  function drawDays() {
    const container = document.querySelector('#days-carousel');
    const today = new Date();
    container.replaceChildren();
    for (let offset = 0; offset < 7; offset += 1) {
      const date = new Date(today);
      date.setDate(today.getDate() + offset);
      const isoDate = localIsoDate(date);
      const button = document.createElement('button');
      button.type = 'button';
      button.className = `day-slot${offset === 0 ? ' active' : ''}`;
      button.dataset.date = isoDate;
      button.innerHTML = `<span class="day-name">${date.toLocaleDateString('es-PE', { weekday: 'short' }).replace('.', '').toUpperCase()}</span><span class="day-num">${date.getDate()}</span><span class="day-sub">${date.toLocaleDateString('es-PE', { month: 'short' }).replace('.', '')}</span>`;
      button.addEventListener('click', () => {
        container.querySelectorAll('.day-slot').forEach((slot) => slot.classList.remove('active'));
        button.classList.add('active');
        fechaSeleccionada = isoDate;
        horaSeleccionada = undefined;
        drawHours();
      });
      container.append(button);
    }
    fechaSeleccionada = localIsoDate(today);
    drawHours();
  }

  function renderDoctor(data) {
    doctor = data;
    const fullName = `${data.nombres} ${data.apellidos}`;
    const establishment = data.establecimiento || 'Sin establecimiento asignado';
    const initialsText = initials(fullName);
    document.title = `${fullName} | MediCerca`;
    document.querySelector('#breadcrumb-doctor').textContent = fullName;
    document.querySelector('#doctor-avatar').textContent = initialsText;
    document.querySelector('#doctor-name').textContent = fullName;
    document.querySelector('#doctor-specialty').textContent = data.especialidad;
    document.querySelector('#doctor-cmp').textContent = data.cmp;
    document.querySelector('#doctor-establishment').textContent = establishment;
    document.querySelector('#doctor-rating').textContent = `★ ${Number(data.rating).toFixed(1)} / 5`;
    document.querySelector('#doctor-experience').textContent = data.aniosExperiencia;
    document.querySelector('#doctor-availability').textContent = data.disponible ? 'Disponible para reservas' : 'No disponible';
    document.querySelector('#doctor-availability').classList.toggle('unavailable-badge', !data.disponible);
    document.querySelector('#doctor-description').textContent = `${fullName} atiende en ${establishment} y cuenta con ${data.aniosExperiencia} años de experiencia en ${data.especialidad}. La información del perfil corresponde al catálogo registrado en MediCerca.`;
    document.querySelector('#office-name').textContent = establishment;
    document.querySelector('#office-address').textContent = data.establecimientoDireccion || 'Dirección no registrada';
    const district = data.establecimientoDistrito || 'Distrito no registrado';
    document.querySelector('#office-district').textContent = district;
    document.querySelector('#map-district').textContent = district;
    document.querySelector('#summary-doctor').textContent = fullName;
    profileStatus.textContent = '';
    profileContent.hidden = false;
    if (!data.disponible) bookingStatus.textContent = 'Este especialista no está aceptando reservas por el momento.';
    document.querySelector('#btn-abrir-reserva').disabled = !data.disponible
      || (cuenta && cuenta.rol !== 'CLIENTE');
    drawDays();
  }

  async function loadAccount() {
    try {
      const response = await fetch(`${API_BASE_URL}/auth/me`);
      if (!response.ok) {
        bookingStatus.textContent = 'Inicia sesión con una cuenta cliente para reservar.';
        return;
      }
      cuenta = await response.json();
      const link = document.querySelector('#auth-link');
      link.textContent = 'Mi cuenta';
      link.href = 'cuenta.html';
      if (cuenta.rol === 'CLIENTE') {
        document.querySelector('#paciente-nombre').value = `${cuenta.nombres} ${cuenta.apellidos}`;
        document.querySelector('#paciente-dni').value = cuenta.dni || '';
        document.querySelector('#paciente-telefono').value = cuenta.telefono || '';
        ['paciente-nombre', 'paciente-dni', 'paciente-telefono'].forEach((id) => {
          document.getElementById(id).readOnly = true;
        });
      }
      if (doctor?.disponible && horaSeleccionada && cuenta.rol === 'CLIENTE') {
        document.querySelector('#btn-abrir-reserva').disabled = false;
        bookingStatus.textContent = '';
      } else if (cuenta.rol !== 'CLIENTE') {
        bookingStatus.textContent = 'Inicia sesión con una cuenta cliente para reservar.';
        document.querySelector('#btn-abrir-reserva').disabled = true;
      }
    } catch {
      cuenta = undefined;
    }
  }

  async function loadDoctor() {
    try {
      const response = await fetch(`${API_BASE_URL}/doctors/${encodeURIComponent(doctorId)}`);
      const payload = await response.json();
      if (!response.ok) throw new Error(payload.message || 'No se pudo cargar el perfil.');
      renderDoctor(payload);
    } catch (error) {
      profileStatus.textContent = error.message;
    }
  }

  document.querySelectorAll('.mod-btn').forEach((button) => {
    button.addEventListener('click', () => {
      document.querySelectorAll('.mod-btn').forEach((item) => item.classList.remove('active'));
      button.classList.add('active');
      modalidad = button.dataset.modality;
      updateSummary();
    });
  });

  document.querySelector('#btn-abrir-reserva').addEventListener('click', () => {
    if (cuenta?.rol !== 'CLIENTE') {
      const retorno = `perfil-doctor.html?doctorId=${encodeURIComponent(doctorId)}`;
      window.location.href = `auth.html?returnTo=${encodeURIComponent(retorno)}`;
      return;
    }
    updateSummary();
    document.querySelector('#form-status').textContent = '';
    openModal(bookingModal);
    document.querySelector('#paciente-nombre').focus();
  });

  document.querySelector('#btn-cerrar-modal').addEventListener('click', () => closeModal(bookingModal));
  document.querySelector('#btn-cancelar-modal').addEventListener('click', () => closeModal(bookingModal));
  bookingModal.addEventListener('click', (event) => {
    if (event.target === bookingModal) closeModal(bookingModal);
  });

  bookingForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!horaSeleccionada) return;
    submitButton.disabled = true;
    submitButton.textContent = 'Guardando...';
    document.querySelector('#form-status').textContent = '';
    const request = {
      doctorId: Number(doctorId),
      motivo: document.querySelector('#paciente-motivo').value.trim(),
      fechaHora: `${fechaSeleccionada}T${horaSeleccionada}:00`,
      modalidad
    };

    try {
      const csrfResponse = await fetch(`${API_BASE_URL}/auth/csrf`);
      const csrf = await csrfResponse.json();
      const response = await fetch(`${API_BASE_URL}/appointments`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf.token },
        body: JSON.stringify(request)
      });
      const payload = await response.json();
      if (!response.ok) throw new Error(payload.message || 'No se pudo registrar la cita.');

      closeModal(bookingModal);
      horaSeleccionada = undefined;
      drawHours();
      document.querySelector('#ticket-code').textContent = `MED-${String(payload.id).padStart(6, '0')}`;
      const savedDate = new Date(payload.fechaHora);
      document.querySelector('#exito-mensaje').textContent = `La cita con ${payload.doctorNombre} quedó registrada para el ${savedDate.toLocaleString('es-PE')} (${payload.modalidad === 'PRESENCIAL' ? 'presencial' : 'teleconsulta'}).`;
      openModal(successModal);
      document.querySelector('#paciente-motivo').value = '';
    } catch (error) {
      document.querySelector('#form-status').textContent = error.message;
    } finally {
      submitButton.disabled = false;
      submitButton.textContent = 'Confirmar reserva';
    }
  });

  document.querySelector('#btn-cerrar-exito').addEventListener('click', () => closeModal(successModal));
  successModal.addEventListener('click', (event) => {
    if (event.target === successModal) closeModal(successModal);
  });

  loadDoctor();
  loadAccount();
});
