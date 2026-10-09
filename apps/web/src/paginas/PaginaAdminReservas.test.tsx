import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PaginaAdminReservas } from './PaginaAdminReservas';

/**
 * La recepción registra al huésped sin pasar por la web: el formulario crea la
 * reserva (origen OTRO), muestra el código y refresca la lista.
 */
const estado = vi.hoisted(() => ({
  postes: [] as { url: string; cuerpo: unknown }[],
  fila: 'PENDIENTE' as 'PENDIENTE' | 'CONFIRMADA' | 'CANCELADA' | 'NO_PRESENTADA',
  detalle: 'PENDIENTE' as 'PENDIENTE' | 'CONFIRMADA' | 'CANCELADA' | 'NO_PRESENTADA',
}));

function reservaFila(
  estadoReserva: 'PENDIENTE' | 'CONFIRMADA' | 'CANCELADA' | 'NO_PRESENTADA' = 'PENDIENTE',
) {
  return {
    codigo: 'H-PANEL1',
    email: 'panel@example.com',
    nombre: 'Panel',
    llegada: '2030-06-10',
    salida: '2030-06-12',
    noches: 2,
    huespedes: 1,
    estado: estadoReserva,
    siguientes: estadoReserva === 'PENDIENTE'
      ? ['CONFIRMADA', 'CANCELADA', 'RECHAZADA']
      : estadoReserva === 'CONFIRMADA' ? ['CANCELADA', 'NO_PRESENTADA'] : [],
    origen: 'OTRO',
    creadoEn: '2030-01-01',
  };
}

vi.mock('../api/cliente', () => ({
  api: {
    get: (url: string) => {
      if (url.startsWith('/api/admin/reservas?')) return Promise.resolve([reservaFila(estado.fila)]);
      if (url === '/api/admin/habitaciones') {
        return Promise.resolve([{ id: 7, codigo: '101', nombre: 'Habitación 101' }]);
      }
      if (url.includes('/comprobante')) {
        return Promise.resolve({
          reserva: {
            ...reservaFila(estado.detalle),
            totalCents: 300000,
            moneda: 'COP',
            plan: 'Estándar',
          },
          habitacion: { codigo: '101', nombre: 'Habitación 101', tipo: 'Doble' },
          historial: [],
        });
      }
      if (url.includes('/saldo')) {
        return Promise.resolve({
          totalCents: 300000,
          moneda: 'COP',
          abonadoCents: 0,
          pendienteCents: 300000,
          movimientos: [],
        });
      }
      if (url.includes('/mensajes')) return Promise.resolve({ mensajes: [] });
      throw new Error('get no esperado: ' + url);
    },
    post: (url: string, cuerpo: unknown) => {
      estado.postes.push({ url, cuerpo });
      return Promise.resolve({ codigo: 'H-MANUAL1' });
    },
  },
  urlApi: (ruta: string) => ruta,
}));

function sesionConectada() {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: unknown) =>
      String(url).endsWith('/api/admin/sesion')
        ? new Response(JSON.stringify({ email: 'a@hotel.test', nombre: 'A' }), { status: 200 })
        : new Response(JSON.stringify({}), { status: 404 }),
    ),
  );
}

afterEach(() => {
  cleanup();
  estado.postes.length = 0;
  estado.fila = 'PENDIENTE';
  estado.detalle = 'PENDIENTE';
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

async function montar() {
  sesionConectada();
  render(
    <MemoryRouter>
      <PaginaAdminReservas />
    </MemoryRouter>,
  );
  await waitFor(() => {
    expect(screen.getByText('Reservas')).toBeTruthy();
  });
}

describe('nueva reserva manual', () => {
  it('crea con los datos del formulario y muestra el código', async () => {
    await montar();
    fireEvent.click(screen.getByText('Nueva reserva'));
    await waitFor(() => {
      expect(screen.getByRole('option', { name: /101/ })).toBeTruthy();
    });
    fireEvent.change(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'mostrador@example.com' },
    });
    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'Mostrador' } });
    fireEvent.change(screen.getByLabelText('Llegada'), { target: { value: '2030-06-10' } });
    fireEvent.change(screen.getByLabelText('Salida'), { target: { value: '2030-06-12' } });
    fireEvent.change(screen.getByLabelText('Huéspedes'), { target: { value: '2' } });
    fireEvent.change(screen.getByLabelText('Habitación'), { target: { value: '7' } });
    fireEvent.click(screen.getByText('Registrar reserva'));

    await waitFor(() => {
      expect(estado.postes.length).toBe(1);
    });
    expect(estado.postes[0]!.url).toBe('/api/admin/reservas');
    expect(estado.postes[0]!.cuerpo).toMatchObject({
      email: 'mostrador@example.com',
      llegada: '2030-06-10',
      salida: '2030-06-12',
      huespedes: 2,
      roomId: 7,
    });
    await waitFor(() => {
      expect(screen.queryByText(/H-MANUAL1/)).not.toBeNull();
    });
  });

  it('sin habitación no envía nada', async () => {
    await montar();
    fireEvent.click(screen.getByText('Nueva reserva'));
    fireEvent.change(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'a@hotel.test' },
    });
    fireEvent.change(screen.getByLabelText('Llegada'), { target: { value: '2030-06-10' } });
    fireEvent.change(screen.getByLabelText('Salida'), { target: { value: '2030-06-12' } });
    expect(estado.postes.length).toBe(0);
    expect(
      (screen.getByText('Registrar reserva').closest('button') as HTMLButtonElement).disabled,
    ).toBe(true);
  });
});

describe('impresión del detalle', () => {
  it('el detalle ofrece imprimir y la lista no sale en papel', async () => {
    await montar();
    const imprimir = vi.fn();
    vi.stubGlobal('print', imprimir);
    fireEvent.click(screen.getByText('Ver detalle'));
    await waitFor(() => {
      expect(screen.getByText('Imprimir comprobante')).toBeTruthy();
    });
    fireEvent.click(screen.getByText('Imprimir comprobante'));
    expect(imprimir).toHaveBeenCalledTimes(1);
    expect(document.querySelector('.tabla-envoltura.no-imprimir')).not.toBeNull();
  });
});

describe('cambiar huespedes desde el panel', () => {
  // El campo se busca por id, no por texto de label: React parte "Huéspedes (ahora 1)"
    // en varios nodos y una expresión regular sobre el label no casa.
    const CAMPO_GRUPO = () => document.querySelector('#grupo-huespedes') as HTMLInputElement;

  it('una reserva vigente ofrece el formulario con el grupo actual', async () => {
    await montar();
    fireEvent.click(screen.getByText('Ver detalle'));
    await waitFor(() => {
      expect(CAMPO_GRUPO()).toBeTruthy();
    });
    expect(screen.getByText(/Huéspedes \(ahora 1\)/)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Cambiar huéspedes' })).toBeTruthy();
  });

  it('envía el grupo nuevo al endpoint de huéspedes', async () => {
    await montar();
    fireEvent.click(screen.getByText('Ver detalle'));
    await waitFor(() => {
      expect(CAMPO_GRUPO()).toBeTruthy();
    });
    fireEvent.change(CAMPO_GRUPO(), { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar huéspedes' }));

    await waitFor(() => {
      expect(estado.postes.some((p) => p.url === '/api/admin/reservas/H-PANEL1/huespedes')).toBe(true);
    });
    const envio = estado.postes.find((p) => p.url === '/api/admin/reservas/H-PANEL1/huespedes');
    expect(envio?.cuerpo).toEqual({ huespedes: 3 });
  });

  it('una reserva cerrada no ofrece cambiar huéspedes', async () => {
    estado.detalle = 'CANCELADA';
    await montar();
    fireEvent.click(screen.getByText('Ver detalle'));
    await waitFor(() => {
      expect(screen.getByText(/no admite abonos/)).toBeTruthy();
    });
    expect(screen.queryByRole('button', { name: 'Cambiar huéspedes' })).toBeNull();
  });
});

describe('marcar una reserva como no presentada', () => {
  it('traduce el estado cerrado y ya no muestra acciones de transición', async () => {
    estado.fila = 'NO_PRESENTADA';
    await montar();

    expect(document.querySelector('[data-label="Estado"] .etiqueta')?.textContent).toBe('No se presentó');
    expect(screen.getByText('Sin acciones')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'No se presentó la reserva H-PANEL1' })).toBeNull();
  });

  it('muestra la acción que autoriza el servidor y envía el nuevo estado', async () => {
    estado.fila = 'CONFIRMADA';
    await montar();

    expect(document.querySelector('[data-label="Estado"] .etiqueta')?.textContent).toBe('Confirmada');
    fireEvent.click(screen.getByRole('button', { name: 'No se presentó la reserva H-PANEL1' }));

    await waitFor(() => {
      expect(estado.postes).toContainEqual({
        url: '/api/admin/reservas/H-PANEL1/estado',
        cuerpo: { estado: 'NO_PRESENTADA' },
      });
    });
  });

  it('una reserva no presentada queda cerrada y no ofrece registrar abonos', async () => {
    estado.detalle = 'NO_PRESENTADA';
    await montar();
    fireEvent.click(screen.getByText('Ver detalle'));

    await waitFor(() => {
      expect(screen.getByText(/no admite abonos/)).toBeTruthy();
    });
    expect(screen.queryByRole('button', { name: 'Registrar abono' })).toBeNull();
  });
});

describe('cobro en reserva cerrada', () => {
  it('una reserva viva ofrece el abono', async () => {
    await montar();
    fireEvent.click(screen.getByText('Ver detalle'));
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Registrar abono' })).toBeTruthy();
    });
  });

  it('una reserva cancelada no ofrece cobrar y lo explica', async () => {
    estado.detalle = 'CANCELADA';
    await montar();
    fireEvent.click(screen.getByText('Ver detalle'));
    await waitFor(() => {
      expect(screen.getByText(/no admite abonos/)).toBeTruthy();
    });
    expect(screen.queryByRole('button', { name: 'Registrar abono' })).toBeNull();
  });
});
