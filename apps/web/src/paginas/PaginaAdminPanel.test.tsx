import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PaginaAdminPanel } from './PaginaAdminPanel';

describe('PaginaAdminPanel', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('el admin ajusta su nombre visible y se guarda', async () => {
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
    await screen.findByText('Nombre guardado');
  });
});
