import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { HiloMensajes } from './HiloMensajes';

const HILO = {
  mensajes: [
    { id: 1, autor: 'HOTEL', texto: 'Hola, ¿en qué te ayudo?', en: '2026-10-08T10:00:00', visto: true },
    { id: 2, autor: 'HUESPED', texto: '¿Tienen cuna?', en: '2026-10-08T10:05:00', visto: false },
  ],
};

const restaurarMediciones: Array<() => void> = [];

function medirAlturaDeHilo() {
  const prototipo = HTMLUListElement.prototype;
  const scrollHeightOriginal = Object.getOwnPropertyDescriptor(prototipo, 'scrollHeight');
  const clientHeightOriginal = Object.getOwnPropertyDescriptor(prototipo, 'clientHeight');
  Object.defineProperty(prototipo, 'scrollHeight', {
    configurable: true,
    get(this: HTMLUListElement) {
      return this.querySelectorAll('li').length * 80;
    },
  });
  Object.defineProperty(prototipo, 'clientHeight', { configurable: true, get: () => 120 });
  restaurarMediciones.push(() => {
    if (scrollHeightOriginal) Object.defineProperty(prototipo, 'scrollHeight', scrollHeightOriginal);
    else Reflect.deleteProperty(prototipo, 'scrollHeight');
    if (clientHeightOriginal) Object.defineProperty(prototipo, 'clientHeight', clientHeightOriginal);
    else Reflect.deleteProperty(prototipo, 'clientHeight');
  });
}

describe('HiloMensajes', () => {
  afterEach(() => {
    cleanup();
    while (restaurarMediciones.length > 0) restaurarMediciones.pop()?.();
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
    medirAlturaDeHilo();
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
    const lista = screen.getByRole('list');
    lista.scrollTop = 12;
    fireEvent.click(screen.getByText('Cargar anteriores'));
    await waitFor(() => {
      expect(cargar).toHaveBeenCalledWith(51);
    });
    await screen.findByText('msg 50');
    expect(lista.scrollTop).toBe(92);
    expect(screen.queryByText('Cargar anteriores')).toBeNull();
  });

  it('abre al final del hilo y conserva el punto de lectura al actualizar', async () => {
    medirAlturaDeHilo();
    const mensajes = [
      { id: 1, autor: 'HOTEL', texto: 'Respuesta anterior', en: '2026-10-08T10:00:00', visto: true },
      { id: 2, autor: 'HUESPED', texto: 'Pregunta reciente', en: '2026-10-08T10:05:00', visto: false },
    ];
    const cargar = vi.fn()
      .mockResolvedValueOnce({ mensajes })
      .mockResolvedValueOnce({ mensajes: [...mensajes, { id: 3, autor: 'HOTEL', texto: 'Nueva respuesta', en: '2026-10-08T10:10:00', visto: false }] });
    render(
      <HiloMensajes titulo="Chat" ladoPropio="HUESPED" cargar={cargar} enviar={() => Promise.resolve()} />,
    );

    const lista = await screen.findByRole('list');
    await screen.findByText('Pregunta reciente');
    expect(lista.scrollTop).toBe(40);
    lista.scrollTop = 0;
    fireEvent.click(screen.getByRole('button', { name: 'Actualizar' }));
    await screen.findByText('Nueva respuesta');
    expect(lista.scrollTop).toBe(0);
  });

  it('baja hasta el mensaje recién enviado', async () => {
    medirAlturaDeHilo();
    const mensajes = [
      { id: 1, autor: 'HOTEL', texto: 'Hola', en: '2026-10-08T10:00:00', visto: true },
      { id: 2, autor: 'HUESPED', texto: '¿Tienen desayuno?', en: '2026-10-08T10:05:00', visto: false },
    ];
    const cargar = vi.fn()
      .mockResolvedValueOnce({ mensajes })
      .mockResolvedValueOnce({ mensajes: [...mensajes, { id: 3, autor: 'HUESPED', texto: 'Gracias', en: '2026-10-08T10:10:00', visto: false }] });
    render(
      <HiloMensajes titulo="Chat" ladoPropio="HUESPED" cargar={cargar} enviar={() => Promise.resolve()} />,
    );

    const lista = await screen.findByRole('list');
    fireEvent.change(await screen.findByPlaceholderText('Escribe tu mensaje…'), {
      target: { value: 'Gracias' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Enviar' }));
    await screen.findByText('Gracias');
    expect(lista.scrollTop).toBe(120);
  });

  it('indica si el hotel ya leyó cada mensaje propio del huésped', async () => {
    render(
      <HiloMensajes
        titulo="Chat"
        ladoPropio="HUESPED"
        cargar={() =>
          Promise.resolve({
            mensajes: [
              { id: 10, autor: 'HUESPED', texto: '¿Puedo llegar antes?', en: '2026-10-08T10:10:00', visto: false },
              { id: 11, autor: 'HUESPED', texto: 'Gracias', en: '2026-10-08T10:15:00', visto: true },
              { id: 12, autor: 'HOTEL', texto: 'Claro', en: '2026-10-08T10:20:00', visto: true },
            ],
          })
        }
        enviar={() => Promise.resolve()}
      />,
    );

    const pendiente = await screen.findByText('¿Puedo llegar antes?');
    const visto = screen.getByText('Gracias');
    const recibido = screen.getByText('Claro');
    expect(pendiente.closest('li')?.textContent).toContain('Enviado · pendiente de lectura');
    expect(visto.closest('li')?.textContent).toContain('Visto por el hotel');
    expect(recibido.closest('li')?.textContent).not.toContain('Visto por');
  });

  it('indica cuando el huésped ya leyó el mensaje propio del hotel', async () => {
    render(
      <HiloMensajes
        titulo="Chat"
        ladoPropio="HOTEL"
        cargar={() =>
          Promise.resolve({
            mensajes: [
              { id: 13, autor: 'HOTEL', texto: 'La habitación estará lista a las tres', en: '2026-10-08T10:25:00', visto: true },
              { id: 14, autor: 'HUESPED', texto: 'Perfecto, gracias', en: '2026-10-08T10:30:00', visto: true },
            ],
          })
        }
        enviar={() => Promise.resolve()}
      />,
    );

    const propio = await screen.findByText('La habitación estará lista a las tres');
    const recibido = screen.getByText('Perfecto, gracias');
    expect(propio.closest('li')?.textContent).toContain('Visto por el huésped');
    expect(recibido.closest('li')?.textContent).not.toContain('Visto por');
  });
});
