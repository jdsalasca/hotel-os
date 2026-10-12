import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PaginaAdminIntegraciones } from './PaginaAdminIntegraciones';

const estado = vi.hoisted(() => ({
  canales: {} as Record<string, unknown>,
  planes: [] as unknown,
  habitaciones: [] as unknown,
  tipos: [] as unknown,
}));

vi.mock('../api/cliente', () => ({
  api: {
    get: (url: string) => {
      if (url === '/api/admin/integraciones') return Promise.resolve(estado.canales);
      if (url === '/api/admin/planes') return Promise.resolve(estado.planes);
      if (url === '/api/admin/habitaciones') return Promise.resolve(estado.habitaciones);
      if (url === '/api/admin/tipos') return Promise.resolve(estado.tipos);
      return Promise.resolve({});
    },
    post: () => Promise.resolve({}),
  },
  urlApi: (ruta: string) => ruta,
}));

vi.mock('../api/useSesion', () => ({
  useSesion: () => ({ haySesion: true, email: 'hotel@eridu.co', nombre: 'Hotel Eridu' }),
}));

function montar() {
  render(
    <MemoryRouter>
      <PaginaAdminIntegraciones />
    </MemoryRouter>,
  );
}

afterEach(() => {
  cleanup();
  estado.canales = {};
  estado.planes = [];
  estado.habitaciones = [];
  estado.tipos = [];
});

describe('integraciones con respuesta parcial', () => {
  /** La pantalla está viva si pinta su encabezado y el nombre del canal. */
  async function esperarViva() {
    await screen.findByRole('heading', { name: /integraciones/i });
    expect(screen.getByText(/booking/i)).toBeTruthy();
    // Con el árbol vacío la aserción de arriba ya habría fallado; esto lo hace explícito.
    expect(document.body.textContent?.length ?? 0).toBeGreaterThan(200);
  }

  it('un canal sin sus listas no tumba la pantalla', async () => {
    // La página hacía `canal.variablesFaltantes.length` y
    // `canal.requisitosPendientes.length` dando por hechas sus listas. Es justo el estado
    // NO_CONFIGURADO de producción, así que no es un caso raro.
    estado.canales = {
      BOOKING: { canal: 'BOOKING', estado: 'NO_CONFIGURADO', entorno: 'sandbox' },
    };
    montar();
    await esperarViva();
  });

  it('un cuerpo totalmente vacío también la deja viva', async () => {
    estado.canales = { BOOKING: { canal: 'BOOKING', estado: 'NO_CONFIGURADO' } };
    montar();
    await esperarViva();
  });

  it('listas que no son arrays no rompen el selector de recursos', async () => {
    // `setPlanes(planesCargados)` guardaba lo que llegara. Con un cuerpo que no es array,
    // `planes.map` reventaba más abajo, en el formulario de mapeos.
    estado.canales = {
      BOOKING: {
        canal: 'BOOKING',
        estado: 'NO_CONFIGURADO',
        variablesFaltantes: [],
        requisitosPendientes: [],
      },
    };
    estado.planes = { no: 'soy un objeto' };
    estado.habitaciones = { no: 'soy un objeto' };
    estado.tipos = { no: 'soy un objeto' };
    montar();
    await esperarViva();
  });
});
