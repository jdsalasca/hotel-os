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

  it('abre la navegación compacta y la cierra después de elegir un destino', async () => {
    window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;
    globalThis.fetch = vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.includes('/api/hotel')) return new Response('{}', { status: 200 });
      if (ruta.includes('/api/admin/sesion') || ruta.includes('/api/yo')) {
        return new Response('{}', { status: 401 });
      }
      if (ruta.includes('/api/disponibilidad/calendario')) {
        return new Response('{"dias":[]}', { status: 200 });
      }
      if (ruta.includes('/api/lugares')) return new Response('{"hotel":{},"lugares":[]}', { status: 200 });
      return new Response('{}', { status: 200 });
    }) as unknown as typeof fetch;

    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByText('¿Cuándo quieres venir?');

    fireEvent.click(screen.getByRole('button', { name: 'Abrir menú principal' }));
    expect(screen.getByRole('button', { name: 'Cerrar menú principal' }).getAttribute('aria-expanded')).toBe('true');

    fireEvent.click(screen.getByRole('link', { name: 'Consultar reserva' }));
    await screen.findByRole('heading', { name: 'Consultar una reserva' });
    expect(screen.getByRole('button', { name: 'Abrir menú principal' }).getAttribute('aria-expanded')).toBe('false');

    fireEvent.click(screen.getByRole('button', { name: 'Abrir menú principal' }));
    fireEvent.click(screen.getByRole('link', { name: 'Consultar reserva' }));
    expect(screen.getByRole('button', { name: 'Abrir menú principal' }).getAttribute('aria-expanded')).toBe('false');
  });

  it('muestra accesos del panel en lugar de mezclar las rutas públicas', async () => {
    window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;
    globalThis.fetch = vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.includes('/api/admin/sesion') || ruta.includes('/api/yo')) {
        return new Response('{}', { status: 401 });
      }
      if (ruta.includes('/api/hotel')) return new Response('{}', { status: 200 });
      return new Response('{}', { status: 200 });
    }) as unknown as typeof fetch;

    const { container } = render(
      <MemoryRouter initialEntries={['/admin']}>
        <App />
      </MemoryRouter>,
    );

    const nav = container.querySelector('header nav[aria-label="Navegación administrativa"]');
    expect(nav?.textContent).toContain('Reservas');
    expect(nav?.textContent).toContain('Inventario');
    expect(nav?.textContent).not.toContain('Consultar reserva');
  });
});
