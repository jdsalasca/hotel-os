import { describe, expect, it, vi, afterEach } from 'vitest';
import { api, ErrorApi } from './cliente';

function respuesta(texto: string, estado = 200): Response {
  return new Response(texto, {
    status: estado,
    headers: { 'Content-Type': estado === 200 && texto.startsWith('<') ? 'text/html' : 'application/json' },
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('cliente HTTP', () => {
  it('un 200 con cuerpo no-JSON es error de protocolo, no un null tipado', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => respuesta('<html>puerta de enlace</html>')));
    await expect(api.get('/api/disponibilidad')).rejects.toMatchObject({
      name: 'ErrorApi',
      estado: 200,
    });
    await expect(api.get('/api/disponibilidad')).rejects.toThrow(/no es JSON/);
  });

  it('un 204 vacío sigue siendo null legítimo', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 204 })));
    await expect(api.get('/api/lo-que-sea')).resolves.toBeNull();
  });

  it('la cancelación deliberada no se convierte en "sin conexión"', async () => {
    const controlador = new AbortController();
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_url: unknown, opciones?: RequestInit) => {
        opciones?.signal?.throwIfAborted();
        throw new Error('no debería llegar');
      }),
    );
    controlador.abort();
    const fallo = await api.get('/api/disponibilidad', { signal: controlador.signal }).catch((e) => e);
    // Sin instanceof: cada reino trae su DOMException; lo que importa es el nombre.
    expect((fallo as { name?: unknown }).name).toBe('AbortError');
    expect(fallo).not.toBeInstanceOf(ErrorApi);
  });

  it('la señal del usuario corta aunque viaje compuesta con el plazo', async () => {
    const controlador = new AbortController();
    const espia = vi.fn(async (_ruta: string, _opciones?: RequestInit): Promise<Response> => respuesta('{}'));
    vi.stubGlobal('fetch', espia);
    await api.get('/api/disponibilidad', { signal: controlador.signal });
    expect(espia).toHaveBeenCalledOnce();
    // Ya no viaja el objeto original (va compuesta con el plazo de 30 s): lo que se
    // fija es que obedecerla corta de verdad.
    const viajera = espia.mock.calls[0]?.[1]?.signal as AbortSignal;
    expect(viajera).not.toBe(controlador.signal);
    controlador.abort();
    expect(viajera.aborted).toBe(true);
  });

  it('sin red el mensaje habla español y no es un TypeError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('fetch failed');
      }),
    );
    const fallo = await api.get('/api/disponibilidad').catch((e) => e);
    expect(fallo).toBeInstanceOf(ErrorApi);
    expect((fallo as ErrorApi).estado).toBe(0);
    expect((fallo as Error).message).toMatch(/conexión/);
  });

  it('colgada 30 segundos avisa de espera en vez de girar eterno', async () => {
    vi.useFakeTimers();
    try {
      vi.stubGlobal(
        'fetch',
        vi.fn(
          (_url: unknown, opciones?: RequestInit) =>
            new Promise<Response>((_resolver, rechazar) => {
              opciones?.signal?.addEventListener('abort', () => {
                rechazar(new DOMException('signal timed out', 'AbortError'));
              });
            }),
        ),
      );
      const pendiente = api.get('/api/disponibilidad').catch((e) => e);
      await vi.advanceTimersByTimeAsync(30_000);
      const fallo = await pendiente;
      expect(fallo).toBeInstanceOf(ErrorApi);
      expect((fallo as ErrorApi).estado).toBe(0);
      expect((fallo as Error).message).toMatch(/tardando/);
    } finally {
      vi.useRealTimers();
    }
  });

  it('la cancelación deliberada gana al plazo: sigue siendo AbortError', async () => {
    vi.useFakeTimers();
    try {
      const controlador = new AbortController();
      vi.stubGlobal(
        'fetch',
        vi.fn(
          (_url: unknown, opciones?: RequestInit) =>
            new Promise<Response>((_resolver, rechazar) => {
              opciones?.signal?.addEventListener('abort', () => {
                rechazar(new DOMException('aborted', 'AbortError'));
              });
            }),
        ),
      );
      const pendiente = api.get('/api/disponibilidad', { signal: controlador.signal }).catch((e) => e);
      controlador.abort();
      await vi.advanceTimersByTimeAsync(60_000);
      const fallo = await pendiente;
      expect(fallo).not.toBeInstanceOf(ErrorApi);
      expect((fallo as { name?: unknown }).name).toBe('AbortError');
    } finally {
      vi.useRealTimers();
    }
  });
});
