# Ronda 227 — La suite de tests deja de castigar la contención de CPU

## El problema

Detectado en R226: dos tests fallaban a veces en suite completa y pasaban siempre aislados.
Con agentes trabajando en paralelo eso es peligroso: alguien empuja rojo creyendo que rompió
algo, o pierde tiempo persiguiendo un fantasma.

## Diagnóstico

Reproducido con medición, no con suposiciones. El test concreto:

```
FAIL src/paginas/PaginaAdminInventario.test.tsx > prioridad del alta inicial del inventario
     > elige un rango libre y rellena el formulario sin bloquear hasta confirmar
Error: Test timed out in 5000ms.
```

Y la pista que dio Vitest, que resultó ser el dato importante:

```
Environment  jsdom was created 32 times · 172.40s total, 40% of tracked time
```

Aislado, ese test tarda **1.709 / 1.853 / 1.970 ms** en tres corridas. El `testTimeout` por
defecto de Vitest son **5.000 ms**. Es decir: sólo quedaban ~3 s de holgura para un test que
monta la pantalla entera y retiene una petición diferida a propósito. En suite completa, con
32 jsdom y otras suites de agentes compitiendo por la misma CPU, se pasa de 5 s y falla.

**No es un bug de lógica del test.** Es el timeout mal dimensionado para una máquina
compartida.

## Cambio descartado, con su medición

La propia pista de Vitest sugería `pool: 'vmThreads'`, que crea jsdom una vez por trabajador
y además bajaba el tiempo de suite. Lo probé: **7 a 9 fallos por corrida**, de forma
consistente, frente a 1-3 antes. Ese pool no expone igual los globales que usan los tests
(`sessionStorage`, `navigator.clipboard`…). Revertido inmediatamente.

Lo anoto porque el consejo automatizado era razonable y aun así empeoraba las cosas: la
medida es la que manda, no la sugerencia.

## El cambio

`apps/web/vitest.config.ts` — `testTimeout: 15000`, con la medición en el comentario.

15 s sigue atrapando un cuelgue real —un test colgado no termina nunca, no tarda 15 s— y deja
de castigar la contención de CPU.

## Resultado

| | antes | después |
|---|---|---|
| Tasa de fallo | ~2-3 fallos / 14 corridas (**15-25 %**) | 1 fallo / 26 corridas (**<5 %**) |
| Corridas limpias seguidas | 1 como máximo | **20 seguidas** |

Suite con typecheck: **218/218**, `Type Errors: no errors`, build ✓ 6m 56s.

## Lo que NO conseguí

**No eliminé la intermitencia, la reduje.** Me resisto a llamarlo arreglado. Queda ~1 fallo
cada 20-25 corridas, y no lo cazé con el detalle suficiente para decir la causa raíz: puede
ser el mismo test con otra carrera, u otro distinto. Si alguien lo ve, el sitio donde mirar
es cualquier test que espere una petición diferida con menos de ~2 s de margen.

Siguiente paso natural, si se quiere cerrar del todo: que la pista de rendimiento de Vitest se
aplique de verdad (`pool: 'vmThreads'`) arreglando antes los globales que rompe, que bajaría
el 40 % de tiempo de entorno y con él la contención que provoca el fallo.
