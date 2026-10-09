import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
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
  hotel: { nombre: 'Hotel Eridu', contacto_telefono: '+57 300 1234567' },
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

  it('imprime y comparte por WhatsApp con el teléfono del hotel', async () => {
    const imprimir = vi.fn();
    vi.stubGlobal('print', imprimir);
    const cargar = vi.fn(() => Promise.resolve(COMPROBANTE));
    render(<ComprobantePropio codigo="H-123" cargar={cargar} />);
    await screen.findByText('Habitación 101 · Doble');
    fireEvent.click(screen.getByRole('button', { name: 'Imprimir' }));
    expect(imprimir).toHaveBeenCalledTimes(1);
    const wa = screen.getByRole('link', { name: /WhatsApp/ });
    expect(wa.getAttribute('href')).toContain('https://wa.me/573001234567');
    expect(wa.getAttribute('href')).toContain('H-123');
    vi.unstubAllGlobals();
  });

  it('sin teléfono no ofrece WhatsApp', async () => {
    const sinTel = { ...COMPROBANTE, hotel: { nombre: 'Hotel Eridu' } };
    const cargar = vi.fn(() => Promise.resolve(sinTel));
    render(<ComprobantePropio codigo="H-123" cargar={cargar} />);
    await screen.findByText('Habitación 101 · Doble');
    expect(screen.queryByRole('link', { name: /WhatsApp/ })).toBeNull();
    expect(screen.getByRole('button', { name: 'Imprimir' })).toBeTruthy();
  });
});
