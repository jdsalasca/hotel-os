import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PaginaConsulta } from './PaginaConsulta';

/**
 * Quien entró con su cuenta no teclea su correo: el formulario lo trae puesto
 * (editable, por si consulta la reserva de otra persona con sus datos).
 */
const estado = vi.hoisted(() => ({ llamadas: [] as string[] }));

vi.mock('../api/cliente', () => ({
  api: {
    get: (url: string) => {
      estado.llamadas.push(url);
      const codigo = url.split('/api/reservas/')[1]?.split('/')[0] ?? 'H-ABC123';
      const email = new URLSearchParams(url.split('?')[1]).get('email') ?? '';
      return Promise.resolve({
        reserva: {
          codigo,
          email,
          nombre: 'Nadie',
          llegada: '2030-06-10',
          salida: '2030-06-12',
          noches: 2,
          huespedes: 1,
          estado: 'PENDIENTE',
          origen: 'WEB',
          totalCents: null,
          moneda: null,
          plan: null,
          abonadoCents: null,
          pendienteCents: null,
        },
        habitacion: null,
        hotel: {},
        historial: [],
      });
    },
    post: () => Promise.resolve({}),
  },
  urlApi: (ruta: string) => ruta,
}));

function sesion({ huesped, admin }: { huesped?: string; admin?: string }) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.endsWith('/api/yo')) {
        return huesped
          ? new Response(
              JSON.stringify({ email: huesped, nombre: 'H', tieneReservas: false }),
              { status: 200 },
            )
          : new Response(JSON.stringify({}), { status: 401 });
      }
      if (ruta.endsWith('/api/admin/sesion')) {
        return admin
          ? new Response(JSON.stringify({ email: admin, nombre: 'A' }), { status: 200 })
          : new Response(JSON.stringify({}), { status: 401 });
      }
      throw new Error('fetch no esperado: ' + ruta);
    }),
  );
}

afterEach(() => {
  cleanup();
  estado.llamadas.length = 0;
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

async function montar() {
  render(
    <MemoryRouter>
      <PaginaConsulta />
    </MemoryRouter>,
  );
  await waitFor(() => {
    expect(screen.getByLabelText('Correo electrónico')).toBeTruthy();
  });
}

describe('correo de la sesión en la consulta', () => {
  it('prefilla el correo del huésped con sesión', async () => {
    sesion({ huesped: 'yo@hotel.test' });
    await montar();
    await waitFor(() => {
      expect(
        (screen.getByLabelText('Correo electrónico') as HTMLInputElement).value,
      ).toBe('yo@hotel.test');
    });
  });

  it('prefilla el del panel cuando el huésped no tiene sesión', async () => {
    sesion({ admin: 'admin@hotel.test' });
    await montar();
    await waitFor(() => {
      expect(
        (screen.getByLabelText('Correo electrónico') as HTMLInputElement).value,
      ).toBe('admin@hotel.test');
    });
  });

  it('lo escrito a mano no lo pisa la sesión y viaja en la consulta', async () => {
    sesion({ huesped: 'yo@hotel.test' });
    await montar();
    const campo = screen.getByLabelText('Correo electrónico') as HTMLInputElement;
    await waitFor(() => {
      expect(campo.value).toBe('yo@hotel.test');
    });
    fireEvent.change(campo, { target: { value: 'otro@hotel.test' } });
    fireEvent.change(screen.getByLabelText('Código de reserva'), {
      target: { value: 'H-ABC123' },
    });
    fireEvent.click(screen.getByText('Consultar reserva'));
    await waitFor(() => {
      expect(estado.llamadas.length).toBe(1);
    });
    expect(estado.llamadas[0]).toContain('email=otro%40hotel.test');
    expect(estado.llamadas[0]).toContain('H-ABC123');
  });

  it('sin sesión el campo arranca vacío', async () => {
    sesion({});
    await montar();
    expect((screen.getByLabelText('Correo electrónico') as HTMLInputElement).value).toBe('');
  });

  it('el comprobante se imprime y el formulario no sale en papel', async () => {
    sesion({});
    const imprimir = vi.fn();
    vi.stubGlobal('print', imprimir);
    await montar();
    fireEvent.change(screen.getByLabelText('Código de reserva'), {
      target: { value: 'H-ABC123' },
    });
    fireEvent.change(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'yo@hotel.test' },
    });
    fireEvent.click(screen.getByText('Consultar reserva'));
    await waitFor(() => {
      expect(screen.getByText('Imprimir comprobante')).toBeTruthy();
    });
    fireEvent.click(screen.getByText('Imprimir comprobante'));
    expect(imprimir).toHaveBeenCalledTimes(1);
    expect(document.querySelector('form.ancho-formulario.no-imprimir')).not.toBeNull();
  });
});

describe('origen del comprobante', () => {
  it('el origen y los movimientos llevan su nombre, no el de Estado', async () => {
    sesion({});
    await montar();
    fireEvent.change(screen.getByLabelText('Código de reserva'), {
      target: { value: 'H-ABC123' },
    });
    fireEvent.change(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'yo@hotel.test' },
    });
    fireEvent.click(screen.getByText('Consultar reserva'));
    await screen.findByText('Imprimir comprobante');
    const ficha = document.querySelector('.comprobante')?.textContent ?? '';
    expect(ficha).toContain('Origen');
    expect(ficha).toContain('Reserva creada desde WEB');
    expect(ficha).toContain('Movimientos');
    expect(screen.queryByText('Estado')).toBeNull();
  });
});
