# Dockerfile multi-stage del backend Quarkus (Cuentas).
# Compila el JAR dentro de la imagen para que el entorno completo arranque con un solo comando,
# sin depender de artefactos pre-construidos en el host.

# --- Etapa de build: compila el proyecto con Maven y JDK 25 ---
# Se usa la imagen oficial de Maven con Temurin 25 (trae Maven instalado), en lugar del
# Maven Wrapper, para evitar la descarga/validación del wrapper dentro del contenedor.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Copiar primero el pom para aprovechar la caché de dependencias.
COPY pom.xml ./
RUN mvn -B -ntp -q dependency:go-offline || true

# Copiar el código fuente y empaquetar sin ejecutar pruebas (requieren base de datos externa).
COPY src/ src/
RUN mvn -B -ntp clean package -DskipTests

# --- Etapa de runtime: imagen ligera con solo el JRE y el quarkus-app ---
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /deployments

# Estructura por capas de Quarkus (lib primero para reutilizar caché).
COPY --from=build /build/target/quarkus-app/lib/ ./lib/
COPY --from=build /build/target/quarkus-app/*.jar ./
COPY --from=build /build/target/quarkus-app/app/ ./app/
COPY --from=build /build/target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8080
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /deployments/quarkus-run.jar"]
