import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { App } from './App';

describe('App', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('al navegar se vuelve arriba, no a mitad de página', async () => {
    const subir = vi.fn();
    window.scrollTo = subir as unknown as typeof window.scrollTo;
    globalThis.fetch = vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.includes('/api/hotel')) return new Response('{}', { status: 200 });
      if (ruta.includes('/api/lugares')) return new Response('{"hotel":{},"lugares":[]}', { status: 200 });
      if (ruta.includes('/api/amenidades')) return new Response('{}', { status: 200 });
      if (ruta.includes('/api/disponibilidad')) {
        return new Response(JSON.stringify({ dias: [] }), { status: 200 });
      }
      return new Response('{"error":"sin sesión"}', { status: 401 });
    }) as unknown as typeof fetch;
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByText('¿Cuándo quieres venir?');
    subir.mockClear();
    fireEvent.click(screen.getByRole('link', { name: 'Mis reservas' }));
    await waitFor(() => {
      expect(subir).toHaveBeenCalledWith(0, 0);
    });
    expect(await screen.findByRole('heading', { name: 'Mis reservas' })).toBeTruthy();
  });

  it('abre la navegación compacta y la cierra después de elegir un destino', async () => {
    window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;
    globalThis.fetch = vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.includes('/api/hotel')) return new Response('{}', { status: 200 });
      if (ruta.includes('/api/admin/sesion') || ruta.includes('/api/yo')) {
        return new Response('{}', { status: 401 });
      }
      if (ruta.includes('/api/disponibilidad/calendario')) {
        return new Response('{"dias":[]}', { status: 200 });
      }
      if (ruta.includes('/api/lugares')) return new Response('{"hotel":{},"lugares":[]}', { status: 200 });
      return new Response('{}', { status: 200 });
    }) as unknown as typeof fetch;

    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByText('¿Cuándo quieres venir?');

    fireEvent.click(screen.getByRole('button', { name: 'Abrir menú principal' }));
    expect(screen.getByRole('button', { name: 'Cerrar menú principal' }).getAttribute('aria-expanded')).toBe('true');

    fireEvent.click(screen.getByRole('link', { name: 'Mis reservas' }));
    await screen.findByRole('heading', { name: 'Mis reservas' });
    expect(await screen.findByRole('link', { name: 'Entrar con Google' })).toBeTruthy();
    expect(await screen.findByRole('link', { name: 'Consultar una reserva' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Abrir menú principal' }).getAttribute('aria-expanded')).toBe('false');

    fireEvent.click(screen.getByRole('button', { name: 'Abrir menú principal' }));
    fireEvent.click(screen.getByRole('link', { name: 'Mis reservas' }));
    expect(screen.getByRole('button', { name: 'Abrir menú principal' }).getAttribute('aria-expanded')).toBe('false');
  });

  it('cierra la navegación compacta con Escape y devuelve el foco al botón', async () => {
    window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;
    globalThis.fetch = vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.includes('/api/hotel')) return new Response('{}', { status: 200 });
      if (ruta.includes('/api/admin/sesion') || ruta.includes('/api/yo')) {
        return new Response('{}', { status: 401 });
      }
      if (ruta.includes('/api/disponibilidad/calendario')) {
        return new Response('{"dias":[]}', { status: 200 });
      }
      if (ruta.includes('/api/lugares')) return new Response('{"hotel":{},"lugares":[]}', { status: 200 });
      return new Response('{}', { status: 200 });
    }) as unknown as typeof fetch;

    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByText('¿Cuándo quieres venir?');

    const boton = screen.getByRole('button', { name: 'Abrir menú principal' });
    fireEvent.click(boton);
    const enlace = screen.getByRole('link', { name: 'Mis reservas' });
    enlace.focus();
    fireEvent.keyDown(enlace, { key: 'Escape' });

    expect(screen.getByRole('button', { name: 'Abrir menú principal' }).getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(boton);
  });

  it('usa un solo acceso del huésped y lo mantiene activo al consultar por código', async () => {
    window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;
    globalThis.fetch = vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.includes('/api/hotel')) return new Response('{}', { status: 200 });
      if (ruta.includes('/api/admin/sesion') || ruta.includes('/api/yo')) {
        return new Response('{}', { status: 401 });
      }
      if (ruta.includes('/api/lugares')) return new Response('{"hotel":{},"lugares":[]}', { status: 200 });
      if (ruta.includes('/api/hotel/venta')) return new Response('{"a_la_venta":true}', { status: 200 });
      if (ruta.includes('/api/disponibilidad/calendario')) return new Response('{"dias":[]}', { status: 200 });
      return new Response('{}', { status: 200 });
    }) as unknown as typeof fetch;

    const { container } = render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByText('¿Cuándo quieres venir?');
    const nav = container.querySelector('header nav[aria-label="Navegación principal"]');
    const enlacesHuesped = nav?.querySelectorAll('a[href="/mis-reservas"]');
    expect(enlacesHuesped).toHaveLength(1);
    expect(nav?.querySelector('a[href="/consulta"]')).toBeNull();

    fireEvent.click(enlacesHuesped![0]!);
    expect(await screen.findByRole('heading', { name: 'Mis reservas' })).toBeTruthy();
    expect(await screen.findByRole('link', { name: 'Entrar con Google' })).toBeTruthy();
    expect(await screen.findByRole('link', { name: 'Consultar una reserva' })).toBeTruthy();

    cleanup();
    render(
      <MemoryRouter initialEntries={['/consulta']}>
        <App />
      </MemoryRouter>,
    );
    const acceso = await screen.findByRole('link', { name: /Mis reservas/ });
    expect(acceso.getAttribute('aria-current')).toBe('page');
    expect(await screen.findByRole('heading', { name: 'Consultar una reserva' })).toBeTruthy();
  });

  it('sin sesión ofrece entrada al panel y oculta sus rutas protegidas', async () => {
    window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;
    globalThis.fetch = vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.includes('/api/admin/sesion') || ruta.includes('/api/yo')) {
        return new Response('{}', { status: 401 });
      }
      if (ruta.includes('/api/hotel')) return new Response('{}', { status: 200 });
      return new Response('{}', { status: 200 });
    }) as unknown as typeof fetch;

    const { container } = render(
      <MemoryRouter initialEntries={['/admin']}>
        <App />
      </MemoryRouter>,
    );

    const nav = container.querySelector('header nav[aria-label="Navegación administrativa"]');
    await waitFor(() => {
      expect(nav?.querySelector('a[href="/admin/entrar"]')?.textContent).toBe('Iniciar sesión');
    });
    expect(nav?.querySelector('a[href="/admin/reservas"]')).toBeNull();
    expect(nav?.querySelector('a[href="/admin/inventario"]')).toBeNull();
    expect(nav?.querySelector('a[href="/"]')?.textContent).toContain('Reservar');
  });

  it('con sesión restaura la navegación completa del panel', async () => {
    window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;
    globalThis.fetch = vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.includes('/api/admin/sesion')) {
        return new Response('{"email":"admin@hotel.test","nombre":"Ana"}', { status: 200 });
      }
      if (ruta.includes('/api/hotel')) return new Response('{}', { status: 200 });
      return new Response('{"error":"sin sesión"}', { status: 401 });
    }) as unknown as typeof fetch;

    const { container } = render(
      <MemoryRouter initialEntries={['/admin']}>
        <App />
      </MemoryRouter>,
    );
    const nav = container.querySelector('header nav[aria-label="Navegación administrativa"]');
    await waitFor(() => expect(nav?.querySelector('a[href="/admin/inventario"]')).not.toBeNull());
    expect(nav?.querySelector('a[href="/admin/reservas"]')).not.toBeNull();
    expect(nav?.querySelector('a[href="/admin/entrar"]')).toBeNull();
    // El dueño también es visitante: sin salida al sitio público no puede reservar ni
    // consultar sus reservas desde el panel.
    expect(nav?.querySelector('a[href="/"]')?.textContent).toContain('Reservar');
    expect(nav?.querySelector('a[href="/mis-reservas"]')).not.toBeNull();
  });

  it('el pie esconde los accesos del panel sin sesión de personal', async () => {
    window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;
    globalThis.fetch = vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.includes('/api/hotel')) return new Response('{}', { status: 200 });
      return new Response('{"error":"sin sesión"}', { status: 401 });
    }) as unknown as typeof fetch;
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByText('¿Cuándo quieres venir?');
    await waitFor(() => {
      expect(document.querySelector('footer nav[aria-label="Navegación del panel"]')).toBeNull();
    });
    expect(
      document.querySelector('footer nav[aria-label="Información legal"]'),
    ).not.toBeNull();
  });

  it('el pie muestra los accesos con sesión de personal', async () => {
    window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;
    globalThis.fetch = vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.includes('/api/admin/sesion')) {
        return new Response(JSON.stringify({ email: 'a@h.test', nombre: 'A' }), { status: 200 });
      }
      if (ruta.includes('/api/hotel')) return new Response('{}', { status: 200 });
      return new Response('{"error":"sin sesión"}', { status: 401 });
    }) as unknown as typeof fetch;
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByText('¿Cuándo quieres venir?');
    await waitFor(() => {
      const nav = document.querySelector('footer nav[aria-label="Navegación del panel"]');
      expect(nav?.textContent).toContain('Inventario');
    });
  });

  it('el huésped también puede cerrar sesión desde la cabecera', async () => {
    window.scrollTo = vi.fn() as unknown as typeof window.scrollTo;
    const hechos: string[] = [];
    globalThis.fetch = vi.fn(async (url: unknown, opciones?: RequestInit) => {
      const ruta = String(url);
      if (ruta.includes('/api/admin/sesion')) {
        return new Response('{"error":"sin sesión"}', { status: 401 });
      }
      if (ruta.includes('/api/yo')) {
        return new Response(
          JSON.stringify({ email: 'huesped@ejemplo.test', nombre: 'Ana', tieneReservas: false }),
          { status: 200 },
        );
      }
      if (ruta.includes('/api/huesped/logout')) {
        hechos.push(`${opciones?.method ?? 'GET'} ${ruta}`);
        return new Response('{"estado":"sesion cerrada"}', { status: 200 });
      }
      if (ruta.includes('/api/lugares')) {
        return new Response('{"hotel":{},"lugares":[]}', { status: 200 });
      }
      if (ruta.includes('/api/hotel')) return new Response('{}', { status: 200 });
      return new Response('{}', { status: 200 });
    }) as unknown as typeof fetch;
    const asignar = vi.fn();
    Object.defineProperty(window, 'location', {
      value: { assign: asignar },
      writable: true,
      configurable: true,
    });

    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByText('¿Cuándo quieres venir?');
    const boton = await screen.findByRole('button', { name: 'Cerrar sesión' });
    fireEvent.click(boton);

    await waitFor(() => {
      expect(asignar).toHaveBeenCalledWith('/');
    });
    expect(hechos).toEqual(['POST /api/huesped/logout']);
  });
});
