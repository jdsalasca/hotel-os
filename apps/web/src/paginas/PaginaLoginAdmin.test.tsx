import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PaginaLoginAdmin } from './PaginaLoginAdmin';

describe('PaginaLoginAdmin', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('cuenta qué pasó cuando Google no completa la vuelta', async () => {
    globalThis.fetch = vi.fn(async () =>
      new Response('{"error":"sin sesión"}', { status: 401 }),
    ) as unknown as typeof fetch;
    render(
      <MemoryRouter initialEntries={['/admin/entrar?error=oauth2']}>
        <PaginaLoginAdmin />
      </MemoryRouter>,
    );
    await screen.findByText(/Google no completó la entrada/);
  });

  it('sin error en la URL no inventa avisos', async () => {
    globalThis.fetch = vi.fn(async () =>
      new Response('{"error":"sin sesión"}', { status: 401 }),
    ) as unknown as typeof fetch;
    render(
      <MemoryRouter initialEntries={['/admin/entrar']}>
        <PaginaLoginAdmin />
      </MemoryRouter>,
    );
    await screen.findByRole('button', { name: 'Entrar' });
    expect(screen.queryByText(/Google no completó la entrada/)).toBeNull();
  });
});
