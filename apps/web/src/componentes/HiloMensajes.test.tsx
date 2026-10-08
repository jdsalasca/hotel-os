import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { HiloMensajes } from './HiloMensajes';

const HILO = {
  mensajes: [
    { id: 1, autor: 'HOTEL', texto: 'Hola, ¿en qué te ayudo?', en: '2026-10-08T10:00:00', visto: true },
    { id: 2, autor: 'HUESPED', texto: '¿Tienen cuna?', en: '2026-10-08T10:05:00', visto: false },
  ],
};

describe('HiloMensajes', () => {
  afterEach(() => {
    cleanup();
  });
  it('muestra lo propio a la derecha y lo ajeno a la izquierda', async () => {
    render(
      <HiloMensajes
        titulo="Chat"
        ladoPropio="HUESPED"
        cargar={() => Promise.resolve(HILO)}
        enviar={() => Promise.resolve()}
      />,
    );
    await waitFor(() => {
      expect(screen.getByText('¿Tienen cuna?')).toBeTruthy();
    });
    const propios = document.querySelectorAll('.hilo__mensaje--propio');
    expect(propios.length).toBe(1);
    expect(propios[0]?.textContent).toContain('¿Tienen cuna?');
  });

  it('envía el texto y limpia la caja', async () => {
    const enviar = vi.fn(() => Promise.resolve());
    const cargar = vi.fn(() => Promise.resolve(HILO));
    render(<HiloMensajes titulo="Chat" ladoPropio="HOTEL" cargar={cargar} enviar={enviar} />);
    const caja = (await screen.findByPlaceholderText('Escribe tu mensaje…')) as HTMLInputElement;
    fireEvent.change(caja, { target: { value: 'Sí, la dejamos lista' } });
    fireEvent.click(screen.getByText('Enviar'));
    await waitFor(() => {
      expect(enviar).toHaveBeenCalledWith('Sí, la dejamos lista');
    });
    expect(caja.value).toBe('');
  });

  it('no envía la caja vacía', async () => {
    const enviar = vi.fn(() => Promise.resolve());
    render(
      <HiloMensajes
        titulo="Chat"
        ladoPropio="HOTEL"
        cargar={() => Promise.resolve({ mensajes: [] })}
        enviar={enviar}
      />,
    );
    await screen.findByText('Sin mensajes todavía: escribe el primero y te responden por aquí.');
    fireEvent.click(screen.getByText('Enviar'));
    expect(enviar).not.toHaveBeenCalled();
  });

  it('avisa cuando hay más historia de la mostrada', async () => {
    render(
      <HiloMensajes
        titulo="Chat"
        ladoPropio="HUESPED"
        cargar={() => Promise.resolve({ mensajes: HILO.mensajes, total: 60 })}
        enviar={() => Promise.resolve()}
      />,
    );
    await screen.findByText('Mostrando los últimos 2 de 60 mensajes.');
  });

  it('carga anteriores y los pone arriba del hilo', async () => {
    const primera = {
      mensajes: [
        { id: 51, autor: 'HOTEL', texto: 'msg 51', en: '2026-10-08T10:00:00', visto: true },
        { id: 52, autor: 'HUESPED', texto: 'msg 52', en: '2026-10-08T10:05:00', visto: true },
      ],
      total: 60,
      hay_mas: true,
    };
    const anteriores = {
      mensajes: [
        { id: 50, autor: 'HOTEL', texto: 'msg 50', en: '2026-10-08T09:55:00', visto: true },
      ],
      total: 60,
      hay_mas: false,
    };
    const cargar = vi.fn().mockResolvedValueOnce(primera).mockResolvedValueOnce(anteriores);
    render(
      <HiloMensajes
        titulo="Chat"
        ladoPropio="HUESPED"
        cargar={cargar}
        enviar={() => Promise.resolve()}
      />,
    );
    await screen.findByText('msg 52');
    fireEvent.click(screen.getByText('Cargar anteriores'));
    await waitFor(() => {
      expect(cargar).toHaveBeenCalledWith(51);
    });
    await screen.findByText('msg 50');
    expect(screen.queryByText('Cargar anteriores')).toBeNull();
  });
});
