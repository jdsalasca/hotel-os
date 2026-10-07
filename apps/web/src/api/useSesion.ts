import { useEffect, useState } from 'react';
import { urlApi } from './cliente';

/**
 * Sesión administrativa. El backend usa sesión con cookie HttpOnly y CSRF por cookie: aquí solo
 * se sabe si hay sesión. Nunca se guarda el token en localStorage ni en memoria accesible.
 */
export function useSesion() {
  const [haySesion, setHaySesion] = useState<boolean | null>(null);
  const [email, setEmail] = useState('');

  async function comprobar() {
    try {
      const r = await fetch(urlApi('/api/admin/sesion'), { credentials: 'same-origin' });
      if (!r.ok) {
        setHaySesion(false);
        setEmail('');
        return;
      }
      const cuerpo = (await r.json()) as { email?: string };
      setEmail(cuerpo.email ?? '');
      setHaySesion(true);
    } catch {
      setHaySesion(false);
      setEmail('');
    }
  }

  useEffect(() => {
    void comprobar();
  }, []);

  return {
    haySesion,
    email,
    comprobar,
    async entrar(email: string, clave: string): Promise<'autenticado' | 'cambio_requerido'> {
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
      if (cuerpo.estado === 'cambio_requerido') return 'cambio_requerido';
      await comprobar();
      return 'autenticado';
    },
    async cambiarClave(email: string, actual: string, nueva: string) {
      const r = await fetch(urlApi('/api/admin/password'), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': leerCsrf(),
        },
        credentials: 'same-origin',
        body: JSON.stringify({ email, actual, nueva }),
      });
      const cuerpo = await r.json().catch(() => ({}));
      if (!r.ok) throw new Error(cuerpo.error ?? 'No se pudo cambiar la contraseña');
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