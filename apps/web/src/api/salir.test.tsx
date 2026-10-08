import { describe, expect, it, vi, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useSesion } from './useSesion';
import { useSesionHuesped } from './useSesionHuesped';

/**
 * Salir miente hoy: declara la sesión cerrada aunque el servidor rechace el
 * cierre (403, 500) o ni responda. Estos tests fijan el contrato honesto:
 * `salir()` avisa true solo con confirmación del servidor y conserva la sesión
 * en caso contrario.
 */
function sesiones(estadoSalida: number, caeRed = false) {
  return vi.fn(async (url: unknown) => {
    const ruta = String(url);
    if (ruta.endsWith('/api/admin/sesion')) {
      return new Response(JSON.stringify({ email: 'admin@hotel.test', nombre: 'Admin' }), {
        status: 200,
      });
    }
    if (ruta.endsWith('/api/yo')) {
      return new Response(
        JSON.stringify({ email: 'huesped@hotel.test', nombre: 'Huésped', tieneReservas: false }),
        { status: 200 },
      );
    }
    if (ruta.endsWith('/api/admin/logout') || ruta.endsWith('/api/huesped/logout')) {
      if (caeRed) throw new TypeError('fetch failed');
      return new Response(JSON.stringify({}), { status: estadoSalida });
    }
    throw new Error('ruta no esperada: ' + ruta);
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('salida del panel', () => {
  it('con 200 se cierra y avisa true', async () => {
    vi.stubGlobal('fetch', sesiones(200));
    const { result } = renderHook(() => useSesion());
    await act(async () => {
      await result.current.comprobar();
    });
    expect(result.current.haySesion).toBe(true);
    let ok!: boolean;
    await act(async () => {
      ok = await result.current.salir();
    });
    expect(ok).toBe(true);
    expect(result.current.haySesion).toBe(false);
  });

  it('con 403 no se declara cerrada y avisa false', async () => {
    vi.stubGlobal('fetch', sesiones(403));
    const { result } = renderHook(() => useSesion());
    await act(async () => {
      await result.current.comprobar();
    });
    expect(result.current.haySesion).toBe(true);
    let ok!: boolean;
    await act(async () => {
      ok = await result.current.salir();
    });
    expect(ok).toBe(false);
    expect(result.current.haySesion).toBe(true);
  });

  it('sin red no se declara cerrada y avisa false', async () => {
    vi.stubGlobal('fetch', sesiones(500, true));
    const { result } = renderHook(() => useSesion());
    await act(async () => {
      await result.current.comprobar();
    });
    expect(result.current.haySesion).toBe(true);
    let ok!: boolean;
    await act(async () => {
      ok = await result.current.salir();
    });
    expect(ok).toBe(false);
    expect(result.current.haySesion).toBe(true);
  });
});

describe('salida del huésped', () => {
  it('con 200 se cierra y avisa true', async () => {
    vi.stubGlobal('fetch', sesiones(200));
    const { result } = renderHook(() => useSesionHuesped());
    await act(async () => {
      await result.current.comprobar();
    });
    expect(result.current.haySesion).toBe(true);
    let ok!: boolean;
    await act(async () => {
      ok = await result.current.salir();
    });
    expect(ok).toBe(true);
    expect(result.current.haySesion).toBe(false);
  });

  it('con 500 no se declara cerrada y avisa false', async () => {
    vi.stubGlobal('fetch', sesiones(500));
    const { result } = renderHook(() => useSesionHuesped());
    await act(async () => {
      await result.current.comprobar();
    });
    expect(result.current.haySesion).toBe(true);
    let ok!: boolean;
    await act(async () => {
      ok = await result.current.salir();
    });
    expect(ok).toBe(false);
    expect(result.current.haySesion).toBe(true);
  });
});
