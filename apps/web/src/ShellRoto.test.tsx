import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { App } from './App';

/**
 * `MuroAntipanico` envuelve a `<Routes>` y por ahi todo fallo queda atrapado. La cabecera
 * (saludo, insignia de mensajes y cierre de sesion) y el pie quedan FUERA de ese muro, y
 * React no propaga hacia arriba el error de un `useEffect`: se desmonta el arbol entero y
 * el huesped se queda con una pantalla en blanco, sin mensaje y sin salida.
 *
 * Estos tests fijan ese hueco: aunque la cabecera falle, la pagina debe conservar su
 * contenido y ofrecer una salida.
 */
function romperEfectoDeLaCabecera() {
  window.scrollTo = (() => {
    throw new Error('fallo en la cabecera');
  }) as unknown as typeof window.scrollTo;
}

function fetchDeHuesped() {
  globalThis.fetch = vi.fn(async (url: unknown) => {
    const ruta = String(url);
    if (ruta.includes('/api/hotel')) return new Response('{}', { status: 200 });
    if (ruta.includes('/api/lugares')) return new Response('{"hotel":{},"lugares":[]}', { status: 200 });
    if (ruta.includes('/api/amenidades')) return new Response('{}', { status: 200 });
    if (ruta.includes('/api/disponibilidad')) return new Response('{"dias":[]}', { status: 200 });
    return new Response('{"error":"sin sesion"}', { status: 401 });
  }) as unknown as typeof fetch;
}

describe('el shell sobrevive a un fallo propio', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    // window.scrollTo se sustituye a mano (no es un mock de vi), asi que restoreAllMocks
    // no lo devuelve. Se restaura uno que no hace nada: si se deja en undefined, el
    // "volver arriba" revienta por sí solo y falsea el resto de tests.
    window.scrollTo = (() => {}) as unknown as typeof window.scrollTo;
  });

  it('un fallo del shell ya no deja la pantalla en blanco, da una salida', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    romperEfectoDeLaCabecera();
    fetchDeHuesped();

    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );

    // Antes, este fallo (un useEffect del shell) tumbaba el árbol entero: pantalla en
    // blanco, sin mensaje y sin salida. Ahora hay un aviso y una forma de reintentar.
    expect(await screen.findByText(/algo se rompió/i)).toBeTruthy();
    expect(screen.getByRole('button', { name: /recargar/i })).toBeTruthy();
  });

  it('sin fallos, la cabecera y las rutas siguen mostrando su contenido', async () => {
    // El stub no hace nada es el estado normal: aqui no se rompe nada.
    window.scrollTo = (() => {}) as unknown as typeof window.scrollTo;
    fetchDeHuesped();

    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );

    expect(await screen.findByText('¿Cuándo quieres venir?')).toBeTruthy();
    expect(screen.getByRole('link', { name: /reservar/i })).toBeTruthy();
  });
});
