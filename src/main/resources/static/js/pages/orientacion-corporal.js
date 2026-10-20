/**
 * Maniquí corporal del asistente de IA (perfil del paciente).
 *
 * Cuando el asistente orienta según los síntomas, este módulo muestra el maniquí (mujer u hombre), marca la zona
 * del cuerpo, explica por qué se sugiere esa especialidad y permite exportar todo en un PDF. Si el paciente toca
 * otra zona del maniquí, se recalcula la orientación y se vuelven a filtrar los médicos.
 *
 * Se conecta con home.js mediante dos objetos globales:
 *   window.MCOrientacion  (lo define este archivo)  -> sexo(), setSexo(), mostrar(), reiniciar()
 *   window.MCChat         (lo define home.js)       -> elegirEspecialidad(), doctores()
 * Backend: POST /api/chat/orientacion (OrientacionCorporalController).
 */
(function () {
  'use strict';

  var root = document.getElementById('asistente');
  var panel = document.getElementById('body-panel');
  if (!root || !panel) return;

  var endpoint = root.dataset.orientacionEndpoint || '/api/chat/orientacion';
  var patientName = root.dataset.patientName || '';
  var svgHost = document.getElementById('body-svg');
  var caption = document.getElementById('body-caption');
  var report = document.getElementById('body-report');
  var pdfButton = document.getElementById('body-pdf');
  var sexButtons = panel.querySelectorAll('[data-sexo]');
  var SEX_KEY = 'mc_sexo';
  var JSPDF_URL = 'https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js';

  var sexo = leerSexo();
  var estado = { symptoms: '', specialty: '', touchedZone: '', resp: null };
  var requestId = 0;

  /* ------------------------------------------------------------------ utilidades */

  function escapeHtml(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function leerSexo() {
    try {
      var v = window.sessionStorage.getItem(SEX_KEY);
      return v === 'mujer' || v === 'hombre' ? v : '';
    } catch (e) { return ''; }
  }

  function guardarSexo(valor) {
    sexo = valor === 'mujer' || valor === 'hombre' ? valor : '';
    try {
      if (sexo) window.sessionStorage.setItem(SEX_KEY, sexo); else window.sessionStorage.removeItem(SEX_KEY);
    } catch (e) { /* sin almacenamiento: se recuerda solo mientras la página siga abierta */ }
    Array.prototype.forEach.call(sexButtons, function (b) {
      var on = b.dataset.sexo === sexo;
      b.classList.toggle('is-active', on);
      b.setAttribute('aria-pressed', String(on));
    });
  }

  /* ------------------------------------------------------------------ maniquí (SVG) */

  var COL = { fill: '#E4F0EB', stroke: '#B7D7CE', shadow: '#DCEAE5', accent: '#F4A259', mark: '#E4665A', dark: '#0A4E43' };

  // Posición [x, y] de cada zona en el maniquí (viewBox 240 x 470) y su nombre.
  var ZONAS = {
    cabeza: [120, 31, 'Cabeza'],
    ojos: [120, 48, 'Ojos'],
    boca: [120, 65, 'Boca y dientes'],
    salud_mental: [160, 30, 'Salud mental'],
    oidos: [120, 90, 'Oídos, nariz y garganta'],
    general: [94, 118, 'Cuerpo en general'],
    pecho: [120, 138, 'Pecho'],
    espalda: [88, 168, 'Espalda, cuello y cadera'],
    abdomen: [120, 191, 'Abdomen'],
    hormonas: [152, 200, 'Hormonas y metabolismo'],
    piel: [49, 178, 'Piel'],
    brazo: [190, 180, 'Hombro, brazo y mano'],
    pelvis: [120, 244, 'Zona pélvica'],
    rodilla: [98, 317, 'Rodilla y pierna'],
    tobillo: [143, 407, 'Pie y tobillo']
  };
  var AJUSTE_MUJER = { rodilla: [101, 317], tobillo: [139, 407], espalda: [96, 168] };

  var FORMAS_HOMBRE = [
    '<circle cx="120" cy="47" r="32"', '<path d="M108 78h24l7 24h-38z"',
    '<path d="M72 101q48-27 96 0l18 116q-3 28-27 30H81q-24-2-27-30z"',
    '<path d="M65 106q-18 4-22 24l-19 105q-3 18 12 23 15 4 19-13l25-106z"',
    '<path d="M175 106q18 4 22 24l19 105q3 18-12 23-15 4-19-13l-25-106z"',
    '<circle cx="37" cy="258" r="14"', '<circle cx="203" cy="258" r="14"',
    '<path d="M82 243h34v161H82z"', '<path d="M124 243h34v161h-34z"',
    '<ellipse cx="97" cy="421" rx="23" ry="13"', '<ellipse cx="143" cy="421" rx="23" ry="13"'
  ];
  var FORMAS_MUJER = [
    '<circle cx="120" cy="47" r="31"', '<path d="M108 77h24l5 25h-34z"',
    '<path d="M84 100q36-18 72 0l19 65-18 86q-37 18-74 0l-18-86z"',
    '<path d="M78 106q-18 4-22 24l-18 105q-3 18 12 23 15 4 19-13l24-106z"',
    '<path d="M162 106q18 4 22 24l18 105q3 18-12 23-15 4-19-13l-24-106z"',
    '<circle cx="37" cy="258" r="14"', '<circle cx="203" cy="258" r="14"',
    '<path d="M88 243h30v161H88z"', '<path d="M122 243h30v161h-30z"',
    '<ellipse cx="101" cy="421" rx="23" ry="13"', '<ellipse cx="139" cy="421" rx="23" ry="13"'
  ];
  var DETALLE_HOMBRE = 'M92 113q28 18 56 0M120 104v111M91 243h58';
  var DETALLE_MUJER = 'M91 116q29 17 58 0M120 105v110M91 243h58';

  function posicion(id, mujer) {
    var p = mujer && AJUSTE_MUJER[id] ? AJUSTE_MUJER[id] : ZONAS[id];
    return [p[0], p[1]];
  }

  /**
   * SVG del maniquí. interactivo = true: puntos que se pueden tocar (pantalla).
   * interactivo = false: imagen fija con solo la zona marcada (para el PDF); sin CSS externo.
   */
  function construirSvg(sexoActual, zonaSel, interactivo) {
    var mujer = sexoActual === 'mujer';
    var todo = zonaSel === 'general';
    var relleno = todo ? '#CBE9DF' : COL.fill;
    var borde = todo ? '#0E6B5C' : COL.stroke;
    var attr = ' fill="' + relleno + '" stroke="' + borde + '" stroke-width="' + (todo ? 2.4 : 1.8) + '"/>';
    var s = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 470" class="body-svg"' +
      (interactivo ? '' : ' width="240" height="470"') + ' role="img" aria-label="Maniquí ' +
      (mujer ? 'femenino' : 'masculino') + (interactivo ? ' con zonas seleccionables' : '') + '">';
    s += '<ellipse cx="120" cy="454" rx="62" ry="8" fill="' + COL.shadow + '"/>';
    (mujer ? FORMAS_MUJER : FORMAS_HOMBRE).forEach(function (forma) { s += forma + attr; });
    s += '<path d="' + (mujer ? DETALLE_MUJER : DETALLE_HOMBRE) + '" fill="none" stroke="' + COL.stroke +
      '" stroke-width="1.5" stroke-linecap="round"/>';

    Object.keys(ZONAS).forEach(function (id) {
      var p = posicion(id, mujer);
      var sel = id === zonaSel;
      var nombre = ZONAS[id][2];
      var t = ' transform="translate(' + p[0] + ' ' + p[1] + ')"';
      if (interactivo) {
        s += '<g class="body-hot' + (sel ? ' is-selected' : '') + '" data-zona="' + id + '" tabindex="0" role="button" ' +
          'aria-label="' + escapeHtml(nombre) + '" aria-pressed="' + sel + '"' + t + '><title>' + escapeHtml(nombre) + '</title>' +
          '<circle class="body-ring" r="15"/><circle class="body-pulse" r="11"/><circle class="body-dot" r="6"/></g>';
      } else if (sel && id !== 'general') {
        s += '<g' + t + '><circle r="16" fill="rgba(228,102,90,.25)" stroke="' + COL.mark + '" stroke-width="2.5"/>' +
          '<circle r="6" fill="' + COL.mark + '" stroke="#fff" stroke-width="2.5"/></g>';
      }
    });
    return s + '</svg>';
  }

  /* ------------------------------------------------------------------ panel */

  function pintarManiqui(zona) {
    if (!svgHost) return;
    svgHost.innerHTML = construirSvg(sexo || 'mujer', zona, true);
    if (caption) {
      caption.textContent = zona && ZONAS[zona]
        ? 'Zona marcada: ' + ZONAS[zona][2] + '. Vista frontal: toca otro punto si el malestar está en otra parte.'
        : 'Vista frontal. Toca el punto donde sientes el malestar.';
    }
  }

  function lista(items) {
    if (!items || !items.length) return '';
    return '<ul>' + items.map(function (i) { return '<li>' + escapeHtml(i) + '</li>'; }).join('') + '</ul>';
  }

  function pintarInforme(d) {
    var html = '';
    if (d.urgent) {
      html += '<p class="body-urgent" role="alert"><strong>Atención:</strong> lo que describes podría ser una urgencia. ' +
        'Si es intenso o va empeorando, acude a emergencias o llama al 106 (SAMU) sin esperar una cita.</p>';
    }
    html += '<div class="body-keys"><p><span class="body-tag">Zona</span><strong>' + escapeHtml(d.zoneLabel) + '</strong></p>' +
      '<p><span class="body-tag">Especialidad sugerida</span><strong>' + escapeHtml(d.specialty || 'Por definir') + '</strong></p></div>';
    html += '<h4>Resumen</h4><p>' + escapeHtml(d.summary) + '</p>';
    html += '<h4>¿Por qué esta zona y esta especialidad?</h4><p>' + escapeHtml(d.whyZone) + '</p><p>' + escapeHtml(d.whySpecialty) + '</p>';
    if (d.probableCauses && d.probableCauses.length) {
      html += '<h4>Posibles causas que el especialista descartará</h4>' + lista(d.probableCauses);
    }
    html += '<h4>Mientras llega tu cita</h4>' + lista(d.selfCare);
    html += '<h4>Señales de alarma: acude a emergencias si presentas</h4>' + lista(d.warningSigns);
    if (d.alternatives && d.alternatives.length) {
      html += '<h4>Otras especialidades que podrían encajar</h4><div class="body-alts">' + d.alternatives.map(function (a) {
        return '<button type="button" class="chat-chip" data-alt="' + escapeHtml(a.specialty) + '">' + escapeHtml(a.specialty) +
          ' <small>(' + escapeHtml(a.reason) + ')</small></button>';
      }).join('') + '</div>';
    }
    html += '<p class="body-note">' + (d.aiGenerated
      ? 'Explicación redactada por la IA de MediCerca.'
      : 'Explicación generada con las reglas de MediCerca (la IA no estaba disponible en este momento).') + '</p>';
    html += '<p class="body-disclaimer">' + escapeHtml(d.disclaimer) + '</p>';
    report.innerHTML = html;
  }

  function cargando() {
    if (report) report.innerHTML = '<p class="body-loading" role="status">Preparando tu orientación…</p>';
    if (pdfButton) pdfButton.disabled = true;
  }

  function consultar(zonaTocada) {
    var id = ++requestId;
    cargando();
    var body = {
      sex: sexo || 'mujer',
      symptoms: estado.symptoms || null,
      specialty: estado.specialty || null,
      zone: zonaTocada || null
    };
    return fetch(endpoint, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(body)
    }).then(function (response) {
      var type = response.headers.get('content-type') || '';
      if (response.status === 401 || response.redirected) throw new Error('Tu sesión no está activa. Vuelve a iniciar sesión.');
      if (!response.ok || type.indexOf('application/json') === -1) throw new Error('No se pudo preparar la orientación. Inténtalo nuevamente.');
      return response.json();
    }).then(function (data) {
      if (id !== requestId) return null;
      estado.resp = data;
      pintarManiqui(data.zone);
      pintarInforme(data);
      if (pdfButton) pdfButton.disabled = false;
      return data;
    }).catch(function (error) {
      if (id !== requestId) return null;
      report.innerHTML = '<p class="body-error" role="alert">' + escapeHtml(error.message || 'Ocurrió un error.') + '</p>';
      return null;
    });
  }

  function mostrar(opciones) {
    opciones = opciones || {};
    estado.symptoms = String(opciones.symptoms || '').slice(0, 500);
    estado.specialty = opciones.specialty || '';
    estado.touchedZone = '';
    estado.resp = null;
    panel.hidden = false;
    guardarSexo(sexo);
    pintarManiqui('');
    consultar(null).then(function () {
      if (panel.scrollIntoView) panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    });
  }

  function reiniciar() {
    requestId++;
    panel.hidden = true;
    estado = { symptoms: '', specialty: '', touchedZone: '', resp: null };
    if (svgHost) svgHost.innerHTML = '';
    if (report) report.innerHTML = '';
    guardarSexo('');
  }

  /* Tocar una zona: se recalcula la orientación y se filtran los médicos de esa especialidad. */
  function elegirZona(id) {
    if (!ZONAS[id]) return;
    estado.touchedZone = id;
    consultar(id).then(function (data) {
      if (data && data.specialty && window.MCChat && window.MCChat.elegirEspecialidad) {
        window.MCChat.elegirEspecialidad(data.specialty, 'Zona elegida: ' + data.zoneLabel);
      }
    });
  }

  if (svgHost) {
    svgHost.addEventListener('click', function (event) {
      var hot = event.target.closest ? event.target.closest('.body-hot') : null;
      if (hot) elegirZona(hot.getAttribute('data-zona'));
    });
    svgHost.addEventListener('keydown', function (event) {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      var hot = event.target.closest ? event.target.closest('.body-hot') : null;
      if (hot) {
        event.preventDefault();
        elegirZona(hot.getAttribute('data-zona'));
      }
    });
  }

  if (report) {
    report.addEventListener('click', function (event) {
      var chip = event.target.closest ? event.target.closest('[data-alt]') : null;
      if (chip && window.MCChat && window.MCChat.elegirEspecialidad) {
        window.MCChat.elegirEspecialidad(chip.getAttribute('data-alt'), 'Prefiero ' + chip.getAttribute('data-alt'));
      }
    });
  }

  Array.prototype.forEach.call(sexButtons, function (button) {
    button.addEventListener('click', function () {
      var nuevo = button.dataset.sexo;
      if (nuevo === sexo) return;
      guardarSexo(nuevo);
      if (!panel.hidden) {
        pintarManiqui(estado.resp ? estado.resp.zone : '');
        consultar(estado.touchedZone || null);
      }
    });
  });

  /* ------------------------------------------------------------------ PDF */

  function pdfTexto(valor) {
    return String(valor == null ? '' : valor)
      .replace(/[\u201C\u201D]/g, '"').replace(/[\u2018\u2019]/g, "'")
      .replace(/[\u2013\u2014]/g, '-').replace(/[\u2022\u00B7]/g, '-')
      .replace(/[^\u0000-\u00FF]/g, '');
  }

  function hoy() {
    var d = new Date();
    function p(n) { return n < 10 ? '0' + n : String(n); }
    return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate());
  }

  function reunirDatos() {
    var resp = estado.resp;
    var medicos = [];
    if (window.MCChat && window.MCChat.doctores) {
      medicos = window.MCChat.doctores().filter(function (m) { return !resp.specialty || m.specialty === resp.specialty; }).slice(0, 3);
    }
    return {
      paciente: patientName,
      resp: resp,
      sintomas: estado.symptoms,
      medicos: medicos,
      svg: construirSvg(sexo || 'mujer', resp.zone, false)
    };
  }

  function cargarJsPdf() {
    return new Promise(function (resolve, reject) {
      if (window.jspdf && window.jspdf.jsPDF) { resolve(); return; }
      var script = document.createElement('script');
      script.src = JSPDF_URL;
      script.async = true;
      script.onload = function () { window.jspdf && window.jspdf.jsPDF ? resolve() : reject(new Error('jsPDF no disponible')); };
      script.onerror = function () { reject(new Error('No se pudo cargar jsPDF')); };
      document.head.appendChild(script);
    });
  }

  function svgAPng(svg) {
    return new Promise(function (resolve, reject) {
      var img = new Image();
      img.onload = function () {
        var canvas = document.createElement('canvas');
        canvas.width = 480;
        canvas.height = 940;
        var ctx = canvas.getContext('2d');
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        resolve(canvas.toDataURL('image/png'));
      };
      img.onerror = function () { reject(new Error('No se pudo dibujar el maniquí')); };
      img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
    });
  }

  function crearPdf(d, png) {
    var doc = new window.jspdf.jsPDF({ unit: 'mm', format: 'a4' });
    var W = 210, H = 297, M = 15, LIM = 278, y;
    var resp = d.resp;

    function color(r, g, b) { doc.setTextColor(r, g, b); }
    function nuevaPagina() { doc.addPage(); y = 20; }
    function asegurar(alto) { if (y + alto > LIM) nuevaPagina(); }

    /* Texto con saltos de línea y de página; devuelve la y final. */
    function parrafo(texto, x, ancho, tam, interlineado) {
      doc.setFontSize(tam);
      var lineas = doc.splitTextToSize(pdfTexto(texto), ancho);
      lineas.forEach(function (linea) {
        asegurar(interlineado);
        doc.text(linea, x, y);
        y += interlineado;
      });
    }

    function titulo(texto) {
      asegurar(14);
      y += 3;
      doc.setFont('helvetica', 'bold');
      color(14, 107, 92);
      doc.setFontSize(12);
      doc.text(pdfTexto(texto), M, y);
      y += 6;
      doc.setFont('helvetica', 'normal');
      color(19, 43, 39);
    }

    function vinetas(items) {
      (items || []).forEach(function (item) {
        var lineas = doc.splitTextToSize(pdfTexto(item), W - 2 * M - 6);
        lineas.forEach(function (linea, i) {
          asegurar(5);
          if (i === 0) doc.text('-', M + 1, y);
          doc.text(linea, M + 6, y);
          y += 5;
        });
      });
    }

    // Encabezado
    doc.setFillColor(14, 107, 92);
    doc.rect(0, 0, W, 24, 'F');
    color(255, 255, 255);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(19);
    doc.text('MediCerca', M, 15);
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(10.5);
    doc.text('Orientación por zona corporal', W - M, 15, { align: 'right' });

    y = 33;
    color(19, 43, 39);
    doc.setFontSize(10.5);
    if (d.paciente) { doc.setFont('helvetica', 'bold'); doc.text('Paciente: ', M, y); doc.setFont('helvetica', 'normal'); doc.text(pdfTexto(d.paciente), M + 20, y); y += 6; }
    doc.setFont('helvetica', 'bold'); doc.text('Sexo: ', M, y); doc.setFont('helvetica', 'normal'); doc.text(pdfTexto(resp.sexLabel), M + 20, y);
    doc.setFont('helvetica', 'bold'); doc.text('Fecha: ', M + 70, y); doc.setFont('helvetica', 'normal'); doc.text(pdfTexto(resp.generatedAt), M + 85, y);
    y += 9;

    if (resp.urgent) {
      doc.setFillColor(252, 234, 231);
      doc.roundedRect(M, y - 4, W - 2 * M, 15, 2, 2, 'F');
      doc.setFont('helvetica', 'bold');
      color(179, 74, 64);
      doc.setFontSize(10);
      var aviso = doc.splitTextToSize('ATENCIÓN: lo que describes podría ser una urgencia. Si es intenso o va empeorando, acude a emergencias o llama al 106 (SAMU) sin esperar una cita.', W - 2 * M - 6);
      doc.text(aviso, M + 3, y + 1);
      doc.setFont('helvetica', 'normal');
      color(19, 43, 39);
      y += 18;
    }

    // Maniquí (izquierda) y datos clave (derecha)
    var imgW = 52, imgH = imgW * 470 / 240, top = y;
    doc.setDrawColor(221, 231, 227);
    doc.roundedRect(M, top - 2, imgW + 4, imgH + 4, 3, 3);
    doc.addImage(png, 'PNG', M + 2, top, imgW, imgH);
    var x2 = M + imgW + 12, w2 = W - M - x2;
    y = top + 4;
    doc.setFont('helvetica', 'bold'); color(92, 114, 108); doc.setFontSize(9);
    doc.text('ZONA MARCADA', x2, y); y += 5.5;
    doc.setFontSize(13); color(19, 43, 39); doc.text(pdfTexto(resp.zoneLabel), x2, y); y += 9;
    doc.setFontSize(9); color(92, 114, 108); doc.text('ESPECIALIDAD SUGERIDA', x2, y); y += 5.5;
    doc.setFontSize(13); color(14, 107, 92);
    doc.text(pdfTexto(resp.specialty || 'Por definir'), x2, y); y += 9;
    doc.setFontSize(9); color(92, 114, 108); doc.text('SÍNTOMAS QUE DESCRIBISTE', x2, y); y += 5;
    doc.setFont('helvetica', 'normal'); color(19, 43, 39);
    parrafo(d.sintomas || 'No los describiste: marcaste la zona directamente en el maniquí.', x2, w2, 10, 5);
    y = Math.max(y, top + imgH) + 6;

    doc.setFont('helvetica', 'normal');
    color(19, 43, 39);
    titulo('Resumen');
    parrafo(resp.summary, M, W - 2 * M, 10.5, 5.4);
    titulo('¿Por qué esta zona y esta especialidad?');
    parrafo(resp.whyZone, M, W - 2 * M, 10.5, 5.4);
    y += 1.5;
    parrafo(resp.whySpecialty, M, W - 2 * M, 10.5, 5.4);
    if (resp.probableCauses && resp.probableCauses.length) {
      titulo('Posibles causas que el especialista descartará');
      doc.setFontSize(10.5);
      vinetas(resp.probableCauses);
    }
    titulo('Mientras llega tu cita');
    doc.setFontSize(10.5);
    vinetas(resp.selfCare);
    titulo('Señales de alarma: acude a emergencias si presentas');
    doc.setFontSize(10.5);
    vinetas(resp.warningSigns);

    if (d.medicos.length) {
      titulo('Médicos sugeridos en MediCerca');
      d.medicos.forEach(function (m) {
        var calif = m.reviews ? 'Calif. ' + Number(m.rating).toFixed(1) + ' (' + m.reviews + ' opiniones)' : 'Nuevo en MediCerca';
        var sede = [m.clinic, m.district].filter(Boolean).join(', ');
        asegurar(16);
        doc.setFont('helvetica', 'bold'); doc.setFontSize(10.5); doc.text(pdfTexto(m.name), M, y); y += 5;
        doc.setFont('helvetica', 'normal'); doc.setFontSize(9.5); color(92, 114, 108);
        doc.text(pdfTexto(m.specialty + '  |  ' + calif + '  |  ' + (m.experienceYears || 0) + ' años de experiencia'), M, y); y += 4.6;
        doc.text(pdfTexto((sede || 'Sede por confirmar') + '  |  ' + (m.price != null ? 'Consulta desde ' + m.priceText : m.priceText)), M, y); y += 7;
        color(19, 43, 39);
      });
    }

    y += 3;
    asegurar(16);
    doc.setFont('helvetica', 'italic');
    color(92, 114, 108);
    parrafo(resp.disclaimer + (resp.aiGenerated ? '' : ' (Explicación generada con las reglas de MediCerca.)'), M, W - 2 * M, 8.5, 4.2);

    // Pie en todas las páginas
    var total = doc.getNumberOfPages();
    for (var i = 1; i <= total; i++) {
      doc.setPage(i);
      doc.setDrawColor(221, 231, 227);
      doc.line(M, H - 14, W - M, H - 14);
      doc.setFont('helvetica', 'normal');
      doc.setFontSize(8);
      color(92, 114, 108);
      doc.text('MediCerca - Orientación generada automáticamente; no es un diagnóstico médico.', M, H - 9);
      doc.text('Página ' + i + ' de ' + total, W - M, H - 9, { align: 'right' });
    }
    doc.save('orientacion-medicerca-' + hoy() + '.pdf');
  }

  /* Plan B si no se puede cargar jsPDF (sin internet o bloqueado): informe imprimible -> "Guardar como PDF". */
  function imprimir(d) {
    var resp = d.resp;
    function ul(items) { return items && items.length ? '<ul>' + items.map(function (i) { return '<li>' + escapeHtml(i) + '</li>'; }).join('') + '</ul>' : ''; }
    var medicos = d.medicos.map(function (m) {
      return '<p><strong>' + escapeHtml(m.name) + '</strong><br>' + escapeHtml(m.specialty) + ' · ' +
        (m.reviews ? 'Calif. ' + Number(m.rating).toFixed(1) : 'Nuevo') + ' · ' + escapeHtml([m.clinic, m.district].filter(Boolean).join(', ')) +
        ' · ' + escapeHtml(m.priceText) + '</p>';
    }).join('');
    var html = '<!DOCTYPE html><html lang="es"><head><meta charset="utf-8"><title>Orientación MediCerca</title><style>' +
      'body{font-family:Arial,Helvetica,sans-serif;color:#132B27;margin:24px;line-height:1.5;font-size:13px}' +
      'h1{color:#0E6B5C;margin:0 0 4px}h2{color:#0E6B5C;font-size:15px;margin:18px 0 4px}' +
      '.row{display:flex;gap:22px;align-items:flex-start}.row svg{width:190px;height:auto;flex:none;border:1px solid #DDE7E3;border-radius:12px;padding:6px}' +
      '.urg{background:#FCEAE7;color:#B34A40;padding:10px 12px;border-radius:8px;font-weight:bold}small{color:#5C726C}' +
      '</style></head><body><h1>MediCerca</h1><small>Orientación por zona corporal · ' + escapeHtml(resp.generatedAt) + '</small>' +
      (d.paciente ? '<p><strong>Paciente:</strong> ' + escapeHtml(d.paciente) + ' · <strong>Sexo:</strong> ' + escapeHtml(resp.sexLabel) + '</p>' : '') +
      (resp.urgent ? '<p class="urg">Atención: lo que describes podría ser una urgencia. Acude a emergencias o llama al 106 (SAMU).</p>' : '') +
      '<div class="row">' + d.svg + '<div><p><strong>Zona marcada:</strong> ' + escapeHtml(resp.zoneLabel) + '</p><p><strong>Especialidad sugerida:</strong> ' +
      escapeHtml(resp.specialty || 'Por definir') + '</p><p><strong>Síntomas que describiste:</strong><br>' + escapeHtml(d.sintomas || 'No los describió.') + '</p></div></div>' +
      '<h2>Resumen</h2><p>' + escapeHtml(resp.summary) + '</p><h2>¿Por qué esta zona y esta especialidad?</h2><p>' + escapeHtml(resp.whyZone) + '</p><p>' + escapeHtml(resp.whySpecialty) + '</p>' +
      (resp.probableCauses && resp.probableCauses.length ? '<h2>Posibles causas que el especialista descartará</h2>' + ul(resp.probableCauses) : '') +
      '<h2>Mientras llega tu cita</h2>' + ul(resp.selfCare) + '<h2>Señales de alarma</h2>' + ul(resp.warningSigns) +
      (medicos ? '<h2>Médicos sugeridos en MediCerca</h2>' + medicos : '') +
      '<hr><small>' + escapeHtml(resp.disclaimer) + '</small></body></html>';
    var frame = document.createElement('iframe');
    frame.setAttribute('aria-hidden', 'true');
    frame.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0';
    document.body.appendChild(frame);
    frame.contentDocument.open();
    frame.contentDocument.write(html);
    frame.contentDocument.close();
    window.setTimeout(function () {
      frame.contentWindow.focus();
      frame.contentWindow.print();
      window.setTimeout(function () { if (frame.parentNode) frame.parentNode.removeChild(frame); }, 4000);
    }, 300);
  }

  function exportar() {
    if (!estado.resp || !pdfButton) return;
    var label = pdfButton.textContent;
    pdfButton.disabled = true;
    pdfButton.textContent = 'Generando PDF…';
    var datos = reunirDatos();
    cargarJsPdf()
      .then(function () { return svgAPng(datos.svg); })
      .then(function (png) { crearPdf(datos, png); })
      .catch(function () { imprimir(datos); })
      .then(function () {
        pdfButton.disabled = false;
        pdfButton.textContent = label;
      });
  }

  if (pdfButton) pdfButton.addEventListener('click', exportar);

  /* ------------------------------------------------------------------ API para home.js */

  window.MCOrientacion = {
    sexo: function () { return sexo; },
    setSexo: guardarSexo,
    mostrar: mostrar,
    reiniciar: reiniciar
  };

  guardarSexo(sexo);
})();
