import { useEffect, useState } from 'react';
import { urlApi } from './cliente';

/**
 * Sesión administrativa. El backend usa sesión con cookie HttpOnly y CSRF por cookie: aquí solo
 * se sabe si hay sesión. Nunca se guarda el token en localStorage ni en memoria accesible.
 */
export function useSesion() {
  const [haySesion, setHaySesion] = useState<boolean | null>(null);

  async function comprobar() {
    try {
      const respuesta = await fetch(urlApi('/api/admin/reservas?limit=1'), { credentials: 'same-origin' });
      setHaySesion(respuesta.ok);
    } catch {
      setHaySesion(false);
    }
  }

  useEffect(() => {
    void comprobar();
  }, []);

  return {
    haySesion,
    comprobar,
    async entrar(email: string, clave: string) {
      const r = await fetch(urlApi('/api/admin/login'), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': leerCsrf(),
        },
        credentials: 'same-origin',
        body: JSON.stringify({ email, password: clave }),
      });
      const cuerpo = await r.json().catch(() => ({}));
      if (!r.ok) throw new Error(cuerpo.error ?? 'Credenciales inválidas');
      setHaySesion(true);
    },
    async salir() {
      const csrf = leerCsrf();
      await fetch(urlApi('/api/admin/logout'), {
        method: 'POST',
        headers: { 'X-XSRF-TOKEN': csrf },
        credentials: 'same-origin',
      });
      setHaySesion(false);
    },
  };
}

function leerCsrf(): string {
  const c = document.cookie.split('; ').find((f) => f.startsWith('XSRF-TOKEN='));
  return c ? decodeURIComponent(c.split('=').slice(1).join('=')) : '';
}