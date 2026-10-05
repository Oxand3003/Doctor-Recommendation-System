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
  if (!response.ok) throw new Error('No se pudo iniciar la sesión segura. Recarga la página.');
  return { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': payload.token };
}

function formData(form) {
  return Object.fromEntries(new FormData(form).entries());
}

function displayStatus(element, message, isError = false) {
  element.textContent = message;
  element.classList.toggle('error-message', isError);
}

document.addEventListener('DOMContentLoaded', () => {
  const loginForm = document.querySelector('#login-form');
  const registerPanel = document.querySelector('#register-panel');
  const clientForm = document.querySelector('#client-form');
  const doctorForm = document.querySelector('#doctor-form');
  document.querySelector('#doctor-graduation').max = new Date().getFullYear();

  function showMode(mode) {
    const registering = mode === 'register';
    loginForm.hidden = registering;
    registerPanel.hidden = !registering;
    document.querySelector('#tab-login').classList.toggle('active', !registering);
    document.querySelector('#tab-register').classList.toggle('active', registering);
  }

  function showRegistrationType(type) {
    const client = type === 'client';
    clientForm.hidden = !client;
    doctorForm.hidden = client;
    document.querySelector('#tab-client').classList.toggle('active', client);
    document.querySelector('#tab-doctor').classList.toggle('active', !client);
  }

  document.querySelector('#tab-login').addEventListener('click', () => showMode('login'));
  document.querySelector('#tab-register').addEventListener('click', () => {
    showMode('register');
    showRegistrationType('client');
  });
  document.querySelector('#tab-client').addEventListener('click', () => showRegistrationType('client'));
  document.querySelector('#tab-doctor').addEventListener('click', () => showRegistrationType('doctor'));

  loginForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const status = document.querySelector('#login-status');
    displayStatus(status, 'Ingresando...');
    try {
      const payload = await apiJson(`${API_BASE_URL}/auth/login`, {
        method: 'POST',
        headers: await csrfHeaders(),
        body: JSON.stringify(formData(loginForm))
      });
      if (payload.rol === 'ADMINISTRADOR') window.location.href = 'admin.html';
      else if (payload.rol === 'MEDICO') window.location.href = 'cuenta.html';
      else {
        const returnTo = new URLSearchParams(window.location.search).get('returnTo');
        const safePath = returnTo && !returnTo.startsWith('//') && !returnTo.includes('://') ? returnTo : 'cuenta.html';
        window.location.href = safePath;
      }
    } catch (error) {
      displayStatus(status, error.message, true);
    }
  });

  clientForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const status = document.querySelector('#client-status');
    displayStatus(status, 'Creando cuenta...');
    try {
      await apiJson(`${API_BASE_URL}/auth/register/client`, {
        method: 'POST',
        headers: await csrfHeaders(),
        body: JSON.stringify(formData(clientForm))
      });
      clientForm.reset();
      showMode('login');
      displayStatus(document.querySelector('#login-status'), 'Cuenta creada. Ya puedes iniciar sesión.');
    } catch (error) {
      displayStatus(status, error.message, true);
    }
  });

  doctorForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const status = document.querySelector('#doctor-status');
    displayStatus(status, 'Enviando solicitud...');
    const payload = formData(doctorForm);
    payload.especialidadId = Number(payload.especialidadId);
    payload.establecimientoId = payload.establecimientoId ? Number(payload.establecimientoId) : null;
    payload.anioEgreso = Number(payload.anioEgreso);
    payload.aniosExperiencia = Number(payload.aniosExperiencia);
    try {
      await apiJson(`${API_BASE_URL}/auth/register/doctor`, {
        method: 'POST',
        headers: await csrfHeaders(),
        body: JSON.stringify(payload)
      });
      doctorForm.reset();
      displayStatus(status, 'Solicitud enviada. El administrador revisará el CMP y los documentos antes de activar la cuenta.');
    } catch (error) {
      displayStatus(status, error.message, true);
    }
  });

  async function fillCatalog(url, select, firstOption) {
    try {
      const items = await apiJson(`${API_BASE_URL}/catalog/${url}`);
      select.replaceChildren(new Option(firstOption, ''));
      items.forEach((item) => {
        const label = url === 'establishments' && item.distrito
          ? `${item.nombre} — ${item.distrito}`
          : item.nombre;
        select.add(new Option(label, item.id));
      });
    } catch {
      displayStatus(document.querySelector('#doctor-status'), 'No se pudo cargar el catálogo. Comprueba la conexión con MySQL.', true);
    }
  }

  fillCatalog('specialties', document.querySelector('#doctor-specialty'), 'Selecciona una especialidad');
  fillCatalog('establishments', document.querySelector('#doctor-establishment'), 'Aún no tengo uno');
});
