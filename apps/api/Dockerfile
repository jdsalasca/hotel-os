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
# Alpine en vez de jammy: mismo JRE, ~150 MB menos en disco. Comprobado además que trae `sh`,
# `wget` y `java`.
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# curl solo para el healthcheck; wget viene en la imagen base. En Alpine se instala con apk.
# Los tres compose (dev, producción y túnel) usan curl en su healthcheck.
#
# El usuario se crea aquí y `/data` nace con su propietario. Docker copia la propiedad del
# directorio de la imagen al volumen nombrado la primera vez que lo monta, así que un volumen
# NUEVO ya queda escribible sin ningún paso extra. Antes esto no se podía hacer: `/data` solo
# existía en el volumen, que nacía de root, y el proceso acababa corriendo como root.
RUN apk add --no-cache curl \
  && addgroup -S hotel \
  && adduser -S -G hotel -h /app hotel \
  && mkdir -p /data \
  && chown hotel:hotel /data

COPY --from=construccion /build/target/hotel-api-*.jar app.jar

# Sin privilegios. Un servidor público no debería ejecutar el backend como uid 0.
#
# Para un volumen YA existente (creado por una imagen anterior, que corría como root) hay que
# cambiarle el propietario una vez, o SQLite no podrá abrir el archivo:
#   docker run --rm -v hotel-os_hotel-data:/data alpine chown -R hotel:hotel /data
# Ese volumen tendrá que llamarse `hotel` porque el usuario existe en la imagen; si no, con el
# uid numérico sirve igual. Ver docs/operations/despliegue-respaldos.md.
ENV JDBC_URL=jdbc:sqlite:/data/hotel.sqlite3

USER hotel

EXPOSE 8080

# Healthcheck sobre el esquema real de la base, no solo "el proceso responde".
HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=30s \
  CMD curl -fsS http://localhost:8080/api/health | grep -q '"estado":"ok"' || exit 1

# `sh -c` para que JAVA_OPTS llegue a la JVM: la forma exec anterior no expandía variables, así que
# el heap se dimensionaba solo (≈1/4 de la RAM del host: 900 MB en un mini PC de 3.7 GB). El `exec`
# interior deja la JVM como PID 1, así que `docker stop` sigue llegando por señales.
# JAVA_OPTS lo fija compose.tunnel.yaml; sin él, `:-` deja la JVM con su dimensionado por defecto.
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:-} -jar app.jar"]