import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup, within } from '@testing-library/react';
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
  llamadasCalendario: [] as string[],
  hotel: null as null | Record<string, string>,
  diasCalendario: [] as Array<{
    fecha: string;
    disponibles: number;
    precios: Array<{ moneda: string; desdeCents: number }>;
  }>,
  lugaresPublicos: null as null | {
    hotel: { ubicado: boolean; latitud?: number; longitud?: number };
    lugares: Array<{
      id: number;
      nombre: string;
      descripcion: string;
      latitud: number;
      longitud: number;
    }>;
  },
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
      estado.llamadasCalendario.push(url);
      return { mes: '2030-06', huespedes: 2, dias: estado.diasCalendario };
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
    if (url === '/api/lugares') {
      return estado.lugaresPublicos ?? { hotel: { ubicado: false }, lugares: [] };
    }
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
  estado.llamadasCalendario.length = 0;
  estado.hotel = null;
  estado.diasCalendario.length = 0;
  estado.lugaresPublicos = null;
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

describe('distancias de sitios', () => {
  it('explica la distancia en línea recta y conserva el acceso a la ruta real', async () => {
    estado.lugaresPublicos = {
      hotel: { ubicado: true, latitud: 4, longitud: -74 },
      lugares: [{
        id: 1,
        nombre: 'Café cercano',
        descripcion: '',
        latitud: 4.0004,
        longitud: -74,
      }],
    };
    render(<MemoryRouter><PaginaInicio /></MemoryRouter>);

    expect(await screen.findByText('La distancia es en línea recta; «Cómo llegar» abre la ruta real.'))
      .toBeTruthy();
    expect(screen.getByRole('link', { name: 'Cómo llegar' }).getAttribute('href'))
      .toBe('https://www.google.com/maps/dir/?api=1&destination=4.0004,-74');
  });

  it('oculta la nota cuando el hotel no tiene ubicación para calcular distancias', async () => {
    estado.lugaresPublicos = {
      hotel: { ubicado: false },
      lugares: [{
        id: 1,
        nombre: 'Café cercano',
        descripcion: '',
        latitud: 4.0004,
        longitud: -74,
      }],
    };
    render(<MemoryRouter><PaginaInicio /></MemoryRouter>);

    await screen.findByRole('heading', { name: 'Encuéntranos y explora' });
    expect(screen.queryByText('La distancia es en línea recta; «Cómo llegar» abre la ruta real.'))
      .toBeNull();
  });

  it('muestra metros para los sitios cercanos', async () => {
    estado.lugaresPublicos = {
      hotel: { ubicado: true, latitud: 4, longitud: -74 },
      lugares: [{
        id: 1,
        nombre: 'Café cercano',
        descripcion: '',
        latitud: 4.0004,
        longitud: -74,
      }],
    };
    render(<MemoryRouter><PaginaInicio /></MemoryRouter>);

    expect(await screen.findByText('a 44 m')).toBeTruthy();
  });

  it('muestra cero metros cuando el lugar está en el punto del hotel', async () => {
    estado.lugaresPublicos = {
      hotel: { ubicado: true, latitud: 4, longitud: -74 },
      lugares: [{
        id: 1,
        nombre: 'En el hotel',
        descripcion: '',
        latitud: 4,
        longitud: -74,
      }],
    };
    render(<MemoryRouter><PaginaInicio /></MemoryRouter>);

    expect(await screen.findByText('a 0 m')).toBeTruthy();
  });

  it('formatea kilómetros lejanos con coma decimal', async () => {
    estado.lugaresPublicos = {
      hotel: { ubicado: true, latitud: 4, longitud: -74 },
      lugares: [{
        id: 1,
        nombre: 'Lugar lejano',
        descripcion: '',
        latitud: 4.01,
        longitud: -74,
      }],
    };
    render(<MemoryRouter><PaginaInicio /></MemoryRouter>);

    expect(await screen.findByText('a 1,1 km')).toBeTruthy();
  });
});

describe('reservas en línea pausadas', () => {
  it('explica la pausa y retira todos los controles de búsqueda que no pueden funcionar', async () => {
    estado.sinVenta = true;
    render(
      <MemoryRouter>
        <PaginaInicio />
      </MemoryRouter>,
    );

    const aviso = await screen.findByRole('heading', { name: 'Reservas en línea pausadas' });
    const collage = document.querySelector('.collage');
    expect(aviso.closest('section')?.compareDocumentPosition(collage!)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    expect(screen.queryByRole('heading', { name: 'Reservar es así de simple' })).toBeNull();
    expect(screen.queryByLabelText('Llegada')).toBeNull();
    expect(screen.queryByLabelText('Salida')).toBeNull();
    expect(screen.queryByLabelText('Huéspedes')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Buscar disponibilidad' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Mes anterior' })).toBeNull();
    expect(document.querySelector('.calendario-mes__rejilla')).toBeNull();
    expect(estado.llamadasCalendario).toHaveLength(0);
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
    expect(screen.getByLabelText('Llegada')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Buscar disponibilidad' })).toBeTruthy();
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
    await screen.findByRole('heading', { name: 'Reservas en línea pausadas' });
    expect(screen.queryByText(/hola@hotel\.test/)).toBeNull();
  });

  it('la portada ofrece gestionar una reserva existente aunque la venta esté pausada', async () => {
    estado.sinVenta = true;
    render(
      <MemoryRouter>
        <PaginaInicio />
      </MemoryRouter>,
    );
    const enlace = await screen.findByRole('link', { name: 'Gestionar una reserva' });
    expect(enlace.getAttribute('href')).toBe('/mis-reservas');
    expect(screen.queryByRole('link', { name: 'Ver disponibilidad' })).toBeNull();
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

describe('búsqueda usable con teclado y lector de pantalla', () => {
  it('enfoca y desplaza la página hasta las habitaciones encontradas', async () => {
    const descriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollIntoView');
    const desplazar = vi.fn();
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
      configurable: true,
      value: desplazar,
    });

    try {
      await buscar();
      const resultados = document.querySelector('.resultados-busqueda') as HTMLElement;
      await waitFor(() => expect(document.activeElement).toBe(resultados));
      expect(desplazar).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
    } finally {
      if (descriptor) {
        Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', descriptor);
      } else {
        Reflect.deleteProperty(HTMLElement.prototype, 'scrollIntoView');
      }
    }
  });

  it('enfoca también el aviso cuando no hay habitaciones disponibles', async () => {
    estado.sinResultados = true;
    render(<MemoryRouter><PaginaInicio /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText('Llegada'), { target: { value: '2030-06-10' } });
    fireEvent.change(screen.getByLabelText('Salida'), { target: { value: '2030-06-12' } });
    fireEvent.click(screen.getByRole('button', { name: 'Buscar disponibilidad' }));

    const vacio = await screen.findByText('No hay habitaciones disponibles para esas fechas');
    await waitFor(() => expect(document.activeElement).toBe(vacio.closest('.resultados-busqueda')));
  });

  it('no consulta fechas inválidas y enfoca la salida que debe corregirse', async () => {
    render(<MemoryRouter><PaginaInicio /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText('Llegada'), { target: { value: '2030-06-12' } });
    const salida = screen.getByLabelText('Salida') as HTMLInputElement;
    fireEvent.change(salida, { target: { value: '2030-06-12' } });
    fireEvent.click(screen.getByRole('button', { name: 'Buscar disponibilidad' }));

    expect(await screen.findByText('La salida debe ser posterior a la llegada.')).toBeTruthy();
    expect(salida.getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(salida);
    expect(estado.llamadasBusqueda).toHaveLength(0);
  });

  it('rechaza más de 20 huéspedes con error junto al campo y sin consultar', async () => {
    render(<MemoryRouter><PaginaInicio /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText('Llegada'), { target: { value: '2030-06-10' } });
    fireEvent.change(screen.getByLabelText('Salida'), { target: { value: '2030-06-12' } });
    const huespedes = screen.getByLabelText('Huéspedes') as HTMLInputElement;
    fireEvent.change(huespedes, { target: { value: '21' } });
    fireEvent.click(screen.getByRole('button', { name: 'Buscar disponibilidad' }));

    expect(await screen.findByText('Elige entre 1 y 20 huéspedes.')).toBeTruthy();
    expect(huespedes.getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(huespedes);
    expect(estado.llamadasBusqueda).toHaveLength(0);
  });

  it('expone los días como botones accesibles e indica cuándo están llenos', async () => {
    estado.diasCalendario = [
      {
        fecha: '2030-06-10',
        disponibles: 1,
        precios: [{ moneda: 'COP', desdeCents: 150000 }],
      },
      { fecha: '2030-06-11', disponibles: 0, precios: [] },
    ];
    render(<MemoryRouter><PaginaInicio /></MemoryRouter>);

    const calendario = await screen.findByRole('group', { name: /disponibilidad de/i });
    const dia = within(calendario).getByRole('button', { name: /habitación libre/i });
    const lleno = within(calendario).getByRole('button', { name: /sin habitaciones/i }) as HTMLButtonElement;
    expect(dia.getAttribute('role')).toBeNull();
    expect(lleno.disabled).toBe(true);
  });

  it('bloquea las opciones antiguas cuando cambian los criterios de búsqueda', async () => {
    await buscar();
    fireEvent.change(screen.getByLabelText('Salida'), { target: { value: '2030-06-15' } });

    expect(await screen.findByText(/cambiaste los criterios de búsqueda/i)).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Elegir Doble' }) as HTMLButtonElement).disabled)
      .toBe(true);
    expect(document.querySelector('section[aria-labelledby="titulo-habitaciones"]')?.textContent)
      .toContain('al 12 de jun de 2030');
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
    const estadoResultados = screen.getByText('Encontramos 1 opción disponible.');
    expect(estadoResultados.getAttribute('role')).toBe('status');
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
