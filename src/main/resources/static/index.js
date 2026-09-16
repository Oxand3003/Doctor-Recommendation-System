const API_BASE_URL = window.MEDICERCA_API_URL || 'http://localhost:8080/api/v1';

const searchForm = document.querySelector('#search-form');
const specialtyInput = document.querySelector('#specialty-input');
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

function renderResults(doctors, specialty) {
  resultsSection.hidden = false;
  resultsSummary.textContent = `${doctors.length} recomendacion${doctors.length === 1 ? '' : 'es'} para ${specialty}.`;

  if (doctors.length === 0) {
    resultsList.innerHTML = '<p class="empty-state">No encontramos doctores disponibles para esta especialidad.</p>';
    return;
  }

  resultsList.innerHTML = doctors.map((doctor) => `
    <article class="result-card">
      <div>
        <h3>${escapeHtml(doctor.nombreCompleto)}</h3>
        <p class="result-specialty">${escapeHtml(doctor.especialidad)}</p>
      </div>
      <div class="result-meta">
        <span>★ ${escapeHtml(doctor.rating)}</span>
        <span>${escapeHtml(doctor.aniosExperiencia)} años exp.</span>
      </div>
      <p class="result-location">${escapeHtml(doctor.establecimiento)}</p>
      <a class="btn btn-primary" href="perfil-doctor.html?doctorId=${encodeURIComponent(doctor.doctorId)}">Ver perfil</a>
    </article>
  `).join('');
}

async function searchDoctors(specialty) {
  const normalizedSpecialty = specialty.trim();
  if (!normalizedSpecialty) {
    searchStatus.textContent = 'Escribe una especialidad para buscar.';
    specialtyInput.focus();
    return;
  }

  searchStatus.textContent = 'Buscando doctores disponibles...';
  resultsSection.hidden = false;
  resultsSummary.textContent = '';
  resultsList.innerHTML = '<p class="empty-state">Cargando recomendaciones...</p>';

  try {
    const response = await fetch(`${API_BASE_URL}/doctors/recomendar?especialidad=${encodeURIComponent(normalizedSpecialty)}&limite=5`);
    const payload = await response.json();
    if (!response.ok) {
      throw new Error(payload.message || 'No fue posible completar la búsqueda.');
    }
    renderResults(payload, normalizedSpecialty);
    searchStatus.textContent = 'Búsqueda completada.';
    resultsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
  } catch (error) {
    resultsSummary.textContent = '';
    resultsList.innerHTML = `<p class="empty-state">${escapeHtml(error.message)}</p>`;
    searchStatus.textContent = 'No se pudo conectar con el servicio de recomendaciones.';
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