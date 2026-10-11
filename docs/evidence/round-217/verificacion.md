# Ronda 217 - el español del correo de reserva queda fijado por pruebas

## Parte de una falsa alarma, y conviene dejarlo escrito

Durante la revisión pareció que el correo de confirmación salía con el texto corrupto
(`confirmaci?n`, `C?digo`). **Era falso**, y la causa fue doble:

1. La consola de PowerShell (cp1252) muestra los acentos como `?`. Leyendo el fichero con
   Python y mirando los bytes, el fuente está en UTF-8 correcto: `confirmaci\xc3\xb3n`.
2. Mi propio experimento de simular la codificación rota dejó una **clase compilada vieja**
   en `target/`. Maven no la recompiló al restaurar el fuente, así que el test seguía
   viendo la versión rota. Con `mvn clean` desapareció.

Lección operativa: ante un síntoma de codificación, comprobar los bytes reales y hacer
construcción limpia antes de concluir nada.

## El hueco real que sí había

Ninguna prueba fijaba el español del correo. Una codificación mal cerrada habría pasado
desapercibida: el contenido del mensaje no estaba asserted en ningún sitio.

Se añadieron tres pruebas (`CorreoConfirmacionEspanolTest`):

- `correoEnEspanolCorrecto`: exige las palabras acentuadas exactas del mensaje
  (`solicitud`, `confirmación`, `Código`, `Huéspedes`, `código`) y que no aparezca
  el carácter de reemplazo U+FFFD.
- `fuenteEnUtf8`: el fichero se lee como UTF-8 y contiene esas palabras.
- `asuntoEnEspanol`: el asunto no sale vacío ni con símbolos de reemplazo.

## Comprobación de que las pruebas sirven

No vale con escribirlas: se comprobó que fallan cuando deben. Rompiendo la codificación a
propósito (`confirmación` -> texto sin tilde), el resultado fue:

    Tests run: 3, Failures: 2

Restaurado el fuente y con construcción limpia: `Tests run: 3, Failures: 0`.

## Estado del correo

Verificado byte a byte sobre el archivo que Java escribe en disco (no la consola):

    Estado: Pendiente de confirmaci\xc3\xb3n.
      C\xc3\xb3digo: H-DBG
      Hu\xc3\xa9spedes: 2
    Puedes consultar o cambiar tus fechas con el c\xc3\xb3digo y tu correo:

Español correcto, UTF-8 válido, sin U+FFFD.

## Pruebas

Backend: 477 pruebas, 0 fallos, 0 errores, 1 omitida (eran 474 antes de esta ronda).
