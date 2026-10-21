/**
 * Panel de administración conectado al backend.
 * Datos:    GET  admin/api/data
 * Acciones: POST admin/api/applications/{id}/approve | reject
 *           POST admin/api/doctors/{id}/suspend | reactivate
 *           POST admin/api/patients/{id}/suspend | reactivate
 * Consultas: GET  admin/api/consultas
 *           POST admin/api/consultas/{id}/reply | attend | reopen
 */
document.addEventListener('DOMContentLoaded', function () {

  var applications = [];
  var doctors = [];
  var patientList = [];
  var patients = 0;
  var activity = [];
  var consultas = [];

  /* ---------------- API ---------------- */
  function api(method, url, params) {
    var headers = { 'Accept': 'application/json' };
    var opts = { method: method, headers: headers, credentials: 'same-origin' };
    var csrf = document.getElementById('csrf');
    if (method !== 'GET' && csrf) headers[csrf.dataset.header] = csrf.value;
    if (params) {
      headers['Content-Type'] = 'application/x-www-form-urlencoded';
      opts.body = new URLSearchParams(params).toString();
    }
    return fetch(url, opts).then(function (res) {
      if (res.redirected) { window.location.href = '/login'; throw new Error('Sesión expirada'); }
      return res.json().catch(function () { return {}; }).then(function (data) {
        if (!res.ok) throw new Error(data.message || ('Error ' + res.status));
        return data;
      });
    });
  }

  function load() {
    return api('GET', 'api/data').then(function (data) {
      applications = data.applications || [];
      doctors = data.doctors || [];
      patientList = data.patientList || [];
      patients = data.patients || 0;
      activity = data.activity || [];
      renderAll();
      // Las consultas van aparte: si fallan, el resto del panel sigue funcionando.
      return api('GET', 'api/consultas').then(function (list) {
        consultas = Array.isArray(list) ? list : [];
        renderAll();
      }).catch(function () { toast('No se pudieron cargar las consultas.'); });
    }).catch(function (e) { toast(e.message); });
  }

  /** Ejecuta una acción, avisa del resultado y recarga los datos. */
  function act(url, params, okMessage) {
    return api('POST', url, params)
      .then(function (res) {
        // Si la decisión se guardó pero el correo al médico falló, el servidor manda "warning".
        toast(res && res.warning ? okMessage + ' ' + res.warning : okMessage, res && res.warning ? 7000 : 0);
        return load();
      })
      .catch(function (e) { toast(e.message); return load(); });
  }

  var state = { appFilter: 'PENDIENTE', appQuery: '', docFilter: 'ALL', docQuery: '', patFilter: 'ALL', patQuery: '', conFilter: 'PENDIENTE', conQuery: '', currentCon: null, mailId: null, waClicked: {}, current: null, pendingConfirm: null };

  /* ---------------- Helpers ---------------- */
  function $(sel) { return document.querySelector(sel); }
  function esc(text) {
    var d = document.createElement('div');
    d.textContent = text == null ? '' : String(text);
    return d.innerHTML;
  }
  function initials(name) {
    return name.split(' ').filter(Boolean).slice(0, 2).map(function (p) { return p[0]; }).join('').toUpperCase();
  }
  function norm(s) { return String(s).toLowerCase(); }
  function statusPill(estado) {
    return '<span class="pill-status st-' + norm(estado) + '">' + esc(estado.charAt(0) + norm(estado).slice(1)) + '</span>';
  }
  function person(name, sub) {
    return '<div class="person"><div class="person-av">' + esc(initials(name)) + '</div><div><b>' + esc(name) + '</b><span>' + esc(sub) + '</span></div></div>';
  }

  var toastTimer;
  function toast(message, ms) {
    var el = $('#toast');
    el.textContent = message;
    el.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.classList.remove('show'); }, ms || 3200);
  }

  function openModal(id) { var m = $(id); m.classList.add('active'); m.setAttribute('aria-hidden', 'false'); }
  function closeModal(m) { m.classList.remove('active'); m.setAttribute('aria-hidden', 'true'); }
  function closeAllModals() { document.querySelectorAll('.adm-modal.active').forEach(closeModal); }

  /* ---------------- Navigation ---------------- */
  var titles = {
    dashboard: ['Dashboard', 'Resumen general de la plataforma.'],
    applications: ['Solicitudes de médicos', 'Revisa credenciales y aprueba o rechaza cuentas profesionales.'],
    doctors: ['Cuentas de médicos', 'Gestiona el acceso de los médicos registrados.'],
    patients: ['Cuentas de pacientes', 'Activa o suspende el acceso de los pacientes registrados.'],
    consultas: ['Consultas', 'Mensajes enviados desde el formulario del inicio: contáctalos y archívalos al atenderlos.']
  };

  function showView(name) {
    document.querySelectorAll('.adm-view').forEach(function (v) { v.hidden = v.id !== 'view-' + name; });
    document.querySelectorAll('.adm-link[data-view]').forEach(function (b) {
      var on = b.dataset.view === name;
      b.classList.toggle('active', on);
      if (on) b.setAttribute('aria-current', 'page'); else b.removeAttribute('aria-current');
    });
    $('#view-title').textContent = titles[name][0];
    $('#view-subtitle').textContent = titles[name][1];
    closeSidebar();
    window.scrollTo({ top: 0 });
    try { history.replaceState(null, '', '#' + name); } catch (e) { /* ignore */ }
  }

  document.querySelectorAll('.adm-link[data-view]').forEach(function (b) {
    b.addEventListener('click', function () { showView(b.dataset.view); });
  });
  document.addEventListener('click', function (e) {
    var go = e.target.closest('[data-goto]');
    if (go) showView(go.dataset.goto);
  });

  /* Mobile sidebar */
  function closeSidebar() { $('#adm-sidebar').classList.remove('open'); $('#adm-backdrop').classList.remove('show'); }
  $('#adm-burger').addEventListener('click', function () {
    $('#adm-sidebar').classList.add('open');
    $('#adm-backdrop').classList.add('show');
  });
  $('#adm-backdrop').addEventListener('click', closeSidebar);

  /* ---------------- Rendering ---------------- */
  function countBy(list, estado) { return list.filter(function (x) { return x.estado === estado; }).length; }

  function renderCounters() {
    var pending = countBy(applications, 'PENDIENTE');
    var navCount = $('#nav-pending-count');
    navCount.textContent = pending;
    navCount.dataset.zero = pending === 0 ? 'true' : 'false';
    $('#kpi-pending').textContent = pending;
    $('#kpi-active').textContent = countBy(doctors, 'ACTIVO');
    $('#kpi-patients').textContent = patients;
    $('#kpi-suspended').textContent = countBy(doctors, 'SUSPENDIDO');
    $('#seg-pending').textContent = pending;
    $('#seg-approved').textContent = countBy(applications, 'APROBADA');
    $('#seg-rejected').textContent = countBy(applications, 'RECHAZADA');
    $('#seg-pat-all').textContent = patientList.length;
    $('#seg-pat-active').textContent = countBy(patientList, 'ACTIVO');
    $('#seg-pat-suspended').textContent = countBy(patientList, 'SUSPENDIDO');
    var conPending = consultas.filter(isPendingCon).length;
    var conNav = $('#nav-consultas-count');
    conNav.textContent = conPending;
    conNav.dataset.zero = conPending === 0 ? 'true' : 'false';
    $('#seg-con-pending').textContent = conPending;
    $('#seg-con-history').textContent = consultas.length - conPending;
  }

  function renderDashboard() {
    var recent = applications.filter(function (a) { return a.estado === 'PENDIENTE'; }).slice(0, 4);
    $('#dash-recent').innerHTML = recent.length ? recent.map(function (a) {
      return '<li>' + person(a.nombre, a.especialidad).replace('class="person"', 'class="person who"') +
        '<button class="abtn" type="button" data-detail="' + a.id + '">Revisar</button></li>';
    }).join('') : '<li class="mini-empty">No hay solicitudes pendientes.</li>';

    var counts = {};
    doctors.forEach(function (d) { counts[d.especialidad] = (counts[d.especialidad] || 0) + 1; });
    var max = Math.max.apply(null, Object.keys(counts).map(function (k) { return counts[k]; }).concat([1]));
    $('#dash-specialties').innerHTML = Object.keys(counts).map(function (k) {
      return '<div class="bar-row"><span>' + esc(k) + '</span><div class="bar-track"><div class="bar-fill" style="width:' + (counts[k] / max * 100) + '%"></div></div><b>' + counts[k] + '</b></div>';
    }).join('');

    $('#dash-activity').innerHTML = activity.length ? activity.map(function (a) {
      return '<li><span class="act-dot' + (a.color ? ' act-' + a.color : '') + '"></span><div><b>' + esc(a.titulo) + ':</b> ' +
        esc(a.detalle) + '<time>' + esc(a.fecha) + '</time></div></li>';
    }).join('') : '<li class="mini-empty">Aún no hay actividad.</li>';
  }

  function renderApplications() {
    var q = norm(state.appQuery);
    var rows = applications.filter(function (a) {
      return a.estado === state.appFilter &&
        (!q || norm(a.nombre + ' ' + a.cmp + ' ' + a.dni).indexOf(q) !== -1);
    });
    $('#app-body').innerHTML = rows.map(function (a) {
      var actions = '<button class="abtn" type="button" data-detail="' + a.id + '">Ver detalle</button>';
      if (a.estado === 'PENDIENTE') {
        actions += '<button class="abtn abtn-ok" type="button" data-approve="' + a.id + '">Aprobar</button>' +
                   '<button class="abtn abtn-danger" type="button" data-reject="' + a.id + '">Rechazar</button>';
      }
      return '<tr><td class="td-main">' + person(a.nombre, a.email) + '</td>' +
        '<td data-label="CMP">' + esc(a.cmp) + '</td>' +
        '<td data-label="Especialidad">' + esc(a.especialidad) + '</td>' +
        '<td data-label="Enviada">' + esc(a.fecha) + '</td>' +
        '<td data-label="Estado">' + statusPill(a.estado) + '</td>' +
        '<td class="td-actions"><div class="row-actions">' + actions + '</div></td></tr>';
    }).join('');
    $('#app-empty').hidden = rows.length > 0;
  }

  function renderDoctors() {
    var q = norm(state.docQuery);
    var rows = doctors.filter(function (d) {
      return (state.docFilter === 'ALL' || d.estado === state.docFilter) &&
        (!q || norm(d.nombre + ' ' + d.email + ' ' + d.especialidad).indexOf(q) !== -1);
    });
    $('#doc-body').innerHTML = rows.map(function (d) {
      var toggle = d.estado === 'ACTIVO'
        ? '<button class="abtn" type="button" data-suspend="' + d.id + '">Suspender</button>'
        : '<button class="abtn abtn-ok" type="button" data-reactivate="' + d.id + '">Reactivar</button>';
      return '<tr><td class="td-main">' + person('Dr(a). ' + d.nombre, d.email) + '</td>' +
        '<td data-label="Especialidad">' + esc(d.especialidad) + '</td>' +
        '<td data-label="Establecimiento">' + esc(d.establecimiento) + '</td>' +
        '<td data-label="Valoración">★ ' + d.rating.toFixed(1) + '</td>' +
        '<td data-label="Estado">' + statusPill(d.estado) + '</td>' +
        '<td class="td-actions"><div class="row-actions">' +
        '<a class="abtn" href="/doctor/' + d.id + '">Ver perfil</a>' + toggle + '</div></td></tr>';
    }).join('');
    $('#doc-empty').hidden = rows.length > 0;
  }

  function renderPatients() {
    var q = norm(state.patQuery);
    var rows = patientList.filter(function (p) {
      return (state.patFilter === 'ALL' || p.estado === state.patFilter) &&
        (!q || norm(p.nombre + ' ' + p.email + ' ' + p.dni).indexOf(q) !== -1);
    });
    $('#pat-body').innerHTML = rows.map(function (p) {
      var toggle = p.estado === 'ACTIVO'
        ? '<button class="abtn" type="button" data-pat-suspend="' + p.id + '">Suspender</button>'
        : '<button class="abtn abtn-ok" type="button" data-pat-reactivate="' + p.id + '">Reactivar</button>';
      return '<tr><td class="td-main">' + person(p.nombre, p.email) + '</td>' +
        '<td data-label="DNI">' + esc(p.dni) + '</td>' +
        '<td data-label="Teléfono">' + esc(p.tel || '—') + '</td>' +
        '<td data-label="Registro">' + esc(p.registro || '—') + '</td>' +
        '<td data-label="Último acceso">' + esc(p.ultimoAcceso) + '</td>' +
        '<td data-label="Estado">' + statusPill(p.estado) + '</td>' +
        '<td class="td-actions"><div class="row-actions">' + toggle + '</div></td></tr>';
    }).join('');
    $('#pat-empty').hidden = rows.length > 0;
  }

  /* ---------------- Consultas ---------------- */
  function isPendingCon(c) { return c.estado !== 'ATENDIDA'; }

  function conMatches(c) {
    var q = norm(state.conQuery);
    return !q || norm([c.nombres, c.correo, c.telefono, c.motivo].join(' ')).indexOf(q) !== -1;
  }

  /** Enlace de WhatsApp: solo si el servidor entregó un wa.me válido. */
  function waLink(c, cls) {
    var url = c.whatsappUrl && c.whatsappUrl.indexOf('https://wa.me/') === 0 ? c.whatsappUrl.replace(/"/g, '&quot;') : '';
    return url
      ? '<a class="abtn abtn-wa" href="' + url + '" target="_blank" rel="noopener noreferrer" data-con-wa="' + c.id + '">WhatsApp</a>'
      : '<span class="abtn is-disabled" title="El número no sirve para WhatsApp">WhatsApp</span>';
  }
  function mailBtn(c) {
    return '<button class="abtn abtn-mail" type="button" data-con-mail="' + c.id + '">Correo</button>';
  }
  function conPerson(c) {
    return person(c.nombres, c.correo) + (c.conCuenta ? '<span class="con-flag">Tiene cuenta</span>' : '');
  }

  function renderConsultas() {
    var showHistory = state.conFilter === 'HISTORIAL';
    $('#con-pending-card').hidden = showHistory;
    $('#con-history-card').hidden = !showHistory;

    var pending = consultas.filter(function (c) { return isPendingCon(c) && conMatches(c); });
    $('#con-body').innerHTML = pending.map(function (c) {
      return '<tr><td class="td-main">' + conPerson(c) + '</td>' +
        '<td data-label="Contacto"><div class="con-phone"><b>' + esc(c.telefono) + '</b><div class="con-contact">' + waLink(c) + mailBtn(c) + '</div></div></td>' +
        '<td data-label="Motivo">' + esc(c.motivo) + '</td>' +
        '<td data-label="Consulta"><span class="con-snippet">' + esc(c.mensaje) + '</span></td>' +
        '<td data-label="Recibida">' + esc(c.fecha) + '</td>' +
        '<td class="td-actions"><div class="row-actions"><button class="abtn abtn-ok" type="button" data-con-detail="' + c.id + '">Ver y atender</button></div></td></tr>';
    }).join('');
    $('#con-empty').hidden = pending.length > 0;

    var history = consultas.filter(function (c) { return !isPendingCon(c) && conMatches(c); })
      .sort(function (a, b) { return String(b.fechaAtencion).localeCompare(String(a.fechaAtencion)); });
    $('#con-hist-body').innerHTML = history.map(function (c) {
      return '<tr><td class="td-main">' + conPerson(c) + '</td>' +
        '<td data-label="Motivo">' + esc(c.motivo) + '</td>' +
        '<td data-label="Recibida">' + esc(c.fecha) + '</td>' +
        '<td data-label="Atendida">' + esc(c.fechaAtencion || '—') + '</td>' +
        '<td data-label="Medio">' + esc(c.canal || '—') + '</td>' +
        '<td data-label="Atendida por">' + esc(c.atendidaPor || '—') + '</td>' +
        '<td class="td-actions"><div class="row-actions"><button class="abtn" type="button" data-con-detail="' + c.id + '">Ver</button>' +
        '<button class="abtn" type="button" data-con-reopen="' + c.id + '">Reabrir</button></div></td></tr>';
    }).join('');
    $('#con-hist-empty').hidden = history.length > 0;
  }

  var CHANNELS = [['WHATSAPP', 'WhatsApp'], ['CORREO', 'Correo'], ['LLAMADA', 'Llamada'], ['OTRO', 'Otro']];

  function openConsulta(c) {
    if (!c) return;
    state.currentCon = c;
    var pending = isPendingCon(c);

    $('#con-modal-title').textContent = 'Consulta de ' + c.nombres;
    $('#con-hero').innerHTML =
      '<div class="person-av detail-av">' + esc(initials(c.nombres)) + '</div>' +
      '<div class="detail-hero-info"><b>' + esc(c.nombres) + '</b><span>' + esc(c.correo) + '</span>' +
      '<div class="detail-chips">' + statusPill(c.estado) + '<span class="chip">' + esc(c.motivo) + '</span>' +
      (c.conCuenta ? '<span class="chip">Tiene cuenta</span>' : '') + '</div></div>';

    var sections = [
      '<section class="dsec"><h3><span aria-hidden="true">' + ICONS.user + '</span>Contacto</h3><dl>' +
        field('Teléfono', c.telefono) + field('Recibida', c.fecha) + field('Correo', c.correo, true) + '</dl>' +
        '<div class="con-contact-big">' + waLink(c) + mailBtn(c) + '</div></section>',
      '<section class="dsec"><h3><span aria-hidden="true">' + ICONS.pulse + '</span>Mensaje del usuario</h3><p class="con-msg">' + esc(c.mensaje) + '</p></section>'
    ];
    if (c.respuesta) {
      sections.push('<section class="dsec"><h3><span aria-hidden="true">' + ICONS.cap + '</span>Respuesta enviada por correo</h3><p class="con-msg">' + esc(c.respuesta) + '</p></section>');
    }
    if (!pending) {
      sections.push(section('Atención', ICONS.user, [field('Atendida', c.fechaAtencion), field('Medio', c.canal), field('Atendida por', c.atendidaPor, true)]));
    }
    $('#con-sections').innerHTML = sections.join('');

    var actions;
    if (pending) {
      var preset = state.waClicked[c.id] ? 'WHATSAPP' : 'OTRO';
      var opts = CHANNELS.map(function (ch) {
        return '<option value="' + ch[0] + '"' + (ch[0] === preset ? ' selected' : '') + '>' + ch[1] + '</option>';
      }).join('');
      actions = '<div class="con-attend"><select id="con-channel" aria-label="Medio por el que se atendió">' + opts + '</select>' +
        '<button type="button" class="btn btn-primary" id="con-attend-ok">Marcar como atendida</button></div>' +
        '<button type="button" class="btn btn-ghost" data-close>Cerrar</button>';
    } else {
      actions = '<button type="button" class="btn btn-ghost" id="con-reopen-ok">Reabrir consulta</button><button type="button" class="btn btn-primary" data-close>Cerrar</button>';
    }
    $('#con-actions').innerHTML = actions;
    openModal('#con-modal');
  }

  function openMail(c) {
    if (!c) return;
    state.mailId = c.id;
    $('#mail-to').textContent = c.nombres + ' <' + c.correo + '>';
    $('#mail-subject').value = 'Respuesta a tu consulta en MediCerca';
    $('#mail-message').value = '';
    $('#mail-count').textContent = '0';
    var archive = $('#mail-archive');
    archive.checked = isPendingCon(c);
    archive.disabled = !isPendingCon(c);
    $('#mail-send').disabled = false;
    $('#mail-send').textContent = 'Enviar correo';
    openModal('#mail-modal');
    setTimeout(function () { $('#mail-message').focus(); }, 60);
  }

  $('#mail-message').addEventListener('input', function (e) { $('#mail-count').textContent = e.target.value.length; });

  $('#mail-send').addEventListener('click', function () {
    var c = findById(consultas, state.mailId);
    if (!c) return;
    var subject = $('#mail-subject').value.trim();
    var message = $('#mail-message').value.trim();
    if (!subject) { $('#mail-subject').focus(); toast('Escribe el asunto del correo.'); return; }
    if (message.length < 5) { $('#mail-message').focus(); toast('Escribe el mensaje que quieres enviar.'); return; }
    var btn = this;
    var archive = $('#mail-archive').checked && !$('#mail-archive').disabled;
    btn.disabled = true;
    btn.textContent = 'Enviando…';
    api('POST', 'api/consultas/' + c.id + '/reply', { subject: subject, message: message, archive: archive ? 'true' : 'false' })
      .then(function () {
        closeAllModals();
        toast('Correo enviado a ' + c.correo + '.' + (archive ? ' La consulta pasó al historial.' : ''), 5000);
        return load();
      })
      .catch(function (e) {
        // No se envió: el modal queda abierto con el texto para reintentar.
        btn.disabled = false;
        btn.textContent = 'Enviar correo';
        toast(e.message, 6000);
      });
  });

  $('#con-actions').addEventListener('click', function (e) {
    var c = state.currentCon;
    if (!c) return;
    if (e.target.id === 'con-attend-ok') {
      var channel = $('#con-channel').value;
      closeAllModals();
      act('api/consultas/' + c.id + '/attend', { channel: channel }, 'Consulta de ' + c.nombres + ' atendida. Pasó al historial.');
    }
    if (e.target.id === 'con-reopen-ok') {
      closeAllModals();
      act('api/consultas/' + c.id + '/reopen', null, 'Consulta devuelta a pendientes.');
    }
  });

  function renderAll() { renderCounters(); renderDashboard(); renderApplications(); renderDoctors(); renderPatients(); renderConsultas(); }

  /* ---------------- Filters & search ---------------- */
  function bindSegment(selector, key, after) {
    document.querySelectorAll(selector + ' .seg-btn').forEach(function (btn) {
      btn.addEventListener('click', function () {
        document.querySelectorAll(selector + ' .seg-btn').forEach(function (b) { b.classList.toggle('active', b === btn); });
        state[key] = btn.dataset.filter;
        after();
      });
    });
  }
  bindSegment('#app-filter', 'appFilter', renderApplications);
  bindSegment('#doc-filter', 'docFilter', renderDoctors);
  bindSegment('#pat-filter', 'patFilter', renderPatients);
  bindSegment('#con-filter', 'conFilter', renderConsultas);
  $('#app-search').addEventListener('input', function (e) { state.appQuery = e.target.value; renderApplications(); });
  $('#doc-search').addEventListener('input', function (e) { state.docQuery = e.target.value; renderDoctors(); });
  $('#pat-search').addEventListener('input', function (e) { state.patQuery = e.target.value; renderPatients(); });
  $('#con-search').addEventListener('input', function (e) { state.conQuery = e.target.value; renderConsultas(); });

  /* ---------------- Business actions (UI only) ---------------- */
  function findById(list, id) { return list.filter(function (x) { return x.id === Number(id); })[0]; }

  function approve(app) {
    return act('api/applications/' + app.id + '/approve', null, 'Solicitud de ' + app.nombre + ' aprobada. La cuenta ya está activa.');
  }
  function reject(app, reason) {
    return act('api/applications/' + app.id + '/reject', { reason: reason }, 'Solicitud de ' + app.nombre + ' rechazada.');
  }

  function askConfirm(title, text, okLabel, danger, onOk) {
    $('#confirm-title').textContent = title;
    $('#confirm-text').textContent = text;
    var ok = $('#confirm-ok');
    ok.textContent = okLabel;
    ok.className = 'btn ' + (danger ? 'btn-danger' : 'btn-primary');
    state.pendingConfirm = onOk;
    openModal('#confirm-modal');
  }
  $('#confirm-ok').addEventListener('click', function () {
    var fn = state.pendingConfirm;
    state.pendingConfirm = null;
    closeAllModals();
    if (fn) fn();
  });

  /* Detail modal */

  /**
   * Convierte el enlace de sustento en una URL segura (solo http/https).
   * Si el médico escribió "drive.google.com/..." sin protocolo, se completa con https://.
   * Devuelve '' cuando no hay enlace utilizable.
   */
  function safeUrl(raw) {
    var text = raw == null ? '' : String(raw).trim();
    if (!text || text === 'null' || text === 'undefined') return '';
    if (!/^[a-z][a-z0-9+.-]*:/i.test(text)) text = 'https://' + text.replace(/^\/+/, '');
    try {
      var url = new URL(text);
      return (url.protocol === 'http:' || url.protocol === 'https:') && url.hostname ? url.href : '';
    } catch (e) { return ''; }
  }

  function field(label, value, wide) {
    return '<div class="df' + (wide ? ' df-wide' : '') + '"><dt>' + esc(label) + '</dt><dd>' + esc(value === '' || value == null ? '—' : value) + '</dd></div>';
  }
  var ICONS = {
    user: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>',
    cap: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 10 12 5 2 10l10 5z"/><path d="M6 12v5c3 2 9 2 12 0v-5"/></svg>',
    pulse: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2.5 12.5h4.2l2.3-6 3.4 11.2 2.4-5.2h6.7"/></svg>',
    warn: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3 2 20h20z"/><path d="M12 10v4M12 17.5v.01"/></svg>'
  };
  function section(title, icon, fields) {
    return '<section class="dsec"><h3><span aria-hidden="true">' + icon + '</span>' + esc(title) + '</h3><dl>' + fields.join('') + '</dl></section>';
  }

  function openDetail(app, rejectMode) {
    if (!app) return;
    state.current = app;
    var estadoTxt = app.estado.charAt(0) + norm(app.estado).slice(1);

    $('#detail-hero').innerHTML =
      '<div class="person-av detail-av">' + esc(initials(app.nombre)) + '</div>' +
      '<div class="detail-hero-info"><b>' + esc(app.nombre) + '</b><span>' + esc(app.email) + '</span>' +
      '<div class="detail-chips">' + statusPill(app.estado) +
      '<span class="chip">' + esc(/^cmp/i.test(app.cmp) ? app.cmp : 'CMP ' + app.cmp) + '</span><span class="chip">' + esc(app.especialidad) + '</span></div></div>';

    var sections = [
      section('Datos personales', ICONS.user, [field('DNI', app.dni), field('Teléfono', app.tel), field('Correo', app.email, true)]),
      section('Formación profesional', ICONS.cap, [
        field('CMP', app.cmp), field('RNE', app.rne || 'No declarado'), field('Título', app.titulo, true),
        field('Universidad', app.universidad, true), field('Año de egreso', app.egreso), field('Experiencia', app.exp + ' años')]),
      section('Práctica médica', ICONS.pulse, [
        field('Especialidad', app.especialidad), field('Establecimiento', app.establecimiento || 'Aún no tiene'), field('Enviada', app.fecha)])
    ];
    if (app.motivo) {
      sections.push('<section class="dsec dsec-warn"><h3><span aria-hidden="true">' + ICONS.warn + '</span>Motivo de rechazo</h3><p>' + esc(app.motivo) + '</p></section>');
    }
    $('#detail-grid').innerHTML = sections.join('');

    /* Enlace de sustento: se abre en pestaña nueva con rel seguro; si no es válido se deshabilita. */
    var link = $('#detail-proof');
    var proof = safeUrl(app.sustento);
    var card = $('#proof-card');
    if (proof) {
      link.href = proof;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      link.removeAttribute('aria-disabled');
      link.classList.remove('is-disabled');
      $('#detail-proof-url').textContent = proof.length > 70 ? proof.slice(0, 67) + '…' : proof;
      link.title = proof;
      card.classList.remove('proof-missing');
    } else {
      link.removeAttribute('href');
      link.setAttribute('aria-disabled', 'true');
      link.classList.add('is-disabled');
      link.removeAttribute('title');
      $('#detail-proof-url').textContent = 'El médico no registró un enlace válido.';
      card.classList.add('proof-missing');
    }

    $('#reject-box').hidden = !rejectMode;
    $('#reject-reason').value = '';

    var actions = '<button type="button" class="btn btn-ghost" data-close>Cerrar</button>';
    if (app.estado === 'PENDIENTE') {
      if (rejectMode) {
        actions = '<button type="button" class="btn btn-ghost" data-close>Cancelar</button><button type="button" class="btn btn-danger" id="detail-reject-ok">Confirmar rechazo</button>';
      } else {
        actions = '<button type="button" class="btn btn-ghost btn-reject" id="detail-reject">Rechazar</button><button type="button" class="btn btn-primary" id="detail-approve">Aprobar cuenta</button>';
      }
    }
    $('#detail-actions').innerHTML = actions;
    openModal('#detail-modal');
    if (rejectMode) setTimeout(function () { $('#reject-reason').focus(); }, 60);
  }

  /* Un enlace deshabilitado no debe navegar. */
  $('#detail-proof').addEventListener('click', function (e) {
    if (this.getAttribute('aria-disabled') === 'true' || !this.getAttribute('href')) {
      e.preventDefault();
      toast('Esta solicitud no tiene un enlace de sustento válido.');
    }
  });

  $('#detail-actions').addEventListener('click', function (e) {
    var app = state.current;
    if (!app) return;
    if (e.target.id === 'detail-approve') { approve(app); closeAllModals(); }
    if (e.target.id === 'detail-reject') { openDetail(app, true); }
    if (e.target.id === 'detail-reject-ok') {
      var reason = $('#reject-reason').value.trim();
      if (!reason) { $('#reject-reason').focus(); toast('Escribe el motivo del rechazo.'); return; }
      reject(app, reason); closeAllModals();
    }
  });

  /* Delegated clicks on tables / lists */
  document.addEventListener('click', function (e) {
    var wa = e.target.closest('a[data-con-wa]');
    if (wa) {
      state.waClicked[wa.dataset.conWa] = true;
      toast('Se abrió WhatsApp. Cuando termines, marca la consulta como atendida.', 5000);
      return;
    }
    var t = e.target.closest('button');
    if (!t) return;

    if (t.dataset.conDetail) openConsulta(findById(consultas, t.dataset.conDetail));
    if (t.dataset.conMail) openMail(findById(consultas, t.dataset.conMail));
    if (t.dataset.conReopen) {
      var con = findById(consultas, t.dataset.conReopen);
      askConfirm('Reabrir consulta', '¿Devolver la consulta de ' + con.nombres + ' a pendientes?', 'Reabrir', false, function () { act('api/consultas/' + con.id + '/reopen', null, 'Consulta devuelta a pendientes.'); });
    }

    if (t.dataset.detail) openDetail(findById(applications, t.dataset.detail), false);
    if (t.dataset.reject) openDetail(findById(applications, t.dataset.reject), true);
    if (t.dataset.approve) {
      var app = findById(applications, t.dataset.approve);
      askConfirm('Aprobar solicitud', '¿Aprobar la cuenta de ' + app.nombre + '? Podrá iniciar sesión y aparecer en las recomendaciones.', 'Aprobar', false, function () { approve(app); });
    }

    var doc;
    if (t.dataset.suspend) {
      doc = findById(doctors, t.dataset.suspend);
      askConfirm('Suspender cuenta', '¿Suspender a ' + doc.nombre + '? No podrá iniciar sesión ni aparecerá en las recomendaciones.', 'Suspender', true, function () { act('api/doctors/' + doc.id + '/suspend', null, 'Cuenta suspendida.'); });
    }
    var pat;
    if (t.dataset.patSuspend) {
      pat = findById(patientList, t.dataset.patSuspend);
      askConfirm('Suspender paciente', '¿Suspender a ' + pat.nombre + '? No podrá iniciar sesión hasta que reactives su cuenta.', 'Suspender', true, function () { act('api/patients/' + pat.id + '/suspend', null, 'Paciente suspendido.'); });
    }
    if (t.dataset.patReactivate) {
      pat = findById(patientList, t.dataset.patReactivate);
      askConfirm('Reactivar paciente', '¿Reactivar la cuenta de ' + pat.nombre + '? Podrá volver a iniciar sesión.', 'Reactivar', false, function () { act('api/patients/' + pat.id + '/reactivate', null, 'Paciente reactivado.'); });
    }
    if (t.dataset.reactivate) {
      doc = findById(doctors, t.dataset.reactivate);
      askConfirm('Reactivar cuenta', '¿Reactivar la cuenta de ' + doc.nombre + '?', 'Reactivar', false, function () { act('api/doctors/' + doc.id + '/reactivate', null, 'Cuenta reactivada.'); });
    }
  });

  /* Close modals */
  document.addEventListener('click', function (e) {
    if (e.target.closest('[data-close]')) { var m = e.target.closest('.adm-modal'); if (m) closeModal(m); }
    else if (e.target.classList.contains('adm-modal')) closeModal(e.target);
  });
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape') { closeAllModals(); closeSidebar(); } });

  /* ---------------- Fecha del encabezado ---------------- */
  try {
    var dateEl = $('#adm-date');
    if (dateEl) {
      var txt = new Date().toLocaleDateString('es-PE', { weekday: 'long', day: 'numeric', month: 'long' });
      dateEl.textContent = txt.charAt(0).toUpperCase() + txt.slice(1);
    }
  } catch (e) { /* ignore */ }

  /* ---------------- Bienvenida ----------------
     El servidor solo la incluye en la primera carga tras verificar el código (flash attribute),
     así que no reaparece al recargar ni al volver desde otras páginas. Se cierra sola. */
  var welcome = $('#welcome');
  if (welcome) {
    var hideWelcome = function () {
      if (!welcome.parentNode) return;
      welcome.classList.add('leaving');
      setTimeout(function () { if (welcome.parentNode) welcome.parentNode.removeChild(welcome); }, 420);
    };
    $('#welcome-close').addEventListener('click', hideWelcome);
    setTimeout(hideWelcome, 6500);
  }

  /* ---------------- Init ---------------- */
  load();
  var start = window.location.hash.replace('#', '');
  showView(titles[start] ? start : 'dashboard');
});