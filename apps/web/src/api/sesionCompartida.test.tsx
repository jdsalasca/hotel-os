import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';

function respuestaSesion(email: string): Response {
  return new Response(JSON.stringify({ email }), { status: 200 });
}

describe('sesión compartida entre hooks', () => {
  beforeEach(() => {
    vi.resetModules();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('dos hooks montados a la vez hacen una sola petición', async () => {
    const fetchEspiado = vi.fn(async () => respuestaSesion('admin@h.test'));
    vi.stubGlobal('fetch', fetchEspiado);
    const { useSesion } = await import('./useSesion');
    const primero = renderHook(() => useSesion());
    const segundo = renderHook(() => useSesion());
    await waitFor(() => {
      expect(primero.result.current.haySesion).toBe(true);
      expect(segundo.result.current.haySesion).toBe(true);
    });
    expect(fetchEspiado).toHaveBeenCalledOnce();
    expect(segundo.result.current.email).toBe('admin@h.test');
  });

  it('comprobar fuerza una petición nueva', async () => {
    const fetchEspiado = vi.fn(async () => respuestaSesion('admin@h.test'));
    vi.stubGlobal('fetch', fetchEspiado);
    const { useSesion } = await import('./useSesion');
    const gancho = renderHook(() => useSesion());
    await waitFor(() => {
      expect(gancho.result.current.haySesion).toBe(true);
    });
    await act(async () => {
      await gancho.result.current.comprobar();
    });
    expect(fetchEspiado).toHaveBeenCalledTimes(2);
  });
});
