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
      loginStatus.textContent = 'Tu cuenta aún no está activa: puede estar pendiente de aprobación, rechazada o suspendida. ' +
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

  // Open the register tab directly with /login#register
  var hash = window.location.hash;
  if (hash === '#register' || hash === '#register-patient') tabRegister.click();
  if (hash === '#register-doctor') { tabRegister.click(); tabDoctor.click(); }
});
