import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { fechaCorta, hoyIso, monto as montoCompartido } from '../api/formato';
import { PaginaMisReservas } from './PaginaMisReservas';

/**
 * El huésped mueve sus fechas sin llamar: el formulario por fila envía al
 * endpoint propio y la lista se refresca con lo guardado de verdad.
 */
type Fila = {
  codigo: string;
  llegada: string;
  salida: string;
  huespedes: number;
  estado: string;
  creado_en: string;
  total_cents: number;
  moneda: string;
  abonado_cents: number;
  pendiente_cents: number;
};

const estado = vi.hoisted(() => ({
  postes: [] as { url: string; cuerpo: unknown }[],
  fallaMover: false,
  fallaGrupo: false,
  lista: [] as Fila[],
}));

vi.mock('../api/cliente', () => ({
  api: {
    get: (url: string) => {
      if (url === '/api/mis-reservas') return Promise.resolve({ reservas: estado.lista });
      if (url === '/api/mis-reservas/mensajes/nuevos') {
        return Promise.resolve({ nuevos: 0, porReserva: {} });
      }
      throw new Error('get no esperado: ' + url);
    },
    post: (url: string, cuerpo: unknown) => {
      estado.postes.push({ url, cuerpo });
      if (estado.fallaMover && url.endsWith('/fechas')) {
        return Promise.reject(new Error('esas fechas no están libres para esa habitación'));
      }
      if (estado.fallaGrupo && url.endsWith('/huespedes')) {
        return Promise.reject(new Error('esa habitación no admite 4 huéspedes en esas fechas'));
      }
      const q = cuerpo as { llegada: string; salida: string; huespedes?: number };
      const r = estado.lista.find((f) => url.includes(f.codigo));
      if (r) {
        if (q.huespedes !== undefined) {
          r.huespedes = q.huespedes;
          return Promise.resolve({ codigo: r.codigo, huespedes: r.huespedes });
        }
        r.llegada = q.llegada;
        r.salida = q.salida;
      }
      return Promise.resolve({ codigo: 'H-MIA1', llegada: q.llegada, salida: q.salida });
    },
  },
  urlApi: (ruta: string) => ruta,
}));

function sesionConectada() {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: unknown) =>
      String(url).endsWith('/api/yo')
        ? new Response(
            JSON.stringify({ email: 'yo@hotel.test', nombre: 'Yo', tieneReservas: true }),
            { status: 200 },
          )
        : new Response(JSON.stringify({}), { status: 404 }),
    ),
  );
}

function filaInicial(): Fila {
  return {
    codigo: 'H-MIA1',
    llegada: '2030-06-10',
    salida: '2030-06-12',
    huespedes: 1,
    estado: 'PENDIENTE',
    creado_en: '2030-01-01',
    total_cents: 300000,
    moneda: 'COP',
    abonado_cents: 0,
    pendiente_cents: 300000,
  };
}

afterEach(() => {
  cleanup();
  estado.postes.length = 0;
  estado.fallaMover = false;
  estado.fallaGrupo = false;
  estado.lista.length = 0;
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function sembrar() {
  estado.lista.length = 0;
  estado.lista.push(filaInicial());
}

async function montar() {
  sesionConectada();
  render(
    <MemoryRouter>
      <PaginaMisReservas />
    </MemoryRouter>,
  );
  await waitFor(() => {
    expect(screen.getByText('H-MIA1')).toBeTruthy();
  });
}

describe('resumen de mis reservas', () => {
  it('muestra las fechas y los importes con el formato compartido del sitio', async () => {
    sembrar();
    await montar();

    expect(screen.getByText(fechaCorta('2030-06-10'))).toBeTruthy();
    expect(screen.getByText(fechaCorta('2030-06-12'))).toBeTruthy();
    expect(screen.queryByText('2030-06-10')).toBeNull();

    const fila = screen.getByText('H-MIA1').closest('tr');
    expect(fila?.textContent).toContain(montoCompartido(300000, 'COP'));
  });
});

describe('cambio de fechas propio', () => {
  it('abre los campos con las fechas que ya tiene la reserva', async () => {
    sembrar();
    await montar();
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar fechas' }));

    expect((screen.getByLabelText('Nueva llegada') as HTMLInputElement).value).toBe('2030-06-10');
    expect((screen.getByLabelText('Nueva salida') as HTMLInputElement).value).toBe('2030-06-12');
  });

  it('impide elegir una llegada pasada o una salida igual o anterior a la llegada', async () => {
    sembrar();
    await montar();
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar fechas' }));

    const llegada = screen.getByLabelText('Nueva llegada') as HTMLInputElement;
    const salida = screen.getByLabelText('Nueva salida') as HTMLInputElement;
    expect(llegada.min).toBe(hoyIso());
    expect(salida.min).toBe('2030-06-11');

    fireEvent.change(llegada, { target: { value: '2030-06-15' } });
    expect(salida.min).toBe('2030-06-16');
  });

  it('explica que las fechas nuevas se guardan con las tarifas vigentes', async () => {
    sembrar();
    await montar();
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar fechas' }));

    expect(screen.getByText('El precio total se recalcula con las tarifas vigentes para las fechas nuevas.')).toBeTruthy();
  });

  it('mueve las fechas y refresca la fila con lo guardado', async () => {
    sembrar();
    await montar();
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar fechas' }));
    fireEvent.change(screen.getByLabelText('Nueva llegada'), {
      target: { value: '2030-06-13' },
    });
    fireEvent.change(screen.getByLabelText('Nueva salida'), {
      target: { value: '2030-06-15' },
    });
    fireEvent.click(screen.getByText('Guardar fechas'));

    await waitFor(() => {
      expect(estado.postes.length).toBe(1);
    });
    expect(estado.postes[0]!.url).toBe('/api/mis-reservas/H-MIA1/fechas');
    expect(estado.postes[0]!.cuerpo).toMatchObject({
      llegada: '2030-06-13',
      salida: '2030-06-15',
    });
    await waitFor(() => {
      expect(screen.queryByText(fechaCorta('2030-06-13'))).not.toBeNull();
    });
  });

  it('si el servidor lo rechaza, avisa y deja las fechas como estaban', async () => {
    estado.fallaMover = true;
    sembrar();
    await montar();
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar fechas' }));
    fireEvent.change(screen.getByLabelText('Nueva llegada'), {
      target: { value: '2030-06-13' },
    });
    fireEvent.change(screen.getByLabelText('Nueva salida'), {
      target: { value: '2030-06-15' },
    });
    fireEvent.click(screen.getByText('Guardar fechas'));

    await waitFor(() => {
      expect(screen.queryByText(/esas fechas no están libres/)).not.toBeNull();
    });
    expect(screen.queryByText(fechaCorta('2030-06-10'))).not.toBeNull();
  });
});

describe('cambio de huespedes propio', () => {
  it('abre el formulario con el grupo actual y lo guarda', async () => {
    sembrar();
    await montar();
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar huéspedes' }));

    const campo = screen.getByLabelText('Cuántos vienen') as HTMLInputElement;
    expect(campo.value).toBe('1');
    fireEvent.change(campo, { target: { value: '2' } });
    fireEvent.click(screen.getByText('Guardar huéspedes'));

    await waitFor(() => {
      expect(estado.postes.length).toBe(1);
    });
    expect(estado.postes[0]!.url).toBe('/api/mis-reservas/H-MIA1/huespedes');
    expect(estado.postes[0]!.cuerpo).toEqual({ huespedes: 2 });
    // La lista se relee: la fila muestra el grupo que quedo guardado, no el tecleado.
    await waitFor(() => {
      const fila = screen.getByText('H-MIA1').closest('tr');
      expect(fila?.textContent).toContain('2');
    });
  });

  it('si el grupo no cabe, avisa y deja la reserva como estaba', async () => {
    estado.fallaGrupo = true;
    sembrar();
    await montar();
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar huéspedes' }));
    fireEvent.change(screen.getByLabelText('Cuántos vienen'), { target: { value: '4' } });
    fireEvent.click(screen.getByText('Guardar huéspedes'));

    await waitFor(() => {
      expect(screen.queryByText(/no admite 4 huéspedes/)).not.toBeNull();
    });
    const fila = screen.getByText('H-MIA1').closest('tr');
    expect(fila?.textContent).toContain('1');
  });
});
