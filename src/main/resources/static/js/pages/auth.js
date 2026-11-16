/**
 * Auth page — pestañas (login / registro, paciente / médico) y mensajes del servidor.
 */
document.addEventListener('DOMContentLoaded', function () {
  var loginForm = document.querySelector('#login-form');
  var registerPanel = document.querySelector('#register-panel');
  var patientForm = document.querySelector('#patient-form');
  var doctorForm = document.querySelector('#doctor-form');
  var recoverPanel = document.querySelector('#recover-panel');
  var recoverRequestForm = document.querySelector('#recover-request-form');
  var recoverConfirmForm = document.querySelector('#recover-confirm-form');

  var tabLogin = document.querySelector('#tab-login');
  var tabRegister = document.querySelector('#tab-register');
  var tabPatient = document.querySelector('#tab-patient');
  var tabDoctor = document.querySelector('#tab-doctor');

  var yearInput = document.querySelector('#doctor-graduation-year');
  if (yearInput) yearInput.max = new Date().getFullYear();

  // DNI y teléfono: no dejar escribir nada que no sea un dígito (puntos, letras, etc.).
  document.querySelectorAll('[data-solo-digitos]').forEach(function (input) {
    input.addEventListener('input', function () {
      var limpio = input.value.replace(/\D/g, '');
      if (limpio !== input.value) input.value = limpio;
    });
  });

  function showMode(mode) {
    var registering = mode === 'register';
    var recovering = mode === 'recover';
    loginForm.hidden = registering || recovering;
    registerPanel.hidden = !registering;
    recoverPanel.hidden = !recovering;
    tabLogin.classList.toggle('active', !registering && !recovering);
    tabRegister.classList.toggle('active', registering);
  }

  // "¿Olvidaste tu contraseña?" y los dos "Volver a iniciar sesión"
  document.querySelector('#toggle-recover').addEventListener('click', function () {
    showMode('recover');
    showRecoverStep('request');
  });
  document.querySelector('#back-to-login-1').addEventListener('click', function () { showMode('login'); });
  document.querySelector('#back-to-login-2').addEventListener('click', function () { showMode('login'); });

  function showRecoverStep(step) {
    recoverRequestForm.hidden = step !== 'request';
    recoverConfirmForm.hidden = step !== 'confirm';
  }

  function showRegistrationType(type) {
    var patient = type === 'patient';
    patientForm.hidden = !patient;
    doctorForm.hidden = patient;
    tabPatient.classList.toggle('active', patient);
    tabDoctor.classList.toggle('active', !patient);
  }

  tabLogin.addEventListener('click', function () { showMode('login'); });
  tabRegister.addEventListener('click', function () {
    showMode('register');
    showRegistrationType('patient');
  });
  tabPatient.addEventListener('click', function () { showRegistrationType('patient'); });
  tabDoctor.addEventListener('click', function () { showRegistrationType('doctor'); });

  // Mensajes del servidor al volver de /login
  var params = new URLSearchParams(window.location.search);
  var loginStatus = document.querySelector('#login-status');
  if (loginStatus) {
    if (params.has('locked')) {
      var minutos = document.body.dataset.bloqueoMinutos || '15';
      loginStatus.textContent = 'Bloqueamos tu cuenta ' + minutos + ' minutos por demasiados intentos fallidos. ' +
        'Espera o usa "¿Olvidaste tu contraseña?" para recuperarla antes.';
    } else if (params.has('disabled')) {
      loginStatus.textContent = 'Tu cuenta aún no está activa: puede estar pendiente de aprobación, rechazada o suspendida (si fue suspendida, te enviamos el motivo por correo). ' +
        'Si fue rechazada, puedes volver a enviar tu solicitud desde "Crear cuenta > Soy médico" con el mismo correo y contraseña.';
    } else if (params.has('error')) {
      loginStatus.textContent = 'Correo o contraseña incorrectos.';
    } else if (params.get('cambio') === 'password') {
      loginStatus.textContent = 'Tu contraseña se cambió correctamente. Inicia sesión con la nueva contraseña.';
      loginStatus.style.color = '#067647';
    } else if (params.get('cambio') === 'email') {
      loginStatus.textContent = 'Tu correo de acceso se cambió correctamente. Inicia sesión con el correo nuevo.';
      loginStatus.style.color = '#067647';
    }
  }

  // Reabrir el panel de recuperación tras un POST a /recuperar-password/** (ver data-reabrir en <body>).
  // El mensaje de éxito/error ya se muestra arriba (#flash-ok / #flash-error); esto solo deja visible
  // el formulario correcto para que el usuario lo vea junto a ese mensaje.
  var reabrir = document.body.dataset.reabrir;
  if (reabrir === 'recuperar' || reabrir === 'recuperar-codigo') {
    showMode('recover');
    showRecoverStep(reabrir === 'recuperar-codigo' ? 'confirm' : 'request');
  }

  // Registro de médico: avisa antes de enviar lo que el HTML no puede comprobar solo (el servidor valida igual).
  var doctorStatus = document.querySelector('#doctor-status');
  var MAX_SUSTENTO = 5 * 1024 * 1024;
  var TIPOS_SUSTENTO = ['application/pdf', 'image/jpeg', 'image/png', 'image/webp'];
  function doctorProblem() {
    var pass = doctorForm.querySelector('#doctor-password').value;
    if (pass !== doctorForm.querySelector('#doctor-password-confirm').value) return 'Las contraseñas no coinciden.';
    var egreso = parseInt(doctorForm.querySelector('#doctor-graduation-year').value, 10);
    var experiencia = parseInt(doctorForm.querySelector('#doctor-experience').value, 10);
    var maximo = new Date().getFullYear() - egreso;
    if (!isNaN(egreso) && !isNaN(experiencia) && experiencia > maximo) {
      return 'Los años de experiencia no pueden ser más que los años desde tu egreso (' + maximo + ').';
    }
    var archivo = doctorForm.querySelector('#doctor-proof').files[0];
    if (archivo) {
      var extension = /\.(pdf|jpe?g|png|webp)$/i.test(archivo.name);
      if (TIPOS_SUSTENTO.indexOf(archivo.type) === -1 && !extension) return 'Adjunta un PDF o una imagen JPG, PNG o WEBP.';
      if (archivo.size > MAX_SUSTENTO) return 'El archivo pesa ' + (archivo.size / 1048576).toFixed(1) + ' MB: el máximo es 5 MB.';
    }
    return '';
  }
  doctorForm.addEventListener('submit', function (event) {
    var problema = doctorProblem();
    if (doctorStatus) doctorStatus.textContent = problema;
    if (problema) event.preventDefault();
  });
  // Al volver con un error del servidor, el archivo no se conserva (el navegador no lo permite): se avisa.
  if (window.location.hash === '#register-doctor' && document.querySelector('#flash-error') && doctorStatus) {
    doctorStatus.textContent = 'Si habías adjuntado tu constancia, vuelve a adjuntarla antes de enviar.';
  }

  // Open the register tab directly with /login#register
  var hash = window.location.hash;
  if (hash === '#register' || hash === '#register-patient') tabRegister.click();
  if (hash === '#register-doctor') { tabRegister.click(); tabDoctor.click(); }
});
