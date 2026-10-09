import { describe, expect, it, afterEach, vi } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ListaPreparacion } from './ListaPreparacion';

describe('ListaPreparacion', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('lista lo pendiente con enlace y calla lo hecho', () => {
    render(
      <MemoryRouter>
        <ListaPreparacion
          puntos={[
            { clave: 'a', titulo: 'Habitaciones', hecho: false, url: '/admin/inventario' },
            { clave: 'b', titulo: 'Hotel', hecho: true, url: '/admin/hotel' },
          ]}
        />
      </MemoryRouter>,
    );
    expect(screen.getByText('Habitaciones')).toBeTruthy();
    expect(screen.queryByText('Hotel')).toBeNull();
    expect(screen.getByText(/1 pendientes/)).toBeTruthy();
  });

  it('desaparece cuando todo está hecho', () => {
    const { container } = render(
      <MemoryRouter>
        <ListaPreparacion puntos={[{ clave: 'a', titulo: 'X', hecho: true, url: '/' }]} />
      </MemoryRouter>,
    );
    expect(container.textContent ?? '').not.toContain('Pon tu hotel a punto');
  });

  it('el punto sin puerta se lee en texto, sin enlace roto', () => {
    render(
      <MemoryRouter>
        <ListaPreparacion
          puntos={[{ clave: 'r', titulo: 'Respaldo reciente', hecho: false, url: null }]}
        />
      </MemoryRouter>,
    );
    expect(screen.getByText('Respaldo reciente')).toBeTruthy();
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('carga del backend cuando no le pasan puntos', async () => {
    globalThis.fetch = vi.fn(async () =>
      new Response(JSON.stringify({ items: [{ clave: 'a', titulo: 'Mapa', hecho: false, url: '/m' }] }), {
        status: 200,
      }),
    ) as unknown as typeof fetch;
    render(
      <MemoryRouter>
        <ListaPreparacion />
      </MemoryRouter>,
    );
    await waitFor(() => {
      expect(screen.getByText('Mapa')).toBeTruthy();
    });
  });
});
