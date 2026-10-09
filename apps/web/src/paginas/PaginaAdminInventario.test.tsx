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
    post: () => Promise.resolve({}),
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
