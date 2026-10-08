# Ronda 124 - El hotel vacío lo dice (backend: /api/hotel/venta)

(Nota de numeración: se trabajó como 123 sin saber que el colega ya había
tomado ese número para "Etapa F". Su evidencia sigue intacta en
`docs/evidence/round-123/`; esta es la 124, siguiente número libre.)

## Hallazgo en prod
La web muestra todo octubre "Lleno". Copia de solo lectura de la base de prod:
`rooms=0, room_types=0, rate_plans=0, rates=0, reservations=0`. No es un bug de
cálculo: el hotel no ha cargado nada que vender, y la web miente "lleno" en vez
de decir "vacío". (El frontend honesto viene en el siguiente commit de la ronda.)

## Cambio (backend)
- `GET /api/hotel/venta` → `{"a_la_venta": bool}` (público, en la lista de
  SecurityConfig). Verdadero solo con ≥1 habitación ACTIVA y ≥1 tarifa.
- `HotelConfigRepository.hayVenta()` + `HotelConfigService.aLaVenta()`.

## TDD rojo-verde
- `HotelVentaTest` nuevo: 3/3 en rojo (404 sin ruta); en verde 3/3. En el camino
  cazó contaminación entre tests (base compartida): `@BeforeEach` que limpia
  `rates/reservations/rooms` lo deja determinista sin importar el orden.
