# Ronda 113 - Bloqueos que se solapan cuentan su unión, no su suma (Etapa D)

Fecha: 2026-10-08. Rama: `goal/round-113-bloqueos-union` (antes `goal/round-112-noches-bloqueadas-union`).

## Problema (reproducido, Etapa D)

`IndicadoresRepository.nochesBloqueadas()` sumaba intervalos en vez de contar su unión
por (habitación, noche). Ejemplo del encargo, verificado en SQLite aislado (test):

- Dos habitaciones activas, período de tres noches: seis noches totales.
- Habitación 101 bloqueada del día 1 al 3 y del 2 al 4. La unión ocupa tres noches.
- El código restaba cuatro y devolvía dos vendibles; deben ser tres.

El javadoc lo declaraba como "aproximación": global + individual solapados restaban
dos veces y los duplicados restaban N veces. La ocupación (f3_ocupacion) salía inflada.

## Cambio (TDD rojo-verde)

- Tests nuevos en `IndicadoresServiceTest$Operacion` (5):
  - `bloqueosSolapadosDeLaMismaHabitacionCuentanSuUnion` (el ejemplo: 57, no 56).
  - `bloqueoGlobalMasIndividualSolapadosCuentanSuUnion` (55, no 54).
  - `bloqueosDuplicadosCuentanUnaSolaVez` (27, no 24).
  - `bloqueoQueCruzaElPeriodoSoloRestaSuInterseccion` (29; guardia de recorte).
  - `bloqueoDeHabitacionFueraDeServicioNoResta` (30; guardia de inactivas).
- Rojo: 3 fallos exactos (`expected: <57> but was: <56>`, `55/54`, `27/24`); las 2
  guardias ya pasaban.
- Verde: `nochesBloqueadas()` agrupa intersecciones por habitación y fusiona
  intervalos (sin expandir día por día: un período amplio no revienta memoria).
  El global (`room_id` nulo) aporta su intersección a cada habitación activa; las
  inactivas ni cuentan ni restan. `Math.max(0, …)` queda solo como invariante
  defensivo documentado, no como el que cuadra las cuentas.

## Verificación (salida real)

- `IndicadoresServiceTest`: `Tests run: 38, Failures: 0, Errors: 0, Skipped: 0` + BUILD SUCCESS
  (33 preexistentes + 5 nuevos).
- Suite completa en esta ronda: `Tests run: 388, Failures: 0, Errors: 7, Skipped: 1`.
  Los 7 errores (6 `LoteTarifasControllerTest` + 1 `LugaresTest`) son colisión de dos
  builds concurrentes sobre el mismo `target/` (JVMs del colega arrancadas 13:39 en
  mitad de mi corrida 13:38-13:42: `NoClassDefFoundError NocheLoteReq`,
  `NoSuchBeanDefinitionException IntentoThrottle` con fuente consistente y
  compilación OK). Prueba: re-ejecución aislada posterior
  `LoteTarifasControllerTest,LugaresTest` → `Tests run: 8, Failures: 0, Errors: 0`.
  Y la corrida post-merge del colega (ronda 112) con este mismo código dio
  `388, 0 fallos, 0 errores, 1 omitido`. Nada que corregir en este cambio.
- Lección operativa: no correr `./mvnw.cmd test` a la vez en la misma copia; turnarse
  o usar `-Dmaven.repo.local`/`target` separados si hace falta paralelo real.

## Trabajo en equipo (sin pisar al colega)

- Sus 5 archivos con cambios (chat + `HiloMensajes`) quedaron intactos y fuera de mis
  commits en todo momento (`git add` solo con mis rutas). Su ronda 112 usa
  `docs/evidence/round-112/`; esta evidencia va en `round-113/` nuevo, sin tocar la suya.
- Coordinación previa en esta sesión: matriz E-F del explorador (E1/E2/E6/E7 correctos;
  huecos E3-E5, E8-E11 pendientes; F1/F5/F6 resueltos; F8 confirmado) y revisión de
  Etapa G del revisor (H1-H6 con pruebas negativas propuestas) quedan como insumo de
  las próximas rondas, sin editar sus archivos.
- Rama de capturas `goal/round-112-capturas-get` (del subagente, solo
  `tools/capturas/capturar.mjs`, `node --check` exit 0) ya existe en origin para
  integrar a `develop` en ventana tranquila.

## Archivos

- Tocados: `apps/api/src/main/java/co/hotel/indicadores/IndicadoresRepository.java`,
  `apps/api/src/test/java/co/hotel/indicadores/IndicadoresServiceTest.java`.
- Nuevos: este archivo.
- Plan: entrada de ronda 113 en `docs/plan.md`.

## Riesgos y limitaciones

- Sin cambio de UI: no requiere capturas.
- `f3_ocupacion` sigue nombrando reserva vigente (PENDIENTE+CONFIRMADA) hasta que
  exista recepción real; pendiente de Etapa H, no de esta ronda.
- Queda por integrar a `develop` + push cuando el colega no esté mergeando.
