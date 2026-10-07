import { useEffect, useState } from 'react';
import { api } from '../api/cliente';

/**
 * Datos de contacto del hotel para ejercer derechos. Si la API falla, la página se lee igual:
 * el contacto es un extra, no el contenido.
 */
export function useContactoHotel(): string {
  const [contacto, setContacto] = useState('');
  useEffect(() => {
    void api
      .get<Record<string, string>>('/api/hotel')
      .then((datos) => {
        const partes = [datos.contacto_email, datos.contacto_telefono].filter(
          (d) => d && d.trim().length > 0,
        );
        setContacto(partes.join(' · '));
      })
      .catch(() => setContacto(''));
  }, []);
  return contacto;
}
