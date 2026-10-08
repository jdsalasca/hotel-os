import { describe, expect, it } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { usePeticion } from './usePeticion';

function diferida<T>() {
  let resolver!: (v: T) => void;
  const promesa = new Promise<T>((res) => {
    resolver = res;
  });
  return { promesa, resolver };
}

describe('usePeticion', () => {
  it('la respuesta tardía de una búsqueda vieja no pisa a la nueva', async () => {
    const { result } = renderHook(() => usePeticion<string>());
    const vieja = diferida<string>();
    const nueva = diferida<string>();

    let pVieja!: Promise<string | null>;
    let pNueva!: Promise<string | null>;
    act(() => {
      pVieja = result.current.ejecutar(() => vieja.promesa);
    });
    act(() => {
      pNueva = result.current.ejecutar(() => nueva.promesa);
    });
    await act(async () => {
      vieja.resolver('A');
      await pVieja;
    });
    expect(result.current.datos).toBeNull();
    await act(async () => {
      nueva.resolver('B');
      await pNueva;
    });
    expect(result.current.datos).toBe('B');
    expect(result.current.cargando).toBe(false);
  });

  it('abortar silencia lo que venía en camino sin mensaje de error', async () => {
    const { result } = renderHook(() => usePeticion<string>());
    const pendiente = diferida<string>();
    let p!: Promise<string | null>;
    act(() => {
      p = result.current.ejecutar(() => pendiente.promesa);
    });
    act(() => {
      result.current.abortar();
    });
    await act(async () => {
      pendiente.resolver('tarde');
      await p;
    });
    expect(result.current.datos).toBeNull();
    expect(result.current.error).toBeNull();
    expect(result.current.cargando).toBe(false);
  });

  it('el error de la petición vigente sí se muestra', async () => {
    const { result } = renderHook(() => usePeticion<string>());
    await act(async () => {
      await result.current.ejecutar(() => Promise.reject(new Error('se cayó')));
    });
    expect(result.current.error).toBe('se cayó');
    expect(result.current.cargando).toBe(false);
  });
});
