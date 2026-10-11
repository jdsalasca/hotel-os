# Ronda 208 - Una ruta que no existe responde 404 de verdad

## El hueco
El nginx del contenedor de web servia la SPA con `try_files $uri $uri/ /index.html`. Eso es
necesario —sin el fallback, recargar en `/admin/reservas` daria 404— pero tambien hacia que
**cualquier** URL inventada respondiera 200 con el documento completo.

Medido antes del cambio, todas devolvian `200` y los mismos 1767 bytes:

```
/                  200  1767 bytes
/no-existe          200  1767 bytes
/admin/hotelaria   200  1767 bytes
/consultaXYZ       200  1767 bytes
```

Un buscador puede indexar paginas que no existen, y un monitor de salud ve "todo bien" donde
no hay nada. Es el P2 que la ronda 207 dejo explicitamente pendiente.

## La solucion
El fallback se concede solo a las rutas que la SPA declara de verdad; el resto cae en `=404`.

```nginx
location ~ ^/(reserva|consulta|mis-reservas|privacidad|terminos)$ {
  try_files $uri /index.html;
}
location ~ ^/admin$ { try_files $uri /index.html; }
location ~ ^/admin/(entrar|reservas|hoy|inventario|hotel|integraciones|indicadores|auditoria|lugares)$ {
  try_files $uri /index.html;
}
location / { try_files $uri $uri/ =404; }
```

Un intento intermedio con `^/admin(/|$)` era demasiado ancho: `/admin/hotelaria` seguia
recibiendo 200. Se sustituyo por la lista explicita de rutas del panel, igual que en R207 se
descarto la herencia por prefijo en los titulos. **Mismo error, mismo sitio: matcher de prefijo
donde la lista es cerrada.**

El trade-off es deliberado y esta escrito en el Dockerfile: si manana se anade una ruta en
`App.tsx` y no en el nginx, recargar en ella da 404. Es un fallo visible que avisa, mejor que
un 200 mentiroso que nadie nota.

## Verificacion (contenedores reales, 16 peticiones)
| Rutas reales | Estado |
|---|---|
| `/`, `/reserva`, `/consulta`, `/mis-reservas`, `/privacidad`, `/terminos` | 200 |
| `/admin`, `/admin/reservas`, `/admin/hotel`, `/admin/lugares`, `/admin/inventario` | 200 |

| Rutas inventadas | Estado |
|---|---|
| `/no-existe`, `/admin/hotelaria`, `/consultaXYZ`, `/adminx`, `/admin/inventario/1` | **404** |

**El riesgo real del cambio esta verificado**: la SPA sigue arrancando con carga directa en
`/admin/reservas` (`SPA arranca en /admin/reservas: true`), que es justo donde actua
`try_files` y donde un 404 habria roto la navegacion. 0 errores JS.

Captura: `01-admin-reservas-carga-directa.png`.

## Nota
`compose.ronda208.yaml` es andamiaje local de puertos y no va commiteado.
