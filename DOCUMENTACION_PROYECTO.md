# MediCerca — Documentación breve del proyecto

## Descripción general

MediCerca es una plataforma web para conectar pacientes con médicos. Permite buscar profesionales por especialidad y distrito, consultar sus perfiles, revisar disponibilidad y gestionar citas. También incluye gestión de cuentas, administración de médicos, mensajería y un formulario público de contacto.

La aplicación está construida con Spring Boot, Thymeleaf, Spring Data JPA, Spring Security y WebSocket/STOMP. La persistencia puede trabajar con H2 o MySQL según el perfil utilizado.

## Módulos principales

### 1. Inicio y búsqueda de médicos

- Vista principal: `templates/home/index.html`.
- Carga especialidades y distritos disponibles.
- Permite buscar médicos por especialidad, distrito y fecha.
- Presenta información institucional, funcionalidades, proceso de recomendación y formulario de contacto.
- La búsqueda se comunica con `/api/doctors/search`.

### 2. Autenticación y registro

- Vistas principales: `templates/auth/auth.html` y `templates/account/account.html`.
- Registro de pacientes y médicos.
- Inicio y cierre de sesión mediante Spring Security.
- Control de estado de cuenta: activa, pendiente, suspendida o eliminada.
- Las contraseñas se almacenan utilizando BCrypt.

### 3. Gestión del paciente

- El paciente puede consultar su cuenta y sus citas.
- Puede buscar médicos, revisar perfiles y seleccionar horarios disponibles.
- Puede gestionar sus citas y registrar valoraciones según el estado de la atención.
- Controladores relacionados: `AccountController` y `CitaPacienteController`.

### 4. Perfil y gestión del médico

- Vista pública del médico: `templates/doctor/profile.html`.
- El perfil puede incluir especialidad, formación, servicios, sedes, fotos, tarifas, horarios y reseñas.
- El médico autenticado administra su perfil, credenciales, horarios, sedes y tarifas.
- Vistas relacionadas: `templates/medico/`.
- Servicios principales: `DoctorProfileService`, `PerfilMedicoService`, `DisponibilidadService`, `SedeService` y `TarifaService`.

### 5. Agenda y citas del médico

- El médico puede consultar su dashboard y agenda.
- Se muestran citas pendientes, confirmadas, completadas y próximas.
- Las consultas de agenda se filtran por el médico autenticado para evitar acceso a información de otros profesionales.
- Componentes principales: `MedicoAgendaController`, `AgendaMedicoService` y `AgendaCitaRepository`.

### 6. Administración

- Vistas principales: `templates/admin/`.
- El administrador puede revisar solicitudes de médicos.
- Puede aprobar, rechazar, suspender, reactivar o eliminar lógicamente cuentas médicas.
- Incluye gestión de administradores, auditoría y verificación de acceso mediante segundo factor.
- Rutas protegidas bajo `/admin/**`.
- Controladores relacionados: `AdminController`, `AdminApiController` y `ConsultaAdminApiController`.

### 7. Mensajería y recomendaciones

- Mensajería paciente-médico mediante WebSocket/STOMP.
- Las conversaciones y mensajes se almacenan en la base de datos.
- El módulo de recomendaciones permite orientar al paciente según síntomas, especialidad, ubicación y disponibilidad.
- Componentes principales: `ChatController`, `ChatRecommendationController`, `MensajeriaWebSocketController`, `ChatRecommendationService` y `MensajeriaService`.

### 8. Contacto y consultas

- El visitante puede enviar una consulta desde el formulario público del inicio.
- Las consultas se almacenan y pueden ser administradas desde el panel administrativo.
- Se contemplan motivos como eliminación de cuenta, problemas de acceso, problemas con citas, denuncias y sugerencias.
- Componentes principales: `ConsultaController`, `ConsultaAdminApiController` y `ConsultaService`.

### 9. Notificaciones y credenciales

- Soporte para notificaciones por correo electrónico.
- Envío de códigos de seguridad para cambio de contraseña y correo.
- Notificación de aprobación o rechazo de solicitudes médicas.
- Componentes principales: `CorreoService`, `NotificacionMedicoService` y `CredencialesMedicoService`.

## Roles del sistema

| Rol | Funciones principales |
|---|---|
| Visitante | Consultar el inicio, buscar médicos y enviar consultas. |
| Paciente | Gestionar su cuenta, buscar médicos, reservar citas, cancelar y valorar atenciones. |
| Médico | Gestionar perfil, horarios, sedes, tarifas y citas recibidas. |
| Administrador | Verificar médicos, administrar cuentas, revisar consultas y consultar auditoría. |

## Interacciones principales

### Búsqueda y reserva

1. El paciente selecciona especialidad, distrito y fecha.
2. El frontend solicita resultados a `/api/doctors/search`.
3. El backend consulta médicos activos y disponibilidad.
4. El paciente abre el perfil del médico.
5. El sistema muestra horarios y modalidad de atención.
6. El paciente realiza la reserva y posteriormente puede consultar su cita desde su cuenta.

### Registro y aprobación de médicos

1. El médico completa el registro y envía sus credenciales.
2. La cuenta queda en estado pendiente.
3. El administrador revisa la solicitud.
4. La solicitud puede ser aprobada o rechazada.
5. Una solicitud aprobada activa la cuenta y permite que el médico opere en el sistema.

### Comunicación en tiempo real

1. Un paciente o médico autenticado entra al módulo de chat.
2. El cliente establece una conexión WebSocket/STOMP.
3. El backend valida el rol y la relación entre los usuarios.
4. Los mensajes se publican en tiempo real y se guardan para mantener el historial.

### Gestión de consultas de contacto

1. Un visitante completa el formulario público.
2. `ConsultaController` recibe la información mediante `POST /consultas`.
3. `ConsultaService` valida y registra la consulta.
4. El administrador puede revisar, atender, responder o reabrir la consulta.

## Seguridad

- Spring Security controla autenticación y autorización.
- Las rutas administrativas requieren `ROLE_ADMIN`.
- Las rutas de médico y paciente requieren el rol correspondiente.
- Se utiliza protección CSRF en formularios y operaciones de modificación.
- El acceso a agenda y datos privados se valida con el usuario autenticado.
- Se utiliza un filtro adicional para la verificación de acceso administrativo.

## Estructura relevante

```text
src/main/java/utp/edu/pe/recomendaciones
├── Controller/       Controladores web y API
├── config/           Seguridad, inicialización y WebSocket
├── dto/              Objetos de transferencia para las vistas y APIs
├── model/            Entidades y enums del dominio
├── repository/       Repositorios Spring Data JPA
└── service/          Lógica de negocio

src/main/resources
├── templates/        Vistas Thymeleaf
├── static/css/       Estilos globales, componentes y páginas
├── static/js/        Interacciones del frontend
└── application*.properties
```

## Descripción breve para el Pull Request

Este proyecto implementa una plataforma web de recomendación y gestión de citas médicas. Se incorporan funcionalidades para registro y autenticación de pacientes, médicos y administradores; búsqueda de profesionales; consulta de perfiles; gestión de horarios, sedes, tarifas y citas; administración de solicitudes médicas; mensajería en tiempo real; recomendaciones por síntomas; consultas de contacto y notificaciones por correo. El sistema aplica control de acceso por roles, validaciones de negocio y persistencia mediante Spring Data JPA.

