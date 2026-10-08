import { describe, expect, it, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PuertaAdmin } from './Estado';

describe('PuertaAdmin', () => {
  afterEach(() => {
    cleanup();
  });

  it('toda pantalla cerrada ofrece entrar, no un callejon sin salida', () => {
    render(
      <MemoryRouter>
        <PuertaAdmin>Esta pantalla es para el personal del hotel.</PuertaAdmin>
      </MemoryRouter>,
    );
    expect(screen.getByText('Sesión requerida')).toBeTruthy();
    const entrar = screen.getByRole('link', { name: 'Iniciar sesión' });
    expect(entrar.getAttribute('href')).toBe('/admin/entrar');
  });
});
