import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PaginaAdminHoy } from './PaginaAdminHoy';

/**
 * Recepción abre esta pantalla para consultar movimientos y saldo en un solo lugar, sin abrir
 * cada reserva por separado.
 */
type Fila = {
  codigo: string;
  email: string;
  nombre: string;
  huespedes: number;
  habitacion: string;
  abonadoCents?: number;
  pendienteCents?: number;
  moneda?: string;
};

const estado = vi.hoisted(() => ({
  llegadas: [] as Fila[],
  salidas: [] as Fila[],
}));

vi.mock('../api/cliente', () => ({
  api: {
    get: (url: string) => {
      if (url.startsWith('/api/admin/ocupacion/dia')) {
        return Promise.resolve({
          fecha: '2030-06-10',
          llegadas: estado.llegadas,
          salidas: estado.salidas,
          enCasa: [],
        });
      }
      throw new Error('get no esperado: ' + url);
    },
    post: () => Promise.resolve({}),
  },
  urlApi: (ruta: string) => ruta,
}));

function sesionAdmin() {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: unknown) =>
      String(url).endsWith('/api/admin/sesion')
        ? new Response(JSON.stringify({ email: 'a@hotel.test', nombre: 'A' }), { status: 200 })
        : new Response(JSON.stringify({}), { status: 404 }),
    ),
  );
}

async function montar() {
  sesionAdmin();
  render(<MemoryRouter><PaginaAdminHoy /></MemoryRouter>);
  await screen.findByText('Hoy en el hotel');
}

afterEach(() => {
  cleanup();
  estado.llegadas = [];
  estado.salidas = [];
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('saldo en el parte de recepción', () => {
  it('una llegada con saldo pendiente muestra cuánto falta por cobrar', async () => {
    estado.llegadas = [{
      codigo: 'H-DEBE', email: 'debe@ejemplo.co', nombre: 'Ana Debe', huespedes: 2,
      habitacion: '101', abonadoCents: 10000, pendienteCents: 290000, moneda: 'COP',
    }];
    await montar();

    // La fila sigue teniendo lo de siempre: quién es y dónde duerme.
    expect(screen.getByText('Ana Debe')).toBeTruthy();
    expect(screen.getByText(/Habitación/)).toBeTruthy();
    // Y ahora también cuánto le falta, que es lo que recepción necesita para cobrar.
    await waitFor(() => expect(screen.getByText(/Pendiente/)).toBeTruthy());
    expect(screen.getByText(/COP/)).toBeTruthy();
    expect(screen.getByText(/2\.900/)).toBeTruthy();
  });

  it('una llegada ya pagada lo dice, y no inventa un pendiente de cero', async () => {
    estado.llegadas = [{
      codigo: 'H-PAGADA', email: 'pago@ejemplo.co', nombre: 'Ana Pagó', huespedes: 2,
      habitacion: '102', abonadoCents: 300000, pendienteCents: 0, moneda: 'COP',
    }];
    await montar();

    await waitFor(() => expect(screen.getByText('PAGADA')).toBeTruthy());
    expect(screen.queryByText(/Pendiente/)).toBeNull();
  });

  it('una reserva sin tarifa no muestra importes inventados', async () => {
    // Se reservó sin precio acordado: el parte no puede decir cuánto debe.
    estado.llegadas = [{
      codigo: 'H-SIN', email: 'sin@ejemplo.co', nombre: 'Ana Sin', huespedes: 1, habitacion: '103',
    }];
    await montar();

    expect(screen.getByText('Ana Sin')).toBeTruthy();
    expect(screen.queryByText(/Pendiente/)).toBeNull();
    expect(screen.queryByText('PAGADA')).toBeNull();
  });

  it('el saldo aparece también en las salidas, no solo en las llegadas', async () => {
    // Quien se va también puede deber la última noche: el parte no es solo de cobros de entrada.
    estado.salidas = [{
      codigo: 'H-SALE', email: 'sale@ejemplo.co', nombre: 'Ana Sale', huespedes: 2,
      habitacion: '101', abonadoCents: 0, pendienteCents: 120000, moneda: 'COP',
    }];
    await montar();

    await waitFor(() => expect(screen.getByText(/1\.200/)).toBeTruthy());
  });
});
