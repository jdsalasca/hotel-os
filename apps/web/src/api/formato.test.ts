import { describe, expect, it } from 'vitest';
import { enlaceWhatsApp, nombreCorto } from './formato';

describe('enlaceWhatsApp', () => {
  it('móvil colombiano de 10 dígitos lleva 57 delante', () => {
    expect(enlaceWhatsApp('320 123 4567', 'Hola')).toBe(
      'https://wa.me/573201234567?text=Hola',
    );
  });

  it('limpia espacios, guiones y el más', () => {
    expect(enlaceWhatsApp('+57 (320) 123-4567', 'x')).toBe(
      'https://wa.me/573201234567?text=x',
    );
  });

  it('respeta el indicativo si ya viene', () => {
    expect(enlaceWhatsApp('521234567890', 'x')).toBe('https://wa.me/521234567890?text=x');
  });

  it('codifica el texto del mensaje', () => {
    expect(enlaceWhatsApp('3201234567', 'Hola, mi reserva es H-1')).toContain(
      'text=Hola%2C%20mi%20reserva%20es%20H-1',
    );
  });

  it('sin teléfono no hay enlace', () => {
    expect(enlaceWhatsApp('', 'x')).toBeNull();
    expect(enlaceWhatsApp('abc', 'x')).toBeNull();
  });
});

describe('nombreCorto', () => {
  it('prefiere el nombre y cae a lo de antes del @', () => {
    expect(nombreCorto('Juan Pérez', 'juan@hotel.test')).toBe('Juan Pérez');
    expect(nombreCorto('', 'savatar62@gmail.com')).toBe('savatar62');
    expect(nombreCorto('  ', 'a@b.co')).toBe('a');
  });

  it('sin nada muestra el correo tal cual', () => {
    expect(nombreCorto('', '')).toBe('');
    expect(nombreCorto('', 'sin-arroba')).toBe('sin-arroba');
  });
});
