# Imagen del backend. Compila dentro del contenedor y ejecuta solo el JRE.
#
# Antes el JAR se copiaba desde `target/`, lo que obligaba a compilar en el host antes de
# `docker compose up --build`. Passó: un despliegue "correcto"levantó el jar de la ronda anterior
# porque `target/` estaba viejo, y los endpoints nuevos devolvían 403 con `Allow: POST`. Un build
# limpio del repositorio no tenía jar, así que ni siquiera arrancaba.

# --- Compilación ---
FROM maven:3.9-eclipse-temurin-25 AS construccion
WORKDIR /build

# Las dependencias se resuelven en una capa aparte: cambiar el código no vuelve a bajarlas.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# --- Ejecución ---
FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

# curl solo para el healthcheck; wget viene en la imagen base.
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/*

COPY --from=construccion /build/target/hotel-api-*.jar app.jar

# El proceso corre como root dentro del contenedor. Es la forma habitual en contenedores de un
# solo proceso, y evita el fallo real que se vio al intentar bajar el privilegio: el volumen
# nombrado nace con propietario root y, si el proceso corre como otro usuario, SQLite no puede
# abrir el archivo (SQLITE_CANTOPEN). El aislamiento lo da el contenedor, no el usuario del proceso.
#
# Lo que SÍ se hace: el proceso no tiene shell interactiva, no monta el socket de Docker y solo
# ve los volúmenes declarados en compose. El disco persistente del host no se ve desde dentro.
ENV JDBC_URL=jdbc:sqlite:/data/hotel.sqlite3

EXPOSE 8080

# Healthcheck sobre el esquema real de la base, no solo "el proceso responde".
HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=30s \
  CMD curl -fsS http://localhost:8080/api/health | grep -q '"estado":"ok"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]