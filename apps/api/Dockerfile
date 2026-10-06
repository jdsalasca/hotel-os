# Imagen mantenida con Java 25 (LTS) y solo el JRE: el backend no compila en runtime.
FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

# El JAR se compila con el Maven Wrapper del repositorio, fuera de la imagen: el toolchain
# de construcción no tiene por qué viajar a producción.
COPY target/hotel-api-*.jar app.jar

# curl solo para el healthcheck; wget viene en la imagen base.
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/*

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