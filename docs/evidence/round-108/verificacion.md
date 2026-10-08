# Ronda 108 - Pon tu hotel a punto

Fecha: 2026-10-08.

## Diseño (brainstorming, vía acotada)
El hotel con perfil vacío no sabe por dónde empezar. Opciones: (a) checklist calculada
del backend + tarjeta en el panel (una fuente de verdad, testeable), (b) solo front
derivando de llamadas sueltas (lógica duplicada), (c) recordatorios por correo
(bloqueado: sin credenciales). Va (a): `GET /api/admin/preparacion` con 5 puntos
(habitaciones, tarifas futuras, datos, ubicación, lugares), cada uno con enlace.

## Verificación real (TDD)
- Backend: test primero en rojo (404), verde tras el controlador; vecinas por correr en
  el deploy (build Docker compila).
- Frontend: `ListaPreparacion.test.tsx` (pendientes con enlace, silencio si todo hecho,
  carga del backend). **26/26 vitest en 8 archivos**, tsc con tests limpio.
- Despliegue + checklist visible en el panel con sesión real.
- Archivos del otro agente intactos.

## Archivos
- Nuevos: `PreparacionController.java`, `PreparacionTest.java`, `ListaPreparacion.tsx`,
  `ListaPreparacion.test.tsx`, este archivo.
- Tocados: `PaginaAdminPanel.tsx`.
