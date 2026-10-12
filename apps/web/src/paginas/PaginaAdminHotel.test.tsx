import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { PaginaAdminHotel } from './PaginaAdminHotel';

const estado = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock('../api/cliente', () => ({
  api: {
    get: estado.get,
    post: estado.post,
  },
}));

vi.mock('../api/useSesion', () => ({
  useSesion: () => ({ haySesion: true, email: 'demo@hotel.test', nombre: 'Hotel Demo' }),
}));

function diferida<T>() {
  let resolver!: (valor: T) => void;
  const promesa = new Promise<T>((res) => {
    resolver = res;
  });
  return { promesa, resolver };
}

function montar() {
  render(
    <MemoryRouter>
      <PaginaAdminHotel />
    </MemoryRouter>,
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('lo que bloquea la web, arriba del formulario', () => {
  it('con el GPS vacío, avisa arriba y abre el selector de un clic', async () => {
    // El panel manda aquí a arreglar el GPS. Los campos son los últimos del formulario, así
    // que el hotelero tenía que cazarlos: el aviso va arriba y abre el mapa directamente.
    estado.get.mockResolvedValue({ latitud: '', longitud: '' });
    montar();
    await waitFor(() => expect(estado.get).toHaveBeenCalled());

    const aviso = await screen.findByText(/el mapa no aparece/i);
    // El aviso está antes que los campos del formulario, no después.
    expect(
      Boolean(
        aviso.compareDocumentPosition(screen.getByRole('textbox', { name: 'Nombre del hotel' })) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ),
    ).toBe(true);

    // Y de la advertencia al mapa hay un solo clic.
    fireEvent.click(screen.getByRole('button', { name: /ubicar en el mapa/i }));
    expect(await screen.findByRole('button', { name: /usar este punto/i })).toBeTruthy();
  });

  it('con el GPS puesto, no avisa de nada', async () => {
    estado.get.mockResolvedValue({ latitud: '5.65', longitud: '-73.52' });
    montar();
    await waitFor(() => expect(estado.get).toHaveBeenCalled());

    await screen.findByRole('textbox', { name: 'Nombre del hotel' });
    expect(screen.queryByText(/el mapa no aparece/i)).toBeNull();
  });
});

describe('estado de la ficha del hotel', () => {
  it('distingue datos informados y pendientes con la configuración cargada', async () => {
    estado.get.mockResolvedValueOnce({
      nombre: 'Hotel Eridu',
      contacto_email: 'reservas@hotel.test',
      direccion: 'Calle 1, Villa de Leyva',
      moneda: 'COP',
      latitud: '5.63',
      longitud: '-73.52',
    });
    montar();

    const resumen = await screen.findByRole('region', { name: 'Estado de la ficha del hotel' });
    const filas = Array.from(resumen.querySelectorAll('li'));
    const correo = filas.find((fila) => fila.textContent?.includes('Correo de contacto'));
    const telefono = filas.find((fila) => fila.textContent?.includes('Teléfono de contacto'));

    expect(correo?.textContent).toContain('Informado');
    expect(telefono?.textContent).toContain('Pendiente');
    expect(resumen.textContent).toContain('Punto del hotel');
  });

  it('lleva el foco al campo del formulario desde un dato pendiente', async () => {
    estado.get.mockResolvedValueOnce({ nombre: 'Hotel Eridu' });
    montar();

    fireEvent.click(await screen.findByRole('button', { name: 'Completar correo de contacto' }));

    expect(document.activeElement).toBe(screen.getByLabelText('Correo de contacto'));
  });

  it('actualiza el estado del GPS al informar las coordenadas desde el selector', async () => {
    estado.get.mockResolvedValueOnce({ latitud: '', longitud: '' });
    montar();

    await screen.findByText(/el mapa no aparece en tu web/i);
    fireEvent.click(await screen.findByRole('button', { name: /ubicar en el mapa/i }));
    fireEvent.click(await screen.findByRole('button', { name: 'Norte' }));

    expect(screen.queryByText('El mapa no aparece en tu web')).toBeNull();
    expect(screen.getByRole('button', { name: 'Cerrar el mapa' })).toBeTruthy();
  });
});

describe('elegir el punto del hotel en el mapa', () => {
  it('conserva cero como coordenada válida al abrir el selector', async () => {
    estado.get.mockResolvedValue({ latitud: '0', longitud: '0' });
    montar();

    await screen.findByRole('textbox', { name: 'Nombre del hotel' });
    fireEvent.click(screen.getByRole('button', { name: /ubicar en el mapa/i }));

    const mapa = await screen.findByTitle('Mapa para elegir el punto: ubicación del hotel');
    expect(mapa.getAttribute('src')).toContain('marker=0%2C0');
  });

  it('usa el centro de referencia solo cuando las coordenadas están vacías', async () => {
    estado.get.mockResolvedValue({ latitud: '', longitud: '' });
    montar();

    await screen.findByText(/el mapa no aparece en tu web/i);
    fireEvent.click(screen.getByRole('button', { name: /ubicar en el mapa/i }));

    const mapa = await screen.findByTitle('Mapa para elegir el punto: ubicación del hotel');
    expect(mapa.getAttribute('src')).toContain('marker=5.65%2C-73.52');
  });

  it('mover el punto cambia lo que se guarda, sin depender de arrastrar el visor', async () => {
    estado.get.mockResolvedValue({ latitud: '', longitud: '' });
    montar();
    await waitFor(() => expect(estado.get).toHaveBeenCalled());

    fireEvent.click(await screen.findByRole('button', { name: /ubicar en el mapa/i }));
    const lat = () => (screen.getByLabelText(/Latitud del hotel/i) as HTMLInputElement).value;
    const lng = () => (screen.getByLabelText(/Longitud del hotel/i) as HTMLInputElement).value;

    // El punto verde es la fuente de verdad: moverlo cambia el valor, no solo la vista.
    fireEvent.click(screen.getByRole('button', { name: /^Norte$/i }));
    const trasNorte = Number(lat());
    expect(trasNorte).toBeGreaterThan(5.65);

    fireEvent.click(screen.getByRole('button', { name: /^Este$/i }));
    expect(Number(lng())).toBeGreaterThan(-73.52);

    // Guardar el punto deja los campos listos para enviar.
    fireEvent.click(screen.getByRole('button', { name: /usar este punto/i }));
    await waitFor(() => expect(lat()).toBe(String(trasNorte)));
  });
});

describe('configuración del hotel durante la carga', () => {
  it('bloquea la edición y el guardado hasta cargar los datos existentes', async () => {
    const lectura = diferida<Record<string, string>>();
    estado.get.mockReturnValue(lectura.promesa);
    montar();

    const nombre = screen.getByLabelText('Nombre del hotel') as HTMLInputElement;
    const politica = screen.getByLabelText('Política de cancelación') as HTMLTextAreaElement;
    const guardar = screen.getByRole('button', { name: 'Guardar datos del hotel' }) as HTMLButtonElement;

    expect(nombre.disabled).toBe(true);
    expect(politica.disabled).toBe(true);
    expect(guardar.disabled).toBe(true);
    expect(estado.post).not.toHaveBeenCalled();

    lectura.resolver({ nombre: 'Hotel ya configurado', politica_cancelacion: 'Avisar con 24 horas' });
    await waitFor(() => expect(nombre.value).toBe('Hotel ya configurado'));

    expect(politica.value).toBe('Avisar con 24 horas');
    expect(nombre.disabled).toBe(false);
    expect(politica.disabled).toBe(false);
    expect(guardar.disabled).toBe(false);
  });

  it('permite editar y guardar si la lectura inicial falla', async () => {
    estado.get.mockRejectedValueOnce(new Error('No hay conexión'));
    estado.post.mockResolvedValueOnce({ nombre: 'Hotel actualizado' });
    montar();

    const alerta = await screen.findByRole('alert');
    expect(alerta.textContent).toContain('No hay conexión');
    const nombre = screen.getByLabelText('Nombre del hotel') as HTMLInputElement;
    const guardar = screen.getByRole('button', { name: 'Guardar datos del hotel' }) as HTMLButtonElement;
    expect(nombre.disabled).toBe(false);
    expect(guardar.disabled).toBe(false);

    fireEvent.change(nombre, { target: { value: 'Hotel actualizado' } });
    fireEvent.click(guardar);
    await waitFor(() => expect(estado.post).toHaveBeenCalled());

    expect(estado.post).toHaveBeenCalledWith(
      '/api/admin/hotel-config',
      expect.objectContaining({ nombre: 'Hotel actualizado' }),
    );
    expect(await screen.findByText('Datos guardados')).toBeTruthy();
  });

  it('permite reingresar un código de moneda no listado si falla la lectura', async () => {
    estado.get.mockRejectedValueOnce(new Error('No hay conexión'));
    estado.post.mockResolvedValueOnce({ nombre: 'Hotel actualizado', moneda: 'CHF' });
    montar();

    await screen.findByRole('alert');
    const nombre = screen.getByLabelText('Nombre del hotel');
    const moneda = screen.getByLabelText('Moneda') as HTMLInputElement;
    expect(moneda.tagName).toBe('INPUT');
    expect(moneda.maxLength).toBe(3);

    fireEvent.change(nombre, { target: { value: 'Hotel actualizado' } });
    fireEvent.change(moneda, { target: { value: 'CHF' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar datos del hotel' }));

    await waitFor(() =>
      expect(estado.post).toHaveBeenCalledWith(
        '/api/admin/hotel-config',
        expect.objectContaining({ moneda: 'CHF' }),
      ),
    );
    const selector = (await screen.findByRole('combobox', { name: 'Moneda' })) as HTMLSelectElement;
    expect(selector.value).toBe('CHF');
  });

  it('permite elegir una moneda disponible para la configuración', async () => {
    estado.get.mockResolvedValueOnce({ nombre: 'Hotel Demo', moneda: 'COP' });
    estado.post.mockResolvedValueOnce({ nombre: 'Hotel Demo', moneda: 'USD' });
    montar();

    const moneda = await screen.findByRole('combobox', { name: 'Moneda' });
    expect(screen.getByRole('option', { name: 'USD — Dólar estadounidense' })).toBeTruthy();
    fireEvent.change(moneda, { target: { value: 'USD' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar datos del hotel' }));

    await waitFor(() =>
      expect(estado.post).toHaveBeenCalledWith(
        '/api/admin/hotel-config',
        expect.objectContaining({ moneda: 'USD' }),
      ),
    );
  });

  it('conserva el código configurado aunque no esté en las opciones habituales', async () => {
    estado.get.mockResolvedValueOnce({ nombre: 'Hotel Demo', moneda: 'CHF' });
    estado.post.mockResolvedValueOnce({ nombre: 'Hotel Demo', moneda: 'CHF' });
    montar();

    const moneda = (await screen.findByRole('combobox', { name: 'Moneda' })) as HTMLSelectElement;
    expect(moneda.value).toBe('CHF');
    expect(screen.getByRole('option', { name: 'Código actual (CHF)' })).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Guardar datos del hotel' }));
    await waitFor(() =>
      expect(estado.post).toHaveBeenCalledWith(
        '/api/admin/hotel-config',
        expect.objectContaining({ moneda: 'CHF' }),
      ),
    );
  });
});
