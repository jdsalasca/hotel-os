# Ronda 47 — El CSV ya no ejecuta fórmulas inyectadas

## Lo que estaba mal

Entrecomillar no impide que Excel ejecute una fórmula: `"=HYPERLINK(...)"` se ejecuta igual. Los
textos del informe salen de la base (nombres, motivos, responsables que escribe el hotel), así que
una fórmula podía colarse sin querer —o queriendo— y ejecutarse en el equipo que abre la hoja.

## Lo que cambia

El helper `csv()` antepone una comilla simple a toda celda que empiece por `= + - @`, que Excel
interpreta como texto literal. Una sola línea en el único sitio por donde sale cada celda.

## Verificación

```text
.\mvnw.cmd test
Tests run: 257, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS

node tools/operacion/verificar-despliegue.mjs
despliegue: rutas de proxy, cabeceras y CSP coherentes   exit=0
```

Ronda sin cambios de interfaz: la evidencia es el test con tres inyecciones (`=`, `+`, `@`) y la
descarga real del CSV en el navegador (200, `text/csv`, 14 líneas).

## Nota de git

Sin push por regla vigente. Solo archivos de esta ronda en el commit.