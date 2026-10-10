# Selección de bloqueos desde el calendario Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir seleccionar noches libres consecutivas desde el calendario del panel y rechazar en el servidor cualquier bloqueo que se cruce con una reserva vigente o con otro bloqueo.

**Architecture:** El calendario del panel conserva su vista de ocupación, pero sus noches libres permiten elegir una habitación y un intervalo. La selección solo rellena el formulario existente; la confirmación sigue siendo explícita. `InventarioService` volverá a consultar el intervalo dentro de la transacción antes de insertar, usando intervalos semiabiertos `[desde, hasta)`.

**Tech Stack:** React, TypeScript, Vitest, Testing Library, SCSS; Java, Spring Boot, JDBC, SQLite, JUnit.

**Spec:** `docs/plan.md`, misión pendiente «Calendario del panel con arrastre para bloquear noches».

## Global Constraints

- No guardar el bloqueo al seleccionar celdas; solo al enviar el formulario actual.
- `desde` es la primera noche y `hasta` es el día de salida, excluido del conteo.
- Solo seleccionar noches con estado `LIBRE`; reservas y bloqueos vigentes se revalidan en el servidor.
- Mantener teclado, lector de pantalla, zona horaria local y estilos en SCSS.
- Integrar por cherry-pick a `develop`, ejecutar validaciones antes y después, subir `origin/develop` y retirar únicamente el worktree propio.

## Review Focus

- Intervalo de una noche: elegir una celda produce `hasta = desde + 1 día` y un conteo de una noche.
- Varias noches: el resumen excluye la fecha de salida; seleccionar el último día del mes puede salir al mes siguiente.
- Rango no contiguo por reserva/bloqueo: no completar la selección y anunciar el primer conflicto.
- Bloqueos globales: comprobar reservas y bloqueos de cualquier habitación.
- Respuesta tardía al cambiar de mes: no completar una selección con datos de otro mes.

---

### Task 1: Revalidar bloqueos en el servidor

**Files:**
- Modify: `apps/api/src/main/java/co/hotel/inventario/InventarioService.java`
- Modify: `apps/api/src/main/java/co/hotel/inventario/InventarioRepository.java`
- Create: `apps/api/src/main/java/co/hotel/inventario/ConflictoBloqueoException.java`
- Modify: `apps/api/src/main/java/co/hotel/inventario/InventarioAdminController.java`
- Test: `apps/api/src/test/java/co/hotel/inventario/InventarioServiceTest.java`
- Test: `apps/api/src/test/java/co/hotel/inventario/BloqueosControllerTest.java`

- [x] Escribir primero pruebas de servicio para: reserva vigente solapada, bloqueo solapado, intervalo adyacente permitido y bloqueo global que cruza una reserva de cualquier habitación.
- [x] Ejecutar esas pruebas y confirmar que fallan porque hoy se inserta el bloqueo sin revisar disponibilidad.
- [x] Dentro de `SqliteTransactionExecutor`, revalidar el intervalo con las consultas de disponibilidad y lanzar `ConflictoBloqueoException` antes de insertar.
- [x] Mapear la excepción a HTTP 409 con un mensaje que indique actualizar el calendario; probar que el API no deja fila de bloqueo.
- [x] Ejecutar las pruebas del servicio y del controlador: 58/58 aprobadas.

### Task 2: Seleccionar un intervalo libre en el calendario

**Files:**
- Modify: `apps/web/src/paginas/PaginaAdminInventario.tsx`
- Test: `apps/web/src/paginas/PaginaAdminInventario.test.tsx`
- Modify: `apps/web/src/styles/_componentes.scss`

- [x] Escribir primero una prueba que seleccione inicio y fin en la misma habitación y compruebe habitación, `desde`, `hasta`, resumen y cero envíos automáticos.
- [x] Ejecutar la prueba y confirmar que falla porque las celdas actuales no son seleccionables.
- [x] Convertir solo las noches libres en controles accesibles. Permitir arrastre con ratón y selección por inicio/fin mediante tacto y teclado.
- [x] Rellenar el formulario actual con intervalo semiabierto, mostrar noches y salida, y conservar la selección frente a errores del servidor hasta que el usuario la cambie.
- [x] Añadir estados SCSS reutilizando tokens y mixins existentes; probar que celdas ocupadas/bloqueadas no se puedan seleccionar.
- [x] Ejecutar suite frontend (169 pruebas), typecheck y build.

### Task 3: Verificación de flujo y cierre

**Files:**
- Update: `docs/plan.md`
- Create: `docs/evidence/round-199/verificacion.md`
- Create: `docs/evidence/round-199/escritorio.png`
- Create: `docs/evidence/round-199/movil.png`

- [x] Revisar visualmente escritorio y móvil con datos sintéticos; verificar mensaje de conflicto, conteo, fecha de salida y ausencia de desbordamiento.
- [x] Ejecutar suite backend, suite frontend, typecheck y build; guardar resultados y capturas en la evidencia.
- [x] Revisión independiente detectó fechas anteriores reutilizadas al iniciar una selección y ancla retenida tras un conflicto; ambos casos añadidos como pruebas rojas y corregidos.
- [x] Actualizar R199 como completada y mantener 3–5 misiones candidatas en `docs/plan.md`.
- [ ] Revisar diff, commit atómico en la rama del worktree, cherry-pick a `develop`, repetir verificaciones, push, comprobar SHA remoto y limpiar el worktree y la rama propios.
