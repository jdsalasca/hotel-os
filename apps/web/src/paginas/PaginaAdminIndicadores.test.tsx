import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PaginaAdminIndicadores } from './PaginaAdminIndicadores';

/**
 * El informe pertenece al período en pantalla: si el hotel cambia de mes antes de
 * que vuelva la lectura, la tardía se ignora en vez de pintar otro mes. Y un error
 * viejo no sobrevive a una relectura que sí funcionó.
 */
const estado = vi.hoisted(() => ({
  diferidas: [] as { url: string; resolver: (v: unknown) => void; rechazar: (e: unknown) => void }[],
}));

function diferida() {
  let resolver!: (v: unknown) => void;
  let rechazar!: (e: unknown) => void;
  const promesa = new Promise<unknown>((res, rej) => {
    resolver = res;
    rechazar = rej;
  });
  return { promesa, resolver, rechazar };
}

function informeDe(mes: string, nombre: string) {
  return {
    periodo: mes,
    indicadores: [
      {
        clave: 'f3_ocupacion',
        fase: 3,
        nombre,
        definicion: 'd',
        formula: 'f',
        fuente: 's',
        periodo: mes,
        unidad: 'porcentaje',
        resultado: 5,
        numerador: 3,
        denominador: 60,
        tieneResultado: true,
        datosFaltantes: null,
        lineaBase: null,
        meta: null,
        responsable: null,
        nota: null,
      },
    ],
    reservasPorCanal: {},
    actividades: [],
  };
}

vi.mock('../api/cliente', () => ({
  api: {
    get: (url: string) => {
      if (url.startsWith('/api/admin/indicadores?')) {
        const d = diferida();
        estado.diferidas.push({ url, resolver: d.resolver, rechazar: d.rechazar });
        return d.promesa;
      }
      throw new Error('ruta no esperada: ' + url);
    },
    post: () => Promise.resolve({}),
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
  estado.diferidas.length = 0;
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

async function montar() {
  sesionConectada();
  render(
    <MemoryRouter>
      <PaginaAdminIndicadores />
    </MemoryRouter>,
  );
  await waitFor(() => {
    expect(screen.getByLabelText('Periodo', { selector: '#periodo' })).toBeTruthy();
  });
}

describe('vigencia del informe', () => {
  it('cambiar de período ignora el informe tardío del mes viejo', async () => {
    await montar();
    fireEvent.change(screen.getByLabelText('Periodo', { selector: '#periodo' }), {
      target: { value: '2026-11' },
    });
    await waitFor(() => {
      expect(estado.diferidas.length).toBe(2);
    });
    const [vieja, nueva] = estado.diferidas;
    nueva!.resolver(informeDe('2026-11', 'Ocupación de noviembre'));
    await waitFor(() => {
      expect(screen.queryByText('Ocupación de noviembre')).not.toBeNull();
    });
    vieja!.resolver(informeDe('2026-10', 'Ocupación de octubre'));
    // La tardía ya tuvo tiempo de pintar: si octubre aparece es porque no se ignoró.
    await new Promise((r) => setTimeout(r, 300));
    expect(screen.queryByText('Ocupación de octubre')).toBeNull();
    expect(screen.queryByText('Ocupación de noviembre')).not.toBeNull();
  });

  it('un error viejo no sobrevive a una relectura que sí funcionó', async () => {
    await montar();
    await waitFor(() => {
      expect(estado.diferidas.length).toBe(1);
    });
    estado.diferidas[0]!.rechazar(new Error('se cayó la red'));
    await waitFor(() => {
      expect(screen.queryByText('se cayó la red')).not.toBeNull();
    });
    fireEvent.change(screen.getByLabelText('Periodo', { selector: '#periodo' }), {
      target: { value: '2026-11' },
    });
    await waitFor(() => {
      expect(estado.diferidas.length).toBe(2);
    });
    estado.diferidas[1]!.resolver(informeDe('2026-11', 'Ocupación de noviembre'));
    await waitFor(() => {
      expect(screen.queryByText('Ocupación de noviembre')).not.toBeNull();
    });
    expect(screen.queryByText('se cayó la red')).toBeNull();
  });
});
