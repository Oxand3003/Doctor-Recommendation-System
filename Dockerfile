# Imagen de MediCerca. La usan Render (despliegue) y GitHub Actions (CI: se construye y se prueba en cada PR).

# Etapa de build: compila el jar con Maven.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Primero solo el pom: si no cambian las dependencias, Docker reutiliza esta capa y el build es más rápido.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src/ src/
# Las pruebas ya corren en GitHub Actions antes de construir la imagen.
RUN ./mvnw -q -B clean package -DskipTests

# Etapa final: solo el jar y el JRE, sin Maven ni el código fuente.
FROM eclipse-temurin:17-jre
WORKDIR /app

# La app no corre como root: si alguien lograra ejecutar código dentro del contenedor, no tendría control total.
RUN groupadd --system medicerca && useradd --system --gid medicerca --home-dir /app medicerca \
    && mkdir -p /app/uploads && chown -R medicerca:medicerca /app

# Spring Boot deja un único jar ejecutable en target/ (el original queda como *.jar.original).
COPY --from=build --chown=medicerca:medicerca /app/target/*.jar app.jar

USER medicerca

# Render asigna el puerto real via la variable de entorno PORT.
EXPOSE 8080

# Java 17 (a diferencia de 18+, ver JEP 400) usa el locale del sistema para decidir
# la codificacion por defecto de archivos/recursos. Este contenedor no trae un locale
# UTF-8, asi que sin esto los textos con tildes del classpath (ej. application.properties)
# se leen mal en tiempo de ejecucion.
# MaxRAMPercentage: Java usa hasta el 75 % de la memoria del contenedor (por defecto solo el 25 %).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8", "-jar", "app.jar"]
