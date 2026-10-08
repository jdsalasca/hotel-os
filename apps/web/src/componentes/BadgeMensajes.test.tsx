import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import { BadgeMensajes } from './BadgeMensajes';

function simular(nuevos: number | null) {
  globalThis.fetch = vi.fn(async (url: unknown) => {
    const ruta = String(url);
    if (ruta.includes('/api/yo')) {
      return new Response(JSON.stringify({ email: 'h@h.test', nombre: 'H', tieneReservas: true }), {
        status: 200,
      });
    }
    if (ruta.includes('/api/mis-reservas/mensajes/nuevos')) {
      return nuevos === null
        ? new Response('{"error":"x"}', { status: 401 })
        : new Response(JSON.stringify({ nuevos, porReserva: {} }), { status: 200 });
    }
    return new Response('{}', { status: 404 });
  }) as unknown as typeof fetch;
}

describe('BadgeMensajes', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('muestra el conteo cuando hay nuevos', async () => {
    simular(3);
    render(<BadgeMensajes />);
    await waitFor(() => {
      expect(screen.getByText('3 sin leer')).toBeTruthy();
    });
  });

  it('no muestra nada sin nuevos ni sin sesión', async () => {
    simular(0);
    const { container } = render(<BadgeMensajes />);
    await waitFor(() => {
      expect(container.textContent ?? '').not.toContain('sin leer');
    });
  });
});
