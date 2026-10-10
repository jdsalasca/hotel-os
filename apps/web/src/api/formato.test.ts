import { describe, expect, it } from 'vitest';
import { enlaceWhatsApp, fechaCorta, nombreCorto } from './formato';

describe('fechaCorta', () => {
  it('usa fecha española compacta con el mes abreviado', () => {
    expect(fechaCorta('2030-06-10')).toBe('10 jun 2030');
  });
});

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
      expect(nombreCorto('', 'savatar62@gmail.com')).toBe('Savatar62');
      expect(nombreCorto('  ', 'a@b.co')).toBe('A');
    });

    it('sin nada muestra el correo tal cual', () => {
      expect(nombreCorto('', '')).toBe('');
      expect(nombreCorto('', 'sin-arroba')).toBe('Sin Arroba');
    });

    it('da forma de nombre a lo que sale del correo: "dueno@..." se lee "Dueno"', () => {
      expect(nombreCorto('', 'dueno@hotel.test')).toBe('Dueno');
      expect(nombreCorto('', 'savatar62@gmail.com')).toBe('Savatar62');
      // Separadores a espacio y mayúscula en cada palabra; los números se conservan.
      expect(nombreCorto('', 'ana.maria+reservas@hotel.test')).toBe('Ana Maria Reservas');
      expect(nombreCorto('', 'juan_perez-1@hotel.test')).toBe('Juan Perez 1');
      // Sin acentos que inventar: no hay forma de saber si el usuario escribe "dueno" o "dueño".
      expect(nombreCorto('', 'dueño@hotel.test')).toBe('Dueño');
    });
});
