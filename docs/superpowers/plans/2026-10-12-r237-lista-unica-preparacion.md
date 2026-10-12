# Lista de preparación del hotel: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** El panel de administración debe presentar una sola lista fiel de pasos pendientes para que el hotel tenga una web pública utilizable.

**Architecture:** La API calcula la preparación desde los datos guardados y entrega los pasos con su estado y ruta. El panel consume el componente compartido ListaPreparacion; elimina las comprobaciones paralelas que podían contradecir el resultado y añade el contacto del hotel como criterio del servidor.

**Tech Stack:** Java 25, Spring Boot, SQLite, React, TypeScript, Vitest y SCSS existente.

**Spec:** Alcance aprobado dentro del objetivo continuo de estabilización y usabilidad de HOTEL-OS.

## Global Constraints

- Mantener todos los estilos en SCSS existente; esta ronda no crea reglas visuales nuevas.
- No mostrar un aviso de que el hotel está listo si falla la carga del checklist.
- Considerar suficiente un correo o un teléfono de contacto.
- Conservar los archivos y capturas preexistentes; los nuevos artefactos usan nombres nuevos.
- Todo commit se atribuye a JUAN DAVID SALAS CAMARGO <31742974+jdsalasca@users.noreply.github.com>.

## Review Focus

1. Sin correo ni teléfono, contacto permanece pendiente.
2. Con solo teléfono o solo correo, contacto queda completo.
3. Un fallo de /api/admin/preparacion no se representa como lista vacía ni como hotel listo.
4. Los siete pasos llegan una sola vez; los completos no aparecen como pendientes.
5. Los pasos con URL tienen enlace propio; respaldo sin URL permanece texto y sigue contado.

### Task 1: Fuente única y contacto en la preparación

**Files:**
- Modify: apps/api/src/main/java/co/hotel/panel/PreparacionController.java
- Test: apps/api/src/test/java/co/hotel/panel/PreparacionTest.java

- [x] Confirmar RED de contactoPendienteEnLaLista sin el nuevo punto contacto.
- [x] Verificar que el contacto es pendiente sin canales y completo con teléfono; conservar la cobertura de correo.
- [x] Ejecutar `mvn -f apps/api/pom.xml -Dtest=PreparacionTest test` y exigir salida con cero fallos y errores.

### Task 2: Una sola lista en el panel

**Files:**
- Modify: apps/web/src/paginas/PaginaAdminPanel.tsx
- Test: apps/web/src/paginas/PaginaAdminPanel.test.tsx
- Consume: apps/web/src/componentes/ListaPreparacion.tsx

- [x] Confirmar RED del caso que recibía las alertas contradictorias o calculadas en cliente.
- [x] Renderizar solo la preparación calculada por el servidor; conservar los destinos accesibles de cada paso.
- [x] Esperar seis pendientes con el fixture actual (siete pasos, uno completo), comprobar sus enlaces y que no aparece el aviso duplicado.
- [x] Ejecutar prueba dirigida, suite frontend, chequeo de tipos de pruebas y build Vite con Node 24.21.0 instalado globalmente.

### Task 3: Verificación de interfaz e integración

- [x] Abrir el panel local en Chrome con datos sintéticos representativos.
- [x] Revisar escritorio y móvil, estados de carga/lista completa/falla, consola y desbordamiento horizontal; guardar capturas nuevas en docs/evidence/round-237/.
- [x] Conservar la captura previa panel-primera-vez.png sin modificar y verificar que la copia coincide por SHA-256.
- [x] Ejecutar toda la suite API: 485 pruebas, 0 fallos, 0 errores y 1 omitida; Maven terminó en BUILD SUCCESS.
- [x] Guardar en docs/evidence/round-237/verificacion.md comandos y salidas completas.
- [x] Revisar el diff, git diff --check, confirmar identidad del commit y mantener la rama aislada hasta que el coordinador haga cherry-pick a develop.
