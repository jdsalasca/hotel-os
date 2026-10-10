import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PaginaAdminInventario } from './PaginaAdminInventario';

/**
 * Las lecturas del panel ignoran la respuesta vieja cuando el hotel ya cambió de
 * plan, mes o tipo: pintar lo tardío encima es mostrar (y tarifar sobre) otro plan.
 * Patrón ya usado en la búsqueda pública y en el parte del día; aquí faltaba.
 */
const estado = vi.hoisted(() => ({
  diferidas: [] as { url: string; resolver: (v: unknown) => void }[],
  habitaciones: [] as { id: number; codigo: string; roomTypeId: number; nombre: string; estado: string }[],
  // Solo el test del calendario retiene la tanda de carga; al resto le resuelve
  // al momento para poblar tipos y planes (Promise.all espera a las cinco).
  retenerCalendario: false,
  publicaciones: [] as { url: string; cuerpo: unknown }[],
  errorPublicacion: null as Error | null,
}));

function diferida() {
  let resolver!: (v: unknown) => void;
  const promesa = new Promise<unknown>((res) => {
    resolver = res;
  });
  return { promesa, resolver };
}

function noche(fecha: string, precioCents: number) {
  return { fecha, precioCents, minEstancia: null, maxEstancia: null, cerrado: false };
}

vi.mock('../api/cliente', () => ({
  api: {
    get: (url: string) => {
      if (url.startsWith('/api/admin/tarifas?') || url.startsWith('/api/amenidades/por-tipo?')) {
        const d = diferida();
        estado.diferidas.push({ url, resolver: d.resolver });
        return d.promesa;
      }
      if (url === '/api/admin/habitaciones') return Promise.resolve(estado.habitaciones);
      if (url === '/api/admin/tipos') {
        return Promise.resolve([
          { id: 1, codigo: 'DOB', nombre: 'Doble', capacidadMax: 2 },
          { id: 2, codigo: 'SUI', nombre: 'Suite', capacidadMax: 4 },
        ]);
      }
      if (url === '/api/admin/planes') {
        return Promise.resolve([
          { id: 1, codigo: 'STD', nombre: 'Estándar', moneda: 'COP', descuentoPct: 0 },
          { id: 2, codigo: 'FLEX', nombre: 'Flexible', moneda: 'COP', descuentoPct: 0 },
        ]);
      }
      if (url.startsWith('/api/admin/calendario?')) {
        if (!estado.retenerCalendario) return Promise.resolve([]);
        const d = diferida();
        estado.diferidas.push({ url, resolver: d.resolver });
        return d.promesa;
      }
      if (url === '/api/admin/bloqueos') return Promise.resolve([]);
      if (url === '/api/amenidades') {
        return Promise.resolve({
          amenidades: [
            { id: 1, codigo: 'WIFI', nombre: 'Wifi' },
            { id: 2, codigo: 'DES', nombre: 'Desayuno' },
          ],
        });
      }
      throw new Error('ruta no esperada: ' + url);
    },
    post: (url: string, cuerpo?: unknown) => {
      estado.publicaciones.push({ url, cuerpo });
      if (url === '/api/admin/bloqueos' && estado.errorPublicacion) {
        return Promise.reject(estado.errorPublicacion);
      }
      return Promise.resolve({});
    },
    put: () => Promise.resolve({}),
  },
  // La página no lo usa, pero useSesion sí: sin esto pedirAdmin revienta.
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
  estado.diferidas.length = 0;
  estado.habitaciones = [];
  estado.retenerCalendario = false;
  estado.publicaciones.length = 0;
  estado.errorPublicacion = null;
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});



async function montar() {
  sesionConectada();
  render(
    <MemoryRouter>
      <PaginaAdminInventario />
    </MemoryRouter>,
  );
  await waitFor(() => {
    expect(screen.getByLabelText('Plan', { selector: '#tar-plan' })).toBeTruthy();
  });
}

describe('prioridad del alta inicial del inventario', () => {
  it('muestra el asistente antes del calendario cuando aún no hay habitaciones', async () => {
    await montar();
    const encabezados = Array.from(document.querySelectorAll('h2')).map((h) => h.textContent?.trim());

    expect(encabezados.indexOf('Alta del hotel, paso a paso')).toBeGreaterThanOrEqual(0);
    expect(encabezados.indexOf('Alta del hotel, paso a paso')).toBeLessThan(
      encabezados.indexOf('Calendario de ocupación'),
    );
    expect(screen.getByText(/Créalas con el alta guiada de arriba/)).toBeTruthy();
  });

  it('mantiene el calendario antes del asistente si ya hay tipos, habitación y plan', async () => {
    estado.habitaciones = [
      { id: 1, codigo: '101', roomTypeId: 1, nombre: 'Principal', estado: 'ACTIVA' },
    ];
    await montar();
    const encabezados = Array.from(document.querySelectorAll('h2')).map((h) => h.textContent?.trim());

    expect(encabezados.indexOf('Calendario de ocupación')).toBeGreaterThanOrEqual(0);
    expect(encabezados.indexOf('Calendario de ocupación')).toBeLessThan(
      encabezados.indexOf('Alta del hotel, paso a paso'),
    );
    expect(screen.getAllByText('101').length).toBeGreaterThanOrEqual(1);
  });

  it('elige un rango libre y rellena el formulario sin bloquear hasta confirmar', async () => {
    estado.habitaciones = [
      { id: 1, codigo: '101', roomTypeId: 1, nombre: 'Principal', estado: 'ACTIVA' },
    ];
    estado.retenerCalendario = true;
    await montar();
    await waitFor(() => expect(peticiones('/api/admin/calendario?')).toHaveLength(1));
    peticiones('/api/admin/calendario?')[0]!.resolver([{
      id: 1,
      codigo: '101',
      noches: [
        { fecha: '2026-10-01', estado: 'LIBRE', codigoReserva: null },
        { fecha: '2026-10-02', estado: 'LIBRE', codigoReserva: null },
        { fecha: '2026-10-03', estado: 'LIBRE', codigoReserva: null },
        { fecha: '2026-10-04', estado: 'OCUPADA', codigoReserva: 'RES-4' },
      ],
    }]);

    fireEvent.click(await screen.findByRole('button', { name: /Habitación 101, noche 2026-10-01, libre/ }));
    fireEvent.click(screen.getByRole('button', { name: /Habitación 101, noche 2026-10-03, libre/ }));

    expect((screen.getByLabelText('Habitación', { selector: '#bloqueo-hab' }) as HTMLSelectElement).value).toBe('1');
    expect((screen.getByLabelText('Desde', { selector: '#bloqueo-desde' }) as HTMLInputElement).value).toBe('2026-10-01');
    expect((screen.getByLabelText('Hasta', { selector: '#bloqueo-hasta' }) as HTMLInputElement).value).toBe('2026-10-04');
    expect(screen.getByRole('status', { name: 'Selección de noches' }).textContent).toMatch(/3 noches/);
    expect(screen.getByRole('status', { name: 'Selección de noches' }).textContent).toMatch(/2026-10-04/);
    expect(screen.queryByRole('button', { name: /Habitación 101, noche 2026-10-04, libre/ })).toBeNull();
    expect(estado.publicaciones).toHaveLength(0);
  });

  it('no permite completar un rango que cruza una noche ocupada', async () => {
    estado.habitaciones = [
      { id: 1, codigo: '101', roomTypeId: 1, nombre: 'Principal', estado: 'ACTIVA' },
    ];
    estado.retenerCalendario = true;
    await montar();
    await waitFor(() => expect(peticiones('/api/admin/calendario?')).toHaveLength(1));
    peticiones('/api/admin/calendario?')[0]!.resolver([{
      id: 1,
      codigo: '101',
      noches: [
        { fecha: '2026-10-01', estado: 'LIBRE', codigoReserva: null },
        { fecha: '2026-10-02', estado: 'OCUPADA', codigoReserva: 'RES-2' },
        { fecha: '2026-10-03', estado: 'LIBRE', codigoReserva: null },
      ],
    }]);

    fireEvent.click(await screen.findByRole('button', { name: /Habitación 101, noche 2026-10-01, libre/ }));
    fireEvent.click(screen.getByRole('button', { name: /Habitación 101, noche 2026-10-03, libre/ }));

    expect((screen.getByLabelText('Desde', { selector: '#bloqueo-desde' }) as HTMLInputElement).value).toBe('');
    expect((screen.getByLabelText('Hasta', { selector: '#bloqueo-hasta' }) as HTMLInputElement).value).toBe('');
    expect(screen.getByRole('status', { name: 'Selección de noches' }).textContent).toMatch(/2026-10-02/);
    expect(estado.publicaciones).toHaveLength(0);
  });

  it('un arrastre de ratón rellena el mismo intervalo semiabierto', async () => {
    estado.habitaciones = [
      { id: 1, codigo: '101', roomTypeId: 1, nombre: 'Principal', estado: 'ACTIVA' },
    ];
    estado.retenerCalendario = true;
    await montar();
    await waitFor(() => expect(peticiones('/api/admin/calendario?')).toHaveLength(1));
    peticiones('/api/admin/calendario?')[0]!.resolver([{
      id: 1,
      codigo: '101',
      noches: [
        { fecha: '2026-10-01', estado: 'LIBRE', codigoReserva: null },
        { fecha: '2026-10-02', estado: 'LIBRE', codigoReserva: null },
        { fecha: '2026-10-03', estado: 'LIBRE', codigoReserva: null },
      ],
    }]);
    const inicio = await screen.findByRole('button', { name: /Habitación 101, noche 2026-10-01, libre/ });
    const fin = screen.getByRole('button', { name: /Habitación 101, noche 2026-10-03, libre/ });

    fireEvent.pointerDown(inicio, { pointerType: 'mouse', button: 0 });
    fireEvent.pointerEnter(fin, { pointerType: 'mouse', button: 0 });
    fireEvent.pointerUp(fin, { pointerType: 'mouse', button: 0 });

    expect((screen.getByLabelText('Desde', { selector: '#bloqueo-desde' }) as HTMLInputElement).value).toBe('2026-10-01');
    expect((screen.getByLabelText('Hasta', { selector: '#bloqueo-hasta' }) as HTMLInputElement).value).toBe('2026-10-04');
    expect(estado.publicaciones).toHaveLength(0);
  });

  it('conserva las fechas seleccionadas si el servidor rechaza el bloqueo', async () => {
    estado.habitaciones = [
      { id: 1, codigo: '101', roomTypeId: 1, nombre: 'Principal', estado: 'ACTIVA' },
    ];
    estado.retenerCalendario = true;
    await montar();
    await waitFor(() => expect(peticiones('/api/admin/calendario?')).toHaveLength(1));
    peticiones('/api/admin/calendario?')[0]!.resolver([{
      id: 1,
      codigo: '101',
      noches: [
        { fecha: '2026-10-01', estado: 'LIBRE', codigoReserva: null },
        { fecha: '2026-10-02', estado: 'LIBRE', codigoReserva: null },
        { fecha: '2026-10-03', estado: 'LIBRE', codigoReserva: null },
      ],
    }]);

    fireEvent.click(await screen.findByRole('button', { name: /Habitación 101, noche 2026-10-01, libre/ }));
    fireEvent.click(screen.getByRole('button', { name: /Habitación 101, noche 2026-10-03, libre/ }));
    estado.errorPublicacion = new Error('El periodo se cruza con una reserva o un bloqueo vigente.');
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar bloqueo' }));

    expect((await screen.findByRole('alert')).textContent).toMatch(/reserva o un bloqueo/);
    expect(screen.getByRole('status', { name: 'Selección de noches' }).textContent).toMatch(/3 noches/);
    expect((screen.getByLabelText('Desde', { selector: '#bloqueo-desde' }) as HTMLInputElement).value).toBe('2026-10-01');
    expect((screen.getByLabelText('Hasta', { selector: '#bloqueo-hasta' }) as HTMLInputElement).value).toBe('2026-10-04');
    expect(estado.publicaciones).toHaveLength(1);
  });

  it('limpia fechas manuales anteriores al iniciar una selección nueva', async () => {
    estado.habitaciones = [
      { id: 1, codigo: '101', roomTypeId: 1, nombre: 'Principal', estado: 'ACTIVA' },
    ];
    estado.retenerCalendario = true;
    await montar();
    await waitFor(() => expect(peticiones('/api/admin/calendario?')).toHaveLength(1));
    peticiones('/api/admin/calendario?')[0]!.resolver([{
      id: 1,
      codigo: '101',
      noches: [
        { fecha: '2026-10-01', estado: 'LIBRE', codigoReserva: null },
        { fecha: '2026-10-02', estado: 'LIBRE', codigoReserva: null },
      ],
    }]);
    const desde = screen.getByLabelText('Desde', { selector: '#bloqueo-desde' }) as HTMLInputElement;
    const hasta = screen.getByLabelText('Hasta', { selector: '#bloqueo-hasta' }) as HTMLInputElement;
    fireEvent.change(desde, { target: { value: '2026-10-08' } });
    fireEvent.change(hasta, { target: { value: '2026-10-10' } });

    fireEvent.click(await screen.findByRole('button', { name: /Habitación 101, noche 2026-10-01, libre/ }));

    expect(desde.value).toBe('');
    expect(hasta.value).toBe('');
    expect(screen.getByRole('status', { name: 'Selección de noches' }).textContent).toMatch(/Inicio elegido/);
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar bloqueo' }));
    expect(estado.publicaciones).toHaveLength(0);
  });

  it('reinicia el inicio del calendario después de un rango que cruza una noche ocupada', async () => {
    estado.habitaciones = [
      { id: 1, codigo: '101', roomTypeId: 1, nombre: 'Principal', estado: 'ACTIVA' },
    ];
    estado.retenerCalendario = true;
    await montar();
    await waitFor(() => expect(peticiones('/api/admin/calendario?')).toHaveLength(1));
    peticiones('/api/admin/calendario?')[0]!.resolver([{
      id: 1,
      codigo: '101',
      noches: [
        { fecha: '2026-10-01', estado: 'LIBRE', codigoReserva: null },
        { fecha: '2026-10-02', estado: 'OCUPADA', codigoReserva: 'RES-2' },
        { fecha: '2026-10-03', estado: 'LIBRE', codigoReserva: null },
        { fecha: '2026-10-04', estado: 'LIBRE', codigoReserva: null },
        { fecha: '2026-10-05', estado: 'LIBRE', codigoReserva: null },
      ],
    }]);

    fireEvent.click(await screen.findByRole('button', { name: /Habitación 101, noche 2026-10-01, libre/ }));
    fireEvent.click(screen.getByRole('button', { name: /Habitación 101, noche 2026-10-03, libre/ }));
    fireEvent.click(screen.getByRole('button', { name: /Habitación 101, noche 2026-10-04, libre/ }));

    expect(screen.getByRole('status', { name: 'Selección de noches' }).textContent)
      .toMatch(/Inicio elegido: 2026-10-04/);
    fireEvent.click(screen.getByRole('button', { name: /Habitación 101, noche 2026-10-05, libre/ }));
    expect((screen.getByLabelText('Desde', { selector: '#bloqueo-desde' }) as HTMLInputElement).value)
      .toBe('2026-10-04');
    expect((screen.getByLabelText('Hasta', { selector: '#bloqueo-hasta' }) as HTMLInputElement).value)
      .toBe('2026-10-06');
    expect(estado.publicaciones).toHaveLength(0);
  });
});

function plan(value: string) {
  fireEvent.change(screen.getByLabelText('Plan', { selector: '#tar-plan' }), {
    target: { value },
  });
}

function tipo(value: string, selector: string) {
  fireEvent.change(screen.getByLabelText('Tipo', { selector }), { target: { value } });
}

function peticiones(url: string): { url: string; resolver: (v: unknown) => void }[] {
  return estado.diferidas.filter((d) => d.url.startsWith(url));
}

function porPlan(url: string): string | null {
  return new URLSearchParams(url.split('?')[1]).get('planId');
}

describe('vigencia de las lecturas del inventario', () => {
  it('cambiar de plan ignora las noches tardías del plan viejo', async () => {
    await montar();
    tipo('1', '#tar-tipo');
    plan('1');
    await waitFor(() => {
      expect(peticiones('/api/admin/tarifas?').length).toBe(1);
    });
    plan('2');
    await waitFor(() => {
      expect(peticiones('/api/admin/tarifas?').length).toBe(2);
    });
    const [vieja, nueva] = peticiones('/api/admin/tarifas?');
    // La nueva vuelve primero (barata) y la vieja después (lenta): manda la nueva.
    nueva!.resolver([noche('2026-10-01', 20000), noche('2026-10-02', 20000)]);
    await waitFor(() => {
      expect(
        (screen.getByLabelText('Precio de la noche 2026-10-01') as HTMLInputElement).value,
      ).toBe('200');
    });
    vieja!.resolver([noche('2026-10-01', 10000), noche('2026-10-02', 10000)]);
    // La tardía ya tuvo tiempo de pintar: si el valor sigue en 200 es porque se ignoró.
    // (Un waitFor aquí daría falso verde: la primera pasada aún vería el 200 viejo.)
    await new Promise((r) => setTimeout(r, 300));
    expect(porPlan(vieja!.url)).toBe('1');
    expect(
      (screen.getByLabelText('Precio de la noche 2026-10-01') as HTMLInputElement).value,
    ).toBe('200');
  });

  it('cambiar de mes ignora las noches tardías del mes viejo', async () => {
    await montar();
    tipo('1', '#tar-tipo');
    plan('1');
    await waitFor(() => {
      expect(peticiones('/api/admin/tarifas?').length).toBe(1);
    });
    fireEvent.change(screen.getByLabelText('Mes', { selector: '#tar-mes' }), {
      target: { value: '2026-11' },
    });
    await waitFor(() => {
      expect(peticiones('/api/admin/tarifas?').length).toBe(2);
    });
    const [viejas, nuevas] = peticiones('/api/admin/tarifas?');
    nuevas!.resolver([noche('2026-11-01', 20000)]);
    await waitFor(() => {
      expect(
        (screen.getByLabelText('Precio de la noche 2026-11-01') as HTMLInputElement).value,
      ).toBe('200');
    });
    viejas!.resolver([noche('2026-10-01', 10000)]);
    await new Promise((r) => setTimeout(r, 300));
    expect(screen.queryByLabelText('Precio de la noche 2026-10-01')).toBeNull();
    expect(
      (screen.getByLabelText('Precio de la noche 2026-11-01') as HTMLInputElement).value,
    ).toBe('200');
  });

  it('cambiar de tipo en servicios ignora los marcados tardíos del tipo viejo', async () => {
    await montar();
    fireEvent.click(screen.getByText('Siguiente'));
    fireEvent.click(screen.getByText('Siguiente'));
    expect(
      screen.getByText('Marca solo los servicios que tienen todas las habitaciones de este tipo. Se mostrarán en sus ofertas.'),
    ).toBeTruthy();
    tipo('1', '#serv-tipo');
    await waitFor(() => {
      expect(peticiones('/api/amenidades/por-tipo?').length).toBe(1);
    });
    tipo('2', '#serv-tipo');
    await waitFor(() => {
      expect(peticiones('/api/amenidades/por-tipo?').length).toBe(2);
    });
    const [viejos, nuevos] = peticiones('/api/amenidades/por-tipo?');
    nuevos!.resolver({ porTipo: { 2: [{ id: 1, codigo: 'WIFI', nombre: 'Wifi' }] } });
    await waitFor(() => {
      expect((screen.getByRole('checkbox', { name: 'Wifi' }) as HTMLInputElement).checked).toBe(
        true,
      );
    });
    viejos!.resolver({ porTipo: { 1: [{ id: 2, codigo: 'DES', nombre: 'Desayuno' }] } });
    await new Promise((r) => setTimeout(r, 300));
    // El catálogo pinta ambas casillas siempre: lo que delata al dato viejo es el check.
    expect((screen.getByRole('checkbox', { name: 'Desayuno' }) as HTMLInputElement).checked).toBe(
      false,
    );
    expect((screen.getByRole('checkbox', { name: 'Wifi' }) as HTMLInputElement).checked).toBe(
      true,
    );
  });

  it('cambiar de mes ignora el calendario tardío del mes viejo', async () => {
    estado.retenerCalendario = true;
    await montar();
    await waitFor(() => {
      expect(peticiones('/api/admin/calendario?').length).toBe(1);
    });
    fireEvent.change(screen.getByLabelText('Mes', { selector: '#mes-inv' }), {
      target: { value: '2026-11' },
    });
    await waitFor(() => {
      expect(peticiones('/api/admin/calendario?').length).toBe(2);
    });
    const [viejo, nuevo] = peticiones('/api/admin/calendario?');
    nuevo!.resolver([{ id: 2, codigo: 'NUEVA-202', noches: [] }]);
    await waitFor(() => {
      expect(screen.queryByText('NUEVA-202')).not.toBeNull();
    });
    viejo!.resolver([{ id: 1, codigo: 'VIEJA-101', noches: [] }]);
    await new Promise((r) => setTimeout(r, 300));
    expect(screen.queryByText('VIEJA-101')).toBeNull();
    expect(screen.queryByText('NUEVA-202')).not.toBeNull();
  });
});
