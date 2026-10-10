import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PaginaMisReservas } from './PaginaMisReservas';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('acceso a mis reservas', () => {
  it('presenta por separado las opciones de cuenta y consulta con código', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response('{}', { status: 401 })),
    );

    render(
      <MemoryRouter>
        <PaginaMisReservas />
      </MemoryRouter>,
    );

    expect(await screen.findByText('Elige cómo consultar tus reservas.')).toBeTruthy();

    const googleCard = screen
      .getByRole('heading', { name: 'Con tu cuenta de Google' })
      .closest('article');
    const codeCard = screen
      .getByRole('heading', { name: 'Con código y correo' })
      .closest('article');

    expect(googleCard).not.toBeNull();
    expect(codeCard).not.toBeNull();
    expect(within(googleCard as HTMLElement).getByRole('link', { name: 'Entrar con Google' }).getAttribute('href'))
      .toContain('/oauth2/authorization/google-huesped');
    expect(
      within(codeCard as HTMLElement)
        .getByRole('link', { name: 'Consultar una reserva' })
        .getAttribute('href'),
    ).toBe('/consulta');
  });
});
