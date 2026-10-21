# MediCerca — Plataforma de recomendación y citas médicas

Plataforma web que conecta pacientes con profesionales médicos: permite buscar
especialistas, consultar perfiles, reservar citas y darles seguimiento, y da a
los médicos y administradores sus propias herramientas de gestión.

---

## Problema

El paciente que no sabe qué especialista necesita elige mal, no vuelve y deja la
cita sin concretar. Cada mes eso se traduce en consultas agendadas que se pierden
y en reclamos por haber sido derivado al especialista equivocado.

---

## 🛡️ Flujo de Trabajo y Reglas del Repositorio

Para mantener la estabilidad del código y asegurar un desarrollo ordenado, este equipo sigue un flujo de trabajo basado en Ramas (Branching Workflow).

Dado que las limitaciones de la plataforma en repositorios privados no nos permiten bloquear las ramas técnicamente, **este documento actúa como un acuerdo obligatorio** para todos los miembros del equipo.

### 🚫 Regla de Oro: Protección de la rama `main`
**Queda estrictamente prohibido hacer un `git push` directo a la rama `main`.**
La rama `main` debe reflejar únicamente el código que está 100% probado, estable y listo para producción.

### 🔄 Flujo de Integración
Todo el desarrollo debe seguir el siguiente proceso:

1. **La rama base de desarrollo es `develop`:** Todo el código nuevo, características y correcciones se integran primero aquí.
2. **Creación de ramas:** Si vas a trabajar en una nueva tarea, crea una rama a partir de `develop` (ejemplo: `feature/nueva-vista` o `fix/error-login`).
3. **Pull Requests (PR):** Una vez termines tu tarea, abre un Pull Request hacia `develop`. Pide a un compañero que revise tu código antes de hacer el merge.
4. **Merge a `main`:** Solo se hará un merge de `develop` a `main` cuando el equipo acuerde que hay una versión estable y lista para ser entregada o desplegada.

El respeto a este flujo es responsabilidad de todos para evitar romper el entorno de trabajo del resto del equipo y prevenir conflictos graves de código.

---

## Modelo de dominio

El sistema gira alrededor de **Usuario** (con un **Rol**: `PACIENTE`, `MEDICO` o
`ADMIN`) y de **Medico**, que extiende los datos profesionales (especialidad,
sedes, horarios, tarifas, formación) sobre un usuario con rol médico.

| Entidad | Para qué sirve |
|---|---|
| `Usuario` / `Rol` | Autenticación y autorización (login, roles) |
| `Medico` | Perfil profesional: especialidad, sedes, horarios, tarifas, fotos |
| `Especialidad` / `Establecimiento` / `Distrito` | Catálogo para buscar y filtrar médicos |
| `Cita` | Reserva de un paciente con un médico (estado, modalidad, fecha) |
| `Valoracion` | Reseña de un paciente sobre una cita atendida |
| `Consulta` | Mensaje del formulario público de contacto |
| `Conversacion` / `MensajeChat` | Mensajería en tiempo real paciente-médico (WebSocket/STOMP) |
| `SolicitudVerificacion` | Solicitud de un médico nuevo, pendiente de aprobación por un admin |

---

## Integrantes y roles

| Integrante | Rol | Módulo principal |
|---|---|---|
| André Ramírez | Owner del repositorio, integración | Controllers, services, merges |
| Nelson Alva Zegarra | Landing page, buscador por síntomas | `feature/pagina-principal`, `feature/sugerencia-sintomas` |
| J. Alvarez F. | Versión final: login, citas, mensajería, panel admin | `feature/version-final` |
| Raúl Rosas | Lógica de recomendación | `feature/nucleo-recommendations` |
| Jhonatan Alva Castillo | Perfil del doctor y reserva de citas | `feature/perfil-doctores` |

Aporte por integrante según `git shortlog -s -n --all` (algunas personas comitean
desde más de un usuario/correo, por eso la suma no es exacta):

```
32  J. Alvarez F.      (DAlvarezF / JAlvarezF / ALvarezF)
31  Nelson Alva Zegarra (NAlvaZ / Zean2040)
30  André Ramírez       (ARamirezA / Oxand)
 7  Raúl Rosas          (ERosasR / Edgar Rosas)
 5  Jhonatan Alva Castillo
```

---

## Modelo de ramas

`main` es la rama estable y protegida. `develop` es la rama de integración.
Nadie trabaja directamente sobre `main`: cada funcionalidad se desarrolla en una
rama `feature/...` y se integra a `develop` mediante un pull request.

```
main        ──●──────────────────────────────────────────────────────────●────────►  estable
               \                                                        /
develop         ●──●────●────●────●────●────●────●────●────●──●────●───●──────────►  integración
                 \    \    \    \    \    \    \    \  /         \   /
feature/…         ●────●────●────●────●────●────●────●           ●─●              una por funcionalidad
                landing  controller  perfil  núcleo  errores  crud  síntomas  version-final
```

Ciclo que sigue cada funcionalidad:

```bash
git checkout develop
git pull origin develop
git checkout -b feature/nombre-funcionalidad
# ... programar, git add, git commit ...
git push -u origin feature/nombre-funcionalidad
# integrar cuando la funcionalidad está terminada (via Pull Request en GitHub)
```

Ramas utilizadas:

| Rama | Funcionalidad |
|---|---|
| `feature/pagina-principal` | Landing page y navegación |
| `feature/controller` | Controller y services de médicos |
| `feature/perfil-doctores` | Perfil del doctor y reserva de citas |
| `feature/nucleo-recommendations` | Núcleo de recomendación |
| `feature/recomendacion-doctores` | Manejo de errores, CORS y OpenAPI |
| `feature/crud-doctores` | CRUD de doctores y correcciones para Spring Boot 4 |
| `feature/sugerencia-sintomas` | Buscador por síntomas y maniquí interactivo en el hero |
| `feature/version-final` | Login, roles, citas, mensajería, panel admin y MySQL (versión final) |

---

## Fusiones y conflictos resueltos

Se realizaron varias fusiones hacia `develop` (una por cada pull request más
los merges de integración). Dos conflictos reales quedaron documentados:

### 1. Título de la landing page

`feature/pagina-principal` y `feature/controller` modificaron la misma línea
del `<title>` a partir de la misma base:

```
<<<<<<< HEAD
<title>MediCerca — Encuentra a tu doctor ideal</title>
=======
<title>MediCerca — Encuentra al doctores indicados</title>
>>>>>>> origin/feature/controller
```

Se conservó la versión de `feature/pagina-principal` ("Encuentra a tu doctor
ideal") por ser más natural y estar libre de errores gramaticales. Commit de
resolución: `c968490` (verificable con `git show c968490:frontend/index.html`).

### 2. Integración de `feature/version-final` (PR #14)

`feature/version-final` se construyó desde el commit inicial del proyecto, en
paralelo a `develop`, reconstruyendo todo el sistema con su propia arquitectura
(entidad `Medico` en vez de `Doctor`, vistas Thymeleaf en vez de HTML estático,
login con roles, citas, mensajería, panel admin y MySQL). Al fusionarla:

- Se descartó el stack REST antiguo (`Doctor`/`domain`, el motor local de
  sugerencia por síntomas, la landing estática en `frontend/`) porque la
  versión final ya cubre esas funciones con su propia implementación.
- Se eliminó un gitlink roto que había quedado comiteado por error en `develop`.
- Se reemplazaron contraseñas reales que estaban en texto plano en
  `application.properties` (MySQL, admin, SMTP) por variables de entorno.

Commit de resolución: `92d9bb8` (`git show 92d9bb8` tiene el detalle completo),
fusionado a `develop` vía el PR #14.

---

## Cómo ejecutar

Requisitos: **Java 17**, **Maven**, y **MySQL** corriendo localmente (puerto
`3306`, base de datos `medicerca`).

```bash
git clone https://github.com/Oxand3003/Doctor-Recommendation-System.git
cd Doctor-Recommendation-System
git checkout develop

# crear la base de datos (una sola vez)
mysql -u root -p -e "CREATE DATABASE medicerca CHARACTER SET utf8mb4;"
```

La configuración de conexión vive en `src/main/resources/application.properties`.
Las credenciales **no se guardan en el repositorio**: se definen como variables
de entorno antes de arrancar (o en `config/mail.local.properties`, que está en
`.gitignore`, para el correo).

| Variable | Para qué | Si no se define |
|---|---|---|
| `DB_USERNAME` | Usuario de MySQL | usa `root` |
| `DB_PASSWORD` | Contraseña de MySQL | vacío |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Cuenta de administrador inicial | vacío (pedir al equipo) |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Envío de correos (notificaciones, códigos) | vacío (deshabilita el correo) |
| `GROQ_API_KEY` | Asistente de orientación por IA (síntoma → especialidad) | vacío (deshabilita el asistente) |

```bash
# arrancar la app (Hibernate crea/actualiza las tablas automáticamente)
./mvnw spring-boot:run
```

La app queda en `http://localhost:8080`. Al arrancar se crean automáticamente
los roles, especialidades, distritos y el usuario administrador
(`medicerca.demo-data=true` por defecto también agrega médicos, citas y
valoraciones de ejemplo). Si se quieren más médicos de prueba, se puede correr
además `sql/cargar-medicos-demo.sql` directamente contra la base de datos.

> **Nota:** si `./mvnw` falla, usar `mvn` del sistema. El wrapper necesita
> `.mvn/wrapper/maven-wrapper.jar`, que está en el `.gitignore`.

---

## Roles y rutas principales

| Rol | Puede hacer |
|---|---|
| Visitante | Ver la página principal, buscar médicos (`/`, `/doctor/**`), enviar una consulta (`/consultas`) |
| Paciente | Todo lo anterior + reservar y gestionar citas (`/appointments/**`), mensajería con su médico, chat de orientación por IA |
| Médico | Gestionar su perfil, horarios, sedes, tarifas, reseñas y citas recibidas (`/medico/**`) |
| Administrador | Aprobar/rechazar solicitudes de médicos, gestionar cuentas y consultas (`/admin/**`) |

Rutas públicas (sin login): `/`, `/login`, `/register/**`, `/doctor/**`,
`/especialidades/**`, `/consultas`. El resto requiere sesión iniciada, y
`/admin/**` exige además el rol `ADMIN`.

Documentación interactiva de la API REST: `http://localhost:8080/swagger-ui/index.html`
Consola de base de datos H2 (solo si se cambia el datasource a H2 para pruebas rápidas): `http://localhost:8080/h2-console`

Para ver los tests automatizados: `./mvnw test`.

---

## Estructura del proyecto

```
src/main/java/utp/edu/pe/recomendaciones/
├── config/       Seguridad, WebSocket, datos iniciales, mensajería
├── Controller/   Controladores web (Thymeleaf) y de API
├── model/        Entidades JPA (Usuario, Medico, Cita, Consulta, ...)
├── model/enums/  Estados y modalidades (EstadoCita, Modalidad, ...)
├── dto/          Objetos de entrada y salida de cada caso de uso
├── exception/    Excepciones de negocio
├── repository/   Acceso a datos
└── service/      Lógica de negocio (citas, credenciales, correo, IA, ...)

src/main/resources/
├── templates/    Vistas Thymeleaf (home, doctor, medico, admin, fragments)
├── static/       CSS, JS e imágenes
└── application*.properties

sql/              Scripts SQL auxiliares (carga de médicos de demostración)
uploads/          Fotos de perfil subidas por los médicos (no versionado)
```
