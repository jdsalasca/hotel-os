import { describe, expect, it, afterEach, vi } from 'vitest';
import { render, cleanup, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { App } from './App';
import { tituloDeRuta } from './tituloRuta';

/**
 * El documento HTML traia un titulo fijo, asi que en la pestaña del navegador todas las
 * paginas de la SPA se llamaban igual. Quien abre /mis-reservas en una pestana nueva, o
 * guarda la pagina, se queda con "Hotel Eridu - Reserva directa" sin saber donde esta.
 * Ademas la ruta inexistente respondia 200 con el mismo titulo.
 */
describe('titulo por ruta', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    window.scrollTo = (() => {}) as unknown as typeof window.scrollTo;
  });

  function stubDeHuesped() {
    window.scrollTo = (() => {}) as unknown as typeof window.scrollTo;
    globalThis.fetch = vi.fn(async (url: unknown) => {
      const ruta = String(url);
      if (ruta.includes('/api/hotel')) return new Response('{}', { status: 200 });
      if (ruta.includes('/api/lugares')) return new Response('{"hotel":{},"lugares":[]}', { status: 200 });
      if (ruta.includes('/api/amenidades')) return new Response('{}', { status: 200 });
      if (ruta.includes('/api/disponibilidad')) return new Response('{"dias":[]}', { status: 200 });
      return new Response('{"error":"sin sesion"}', { status: 401 });
    }) as unknown as typeof fetch;
  }

  it('la portada conserva el titulo del hotel', async () => {
    stubDeHuesped();
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    );

    await waitFor(() =>
      expect(document.title).toContain('Hotel Eridu'));
  });

  it('mis reservas se anuncia como tal y no como la portada', async () => {
    stubDeHuesped();
    render(
      <MemoryRouter initialEntries={['/mis-reservas']}>
        <App />
      </MemoryRouter>,
    );

    await waitFor(() => expect(document.title).toContain('Mis reservas'));
    expect(document.title).not.toBe('Hotel Eridu · Reserva directa');
  });

  it('una ruta que no existe no se presenta como si fuera la portada', async () => {
    stubDeHuesped();
    render(
      <MemoryRouter initialEntries={['/no-existe']}>
        <App />
      </MemoryRouter>,
    );

    await waitFor(() => expect(document.title).toMatch(/no encontrada/i));
    expect(document.title).not.toContain('Reserva directa · ');
  });

  it('la consulta de una reserva por codigo tiene su propio titulo', async () => {
    stubDeHuesped();
    render(
      <MemoryRouter initialEntries={['/consulta']}>
        <App />
      </MemoryRouter>,
    );

    await waitFor(() => expect(document.title).toContain('Consulta'));
  });

  it('una ruta nueva que no este en el mapa hereda el titulo de su seccion', () => {
    // Si manana se anade /admin/promociones y nadie lo anade al mapa, la pestana no puede
    // quedarse con el titulo de la portada: hereda el de la seccion del panel.
    expect(tituloDeRuta('/admin/promociones')).toContain('Panel');
    expect(tituloDeRuta('/')).toBe('Hotel Eridu · Reserva directa');
  });
});