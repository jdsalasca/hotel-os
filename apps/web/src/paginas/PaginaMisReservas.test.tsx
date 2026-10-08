import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PaginaMisReservas } from './PaginaMisReservas';

/**
 * El huésped mueve sus fechas sin llamar: el formulario por fila envía al
 * endpoint propio y la lista se refresca con lo guardado de verdad.
 */
type Fila = {
  codigo: string;
  llegada: string;
  salida: string;
  huespedes: number;
  estado: string;
  creado_en: string;
  total_cents: number;
  moneda: string;
  abonado_cents: number;
  pendiente_cents: number;
};

const estado = vi.hoisted(() => ({
  postes: [] as { url: string; cuerpo: unknown }[],
  fallaMover: false,
  lista: [] as Fila[],
}));

vi.mock('../api/cliente', () => ({
  api: {
    get: (url: string) => {
      if (url === '/api/mis-reservas') return Promise.resolve({ reservas: estado.lista });
      if (url === '/api/mis-reservas/mensajes/nuevos') {
        return Promise.resolve({ nuevos: 0, porReserva: {} });
      }
      throw new Error('get no esperado: ' + url);
    },
    post: (url: string, cuerpo: unknown) => {
      estado.postes.push({ url, cuerpo });
      if (estado.fallaMover && url.endsWith('/fechas')) {
        return Promise.reject(new Error('esas fechas no están libres para esa habitación'));
      }
      const q = cuerpo as { llegada: string; salida: string };
      const r = estado.lista.find((f) => url.includes(f.codigo));
      if (r) {
        r.llegada = q.llegada;
        r.salida = q.salida;
      }
      return Promise.resolve({ codigo: 'H-MIA1', llegada: q.llegada, salida: q.salida });
    },
  },
  urlApi: (ruta: string) => ruta,
}));

function sesionConectada() {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: unknown) =>
      String(url).endsWith('/api/yo')
        ? new Response(
            JSON.stringify({ email: 'yo@hotel.test', nombre: 'Yo', tieneReservas: true }),
            { status: 200 },
          )
        : new Response(JSON.stringify({}), { status: 404 }),
    ),
  );
}

function filaInicial(): Fila {
  return {
    codigo: 'H-MIA1',
    llegada: '2030-06-10',
    salida: '2030-06-12',
    huespedes: 1,
    estado: 'PENDIENTE',
    creado_en: '2030-01-01',
    total_cents: 300000,
    moneda: 'COP',
    abonado_cents: 0,
    pendiente_cents: 300000,
  };
}

afterEach(() => {
  cleanup();
  estado.postes.length = 0;
  estado.fallaMover = false;
  estado.lista.length = 0;
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function sembrar() {
  estado.lista.length = 0;
  estado.lista.push(filaInicial());
}

async function montar() {
  sesionConectada();
  render(
    <MemoryRouter>
      <PaginaMisReservas />
    </MemoryRouter>,
  );
  await waitFor(() => {
    expect(screen.getByText('H-MIA1')).toBeTruthy();
  });
}

describe('cambio de fechas propio', () => {
  it('mueve las fechas y refresca la fila con lo guardado', async () => {
    sembrar();
    await montar();
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar fechas' }));
    fireEvent.change(screen.getByLabelText('Nueva llegada'), {
      target: { value: '2030-06-13' },
    });
    fireEvent.change(screen.getByLabelText('Nueva salida'), {
      target: { value: '2030-06-15' },
    });
    fireEvent.click(screen.getByText('Guardar fechas'));

    await waitFor(() => {
      expect(estado.postes.length).toBe(1);
    });
    expect(estado.postes[0]!.url).toBe('/api/mis-reservas/H-MIA1/fechas');
    expect(estado.postes[0]!.cuerpo).toMatchObject({
      llegada: '2030-06-13',
      salida: '2030-06-15',
    });
    await waitFor(() => {
      expect(screen.queryByText('2030-06-13')).not.toBeNull();
    });
  });

  it('si el servidor lo rechaza, avisa y deja las fechas como estaban', async () => {
    estado.fallaMover = true;
    sembrar();
    await montar();
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar fechas' }));
    fireEvent.change(screen.getByLabelText('Nueva llegada'), {
      target: { value: '2030-06-13' },
    });
    fireEvent.change(screen.getByLabelText('Nueva salida'), {
      target: { value: '2030-06-15' },
    });
    fireEvent.click(screen.getByText('Guardar fechas'));

    await waitFor(() => {
      expect(screen.queryByText(/esas fechas no están libres/)).not.toBeNull();
    });
    expect(screen.queryByText('2030-06-10')).not.toBeNull();
  });
});
