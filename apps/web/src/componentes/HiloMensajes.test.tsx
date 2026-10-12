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
  it('la respuesta del otro lado llega sola, sin tener que pulsar Actualizar', async () => {
    // Un chat que hay que refrescar a mano se siente roto: el hotel responde y el huésped no
    // se entera hasta que pulsa. Aquí llega una respuesta nueva sin que el test toque el botón.
    vi.useFakeTimers();
    try {
      const conRespuesta = {
        mensajes: [
          HILO.mensajes[0],
          HILO.mensajes[1],
          { id: 3, autor: 'HUESPED', texto: 'Y servicio de traslado', en: '2026-10-08T10:09:00', visto: false },
        ],
      };
      let veces = 0;
      const cargar = vi.fn(() => {
        veces += 1;
        return Promise.resolve(veces === 1 ? HILO : conRespuesta);
      });
      render(
        <HiloMensajes titulo="Chat" ladoPropio="HOTEL" cargar={cargar} enviar={() => Promise.resolve()} />,
      );
      // La primera lectura es la del montaje.
      await vi.waitFor(() => expect(cargar).toHaveBeenCalledTimes(1));

      vi.advanceTimersByTime(60000);

      await vi.waitFor(() => expect(screen.getByText('Y servicio de traslado')).toBeTruthy());
      // Sin haber pulsado nada, salvo el intervalo.
      expect(screen.queryByText('Actualizar')).toBeTruthy();
    } finally {
      vi.useRealTimers();
    }
  });

  it('con la pestaña oculta deja de sondear', async () => {
    // Quien atiende el panel con la pestaña en segundo plano no debe pagar sondeo por cada
    // conversación abierta: en un hotel son muchas. Primero se ve que el sondeo está vivo,
    // luego que ocultar la pestaña lo para.
    vi.useFakeTimers();
    try {
      const cargar = vi.fn(() => Promise.resolve(HILO));
      const ocultar = vi.spyOn(document, 'visibilityState', 'get');
      ocultar.mockReturnValue('visible');
      render(
        <HiloMensajes titulo="Chat" ladoPropio="HOTEL" cargar={cargar} enviar={() => Promise.resolve()} />,
      );
      await vi.waitFor(() => expect(cargar).toHaveBeenCalledTimes(1));

      // Con la pestaña a la vista, el sondeo corre.
      vi.advanceTimersByTime(200000);
      await vi.waitFor(() => expect(cargar.mock.calls.length).toBeGreaterThan(1));
      const sondesandOculto = cargar.mock.calls.length;

      // Al ocultarla, se para: no se gasta ni una petición más.
      ocultar.mockReturnValue('hidden');
      vi.advanceTimersByTime(400000);
      expect(cargar.mock.calls.length).toBe(sondesandOculto);
      ocultar.mockRestore();
    } finally {
      vi.useRealTimers();
    }
  });

  it('avisar de los mensajes nuevos que llegaron mientras leías arriba', async () => {
    // Si el huésped subió a releer algo antiguo y el hotel responde, la respuesta entra al
    // final sin avisar: nunca la vería sin bajar. El aviso lo saca del fondo.
    vi.useFakeTimers();
    try {
      // El hilo es alto y la ventana corta: el usuario queda en el medio, no al final.
      medirAlturaDeHilo();
      const prototipo = HTMLUListElement.prototype;
      const scrollTopOriginal = Object.getOwnPropertyDescriptor(prototipo, 'scrollTop');
      Object.defineProperty(prototipo, 'scrollTop', {
        configurable: true,
        set(this: HTMLUListElement, valor: number) {
          this.dataset.scrollTop = String(valor);
        },
        get(this: HTMLUListElement) {
          return this.dataset.scrollTop === undefined ? 0 : Number(this.dataset.scrollTop);
        },
      });
      restaurarMediciones.push(() => {
        if (scrollTopOriginal) Object.defineProperty(prototipo, 'scrollTop', scrollTopOriginal);
        else Reflect.deleteProperty(prototipo, 'scrollTop');
      });

      const larga = {
        mensajes: [
          { id: 1, autor: 'HUESPED', texto: 'Mensaje inicial muy arriba', en: '2026-10-08T10:00:00', visto: true },
          { id: 2, autor: 'HOTEL', texto: 'Respuesta', en: '2026-10-08T10:01:00', visto: true },
          { id: 3, autor: 'HUESPED', texto: 'Otro mensaje', en: '2026-10-08T10:02:00', visto: true },
          { id: 4, autor: 'HOTEL', texto: 'Y otra respuesta', en: '2026-10-08T10:03:00', visto: true },
          { id: 5, autor: 'HUESPED', texto: 'Respuesta nueva del hotel', en: '2026-10-08T10:04:00', visto: true },
        ],
      };
      let veces = 0;
      const cargar = vi.fn(() => {
        veces += 1;
        return Promise.resolve(veces === 1 ? { mensajes: larga.mensajes.slice(0, 4) } : larga);
      });
      render(
        <HiloMensajes titulo="Chat" ladoPropio="HUESPED" cargar={cargar} enviar={() => Promise.resolve()} />,
      );
      await vi.waitFor(() => expect(screen.getByText('Respuesta')).toBeTruthy());
      // El hilo abre al final. Para reproducir al que sube a releer algo antiguo, hay que
      // subirlo de verdad: si se queda al final, la respuesta entra a la vista y avisar es ruido.
      const listaInicial = document.querySelector('.hilo__lista') as HTMLElement;
      listaInicial.dataset.scrollTop = '0';

      vi.advanceTimersByTime(60000);

      // El aviso existe y nombra los mensajes que no ha visto.
      await vi.waitFor(() => expect(screen.getByText(/1 mensaje nuevo/i)).toBeTruthy());
      // Y lleva al fondo, que es donde está la respuesta.
      fireEvent.click(screen.getByText(/1 mensaje nuevo/i));
      await vi.waitFor(() => {
        const lista = document.querySelector('.hilo__lista') as HTMLElement;
        expect(lista.dataset.scrollTop).not.toBe('0');
      });
    } finally {
      vi.useRealTimers();
    }
  });

  it('el aviso no se borra solo mientras nadie baje a mirar', async () => {
    // El primer sondeo enterró el aviso porque lo recalculaba sobre lo que ya había en
    // pantalla. La respuesta seguía ahí sin que nadie la viera y el aviso desaparecía.
    vi.useFakeTimers();
    try {
      medirAlturaDeHilo();
      const prototipo = HTMLUListElement.prototype;
      const scrollTopOriginal = Object.getOwnPropertyDescriptor(prototipo, 'scrollTop');
      Object.defineProperty(prototipo, 'scrollTop', {
        configurable: true,
        set(this: HTMLUListElement, valor: number) { this.dataset.scrollTop = String(valor); },
        get(this: HTMLUListElement) {
          return this.dataset.scrollTop === undefined ? 0 : Number(this.dataset.scrollTop);
        },
      });
      restaurarMediciones.push(() => {
        if (scrollTopOriginal) Object.defineProperty(prototipo, 'scrollTop', scrollTopOriginal);
        else Reflect.deleteProperty(prototipo, 'scrollTop');
      });

      const cuatro = {
        mensajes: [
          { id: 1, autor: 'HUESPED', texto: 'Mensaje inicial muy arriba', en: '2026-10-08T10:00:00', visto: true },
          { id: 2, autor: 'HOTEL', texto: 'Respuesta', en: '2026-10-08T10:01:00', visto: true },
          { id: 3, autor: 'HUESPED', texto: 'Otro mensaje', en: '2026-10-08T10:02:00', visto: true },
          { id: 4, autor: 'HOTEL', texto: 'Y otra respuesta', en: '2026-10-08T10:03:00', visto: true },
        ],
      };
      const cinco = { mensajes: [...cuatro.mensajes, { id: 5, autor: 'HUESPED', texto: 'Respuesta nueva', en: '2026-10-08T10:04:00', visto: true }] };
      let veces = 0;
      const cargar = vi.fn(() => {
        veces += 1;
        return Promise.resolve(veces === 1 ? cuatro : cinco);
      });
      render(
        <HiloMensajes titulo="Chat" ladoPropio="HUESPED" cargar={cargar} enviar={() => Promise.resolve()} />,
      );
      await vi.waitFor(() => expect(screen.getByText('Respuesta')).toBeTruthy());
      (document.querySelector('.hilo__lista') as HTMLElement).dataset.scrollTop = '0';

      // Dos ciclos de sondeo completos sin que nadie baje.
      vi.advanceTimersByTime(90000);
      await vi.waitFor(() => expect(screen.getByText(/1 mensaje nuevo/i)).toBeTruthy());
      // El aviso sigue: bajar a mirar lo borra, el reloj no.
      expect(screen.getByText(/1 mensaje nuevo/i)).toBeTruthy();
    } finally {
      vi.useRealTimers();
    }
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
