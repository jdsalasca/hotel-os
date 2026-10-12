import { describe, expect, it, vi, afterEach, beforeEach } from 'vitest';
import { act, render, screen, waitFor, cleanup } from '@testing-library/react';
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
  tandas: [] as { llegadas: Fila[]; salidas: Fila[]; enCasa: Fila[] }[],
  lecturas: 0,
  fallarEn: 0,
}));

vi.mock('../api/cliente', () => ({
  api: {
    get: (url: string) => {
      if (url.startsWith('/api/admin/ocupacion/dia')) {
        if (estado.tandas.length > 0) {
          const lectura = ++estado.lecturas;
          if (estado.fallarEn === lectura) return Promise.reject(new Error('sin conexión'));
          const tanda = estado.tandas[Math.min(lectura - 1, estado.tandas.length - 1)];
          return Promise.resolve({ fecha: '2030-06-10', ...tanda });
        }
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
  estado.tandas = [];
  estado.lecturas = 0;
  estado.fallarEn = 0;
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.useRealTimers();
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

describe('actualización automática del parte', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  it('muestra una llegada nueva sin recargar y no hace parpadear la carga', async () => {
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    estado.tandas = [
      { llegadas: [], salidas: [], enCasa: [fila('H-AAA', 'Ana Gómez')] },
      { llegadas: [fila('H-BBB', 'Luis Pérez')], salidas: [], enCasa: [fila('H-AAA', 'Ana Gómez')] },
    ];
    sesionAdmin();
    render(<MemoryRouter><PaginaAdminHoy /></MemoryRouter>);
    await vi.waitFor(() => expect(screen.getByText('Llegadas (0)')).toBeTruthy());

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30000);
    });

    await vi.waitFor(() => expect(screen.getByText('Luis Pérez')).toBeTruthy());
    expect(screen.getByText('Llegadas (1)')).toBeTruthy();
    expect(screen.queryByText(/cargando el parte/i)).toBeNull();
  });

  it('conserva el parte y avisa si falla una lectura de fondo', async () => {
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    estado.tandas = [{ llegadas: [fila('H-AAA', 'Ana Gómez')], salidas: [], enCasa: [] }];
    estado.fallarEn = 2;
    sesionAdmin();
    render(<MemoryRouter><PaginaAdminHoy /></MemoryRouter>);
    await vi.waitFor(() => expect(screen.getByText('Ana Gómez')).toBeTruthy());

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30000);
    });
    await vi.waitFor(() => expect(estado.lecturas).toBe(2));

    expect(screen.getByText('Ana Gómez')).toBeTruthy();
    await vi.waitFor(() => expect(screen.getByText(/no pudimos actualizar el parte/i)).toBeTruthy());
    expect(screen.queryByText('sin conexión')).toBeNull();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30000);
    });
    await vi.waitFor(() => expect(screen.queryByText(/no pudimos actualizar el parte/i)).toBeNull());
  });

  it('deja de sondear mientras la pestaña está oculta', async () => {
    const visibilidad = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    estado.tandas = [{ llegadas: [], salidas: [], enCasa: [] }];
    sesionAdmin();
    render(<MemoryRouter><PaginaAdminHoy /></MemoryRouter>);
    await vi.waitFor(() => expect(screen.getByText('En casa (0)')).toBeTruthy());

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30000);
    });
    await vi.waitFor(() => expect(estado.lecturas).toBe(2));
    visibilidad.mockReturnValue('hidden');
    await act(async () => {
      await vi.advanceTimersByTimeAsync(90000);
    });

    expect(estado.lecturas).toBe(2);
  });
});

function fila(codigo: string, nombre: string): Fila {
  return { codigo, email: 'ana@example.com', nombre, huespedes: 2, habitacion: '101' };
}
