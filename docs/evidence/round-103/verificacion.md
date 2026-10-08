# Ronda 103 — Tarifar por rango y días desde el panel (Etapa C/I)

Fecha: 2026-10-08.

## Dolor

El endpoint de rango existía (Ronda 101) pero solo por API: el hotel seguía sin poder
tarifar "fines de semana" sin mandar noches una por una.

## Diseño (bounded)

`<details>` en Precios que reutiliza la previa y la confirmación del lote
(`previaOrigen` decide a dónde confirma). Cero estilos nuevos: tarjeta, campo,
grupo-chequeos, botones y navegación existentes.

## Cambio

- Formulario rango: desde/hasta, checkboxes Lun–Dom (ISO 1–7, todos por defecto),
  precio y no-vendible; "Revisar rango" → misma previa; Confirmar guarda el rango.
- La previa del mes invalida la del rango y viceversa (nunca se mezclan).

## Verificación real

```text
docker compose build web → tsc + vite ok. npm test → 23/23. test:typecheck → 0.
Prueba viva (admin demo, Flexible/Doble): rango 07–13 jun 2027 todos los días →
previa «7 noches, — → 88, Cambia». No se confirmó: el dev queda intacto.
Capturas desktop + móvil (esta carpeta). Consola: solo 401 esperados.
```

## Archivos

- Tocados: `PaginaAdminInventario.tsx`, `docs/plan.md`.
- Nuevos: esta carpeta.

## Riesgos y límites

- Coordinación: 100 y 102 del colega (su `round-102` intacto, restaurado); 103 libre.
  Trabajo del colega respetado en todo.
