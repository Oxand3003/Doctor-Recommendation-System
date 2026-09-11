/**
 * MediCerca — Lógica de Interacción para Perfil de Doctor
 * Agendamiento de citas, selección de turnos y modales interactivos
 */

document.addEventListener('DOMContentLoaded', () => {
  // Variables de estado
  let selectedModality = 'Presencial';
  let selectedDate = 'Lun 15 Mar';
  let selectedHour = '09:00 AM';

  // Elementos DOM
  const modalityBtns = document.querySelectorAll('.mod-btn');
  const daySlots = document.querySelectorAll('.day-slot');
  const hourBtns = document.querySelectorAll('.hour-btn:not(.disabled)');
  const summaryDatetime = document.getElementById('summary-datetime');
  const summaryModality = document.getElementById('summary-modality');
  const lblSelectedDate = document.getElementById('lbl-selected-date');

  // Modales
  const modalReserva = document.getElementById('modal-reserva');
  const modalExito = document.getElementById('modal-exito');
  const btnAbrirReserva = document.getElementById('btn-abrir-reserva');
  const btnCerrarModal = document.getElementById('btn-cerrar-modal');
  const btnCancelarModal = document.getElementById('btn-cancelar-modal');
  const btnCerrarExito = document.getElementById('btn-cerrar-exito');
  const formReserva = document.getElementById('form-reserva');
  const modalSummaryText = document.getElementById('modal-summary-text');
  const exitoMensaje = document.getElementById('exito-mensaje');
  const btnAbrirOpinion = document.getElementById('btn-abrir-opinion');

  // 1. Selector de Modalidad (Presencial vs Teleconsulta)
  modalityBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      modalityBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      selectedModality = btn.dataset.modality === 'presencial' ? 'Presencial' : 'Teleconsulta';
      actualizarResumen();
    });
  });

  // 2. Selector de Días (Carrusel de fechas)
  daySlots.forEach(slot => {
    slot.addEventListener('click', () => {
      daySlots.forEach(s => s.classList.remove('active'));
      slot.classList.add('active');
      selectedDate = slot.dataset.date;
      if (lblSelectedDate) {
        lblSelectedDate.textContent = selectedDate;
      }
      actualizarResumen();
    });
  });

  // 3. Selector de Horarios
  hourBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      hourBtns.forEach(h => h.classList.remove('active'));
      btn.classList.add('active');
      selectedHour = btn.dataset.hour;
      actualizarResumen();
    });
  });

  // Actualizar textos del resumen
  function actualizarResumen() {
    if (summaryDatetime) {
      summaryDatetime.textContent = `${selectedDate} — ${selectedHour}`;
    }
    if (summaryModality) {
      summaryModality.textContent = selectedModality;
    }
    if (modalSummaryText) {
      modalSummaryText.textContent = `Cardiología • ${selectedDate} a las ${selectedHour} (${selectedModality})`;
    }
  }

  // 4. Apertura y Cierre de Modal de Reserva
  if (btnAbrirReserva && modalReserva) {
    btnAbrirReserva.addEventListener('click', () => {
      actualizarResumen();
      modalReserva.classList.add('active');
      modalReserva.setAttribute('aria-hidden', 'false');
    });
  }

  const cerrarModal = () => {
    if (modalReserva) {
      modalReserva.classList.remove('active');
      modalReserva.setAttribute('aria-hidden', 'true');
    }
  };

  if (btnCerrarModal) btnCerrarModal.addEventListener('click', cerrarModal);
  if (btnCancelarModal) btnCancelarModal.addEventListener('click', cerrarModal);

  // Cerrar al dar clic fuera del contenido
  if (modalReserva) {
    modalReserva.addEventListener('click', (e) => {
      if (e.target === modalReserva) cerrarModal();
    });
  }

  // 5. Envío del Formulario de Reserva
  if (formReserva) {
    formReserva.addEventListener('submit', (e) => {
      e.preventDefault();
      const pacienteNombre = document.getElementById('paciente-nombre').value;
      cerrarModal();

      if (modalExito) {
        if (exitoMensaje) {
          exitoMensaje.textContent = `¡Gracias, ${pacienteNombre}! Tu cita para el ${selectedDate} a las ${selectedHour} (${selectedModality}) con la Dra. Rosa Medina ha sido confirmada con éxito.`;
        }
        modalExito.classList.add('active');
        modalExito.setAttribute('aria-hidden', 'false');
      }

      formReserva.reset();
    });
  }

  if (btnCerrarExito && modalExito) {
    btnCerrarExito.addEventListener('click', () => {
      modalExito.classList.remove('active');
      modalExito.setAttribute('aria-hidden', 'true');
    });
  }

  // 6. Botón de Opinión
  if (btnAbrirOpinion) {
    btnAbrirOpinion.addEventListener('click', () => {
      const comentario = prompt('Por favor, escribe tu opinión sobre la atención recibida:');
      if (comentario && comentario.trim() !== '') {
        alert('¡Muchas gracias por tu valoración! Será publicada tras una breve moderación.');
      }
    });
  }
});

