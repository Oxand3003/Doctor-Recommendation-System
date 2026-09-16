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
| `disponible` | Boolean | por defecto `true` |
| `especialidad` | Especialidad | relación obligatoria |
| `establecimiento` | Establecimiento | relación opcional |

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

Se realizaron **6 fusiones hacia `develop`** (una por cada pull request más el
merge de integración). El conflicto real ocurrió en el `<title>` de
`frontend/index.html`, cuando dos ramas modificaron la misma línea:

```
<<<<<<< HEAD
<title>MediCerca — Encuentra al doctor indicado mediante este sitio web</title>
=======
<title>MediCerca — Encuentra a tu doctor real ideal</title>
>>>>>>> feature/pagina-principal
```

Se conservó la versión de `feature/pagina-principal` por ser la más corta y
legible, y porque describe mejor el propósito del sitio. Commit de resolución:
`c968490` — *merge: resolver conflicto en titulo de landing page*.

Las capturas del antes y del después deben guardarse en `docs/evidencias/`.

---

## Cómo ejecutar

Requisitos: **Java 17 o superior** y **Maven**.

```bash
git clone https://github.com/Oxand3003/Doctor-Recommendation-System.git
cd Doctor-Recommendation-System
git checkout develop

# levantar la API
mvn spring-boot:run
```

La API queda en `http://localhost:8080`. La base de datos es H2 en memoria y se
crea con datos de ejemplo en cada arranque (`src/main/resources/data.sql`).

Para abrir el frontend, servir la carpeta `frontend/` con cualquier servidor
estático y entrar a `index.html`.

> **Nota:** si `./mvnw` falla, usar `mvn` del sistema. El wrapper necesita
> `.mvn/wrapper/maven-wrapper.jar`, que está en el `.gitignore`.

---

## Endpoints

Base: `http://localhost:8080/api/v1/doctors`

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/` | Lista todos los médicos |
| GET | `/{id}` | Obtiene un médico por id |
| POST | `/` | Registra un médico nuevo |
| PUT | `/{id}` | Actualiza un médico |
| DELETE | `/{id}` | Elimina un médico |
| GET | `/recomendar?especialidad=&limite=` | Recomienda médicos por especialidad |

Documentación interactiva: `http://localhost:8080/swagger-ui/index.html`
Consola de base de datos: `http://localhost:8080/h2-console`

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
├── config/       CORS y metadatos de OpenAPI
├── controller/   Endpoints REST
├── domain/       Entidades JPA (Doctor, Especialidad, Establecimiento)
├── dto/          Objetos de entrada y salida
├── exception/    Manejo global de errores
├── repository/   Acceso a datos
└── service/      Lógica de negocio
frontend/         Landing page, buscador y perfil del doctor
