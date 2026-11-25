# Etapa 1: Compilacion del backend
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY . .

# Compatibilidad con archivos provenientes de Windows
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Generar el JAR ejecutable
RUN ./gradlew --no-daemon bootJar

# Seleccionar el JAR de Spring Boot
RUN cp build/libs/appresso*-SNAPSHOT.jar /app/app.jar

# Etapa 2: Ejecucion del backend
FROM eclipse-temurin:21-jre

WORKDIR /app

# Ejecutar con un usuario sin privilegios
RUN groupadd --system appresso && \
    useradd --system --gid appresso appresso

COPY --from=build --chown=appresso:appresso /app/app.jar app.jar

USER appresso

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]