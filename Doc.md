# MediCerca — Documentación del modelo de datos

Este documento describe **todas las entidades (models)**, sus **atributos**, los **enums** y **cómo se relacionan**. Está generado a partir del código real del proyecto.

- **Paquete de entidades:** `utp.edu.pe.recomendaciones.model`
- **Paquete de enums:** `utp.edu.pe.recomendaciones.model.enums`
- **Paquete de repositorios:** `utp.edu.pe.recomendaciones.repository`
- **Paquete de configuración:** `utp.edu.pe.recomendaciones.config`
- **Paquetes de lógica y web:** `...service`, `...dto`, `...exception`, `...Controller`

## 1. Visión general

MediCerca es una plataforma donde **pacientes** buscan **médicos** por especialidad y distrito, comparan valoración y experiencia, y reservan **citas**. Los médicos se registran y deben ser **verificados por un administrador** antes de aparecer.

Hay 13 entidades agrupadas en cuatro áreas (las secciones 6 a 17 describen repositorios, servicios, controladores, vistas y el estado actual del proyecto):

| Área | Entidades |
|---|---|
| Seguridad y usuarios | `Rol`, `Usuario`, `Auditoria` |
| Catálogos y ubicación | `Especialidad`, `Distrito`, `Establecimiento` |
| Perfil del médico | `Medico`, `HorarioAtencion`, `ServicioMedico`, `FormacionMedico`, `SolicitudVerificacion` |
| Atención | `Cita`, `Valoracion` |

## 2. Diagrama de relaciones

```
ROL 1───N USUARIO 1───0..1 MEDICO N───1 ESPECIALIDAD
                 │                │ N───0..1 ESTABLECIMIENTO N───1 DISTRITO
                 │                ├─1───N HORARIO_ATENCION
                 │                ├─1───N SERVICIO_MEDICO
                 │                ├─1───N FORMACION_MEDICO
                 │                ├─1───N SOLICITUD_VERIFICACION N───0..1 USUARIO (admin revisor)
                 │                ├─1───N CITA
                 │                └─1───N VALORACION
                 ├─1───N CITA        (usuario como paciente)
                 ├─1───N VALORACION  (usuario como paciente)
                 └─1───N AUDITORIA   (usuario como admin)
CITA 1───0..1 VALORACION
```

### Resumen de relaciones

| Origen | Tipo | Destino | Cómo se implementa |
|---|---|---|---|
| Usuario | N : 1 | Rol | `usuarios.rol_id` → `roles.id` |
| Medico | 1 : 1 | Usuario | `medicos.usuario_id` (único) → `usuarios.id` |
| Medico | N : 1 | Especialidad | `medicos.especialidad_id` |
| Medico | N : 1 (opcional) | Establecimiento | `medicos.establecimiento_id` |
| Establecimiento | N : 1 | Distrito | `establecimientos.distrito_id` |
| HorarioAtencion | N : 1 | Medico | `horarios_atencion.medico_id` |
| ServicioMedico | N : 1 | Medico | `servicios_medico.medico_id` |
| FormacionMedico | N : 1 | Medico | `formaciones_medico.medico_id` |
| SolicitudVerificacion | N : 1 | Medico | `solicitudes_verificacion.medico_id` |
| SolicitudVerificacion | N : 1 (opcional) | Usuario (admin) | `solicitudes_verificacion.revisado_por` |
| Cita | N : 1 | Usuario (paciente) | `citas.paciente_id` |
| Cita | N : 1 | Medico | `citas.medico_id` |
| Valoracion | 1 : 1 | Cita | `valoraciones.cita_id` (único) |
| Valoracion | N : 1 | Usuario (paciente) | `valoraciones.paciente_id` |
| Valoracion | N : 1 | Medico | `valoraciones.medico_id` |
| Auditoria | N : 1 | Usuario (admin) | `auditoria.usuario_id` |

Todas las relaciones `@ManyToOne` y `@OneToOne` usan `FetchType.LAZY` (los datos relacionados solo se cargan cuando se usan). Las entidades guardan solo el lado "muchos" (la clave foránea); no hay colecciones `@OneToMany` ni cascadas de borrado, lo que evita borrados en cadena accidentales.

## 3. Roles y estados

### Roles (tabla `roles`)

| Rol | Quién es | Qué puede hacer |
|---|---|---|
| `ADMIN` | Administrador de la plataforma | Entrar al panel `/admin/**`, aprobar/rechazar solicitudes, suspender/reactivar/eliminar médicos, consultar la auditoría. |
| `MEDICO` | Profesional de salud verificado | Gestionar su perfil, servicios, horarios y citas recibidas. Solo opera con cuenta ACTIVA. |
| `PACIENTE` | Usuario que busca atención | Buscar médicos, reservar y cancelar citas, ver "Mi cuenta", dejar valoraciones de citas completadas. |

En Spring Security se manejan como `ROLE_ADMIN`, `ROLE_MEDICO` y `ROLE_PACIENTE`. Los roles se crean solos al arrancar (`DataInitializer`).

El **rol** dice *qué puede hacer* una persona; el **estado** (`EstadoUsuario`) dice *si su cuenta está habilitada*. Son independientes.

## 4. Enums

Todos se guardan como **texto** en la base de datos (`@Enumerated(EnumType.STRING)`), nunca como número, para que agregar o reordenar valores no corrompa los datos.

### `AccionAuditoria` — usado en `Auditoria.accion`

| Valor | Significado |
|---|---|
| `APROBAR_MEDICO` | Admin aprobó una solicitud. |
| `RECHAZAR_MEDICO` | Admin rechazó una solicitud. |
| `SUSPENDER_MEDICO` | Admin suspendió una cuenta. |
| `REACTIVAR_MEDICO` | Admin reactivó una cuenta. |
| `ELIMINAR_MEDICO` | Admin eliminó (lógicamente) una cuenta. |

### `EstadoCita` — usado en `Cita.estado`

| Valor | Significado |
|---|---|
| `PENDIENTE` | Reservada, a la espera de confirmación. |
| `CONFIRMADA` | Confirmada por el médico/sistema. |
| `CANCELADA` | Cancelada por el paciente o el médico. |
| `COMPLETADA` | La atención ya se realizó (habilita dejar una valoración). |
| `NO_ASISTIO` | El paciente no se presentó. |

### `EstadoSolicitud` — usado en `SolicitudVerificacion.estado`

| Valor | Significado |
|---|---|
| `PENDIENTE` | Esperando revisión del admin. |
| `APROBADA` | El admin verificó las credenciales; el médico pasa a ACTIVO. |
| `RECHAZADA` | El admin la rechazó indicando un motivo. |

### `EstadoUsuario` — usado en `Usuario.estado`

| Valor | Significado |
|---|---|
| `ACTIVO` | Puede iniciar sesión y usar el sistema. |
| `PENDIENTE` | Médico registrado que espera aprobación; no puede entrar ni aparece en búsquedas. |
| `SUSPENDIDO` | Bloqueado temporalmente por un admin. |
| `ELIMINADO` | Borrado lógico: el registro permanece para conservar el historial. |

### `Modalidad` — usado en `Cita.modalidad`

| Valor | Significado |
|---|---|
| `PRESENCIAL` | Atención en el consultorio. |
| `TELECONSULTA` | Atención virtual. |

### `ModalidadAtencion` — usado en `HorarioAtencion.modalidad`

| Valor | Significado |
|---|---|
| `PRESENCIAL` | La franja horaria solo admite citas presenciales. |
| `TELECONSULTA` | La franja solo admite teleconsultas. |
| `AMBAS` | Admite ambas modalidades (valor por defecto). |

### `TipoFormacion` — usado en `FormacionMedico.tipo`

| Valor | Significado |
|---|---|
| `TITULO` | Título profesional (ej. Médico cirujano). |
| `ESPECIALIDAD` | Título de especialista. |
| `MEMBRESIA` | Pertenencia a una sociedad (ej. SOPECAR). |
| `PASANTIA` | Pasantía o capacitación en otra institución. |

## 5. Entidades

Convenciones: todas tienen `id` (`Long`, autoincremental). Los nombres Java usan camelCase y en la base de datos se convierten a snake_case (`passwordHash` → `password_hash`). Las fechas de creación/actualización se llenan automáticamente con `@PrePersist` / `@PreUpdate`.

### 5.1 `Rol` — tabla `roles`

**Propósito:** Catálogo de roles de seguridad.

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| `id` | `Long` | PK, autoincremental | Identificador único. |
| `nombre` | `String` | obligatorio; único; máx. 30 | Nombre del rol: ADMIN, MEDICO o PACIENTE. Se escribe sin el prefijo ROLE_ (Spring Security lo agrega). |

**Relaciones:**
- 1 Rol → N Usuarios

### 5.2 `Usuario` — tabla `usuarios`

**Propósito:** Cuenta de acceso e identidad de cualquier persona del sistema (paciente, médico o administrador). Es la ÚNICA tabla que guarda nombres, DNI, correo, teléfono y contraseña.

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| `id` | `Long` | PK, autoincremental | Identificador único. |
| `nombres` | `String` | obligatorio; máx. 100 | Nombres de la persona. |
| `apellidos` | `String` | obligatorio; máx. 100 | Apellidos de la persona. |
| `dni` | `String` | obligatorio; único; máx. 12 | Documento de identidad (único en todo el sistema). |
| `email` | `String` | obligatorio; único; máx. 190 | Correo electrónico; se usa como nombre de usuario para iniciar sesión (único). Se guarda en minúsculas. |
| `telefono` | `String` | obligatorio; máx. 30 | Teléfono de contacto. |
| `passwordHash` | `String` | obligatorio; máx. 100 | Contraseña cifrada con BCrypt. Nunca se guarda en texto plano. |
| `estado` | `EstadoUsuario` | obligatorio; máx. 20; enum guardado como texto | Estado de la cuenta (ver EstadoUsuario). Solo ACTIVO puede iniciar sesión. |
| `rol` | `Rol` | FK → Rol; obligatorio | Rol del usuario (relación N:1 con Rol). |
| `ultimoAcceso` | `LocalDateTime` |  | Fecha y hora del último inicio de sesión. |
| `creadoEn` | `LocalDateTime` | obligatorio | Fecha de creación (se llena sola). |
| `actualizadoEn` | `LocalDateTime` |  | Fecha de la última modificación (se llena sola). |

**Relaciones:**
- N Usuarios → 1 Rol
- 1 Usuario → 0..1 Medico (solo si su rol es MEDICO)
- 1 Usuario (paciente) → N Citas
- 1 Usuario (paciente) → N Valoraciones
- 1 Usuario (admin) → N SolicitudesVerificacion revisadas
- 1 Usuario (admin) → N registros de Auditoria

**Reglas de negocio:**
- dni y email únicos.
- Solo estado ACTIVO puede iniciar sesión (UsuarioDetailsService marca la cuenta como deshabilitada en cualquier otro estado).
- Un médico se registra con estado PENDIENTE y pasa a ACTIVO cuando el admin aprueba su solicitud.

### 5.3 `Especialidad` — tabla `especialidades`

**Propósito:** Catálogo de especialidades médicas.

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| `id` | `Long` | PK, autoincremental | Identificador único. |
| `nombre` | `String` | obligatorio; único; máx. 100 | Nombre de la especialidad (único). Ej.: Cardiología. |
| `descripcion` | `String` | máx. 300 | Texto opcional explicativo. |
| `activa` | `boolean` | obligatorio; por defecto true | Si es false deja de ofrecerse sin borrarla. |

**Relaciones:**
- 1 Especialidad → N Medicos

### 5.4 `Distrito` — tabla `distritos`

**Propósito:** Catálogo de ubicaciones; evita escribir distritos como texto libre.

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| `id` | `Long` | PK, autoincremental | Identificador único. |
| `nombre` | `String` | obligatorio; máx. 100 | Nombre del distrito. Ej.: San Borja. |
| `provincia` | `String` | obligatorio; máx. 100 | Provincia. Ej.: Lima. |
| `departamento` | `String` | obligatorio; máx. 100 | Departamento. Ej.: Lima. |

**Restricción única compuesta:** (nombre, provincia)

**Relaciones:**
- 1 Distrito → N Establecimientos

**Reglas de negocio:**
- Restricción única (nombre, provincia).

### 5.5 `Establecimiento` — tabla `establecimientos`

**Propósito:** Clínica, centro médico o consultorio donde atienden los médicos.

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| `id` | `Long` | PK, autoincremental | Identificador único. |
| `nombre` | `String` | obligatorio; máx. 150 | Nombre de la clínica o consultorio. |
| `direccion` | `String` | máx. 200 | Dirección física. |
| `referencia` | `String` | máx. 200 | Referencia para ubicarlo (opcional). |
| `latitud` | `Double` |  | Coordenada para mapas (opcional). |
| `longitud` | `Double` |  | Coordenada para mapas (opcional). |
| `distrito` | `Distrito` | FK → Distrito; obligatorio | Distrito donde está ubicado (N:1 con Distrito). Permite buscar por distrito. |

**Relaciones:**
- N Establecimientos → 1 Distrito
- 1 Establecimiento → N Medicos

### 5.6 `Medico` — tabla `medicos`

**Propósito:** Perfil profesional de un usuario con rol MEDICO. Guarda solo lo profesional; los datos personales viven en Usuario.

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| `id` | `Long` | PK, autoincremental | Identificador único. |
| `usuario` | `Usuario` | FK → Usuario (1:1, única); obligatorio | Cuenta de acceso del médico (1:1 con Usuario). De aquí salen nombres, DNI, correo y teléfono. |
| `cmp` | `String` | obligatorio; único; máx. 20 | Número del Colegio Médico del Perú (único). |
| `rne` | `String` | máx. 20 | Registro Nacional de Especialista (opcional). |
| `aniosExperiencia` | `int` | obligatorio | Años de experiencia profesional. Entra en el puntaje de recomendación. |
| `tituloProfesional` | `String` | máx. 150 | Ej.: Médico cirujano. |
| `universidad` | `String` | máx. 180 | Universidad de egreso. |
| `anioEgreso` | `Integer` |  | Año de egreso. |
| `biografia` | `String` | máx. 2000 | Texto de la sección "Sobre la especialista". |
| `costoConsulta` | `BigDecimal` | decimal(10,2) | Precio de la consulta base (S/). |
| `ratingPromedio` | `double` | obligatorio | Promedio de valoraciones (0 a 5). Valor copiado/calculado para no hacer AVG en cada búsqueda; lo actualiza el servicio al crear una valoración. |
| `totalValoraciones` | `int` | obligatorio | Cantidad de valoraciones recibidas (acompaña al promedio). |
| `especialidad` | `Especialidad` | FK → Especialidad; obligatorio | Especialidad del médico (N:1 con Especialidad). |
| `establecimiento` | `Establecimiento` | FK → Establecimiento (opcional) | Sede donde atiende (N:1 con Establecimiento, opcional). |
| `creadoEn` | `LocalDateTime` | obligatorio | Fecha de creación (automática). |
| `actualizadoEn` | `LocalDateTime` |  | Última modificación (automática). |

**Relaciones:**
- 1 Medico ↔ 1 Usuario
- N Medicos → 1 Especialidad
- N Medicos → 0..1 Establecimiento
- 1 Medico → N HorariosAtencion
- 1 Medico → N ServiciosMedico
- 1 Medico → N FormacionesMedico
- 1 Medico → N SolicitudesVerificacion
- 1 Medico → N Citas
- 1 Medico → N Valoraciones

**Reglas de negocio:**
- Un usuario solo puede tener un perfil de médico (usuario_id único).
- cmp único.
- No se borra físicamente: se suspende o se marca ELIMINADO en su Usuario.

### 5.7 `HorarioAtencion` — tabla `horarios_atencion`

**Propósito:** Franjas semanales en las que un médico atiende. De aquí se calculan los turnos disponibles.

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| `id` | `Long` | PK, autoincremental | Identificador único. |
| `medico` | `Medico` | FK → Medico; obligatorio | Médico dueño del horario (N:1). |
| `diaSemana` | `int` | obligatorio | Día de la semana: 1 = lunes … 7 = domingo. |
| `horaInicio` | `LocalTime` | obligatorio | Hora en que empieza a atender. |
| `horaFin` | `LocalTime` | obligatorio | Hora en que termina de atender. |
| `duracionCitaMin` | `int` | obligatorio; por defecto 40 | Duración de cada cita en minutos (por defecto 40). Con esto se generan los turnos 09:00, 09:40, 10:20… |
| `modalidad` | `ModalidadAtencion` | obligatorio; máx. 20; enum guardado como texto; por defecto ModalidadAtencion.AMBAS | Modalidad en la que atiende en esta franja (ver ModalidadAtencion). |
| `activo` | `boolean` | obligatorio; por defecto true | Permite desactivar una franja sin borrarla. |

**Relaciones:**
- N Horarios → 1 Medico

**Reglas de negocio:**
- Validar en el servicio que no se solapen franjas del mismo médico y día.

### 5.8 `ServicioMedico` — tabla `servicios_medico`

**Propósito:** Lista de servicios/procedimientos con precio que muestra el perfil del médico.

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| `id` | `Long` | PK, autoincremental | Identificador único. |
| `medico` | `Medico` | FK → Medico; obligatorio | Médico que ofrece el servicio (N:1). |
| `nombre` | `String` | obligatorio; máx. 150 | Nombre del servicio o procedimiento. Ej.: Electrocardiograma. |
| `descripcion` | `String` | máx. 300 | Detalle del servicio. |
| `precio` | `BigDecimal` | obligatorio; decimal(10,2) | Precio en soles. |
| `activo` | `boolean` | obligatorio; por defecto true | Si el servicio se muestra en el perfil. |

**Relaciones:**
- N Servicios → 1 Medico

### 5.9 `FormacionMedico` — tabla `formaciones_medico`

**Propósito:** Estudios, certificaciones y membresías del médico (sección "Formación académica").

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| `id` | `Long` | PK, autoincremental | Identificador único. |
| `medico` | `Medico` | FK → Medico; obligatorio | Médico al que pertenece (N:1). |
| `tipo` | `TipoFormacion` | obligatorio; máx. 20; enum guardado como texto | Tipo de formación (ver TipoFormacion). |
| `titulo` | `String` | obligatorio; máx. 200 | Nombre del título o certificación. |
| `institucion` | `String` | obligatorio; máx. 200 | Institución que lo otorgó. |
| `anio` | `Integer` |  | Año en que se obtuvo (opcional). |

**Relaciones:**
- N Formaciones → 1 Medico

### 5.10 `SolicitudVerificacion` — tabla `solicitudes_verificacion`

**Propósito:** Trámite de verificación de un médico que revisa el administrador. Está separada de Medico porque tiene su propio ciclo de vida y un médico rechazado puede volver a enviar otra.

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| `id` | `Long` | PK, autoincremental | Identificador único. |
| `medico` | `Medico` | FK → Medico; obligatorio | Médico que pide ser verificado (N:1). |
| `sustentoUrl` | `String` | obligatorio; máx. 500 | Enlace a la constancia CMP, RNE o título que el admin abre para corroborar. |
| `estado` | `EstadoSolicitud` | obligatorio; máx. 20; enum guardado como texto; por defecto EstadoSolicitud.PENDIENTE | Estado de la revisión (ver EstadoSolicitud). Por defecto PENDIENTE. |
| `motivoRechazo` | `String` | máx. 500 | Motivo escrito por el admin cuando rechaza. |
| `revisadoPor` | `Usuario` | FK → Usuario (opcional) | Admin que revisó (N:1 con Usuario). Nulo mientras está pendiente. |
| `fechaSolicitud` | `LocalDateTime` | obligatorio | Cuándo se envió (se llena sola). |
| `fechaRevision` | `LocalDateTime` |  | Cuándo la revisó el admin. |

**Relaciones:**
- N Solicitudes → 1 Medico
- N Solicitudes → 0..1 Usuario (admin revisor)

**Reglas de negocio:**
- Validar en el servicio que un médico tenga como máximo una solicitud PENDIENTE.
- Al aprobar: Usuario.estado → ACTIVO y se registra en Auditoria.

### 5.11 `Cita` — tabla `citas`

**Propósito:** Reserva de un paciente con un médico en una fecha/hora concreta.

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| `id` | `Long` | PK, autoincremental | Identificador único. |
| `paciente` | `Usuario` | FK → Usuario; obligatorio | Usuario con rol PACIENTE que reserva (N:1 con Usuario). |
| `medico` | `Medico` | FK → Medico; obligatorio | Médico con quien se reserva (N:1 con Medico). |
| `fechaHoraInicio` | `LocalDateTime` | obligatorio | Inicio de la cita. |
| `fechaHoraFin` | `LocalDateTime` | obligatorio | Fin de la cita (inicio + duración del horario). |
| `modalidad` | `Modalidad` | obligatorio; máx. 20; enum guardado como texto | Presencial o teleconsulta (ver Modalidad). |
| `motivo` | `String` | máx. 500 | Motivo de consulta o síntomas que escribe el paciente. |
| `estado` | `EstadoCita` | obligatorio; máx. 20; enum guardado como texto; por defecto EstadoCita.PENDIENTE | Estado de la cita (ver EstadoCita). Por defecto PENDIENTE. |
| `motivoCancelacion` | `String` | máx. 300 | Razón si fue cancelada. |
| `precio` | `BigDecimal` | decimal(10,2) | Precio acordado al reservar (copia del costo en ese momento; si el médico cambia su tarifa, las citas antiguas no cambian). |
| `version` | `Long` | control de versión | Número de versión para bloqueo optimista: evita que dos personas modifiquen/reserven la misma cita a la vez. |
| `creadoEn` | `LocalDateTime` | obligatorio | Fecha de creación (automática). |
| `actualizadoEn` | `LocalDateTime` |  | Última modificación (automática). |

**Restricción única compuesta:** (medico_id, fecha_hora_inicio)

**Relaciones:**
- N Citas → 1 Usuario (paciente)
- N Citas → 1 Medico
- 1 Cita ↔ 0..1 Valoracion

**Reglas de negocio:**
- Restricción única (medico_id, fecha_hora_inicio): evita dos reservas en la misma hora.
- Validar en el servicio que la fecha no sea pasada y que caiga dentro del horario del médico.
- Cancelar = cambiar estado a CANCELADA; no se borra.

### 5.12 `Valoracion` — tabla `valoraciones`

**Propósito:** Opinión y puntaje que deja un paciente sobre una cita completada.

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| `id` | `Long` | PK, autoincremental | Identificador único. |
| `cita` | `Cita` | FK → Cita (1:1, única); obligatorio | Cita que originó la opinión (1:1 con Cita, única). Garantiza una reseña por cita. |
| `paciente` | `Usuario` | FK → Usuario; obligatorio | Quien opina (N:1 con Usuario). |
| `medico` | `Medico` | FK → Medico; obligatorio | Médico valorado (N:1 con Medico). Se repite aquí para consultar reseñas por médico sin pasar por Cita. |
| `puntajeGeneral` | `int` | obligatorio | Puntaje total de 1 a 5. |
| `calidez` | `Integer` |  | Criterio "Calidez y atención" (1 a 5, opcional). |
| `explicacion` | `Integer` |  | Criterio "Explicación del diagnóstico" (1 a 5, opcional). |
| `puntualidad` | `Integer` |  | Criterio "Puntualidad" (1 a 5, opcional). |
| `instalaciones` | `Integer` |  | Criterio "Instalaciones" (1 a 5, opcional). |
| `comentario` | `String` | máx. 500 | Texto de la opinión. |
| `creadaEn` | `LocalDateTime` | obligatorio | Fecha de la opinión (automática). |

**Relaciones:**
- 1 Valoracion ↔ 1 Cita
- N Valoraciones → 1 Usuario (paciente)
- N Valoraciones → 1 Medico

**Reglas de negocio:**
- Solo se permite valorar una cita COMPLETADA y solo una vez (cita_id único).
- Los puntajes van de 1 a 5 (validar en el servicio).
- Al guardar, el servicio recalcula Medico.ratingPromedio y totalValoraciones.

### 5.13 `Auditoria` — tabla `auditoria`

**Propósito:** Registro de las acciones del administrador (quién hizo qué y cuándo).

| Atributo | Tipo | Restricciones | Descripción |
|---|---|---|---|
| `id` | `Long` | PK, autoincremental | Identificador único. |
| `usuario` | `Usuario` | FK → Usuario; obligatorio | Admin que realizó la acción (N:1 con Usuario). |
| `accion` | `AccionAuditoria` | obligatorio; máx. 40; enum guardado como texto | Qué hizo (ver AccionAuditoria). |
| `entidad` | `String` | obligatorio; máx. 60 | Nombre del modelo afectado. Ej.: "Medico". |
| `entidadId` | `Long` |  | ID del registro afectado. |
| `detalle` | `String` | máx. 500 | Descripción adicional (motivo, valores…). |
| `fecha` | `LocalDateTime` | obligatorio | Cuándo ocurrió (automática). |

**Relaciones:**
- N Registros → 1 Usuario (admin)

## 6. Repositorios (`repository`)

Cada entidad tiene una interfaz `XxxRepository extends JpaRepository<Xxx, Long>` que ya incluye `save`, `findById`, `findAll`, `delete`, `count`, etc. Además:

| Repositorio | Métodos adicionales |
|---|---|
| `RolRepository` | `findByNombre(String nombre)` |
| `UsuarioRepository` | `findByEmail(String email)`<br>`existsByEmail(String email)`<br>`existsByDni(String dni)`<br>`findByRolNombre(String rol)` |
| `EspecialidadRepository` | `findByNombre(String nombre)`<br>`findByActivaTrueOrderByNombreAsc()` |
| `DistritoRepository` | `findByNombreAndProvincia(String nombre, String provincia)` |
| `EstablecimientoRepository` | `findByDistritoId(Long distritoId)` |
| `MedicoRepository` | `findByUsuarioId(Long usuarioId)`<br>`existsByCmp(String cmp)`<br>`findByCmp(String cmp)` *(nuevo: reenvío de solicitud rechazada)*<br>`findByEspecialidadIdOrderByRatingPromedioDesc(Long especialidadId)`<br>`findByIdAndUsuarioEstado(Long id, EstadoUsuario estado)` *(perfil público: solo médicos ACTIVOS)* |
| `HorarioAtencionRepository` | `findByMedicoIdAndActivoTrue(Long medicoId)` |
| `ServicioMedicoRepository` | `findByMedicoIdAndActivoTrue(Long medicoId)` |
| `FormacionMedicoRepository` | `findByMedicoId(Long medicoId)` |
| `SolicitudVerificacionRepository` | `findByEstadoOrderByFechaSolicitudDesc(EstadoSolicitud estado)`<br>`existsByMedicoIdAndEstado(Long medicoId, EstadoSolicitud estado)` |
| `CitaRepository` | `findByPacienteIdOrderByFechaHoraInicioDesc(Long pacienteId)`<br>`findByMedicoIdAndFechaHoraInicioBetween(Long medicoId, LocalDateTime desde, LocalDateTime hasta)`<br>`existsByMedicoIdAndFechaHoraInicio(Long medicoId, LocalDateTime fechaHoraInicio)`<br>`countByMedicoIdAndEstado(Long medicoId, EstadoCita estado)` *(pacientes atendidos)* |
| `ValoracionRepository` | `findByMedicoIdOrderByCreadaEnDesc(Long medicoId)`<br>`existsByCitaId(Long citaId)` |
| `AuditoriaRepository` | `findTop50ByOrderByFechaDesc()` |

## 7. Configuración y arranque (`config`)

| Clase | Qué hace |
|---|---|
| `SecurityConfig` | Define qué rutas son públicas (`/`, `/login`, `/register/**`, `/doctor/**`, `/error`, `/css`, `/js`, `/img`, Swagger), exige rol `ADMIN` en `/admin/**` (incluye `/admin/api/**`) y autenticación en el resto (ej. `/account`). Expone el bean `PasswordEncoder` (BCrypt). Login por formulario (campos `email` y `password`), logout por POST a `/logout`. **Éxito de login:** admin → `/admin/doctor-applications`, otros → `/account`. **Fallo de login:** si la cuenta está deshabilitada (`DisabledException`: médico pendiente, rechazado o suspendido) redirige a `/login?disabled`; en cualquier otro caso a `/login?error`. CSRF está activo (valores por defecto de Spring Security). |
| `UsuarioDetailsService` | Busca al `Usuario` por correo (en minúsculas) para Spring Security, asigna la autoridad `ROLE_<rol>` y deshabilita la cuenta si su estado no es `ACTIVO`. Actualiza `ultimoAcceso` solo cuando la cuenta está activa. |
| `DataInitializer` | `@Order(1)`. Al arrancar crea (si no existen) los roles, 8 especialidades, 5 distritos, 3 establecimientos y el usuario administrador (`medicerca.admin.email` / `medicerca.admin.password`). Es idempotente: se puede ejecutar en cada arranque. |
| `DemoDataLoader` | `@Order(2)` (corre después del `DataInitializer`). Solo si no hay médicos, crea 3 médicos ACTIVOS de ejemplo (Rosa Medina, Luis Quispe, Ana Torres) con horarios, servicios, formación, 4 pacientes y reseñas. Se desactiva con `medicerca.demo-data=false`. Contraseñas: médicos `Medico123!`, pacientes `Paciente123!`. |

## 8. Excepciones (`exception`)

| Clase | Para qué sirve |
|---|---|
| `NegocioException` | `RuntimeException` con un mensaje apto para mostrar al usuario (validaciones, duplicados, estados inválidos). Las capturan `AuthController` (muestra el error en el formulario) y `AdminApiController` (responde HTTP 400 con `{"message": "..."}`). |

## 9. Servicios (`service`)

### `RegistroService` — registro de pacientes y médicos

| Método | Qué hace |
|---|---|
| `registrarPaciente(RegistroPacienteForm)` | Valida los datos, comprueba que email y DNI no existan y crea un `Usuario` con rol `PACIENTE` y estado `ACTIVO`. |
| `registrarMedico(RegistroMedicoForm)` | Valida datos personales y profesionales, comprueba que email, DNI y CMP no existan, y en una sola transacción crea: `Usuario` (rol `MEDICO`, estado `PENDIENTE`) → `Medico` → `SolicitudVerificacion` (estado `PENDIENTE`). |
| *(interno)* reenvío | Si el email ya existe y pertenece a un médico **rechazado**, permite volver a enviar la solicitud. Requisitos: rol MEDICO, usuario PENDIENTE, contraseña correcta, DNI igual al original, última solicitud `RECHAZADA` y ninguna `PENDIENTE`. Actualiza los datos del médico y crea una nueva `SolicitudVerificacion` PENDIENTE. Si no se cumplen: "Ya existe una cuenta con ese correo electrónico." |
| `especialidades()` | Lista (id, nombre) de especialidades activas para el `<select>` del formulario de médico. |
| `establecimientos()` | Lista (id, "nombre — distrito") de establecimientos para el `<select>`. |

**Reglas de validación del registro** (se aplican en el servidor aunque el HTML ya valide):

| Campo | Regla |
|---|---|
| Nombres / apellidos | Obligatorios, máx. 100. |
| DNI | Exactamente 8 dígitos; único. |
| Teléfono | 6 a 30 caracteres entre dígitos, espacios, `+`, `(`, `)`, `-`. |
| Correo | Formato válido, máx. 190; se guarda en minúsculas; único. |
| Contraseña | Mínimo 8 caracteres y máximo 72 bytes (límite de BCrypt). Se guarda cifrada con BCrypt. |
| CMP | 4 a 20 letras, números o guiones; se guarda en mayúsculas; único. |
| RNE | Opcional, máx. 20. |
| Título profesional / universidad | Obligatorios, máx. 150 / 180. |
| Año de egreso | Entre 1950 y el año actual. |
| Años de experiencia | Entre 0 y 80. |
| Especialidad | Obligatoria, debe existir y estar activa. |
| Establecimiento | Opcional; si se envía, debe existir. |
| Enlace de sustento | Obligatorio, máx. 500, debe ser `http://` o `https://` con host (se bloquean esquemas como `javascript:` porque el admin abre este enlace). |

### `AdminService` — panel de administración

| Método | Qué hace |
|---|---|
| `datos()` | Devuelve `AdminDataView`: todas las solicitudes (más recientes primero), los médicos con cuenta ACTIVA o SUSPENDIDA, el número de pacientes (no eliminados) y las últimas 6 acciones de auditoría. |
| `aprobar(solicitudId, adminEmail)` | Solo si la solicitud está PENDIENTE: pasa a `APROBADA`, guarda `revisadoPor` y `fechaRevision`, el `Usuario` del médico pasa a `ACTIVO` y se registra `APROBAR_MEDICO` en `Auditoria`. |
| `rechazar(solicitudId, motivo, adminEmail)` | Solo si está PENDIENTE; el motivo es obligatorio (máx. 500). La solicitud pasa a `RECHAZADA` con `motivoRechazo`; el usuario **sigue PENDIENTE** (no puede entrar). Registra `RECHAZAR_MEDICO`. |
| `suspender(medicoId, adminEmail)` | Solo si el usuario está ACTIVO → `SUSPENDIDO`. Registra `SUSPENDER_MEDICO`. |
| `reactivar(medicoId, adminEmail)` | Solo si está SUSPENDIDO → `ACTIVO`. Registra `REACTIVAR_MEDICO`. |
| `eliminar(medicoId, adminEmail)` | Borrado lógico: el usuario pasa a `ELIMINADO` (el registro se conserva). Registra `ELIMINAR_MEDICO`. |

Todas las acciones son transaccionales y dejan un registro en `Auditoria` con el admin que las hizo.

### `CuentaService`

| Método | Qué hace |
|---|---|
| `cuenta(email)` | Arma `AccountView` para la página `/account`: datos del usuario, rol en texto (Administrador / Paciente / Médico), `medicoId` si es médico y, si es paciente, la lista de sus citas (más recientes primero). |

### `DoctorProfileService`

Construye el perfil público dinámico (`DoctorProfileView`) y calcula los turnos disponibles (`SlotView`) a partir de `HorarioAtencion` menos las citas no canceladas. Solo muestra médicos con cuenta `ACTIVO`.

## 10. DTOs (`dto`)

| DTO | Uso |
|---|---|
| `RegistroPacienteForm` | Datos del formulario de paciente (nombres, apellidos, dni, telefono, email, password). |
| `RegistroMedicoForm` | Datos del formulario de médico (los anteriores + cmp, rne, tituloProfesional, universidad, anioEgreso, aniosExperiencia, especialidadId, establecimientoId, sustentoUrl). |
| `OpcionView` | Opción de un `<select>` (id, label). |
| `AdminDataView` | JSON del panel admin, con `ApplicationView`, `DoctorView` y `ActivityView`. Los nombres de campo deben coincidir con los que lee `admin.js`. |
| `AccountView` | Datos de `/account` (con `CitaItem` para cada cita). |
| `DoctorProfileView` / `SlotView` | Perfil público y turnos. |

## 11. Controladores (`Controller`)

> El paquete se llama `utp.edu.pe.recomendaciones.Controller` (con mayúscula), tal como está en el proyecto.

| Clase | Ruta | Acceso | Qué hace |
|---|---|---|---|
| `HomeController` | `GET /` | Público | Página de inicio. |
| `AuthController` | `GET /login` | Público | Pantalla de acceso/registro. Carga las especialidades y establecimientos para el formulario de médico. |
| | `POST /register/patient` | Público | Registra un paciente. Éxito → `/login` con mensaje. Error → `/login#register-patient` con el mensaje y los datos escritos (sin contraseña). |
| | `POST /register/doctor` | Público | Envía la solicitud de médico. Éxito → `/login` con mensaje. Error → `/login#register-doctor`. |
| `AccountController` | `GET /account` | Autenticado | "Mi cuenta" con los datos reales del usuario con sesión. |
| `AdminController` | `GET /admin/doctor-applications` | ADMIN | Página del panel de administración. |
| `AdminApiController` | `GET /admin/api/data` | ADMIN | JSON con solicitudes, médicos, pacientes y actividad. |
| | `POST /admin/api/applications/{id}/approve` | ADMIN | Aprueba la solicitud `{id}`. |
| | `POST /admin/api/applications/{id}/reject` | ADMIN | Rechaza; parámetro de formulario `reason` (obligatorio). |
| | `POST /admin/api/doctors/{id}/suspend` | ADMIN | Suspende al médico `{id}` (id de `Medico`). |
| | `POST /admin/api/doctors/{id}/reactivate` | ADMIN | Reactiva al médico. |
| | `POST /admin/api/doctors/{id}/delete` | ADMIN | Eliminación lógica. |
| `DoctorController` | `GET /doctor/{id}` | Público | Perfil dinámico del médico ACTIVO (plantilla `doctor/profile`). |
| | `GET /doctor/{id}/slots?date=AAAA-MM-DD&modalidad=PRESENCIAL` | Público | JSON con los turnos del día. |
| | `GET /doctor/profile` | Público | Redirige a `/doctor/1` (los enlaces de ejemplo del home). |

Los POST de `/admin/api/**` exigen el token CSRF en la cabecera (el JS lo lee del campo oculto `#csrf` de la plantilla). Ante un error de negocio responden HTTP 400 con `{"message": "..."}`.

## 12. Vistas y JavaScript (`resources`)

| Archivo | Qué hace |
|---|---|
| `templates/auth/auth.html` | Login y registro. Los formularios hacen POST real (ya no hay modo demo). Muestra mensajes de éxito/error (`success`, `error`), conserva lo escrito tras un error (`f`) y carga los `<select>` de especialidad y establecimiento desde la base de datos. |
| `static/js/pages/auth.js` | Cambia entre pestañas (login/registro, paciente/médico), abre la pestaña correcta según el hash de la URL (`#register`, `#register-patient`, `#register-doctor`) y muestra el mensaje de `/login?error` o `/login?disabled`. |
| `templates/account/account.html` | Datos reales de la cuenta; para pacientes lista sus citas; médicos ven "Ver perfil público"; admin ve "Revisar solicitudes". |
| `templates/admin/doctor-applications.html` | Panel admin. Incluye el campo oculto `#csrf` y el correo del admin con sesión. |
| `static/js/pages/admin.js` | Carga los datos con `GET admin/api/data` y ejecuta aprobar, rechazar, suspender, reactivar y eliminar contra la API; después vuelve a cargar los datos. Dashboard (KPIs, solicitudes recientes, médicos por especialidad, actividad) con datos reales. |
| `templates/doctor/profile.html` + `static/js/pages/doctor-profile.js` | Perfil público dinámico; los turnos se piden a `/doctor/{id}/slots`. |

## 13. Flujos principales

**Registro de paciente** (funciona)
1. El paciente llena "Crear cuenta → Soy paciente" y se envía `POST /register/patient`.
2. Se crea el `Usuario` con rol `PACIENTE` y estado `ACTIVO`.
3. Se muestra "Cuenta creada" y el paciente inicia sesión con su correo y contraseña; entra a `/account`.

**Registro y verificación de un médico** (funciona)
1. El médico llena "Crear cuenta → Soy médico" y se envía `POST /register/doctor`.
2. En una transacción se crean: `Usuario` (rol `MEDICO`, estado `PENDIENTE`), `Medico` y `SolicitudVerificacion` en estado `PENDIENTE` con el enlace de sustento.
3. Mientras esté pendiente, el login muestra que la cuenta aún no está activa.
4. El admin entra a `/admin/doctor-applications`, revisa el detalle y el enlace de sustento:
   - **Aprueba** → solicitud `APROBADA`, usuario `ACTIVO`, registro en `Auditoria`. El médico ya puede iniciar sesión y su perfil público (`/doctor/{id}`) está disponible.
   - **Rechaza** (con motivo) → solicitud `RECHAZADA`, el usuario sigue `PENDIENTE`, registro en `Auditoria`.
5. Si fue rechazado, puede volver a enviar la solicitud desde "Soy médico" con el mismo correo, DNI y contraseña; se crea una nueva solicitud `PENDIENTE`.

**Inicio de sesión** (funciona)
- Credenciales correctas y cuenta `ACTIVO` → admin va a `/admin/doctor-applications`, el resto a `/account`.
- Cuenta `PENDIENTE`, `SUSPENDIDO` o `ELIMINADO` → `/login?disabled`.
- Correo o contraseña incorrectos → `/login?error`.

**Gestión de cuentas de médicos por el admin** (funciona)
- Suspender (ACTIVO → SUSPENDIDO), reactivar (SUSPENDIDO → ACTIVO) y eliminar (borrado lógico → ELIMINADO). Cada acción queda en `Auditoria`.

**Reserva de una cita** (pendiente de implementar; los turnos sí se calculan)
1. El paciente elige médico, modalidad, día y hora (los turnos salen de `HorarioAtencion` menos las citas ya tomadas).
2. Se crea una `Cita` en estado `PENDIENTE` con `precio` copiado del `costoConsulta` del médico. La restricción única impide reservar la misma hora dos veces.
3. La cita pasa a `CONFIRMADA`, y luego a `COMPLETADA` o `NO_ASISTIO`; puede quedar `CANCELADA`.

**Valoración y recomendación** (pendiente de implementar; la lectura de reseñas en el perfil sí funciona)
1. Con la cita `COMPLETADA`, el paciente crea una `Valoracion` (una sola por cita).
2. El servicio recalcula `Medico.ratingPromedio` y `totalValoraciones`.
3. El puntaje de recomendación combina valoración y experiencia; se calcula al consultar, no se guarda.

## 14. Decisiones de diseño (buenas prácticas aplicadas)

- **Normalización (3FN):** los datos personales viven solo en `Usuario`; `Medico` y `Cita` no los repiten.
- **Catálogos separados** (`Rol`, `Especialidad`, `Distrito`) para evitar texto libre y duplicados.
- **Verificación aparte** (`SolicitudVerificacion`) para conservar el historial de intentos.
- **Desnormalización controlada:** solo `ratingPromedio`/`totalValoraciones` (rendimiento de búsquedas) y `Cita.precio` (historial de precios).
- **Borrado lógico** vía estados en lugar de eliminar filas.
- **Bloqueo optimista** (`@Version`) en `Cita` para reservas concurrentes.
- **Enums como texto** y relaciones `LAZY` sin cascadas de borrado. Como `spring.jpa.open-in-view=false`, todo acceso a relaciones se hace dentro de métodos `@Transactional` de los servicios.
- **Contraseñas con BCrypt**; nunca se guardan ni se devuelven en texto plano (al volver a un formulario con error no se reenvía la contraseña).
- **No exponer entidades en los controladores:** se usan DTOs (`record`).
- **Validación en el servidor** además de la del HTML.
- **Seguridad del panel admin:** `/admin/**` exige `ROLE_ADMIN`, las acciones son POST con CSRF y el enlace de sustento solo admite `http/https`. Todo el texto que muestra `admin.js` se escapa antes de insertarlo en el HTML.

## 15. Base de datos y perfiles

- **Por defecto** la aplicación usa **H2 en archivo** (`jdbc:h2:file:./data/medicerca;MODE=MySQL`): no requiere instalar nada y **los datos se conservan al reiniciar** (se guardan en la carpeta `data/`, que está en `.gitignore`). Para empezar de cero, borra esa carpeta. Hibernate crea las tablas (`ddl-auto=update`) y `DataInitializer` carga los datos base.
- **MySQL:** arrancar con `--spring.profiles.active=mysql` y definir `MYSQL_PASSWORD`. Usa la base nueva `medicerca_app` (se crea sola).
- **Pruebas:** `src/test/resources/application.properties` usa H2 en memoria con `create-drop`.
- **Administrador inicial:** se crea al configurar `ADMIN_EMAIL` y `ADMIN_PASSWORD` en el entorno; no hay credenciales predeterminadas en el repositorio.
- **Datos de ejemplo:** `DemoDataLoader` (activo por defecto; `medicerca.demo-data=false` para desactivarlo).

## 16. Estado actual del proyecto

### Funciona (conectado a la base de datos)

- Registro de pacientes (cuenta activa al instante).
- Registro de médicos como solicitud pendiente de aprobación.
- Reenvío de solicitud por parte de un médico rechazado.
- Login real con control de estado de cuenta y mensajes distintos para "credenciales incorrectas" y "cuenta no activa".
- Panel admin: lista de solicitudes con filtros y búsqueda, ver detalle, aprobar, rechazar con motivo; lista de médicos, suspender, reactivar y eliminar (lógico); dashboard con datos reales; registro de auditoría.
- Página "Mi cuenta" con datos reales y las citas del paciente.
- Perfil público dinámico del médico (`/doctor/{id}`), con horarios, servicios, formación y reseñas desde la base de datos, y turnos calculados.

### Pendiente por implementar

- **Reserva real de citas** (`POST /appointments`): hoy el formulario del perfil es demostración.
- **Publicar valoraciones** y recalcular `ratingPromedio`/`totalValoraciones`: hoy el formulario es demostración.
- **Búsqueda real de médicos** en el home (las tarjetas del home son estáticas y sus enlaces van a `/doctor/profile`, que redirige a `/doctor/1`).
- **Panel del médico** para editar su perfil, servicios y horarios (los médicos nuevos aprobados aparecen sin biografía, servicios ni horarios hasta que exista esta pantalla).
- Campo de género en `Medico`: hoy "Dr." / "Dra." se deduce del primer nombre.
- Mostrar al médico rechazado el motivo del rechazo.
- Recuperación de contraseña y verificación de correo.

### Limitaciones conocidas

- El mensaje de "cuenta no activa" en el login se muestra antes de comprobar la contraseña, por lo que permite saber si un correo existe y está pendiente. Es aceptable en este alcance; se puede endurecer más adelante.
- El código se escribió y revisó sin poder compilarlo en el entorno de edición; la primera ejecución con Maven (`mvn spring-boot:run`) es la prueba definitiva.

## 17. Cómo probar el flujo de cuentas

1. Arranca la app y entra a `http://localhost:8080/login`.
2. **Paciente:** "Crear cuenta → Soy paciente", registra y luego inicia sesión → debe llevarte a `/account`.
3. **Médico:** "Crear cuenta → Soy médico", registra (usa un enlace `https://...` de sustento). Intenta iniciar sesión con esa cuenta → debe decir que no está activa.
4. **Admin:** inicia sesión con `admin@medicerca.local` / `Admin123!`, abre "Solicitudes de médicos", aprueba la solicitud.
5. Cierra sesión e inicia con la cuenta del médico → ahora entra a `/account` y ve "Ver perfil público".
6. Repite el paso 3 con otro médico y esta vez **recházalo** con un motivo; comprueba que no puede entrar y que puede reenviar la solicitud desde "Soy médico".


## 18. Ampliaciones del perfil médico (segunda fase)

Columnas nuevas (todas las crea Hibernate con `ddl-auto=update`; son opcionales o con valor por defecto, así que no afectan a los datos existentes):

| Tabla | Columna | Tipo | Significado |
|---|---|---|---|
| `medicos` | `costo_teleconsulta` | decimal(10,2) null | Precio de teleconsulta. Vacío = se cobra `costo_consulta`. |
| `medicos` | `costo_por_hora` | decimal(10,2) null | Tarifa referencial por hora (informativa). |
| `establecimientos` | `propietario_id` | FK → `medicos.id` null | Médico que creó la sede. `null` = sede del catálogo (compartida). |
| `valoraciones` | `visible_publico` | boolean not null default true | Si la reseña se muestra en `/doctor/{id}`. No altera la reseña ni el rating. |

Reglas:
- `medicos.costo_consulta` es el precio **presencial** y el precio base. `Medico.precioPara(Modalidad)` devuelve el precio que corresponde a una cita (úsalo para `Cita.precio`).
- Una sede con `propietario_id` solo la ve, elige, edita y elimina su dueño. Las sedes del catálogo (`propietario_id` null) solo se pueden elegir.
- Las franjas de `horarios_atencion` activas de un mismo médico y día no pueden solaparse.


---

## 19. Dashboard y agenda del médico (tercera fase)

Solo lectura sobre las citas reales; no se agregaron tablas ni columnas y no se tocó la reserva.

| Ruta | Qué muestra |
|---|---|
| `GET /medico/dashboard` | Pacientes atendidos, citas programadas, citas pendientes, calificación promedio y próximas 5 citas |
| `GET /medico/agenda?vista=dia\|semana\|mes&fecha=AAAA-MM-DD` | Calendario con las citas del periodo |
| `GET /medico/citas/{id}` | Detalle: paciente, contacto, fecha y hora, modalidad, sede, estado, motivo y precio |

Definiciones: pacientes atendidos = pacientes distintos con alguna cita `COMPLETADA`; programadas = `CONFIRMADA` desde ahora; pendientes = `PENDIENTE` desde ahora; próximas = las 5 siguientes `PENDIENTE` o `CONFIRMADA`. La calificación sale de `Medico.ratingPromedio` y `totalValoraciones`.

Seguridad: el médico sale de `MedicoSesion.actual(email)`; todas las consultas de `AgendaCitaRepository` filtran por su id y una cita ajena se trata como inexistente. `Cita` no guarda la sede: en consultas presenciales se muestra la sede principal actual del médico.

Archivos nuevos: `repository/AgendaCitaRepository`, `service/AgendaMedicoService`, `Controller/MedicoAgendaController`, `dto/DashboardMedicoView`, `AgendaView`, `CitaResumen`, `CitaDetalleView`, `templates/medico/dashboard.html`, `agenda.html`, `cita.html`, `static/css/pages/medico-agenda.css`. Modificado: `templates/fragments/medico-tabs.html` (pestañas Resumen y Agenda).


---

## 20. Ajustes finales, parte 1: navbar y correo

**Navbar.** `config/NavbarAdvice` agrega a todas las vistas el atributo `nav` (`nav.medico()` = hay una sesión con `ROLE_MEDICO`). `fragments/navbar.html` lo usa: un médico con sesión ve "Mi perfil" (`/medico/perfil`) en `home`, `profile`, `auth` y `session`; en `home` y `profile` el botón de iniciar sesión se cambia por "Cerrar sesión". En `session` (todas las vistas `/medico/**`) se quita "Buscar médicos" y quedan "Mi perfil" y "Cerrar sesión". Para pacientes, administradores y visitantes nada cambia.

**Correo.** Dependencia `spring-boot-starter-mail`. Propiedades `spring.mail.*` y `medicerca.mail.*` al final de `application.properties`. `service/CorreoService` (`disponible()`, `motivoNoDisponible()`, `verificarConexion()`, `enviarTexto()`, `enviarHtml()`) todavía no la usa ningún flujo. `config/CorreoStartupCheck` deja en el log el estado del correo y, si `MAIL_VERIFY_ON_STARTUP=true`, prueba la conexión SMTP sin enviar nada.

| Variable de entorno | Para qué | Por defecto |
|---|---|---|
| `MAIL_USERNAME` | Cuenta de correo (Gmail completo) | vacío |
| `MAIL_PASSWORD` | Contraseña de aplicación de Google (16 letras, sin espacios) | vacío |
| `MAIL_FROM` | Remitente visible | `MAIL_USERNAME` |
| `MAIL_FROM_NAME` | Nombre visible del remitente | `MediCerca` |
| `MAIL_HOST` / `MAIL_PORT` | Servidor SMTP | `smtp.gmail.com` / `587` |
| `MAIL_STARTTLS` / `MAIL_SSL` | 587 → STARTTLS; 465 → `MAIL_SSL=true` y `MAIL_STARTTLS=false` | `true` / `false` |
| `MAIL_VERIFY_ON_STARTUP` | Probar login SMTP al arrancar | `false` |

Alternativa a las variables: copiar `config/mail.local.properties.example` a `config/mail.local.properties` (ignorado por git). Las variables de entorno tienen prioridad sobre ese archivo.

## 18. Parte 2: notificaciones a médicos y cambio de credenciales

**Nueva entidad `CodigoSeguridad` — tabla `codigos_seguridad`** (enum `PropositoCodigo`: `CAMBIO_PASSWORD`, `CAMBIO_EMAIL`).

| Campo | Descripción |
|---|---|
| `usuario_id` | Usuario (médico) al que se le emitió el código (N : 1 con `usuarios`). |
| `proposito` | Para qué se emitió. |
| `codigoHash` | Hash BCrypt del código de 6 dígitos (el código nunca se guarda en claro). |
| `nuevoEmail` | Solo `CAMBIO_EMAIL`: correo nuevo ligado al código. |
| `intentos` / `consumido` | Intentos fallidos y si ya no sirve (usado, anulado o reemplazado). |
| `creadoEn` / `expiraEn` | Emisión y caducidad (10 minutos por defecto). |

**Servicios y controladores nuevos:** `NotificacionMedicoService` (correo al aprobar/rechazar), `CredencialesMedicoService` (códigos y cambio de credenciales) y `MedicoCredencialesController` (`/medico/credenciales/**`). Resumen completo en `CONTEXTO_FINAL.md`.
