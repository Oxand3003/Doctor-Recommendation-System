/**
 * Cliente del chat privado: REST para historial/lecturas y STOMP para mensajes en vivo.
 * La sesión del navegador autentica cada solicitud y cada conexión WebSocket.
 */
(function (global) {
  'use strict';

  function createService(config) {
    var base = String(config.apiBase || '/api/mensajeria').replace(/\/$/, '');
    var handlers = null;
    var socket = null;
    var connected = false;
    var everConnected = false;
    var stopped = false;
    var reconnectTimer = null;
    var reconnectDelay = 1000;
    var initialConnectionErrorReported = false;
    var heartbeatTimer = null;
    var receiveBuffer = '';
    var subscriptions = {};
    var pendingAcks = {};
    var subSeq = 0;

    function report(name, value) {
      if (!handlers || typeof handlers[name] !== 'function') return;
      try { handlers[name](value); } catch (error) { console.error('[chat]', error); }
    }

    function errorFromResponse(response, payload) {
      var error = new Error(payload.detail || payload.message || payload.title
        || 'No se pudo completar la solicitud (' + response.status + ').');
      error.status = response.status;
      return error;
    }

    function request(path, method, body) {
      var headers = { Accept: 'application/json' };
      if (body != null) headers['Content-Type'] = 'application/json';
      if (method && method !== 'GET' && config.csrf && config.csrf.header && config.csrf.token) {
        headers[config.csrf.header] = config.csrf.token;
      }
      return fetch(base + path, {
        method: method || 'GET', credentials: 'same-origin', headers: headers,
        body: body == null ? undefined : JSON.stringify(body)
      }).then(function (response) {
        return response.text().then(function (text) {
          var payload = {};
          if (text) {
            try { payload = JSON.parse(text); } catch (_) { payload = { detail: text }; }
          }
          if (!response.ok) throw errorFromResponse(response, payload);
          return payload;
        });
      });
    }

    function contactView(contact) {
      var name = String(contact.name || '');
      var names = name.replace(/^(Dra?\.)\s+/i, '').trim().split(/\s+/);
      return Object.assign({}, contact, {
        id: String(contact.id),
        initials: ((names[0] || '?').charAt(0) + (names.length > 1 ? names[names.length - 1].charAt(0) : '')).toUpperCase(),
        tone: Number(contact.id || 0) % 5
      });
    }

    function conversationView(conversation) {
      var item = Object.assign({}, conversation, {
        id: String(conversation.id),
        contact: contactView(conversation.contact),
        unread: Number(conversation.unread || 0)
      });
      if (item.lastMessage) {
        item.lastMessage = Object.assign({}, item.lastMessage, { id: Number(item.lastMessage.id) });
        if (!item.lastMessage.status) item.lastMessage.status = 'sent';
      }
      return item;
    }

    function messageView(message) {
      return Object.assign({}, message, {
        id: Number(message.id),
        conversationId: String(message.conversationId),
        mine: !!message.mine,
        status: message.status || 'sent'
      });
    }

    function websocketUrl() {
      var url = new URL(config.websocketUrl || '/ws-mensajeria', window.location.href);
      url.protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      return url.toString();
    }

    function encodeHeader(value) {
      return String(value).replace(/\\/g, '\\\\').replace(/\r/g, '\\r').replace(/\n/g, '\\n').replace(/:/g, '\\c');
    }

    function sendFrame(command, headers, body) {
      if (!socket || socket.readyState !== 1) return false;
      var frame = command + '\n';
      Object.keys(headers || {}).forEach(function (key) { frame += key + ':' + encodeHeader(headers[key]) + '\n'; });
      try {
        socket.send(frame + '\n' + (body || '') + '\0');
        return true;
      } catch (_) {
        return false;
      }
    }

    function subscribe(destination, type) {
      var id = 'mc-' + (++subSeq);
      subscriptions[id] = type;
      sendFrame('SUBSCRIBE', { id: id, destination: destination, ack: 'auto' });
    }

    function decodeHeader(value) {
      return value.replace(/\\c/g, ':').replace(/\\n/g, '\n').replace(/\\r/g, '\r').replace(/\\\\/g, '\\');
    }

    function sendPendingToRest(clientId) {
      var pending = pendingAcks[clientId];
      if (!pending) return;
      clearTimeout(pending.timer);
      delete pendingAcks[clientId];
      request('/conversaciones/' + encodeURIComponent(pending.conversationId) + '/mensajes', 'POST', {
        text: pending.text, clientId: clientId
      }).then(function (message) {
        pending.resolve(messageView(message));
      }, pending.reject);
    }

    function retryPendingMessages() {
      Object.keys(pendingAcks).forEach(sendPendingToRest);
    }

    function notifyConnection(isConnected, reconnecting, first) {
      report('onConnection', { connected: isConnected, reconnecting: !!reconnecting, first: !!first });
    }

    function reportInitialConnectionError() {
      if (everConnected || initialConnectionErrorReported) return;
      initialConnectionErrorReported = true;
      report('onError', { message: 'No se pudo establecer la conexión en tiempo real. Se intentará reconectar.' });
    }

    function handleMessage(headers, body) {
      var payload;
      try { payload = JSON.parse(body); } catch (_) { return; }
      var type = subscriptions[headers.subscription];
      if (type === 'ack') {
        var pending = payload.clientId && pendingAcks[payload.clientId];
        if (pending) {
          clearTimeout(pending.timer);
          delete pendingAcks[payload.clientId];
          pending.resolve(messageView(payload));
        }
      } else if (type === 'inbox') {
        if (payload.conversation && payload.message) {
          report('onMessage', {
            conversation: conversationView(payload.conversation),
            message: messageView(payload.message)
          });
        } else if (payload.conversationId && typeof payload.typing === 'boolean') {
          report('onTyping', { conversationId: String(payload.conversationId), typing: payload.typing });
        } else if (payload.conversationId && payload.messageId) {
          report('onStatus', Object.assign({}, payload, { conversationId: String(payload.conversationId) }));
        }
      } else if (type === 'errors') {
        report('onError', payload);
        retryPendingMessages();
      }
    }

    function handleFrame(frame) {
      var split = frame.indexOf('\n\n');
      if (split < 0) return;
      var lines = frame.slice(0, split).split('\n');
      var command = lines.shift().replace(/\r$/, '');
      var headers = {};
      lines.forEach(function (line) {
        var at = line.indexOf(':');
        if (at > 0) headers[line.slice(0, at)] = decodeHeader(line.slice(at + 1).replace(/\r$/, ''));
      });
      var body = frame.slice(split + 2);
      if (command === 'CONNECTED') {
        var firstConnection = !everConnected;
        connected = true;
        everConnected = true;
        initialConnectionErrorReported = false;
        reconnectDelay = 1000;
        subscriptions = {};
        subscribe('/user/queue/mensajeria', 'inbox');
        subscribe('/user/queue/mensajeria/ack', 'ack');
        subscribe('/user/queue/mensajeria/errors', 'errors');
        if (heartbeatTimer) clearInterval(heartbeatTimer);
        heartbeatTimer = setInterval(function () {
          if (connected && socket && socket.readyState === 1) socket.send('\n');
        }, 10000);
        notifyConnection(true, !firstConnection, firstConnection);
        report('onReconnect', { first: firstConnection });
        return;
      }
      if (command === 'MESSAGE') handleMessage(headers, body);
      else if (command === 'ERROR') {
        report('onError', { message: headers.message || body || 'El servidor rechazó la conexión del chat.' });
        retryPendingMessages();
      }
    }

    function receive(data) {
      receiveBuffer += data;
      receiveBuffer = receiveBuffer.replace(/^[\r\n]+/, '');
      var end;
      while ((end = receiveBuffer.indexOf('\0')) >= 0) {
        var frame = receiveBuffer.slice(0, end);
        receiveBuffer = receiveBuffer.slice(end + 1);
        if (frame.trim()) handleFrame(frame);
      }
    }

    function scheduleReconnect() {
      if (stopped || reconnectTimer) return;
      reconnectTimer = setTimeout(function () {
        reconnectTimer = null;
        connect();
        reconnectDelay = Math.min(reconnectDelay * 2, 30000);
      }, reconnectDelay);
    }

    function closeSocket() {
      var wasConnected = connected;
      connected = false;
      if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null; }
      if (socket) {
        socket.onclose = socket.onerror = socket.onmessage = socket.onopen = null;
        try { socket.close(); } catch (_) { /* el socket ya terminó */ }
        socket = null;
      }
      retryPendingMessages();
      if (wasConnected || everConnected) notifyConnection(false, everConnected, false);
    }

    function connect() {
      if (stopped || socket) return;
      if (!everConnected) notifyConnection(false, false, true);
      if (!window.WebSocket) {
        if (!everConnected) notifyConnection(false, false, true);
        reportInitialConnectionError();
        scheduleReconnect();
        return;
      }
      try { socket = new WebSocket(websocketUrl()); } catch (_) {
        socket = null;
        if (!everConnected) notifyConnection(false, false, true);
        reportInitialConnectionError();
        scheduleReconnect();
        return;
      }
      socket.onopen = function () {
        var headers = { 'accept-version': '1.2', host: window.location.host, 'heart-beat': '10000,10000' };
        if (config.csrf && config.csrf.header && config.csrf.token) headers[config.csrf.header] = config.csrf.token;
        if (!sendFrame('CONNECT', headers)) scheduleReconnect();
      };
      socket.onmessage = function (event) { receive(event.data); };
      socket.onerror = function () { /* onclose inicia la reconexión */ };
      socket.onclose = function () {
        socket = null;
        closeSocket();
        reportInitialConnectionError();
        scheduleReconnect();
      };
    }

    function sendRest(conversationId, text, clientId) {
      return request('/conversaciones/' + encodeURIComponent(conversationId) + '/mensajes', 'POST', {
        text: text, clientId: clientId
      }).then(messageView);
    }

    return {
      listConversations: function () {
        return request('/conversaciones').then(function (list) { return list.map(conversationView); });
      },
      getMessages: function (conversationId) {
        return request('/conversaciones/' + encodeURIComponent(conversationId) + '/mensajes')
          .then(function (list) { return list.map(messageView); });
      },
      sendMessage: function (conversationId, text, clientId) {
        return new Promise(function (resolve, reject) {
          var pending = { conversationId: conversationId, text: text, resolve: resolve, reject: reject };
          pending.timer = setTimeout(function () { sendPendingToRest(clientId); }, 8000);
          pendingAcks[clientId] = pending;
          if (!connected || !sendFrame('SEND', {
            destination: '/app/mensajeria/conversaciones/' + encodeURIComponent(conversationId) + '/mensajes',
            'content-type': 'application/json'
          }, JSON.stringify({ text: text, clientId: clientId }))) sendPendingToRest(clientId);
        });
      },
      markAsRead: function (conversationId) {
        return request('/conversaciones/' + encodeURIComponent(conversationId) + '/leidos', 'POST', {});
      },
      sendTyping: function (conversationId, typing) {
        return connected && sendFrame('SEND', {
          destination: '/app/mensajeria/conversaciones/' + encodeURIComponent(conversationId) + '/escribiendo',
          'content-type': 'application/json'
        }, JSON.stringify({ typing: !!typing }));
      },
      subscribe: function (callbacks) {
        handlers = callbacks;
        stopped = false;
        connect();
        return function unsubscribe() {
          stopped = true;
          handlers = null;
          if (reconnectTimer) clearTimeout(reconnectTimer);
          reconnectTimer = null;
          closeSocket();
        };
      }
    };
  }

  global.MediCercaChat = { createService: createService };
})(window);
