import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { App } from './App';

describe('App', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('al navegar se vuelve arriba, no a mitad de página', async () => {
    const subir = vi.fn();
    window.scrollTo = subir as unknown as typeof window.scrollTo;
    globalThis.fetch = vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.includes('/api/hotel')) return new Response('{}', { status: 200 });
      if (ruta.includes('/api/lugares')) return new Response('{"hotel":{},"lugares":[]}', { status: 200 });
      if (ruta.includes('/api/amenidades')) return new Response('{}', { status: 200 });
      if (ruta.includes('/api/disponibilidad')) {
        return new Response(JSON.stringify({ dias: [] }), { status: 200 });
      }
      return new Response('{"error":"sin sesión"}', { status: 401 });
    }) as unknown as typeof fetch;
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByText('¿Cuándo quieres venir?');
    subir.mockClear();
    fireEvent.click(screen.getByRole('link', { name: 'Consultar reserva' }));
    await waitFor(() => {
      expect(subir).toHaveBeenCalledWith(0, 0);
    });
  });
});
