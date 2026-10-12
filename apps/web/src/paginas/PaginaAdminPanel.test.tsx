import { describe, expect, it, vi, afterEach } from 'vitest';
import { act, render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PaginaAdminPanel } from './PaginaAdminPanel';

describe('la web pública sin salida', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  /**
   * Monta el panel. `preparacion` es lo que devuelve `/api/admin/preparacion`: la única
   * lista de pendientes, que el servidor calcula de su propia base.
   */
  function Montar(preparacion: { clave: string; titulo: string; hecho: boolean; url: string | null }[]) {
    globalThis.fetch = vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.includes('/api/admin/sesion')) {
        return new Response(JSON.stringify({ email: 'dueno@hotel.test', nombre: 'Dueño' }), {
          status: 200,
        });
      }
      if (ruta.includes('/api/admin/mensajes/nuevos')) {
        return new Response(JSON.stringify({ nuevos: 0 }), { status: 200 });
      }
      if (ruta.includes('/api/admin/preparacion')) {
        return new Response(JSON.stringify({ items: preparacion }), { status: 200 });
      }
      return new Response('{}', { status: 404 });
    }) as unknown as typeof fetch;
    render(
      <MemoryRouter>
        <PaginaAdminPanel />
      </MemoryRouter>,
    );
  }

  const PENDIENTES = [
    { clave: 'habitaciones', titulo: 'Habitaciones registradas', hecho: false, url: '/admin/inventario' },
    { clave: 'tarifas', titulo: 'Precios publicados a futuro', hecho: false, url: '/admin/inventario' },
    { clave: 'hotel', titulo: 'Nombre y datos del hotel', hecho: true, url: '/admin/hotel' },
    { clave: 'ubicacion', titulo: 'Ubicación en el mapa', hecho: false, url: '/admin/hotel' },
    { clave: 'contacto', titulo: 'Contacto para los huéspedes', hecho: false, url: '/admin/hotel' },
    { clave: 'lugares', titulo: 'Sitios cercanos en el mapa', hecho: false, url: '/admin/lugares' },
    { clave: 'respaldo', titulo: 'Respaldo reciente', hecho: false, url: null },
  ];

  it('una sola lista de pendientes, con su enlace cada uno', async () => {
    // El panel tenía dos: "tu web tiene 3 cosas pendientes" y "pon tu hotel a punto (5
    // pendientes)". Se solapaban —GPS y habitaciones salían en las dos— y los contadores no
    // coincidian. Ahora es una, calculada por el servidor de su propia base.
    Montar(PENDIENTES);

    await waitFor(() => {
      expect(screen.getByText(/pon tu hotel a punto/i)).toBeTruthy();
    });
    // Siete puntos y uno ya hecho: seis pendientes en pantalla.
    expect(screen.getAllByRole('listitem')).toHaveLength(6);
    expect(screen.getByRole('link', { name: /ubicación en el mapa/i })).toBeTruthy();
    expect(screen.getByRole('link', { name: /habitaciones registradas/i })).toBeTruthy();
    expect(screen.getByRole('link', { name: /contacto para los huéspedes/i })).toBeTruthy();
    // El aviso repetido ya no existe.
    expect(screen.queryByText(/cosas pendientes/i)).toBeNull();
  });

  it('no muestra nada cuando no queda pendiente', async () => {
    Montar(PENDIENTES.map((p) => ({ ...p, hecho: true })));

    await screen.findByRole('heading', { name: 'Panel del hotel' });
    expect(screen.queryByRole('listitem')).toBeNull();
    expect(screen.queryByText(/pon tu hotel a punto/i)).toBeNull();
  });

  it('el contacto ya no se calcula en el navegador', async () => {
    // Antes el panel pedía /api/admin/tipos, /api/hotel y /api/lugares solo para deducir
    // tres de los pendientes. Esas peticiones ya no se hacen: la lista viene del servidor.
    const pedidas: string[] = [];
    globalThis.fetch = vi.fn(async (url: unknown) => {
      pedidas.push(String(url));
      const ruta = String(url);
      if (ruta.includes('/api/admin/sesion')) {
        return new Response(JSON.stringify({ email: 'dueno@hotel.test', nombre: 'Dueño' }), { status: 200 });
      }
      if (ruta.includes('/api/admin/preparacion')) {
        return new Response(JSON.stringify({ items: PENDIENTES }), { status: 200 });
      }
      return new Response('{}', { status: 404 });
    }) as unknown as typeof fetch;
    render(
      <MemoryRouter>
        <PaginaAdminPanel />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText(/pon tu hotel a punto/i)).toBeTruthy();
    });
    expect(pedidas.some((r) => r.includes('/api/admin/tipos'))).toBe(false);
    expect(pedidas.some((r) => r.includes('/api/lugares'))).toBe(false);
  });
});

describe('PaginaAdminPanel', () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('el admin ajusta su nombre visible y recarga con el saludo fresco', async () => {
    const recargar = vi.fn();
    Object.defineProperty(window, 'location', {
      value: { assign: vi.fn(), reload: recargar },
      writable: true,
    });
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
    await waitFor(() => {
      expect(recargar).toHaveBeenCalled();
    });
  });

  it('anuncia al lector de pantalla los mensajes nuevos al actualizarse', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    let nuevos = 1;
    globalThis.fetch = vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.includes('/api/admin/sesion')) {
        return new Response(JSON.stringify({ email: 'admin@hotel.test', nombre: 'A' }), { status: 200 });
      }
      if (ruta.includes('/api/admin/mensajes/nuevos')) {
        return new Response(JSON.stringify({ nuevos }), { status: 200 });
      }
      return new Response('{}', { status: 404 });
    }) as unknown as typeof fetch;
    render(
      <MemoryRouter>
        <PaginaAdminPanel />
      </MemoryRouter>,
    );

    await screen.findByText('1 mensaje sin leer');
    expect(screen.getByRole('status').textContent).toBe('1 mensaje sin leer');
    nuevos = 4;
    await act(async () => { await vi.advanceTimersByTimeAsync(30_000); });

    expect(screen.getByRole('status').textContent).toBe('4 mensajes sin leer');
  });

  it('llama "habitaciones" a la puerta y a su botón, no "inventario"', async () => {
    // "Inventario" es jerga de gestión de ingresos. Quien regenta un hotel busca
    // "habitaciones": si la puerta y el destino no se llaman igual, no los relaciona.
    globalThis.fetch = vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.includes('/api/admin/sesion')) {
        return new Response(JSON.stringify({ email: 'dueno@hotel.test', nombre: 'Dueño' }), {
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

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'Habitaciones' })).toBeTruthy(),
    );
    // En toda la página no debe quedar ni un "inventario": es otra palabra para lo mismo.
    expect(screen.queryAllByText(/inventario/i)).toHaveLength(0);
  });
});
