import { describe, expect, it, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PuertaAdmin } from './Estado';
import { MuroAntipanico } from './Estado';

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

function Explota(): React.ReactNode {
  throw new Error('pum');
}

describe('MuroAntipanico', () => {
  afterEach(() => {
    cleanup();
  });

  it('una página rota ofrece salidas, no la pantalla en blanco', () => {
    render(
      <MemoryRouter>
        <MuroAntipanico>
          <Explota />
        </MuroAntipanico>
      </MemoryRouter>,
    );
    return screen.findByText('Algo se rompió en esta pantalla').then(() => {
      expect(screen.getByRole('link', { name: 'Volver al inicio' })).toBeTruthy();
    });
  });

  it('sin error deja pasar al contenido', () => {
    render(
      <MemoryRouter>
        <MuroAntipanico>
          <p>contenido sano</p>
        </MuroAntipanico>
      </MemoryRouter>,
    );
    expect(screen.getByText('contenido sano')).toBeTruthy();
  });
});
