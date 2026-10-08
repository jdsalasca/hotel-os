import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { SaludoSesion } from './App';

function simularSesion(admin: { email: string } | null, huesped: object | null) {
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
    vi.unstubAllGlobals();
  });

  it('muestra el correo con sesión del panel', async () => {
    simularSesion({ email: 'admin@hotel.test' }, null);
    const { container } = render(<SaludoSesion />);
    await waitFor(() => {
      expect(screen.getByText('Hola, admin@hotel.test')).toBeTruthy();
    });
    expect(container.textContent).toContain('Hola, admin@hotel.test');
  });

  it('muestra el nombre con sesión de huésped', async () => {
    simularSesion(null, { email: 'huesped@hotel.test', nombre: 'Luz', tieneReservas: false });
    render(<SaludoSesion />);
    await waitFor(() => {
      expect(screen.getByText('Hola, Luz')).toBeTruthy();
    });
  });

  it('no muestra nada sin sesión', async () => {
    simularSesion(null, null);
    const { container } = render(<SaludoSesion />);
    await waitFor(() => {
      expect(container.textContent ?? '').not.toContain('Hola,');
    });
  });
});
