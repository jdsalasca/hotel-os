import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { PaginaAdminHoy } from './PaginaAdminHoy';

type Movimiento = {
  codigo: string;
  email: string;
  nombre: string;
  huespedes: number;
  habitacion: string;
};

const estado = vi.hoisted(() => ({
  /** Cada lectura devuelve la siguiente tanda: la primera es lo que había al abrir. */
  tandas: [] as { llegadas: Movimiento[]; salidas: Movimiento[]; enCasa: Movimiento[] }[],
  lecturas: 0,
}));

vi.mock('../api/cliente', () => ({
  api: {
    get: () => {
      const tanda = estado.tandas[Math.min(estado.lecturas, estado.tandas.length - 1)];
      estado.lecturas += 1;
      return Promise.resolve({ fecha: '2026-10-12', ...tanda });
    },
  },
}));

vi.mock('../api/useSesion', () => ({
  useSesion: () => ({ haySesion: true, email: 'recepcion@hotel.test', nombre: 'Recepción' }),
}));

function movimiento(codigo: string, nombre: string): Movimiento {
  return { codigo, email: 'ana@example.com', nombre, huespedes: 2, habitacion: '101' };
}

/** Con timers falsos, `findByText` se queda colgado: hay que esperar con `vi.waitFor`. */
async function verTexto(texto: RegExp) {
  await vi.waitFor(() => expect(screen.getByText(texto)).toBeTruthy());
}

describe('el parte del día', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    cleanup();
    estado.lecturas = 0;
    estado.tandas.length = 0;
    vi.useRealTimers();
  });

  it('una reserva web que entra con el parte abierto aparece sola', async () => {
    // La recepción abre el parte por la mañana y lo deja abierto todo el turno. Sin
    // refresco, una reserva que llega por la web no se ve hasta recargar la página.
    estado.tandas = [
      { llegadas: [], salidas: [], enCasa: [movimiento('H-AAA', 'Ana Gómez')] },
      { llegadas: [movimiento('H-BBB', 'Luis Pérez')], salidas: [], enCasa: [movimiento('H-AAA', 'Ana Gómez')] },
    ];
    render(<PaginaAdminHoy />);
    await verTexto(/Llegadas \(0\)/);

    vi.advanceTimersByTime(60000);

    await verTexto(/Llegadas \(1\)/);
    expect(screen.getByText('Luis Pérez')).toBeTruthy();
  });

  it('el refresco de fondo no vuelve a mostrar el cargando', async () => {
    // Si el indicador de carga parpadea cada 30 segundos, la pantalla parece rota.
    estado.tandas = [{ llegadas: [], salidas: [], enCasa: [movimiento('H-AAA', 'Ana Gómez')] }];
    render(<PaginaAdminHoy />);
    await verTexto(/Ana Gómez/);

    vi.advanceTimersByTime(90000);
    await vi.waitFor(() => expect(estado.lecturas).toBeGreaterThan(1));

    expect(screen.queryByText(/cargando el parte/i)).toBeNull();
  });

  it('con la pestaña oculta deja de sondear', async () => {
    // La recepción tiene el parte abierto en segundo plano mientras atiende otra cosa:
    // sondear para nadie gasta peticiones. Primero se ve que el sondeo está vivo, luego
    // que ocultar la pestaña lo para.
    estado.tandas = [{ llegadas: [], salidas: [], enCasa: [] }];
    const ocultar = vi.spyOn(document, 'visibilityState', 'get');
    ocultar.mockReturnValue('visible');
    render(<PaginaAdminHoy />);
    await verTexto(/En casa \(0\)/);

    vi.advanceTimersByTime(200000);
    await vi.waitFor(() => expect(estado.lecturas).toBeGreaterThan(1));
    const sondesando = estado.lecturas;

    ocultar.mockReturnValue('hidden');
    vi.advanceTimersByTime(400000);
    expect(estado.lecturas).toBe(sondesando);
    ocultar.mockRestore();
  });
});
