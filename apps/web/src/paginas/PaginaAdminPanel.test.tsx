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
   * Monta el panel con las respuestas que se le indiquen. `tipos` es lo que devuelve
   * `/api/admin/tipos`: vacío significa que no hay nada que vender.
   */
  function Montar(hotel: Record<string, string>, tipos: unknown[] = [{ id: 1 }]) {
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
      if (ruta.includes('/api/admin/tipos')) {
        return new Response(JSON.stringify(tipos), { status: 200 });
      }
      if (ruta.includes('/api/hotel')) {
        return new Response(JSON.stringify(hotel), { status: 200 });
      }
      return new Response('{}', { status: 404 });
    }) as unknown as typeof fetch;
    render(
      <MemoryRouter>
        <PaginaAdminPanel />
      </MemoryRouter>,
    );
  }

  it('avisa en el panel que los huéspedes no tienen forma de contactar al hotel', async () => {
    Montar({ nombre: 'Hotel Eridu', contacto_email: '', contacto_telefono: '' });

    await waitFor(() => {
      expect(screen.getByText(/los huéspedes no pueden contactarte/i)).toBeTruthy();
    });
    // Y dice por dónde se arregla, para que el aviso no sea un reproche sin salida.
    expect(screen.getByRole('link', { name: /pon tu contacto/i })).toBeTruthy();
  });

  it('no avisa cuando ya hay correo o teléfono', async () => {
    Montar({ nombre: 'Hotel Eridu', contacto_email: 'hola@eridu.top' });

    await screen.findByRole('heading', { name: 'Panel del hotel' });
    expect(screen.queryByText(/los huéspedes no pueden contactarte/i)).toBeNull();
  });

  it('si la lectura del hotel falla, no inventa el aviso', async () => {
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
      if (ruta.includes('/api/admin/tipos')) {
        return new Response(JSON.stringify([{ id: 1 }]), { status: 200 });
      }
      return new Response('', { status: 500 });
    }) as unknown as typeof fetch;
    render(
      <MemoryRouter>
        <PaginaAdminPanel />
      </MemoryRouter>,
    );

    await screen.findByRole('heading', { name: 'Panel del hotel' });
    expect(screen.queryByText(/los huéspedes no pueden contactarte/i)).toBeNull();
  });

  it('avisa que la web no puede vender mientras no haya ningún tipo de habitación', async () => {
    // Sin tipos no hay nada que ofrecer: la portada no muestra disponibilidad y quien
    // llega a reservar se topa con el estado vacío. El hotelero tiene que saberlo aquí.
    Montar({ nombre: 'Hotel Eridu', contacto_email: 'hola@eridu.top' }, []);

    await waitFor(() => {
      expect(screen.getByText(/tu web no puede vender/i)).toBeTruthy();
    });
    expect(screen.getByRole('link', { name: /crear habitaciones/i })).toBeTruthy();
  });

  it('no avisa de venta cuando ya hay tipos creados', async () => {
    Montar({ nombre: 'Hotel Eridu', contacto_email: 'hola@eridu.top' }, [{ id: 1 }]);

    await screen.findByRole('heading', { name: 'Panel del hotel' });
    expect(screen.queryByText(/tu web no puede vender/i)).toBeNull();
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
