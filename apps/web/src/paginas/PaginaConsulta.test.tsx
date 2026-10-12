import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PaginaConsulta } from './PaginaConsulta';

/**
 * Quien entró con su cuenta no teclea su correo: el formulario lo trae puesto
 * (editable, por si consulta la reserva de otra persona con sus datos).
 */
const estado = vi.hoisted(() => ({
  llamadas: [] as string[],
  consultas: [] as { url: string; cuerpo: unknown }[],
  errorEstado: null as number | null,
  errorMensaje: '',
}));

vi.mock('../api/cliente', () => {
  class ErrorApi extends Error {
    constructor(readonly estado: number, mensaje: string) {
      super(mensaje);
      this.name = 'ErrorApi';
    }
  }

  return {
    ErrorApi,
    api: {
      get: (url: string) => {
        estado.llamadas.push(url);
        if (estado.errorEstado !== null) {
          return Promise.reject(new ErrorApi(estado.errorEstado, estado.errorMensaje));
        }
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
      post: (url: string, cuerpo?: unknown) => {
        if (url !== '/api/reservas/consulta') return Promise.resolve({});
        estado.llamadas.push(url);
        estado.consultas.push({ url, cuerpo });
        if (estado.errorEstado !== null) {
          return Promise.reject(new ErrorApi(estado.errorEstado, estado.errorMensaje));
        }
        const datos = cuerpo as { codigo: string; email: string };
        return Promise.resolve({
          reserva: {
            codigo: datos.codigo,
            email: datos.email,
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
    },
    urlApi: (ruta: string) => ruta,
  };
});

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
  estado.consultas.length = 0;
  estado.errorEstado = null;
  estado.errorMensaje = '';
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

  it('lo escrito a mano no lo pisa la sesión y viaja solo en el cuerpo de la consulta', async () => {
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
    expect(estado.llamadas[0]).toBe('/api/reservas/consulta');
    expect(estado.llamadas[0]).not.toContain('otro@hotel.test');
    expect(estado.llamadas[0]).not.toContain('H-ABC123');
    expect(estado.consultas[0]).toEqual({
      url: '/api/reservas/consulta',
      cuerpo: { codigo: 'H-ABC123', email: 'otro@hotel.test' },
    });
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

describe('validación del correo en la consulta', () => {
  it('no busca con un correo inválido y explica el error junto al campo', async () => {
    sesion({});
    await montar();

    const campoCorreo = screen.getByLabelText('Correo electrónico') as HTMLInputElement;
    fireEvent.change(screen.getByLabelText('Código de reserva'), {
      target: { value: 'H-ABC123' },
    });
    fireEvent.change(campoCorreo, { target: { value: 'no-es-correo' } });
    fireEvent.click(screen.getByRole('button', { name: 'Consultar reserva' }));

    expect(estado.llamadas).toHaveLength(0);
    expect(campoCorreo.getAttribute('aria-invalid')).toBe('true');
    expect(screen.getByText('Escribe un correo electrónico válido.')).toBeTruthy();
    expect(document.activeElement).toBe(campoCorreo);
  });
});

describe('errores al consultar una reserva', () => {
  it('no presenta una falla temporal como credenciales incorrectas y conserva los datos', async () => {
    estado.errorEstado = 503;
    estado.errorMensaje = 'detalle interno que no debe mostrarse';
    sesion({});
    await montar();

    const codigo = screen.getByLabelText('Código de reserva') as HTMLInputElement;
    const correo = screen.getByLabelText('Correo electrónico') as HTMLInputElement;
    fireEvent.change(codigo, { target: { value: 'H-ABC123' } });
    fireEvent.change(correo, { target: { value: 'yo@hotel.test' } });
    fireEvent.click(screen.getByRole('button', { name: 'Consultar reserva' }));

    expect(await screen.findByText(
      'No pudimos consultar la reserva ahora. Inténtalo de nuevo en unos minutos.',
    )).toBeTruthy();
    expect(screen.queryByText('No encontramos una reserva con ese código y ese correo.')).toBeNull();
    expect(screen.queryByText('detalle interno que no debe mostrarse')).toBeNull();
    expect(codigo.value).toBe('H-ABC123');
    expect(correo.value).toBe('yo@hotel.test');
  });

  it('mantiene el mensaje indistinguible cuando la API responde que no existe', async () => {
    estado.errorEstado = 404;
    estado.errorMensaje = 'reserva no encontrada';
    sesion({});
    await montar();

    fireEvent.change(screen.getByLabelText('Código de reserva'), {
      target: { value: 'H-ABC123' },
    });
    fireEvent.change(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'yo@hotel.test' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Consultar reserva' }));

    expect(await screen.findByText(
      'No encontramos una reserva con ese código y ese correo.',
    )).toBeTruthy();
    expect(screen.getByText(
      'Revisa el código y el correo usados al reservar. Corrige los campos de arriba y vuelve a consultar.',
    )).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'Ver mis reservas de esta cuenta' })).toBeNull();
    expect((screen.getByLabelText('Código de reserva') as HTMLInputElement).value).toBe('H-ABC123');
    expect((screen.getByLabelText('Correo electrónico') as HTMLInputElement).value).toBe('yo@hotel.test');
  });

  it('ofrece ver las reservas de la cuenta cuando la consulta anónima no encuentra el código', async () => {
    estado.errorEstado = 404;
    estado.errorMensaje = 'reserva no encontrada';
    sesion({ huesped: 'yo@hotel.test' });
    await montar();

    await waitFor(() => {
      expect((screen.getByLabelText('Correo electrónico') as HTMLInputElement).value).toBe('yo@hotel.test');
    });
    fireEvent.change(screen.getByLabelText('Código de reserva'), {
      target: { value: 'H-ABC123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Consultar reserva' }));

    await screen.findByText('No encontramos una reserva con ese código y ese correo.');
    expect(screen.getByRole('link', { name: 'Ver mis reservas de esta cuenta' }).getAttribute('href')).toBe('/mis-reservas');
  });

  it('muestra el mensaje seguro de conexión para los errores de red', async () => {
    estado.errorEstado = 0;
    estado.errorMensaje = 'No hay conexión con el hotel. Revisa tu internet e inténtalo de nuevo.';
    sesion({});
    await montar();

    fireEvent.change(screen.getByLabelText('Código de reserva'), {
      target: { value: 'H-ABC123' },
    });
    fireEvent.change(screen.getByLabelText('Correo electrónico'), {
      target: { value: 'yo@hotel.test' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Consultar reserva' }));

    expect(await screen.findByText(estado.errorMensaje)).toBeTruthy();
    expect(screen.queryByText('No encontramos una reserva con ese código y ese correo.')).toBeNull();
  });
});
