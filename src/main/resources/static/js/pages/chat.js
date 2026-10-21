/**
 * Chat paciente <-> médico conectado a la persistencia y mensajería en tiempo real.
 * Todo el texto de los usuarios se inserta con textContent (nunca innerHTML) para evitar inyección de HTML.
 */
(function () {
  'use strict';

  var root = document.getElementById('chat-app');
  if (!root) return;

  function $(id) { return document.getElementById(id); }
  var ui = {
    side: $('cx-side'), total: $('cx-total'),
    search: $('cx-search'), clear: $('cx-search-clear'), chips: root.querySelectorAll('.cx-chip'),
    sideBody: $('cx-side-body'), skeleton: $('cx-list-skeleton'), empty: $('cx-list-empty'), noRes: $('cx-list-noresults'),
    noResText: $('cx-noresults-text'), error: $('cx-list-error'), retryList: $('cx-list-retry'),
    wrap: $('cx-list-wrap'), list: $('cx-list'),
    thread: $('cx-thread'), placeholder: $('cx-placeholder'), panel: $('cx-panel'), back: $('cx-back'),
    headAv: $('cx-head-av'), headName: $('cx-head-name'), headStatus: $('cx-head-status'),
    scroll: $('cx-scroll'), msgSkeleton: $('cx-msg-skeleton'), threadEmpty: $('cx-thread-empty'),
    threadEmptyText: $('cx-thread-empty-text'), threadError: $('cx-thread-error'), retryThread: $('cx-thread-retry'),
    msgs: $('cx-msgs'), typing: $('cx-typing'), typingText: $('cx-typing-text'),
    jump: $('cx-jump'), jumpText: $('cx-jump-text'),
    form: $('cx-form'), input: $('cx-input'), count: $('cx-count'), send: $('cx-send'), live: $('cx-live')
  };

  var ROLE = root.dataset.role === 'MEDICO' ? 'MEDICO' : 'PACIENTE';
  var REDUCED = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var MOBILE = window.matchMedia('(max-width:860px)');
  var BASE_TITLE = document.title;
  var MAX_LEN = 1000, NEAR_BOTTOM = 90, GROUP_MIN = 5;

  var S = {
    svc: null, convs: [], messages: {}, activeId: null, openToken: 0,
    filter: 'all', query: '', typing: {}, typingTimers: {}, localTyping: null, localTypingTimer: null,
    pendingNew: 0, pendingStatus: {}, firstRender: true, pushed: false, connectionAnnounced: false
  };

  // ------------------------------------------------------------------ utilidades
  function el(tag, attrs, kids) {
    var n = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (k) {
      if (k === 'class') n.className = attrs[k];
      else if (k === 'text') n.textContent = attrs[k];
      else if (attrs[k] !== false && attrs[k] != null) n.setAttribute(k, attrs[k] === true ? '' : attrs[k]);
    });
    (kids || []).forEach(function (c) { if (c) n.appendChild(typeof c === 'string' ? document.createTextNode(c) : c); });
    return n;
  }
  function norm(s) { return String(s || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, ''); }
  function sameDay(a, b) { return a.toDateString() === b.toDateString(); }
  var timeFmt = new Intl.DateTimeFormat('es-PE', { hour: 'numeric', minute: '2-digit', hour12: true });
  var dayFmt = new Intl.DateTimeFormat('es-PE', { weekday: 'long', day: 'numeric', month: 'long' });
  var wdFmt = new Intl.DateTimeFormat('es-PE', { weekday: 'short' });
  var dateFmt = new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: '2-digit', year: '2-digit' });
  function clock(iso) { return timeFmt.format(new Date(iso)); }
  function dayAgo(d) { var t = new Date(); t.setHours(0, 0, 0, 0); var x = new Date(d); x.setHours(0, 0, 0, 0); return Math.round((t - x) / 864e5); }
  function listTime(iso) {
    var d = new Date(iso), n = dayAgo(d);
    return n === 0 ? clock(iso) : n === 1 ? 'Ayer' : n < 7 ? wdFmt.format(d).replace('.', '') : dateFmt.format(d);
  }
  function dayLabel(iso) {
    var d = new Date(iso), n = dayAgo(d);
    return n === 0 ? 'Hoy' : n === 1 ? 'Ayer' : dayFmt.format(d);
  }
  function announce(text) { if (ui.live) { ui.live.textContent = ''; setTimeout(function () { ui.live.textContent = text; }, 40); } }

  var ICON = {
    sending: '<svg viewBox="0 0 16 16" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><circle cx="8" cy="8" r="5.5"/><path d="M8 5v3.2l2 1.2"/></svg>',
    sent: '<svg viewBox="0 0 16 16" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="m3.5 8.5 3 3 6-6.5"/></svg>',
    delivered: '<svg viewBox="0 0 20 16" width="19" height="15" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="m1.5 8.5 3 3 6-6.5M8.5 11.5l1 .1 7-7.6"/></svg>',
    failed: '<svg viewBox="0 0 16 16" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="8" cy="8" r="5.8"/><path d="M8 5v3.4M8 11v.01"/></svg>'
  };
  ICON.read = ICON.delivered;
  var STATUS_TEXT = { sending: 'Enviando', sent: 'Enviado', delivered: 'Entregado', read: 'Leído', failed: 'No se pudo enviar' };
  var RANK = { sending: 0, sent: 1, delivered: 2, read: 3 };

  function tick(status) {
    var s = el('span', { class: 'cx-tick is-' + status, title: STATUS_TEXT[status] });
    s.innerHTML = ICON[status] || '';                       // iconos constantes definidos arriba (sin datos de usuario)
    s.appendChild(el('span', { class: 'sr-only', text: STATUS_TEXT[status] }));
    return s;
  }
  function avatar(c) {
    return el('span', { class: 'cx-av cx-av--t' + (c.tone % 5), 'aria-hidden': 'true' }, [c.initials]);
  }
  function highlight(text, q) {
    var frag = document.createDocumentFragment();
    var i = q ? norm(text).indexOf(q) : -1;
    if (i < 0) { frag.appendChild(document.createTextNode(text)); return frag; }
    frag.appendChild(document.createTextNode(text.slice(0, i)));
    frag.appendChild(el('mark', { text: text.slice(i, i + q.length) }));
    frag.appendChild(document.createTextNode(text.slice(i + q.length)));
    return frag;
  }
  function conv(id) { return S.convs.filter(function (c) { return c.id === id; })[0]; }
  function totalUnread() { return S.convs.reduce(function (n, c) { return n + (c.unread || 0); }, 0); }

  // ------------------------------------------------------------------ lista de conversaciones
  function setListState(state) { // loading | error | empty | noresults | list
    ui.sideBody.setAttribute('aria-busy', state === 'loading' ? 'true' : 'false');
    ui.skeleton.hidden = state !== 'loading';
    ui.error.hidden = state !== 'error';
    ui.empty.hidden = state !== 'empty';
    ui.noRes.hidden = state !== 'noresults';
    ui.wrap.hidden = state !== 'list';
  }

  function conversationItem(c, q, i) {
    var last = c.lastMessage;
    var pv = el('span', { class: 'cx-pv' + (last ? '' : ' is-none') });
    if (S.typing[c.id]) { pv.classList.add('is-typing'); pv.textContent = 'escribiendo…'; }
    else if (!last) pv.textContent = 'Sin mensajes todavía';
    else { if (last.mine) pv.appendChild(tick(last.status)); pv.appendChild(el('span', { class: 'cx-pv-text', text: last.text })); }

    var badge = c.unread > 0 ? el('span', { class: 'cx-badge', 'aria-label': c.unread + (c.unread === 1 ? ' mensaje sin leer' : ' mensajes sin leer'), text: c.unread > 99 ? '99+' : String(c.unread) }) : null;
    var name = el('span', { class: 'cx-name' }); name.appendChild(highlight(c.contact.name, q));
    var sub = el('span', { class: 'cx-sub' }); sub.appendChild(highlight(c.contact.subtitle, q));

    var btn = el('button', { type: 'button', class: 'cx-item' + (c.id === S.activeId ? ' is-active' : '') + (c.unread > 0 ? ' has-unread' : ''), 'data-conv': c.id, 'aria-current': c.id === S.activeId ? 'true' : false }, [
      avatar(c.contact),
      el('span', { class: 'cx-item-body' }, [
        el('span', { class: 'cx-row' }, [name, last ? el('time', { class: 'cx-when', datetime: last.createdAt, text: listTime(last.createdAt) }) : null]),
        sub,
        el('span', { class: 'cx-row' }, [pv, badge])
      ])
    ]);
    var li = el('li', { class: S.firstRender ? 'cx-in' : '' }, [btn]);
    if (S.firstRender) li.style.setProperty('--i', i);
    return li;
  }

  function renderList() {
    var q = norm(S.query.trim());
    var focused = document.activeElement && document.activeElement.closest && document.activeElement.closest('.cx-list');
    var focusKey = focused && document.activeElement.dataset.conv ? 'conv:' + document.activeElement.dataset.conv : null;

    var vis = S.convs.filter(function (c) {
      var searchable = c.contact.name + ' ' + c.contact.subtitle + ' ' + (c.lastMessage ? c.lastMessage.text : '');
      return (S.filter === 'all' || c.unread > 0) && (!q || norm(searchable).indexOf(q) >= 0);
    });
    updateTotals();
    if (!S.convs.length && !q) { setListState('empty'); return; }
    if (!vis.length) {
      ui.noResText.textContent = q ? 'No encontramos conversaciones con “' + S.query.trim() + '”.' : 'No tienes mensajes sin leer. ¡Estás al día!';
      setListState('noresults'); return;
    }
    setListState('list');
    ui.list.textContent = '';
    vis.forEach(function (c, i) { ui.list.appendChild(conversationItem(c, q, i)); });
    S.firstRender = false;

    if (focusKey) {
      var again = root.querySelector('.cx-list [data-conv="' + focusKey.slice(5) + '"]');
      if (again) again.focus({ preventScroll: true });
    }
  }

  function updateTotals() {
    var n = totalUnread();
    ui.total.hidden = n === 0;
    ui.total.textContent = n > 99 ? '99+' : String(n);
    document.querySelectorAll('[data-chat-unread]').forEach(function (b) { b.hidden = n === 0; b.textContent = n > 99 ? '99+' : String(n); });
    document.title = (n ? '(' + n + ') ' : '') + BASE_TITLE;
  }

  // ------------------------------------------------------------------ hilo de mensajes
  function isNearBottom() { return ui.scroll.scrollHeight - ui.scroll.scrollTop - ui.scroll.clientHeight < NEAR_BOTTOM; }
  function toBottom(smooth) { ui.scroll.scrollTo({ top: ui.scroll.scrollHeight, behavior: smooth && !REDUCED ? 'smooth' : 'auto' }); }

  function setThreadState(state) { // loading | error | empty | messages
    ui.msgSkeleton.hidden = state !== 'loading';
    ui.threadError.hidden = state !== 'error';
    ui.threadEmpty.hidden = state !== 'empty';
    ui.msgs.hidden = state !== 'messages';
    ui.msgs.setAttribute('aria-busy', state === 'loading' ? 'true' : 'false');
  }

  function dayDivider(iso) { return el('li', { class: 'cx-divider', role: 'separator' }, [el('span', { text: dayLabel(iso) })]); }

  function messageNode(m, prev, animate) {
    var cont = prev && prev.mine === m.mine && sameDay(new Date(prev.createdAt), new Date(m.createdAt)) &&
      (new Date(m.createdAt) - new Date(prev.createdAt)) < GROUP_MIN * 60000;
    var meta = el('span', { class: 'cx-meta' }, [el('time', { datetime: m.createdAt, text: clock(m.createdAt) })]);
    if (m.mine) meta.appendChild(tick(m.status === 'failed' ? 'failed' : m.status));
    var li = el('li', { class: 'cx-m ' + (m.mine ? 'cx-m--out' : 'cx-m--in') + (cont ? ' cx-m--cont' : '') + (animate ? ' is-new' : '') + (m.status === 'failed' ? ' is-failed' : ''), 'data-id': m.id }, [
      el('div', { class: 'cx-bubble' }, [el('p', { class: 'cx-text', text: m.text }), meta])
    ]);
    if (m.status === 'failed') li.appendChild(retryButton(m));
    return li;
  }
  function retryButton(m) {
    var b = el('button', { type: 'button', class: 'cx-retry', text: 'No enviado · Reintentar' });
    b.addEventListener('click', function () { retrySend(m); });
    return b;
  }

  function renderThread(id, unreadCount) {
    var list = S.messages[id] || [];
    ui.msgs.textContent = '';
    if (!list.length) {
      var c = conv(id);
      ui.threadEmptyText.textContent = ROLE === 'MEDICO'
        ? 'Aún no hay mensajes con ' + c.contact.name + '. Puedes escribirle primero.'
        : 'Escribe el primer mensaje a ' + c.contact.name + ' para iniciar la conversación.';
      setThreadState('empty'); return;
    }
    // el divisor "N mensajes nuevos" va antes del primer mensaje entrante sin leer
    var dividerAt = -1;
    if (unreadCount > 0) {
      var seen = 0;
      for (var k = list.length - 1; k >= 0 && seen < unreadCount; k--) { if (!list[k].mine) { seen++; dividerAt = k; } }
    }
    list.forEach(function (m, i) {
      var prev = list[i - 1];
      if (!prev || !sameDay(new Date(prev.createdAt), new Date(m.createdAt))) ui.msgs.appendChild(dayDivider(m.createdAt));
      if (i === dividerAt) ui.msgs.appendChild(el('li', { class: 'cx-divider cx-divider--new', id: 'cx-new-divider', role: 'separator' }, [el('span', { text: unreadCount + (unreadCount === 1 ? ' mensaje nuevo' : ' mensajes nuevos') })]));
      ui.msgs.appendChild(messageNode(m, prev, false));
    });
    setThreadState('messages');
    var d = $('cx-new-divider');
    if (d) ui.scroll.scrollTop = Math.max(0, d.offsetTop - 18); else toBottom(false);
  }

  function appendMessage(m, forceScroll) {
    var list = S.messages[S.activeId];
    var prev = list[list.length - 2];                       // m ya fue agregado a la lista
    if (ui.msgs.hidden) { ui.msgs.hidden = false; ui.threadEmpty.hidden = true; }
    var stick = forceScroll || isNearBottom();
    if (!prev || !sameDay(new Date(prev.createdAt), new Date(m.createdAt))) ui.msgs.appendChild(dayDivider(m.createdAt));
    ui.msgs.appendChild(messageNode(m, prev, true));
    if (stick) toBottom(true);
    return stick;
  }

  function setMessageStatus(m, status) {
    if (m.status !== 'failed' && RANK[status] != null && RANK[m.status] != null && RANK[status] < RANK[m.status]) return; // nunca retroceder
    m.status = status;
    var li = ui.msgs.querySelector('[data-id="' + cssEsc(m.id) + '"]');
    if (li) {
      li.classList.toggle('is-failed', status === 'failed');
      var old = li.querySelector('.cx-tick'); var meta = li.querySelector('.cx-meta');
      if (old && meta) meta.replaceChild(tick(status), old);
      var rb = li.querySelector('.cx-retry');
      if (status === 'failed' && !rb) li.appendChild(retryButton(m)); else if (status !== 'failed' && rb) rb.remove();
    }
    var c = conv(m.conversationId);
    if (c && c.lastMessage && c.lastMessage.mine && c.lastMessage.createdAt === m.createdAt) { c.lastMessage.status = status; renderList(); }
  }
  function cssEsc(v) { return window.CSS && CSS.escape ? CSS.escape(String(v)) : String(v).replace(/"/g, '\\"'); }

  function setHead(c) {
    ui.headAv.className = 'cx-av cx-av--t' + (c.contact.tone % 5);
    ui.headAv.textContent = c.contact.initials;
    ui.headName.textContent = c.contact.name;
    refreshStatusLine();
  }
  function refreshStatusLine() {
    var c = conv(S.activeId); if (!c) return;
    var typing = S.typing[c.id];
    ui.headStatus.textContent = typing ? 'escribiendo…' : c.contact.subtitle;
    ui.headStatus.classList.toggle('is-typing', !!typing);
    ui.typing.hidden = !typing;
    ui.typingText.textContent = c.contact.name + ' está escribiendo…';
  }

  // ------------------------------------------------------------------ abrir / cerrar conversación
  function showThreadOnMobile(open) {
    root.classList.toggle('is-thread-open', open);
    if (open && MOBILE.matches && !S.pushed) { history.pushState({ cx: S.activeId }, ''); S.pushed = true; }
  }

  function openConversation(id) {
    var c = conv(id); if (!c) return;
    if (S.activeId === id) { showThreadOnMobile(true); if (!MOBILE.matches) ui.input.focus(); return; }
    stopTyping();
    S.activeId = id;
    var token = ++S.openToken;
    S.pendingNew = 0; ui.jump.hidden = true;
    ui.placeholder.hidden = true; ui.panel.hidden = false;
    ui.panel.classList.remove('cx-swap'); void ui.panel.offsetWidth; ui.panel.classList.add('cx-swap');
    setHead(c);
    setComposer(c);
    ui.msgs.textContent = ''; setThreadState('loading');
    ui.input.value = ''; resizeInput();
    showThreadOnMobile(true);
    renderList();

    S.svc.getMessages(id).then(function (list) {
      if (token !== S.openToken) return;                    // el usuario ya abrió otra conversación
      S.messages[id] = list;
      var unread = c.unread;
      renderThread(id, unread);
      if (unread > 0) markRead(c);
      if (!MOBILE.matches) ui.input.focus({ preventScroll: true });
    }).catch(function () {
      if (token !== S.openToken) return;
      setThreadState('error');
    });
  }

  function closeThread() {
    stopTyping();
    S.activeId = null; S.openToken++;
    root.classList.remove('is-thread-open');
    S.pushed = false;
    renderList();
  }

  function markRead(c) {
    if (!c || !c.unread) return;
    c.unread = 0;
    S.svc.markAsRead(c.id).catch(function (error) {
      announce(error.message || 'No se pudo actualizar el estado de lectura. Inténtalo de nuevo.');
      syncConversations();
    });
    renderList();
  }

  function syncConversations() {
    return S.svc.listConversations().then(function (conversations) {
      S.convs = conversations;
      renderList();
      if (S.activeId == null) return;
      var active = conv(S.activeId);
      if (!active) { closeThread(); return; }
      setHead(active);
      setComposer(active);
    }).catch(function () { /* La lista conserva su estado y puede volver a cargarse con Reintentar. */ });
  }

  // ------------------------------------------------------------------ enviar
  function bumpConversation(c, m) {
    c.lastMessage = { id: m.id, clientId: m.clientId, text: m.text, createdAt: m.createdAt, mine: m.mine, status: m.status };
    S.convs = [c].concat(S.convs.filter(function (x) { return x !== c; }));
  }

  function submit() {
    var text = ui.input.value.trim();
    if (!text || !S.activeId) return;
    var c = conv(S.activeId);
    if (!c || c.canSend === false) return;
    stopTyping();
    var clientId = 'tmp-' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
    var m = { id: clientId, clientId: clientId, conversationId: c.id, mine: true, text: text, createdAt: new Date().toISOString(), status: 'sending' };
    S.messages[c.id].push(m);
    appendMessage(m, true);
    bumpConversation(c, m);
    ui.input.value = ''; resizeInput(); ui.input.focus();
    renderList();
    deliver(c, m);
  }

  function deliver(c, m) {
    S.svc.sendMessage(c.id, m.text, m.clientId).then(function (saved) {
      var li = ui.msgs.querySelector('[data-id="' + cssEsc(m.id) + '"]');
      if (li) li.dataset.id = saved.id;
      m.id = saved.id;
      m.createdAt = saved.createdAt || m.createdAt;
      m.text = saved.text || m.text;
      if (li) {
        var time = li.querySelector('time');
        if (time) { time.dateTime = m.createdAt; time.textContent = clock(m.createdAt); }
      }
      if (c.lastMessage && c.lastMessage.clientId === m.clientId) {
        c.lastMessage.id = saved.id;
        c.lastMessage.createdAt = m.createdAt;
        c.lastMessage.text = m.text;
      }
      var early = S.pendingStatus[saved.id];                // estado que llegó antes que la respuesta del envío
      setMessageStatus(m, early && RANK[early] > RANK[saved.status] ? early : saved.status);
      delete S.pendingStatus[saved.id];
    }).catch(function (error) {
      setMessageStatus(m, 'failed');
      announce(error.message || 'No se pudo enviar el mensaje. Comprueba la conexión e inténtalo de nuevo.');
      if (error.status === 403 || error.status === 404) syncConversations();
    });
  }

  function retrySend(m) {
    var c = conv(m.conversationId); if (!c) return;
    if (c.canSend === false) { announce('Esta conversación no admite nuevos mensajes porque ya no hay una cita pendiente.'); return; }
    setMessageStatus(m, 'sending');
    deliver(c, m);
  }

  function resizeInput() {
    ui.input.style.height = 'auto';
    ui.input.style.height = Math.min(ui.input.scrollHeight, 140) + 'px';
    var len = ui.input.value.length;
    var active = conv(S.activeId);
    ui.send.disabled = !ui.input.value.trim() || !active || active.canSend === false;
    ui.count.hidden = len < MAX_LEN * .8;
    ui.count.textContent = len + '/' + MAX_LEN;
    ui.count.classList.toggle('is-max', len >= MAX_LEN);
  }

  function setComposer(c) {
    var permitido = !!c && c.canSend !== false;
    ui.input.disabled = !permitido;
    ui.input.placeholder = permitido ? 'Escribe un mensaje…'
      : 'Puedes consultar el historial; para escribir necesitas una cita pendiente.';
    ui.input.title = permitido ? '' : 'El envío requiere una cita pendiente.';
    if (!permitido) stopTyping();
    resizeInput();
  }

  function stopTyping() {
    if (S.localTypingTimer) { clearTimeout(S.localTypingTimer); S.localTypingTimer = null; }
    if (S.localTyping != null && S.svc) S.svc.sendTyping(S.localTyping, false);
    S.localTyping = null;
  }

  function updateTyping() {
    var active = conv(S.activeId);
    if (!active || active.canSend === false || !ui.input.value.trim()) { stopTyping(); return; }
    if (S.localTyping !== active.id) {
      stopTyping();
      S.localTyping = active.id;
      S.svc.sendTyping(active.id, true);
    }
    if (S.localTypingTimer) clearTimeout(S.localTypingTimer);
    S.localTypingTimer = setTimeout(stopTyping, 1800);
  }

  function updateRemoteTyping(conversationId, isTyping) {
    if (S.typingTimers[conversationId]) clearTimeout(S.typingTimers[conversationId]);
    if (isTyping) {
      S.typing[conversationId] = true;
      S.typingTimers[conversationId] = setTimeout(function () {
        S.typing[conversationId] = false;
        delete S.typingTimers[conversationId];
        if (S.activeId === conversationId) refreshStatusLine();
      }, 5000);
    } else {
      S.typing[conversationId] = false;
      delete S.typingTimers[conversationId];
    }
    if (S.activeId === conversationId) {
      var stick = isNearBottom(); refreshStatusLine(); if (stick) toBottom(true);
    }
    renderList();
  }

  // ------------------------------------------------------------------ eventos en tiempo real
  var handlers = {
    onMessage: function (e) {
      var summary = e.conversation, m = e.message;
      var existing = conv(summary.id);
      if (existing) S.convs = [summary].concat(S.convs.filter(function (x) { return x.id !== summary.id; }));
      else S.convs.unshift(summary);

      if (S.activeId === summary.id) {
        S.messages[summary.id] = S.messages[summary.id] || [];
        var duplicate = S.messages[summary.id].some(function (item) {
          return item.id === m.id || (m.clientId && item.clientId === m.clientId);
        });
        if (!duplicate) {
          S.messages[summary.id].push(m);
          var stuck = appendMessage(m, false);
          if (stuck && document.visibilityState === 'visible') markRead(conv(summary.id));
          else { S.pendingNew++; showJump(); }
        }
      } else {
        announce('Nuevo mensaje de ' + summary.contact.name);
      }
      renderList();
      var badge = ui.list.querySelector('[data-conv="' + cssEsc(summary.id) + '"] .cx-badge');
      if (badge) { badge.classList.remove('cx-pop'); void badge.offsetWidth; badge.classList.add('cx-pop'); }
    },
    onStatus: function (e) {
      var list = S.messages[e.conversationId];
      var m = list && list.filter(function (x) { return x.id === e.messageId; })[0];
      if (m) setMessageStatus(m, e.status);
      else S.pendingStatus[e.messageId] = e.status;
      var c = conv(e.conversationId);
      if (c && c.lastMessage && c.lastMessage.id === e.messageId) {
        c.lastMessage.status = e.status;
        renderList();
      }
    },
    onTyping: function (e) {
      updateRemoteTyping(e.conversationId, e.typing);
    },
    onConnection: function (state) {
      if (!state.connected && state.reconnecting && S.activeId) ui.headStatus.textContent = 'Reconectando…';
      else if (state.connected && S.activeId) refreshStatusLine();
      if (!state.connected && state.reconnecting && !S.connectionAnnounced) {
        announce('Se perdió la conexión en tiempo real. Intentando reconectar…');
        S.connectionAnnounced = true;
      } else if (state.connected && state.reconnecting) {
        announce('Conexión del chat restablecida.');
        S.connectionAnnounced = false;
      }
    },
    onError: function (error) {
      var message = error && (error.message || error.detail);
      announce(message || 'Se interrumpió la conexión del chat. Intentando reconectar…');
    },
    onReconnect: function () {
      syncConversations().then(function () {
        if (S.activeId == null) return;
        var active = conv(S.activeId);
        if (!active) { closeThread(); return; }
        setHead(active);
        setComposer(active);
        return S.svc.getMessages(active.id).then(function (messages) {
          S.messages[active.id] = messages;
          renderThread(active.id, active.unread || 0);
          if (active.unread) markRead(active);
        });
      }).catch(function () { /* El próximo ciclo de reconexión volverá a sincronizar el historial. */ });
    }
  };

  function showJump() {
    ui.jumpText.textContent = S.pendingNew === 1 ? '1 mensaje nuevo' : S.pendingNew + ' mensajes nuevos';
    ui.jump.hidden = false;
  }

  // ------------------------------------------------------------------ eventos de la interfaz
  ui.search.addEventListener('input', function () {
    S.query = ui.search.value; ui.clear.hidden = !S.query; renderList();
  });
  ui.search.addEventListener('keydown', function (e) { if (e.key === 'Escape' && ui.search.value) { e.stopPropagation(); ui.clear.click(); } });
  ui.clear.addEventListener('click', function () { ui.search.value = ''; S.query = ''; ui.clear.hidden = true; renderList(); ui.search.focus(); });

  ui.chips.forEach(function (chip, i) {
    chip.addEventListener('click', function () {
      S.filter = chip.dataset.filter;
      ui.chips.forEach(function (x) { var on = x === chip; x.classList.toggle('is-active', on); x.setAttribute('aria-selected', String(on)); x.tabIndex = on ? 0 : -1; });
      renderList();
    });
    chip.addEventListener('keydown', function (e) {
      if (e.key !== 'ArrowRight' && e.key !== 'ArrowLeft') return;
      var next = ui.chips[(i + (e.key === 'ArrowRight' ? 1 : ui.chips.length - 1)) % ui.chips.length];
      next.focus(); next.click();
    });
  });

  root.addEventListener('click', function (e) {
    var item = e.target.closest('.cx-item');
    if (!item || !root.contains(item)) return;
    if (item.dataset.conv) openConversation(item.dataset.conv);
  });

  ui.back.addEventListener('click', function () { if (S.pushed) history.back(); else closeThread(); });
  window.addEventListener('popstate', function () { if (S.pushed || root.classList.contains('is-thread-open')) { S.pushed = false; closeThread(); } });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && MOBILE.matches && root.classList.contains('is-thread-open')) ui.back.click();
  });
  MOBILE.addEventListener && MOBILE.addEventListener('change', function () { if (!MOBILE.matches) root.classList.toggle('is-thread-open', !!S.activeId); });

  ui.form.addEventListener('submit', function (e) { e.preventDefault(); submit(); });
  ui.input.addEventListener('input', function () { resizeInput(); updateTyping(); });
  ui.input.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) { e.preventDefault(); submit(); }
  });

  ui.scroll.addEventListener('scroll', function () {
    if (!isNearBottom()) return;
    ui.jump.hidden = true;
    if (S.pendingNew) { S.pendingNew = 0; markRead(conv(S.activeId)); }
  }, { passive: true });
  ui.jump.addEventListener('click', function () { toBottom(true); });
  document.addEventListener('visibilitychange', function () {
    var c = conv(S.activeId);
    if (document.visibilityState === 'visible' && c && c.unread && isNearBottom()) { S.pendingNew = 0; ui.jump.hidden = true; markRead(c); }
  });

  ui.retryList.addEventListener('click', load);
  ui.retryThread.addEventListener('click', function () { var id = S.activeId; S.activeId = null; if (id) openConversation(id); });

  // ------------------------------------------------------------------ arranque
  var unsubscribe = null;
  function load() {
    setListState('loading');
    S.svc.listConversations().then(function (convs) {
      S.convs = convs;
      S.firstRender = true;
      renderList();
      if (!unsubscribe) unsubscribe = S.svc.subscribe(handlers);
    }).catch(function () { setListState('error'); });
  }

  try {
    S.svc = window.MediCercaChat.createService({
      apiBase: root.dataset.apiBase, websocketUrl: root.dataset.websocketUrl,
      csrf: root.dataset.csrfToken ? { header: root.dataset.csrfHeader, token: root.dataset.csrfToken } : null
    });
  } catch (err) {
    console.error('[chat]', err);
    setListState('error');
    return;
  }
  window.addEventListener('pagehide', function () {
    stopTyping();
    if (unsubscribe) { unsubscribe(); unsubscribe = null; }
  });
  window.addEventListener('pageshow', function (event) { if (event.persisted) load(); });
  load();
})();
