# Ronda 230 — consulta privada del comprobante

**Fecha:** 2026-10-11

**Alcance:** impedir que el código y el correo de una reserva queden expuestos en la URL al consultar o recuperar un comprobante.

## Cambio

- La consulta pública usa POST /api/reservas/consulta y recibe código y correo en el cuerpo JSON.
- La ruta antigua GET fue retirada.
- Se mantiene el límite por IP, la respuesta genérica 404 para credenciales que no coinciden y la protección CSRF.
- Las respuestas 200, 404 y 429 de la consulta se marcan no-store.
- La página de consulta y la recuperación del comprobante tras recargar envían las credenciales en el cuerpo.
- Las pruebas de vinculación de reservas, seguridad y límite por IP usan la ruta actual.

## Verificación

- Pruebas API específicas de la ruta: 5/5; CSRF, resultado válido, respuesta 404 y ruta GET retirada cubiertos.
- Pruebas frontend dirigidas: 25/25.
- Suite frontend: 227/227 en 32 archivos; chequeo de tipos y compilación Vite aprobados.
- Verificación visual local en Chrome: escritorio 1440 × 1000 y móvil 390 × 844. El documento no desborda horizontalmente. No hubo excepciones JavaScript; se observaron 2 respuestas 401 de consultas de sesión al navegar sin autenticar, esperables en ese contexto.
- Suite completa del backend: 481 pruebas ejecutadas, 0 fallas y 0 errores; 1 omitida; BUILD SUCCESS. La primera pasada completa descubrió cuatro pruebas internas que aún llamaban el GET retirado o asumían el JSON antiguo; se actualizaron y la suite dirigida pasó 65/65 antes de la pasada completa final.

## Límite de seguridad

Esta ronda elimina código y correo de la URL, pero no refuerza por sí sola la autenticación del comprobante. El código actual tiene 32 bits de entropía y el límite por IP existente es una barrera básica, no una defensa suficiente contra intentos distribuidos. Antes de conceder acceso anónimo duradero (por ejemplo, al chat), debe diseñarse una verificación por correo y un token aleatorio, temporal, revocable y limitado a una reserva.
## Capturas

- consulta-escritorio.png
- consulta-movil.png