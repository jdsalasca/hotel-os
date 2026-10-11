import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { PaginaReserva } from './PaginaReserva';

const api = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

const sesionHuesped = vi.hoisted(() => ({
  haySesion: false as boolean | null,
  email: '',
  nombre: '',
}));

vi.mock('../api/cliente', () => ({
  api,
  ErrorApi: class ErrorApi extends Error {},
}));

vi.mock('../api/useSesionHuesped', () => ({
  useSesionHuesped: () => ({ ...sesionHuesped, comprobar: vi.fn() }),
}));

vi.mock('../api/useSesion', () => ({
  useSesion: () => ({ haySesion: false, email: '', nombre: '', comprobar: vi.fn() }),
}));

function EstadoNavegacion() {
  const location = useLocation();
  return <output aria-label="Estado de navegación">{JSON.stringify(location.state)}</output>;
}

function prepararReserva() {
  sessionStorage.setItem('reserva-en-curso', JSON.stringify({
    habitacion: { id: 5, codigo: '101', nombre: 'Hab 101' },
    tipo: { nombre: 'Doble' },
    totalCents: 300000,
    moneda: 'COP',
    noches: 2,
    llegada: '2030-06-10',
    salida: '2030-06-12',
    huespedes: 2,
    clave: 'clave-test',
  }));
}

describe('PaginaReserva', () => {
  beforeEach(() => {
    sessionStorage.clear();
    prepararReserva();
    sesionHuesped.haySesion = false;
    sesionHuesped.email = '';
    sesionHuesped.nombre = '';
    api.get.mockResolvedValue({});
    api.post.mockResolvedValue({});
  });

  afterEach(() => {
    cleanup();
    sessionStorage.clear();
    vi.clearAllMocks();
  });

  it('presenta el resumen de la habitación antes de pedir los datos', () => {
    render(<MemoryRouter><PaginaReserva /></MemoryRouter>);

    const resumen = screen.getByRole('heading', { name: 'Tu selección' });
    const correo = screen.getByLabelText('Correo electrónico *');
    expect(Boolean(resumen.compareDocumentPosition(correo) & Node.DOCUMENT_POSITION_FOLLOWING)).toBe(true);
  });

  it('precarga la sesión del huésped y conserva los datos que edita', () => {
    sesionHuesped.haySesion = true;
    sesionHuesped.email = 'ana@hotel.test';
    sesionHuesped.nombre = 'Ana Pérez';
    const vista = render(<MemoryRouter><PaginaReserva /></MemoryRouter>);

    const correo = screen.getByLabelText('Correo electrónico *') as HTMLInputElement;
    const nombre = screen.getByLabelText('Nombre *') as HTMLInputElement;
    expect(correo.value).toBe('ana@hotel.test');
    expect(nombre.value).toBe('Ana Pérez');

    fireEvent.change(correo, { target: { value: 'ana.personal@correo.test' } });
    fireEvent.change(nombre, { target: { value: 'Ana Martínez' } });
    sesionHuesped.email = 'otra@hotel.test';
    sesionHuesped.nombre = 'Otro nombre';
    vista.rerender(<MemoryRouter><PaginaReserva /></MemoryRouter>);

    expect(correo.value).toBe('ana.personal@correo.test');
    expect(nombre.value).toBe('Ana Martínez');
  });

  it('enseña las condiciones del hotel antes de confirmar la reserva', async () => {
    api.get.mockImplementation((url: string) =>
      Promise.resolve(url === '/api/hotel'
        ? { hora_entrada: '15:00', hora_salida: '12:00', politica_cancelacion: 'Gratis hasta 24 h antes' }
        : {}));
    render(<MemoryRouter><PaginaReserva /></MemoryRouter>);

    const condiciones = await screen.findByRole('region', { name: /condiciones/i });
    expect(condiciones.textContent).toContain('15:00');
    expect(condiciones.textContent).toContain('12:00');
    expect(condiciones.textContent).toContain('Gratis hasta 24 h antes');
  });

  it('no inventa condiciones: si el hotel no ha configurado nada, no muestra el bloque', async () => {
    api.get.mockResolvedValue({});
    render(<MemoryRouter><PaginaReserva /></MemoryRouter>);

    await screen.findByRole('heading', { name: 'Tu selección' });
    expect(screen.queryByRole('region', { name: /condiciones/i })).toBeNull();
  });

  it('copia el código de reserva de un clic y lo confirma', async () => {
    // Quien reserva sin cuenta solo se lleva el código: si no lo puede copiar tiene que
    // anotarlo a mano, y un H-0/l mal transcrito se pierde.
    const escribir = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText: escribir } });
    api.post.mockResolvedValue({
      codigo: 'H-A1B2C3',
      mensaje: 'Solicitud recibida',
    });
    render(<MemoryRouter><PaginaReserva /></MemoryRouter>);

    const correo = await screen.findByLabelText('Correo electrónico *');
    fireEvent.change(correo, { target: { value: 'ana@hotel.test' } });
    const nombre = screen.getByLabelText('Nombre *');
    fireEvent.change(nombre, { target: { value: 'Ana' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar solicitud de reserva' }));

    const copiar = await screen.findByRole('button', { name: /copiar código/i });
    fireEvent.click(copiar);

    expect(escribir).toHaveBeenCalledWith('H-A1B2C3');
    // Y se dice que se copió: si no, el huésped no sabe si funcionó y lo intentará otra vez.
    expect(await screen.findByText(/copiado/i)).toBeTruthy();
  });

  it('si el navegador no deja copiar, el código queda seleccionable a mano', async () => {
    // El portapapeles puede estar bloqueado (permiso, http antiguo). Antes de rendirse, el
    // código se selecciona: eso siempre funciona y evita perder la reserva.
    Object.assign(navigator, {
      clipboard: {
        writeText: vi.fn().mockRejectedValue(new Error('bloqueado')),
      },
    });
    const seleccionar = vi.fn();
    (window as unknown as { getSelection: unknown }).getSelection = () => ({
      removeAllRanges: vi.fn(),
      addRange: seleccionar,
    }) as unknown as Selection;
    api.post.mockResolvedValue({ codigo: 'H-A1B2C3', mensaje: 'Solicitud recibida' });
    render(<MemoryRouter><PaginaReserva /></MemoryRouter>);

    const correo = await screen.findByLabelText('Correo electrónico *');
    fireEvent.change(correo, { target: { value: 'ana@hotel.test' } });
    fireEvent.change(screen.getByLabelText('Nombre *'), { target: { value: 'Ana' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar solicitud de reserva' }));

    fireEvent.click(await screen.findByRole('button', { name: /copiar código/i }));

    // El aviso honesto: no se copió, pero se puede seleccionar a mano.
    expect(await screen.findByText(/seleccionado/i)).toBeTruthy();
  });

  it('devuelve a la búsqueda con las fechas y huéspedes elegidos', () => {
    render(<MemoryRouter><PaginaReserva /><EstadoNavegacion /></MemoryRouter>);

    fireEvent.click(screen.getByRole('link', { name: 'Cambiar fechas' }));

    expect(screen.getByLabelText('Estado de navegación').textContent).toContain(
      '"restaurarBusqueda":{"llegada":"2030-06-10","salida":"2030-06-12","huespedes":2}',
    );
  });

  it('marca y enfoca el correo cuando falta o tiene un formato inválido', () => {
    render(<MemoryRouter><PaginaReserva /></MemoryRouter>);

    const correo = screen.getByLabelText('Correo electrónico *') as HTMLInputElement;
    fireEvent.change(correo, { target: { value: 'sin-correo' } });
    fireEvent.change(screen.getByLabelText('Nombre *'), { target: { value: 'Ana' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar solicitud de reserva' }));

    expect(screen.getByText('Escribe un correo electrónico válido.')).toBeTruthy();
    expect(correo.getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(correo);
    expect(api.post).not.toHaveBeenCalled();
  });

  it('indica en el campo si falta el nombre y no envía la reserva', () => {
    render(<MemoryRouter><PaginaReserva /></MemoryRouter>);

    fireEvent.change(screen.getByLabelText('Correo electrónico *'), {
      target: { value: 'ana@hotel.test' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar solicitud de reserva' }));

    const nombre = screen.getByLabelText('Nombre *') as HTMLInputElement;
    expect(screen.getByText('Escribe tu nombre.')).toBeTruthy();
    expect(nombre.getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(nombre);
    expect(api.post).not.toHaveBeenCalled();
  });
});
