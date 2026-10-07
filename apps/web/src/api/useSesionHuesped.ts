import { useCallback, useEffect, useState } from 'react';
import { urlApi } from './cliente';

type Yo = { email: string; nombre: string; tieneReservas: boolean };

/**
 * Sesión del huésped (Google). El backend usa sesión con cookie HttpOnly, igual que el panel: aquí
 * solo se sabe quién es y si hay sesión. Nunca se guarda el token en localStorage.
 *
 * Es un hook aparte del del panel a propósito: uno pregunta "soy admin" y este "soy huésped", y el
 * backend devuelve 401 en cada caso si la sesión no vale para esa puerta.
 */
export function useSesionHuesped() {
  const [yo, setYo] = useState<Yo | null>(null);
  const [haySesion, setHaySesion] = useState<boolean | null>(null);

  const comprobar = useCallback(async () => {
    try {
      const r = await fetch(urlApi('/api/yo'), { credentials: 'same-origin' });
      if (!r.ok) {
        setHaySesion(false);
        setYo(null);
        return;
      }
      setYo((await r.json()) as Yo);
      setHaySesion(true);
    } catch {
      setHaySesion(false);
      setYo(null);
    }
  }, []);

  useEffect(() => {
    void comprobar();
  }, [comprobar]);

  return {
    haySesion,
    email: yo?.email ?? '',
    nombre: yo?.nombre ?? '',
    tieneReservas: yo?.tieneReservas ?? false,
    comprobar,
    async salir() {
      await fetch(urlApi('/api/huesped/logout'), {
        method: 'POST',
        headers: { 'X-XSRF-TOKEN': leerCsrf() },
        credentials: 'same-origin',
      });
      setHaySesion(false);
      setYo(null);
    },
  };
}

function leerCsrf(): string {
  const c = document.cookie.split('; ').find((f) => f.startsWith('XSRF-TOKEN='));
  return c ? decodeURIComponent(c.split('=').slice(1).join('=')) : '';
}