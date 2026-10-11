import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom',
    // Medido: el test más lento («elige un rango libre…», que monta la pantalla y retiene una
    // petición diferida a propósito) tarda 1,7-2,0 s aislado. El límite por defecto de Vitest
    // son 5 s, así que sólo quedaban ~3 s de holgura: en suite completa, con jsdom creado 32
    // veces y otras suites de agentes compitiendo por la CPU, ese test agotaba el tiempo y
    // fallaba sin que nadie hubiera roto nada. 15 s sigue atrapando un cuelgue real (que no
    // termina nunca) y deja de castigar la contención de CPU.
    testTimeout: 15000,
  },
});
