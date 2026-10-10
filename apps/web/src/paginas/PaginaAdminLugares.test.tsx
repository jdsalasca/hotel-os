import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PaginaAdminLugares } from './PaginaAdminLugares';

type LugarFixture = {
  id: number;
  nombre: string;
  descripcion: string;
  latitud: number;
  longitud: number;
  categoria: 'COMER' | 'VISITAR' | 'ALOJARSE';
  activo: boolean;
  metros?: number;
};

const estado = vi.hoisted(() => ({
  lugares: [
    {
      id: 7,
      nombre: 'Mirador del valle',
      descripcion: 'Vista sobre el valle',
      latitud: 5.635,
      longitud: -73.525,
      categoria: 'VISITAR',
      activo: true,
    },
  ] as LugarFixture[],
  cambios: [] as {
    ruta: string;
    cuerpo: {
      nombre: string;
      descripcion: string;
      latitud: number;
      longitud: number;
      categoria: 'COMER' | 'VISITAR' | 'ALOJARSE';
      activo: boolean;
    };
  }[],
  creaciones: 0,
  lugaresCreados: [] as {
    nombre: string;
    descripcion: string;
    latitud: number;
    longitud: number;
    categoria: 'COMER' | 'VISITAR' | 'ALOJARSE';
  }[],
  // Configuración del hotel tal cual la lee el panel: `latitud`/`longitud` vacíos es el caso
  // real en producción, donde el mapa no se publica aunque haya lugares creados.
  hotelConfig: { nombre: 'Hotel Eridu', latitud: '5.65', longitud: '-73.52' },
}));

vi.mock('../api/cliente', () => ({
  api: {
    get: async (ruta: string) => {
      if (ruta === '/api/admin/lugares') return { lugares: estado.lugares };
      if (ruta === '/api/admin/hotel-config') return estado.hotelConfig;
      throw new Error(`ruta no esperada: ${ruta}`);
    },
    post: async (_ruta: string, cuerpo: (typeof estado.lugaresCreados)[number]) => {
      estado.creaciones++;
      estado.lugaresCreados.push(cuerpo);
      estado.lugares = [...estado.lugares, { id: 8, ...cuerpo, activo: true }];
      return {};
    },
    put: async (ruta: string, cuerpo: (typeof estado.cambios)[number]['cuerpo']) => {
      estado.cambios.push({ ruta, cuerpo });
      const id = Number(ruta.split('/').at(-1));
      estado.lugares = estado.lugares.map((lugar) =>
        lugar.id === id ? { ...lugar, ...cuerpo } : lugar,
      );
      return {};
    },
    del: async () => ({}),
  },
  urlApi: (ruta: string) => ruta,
}));

function conectarAdmin() {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: unknown) =>
      String(url).endsWith('/api/admin/sesion')
        ? new Response(JSON.stringify({ email: 'admin@hotel.test', nombre: 'Admin' }), { status: 200 })
        : new Response(JSON.stringify({}), { status: 404 }),
    ),
  );
}

async function montar() {
  conectarAdmin();
  render(
    <MemoryRouter>
      <PaginaAdminLugares />
    </MemoryRouter>,
  );
  await waitFor(() => expect(screen.getByRole('row', { name: /Mirador del valle/ })).toBeTruthy());
}

afterEach(() => {
  cleanup();
  estado.lugares = [
    {
      id: 7,
      nombre: 'Mirador del valle',
      descripcion: 'Vista sobre el valle',
      latitud: 5.635,
      longitud: -73.525,
      categoria: 'VISITAR',
      activo: true,
    },
  ];
  estado.cambios.length = 0;
  estado.creaciones = 0;
  estado.lugaresCreados.length = 0;
  estado.hotelConfig = { nombre: 'Hotel Eridu', latitud: '5.65', longitud: '-73.52' };
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('edición de lugares del mapa', () => {
  it('permite clasificar y guardar un lugar nuevo', async () => {
    await montar();
    fireEvent.change(screen.getByLabelText('Categoría'), { target: { value: 'COMER' } });
    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'Café del pueblo' } });
    fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: 'Café local' } });
    fireEvent.change(screen.getByLabelText('Latitud'), { target: { value: '5.63' } });
    fireEvent.change(screen.getByLabelText('Longitud'), { target: { value: '-73.52' } });
    fireEvent.click(screen.getByRole('button', { name: 'Agregar lugar' }));

    expect(await screen.findByRole('row', { name: /Café del pueblo.*Qué comer/ })).toBeTruthy();
    expect(estado.lugaresCreados).toEqual([{
      nombre: 'Café del pueblo',
      descripcion: 'Café local',
      latitud: 5.63,
      longitud: -73.52,
      categoria: 'COMER',
    }]);
  });

  it('avisa que el mapa no saldrá si el hotel no tiene su punto en el GPS', async () => {
    // Situación real en producción: el hotel no está ubicado, pero el hotelero ya creó
    // lugares. Sin este aviso guarda todo y la web sigue sin mostrar nada, sin explicación.
    estado.hotelConfig = { nombre: 'Hotel Eridu', latitud: '', longitud: '' };
    estado.lugares = [
      {
        id: 7,
        nombre: 'Mirador',
        descripcion: '',
        latitud: 5.635,
        longitud: -73.525,
        categoria: 'VISITAR',
        activo: true,
      },
    ];
    conectarAdmin();
    render(
      <MemoryRouter>
        <PaginaAdminLugares />
      </MemoryRouter>,
    );

    expect(await screen.findByText(/el mapa no aparece/i)).toBeTruthy();
  });

  it('el panel dice a qué distancia queda cada sitio del hotel', async () => {
      estado.lugares = [
        { id: 7, nombre: 'Al lado', descripcion: '', latitud: 5.635, longitud: -73.525, categoria: 'VISITAR', activo: true, metros: 240 },
        { id: 8, nombre: 'Lejos', descripcion: '', latitud: -33.45, longitud: -70.67, categoria: 'ALOJARSE', activo: true, metros: 3450000 },
      ];
      conectarAdmin();
      render(
        <MemoryRouter>
          <PaginaAdminLugares />
        </MemoryRouter>,
      );

    // metros exactos bajo 1 km, y redondeado a un decimal por encima.
    expect(await screen.findByText('240 m')).toBeTruthy();
    expect(await screen.findByText('3.450 km')).toBeTruthy();

    estado.lugares = [
      { id: 7, nombre: 'Mirador del valle', descripcion: 'Vista sobre el valle', latitud: 5.635, longitud: -73.525, categoria: 'VISITAR', activo: true },
    ];
  });

  it('guarda los cambios del sitio elegido con PUT y actualiza la lista', async () => {
    await montar();
    const fila = screen.getByRole('row', { name: /Mirador del valle/ });

    fireEvent.click(within(fila).getByRole('button', { name: 'Editar Mirador del valle' }));
    expect(document.activeElement).toBe(screen.getByLabelText('Nombre'));
    expect((screen.getByLabelText('Categoría') as HTMLSelectElement).value).toBe('VISITAR');
    fireEvent.change(screen.getByLabelText('Categoría'), { target: { value: 'ALOJARSE' } });
    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'Mirador del valle alto' } });
    fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: 'Vista desde arriba' } });
    fireEvent.change(screen.getByLabelText('Latitud'), { target: { value: '5.64' } });
    fireEvent.change(screen.getByLabelText('Longitud'), { target: { value: '-73.53' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    await waitFor(() => expect(estado.cambios).toHaveLength(1));
    expect(estado.cambios[0]).toEqual({
      ruta: '/api/admin/lugares/7',
      cuerpo: {
        nombre: 'Mirador del valle alto',
        descripcion: 'Vista desde arriba',
        latitud: 5.64,
        longitud: -73.53,
        categoria: 'ALOJARSE',
        activo: true,
      },
    });
    expect(estado.creaciones).toBe(0);
    expect(screen.getByRole('row', { name: /Mirador del valle alto/ })).toBeTruthy();
  });

  it('cancela la edición y conserva intacto el lugar', async () => {
    await montar();
    const fila = screen.getByRole('row', { name: /Mirador del valle/ });

    fireEvent.click(within(fila).getByRole('button', { name: 'Editar Mirador del valle' }));
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar edición' }));

    expect(screen.getByRole('heading', { name: 'Nuevo lugar' })).toBeTruthy();
    expect((screen.getByLabelText('Nombre') as HTMLInputElement).value).toBe('');
    expect(screen.getByRole('row', { name: /Mirador del valle/ })).toBeTruthy();
    expect(estado.cambios).toHaveLength(0);
  });
});
