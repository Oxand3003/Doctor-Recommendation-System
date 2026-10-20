/**
 * Buscador de médicos (inicio + perfil del paciente) y asistente de recomendaciones.
 * Todo se consulta al backend: /api/doctors/search (manual) y /api/chat/recommendations (asistente IA).
 */
document.addEventListener('DOMContentLoaded', function () {
  var form = document.querySelector('#search-form');
  var specialtyInput = document.querySelector('#specialty-input');
  var districtInput = document.querySelector('#district-input');
  var clinicInput = document.querySelector('#clinic-input');
  var dateInput = document.querySelector('#date-input');
  var timeInput = document.querySelector('#time-input');
  var status = document.querySelector('#search-status');
  var results = document.querySelector('#results');
  var resultsList = document.querySelector('#results-list');
  var resultsSummary = document.querySelector('#results-summary');
  var searchRequestId = 0;
  var manualLoaded = false;

  function escapeHtml(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (character) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[character];
    });
  }

  function pad(number) { return number < 10 ? '0' + number : String(number); }

  function todayIso() {
    var now = new Date();
    return now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate());
  }

  // No se pueden elegir fechas pasadas.
  Array.prototype.forEach.call(document.querySelectorAll('input[type="date"]'), function (input) {
    input.min = todayIso();
  });

  function setSelectValue(select, value) {
    if (!select) return;
    value = value || '';
    select.value = Array.prototype.some.call(select.options, function (option) {
      return option.value === value;
    }) ? value : '';
  }

  function bookingLink(profileUrl, date, times, wantedTime) {
    var params = new URLSearchParams();
    if (date) {
      params.set('fecha', date);
      // Se reserva un turno real: el pedido si existe y, si no, el primero libre.
      var hour = times && times.length ? (times.indexOf(wantedTime) > -1 ? wantedTime : times[0]) : '';
      if (hour) params.set('hora', hour);
    }
    return profileUrl + (params.toString() ? '?' + params.toString() : '') + '#booking-card';
  }

  /* ------------------------------------------------------------------ búsqueda manual */

  function renderSearchResults(doctors) {
    var date = dateInput && dateInput.value ? dateInput.value : '';
    if (!doctors.length) {
      resultsList.innerHTML = '<p class="empty-state">No encontramos médicos con esos filtros. Prueba otra combinación o pulsa Limpiar.</p>';
      resultsSummary.textContent = 'Sin resultados';
      return;
    }
    resultsSummary.textContent = doctors.length + (doctors.length === 1 ? ' médico encontrado.' : ' médicos encontrados, de mejor a menor calificación.');
    resultsList.innerHTML = doctors.map(function (doctor) {
      var rating = doctor.reviews ? '★ ' + Number(doctor.rating).toFixed(1) + ' (' + doctor.reviews + ')' : 'Sin calificaciones';
      var location = [doctor.clinic, doctor.district].filter(Boolean).join(' · ') || 'Ubicación no registrada';
      var times = doctor.availableTimes || [];
      var availability = date
        ? (times.length ? 'Libre: ' + times.slice(0, 4).join(', ') + (times.length > 4 ? ' y ' + (times.length - 4) + ' más' : '') : 'Sin horarios disponibles')
        : 'Elige una fecha para ver horarios';
      var profileUrl = (form.dataset.doctorBase || '/doctor/') + encodeURIComponent(doctor.id);
      var bookingUrl = bookingLink(profileUrl, date, times, timeInput ? timeInput.value : '');
      var avatar = doctor.photoUrl
        ? '<img class="result-photo" src="' + escapeHtml(doctor.photoUrl) + '" alt="Foto de ' + escapeHtml(doctor.name) + '" loading="lazy">'
        : '<span class="result-avatar" aria-hidden="true">' + escapeHtml(doctor.initials) + '</span>';
      return '<article class="result-card"><div class="result-card-heading">' + avatar + '<div><h3>' + escapeHtml(doctor.name) +
        '</h3><p class="result-specialty">' + escapeHtml(doctor.specialty) + '</p></div></div><div class="result-meta"><span>' + escapeHtml(rating) +
        '</span><span>' + (doctor.price === '—' ? 'Precio no registrado' : 'S/ ' + escapeHtml(doctor.price)) + '</span></div><p class="result-location">⌖ ' +
        escapeHtml(location) + '</p><p class="result-availability">◷ ' + escapeHtml(availability) + '</p><div class="result-actions"><a class="btn btn-ghost" href="' +
        escapeHtml(profileUrl) + '">Ver perfil</a><a class="btn btn-primary" href="' + escapeHtml(bookingUrl) + '">Reservar cita</a></div></article>';
    }).join('');
  }

  function searchDoctors(scrollToResults) {
    if (!form) return Promise.resolve();
    if (timeInput && timeInput.value && (!dateInput || !dateInput.value)) {
      status.textContent = 'Selecciona una fecha para filtrar por horario.';
      if (dateInput) dateInput.focus();
      return Promise.resolve();
    }
    var requestId = ++searchRequestId;
    manualLoaded = true;
    results.hidden = false;
    resultsList.innerHTML = '<p class="empty-state" role="status">Buscando médicos registrados…</p>';
    resultsSummary.textContent = 'Cargando resultados…';
    status.textContent = '';
    var params = new URLSearchParams();
    [['specialty', specialtyInput], ['district', districtInput], ['clinic', clinicInput], ['date', dateInput], ['time', timeInput]].forEach(function (pair) {
      if (pair[1] && pair[1].value.trim()) params.set(pair[0], pair[1].value.trim());
    });
    return fetch((form.dataset.searchEndpoint || '/api/doctors/search') + '?' + params.toString(), { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
      .then(function (response) {
        if (response.status === 400) throw new Error('Revisa los filtros: la fecha debe ser hoy o posterior y la hora necesita una fecha.');
        if (!response.ok) throw new Error('No se pudo completar la búsqueda. Inténtalo nuevamente.');
        return response.json();
      }).then(function (doctors) {
        if (requestId !== searchRequestId) return;
        renderSearchResults(doctors);
        if (scrollToResults) results.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }).catch(function (error) {
        if (requestId !== searchRequestId) return;
        resultsList.innerHTML = '<p class="empty-state" role="alert">' + escapeHtml(error.message || 'Ocurrió un error al buscar.') + '</p>';
        resultsSummary.textContent = 'No se pudieron cargar los resultados.';
      });
  }

  if (form) {
    form.addEventListener('submit', function (event) {
      event.preventDefault();
      searchDoctors(true);
    });
    form.addEventListener('reset', function () {
      window.setTimeout(function () { searchDoctors(false); }, 0);
    });
    [specialtyInput, districtInput, clinicInput, dateInput, timeInput].filter(Boolean).forEach(function (field) {
      field.addEventListener('change', function () { searchDoctors(false); });
    });
  }

  /* ------------------------------------------------------------------ asistente IA */

  var chatForm = document.querySelector('#chat-form');
  var chatReady = false;
  var chatStarted = false;
  var startChat = function () {};

  if (chatForm) {
    var chatInput = document.querySelector('#chat-input');
    var chatMessages = document.querySelector('#chat-messages');
    var chatOptions = document.querySelector('#chat-options');
    var chatNew = document.querySelector('#chat-new');
    var chatSection = document.querySelector('#asistente');
    var chatFilter = document.querySelector('#chat-specialty-filter');
    var districtFilter = document.querySelector('#chat-district-filter');
    var dateFilter = document.querySelector('#chat-date-filter');
    var timeFilter = document.querySelector('#chat-time-filter');
    var sortFilter = document.querySelector('#chat-sort-filter');
    var doctorCards = document.querySelector('#chat-doctor-cards');
    var summary = document.querySelector('#chat-results-summary');
    var chatFilters = [chatFilter, districtFilter, dateFilter, timeFilter, sortFilter].filter(Boolean);
    var chatEndpoint = chatSection && chatSection.dataset.chatEndpoint ? chatSection.dataset.chatEndpoint : '/api/chat/recommendations';
    var conversationEndpoint = chatSection && chatSection.dataset.conversationEndpoint ? chatSection.dataset.conversationEndpoint : '/api/chat/conversation';
    var chatHistory = [];
    var currentSort = 'rating';
    var restoredSpecialty = '';
    var loading = false;
    var talkQueue = [];        // turnos del paciente que esperan su turno (nunca se pierden)
    var silentQueued = false;  // refresco de filtros pendiente
    var filtersTouched = false;

    function fieldValue(field) {
      return field && typeof field.value === 'string' ? field.value.trim() : '';
    }

    function renderDoctors(data) {
      var doctors = data.doctors || [];
      var missing = data.missing || [];
      var byPrice = data.sort === 'price';
      if (summary) {
        var text = doctors.length
          ? doctors.length + (doctors.length === 1 ? ' médico recomendado' : ' médicos recomendados') +
            (byPrice ? ', del precio de consulta más bajo al más alto.' : ', de mejor a menor calificación.')
          : 'No hay médicos que coincidan con esos datos.';
        if (missing.length) text += ' Para afinar, dime: ' + missing.join(', ') + '.';
        summary.textContent = text;
      }
      if (!doctorCards) return;
      var doctorBase = chatSection && chatSection.dataset.doctorBase ? chatSection.dataset.doctorBase : '/doctor/';
      doctorCards.innerHTML = doctors.map(function (doctor) {
        var location = [doctor.clinic, doctor.district].filter(Boolean).join(' · ');
        var score = doctor.reviews ? '★ ' + Number(doctor.rating).toFixed(1) + ' · ' + doctor.reviews + ' opiniones' : 'Nuevo';
        var profileUrl = doctorBase + doctor.id;
        var date = data.date || '';
        var times = doctor.availableTimes || [];
        var wanted = data.time ? String(data.time).slice(0, 5) : '';
        var bookingUrl = bookingLink(profileUrl, date, times, wanted);
        var initials = doctor.name.split(/\s+/).filter(function (part) { return /^[A-Za-zÁÉÍÓÚÑáéíóúñ]/.test(part) && part.indexOf('.') === -1; })
          .map(function (part) { return part.charAt(0); }).slice(0, 2).join('');
        var price = doctor.price != null ? 'Consulta desde ' + doctor.priceText : doctor.priceText;
        return '<article class="recommendation-card"><div class="recommendation-card-top"><span class="recommendation-avatar" aria-hidden="true">' +
          escapeHtml(initials) + '</span><span class="recommendation-rating">' + escapeHtml(score) +
          '</span></div><h4>' + escapeHtml(doctor.name) + '</h4><p class="recommendation-specialty">' + escapeHtml(doctor.specialty) +
          '</p><p class="recommendation-price' + (doctor.price != null ? '' : ' is-unknown') + '">' + escapeHtml(price) +
          '</p><div class="recommendation-details"><span>⌖ ' + escapeHtml(location || 'Sede por confirmar') + '</span><span class="' +
          (doctor.available ? 'availability-open' : 'availability-note') + '">◷ ' + escapeHtml(doctor.availability) + '</span><span>＋ ' +
          escapeHtml(doctor.experienceYears) + ' años de experiencia</span></div><div class="result-actions"><a class="btn btn-ghost" href="' +
          escapeHtml(profileUrl) + '">Ver perfil</a><a class="btn btn-primary" href="' + escapeHtml(bookingUrl) + '">Reservar cita</a></div></article>';
      }).join('');
    }

    function addMessage(message, sender) {
      if (!chatMessages) return null;
      var item = document.createElement('div');
      item.className = 'chat-message ' + (sender === 'user' ? 'user-message' : 'assistant-message');
      if (sender !== 'user') {
        var avatar = document.createElement('span');
        avatar.className = 'chat-avatar';
        avatar.setAttribute('aria-hidden', 'true');
        avatar.textContent = 'M';
        item.appendChild(avatar);
      }
      var paragraph = document.createElement('p');
      paragraph.textContent = message;
      item.appendChild(paragraph);
      chatMessages.appendChild(item);
      chatMessages.scrollTop = chatMessages.scrollHeight;
      return item;
    }

    function showTyping() {
      var item = addMessage('Escribiendo…', 'assistant');
      if (item) item.classList.add('is-typing');
      return item;
    }

    /* Botones de respuesta rápida bajo la conversación. */
    function renderOptions(options) {
      if (!chatOptions) return;
      chatOptions.innerHTML = '';
      (options || []).forEach(function (option) {
        var button = document.createElement('button');
        button.type = 'button';
        var soft = option.action === 'MENU' || (option.action === 'ESPECIALIDAD' && option.label.indexOf('Cambiar') === 0);
        button.className = 'chat-chip' + (soft ? ' is-soft' : '');
        button.textContent = option.label;
        button.addEventListener('click', function () { chooseOption(option); });
        chatOptions.appendChild(button);
      });
      chatOptions.hidden = !chatOptions.children.length;
    }

    /* ---- Maniquí corporal (ver orientacion-corporal.js): pregunta el sexo y muestra la orientación por zona ---- */
    var lastOptions = [];
    var lastDoctors = [];
    var pendingSex = null;     // qué hacer cuando el paciente elige mujer / hombre

    function bodyApi() { return window.MCOrientacion || null; }

    function askSex(intro, onDone, echo) {
      pendingSex = { done: onDone, echo: !!echo };
      addMessage(intro, 'assistant');
      renderOptions([
        { label: '👩 Soy mujer', action: 'SEXO', value: 'mujer' },
        { label: '👨 Soy hombre', action: 'SEXO', value: 'hombre' },
        { label: '← Volver al menú', action: 'MENU', value: '' }
      ]);
    }

    /* El asistente ya orientó según los síntomas: se muestra el maniquí con la zona marcada. */
    function offerBody(data, symptoms) {
      var api = bodyApi();
      if (!api) return;
      var show = function () { api.mostrar({ symptoms: symptoms, specialty: data.specialty || '' }); };
      if (api.sexo()) { show(); return; }
      askSex('Para mostrarte el maniquí y marcar la zona donde tienes los síntomas, ¿eres mujer o hombre?',
        function () { renderOptions(lastOptions); show(); }, true);
    }

    function chooseOption(option) {
      if (option.action !== 'SEXO') pendingSex = null;
      if (option.action === 'SEXO') {
        var api = bodyApi();
        if (api) api.setSexo(option.value);
        var answer = option.label.replace(/^\S+\s/, '');          // "Soy mujer" (sin el emoji)
        var next = pendingSex;
        pendingSex = null;
        if (next && next.echo) addMessage(answer, 'user');
        if (next) next.done(answer);
        return;
      }
      if (option.action === 'SINTOMAS' && bodyApi()) {
        addMessage(option.label, 'user');
        askSex('Antes de empezar, ¿eres mujer o hombre? Así te muestro el maniquí correcto y marco la zona donde tienes los síntomas.',
          function (answer) {
            sendTurn(answer + '. Quiero orientarme según mis síntomas.', { action: 'SINTOMAS', label: answer });
          }, false);
        return;
      }
      if (option.action === 'ESCRIBIR') {
        if (chatInput) chatInput.focus();
        return;
      }
      if (!option.action) {                                   // texto rápido: "Mañana", "Hoy"...
        sendTurn(option.value || option.label, { label: option.label });
        return;
      }
      var extra = { action: option.action, label: option.label };
      if (option.action === 'ELEGIR') extra.specialty = option.value || '';
      if (option.action === 'ORDEN') extra.sort = option.value;
      sendTurn(option.label, extra);
    }

    function setBusy(busy) {
      if (chatOptions) Array.prototype.forEach.call(chatOptions.querySelectorAll('button'), function (b) { b.disabled = busy; });
    }

    /* Un turno del paciente (texto o botón): aparece al instante y se atiende en orden. */
    function sendTurn(text, extra) {
      extra = extra || {};
      pendingSex = null;
      addMessage(extra.label || text, 'user');
      chatHistory.push({ role: 'user', content: (extra.label || text).slice(0, 500) });
      chatHistory = chatHistory.slice(-10);
      talkQueue.push({ message: text, extra: extra, typing: showTyping() });
      setBusy(true);
      pump();
    }

    function currentBody(request) {
      var extra = request.extra || {};
      var specialty = extra.action === 'ELEGIR' ? (extra.specialty || null) : (fieldValue(chatFilter) || restoredSpecialty || null);
      return {
        message: request.message,
        specialty: specialty,
        district: fieldValue(districtFilter) || null,
        date: fieldValue(dateFilter) || null,
        time: fieldValue(timeFilter) || null,
        applyMessageFilters: request.talk,
        history: chatHistory.slice(-10),
        action: extra.action || null,
        sort: extra.sort || currentSort || null
      };
    }

    function pump() {
      if (loading) return;
      var request;
      if (talkQueue.length) {
        request = talkQueue.shift();
        request.talk = true;
      } else if (silentQueued) {
        silentQueued = false;
        request = { message: 'Muéstrame médicos recomendados.', extra: {}, talk: false };
      } else {
        return;
      }
      loading = true;
      chatStarted = true;
      filtersTouched = false;
      if (summary) summary.textContent = 'Buscando médicos y comprobando disponibilidad…';
      fetch(chatEndpoint, {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify(currentBody(request))
      }).then(function (response) {
        var contentType = response.headers.get('content-type') || '';
        if (!response.ok || contentType.indexOf('application/json') === -1) {
          if (response.status === 401 || response.redirected) throw new Error('Tu sesión no está activa. Vuelve a iniciar sesión con tu cuenta de paciente.');
          if (response.status === 403) throw new Error('Tu cuenta no tiene permiso para usar el asistente. Inicia sesión con una cuenta de paciente.');
          if (response.status === 400) throw new Error('Revisa la fecha: debe ser hoy o posterior.');
          throw new Error('No se pudieron cargar las recomendaciones. Inténtalo nuevamente.');
        }
        return response.json();
      }).then(function (data) {
        if (request.typing) request.typing.remove();
        applyResponse(data, request.talk, request);
      }).catch(function (error) {
        if (request.typing) request.typing.remove();
        var text = error && error.message ? error.message : 'No se pudieron cargar las recomendaciones.';
        if (request.talk) addMessage(text, 'assistant');
        if (summary) summary.textContent = text;
      }).finally(function () {
        loading = false;
        if (talkQueue.length || silentQueued) pump(); else setBusy(false);
      });
    }

    function applyResponse(data, talk, request) {
      currentSort = data.sort || currentSort;
      restoredSpecialty = '';
      // Si el paciente tocó un filtro mientras esperaba, se respeta lo que tiene en pantalla.
      var keepScreen = filtersTouched || talkQueue.length > 0;
      if (chatFilter) {
        var shown = keepScreen ? chatFilter.value : (data.specialty || '');
        chatFilter.innerHTML = '<option value="">Todas</option>' + (data.specialties || []).map(function (specialty) {
          return '<option value="' + escapeHtml(specialty) + '">' + escapeHtml(specialty) + '</option>';
        }).join('');
        chatFilter.value = shown;
      }
      if (!keepScreen) {
        setSelectValue(districtFilter, data.district || '');
        if (dateFilter) dateFilter.value = data.date || '';
        if (timeFilter) timeFilter.value = data.time ? String(data.time).slice(0, 5) : '';
        if (sortFilter) sortFilter.value = data.sort === 'price' ? 'price' : 'rating';
      } else if (filtersTouched) {
        silentQueued = true;
      }
      renderDoctors(data);
      renderOptions(data.options);
      lastOptions = data.options || [];
      lastDoctors = data.doctors || [];
      if (talk) {
        var reply = data.reply || 'Actualicé los médicos recomendados según los datos disponibles.';
        addMessage(reply, 'assistant');
        chatHistory.push({ role: 'assistant', content: reply.slice(0, 500) });
        chatHistory = chatHistory.slice(-10);
        // Texto libre con síntomas que el asistente supo orientar: se muestra el maniquí con la zona marcada.
        var freeText = request && !(request.extra && request.extra.action);
        if (freeText && data.suggestions && data.suggestions.length) offerBody(data, request.message);
      }
    }

    function refreshFilters() {
      filtersTouched = true;
      silentQueued = true;
      pump();
    }

    /* Memoria: se recupera la conversación guardada; "Nueva conversación" la borra. */
    function showConversation(conversation, keepFilters) {
      if (chatMessages) chatMessages.innerHTML = '';
      chatHistory = [];
      (conversation.messages || []).forEach(function (turn) {
        var mine = turn.role === 'user';
        addMessage(turn.content, mine ? 'user' : 'assistant');
        chatHistory.push({ role: mine ? 'user' : 'assistant', content: String(turn.content).slice(0, 500) });
      });
      chatHistory = chatHistory.slice(-10);
      var state = conversation.state || {};
      currentSort = state.sort || 'rating';
      restoredSpecialty = state.specialty || '';
      if (!keepFilters) {
        if (chatFilter) chatFilter.value = '';
        setSelectValue(districtFilter, state.district || '');
        if (dateFilter) dateFilter.value = state.date || '';
        if (timeFilter) timeFilter.value = state.time ? String(state.time).slice(0, 5) : '';
        if (sortFilter) sortFilter.value = currentSort === 'price' ? 'price' : 'rating';
      }
    }

    function loadConversation(method) {
      return fetch(conversationEndpoint, {
        method: method,
        credentials: 'same-origin',
        headers: { Accept: 'application/json' }
      }).then(function (response) {
        if (!response.ok) throw new Error('No se pudo cargar la conversación.');
        return response.json();
      }).then(function (conversation) {
        showConversation(conversation, false);
        silentQueued = true;
        pump();
      }).catch(function () {
        // Sin memoria disponible: el asistente igual funciona con una conversación nueva en pantalla.
        if (chatMessages && !chatMessages.children.length) {
          addMessage('¡Hola! Cuéntame qué síntomas o malestar tienes y te ayudo a encontrar médicos disponibles.', 'assistant');
        }
        silentQueued = true;
        pump();
      });
    }

    chatForm.addEventListener('submit', function (event) {
      event.preventDefault();
      var message = chatInput ? chatInput.value.trim() : '';
      if (!message) return;
      chatInput.value = '';
      chatInput.style.height = 'auto';
      chatInput.focus();
      sendTurn(message, {});
    });
    // Enter envía; Shift+Enter hace salto de línea.
    if (chatInput) {
      chatInput.addEventListener('keydown', function (event) {
        if (event.key === 'Enter' && !event.shiftKey) {
          event.preventDefault();
          if (chatForm.requestSubmit) chatForm.requestSubmit(); else chatForm.dispatchEvent(new Event('submit', { cancelable: true }));
        }
      });
      chatInput.addEventListener('input', function () {
        chatInput.style.height = 'auto';
        chatInput.style.height = Math.min(chatInput.scrollHeight, 120) + 'px';
      });
    }
    chatFilters.forEach(function (filter) {
      filter.addEventListener('change', function () {
        if (filter === sortFilter) currentSort = sortFilter.value;
        refreshFilters();
      });
    });
    if (chatNew) {
      chatNew.addEventListener('click', function () {
        if (!window.confirm('Se borrará esta conversación y el asistente ya no la recordará. ¿Empezar una nueva?')) return;
        talkQueue = [];
        silentQueued = false;
        pendingSex = null;
        if (bodyApi()) bodyApi().reiniciar();
        if (chatOptions) chatOptions.innerHTML = '';
        loadConversation('DELETE');
      });
    }
    startChat = function () {
      if (chatStarted) return;
      chatStarted = true;
      loadConversation('GET');
    };
    // Puente para el maniquí: filtrar médicos por una especialidad y leer los que se muestran (para el PDF).
    window.MCChat = {
      elegirEspecialidad: function (specialty, label) {
        if (!specialty) return;
        sendTurn(label || specialty, { action: 'ELEGIR', specialty: specialty, label: label || specialty });
      },
      doctores: function () { return lastDoctors.slice(); }
    };
    chatReady = true;
  }

  /* ------------------------------------------------------------------ pestañas del perfil (IA / manual) */

  var tabs = document.querySelectorAll('.mode-tab');
  var aiPanel = document.querySelector('#ai-search-panel');
  var manualPanel = document.querySelector('#manual-search-panel');

  function activateMode(mode, updateUrl) {
    if (!aiPanel || !manualPanel) return;
    var ia = mode === 'ia';
    aiPanel.classList.toggle('is-hidden', !ia);
    manualPanel.classList.toggle('is-hidden', ia);
    Array.prototype.forEach.call(tabs, function (tab) {
      var active = tab.dataset.mode === mode;
      tab.classList.toggle('is-active', active);
      tab.setAttribute('aria-selected', String(active));
    });
    if (updateUrl && window.history && window.history.replaceState) {
      window.history.replaceState(null, '', window.location.pathname + '?modo=' + mode + '#patient-search');
    }
    if (ia) { if (chatReady) startChat(); } else if (form && !manualLoaded) { searchDoctors(false); }
  }

  Array.prototype.forEach.call(tabs, function (tab) {
    tab.addEventListener('click', function () { activateMode(tab.dataset.mode, true); });
  });

  if (aiPanel && manualPanel) {
    // Abre el modo indicado por el navbar (?modo=ia | ?modo=manual) y carga datos reales de inmediato.
    activateMode(aiPanel.classList.contains('is-hidden') ? 'manual' : 'ia', false);
  }

  // Botón inferior del inicio: vuelve al buscador.
  var cta = document.querySelector('#cta-search');
  if (cta) {
    cta.addEventListener('click', function () {
      if (specialtyInput) specialtyInput.focus();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  }
  /* Formulario de contacto: contador de caracteres y botón bloqueado mientras se envía. */
  (function () {
    var form = document.getElementById('contact-form');
    if (!form) return;
    var area = document.getElementById('c-mensaje');
    var count = document.getElementById('c-count');
    var btn = document.getElementById('contact-submit');
    function actualizar() { if (area && count) count.textContent = area.value.length; }
    if (area) { area.addEventListener('input', actualizar); actualizar(); }
    form.addEventListener('submit', function (e) {
      if (!form.checkValidity()) { e.preventDefault(); form.reportValidity(); return; }
      if (btn) { btn.disabled = true; btn.textContent = 'Enviando…'; }
    });
    // Tras enviar, la página vuelve a #contacto: deja visible el mensaje de éxito o error.
    if (location.hash === '#contacto' && form.querySelector('.contact-flash')) {
      form.scrollIntoView({ block: 'center' });
    }
  })();
  /* Aparición suave de las secciones informativas al hacer scroll. */
  (function () {
    var items = document.querySelectorAll('.reveal');
    if (!items.length) return;
    if (!('IntersectionObserver' in window) || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    document.documentElement.classList.add('reveal-ready');
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) { entry.target.classList.add('in'); io.unobserve(entry.target); }
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });
    Array.prototype.forEach.call(items, function (el) { io.observe(el); });
  })();
});
