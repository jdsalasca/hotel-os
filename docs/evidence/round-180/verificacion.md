# Ronda 180 - La reserva congela las condiciones acordadas

## El hueco
La ronda 175 muestra hora de entrada/salida y política de cancelación en el paso de
confirmación, leídas de `hotel_config`. Pero nada las guardaba en la reserva: si el hotel
cambiaba su política después de vender, quien ya reservó perdía el acuerdo que aceptó y el
comprobante pasaría a mostrar reglas que nunca vio. El precio ya se congelaba desde V4;
las condiciones, no.

## El cambio
- Migración `V20__condiciones_acordadas.sql`: tres columnas NULL en `reservations`
  (`hora_entrada`, `hora_salida`, `politica_cancelacion`). NULL en reservas antiguas: no
  había nada que congelar y no se inventa.
- `ReservaService.crear` lee `hotelConfig.publicos()` **dentro de la misma transacción**
  del alta y guarda las tres condiciones junto al precio. Cadenas vacías se guardan como
  NULL (`vacioANull`).
- `ComprobanteService` expone `horaEntrada`, `horaSalida` y `politicaCancelacion` leídas
  de la **reserva**, no de `hotel_config`: el comprobante dice lo acordado ese día.
- Integración con R173 (no-show) en el mismo servicio: el constructor unificado recibe
  `HotelConfigService` + `hoyHotel`; ambas reglas conviven sin duplicar dependencias.

## TDD rojo-verde
- `ReservaPublicaTest.laReservaCongelaLasCondiciones`: en rojo daba
  `expected "Gratis hasta 48 horas antes" but was null`; en verde congela la política
  vigente al vender y la conserva aunque `hotel_config` cambie después.
- `AdminReservasControllerTest.comprobanteCongelaLasCondicionesAcordadas`: el comprobante
  (público y panel) muestra `politicaCancelacion`/`horaEntrada`/`horaSalida` congelados
  aunque el hotel cambie su configuración después. Rojo demostrado retirando la
  exposición (`Tests run: 1, Failures: 1`); verde al restaurarla
  (`AdminReservasControllerTest`: 40/40). Usa el endpoint real
  `POST /api/admin/hotel-config` y restaura los valores en `finally`.
- DDL de `ReservaServiceTest` y `EscaladoSqliteTest` ampliados con `hotel_config` y las
  tres columnas: esos tests construyen su esquema a mano y el servicio nuevo lo exige.

## Verificación (real, worktree aislado sobre `origin/develop@26a0ddc`)
- Backend completo: `Tests run: 452, Failures: 0, Errors: 0, Skipped: 1`
  (`BUILD SUCCESS`; el omitido es `PermisosDeVolumenTest`, deshabilitado en Windows).
- Tras añadir la prueba del comprobante: `AdminReservasControllerTest` 40/40 en verde
  (cada clase usa su propia base temporal; la prueba restaura `hotel_config` en
  `finally`, así que no contamina al resto).
- Re-verificado tras integrar `origin/develop@4212164` (13 commits nuevos, incluido el
  catálogo centralizado de monedas): `Tests run: 453, Failures: 0, Errors: 0`,
  `BUILD SUCCESS`. Sin conflictos en el merge.
- Re-verificado tras integrar `origin/develop@a7d4451` (R188 amenities, R189 lugares,
  R190/R191 consulta): `Tests run: 455, Failures: 0, Errors: 0, Skipped: 1`,
  `BUILD SUCCESS`. El `@PutMapping("/api/admin/lugares/{id}")` de R189 se comprobó
  intacto en esta rama: el diff que lo borra en el checkout principal es artefacto de
  que ese HEAD va 25 commits atrasado, no una eliminación real.
- `git diff --check` limpio; `V20` va después de `V19__estado_no_presentada.sql` (R173).

## Nota de numeración
Esta ronda se llamó 176 mientras se escribía, pero el 176 ya es la ronda del coordinador
(saldo cero en reserva cerrada, `8025daa`). Se renumera a 180: no hay colisión de
migraciones (V19/V20) ni de evidencia (`round-180/` nuevo, `round-176/` intacto).
