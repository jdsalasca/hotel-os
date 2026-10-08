import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import { SaludoSesion } from './App';

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
    render(<SaludoSesion />);
    await waitFor(() => {
      expect(screen.getByText('Bienvenido de vuelta, Juan')).toBeTruthy();
    });
  });

  it('cae al correo si el panel no tiene nombre', async () => {
    simularSesion({ email: 'admin@hotel.test', nombre: '' }, null);
    render(<SaludoSesion />);
    await waitFor(() => {
      expect(screen.getByText('Bienvenido de vuelta, admin@hotel.test')).toBeTruthy();
    });
  });

  it('da la bienvenida al huésped por su nombre de Google', async () => {
    simularSesion(null, { email: 'huesped@hotel.test', nombre: 'Luz', tieneReservas: false });
    render(<SaludoSesion />);
    await waitFor(() => {
      expect(screen.getByText('Bienvenido de vuelta, Luz')).toBeTruthy();
    });
  });

  it('no muestra nada sin sesión', async () => {
    simularSesion(null, null);
    const { container } = render(<SaludoSesion />);
    await waitFor(() => {
      expect(container.textContent ?? '').not.toContain('Bienvenido');
    });
  });
});
