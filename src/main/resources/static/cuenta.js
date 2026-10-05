const API_BASE_URL = window.MEDICERCA_API_URL || `${window.location.origin}/api/v1`;

async function cuentaJson(url, options = {}) {
  const response = await fetch(url, { credentials: 'same-origin', ...options });
  const payload = response.status === 204 ? null : await response.json();
  if (!response.ok) throw new Error(payload?.message || 'No se pudo cargar la cuenta.');
  return payload;
}

async function cuentaCsrfHeaders() {
  const response = await fetch(`${API_BASE_URL}/auth/csrf`, { credentials: 'same-origin' });
  const payload = await response.json();
  return { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': payload.token };
}

document.addEventListener('DOMContentLoaded', async () => {
  const status = document.querySelector('#account-status');
  const appointmentsTitle = document.querySelector('#appointments-title');
  const appointmentList = document.querySelector('#appointment-list');

  function addDetail(label, value) {
    const line = document.createElement('p');
    const strong = document.createElement('strong');
    strong.textContent = `${label}: `;
    line.append(strong, document.createTextNode(value || '—'));
    document.querySelector('#account-details').append(line);
  }

  try {
    const account = await cuentaJson(`${API_BASE_URL}/auth/me`);
    document.querySelector('#account-name').textContent = `${account.nombres} ${account.apellidos}`;
    const roleNames = { CLIENTE: 'Cliente', MEDICO: 'Médico verificado', ADMINISTRADOR: 'Administrador' };
    document.querySelector('#account-role').textContent = roleNames[account.rol] || account.rol;
    addDetail('Correo', account.email);
    addDetail('DNI', account.dni);
    addDetail('Teléfono', account.telefono);
    addDetail('Estado de cuenta', account.estado);

    if (account.rol === 'ADMINISTRADOR') {
      const action = document.querySelector('#account-action');
      action.href = 'admin.html';
      action.textContent = 'Revisar solicitudes de médicos';
      action.hidden = false;
    }

    if (account.rol === 'MEDICO') {
      const action = document.querySelector('#account-action');
      action.href = `perfil-doctor.html?doctorId=${encodeURIComponent(account.doctorId)}`;
      action.textContent = 'Ver perfil público';
      action.hidden = false;
    }

    if (account.rol === 'CLIENTE' || account.rol === 'MEDICO') {
      appointmentsTitle.hidden = false;
      appointmentsTitle.textContent = account.rol === 'CLIENTE' ? 'Mis citas' : 'Citas de pacientes';
      const path = account.rol === 'CLIENTE' ? '/client/me/appointments' : '/doctor/me/appointments';
      const appointments = await cuentaJson(`${API_BASE_URL}${path}`);
      if (appointments.length === 0) {
        status.textContent = 'Aún no hay citas registradas.';
      } else {
        appointments.forEach((appointment) => {
          const card = document.createElement('article');
          card.className = 'appointment-card';
          const title = document.createElement('h3');
          title.textContent = account.rol === 'CLIENTE' ? appointment.doctorNombre : appointment.pacienteNombre;
          card.append(title);
          card.append(document.createTextNode(new Date(appointment.fechaHora).toLocaleString('es-PE')));
          const modality = document.createElement('p');
          modality.textContent = `Modalidad: ${appointment.modalidad === 'PRESENCIAL' ? 'Presencial' : 'Teleconsulta'} · ${appointment.estado}`;
          card.append(modality);
          if (account.rol === 'MEDICO') {
            if (appointment.pacienteTelefono) {
              const phone = document.createElement('p');
              phone.textContent = `Teléfono: ${appointment.pacienteTelefono}`;
              card.append(phone);
            }
            if (appointment.motivo) {
              const reason = document.createElement('p');
              reason.textContent = `Motivo: ${appointment.motivo}`;
              card.append(reason);
            }
          }
          appointmentList.append(card);
        });
      }
    }
  } catch {
    window.location.href = 'auth.html';
  }

  document.querySelector('#logout-button').addEventListener('click', async () => {
    try {
      await cuentaJson(`${API_BASE_URL}/auth/logout`, { method: 'POST', headers: await cuentaCsrfHeaders() });
    } finally {
      window.location.href = 'index.html';
    }
  });
});
