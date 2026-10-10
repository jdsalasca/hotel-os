import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { CierreSesionPanel, SaludoSesion } from './App';

function simularSesion(admin: object | null, huesped: object | null) {
  globalThis.fetch = vi.fn(async (url: unknown) => {
    const ruta = String(url);
    if (ruta.includes('/api/admin/sesion')) {
      return admin
        ? new Response(JSON.stringify(admin), { status: 200 })
        : new Response('{"error":"sin sesión"}', { status: 401 });
    }
    if (ruta.includes('/api/yo')) {
      return huesped
        ? new Response(JSON.stringify(huesped), { status: 200 })
        : new Response('{"error":"sin sesión"}', { status: 401 });
    }
    return new Response('{}', { status: 404 });
  }) as unknown as typeof fetch;
}

describe('SaludoSesion', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('da la bienvenida por el nombre del panel', async () => {
    simularSesion({ email: 'admin@hotel.test', nombre: 'Juan' }, null);
    render(
      <MemoryRouter>
        <SaludoSesion />
      </MemoryRouter>,
    );
    await waitFor(() => {
      expect(screen.getByText('Bienvenido de vuelta, Juan')).toBeTruthy();
    });
    expect(screen.getByText('Administrador')).toBeTruthy();
  });

  it('sin nombre usa lo de antes del @, no el correo cortado', async () => {
    simularSesion({ email: 'savatar62@gmail.com', nombre: '' }, null);
    render(
      <MemoryRouter>
        <SaludoSesion />
      </MemoryRouter>,
    );
    await waitFor(() => {
      expect(screen.getByText('Bienvenido de vuelta, Savatar62')).toBeTruthy();
    });
  });

  it('da la bienvenida al huésped por su nombre de Google', async () => {
    simularSesion(null, { email: 'huesped@hotel.test', nombre: 'Luz', tieneReservas: false });
    render(
      <MemoryRouter>
        <SaludoSesion />
      </MemoryRouter>,
    );
    await waitFor(() => {
      expect(screen.getByText('Bienvenido de vuelta, Luz')).toBeTruthy();
    });
    expect(screen.getByText('Huésped')).toBeTruthy();
  });

  it('no muestra nada sin sesión', async () => {
    simularSesion(null, null);
    const { container } = render(
      <MemoryRouter>
        <SaludoSesion />
      </MemoryRouter>,
    );
    await waitFor(() => {
      expect(container.textContent ?? '').not.toContain('Bienvenido');
    });
  });
});

describe('CierreSesionPanel', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('ofrece salir con sesión y recarga a la entrada al cerrar', async () => {
    const asignar = vi.fn();
    Object.defineProperty(window, 'location', {
      value: { assign: asignar },
      writable: true,
    });
    globalThis.fetch = vi.fn(async (url: unknown, init?: { method?: string }) => {
      const ruta = String(url);
      if (ruta.includes('/api/admin/logout')) {
        expect(init?.method).toBe('POST');
        return new Response('{"estado":"sesión cerrada"}', { status: 200 });
      }
      if (ruta.includes('/api/admin/sesion')) {
        return new Response(JSON.stringify({ email: 'admin@hotel.test', nombre: 'Juan' }), {
          status: 200,
        });
      }
      return new Response('{}', { status: 404 });
    }) as unknown as typeof fetch;
    render(
      <MemoryRouter>
        <CierreSesionPanel />
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole('button', { name: 'Cerrar sesión' }));
    await waitFor(() => {
      expect(asignar).toHaveBeenCalledWith('/admin/entrar');
    });
  });

  it('no muestra nada sin sesión', async () => {
    globalThis.fetch = vi.fn(async () =>
      new Response('{"error":"sin sesión"}', { status: 401 }),
    ) as unknown as typeof fetch;
    render(
      <MemoryRouter>
        <CierreSesionPanel />
      </MemoryRouter>,
    );
    await waitFor(() => {
      expect(document.body.textContent ?? '').not.toContain('Cerrar sesión');
    });
  });
});
