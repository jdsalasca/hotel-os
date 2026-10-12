import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { TablaReservas, type Reserva } from './TablaReservas';

afterEach(cleanup);

function reserva(overrides: Partial<Reserva> = {}): Reserva {
  return {
    codigo: 'H-R241',
    email: 'huesped@example.com',
    nombre: 'Huésped de prueba',
    llegada: '2030-06-10',
    salida: '2030-06-12',
    noches: 2,
    huespedes: 2,
    estado: 'PENDIENTE',
    siguientes: ['CONFIRMADA', 'CANCELADA'],
    origen: 'WEB',
    creadoEn: '2030-01-01',
    ...overrides,
  };
}

function renderizar({
  fila = reserva(),
  detalleAbierto = false,
  mensajesNuevos = {},
  onCambiar = vi.fn(),
  onVerDetalle = vi.fn(),
}: {
  fila?: Reserva;
  detalleAbierto?: boolean;
  mensajesNuevos?: Record<string, number>;
  onCambiar?: (codigo: string, estado: string) => void;
  onVerDetalle?: (codigo: string) => void;
} = {}) {
  const vista = render(
    <TablaReservas
      reservas={[fila]}
      detalleAbierto={detalleAbierto}
      mensajesNuevos={mensajesNuevos}
      onCambiar={onCambiar}
      onVerDetalle={onVerDetalle}
    />,
  );
  return { ...vista, onCambiar, onVerDetalle };
}

describe('TablaReservas', () => {
  it('muestra y delega únicamente las transiciones autorizadas', () => {
    const onCambiar = vi.fn();
    renderizar({ fila: reserva({ siguientes: ['CANCELADA'] }), onCambiar });

    fireEvent.click(screen.getByRole('button', { name: 'Cancelar la reserva H-R241' }));

    expect(onCambiar).toHaveBeenCalledWith('H-R241', 'CANCELADA');
    expect(screen.queryByRole('button', { name: 'Confirmar la reserva H-R241' })).toBeNull();
  });

  it('muestra «Sin acciones» cuando el servidor no permite transiciones', () => {
    renderizar({ fila: reserva({ siguientes: [] }) });

    expect(screen.getByText('Sin acciones')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /la reserva H-R241/ })).toBeNull();
  });

  it('expone mensajes sin leer y abre la reserva correspondiente', () => {
    const onVerDetalle = vi.fn();
    renderizar({ mensajesNuevos: { 'H-R241': 2 }, onVerDetalle });

    expect(screen.getByRole('status', { name: '2 mensajes sin leer' }).textContent).toContain('2 sin leer');
    fireEvent.click(screen.getByRole('button', { name: /Ver detalle/ }));

    expect(onVerDetalle).toHaveBeenCalledWith('H-R241');
  });

  it('conserva datos y semántica de la fila', () => {
    renderizar({ fila: reserva({ nombre: '' }) });

    expect(screen.getByRole('table', { name: 'Reservas del hotel' })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: 'Huésped' })).toBeTruthy();
    expect(screen.getByText('huesped@example.com')).toBeTruthy();
    expect(screen.getByText('Pendiente')).toBeTruthy();
  });

  it('marca la tabla como no imprimible cuando el detalle está abierto', () => {
    const { container } = renderizar({ detalleAbierto: true });

    expect(container.querySelector('.tabla-envoltura')?.classList.contains('no-imprimir')).toBe(true);
  });
});
