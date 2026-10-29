# Etapa de build: compila el jar con Maven.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -q -B clean package -DskipTests

# Etapa final: solo el jar, sin herramientas de build.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/recomendacion_citas-0.0.1-SNAPSHOT.jar app.jar

# Render asigna el puerto real via la variable de entorno PORT.
EXPOSE 8080

# Java 17 (a diferencia de 18+, ver JEP 400) usa el locale del sistema para decidir
# la codificacion por defecto de archivos/recursos. Este contenedor no trae un locale
# UTF-8, asi que sin esto los textos con tildes del classpath (ej. application.properties)
# se leen mal en tiempo de ejecucion.
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8", "-jar", "app.jar"]
