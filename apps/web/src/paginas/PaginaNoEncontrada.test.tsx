import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { App } from '../App';

describe('PaginaNoEncontrada', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('una ruta falsa ofrece salidas, no un vacío', async () => {
    globalThis.fetch = vi.fn(async () =>
      new Response('{"error":"sin sesión"}', { status: 401 }),
    ) as unknown as typeof fetch;
    render(
      <MemoryRouter initialEntries={['/ruta-que-no-existe-xyz']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByText('Esa página no existe');
    expect(screen.getByRole('link', { name: 'Volver al inicio' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Consultar mi reserva' })).toBeTruthy();
  });
});
