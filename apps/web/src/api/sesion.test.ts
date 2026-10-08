import { describe, expect, it, vi } from 'vitest';
import { crearEstadoSesion } from './sesion';

describe('estado de sesión compartido', () => {
  it('dos lecturas a la vez hacen una sola petición', async () => {
    const pedir = vi.fn(async () => ({ email: 'a@h.test' }));
    const estado = crearEstadoSesion(pedir);
    const [una, otra] = await Promise.all([estado.leer(), estado.leer()]);
    expect(pedir).toHaveBeenCalledOnce();
    expect(una).toEqual(otra);
    expect(una.haySesion).toBe(true);
  });

  it('cada lectura pregunta de nuevo: la sesión no se cachea', async () => {
    const pedir = vi.fn(async () => ({ email: 'a@h.test' }));
    const estado = crearEstadoSesion(pedir);
    await estado.leer();
    await estado.leer();
    expect(pedir).toHaveBeenCalledTimes(2);
  });

  it('olvidar descarta lo que venga en camino', async () => {
    const pedir = vi.fn(async () => ({ email: 'a@h.test' }));
    const estado = crearEstadoSesion(pedir);
    const primera = estado.leer();
    estado.olvidar();
    const segunda = estado.leer();
    await Promise.all([primera, segunda]);
    expect(pedir).toHaveBeenCalledTimes(2);
  });

  it('si no hay sesión, el estado lo dice sin datos', async () => {
    const estado = crearEstadoSesion(async () => null);
    expect(await estado.leer()).toEqual({ haySesion: false, datos: null });
  });
});
