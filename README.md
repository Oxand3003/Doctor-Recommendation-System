# MediCerca — Recomendación de citas médicas

Módulo que ayuda a un paciente a encontrar al especialista adecuado y reservar
una cita, en lugar de elegir a ciegas.

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

## Entidad núcleo

**Doctor** — es la entidad sobre la que se construye el CRUD completo:

| Campo | Tipo | Notas |
|---|---|---|
| `id` | Long | autogenerado |
| `nombres` | String | obligatorio |
| `apellidos` | String | obligatorio |
| `cmp` | String | obligatorio y único (colegiatura) |
| `rating` | Double | 0.0 a 5.0 |
| `aniosExperiencia` | Integer | 0 a 80 |
| `disponible` | Boolean | indica si acepta reservas; el alta profesional queda desactivada hasta aprobación |
| `especialidad` | Especialidad | relación obligatoria |
| `establecimiento` | Establecimiento | relación opcional |

Los perfiles creados desde el catálogo de demostración aparecen disponibles.
Los registros asociados a una cuenta médica quedan pendientes y no se publican
hasta que el administrador los aprueba.

Entidades de apoyo: **Especialidad** y **Establecimiento**.

---

## Integrantes y roles

| Integrante | Rol | Módulo |
|---|---|---|
| André Ramírez | Owner del repositorio, integración | Controller, services, merges |
| Nelson Alva Zegarra | Landing page | `feature/pagina-principal` |
| J. Alvarez F. | Manejo de errores y documentación | `feature/recomendacion-doctores` |
| Raúl Rosas | Lógica de recomendación | `feature/nucleo-recommendations` |
| Jhonatan Alva Castillo | Perfil del doctor y reserva de citas | `feature/perfil-doctores` |

Aporte por integrante según `git shortlog -s -n --all`:

```
11  André Ramírez
11  Nelson Alva Zegarra
 7  J. Alvarez F.
 6  Raúl Rosas
 5  Jhonatan Alva Castillo
```

---

## Modelo de ramas

`main` es la rama estable y protegida. `develop` es la rama de integración.
Nadie trabaja directamente sobre `main`: cada funcionalidad se desarrolla en una
rama `feature/...` y se integra a `develop` mediante un pull request.

```
main        ──●────────────────────────────────────────────●────────►  estable
               \                                          /
develop         ●──●────●────●────●────●────●────●────●──●──────────►  integración
                 \    \    \    \    \    \    \    \  /
feature/…         ●────●────●────●────●────●────●────●              una por funcionalidad
                landing  controller  perfil  núcleo  errores  crud
```

Ciclo que sigue cada funcionalidad:

```bash
git checkout develop
git pull origin develop
git checkout -b feature/nombre-funcionalidad
# ... programar, git add, git commit ...
git push -u origin feature/nombre-funcionalidad
# integrar cuando la funcionalidad está terminada
git checkout develop
git merge feature/nombre-funcionalidad
git push origin develop
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

---

## Fusión y conflicto resuelto

Se realizaron varias fusiones hacia `develop` (una por cada pull request más
los merges de integración). El conflicto real ocurrió en el `<title>` de
`frontend/index.html`, cuando `feature/pagina-principal` y `feature/controller`
modificaron la misma línea a partir de la misma base:

```
<<<<<<< HEAD
<title>MediCerca — Encuentra a tu doctor ideal</title>
=======
<title>MediCerca — Encuentra al doctores indicados</title>
>>>>>>> origin/feature/controller
```

Se conservó la versión de `feature/pagina-principal` ("Encuentra a tu doctor
ideal") por ser más natural y estar libre de errores gramaticales. Commit de
resolución: `c968490` — *merge: resolver conflicto en titulo de landing page*
(verificable con `git show c968490:frontend/index.html`).

---

## Cómo ejecutar

Requisitos: **Java 17 o superior** y **Maven**.

```bash
git clone https://github.com/Oxand3003/Doctor-Recommendation-System.git
cd Doctor-Recommendation-System
git checkout develop

# crear el esquema en MySQL (solicita la contraseña de MySQL)
mysql -u root -p < database/medicerca_mysql.sql

# levantar la aplicación
MYSQL_USER=root MYSQL_PASSWORD='tu contraseña' ./mvnw spring-boot:run
```

El SQL crea el esquema `medicerca` en MySQL con `utf8mb4`. La aplicación usa MySQL
en `localhost:3306`, base `medicerca`, usuario `root` y la contraseña que indiques
en `MYSQL_PASSWORD`. Si usas otro usuario, URL o puerto, configura
`MYSQL_USER`, `MYSQL_PASSWORD` y `MEDICERCA_DB_URL`.

La aplicación sirve la API y el frontend en `http://localhost:8080`. Al primer
inicio agrega las especialidades y establecimientos de ejemplo y crea un
administrador inicial. El catálogo de doctores de demostración también se carga
si aún no hay especialidades registradas.

El administrador inicial es `admin@medicerca.local` con contraseña
`Admin123!`. Puedes cambiar esos valores con las variables `ADMIN_EMAIL` y
`ADMIN_PASSWORD` antes de iniciar la aplicación.

Para conectar MySQL Workbench u otro cliente:

| Campo | Valor |
|---|---|
| Host | `localhost` |
| Puerto | `3306` |
| Base de datos | `medicerca` |
| Usuario | `root` o el valor de `MYSQL_USER` |
| Contraseña | tu contraseña de MySQL o el valor de `MYSQL_PASSWORD` |

La interfaz principal está en `http://localhost:8080/`. Desde ahí puedes buscar
especialistas, abrir un perfil y registrar una cita; el horario y la reserva se
guardan en la base de datos.

Desde `http://localhost:8080/auth.html` se puede iniciar sesión o crear una
cuenta. Los pacientes quedan activos al registrarse. Los médicos envían su CMP,
RNE si corresponde, formación profesional y un enlace de sustento; el administrador
debe revisar y aprobar la solicitud para habilitar el perfil y el acceso.

> **Nota:** si `./mvnw` falla, usar `mvn` del sistema. El wrapper necesita
> `.mvn/wrapper/maven-wrapper.jar`, que está en el `.gitignore`.

---

## Cuentas y permisos

| Rol | Cómo se obtiene | Permisos principales |
|---|---|---|
| Cliente | Registro inmediato desde `auth.html` | Buscar médicos, reservar y consultar sus citas |
| Médico | Solicitud con CMP, formación y enlace de sustento | Acceso al perfil y a sus citas después de aprobación administrativa |
| Administrador | Cuenta inicial creada al arrancar | Revisar solicitudes, aprobar/rechazar médicos y administrar el catálogo de médicos |

Los registros médicos quedan `PENDIENTE`: no pueden iniciar sesión ni aparecer en
las recomendaciones hasta que un administrador revise el CMP y abra el enlace
de sustento. En esta versión el documento se entrega como URL compartida; no se
suben archivos al servidor.

## Endpoints principales

Base: `http://localhost:8080/api/v1`

| Método | Ruta | Acceso |
|---|---|---|
| POST | `/auth/register/client` | Público; crea cliente activo |
| POST | `/auth/register/doctor` | Público; crea solicitud médica pendiente |
| POST | `/auth/login` | Público; inicia sesión |
| GET | `/auth/me` | Cuenta autenticada |
| POST | `/auth/logout` | Cuenta autenticada |
| GET | `/doctors`, `/doctors/{id}`, `/doctors/recomendar` | Público; médicos disponibles |
| POST/PUT/DELETE | `/doctors/...` | Administrador |
| GET | `/catalog/specialties`, `/catalog/establishments` | Público; formularios |
| GET | `/admin/doctor-applications` | Administrador |
| POST | `/admin/doctor-applications/{id}/approve` o `/reject` | Administrador |
| POST | `/appointments` | Cliente; reserva un horario disponible |
| GET | `/appointments/availability?doctorId=&fecha=` | Público; horas ocupadas |
| GET | `/appointments/{id}` | Cliente dueño de la cita, médico asignado o administrador |
| GET | `/client/me/appointments` | Cliente; solo sus citas |
| GET | `/doctor/me/appointments` | Médico aprobado; citas asignadas |

Las solicitudes `POST`, `PUT` y `DELETE` usan sesión y protección CSRF. La
interfaz obtiene el token automáticamente. Documentación interactiva:
`http://localhost:8080/swagger-ui/index.html`.

Una vez dentro de `http://localhost:8080`, el usuario puede registrarse, iniciar
sesión, mantener los horarios existentes y reservar una cita en un horario libre.
La identidad del paciente se toma de su cuenta, para evitar que se reserven citas
con datos de otra persona.

### Fórmula de recomendación

```
puntaje = (rating / 5 * 0.70 + min(aniosExperiencia, 20) / 20 * 0.30) * 100
```

El rating pesa 70 % y la experiencia 30 %, con un tope de 20 años para que una
trayectoria muy larga no aplaste al resto. Ambos pesos y el tope se definieron
para priorizar la satisfacción del paciente sin ignorar la experiencia clínica.

---

## Estructura del proyecto

```
src/main/java/utp/edu/pe/recomendaciones/
├── config/       Seguridad, carga inicial y metadatos de OpenAPI
├── controller/   Endpoints REST
├── domain/       Entidades JPA (usuarios, médicos, citas y catálogos)
├── dto/          Objetos de entrada y salida
├── exception/    Manejo global de errores
├── repository/   Acceso a datos
├── security/     Carga de cuentas y roles para Spring Security
└── service/      Lógica de negocio
src/main/resources/static/  Landing, buscador, perfiles, acceso y paneles de cuenta
database/                   Esquema SQL para MySQL
