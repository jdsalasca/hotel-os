import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import { BotonWhatsApp } from './BotonWhatsApp';

function simularHotel(telefono: string | null) {
  globalThis.fetch = vi.fn(async () =>
    new Response(JSON.stringify(telefono === null ? {} : { contacto_telefono: telefono }), {
      status: 200,
    }),
  ) as unknown as typeof fetch;
}

describe('BotonWhatsApp', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('arma el enlace con el código de la reserva', async () => {
    simularHotel('3201234567');
    render(<BotonWhatsApp codigo="H-ABC123" />);
    const enlace = await screen.findByText('¿Dudas? Escríbenos por WhatsApp');
    expect(enlace.getAttribute('href')).toContain('https://wa.me/573201234567');
    expect(enlace.getAttribute('href')).toContain('H-ABC123');
  });

  it('no se muestra sin teléfono del hotel', async () => {
    simularHotel('');
    const { container } = render(<BotonWhatsApp codigo="H-ABC123" />);
    await waitFor(() => {
      expect(container.textContent ?? '').not.toContain('WhatsApp');
    });
  });
});
