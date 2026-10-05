const API_BASE_URL = window.MEDICERCA_API_URL || `${window.location.origin}/api/v1`;

async function apiJson(url, options = {}) {
  const response = await fetch(url, { credentials: 'same-origin', ...options });
  const payload = response.status === 204 ? null : await response.json();
  if (!response.ok) throw new Error(payload?.message || 'No se pudo completar la solicitud.');
  return payload;
}

async function csrfHeaders() {
  const response = await fetch(`${API_BASE_URL}/auth/csrf`, { credentials: 'same-origin' });
  const payload = await response.json();
  return { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': payload.token };
}

document.addEventListener('DOMContentLoaded', async () => {
  const status = document.querySelector('#admin-status');
  const list = document.querySelector('#application-list');

  function infoLine(label, value) {
    const line = document.createElement('p');
    const strong = document.createElement('strong');
    strong.textContent = `${label}: `;
    line.append(strong, document.createTextNode(value || 'No declarado'));
    return line;
  }

  async function loadApplications() {
    status.textContent = 'Cargando solicitudes...';
    try {
      const applications = await apiJson(`${API_BASE_URL}/admin/doctor-applications`);
      list.replaceChildren();
      if (applications.length === 0) {
        status.textContent = 'No hay solicitudes pendientes.';
        return;
      }
      status.textContent = `${applications.length} solicitud${applications.length === 1 ? '' : 'es'} pendiente${applications.length === 1 ? '' : 's'}.`;
      applications.forEach((application) => {
        const card = document.createElement('article');
        card.className = 'application-card';
        const title = document.createElement('h2');
        title.textContent = `${application.nombres} ${application.apellidos}`;
        card.append(title);
        [
          ['Correo', application.email], ['DNI', application.dni], ['Teléfono', application.telefono],
          ['CMP', application.cmp], ['RNE', application.rne], ['Título', application.tituloProfesional],
          ['Universidad', application.universidad], ['Año de egreso', application.anioEgreso],
          ['Experiencia', `${application.aniosExperiencia} años`], ['Especialidad', application.especialidad],
          ['Establecimiento', application.establecimiento]
        ].forEach(([label, value]) => card.append(infoLine(label, String(value ?? ''))));

        const proof = document.createElement('a');
        proof.href = application.sustentoUrl;
        proof.target = '_blank';
        proof.rel = 'noopener noreferrer';
        proof.textContent = 'Abrir sustento profesional';
        proof.className = 'proof-link';
        card.append(proof);

        const actions = document.createElement('div');
        actions.className = 'application-actions';
        const approve = document.createElement('button');
        approve.type = 'button';
        approve.className = 'btn btn-primary';
        approve.textContent = 'Aprobar';
        approve.addEventListener('click', () => review(application.doctorId, 'approve'));
        const reject = document.createElement('button');
        reject.type = 'button';
        reject.className = 'btn btn-ghost';
        reject.textContent = 'Rechazar';
        reject.addEventListener('click', () => review(application.doctorId, 'reject'));
        actions.append(approve, reject);
        card.append(actions);
        list.append(card);
      });
    } catch (error) {
      status.textContent = error.message;
      if (error.message.includes('Inicia sesión') || error.message.includes('permiso')) {
        window.location.href = 'auth.html';
      }
    }
  }

  async function review(doctorId, action) {
    let motivo = '';
    if (action === 'reject') {
      motivo = window.prompt('Indica al médico por qué se rechazó la solicitud:')?.trim() || '';
      if (!motivo) return;
    }
    try {
      await apiJson(`${API_BASE_URL}/admin/doctor-applications/${doctorId}/${action}`, {
        method: 'POST',
        headers: await csrfHeaders(),
        ...(action === 'reject' ? { body: JSON.stringify({ motivo }) } : {})
      });
      await loadApplications();
    } catch (error) {
      status.textContent = error.message;
    }
  }

  try {
    const account = await apiJson(`${API_BASE_URL}/auth/me`);
    if (account.rol !== 'ADMINISTRADOR') {
      window.location.href = 'cuenta.html';
      return;
    }
    await loadApplications();
  } catch {
    window.location.href = 'auth.html';
  }

  document.querySelector('#logout-button').addEventListener('click', async () => {
    try {
      await apiJson(`${API_BASE_URL}/auth/logout`, { method: 'POST', headers: await csrfHeaders() });
    } finally {
      window.location.href = 'index.html';
    }
  });
});
