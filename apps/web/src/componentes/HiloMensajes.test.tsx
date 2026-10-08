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
});
