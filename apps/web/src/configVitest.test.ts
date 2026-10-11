import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * La suite se degrada bajo carga. Medido: 204 pruebas pasan, la suite corre en ~15 s, pero
 * jsdom se crea **una vez por archivo**, 31 veces con ~50 s acumulados, un 3,3x el tiempo
 * real de la suite. Con Docker y otros procesos en marcha ese coste dispara timeouts
 * intermitentes que no se reproducen con la maquina tranquila.
 *
 * Se probaron las dos salidas que anuncia Vitest y **las dos estan bloqueadas**:
 *
 * 1. `pool: 'vmThreads'` (reutiliza el entorno por worker, mantiene el aislamiento). Bajo
 *    ese pool `window.location` y sus metodos quedan no redefinibles, y los 7 tests que los
 *    estubean revientan con `TypeError: Cannot redefine property` (PaginaLoginAdmin,
 *    SaludoSesion, PaginaAdminPanel, App). No es un fallo de esos tests: es un contrato
 *    distinto del pool. Usarlo exigiria que el codigo de produccion no dependa de
 *    `window.location` directamente.
 *
 * 2. `isolate: false` (comparte el entorno entre archivos). Mas rapido, pero un `window`
 *    modificado en un test aparece modificado en el siguiente y el fallo sale donde no
 *    toca. Por eso queda vigilado abajo.
 *
 * Lo que sí se aplico, medido y sin riesgo: los tests de logica pura (cliente, formato,
 * sesion) no necesitan DOM y se marcaron `@vitest-environment node`. Esos tres pasaron de
 * 1,82 s a 0,85 s.
 */
const CONFIG = join(process.cwd(), 'vitest.config.ts');

describe('configuracion de la suite', () => {
  it('cada archivo conserva su propio entorno', () => {
    // Sin comentarios: se busca el ajuste real, no su menciona en un comentario.
    const codigo = readFileSync(CONFIG, 'utf8')
      .split('\n')
      .map((linea) => linea.replace(/\/\/.*$/, ''))
      .join('\n');

    expect(codigo).not.toContain('isolate: false');
  });
});