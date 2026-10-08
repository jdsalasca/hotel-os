import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { ComprobantePropio } from './ComprobantePropio';

const COMPROBANTE = {
  reserva: {
    codigo: 'H-123',
    llegada: '2030-04-10',
    salida: '2030-04-12',
    huespedes: 2,
    estado: 'CONFIRMADA',
    totalCents: 20000,
    moneda: 'COP',
    plan: 'Estándar',
    abonadoCents: 20000,
    pendienteCents: 0,
  },
  habitacion: { codigo: '101', nombre: 'Habitación 101', tipo: 'Doble' },
  hotel: { nombre: 'Hotel Eridu' },
};

describe('ComprobantePropio', () => {
  afterEach(() => {
    cleanup();
  });

  it('muestra habitación, total y PAGADA sin pedir el correo', async () => {
    const cargar = vi.fn(() => Promise.resolve(COMPROBANTE));
    render(<ComprobantePropio codigo="H-123" cargar={cargar} />);
    expect(cargar).toHaveBeenCalledWith('H-123');
    await screen.findByText('Habitación 101 · Doble');
    await screen.findByText('PAGADA');
    await screen.findByText('Hotel Eridu');
  });

  it('avisa si el comprobante no se puede leer', async () => {
    render(<ComprobantePropio codigo="H-404" cargar={() => Promise.reject(new Error('falló'))} />);
    await screen.findByText('No se pudo leer el comprobante');
  });
});
