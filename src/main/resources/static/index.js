const API_BASE_URL = window.MEDICERCA_API_URL || `${window.location.origin}/api/v1`;

const searchForm = document.querySelector('#search-form');
const specialtyInput = document.querySelector('#specialty-input');
const locationInput = document.querySelector('#location-input');
const searchStatus = document.querySelector('#search-status');
const resultsSection = document.querySelector('#resultados');
const resultsSummary = document.querySelector('#results-summary');
const resultsList = document.querySelector('#results-list');

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

async function getJson(url) {
  const response = await fetch(url);
  const payload = await response.json();
  if (!response.ok) throw new Error(payload.message || 'No fue posible completar la solicitud.');
  return payload;
}

function renderResults(doctors, specialty, district) {
  resultsSection.hidden = false;
  const locationText = district ? ` en ${district}` : '';
  resultsSummary.textContent = `${doctors.length} recomendacion${doctors.length === 1 ? '' : 'es'} para ${specialty}${locationText}.`;

  if (doctors.length === 0) {
    resultsList.innerHTML = '<p class="empty-state">No encontramos especialistas que coincidan con esos filtros.</p>';
    return;
  }

  resultsList.innerHTML = doctors.map((doctor) => `
    <article class="result-card">
      <div>
        <h3>${escapeHtml(doctor.nombreCompleto)}</h3>
        <p class="result-specialty">${escapeHtml(doctor.especialidad)}</p>
      </div>
      <div class="result-meta">
        <span>★ ${Number(doctor.rating).toFixed(1)}</span>
        <span>${escapeHtml(doctor.aniosExperiencia)} años exp.</span>
      </div>
      <p class="result-location">${escapeHtml(doctor.establecimiento)}</p>
      <p class="result-location">Puntaje MediCerca: ${Number(doctor.puntajeRecomendacion).toFixed(2)} / 100</p>
      <a class="btn btn-primary" href="perfil-doctor.html?doctorId=${encodeURIComponent(doctor.doctorId)}">Ver perfil y reservar</a>
    </article>
  `).join('');
}

async function searchDoctors(specialty) {
  const normalizedSpecialty = specialty.trim();
  const district = locationInput.value.trim();
  if (!normalizedSpecialty) {
    searchStatus.textContent = 'Escribe una especialidad para buscar.';
    specialtyInput.focus();
    return;
  }

  searchStatus.textContent = 'Buscando especialistas disponibles...';
  resultsSection.hidden = false;
  resultsSummary.textContent = '';
  resultsList.innerHTML = '<p class="empty-state">Cargando recomendaciones...</p>';

  try {
    const params = new URLSearchParams({ especialidad: normalizedSpecialty, limite: '5' });
    if (district) params.set('distrito', district);
    const payload = await getJson(`${API_BASE_URL}/doctors/recomendar?${params}`);
    renderResults(payload, normalizedSpecialty, district);
    searchStatus.textContent = 'Búsqueda completada.';
    resultsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
  } catch (error) {
    resultsSummary.textContent = '';
    resultsList.innerHTML = `<p class="empty-state">${escapeHtml(error.message)}</p>`;
    searchStatus.textContent = 'No se pudo completar la búsqueda.';
  }
}

async function loadDashboard() {
  try {
    const doctors = await getJson(`${API_BASE_URL}/doctors`);
    const ratings = doctors.map((doctor) => Number(doctor.rating)).filter(Number.isFinite);
    const specialties = new Set(doctors.map((doctor) => doctor.especialidad).filter(Boolean));
    document.querySelector('#stat-doctors').textContent = doctors.length;
    document.querySelector('#stat-specialties').textContent = specialties.size;
    document.querySelector('#stat-rating').textContent = ratings.length
      ? (ratings.reduce((total, rating) => total + rating, 0) / ratings.length).toFixed(1)
      : '—';

    const featured = await getJson(`${API_BASE_URL}/doctors/recomendar?especialidad=${encodeURIComponent('Cardiología')}&limite=1`);
    if (!featured.length) return;
    const doctor = featured[0];
    const [firstName, ...lastNames] = doctor.nombreCompleto.split(' ');
    const profileUrl = `perfil-doctor.html?doctorId=${encodeURIComponent(doctor.doctorId)}`;
    document.querySelector('#featured-name').textContent = doctor.nombreCompleto;
    document.querySelector('#featured-name').href = profileUrl;
    document.querySelector('#featured-link').href = profileUrl;
    document.querySelector('#featured-specialty').textContent = doctor.especialidad;
    document.querySelector('#featured-avatar').textContent = `${firstName?.[0] || ''}${lastNames[0]?.[0] || ''}`.toUpperCase();
    document.querySelector('#featured-rating').textContent = `★ ${Number(doctor.rating).toFixed(1)}`;
    document.querySelector('#featured-location').textContent = doctor.establecimiento;
    document.querySelector('#featured-experience').textContent = `${doctor.aniosExperiencia} años de experiencia`;
  } catch {
    searchStatus.textContent = 'Conecta el servidor MediCerca para ver el catálogo.';
  }
}

async function updateAuthLink() {
  try {
    const account = await getJson(`${API_BASE_URL}/auth/me`);
    const link = document.querySelector('#auth-link');
    link.textContent = `${account.nombres} · Mi cuenta`;
    link.href = 'cuenta.html';
  } catch {
    // El enlace de acceso permanece visible cuando no hay una sesión activa.
  }
}

searchForm.addEventListener('submit', (event) => {
  event.preventDefault();
  searchDoctors(specialtyInput.value);
});

document.querySelectorAll('[data-specialty]').forEach((button) => {
  button.addEventListener('click', () => {
    specialtyInput.value = button.dataset.specialty;
    searchDoctors(specialtyInput.value);
  });
});

document.querySelector('#cta-search').addEventListener('click', () => {
  specialtyInput.focus();
  window.scrollTo({ top: 0, behavior: 'smooth' });
});

loadDashboard();
updateAuthLink();
