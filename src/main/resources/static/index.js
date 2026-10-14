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

function renderResults(doctors, etiqueta) {
  resultsSection.hidden = false;
  resultsSummary.textContent = `${doctors.length} recomendacion${doctors.length === 1 ? '' : 'es'} para ${etiqueta}.`;

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

/**
 * Busca medicos por especialidad exacta. `etiqueta` es el texto que se
 * muestra al paciente (puede ser distinto de `especialidad` cuando viene
 * de interpretar un sintoma, ej: "Cardiología (según tu síntoma)").
 */
async function searchDoctors(especialidad, etiqueta) {
  const normalizedSpecialty = especialidad.trim();
  const etiquetaMostrada = etiqueta || normalizedSpecialty;

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
    renderResults(payload, etiquetaMostrada);
    searchStatus.textContent = 'Búsqueda completada.';
    resultsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
  } catch (error) {
    resultsSummary.textContent = '';
    resultsList.innerHTML = `<p class="empty-state">${escapeHtml(error.message)}</p>`;
    searchStatus.textContent = 'No se pudo conectar con el servicio de recomendaciones.';
  }
}

// --- Interpretar especialidad o sintoma escrito libremente ---

const ESPECIALIDADES_CONOCIDAS = new Set(
  Array.from(document.querySelectorAll('[data-specialty]')).map((el) => el.dataset.specialty.toLowerCase())
);

async function interpretarComoEspecialidad(texto) {
  const response = await fetch(`${API_BASE_URL}/doctors/sugerir-especialidad`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sintoma: texto }),
  });
  const payload = await response.json();
  if (!response.ok) {
    throw new Error(payload.message || 'No se pudo interpretar el texto.');
  }
  return payload.especialidad;
}

async function buscarPorTextoLibre(texto) {
  const normalizado = texto.trim();
  if (!normalizado) {
    searchStatus.textContent = 'Escribe una especialidad o describe tu síntoma.';
    specialtyInput.focus();
    return;
  }

  // Si ya es el nombre exacto de una especialidad, no hace falta interpretar nada.
  if (ESPECIALIDADES_CONOCIDAS.has(normalizado.toLowerCase())) {
    searchDoctors(normalizado);
    return;
  }

  searchStatus.textContent = 'Analizando tu síntoma...';
  try {
    const especialidad = await interpretarComoEspecialidad(normalizado);
    specialtyInput.value = especialidad;
    await searchDoctors(especialidad, `${especialidad} (según tu síntoma)`);
  } catch (error) {
    searchStatus.textContent = escapeHtml(error.message);
  }
}

searchForm.addEventListener('submit', (event) => {
  event.preventDefault();
  buscarPorTextoLibre(specialtyInput.value);
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

// --- Maniqui: señalar la zona del cuerpo que duele ---

const ETIQUETAS_ZONA = {
  cabeza: 'Cabeza',
  ojos: 'Ojos',
  boca: 'Boca / dientes',
  pecho: 'Pecho',
  abdomen: 'Abdomen',
  pelvis: 'Zona pélvica',
  brazo: 'Brazo',
  rodilla: 'Rodilla / pierna',
  tobillo: 'Pie / tobillo',
};

async function activarZona(zona) {
  const etiqueta = ETIQUETAS_ZONA[zona] || zona;
  searchStatus.textContent = `Analizando "${etiqueta}"...`;
  try {
    const especialidad = await interpretarComoEspecialidad(etiqueta);
    specialtyInput.value = especialidad;
    await searchDoctors(especialidad, `${especialidad} (según "${etiqueta}")`);
  } catch (error) {
    searchStatus.textContent = escapeHtml(error.message);
  }
}

document.querySelectorAll('.hotspot').forEach((hotspot) => {
  hotspot.addEventListener('click', () => activarZona(hotspot.dataset.zona));
  hotspot.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      activarZona(hotspot.dataset.zona);
    }
  });
});
