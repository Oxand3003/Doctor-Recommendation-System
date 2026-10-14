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

// --- Sugerencia de especialidad por sintomas / maniqui ---

const diagTabs = document.querySelectorAll('.diag-tab');
const diagPanels = {
  texto: document.querySelector('#panel-texto'),
  cuerpo: document.querySelector('#panel-cuerpo'),
};
const sintomaForm = document.querySelector('#sintoma-form');
const sintomaInput = document.querySelector('#sintoma-input');
const diagStatus = document.querySelector('#diag-status');
const diagResultado = document.querySelector('#diag-resultado');
const diagBadge = document.querySelector('#diag-badge');
const diagEspecialidad = document.querySelector('#diag-especialidad');
const diagConfianzaFill = document.querySelector('#diag-confianza-fill');
const diagConfianzaTexto = document.querySelector('#diag-confianza-texto');
const diagCoincidencias = document.querySelector('#diag-coincidencias');
const diagAlternativas = document.querySelector('#diag-alternativas');
const diagVerDoctores = document.querySelector('#diag-ver-doctores');

const ETIQUETAS_ZONA = {
  cabeza: 'Cabeza',
  ojos: 'Ojos',
  boca: 'Boca / dientes',
  pecho: 'Pecho',
  abdomen: 'Abdomen',
  brazo: 'Brazo',
  rodilla: 'Rodilla / pierna',
  tobillo: 'Pie / tobillo',
};

diagTabs.forEach((tab) => {
  tab.addEventListener('click', () => {
    diagTabs.forEach((t) => {
      t.classList.remove('active');
      t.setAttribute('aria-selected', 'false');
    });
    tab.classList.add('active');
    tab.setAttribute('aria-selected', 'true');

    Object.entries(diagPanels).forEach(([key, panel]) => {
      panel.hidden = key !== tab.dataset.tab;
    });
  });
});

let especialidadSugerida = null;

async function sugerirEspecialidad(sintoma, etiquetaOrigen) {
  diagStatus.textContent = 'Analizando...';
  diagResultado.hidden = true;

  try {
    const response = await fetch(`${API_BASE_URL}/doctors/sugerir-especialidad`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sintoma }),
    });
    const payload = await response.json();
    if (!response.ok) {
      throw new Error(payload.message || 'No se pudo analizar el sintoma.');
    }
    renderSugerencia(payload, etiquetaOrigen);
    diagStatus.textContent = '';
  } catch (error) {
    diagStatus.textContent = escapeHtml(error.message);
  }
}

function renderSugerencia(sugerencia, etiquetaOrigen) {
  especialidadSugerida = sugerencia.especialidad;

  diagBadge.textContent = etiquetaOrigen
    ? `Según "${etiquetaOrigen}"`
    : 'Especialidad sugerida';
  diagEspecialidad.textContent = sugerencia.especialidad;

  const porcentaje = Math.round((sugerencia.confianza || 0) * 100);
  diagConfianzaFill.style.width = `${Math.max(porcentaje, 6)}%`;
  diagConfianzaTexto.textContent = `${porcentaje}% de coincidencia`;

  diagCoincidencias.textContent = sugerencia.coincidencias && sugerencia.coincidencias.length
    ? `Detectamos: ${sugerencia.coincidencias.join(', ')}`
    : 'No encontramos palabras clave exactas; esta es una sugerencia general.';

  diagAlternativas.innerHTML = '';
  if (sugerencia.alternativas && sugerencia.alternativas.length) {
    const titulo = document.createElement('span');
    titulo.className = 'diag-coincidencias';
    titulo.textContent = 'También podría ser:';
    diagAlternativas.appendChild(titulo);

    sugerencia.alternativas.forEach((alt) => {
      const chip = document.createElement('button');
      chip.type = 'button';
      chip.className = 'diag-alt-chip';
      chip.textContent = alt.especialidad;
      chip.addEventListener('click', () => {
        especialidadSugerida = alt.especialidad;
        renderSugerencia(alt, etiquetaOrigen);
      });
      diagAlternativas.appendChild(chip);
    });
  }

  diagResultado.hidden = false;
  diagResultado.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

sintomaForm.addEventListener('submit', (event) => {
  event.preventDefault();
  const texto = sintomaInput.value.trim();
  if (!texto) {
    diagStatus.textContent = 'Escribe un síntoma para analizarlo.';
    return;
  }
  sugerirEspecialidad(texto, null);
});

function activarZona(zona) {
  const etiqueta = ETIQUETAS_ZONA[zona] || zona;
  sugerirEspecialidad(etiqueta, etiqueta);
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

document.querySelectorAll('.maniqui-legend button').forEach((button) => {
  button.addEventListener('click', () => activarZona(button.dataset.zona));
});

diagVerDoctores.addEventListener('click', () => {
  if (!especialidadSugerida) {
    return;
  }
  specialtyInput.value = especialidadSugerida;
  searchDoctors(especialidadSugerida);
});