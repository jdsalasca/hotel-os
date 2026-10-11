import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
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

  it('tras entrar con clave recarga al panel con estado fresco', async () => {
    const asignar = vi.fn();
    Object.defineProperty(window, 'location', {
      value: { assign: asignar },
      writable: true,
    });
    globalThis.fetch = vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.includes('/api/admin/login')) {
        return new Response(JSON.stringify({ estado: 'autenticado' }), { status: 200 });
      }
      return new Response('{"error":"sin sesión"}', { status: 401 });
    }) as unknown as typeof fetch;
    render(
      <MemoryRouter initialEntries={['/admin/entrar']}>
        <PaginaLoginAdmin />
      </MemoryRouter>,
    );
    fireEvent.change(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'admin@hotel.test' },
    });
    fireEvent.change(screen.getByLabelText('Contraseña'), {
      target: { value: 'clave-larga-123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));
    await waitFor(() => {
      expect(asignar).toHaveBeenCalledWith('/admin');
    });
  });

  it('vuelve a la pantalla protegida desde la que pidió iniciar sesión', async () => {
    const asignar = vi.fn();
    Object.defineProperty(window, 'location', {
      value: { assign: asignar },
      writable: true,
    });
    globalThis.fetch = vi.fn(async (url: unknown) => {
      if (String(url).includes('/api/admin/login')) {
        return new Response(JSON.stringify({ estado: 'autenticado' }), { status: 200 });
      }
      return new Response('{"error":"sin sesión"}', { status: 401 });
    }) as unknown as typeof fetch;
    render(
      <MemoryRouter initialEntries={[{
        pathname: '/admin/entrar',
        state: { volverA: '/admin/reservas?estado=PENDIENTE#lista' },
      }]}>
        <PaginaLoginAdmin />
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'admin@hotel.test' },
    });
    fireEvent.change(screen.getByLabelText('Contraseña'), {
      target: { value: 'clave-larga-123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => {
      expect(asignar).toHaveBeenCalledWith('/admin/reservas?estado=PENDIENTE#lista');
    });
  });

  it('ignora destinos de retorno que apuntan fuera del panel', async () => {
    const asignar = vi.fn();
    Object.defineProperty(window, 'location', {
      value: { assign: asignar },
      writable: true,
    });
    globalThis.fetch = vi.fn(async (url: unknown) => {
      if (String(url).includes('/api/admin/login')) {
        return new Response(JSON.stringify({ estado: 'autenticado' }), { status: 200 });
      }
      return new Response('{"error":"sin sesión"}', { status: 401 });
    }) as unknown as typeof fetch;
    render(
      <MemoryRouter initialEntries={[{
        pathname: '/admin/entrar',
        state: { volverA: '//malicioso.test/admin' },
      }]}>
        <PaginaLoginAdmin />
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'admin@hotel.test' },
    });
    fireEvent.change(screen.getByLabelText('Contraseña'), {
      target: { value: 'clave-larga-123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => expect(asignar).toHaveBeenCalledWith('/admin'));
  });

  it('conserva el destino después de exigir el cambio de la clave inicial', async () => {
    const asignar = vi.fn();
    Object.defineProperty(window, 'location', {
      value: { assign: asignar },
      writable: true,
    });
    let entradas = 0;
    globalThis.fetch = vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.includes('/api/admin/login')) {
        entradas += 1;
        const estado = entradas === 1 ? 'cambio_requerido' : 'autenticado';
        return new Response(JSON.stringify({ estado }), { status: 200 });
      }
      if (ruta.includes('/api/admin/password')) return new Response('{}', { status: 200 });
      return new Response('{"error":"sin sesión"}', { status: 401 });
    }) as unknown as typeof fetch;
    render(
      <MemoryRouter initialEntries={[{
        pathname: '/admin/entrar',
        state: { volverA: '/admin/hotel?seccion=contacto' },
      }]}>
        <PaginaLoginAdmin />
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'admin@hotel.test' },
    });
    fireEvent.change(screen.getByLabelText('Contraseña'), {
      target: { value: 'clave-inicial-123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }));
    await screen.findByRole('heading', { name: 'Cambia tu contraseña' });
    fireEvent.change(screen.getByLabelText('Nueva contraseña'), {
      target: { value: 'clave-nueva-segura-123' },
    });
    fireEvent.change(screen.getByLabelText('Confírmala'), {
      target: { value: 'clave-nueva-segura-123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar y entrar' }));

    await waitFor(() => {
      expect(asignar).toHaveBeenCalledWith('/admin/hotel?seccion=contacto');
    });
  });
});
