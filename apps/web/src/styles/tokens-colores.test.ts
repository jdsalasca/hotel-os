import { readdir, readFile } from 'node:fs/promises';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const carpetaEstilos = dirname(fileURLToPath(import.meta.url));
const literalColor = /#[\da-f]{3,8}\b|\b(?:rgba?|hsla?|hwb|lab|lch|oklab|oklch|color)\s*\(|\btransparent\b/gi;

async function listarScss(carpeta: string): Promise<string[]> {
  const entradas = await readdir(carpeta, { withFileTypes: true });
  const grupos = await Promise.all(entradas.map(async (entrada) => {
    const ruta = join(carpeta, entrada.name);
    if (entrada.isDirectory()) return listarScss(ruta);
    return entrada.isFile() && entrada.name.endsWith('.scss') ? [ruta] : [];
  }));
  return grupos.flat();
}

describe('tokens de color SCSS', () => {
  it('mantiene los valores cromáticos dentro del archivo central de tokens', async () => {
    const archivos = (await listarScss(carpetaEstilos))
      .filter((archivo) => relative(carpetaEstilos, archivo) !== '_tokens.scss');
    const usosDirectos: string[] = [];

    for (const archivo of archivos) {
      const contenido = await readFile(archivo, 'utf8');
      for (const coincidencia of contenido.matchAll(new RegExp(literalColor.source, literalColor.flags))) {
        const posicion = coincidencia.index ?? 0;
        const linea = contenido.slice(0, posicion).split('\n').length;
        usosDirectos.push(`${relative(carpetaEstilos, archivo)}:${linea}`);
      }
    }

    expect(usosDirectos).toEqual([]);
  });
});
