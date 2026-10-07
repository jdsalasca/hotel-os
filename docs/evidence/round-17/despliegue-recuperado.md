# Ronda 18 (cierre) — Endurecimiento de despliegue sin commitear

Estos archivos llevaba una sesión sin commitear. Se recuperan, se verifican y se committean
aparte para no mezclarlos con la ronda de auditoría.

## Corrección real: la JVM nunca recibió JAVA_OPTS

```dockerfile
# antes
ENTRYPOINT ["java", "-jar", "app.jar"]
```

La forma exec no expande variables, así que `-Xmx`/`-Xms` nunca llegaban a la JVM y el heap se
dimensionaba solo: ≈1/4 de la RAM del host, unos 900 MB en un mini PC de 3.7 GB. Ahora:

```dockerfile
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:-} -jar app.jar"]
```

El `exec` interior deja la JVM como PID 1, así que `docker stop` sigue llegando por señales.
Verificado leyendo `/proc/1/cmdline` del contenedor: `java -jar app.jar`, con la JVM como PID 1 y
sin flags porque `JAVA_OPTS` no está definida en dev (comportamiento correcto: `:-` deja el
dimensionado por defecto).

## Imagen de la API: jammy → alpine

Mismo JRE, ~150 MB menos en disco. `apt-get` pasa a `apk add` para el `curl` del healthcheck, que
los tres compose necesitan. Verificado: la imagen corre como uid=0, así que el volumen de SQLite
sigue naciendo con propietario root y el comentario del Dockerfile sigue siendo cierto.

## gzip del bundle: 64,4% menos en cada carga

El `nginx.conf` de la imagen traía gzip comentado, así que el bundle de Vite bajaba íntegro.
Medido sobre `/assets/index-Bx8a4CeO.js` del contenedor real:

```text
Accept-Encoding: gzip     -> Content-Encoding: gzip,  99 166 bytes
Accept-Encoding: identity ->                           278 659 bytes   (64,4% menos)
```

No comprime imágenes ni fuentes, por eso `gzip_types` no incluye `image/*`.

## Caddy: una sola ruta, no dos

El nginx de la imagen web ya reenruta `/api/*` a `api:8080`. El `reverse_proxy /api/*` que tenía
el Caddy era redundante. Se deja un único `reverse_proxy web:80` y solo las cabeceras que nadie más
pone (`Permissions-Policy`, ocultar `Server`). Se añade `encode gzip` para el tráfico que sí pasa
por Caddy.

## Modo túnel: despliegue sin IP pública ni puertos 80/443

`compose.tunnel.yaml` + `.env.tunnel.example`. Sin servicio `proxy`: quien termina TLS y enruta es
`cloudflared`, fuera del compose. `web` publica un puerto local para que el túnel lo alcance.

Esto ataca el punto 1 de la lista de pendientes (dominio y correo) por el camino que no exige IP
pública ni correo ACME. **Lo verificado es que los tres compose validan** (`config --quiet` con
exit 0 para dev, túnel y producción) **y que las imágenes arrancan sanas con healthcheck en
verde. Un túnel real de Cloudflare no se ha probado: requiere una cuenta y un nombre de túnel.**