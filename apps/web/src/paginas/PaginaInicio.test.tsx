import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PaginaInicio } from './PaginaInicio';
import { fechaCorta } from '../api/formato';

/**
 * El detalle y el resumen pertenecen a la búsqueda que produjo las ofertas, no al
 * formulario que el huésped pudo editar después sin volver a buscar. Hoy usan los
 * valores vivos: con el formulario cambiado se pide otro desglose y el abierto se
 * pierde ("Ocultar detalle" sin nada debajo).
 */
const estado = vi.hoisted(() => ({
  llamadasDetalle: [] as string[],
  llamadasBusqueda: [] as string[],
  dosPlanes: false,
  sinResultados: false,
  sinVenta: false,
  fallaVenta: false,
  hotel: null as null | Record<string, string>,
}));

vi.mock('../api/cliente', () => {
  function oferta(planId: number, nombrePlan: string) {
    return {
      habitacion: { id: 5, codigo: '101', nombre: 'Hab 101' },
      tipo: { id: 9, codigo: 'DOB', nombre: 'Doble', capacidadMax: 2 },
      totalCents: 300000,
      moneda: 'COP',
      noches: 2,
      plan: { id: planId, codigo: planId === 7 ? 'STD' : 'FLEX', nombre: nombrePlan },
      descuentoPct: 0,
      totalSinDescuentoCents: 300000,
    };
  }
  async function responder(url: string) {
    if (url.startsWith('/api/disponibilidad/detalle?')) {
      estado.llamadasDetalle.push(url);
      const q = new URLSearchParams(url.split('?')[1]);
      return {
        habitacion: { id: Number(q.get('roomId')), codigo: '101', nombre: 'Hab 101' },
        tipo: { id: 9, codigo: 'DOB', nombre: 'Doble', capacidadMax: 2 },
        plan: { codigo: 'STD', nombre: 'Estándar' },
        noches: [
          { fecha: '2030-06-10', precioCents: 150000 },
          { fecha: '2030-06-11', precioCents: 150000 },
        ],
        totalCents: 300000,
        moneda: 'COP',
        descuentoPct: 0,
        totalSinDescuentoCents: 300000,
      };
    }
    if (url.startsWith('/api/disponibilidad?')) {
      estado.llamadasBusqueda.push(url);
      const q = new URLSearchParams(url.split('?')[1]);
      return {
        llegada: q.get('llegada'),
        salida: q.get('salida'),
        huespedes: Number(q.get('huespedes')),
        ofertas: estado.sinResultados
          ? []
          : estado.dosPlanes
          ? [oferta(7, 'Estándar'), oferta(8, 'Flexible')]
          : [oferta(7, 'Estándar')],
      };
    }
    if (url.startsWith('/api/disponibilidad/calendario?')) {
      return { mes: '2030-06', huespedes: 2, dias: [] };
    }
    if (url === '/api/hotel/venta') {
      if (estado.fallaVenta) throw new Error('venta caída');
      return { a_la_venta: !estado.sinVenta };
    }
    if (url === '/api/hotel') {
      return estado.hotel ?? {
        nombre: 'Hotel Eridu',
        contacto_email: 'hola@hotel.test',
        contacto_telefono: '+57 300 1234567',
      };
    }
    if (url.startsWith('/api/amenidades/por-tipo?')) return { porTipo: {} };
    if (url === '/api/lugares') return { hotel: { ubicado: false }, lugares: [] };
    throw new Error('ruta no esperada: ' + url);
  }
  return { api: { get: responder }, nuevaClaveIdempotencia: () => 'clave-test' };
});

afterEach(() => {
  cleanup();
  estado.llamadasDetalle.length = 0;
  estado.llamadasBusqueda.length = 0;
  estado.dosPlanes = false;
  estado.sinResultados = false;
  estado.sinVenta = false;
  estado.fallaVenta = false;
  estado.hotel = null;
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

async function buscar() {
  render(
    <MemoryRouter>
      <PaginaInicio />
    </MemoryRouter>,
  );
  fireEvent.change(screen.getByLabelText('Llegada'), { target: { value: '2030-06-10' } });
  fireEvent.change(screen.getByLabelText('Salida'), { target: { value: '2030-06-12' } });
  fireEvent.click(screen.getByText('Buscar disponibilidad'));
  await screen.findAllByText('Elegir esta habitación');
}

describe('hotel sin nada que vender', () => {
  it('lo dice en vez de fingir un mes lleno', async () => {
    estado.sinVenta = true;
    render(
      <MemoryRouter>
        <PaginaInicio />
      </MemoryRouter>,
    );
    await screen.findByText('Este hotel aún no publica habitaciones');
    expect(document.querySelector('.calendario-mes__rejilla')).toBeNull();
  });

  it('con venta muestra el calendario como siempre', async () => {
    render(
      <MemoryRouter>
        <PaginaInicio />
      </MemoryRouter>,
    );
    await waitFor(() => {
      expect(document.querySelector('.calendario-mes__rejilla')).not.toBeNull();
    });
  });

  it('el vacío trae cómo contactar al hotel', async () => {
    estado.sinVenta = true;
    render(
      <MemoryRouter>
        <PaginaInicio />
      </MemoryRouter>,
    );
    await screen.findByText(/hola@hotel\.test/);
    await screen.findByText(/\+57 300 1234567/);
    expect(screen.getByRole('link', { name: 'hola@hotel.test' }).getAttribute('href')).toBe(
      'mailto:hola@hotel.test',
    );
    expect(screen.getByRole('link', { name: '+57 300 1234567' }).getAttribute('href')).toBe(
      'tel:+573001234567',
    );
  });

  it('sin contacto igual dice que no hay nada publicado', async () => {
    estado.sinVenta = true;
    estado.hotel = {};
    render(
      <MemoryRouter>
        <PaginaInicio />
      </MemoryRouter>,
    );
    await screen.findByText('Este hotel aún no publica habitaciones');
    expect(screen.queryByText(/hola@hotel\.test/)).toBeNull();
  });

  it('sin venta la búsqueda se deshabilita con motivo', async () => {
    estado.sinVenta = true;
    render(
      <MemoryRouter>
        <PaginaInicio />
      </MemoryRouter>,
    );
    await screen.findByText(/la búsqueda no traerá nada todavía/);
    const boton = screen.getByRole('button', {
      name: 'Buscar disponibilidad',
    }) as HTMLButtonElement;
    expect(boton.disabled).toBe(true);
  });

  it('si la venta falla al leer, la búsqueda sigue abierta', async () => {
    estado.fallaVenta = true;
    render(
      <MemoryRouter>
        <PaginaInicio />
      </MemoryRouter>,
    );
    await waitFor(() => {
      expect(document.querySelector('.calendario-mes__rejilla')).not.toBeNull();
    });
    fireEvent.change(screen.getByLabelText('Llegada'), { target: { value: '2030-06-10' } });
    fireEvent.change(screen.getByLabelText('Salida'), { target: { value: '2030-06-12' } });
    const boton = screen.getByRole('button', {
      name: 'Buscar disponibilidad',
    }) as HTMLButtonElement;
    expect(boton.disabled).toBe(false);
  });
});

describe('detalle de la oferta', () => {
  it('anuncia el estado vacío cuando no hay ofertas', async () => {
    estado.sinResultados = true;
    render(
      <MemoryRouter>
        <PaginaInicio />
      </MemoryRouter>,
    );
    fireEvent.change(screen.getByLabelText('Llegada'), { target: { value: '2030-06-10' } });
    fireEvent.change(screen.getByLabelText('Salida'), { target: { value: '2030-06-12' } });
    fireEvent.click(screen.getByRole('button', { name: 'Buscar disponibilidad' }));

    await screen.findByText('No hay habitaciones disponibles para esas fechas');
    const estadoVacio = document.querySelector('.vacio');
    expect(estadoVacio?.getAttribute('role')).toBe('status');
    expect(estadoVacio?.getAttribute('aria-live')).toBe('polite');
  });

  it('al volver a cambiar fechas restaura la búsqueda y vuelve a consultar', async () => {
    render(
      <MemoryRouter initialEntries={[{
        pathname: '/',
        state: { restaurarBusqueda: { llegada: '2030-06-10', salida: '2030-06-12', huespedes: 3 } },
      }]}>
        <PaginaInicio />
      </MemoryRouter>,
    );

    await screen.findAllByText('Elegir esta habitación');
    expect((screen.getByLabelText('Llegada') as HTMLInputElement).value).toBe('2030-06-10');
    expect((screen.getByLabelText('Salida') as HTMLInputElement).value).toBe('2030-06-12');
    expect((screen.getByLabelText('Huéspedes') as HTMLInputElement).value).toBe('3');
    expect(estado.llamadasBusqueda).toHaveLength(1);
    expect(estado.llamadasBusqueda[0]).toContain('llegada=2030-06-10');
    expect(estado.llamadasBusqueda[0]).toContain('salida=2030-06-12');
    expect(estado.llamadasBusqueda[0]).toContain('huespedes=3');
  });

  it('indica cuántas opciones puede elegir el huésped', async () => {
    await buscar();
    const estadoResultados = screen.getByRole('status');
    expect(estadoResultados.textContent).toBe('Encontramos 1 opción disponible.');
    expect(estadoResultados.getAttribute('aria-live')).toBe('polite');
  });

  it('pide las fechas buscadas aunque el formulario haya cambiado', async () => {
    await buscar();
    fireEvent.change(screen.getByLabelText('Llegada'), { target: { value: '2030-07-01' } });
    fireEvent.click(screen.getByRole('button', { name: /noche por noche/ }));
    await waitFor(() => expect(estado.llamadasDetalle.length).toBe(1));
    expect(estado.llamadasDetalle[0]).toContain('llegada=2030-06-10');
    expect(estado.llamadasDetalle[0]).toContain('salida=2030-06-12');
    expect(estado.llamadasDetalle[0]).not.toContain('2030-07-01');
  });

  it('el desglose y el resumen sobreviven a editar el formulario sin buscar', async () => {
    await buscar();
    fireEvent.click(screen.getByRole('button', { name: /noche por noche/ }));
    await waitFor(() => {
      expect(document.querySelector('.desglose__noches')).not.toBeNull();
    });
    fireEvent.change(screen.getByLabelText('Salida'), { target: { value: '2030-06-15' } });
    expect(document.querySelector('.desglose__noches')).not.toBeNull();
    expect(screen.getByRole('button', { name: /noche por noche/ }).textContent).toBe(
      'Ocultar detalle',
    );
    const seccion = document.querySelector('section[aria-labelledby="titulo-habitaciones"]');
    expect(seccion?.textContent).toContain(`al ${fechaCorta('2030-06-12')}`);
    expect(seccion?.textContent).not.toContain(fechaCorta('2030-06-15'));
  });

  it('los detalles de dos planes se abren por separado', async () => {
    estado.dosPlanes = true;
    await buscar();
    const botones = await screen.findAllByRole('button', { name: /noche por noche/ });
    expect(botones.length).toBe(2);
    fireEvent.click(botones[0]!);
    await waitFor(() => {
      expect(document.querySelectorAll('.desglose__noches').length).toBe(1);
    });
    // Abrir el segundo no cierra el primero: hoy comparten el interruptor por
    // habitación y el clic lo apaga en vez de pedir el otro desglose.
    fireEvent.click(botones[1]!);
    await waitFor(() => expect(estado.llamadasDetalle.length).toBe(2));
    expect(document.querySelectorAll('.desglose__noches').length).toBe(2);
  });
});
