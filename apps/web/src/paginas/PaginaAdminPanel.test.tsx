import { describe, expect, it, vi, afterEach } from 'vitest';
import { act, render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PaginaAdminPanel } from './PaginaAdminPanel';

describe('PaginaAdminPanel', () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('el admin ajusta su nombre visible y recarga con el saludo fresco', async () => {
    const recargar = vi.fn();
    Object.defineProperty(window, 'location', {
      value: { assign: vi.fn(), reload: recargar },
      writable: true,
    });
    const llamadas: { url: string; cuerpo: string }[] = [];
    globalThis.fetch = vi.fn(async (url: unknown, init?: { body?: string }) => {
      const ruta = String(url);
      if (ruta.includes('/api/admin/perfil')) {
        llamadas.push({ url: ruta, cuerpo: String(init?.body ?? '') });
        return new Response(JSON.stringify({ nombre: 'Carolina Ruiz' }), { status: 200 });
      }
      if (ruta.includes('/api/admin/sesion')) {
        return new Response(JSON.stringify({ email: 'admin@hotel.test', nombre: '' }), {
          status: 200,
        });
      }
      if (ruta.includes('/api/admin/mensajes/nuevos')) {
        return new Response(JSON.stringify({ nuevos: 0 }), { status: 200 });
      }
      return new Response('{}', { status: 404 });
    }) as unknown as typeof fetch;
    render(
      <MemoryRouter>
        <PaginaAdminPanel />
      </MemoryRouter>,
    );
    fireEvent.change(await screen.findByLabelText('Nombre visible'), {
      target: { value: 'Carolina Ruiz' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar nombre' }));
    await waitFor(() => {
      expect(llamadas.length).toBe(1);
    });
    expect(llamadas[0]?.cuerpo).toContain('Carolina Ruiz');
    await waitFor(() => {
      expect(recargar).toHaveBeenCalled();
    });
  });

  it('anuncia al lector de pantalla los mensajes nuevos al actualizarse', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    let nuevos = 1;
    globalThis.fetch = vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.includes('/api/admin/sesion')) {
        return new Response(JSON.stringify({ email: 'admin@hotel.test', nombre: 'A' }), { status: 200 });
      }
      if (ruta.includes('/api/admin/mensajes/nuevos')) {
        return new Response(JSON.stringify({ nuevos }), { status: 200 });
      }
      return new Response('{}', { status: 404 });
    }) as unknown as typeof fetch;
    render(
      <MemoryRouter>
        <PaginaAdminPanel />
      </MemoryRouter>,
    );

    await screen.findByText('1 mensaje sin leer');
    expect(screen.getByRole('status').textContent).toBe('1 mensaje sin leer');
    nuevos = 4;
    await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });

    expect(screen.getByRole('status').textContent).toBe('4 mensajes sin leer');
  });

  it('llama "habitaciones" a la puerta y a su botón, no "inventario"', async () => {
    // "Inventario" es jerga de gestión de ingresos. Quien regenta un hotel busca
    // "habitaciones": si la puerta y el destino no se llaman igual, no los relaciona.
    globalThis.fetch = vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.includes('/api/admin/sesion')) {
        return new Response(JSON.stringify({ email: 'dueno@hotel.test', nombre: 'Dueño' }), {
          status: 200,
        });
      }
      if (ruta.includes('/api/admin/mensajes/nuevos')) {
        return new Response(JSON.stringify({ nuevos: 0 }), { status: 200 });
      }
      return new Response('{}', { status: 404 });
    }) as unknown as typeof fetch;

    render(
      <MemoryRouter>
        <PaginaAdminPanel />
      </MemoryRouter>,
    );

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'Habitaciones' })).toBeTruthy(),
    );
    // En toda la página no debe quedar ni un "inventario": es otra palabra para lo mismo.
    expect(screen.queryAllByText(/inventario/i)).toHaveLength(0);
  });
});
