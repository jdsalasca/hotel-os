# Ronda 135 - Sin 500 con correos duplicados (Etapa G, H2)

Fecha: 2026-10-08. Rama: `goal/round-135-sin-500-duplicados`.

## Misión (brainstorming, vía acotada)

Opciones: (a) blindar H2 en las 3 superficies de sesión —bounded, crash real—;
(b) H6 rol (delgado + zona SecurityConfig activa del colega); (c) check-in
(fuera de ventana). Va (a): el 500 por duplicado se vio en vivo en la R115.

## Cambio

- Ningún cambio de producción en la ronda: la mitigación (`ORDER BY id LIMIT 1`
  en `idPorEmail`, R115) ya cubre las tres superficies. La ronda la blinda con
  tests que hoy no existían.
- Tests nuevos en `HuespedDuplicadoTest` (3, panel+Google con el mismo correo):
  `/api/yo` 200, `/api/mis-reservas` 200 y `POST /api/reservas` 201 enganchada a
  la cuenta más antigua.
- Rojo probado revirtiendo temporalmente la mitigación: 3×
  `IncorrectResultSizeDataAccessException: expected 1, actual 2` (el 500); con
  la mitigación restaurada, 3/3 en verde. Vitest no aplica (backend puro).

## Verificación real

- Rojo→verde: 3/3 en archivos nuevos (rojo `IncorrectResultSize... expected 1,
  actual 2` probado por reversión temporal de la mitigación).
- Suite backend: **424 tests, 419 verdes, 5 rojos ajenos, 1 omitido**. Los 5
  rojos son los tests de cambiar-huéspedes del colega (404 sin endpoint: su TDD
  en curso, intactos); lo mío (duplicado 3/3, manual, resto) todo verde.
- De paso, en el mismo árbol: colisión paralela en
  `AdminReservasControllerTest` (el colega inserta sus tests mientras yo corro):
  una llave de más cerraba la clase antes de tiempo y su inserción barrió el
  helper `admin()` compartido. Se restauró lo estructural (1 llave + helper
  verbatim, cero lógica) y compila.

## Trabajo en equipo

- Solo mis rutas en el commit (test + evidencia + plan). Lo del colega (sus
  tests de huéspedes, OAuth, hotel-vacio) intacto y fuera; la reparación
  estructural del archivo compartido no cambia su lógica.

## Archivos

- Nuevos: `test/.../huespedes/HuespedDuplicadoTest.java`, este archivo.
- Plan: entrada de ronda 135.

## Pendiente

- Identidad por (subject, issuer) (cura de fondo de H2); check-in/out; H6.
