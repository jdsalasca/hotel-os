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
});
