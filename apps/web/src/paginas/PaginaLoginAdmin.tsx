import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useSesion } from '../api/useSesion';
import { urlApi } from '../api/cliente';
import { Aviso } from '../componentes/Estado';

/**
 * Inicio de sesión del panel. El backend limita los intentos fallidos con contraseña; con Google
 * solo entra quien esté en la allowlist, y el backend lo dice con ?error=denegado en la vuelta.
 */
export function PaginaLoginAdmin() {
  const sesion = useSesion();
  const [parametros] = useSearchParams();
  const [email, setEmail] = useState('');
  const [clave, setClave] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const denegado = parametros.get('error') === 'denegado';

  async function entrar(evento: React.FormEvent) {
    evento.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      await sesion.entrar(email, clave);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo iniciar sesión');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main id="contenido" className="centrado">
      <section className="seccion ancho-acceso">
        <h1 className="seccion__titulo">Panel del hotel</h1>
        <p className="seccion__intro">Acceso para el personal autorizado.</p>

        <form onSubmit={entrar} noValidate>
          <div className="pila">
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="admin-email">Correo electrónico</label>
              <input
                id="admin-email"
                type="email"
                required
                autoComplete="username"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="admin-clave">Contraseña</label>
              <input
                id="admin-clave"
                type="password"
                required
                autoComplete="current-password"
                value={clave}
                onChange={(e) => setClave(e.target.value)}
              />
            </div>
            {error ? <Aviso tono="error">{error}</Aviso> : null}
            {denegado && !error ? (
              <Aviso tono="aviso">
                Esa cuenta de Google no está autorizada para el panel. Pide que la añadan o entra
                con contraseña.
              </Aviso>
            ) : null}
            <button className="boton boton--primario boton--bloque" type="submit" disabled={enviando}>
              {enviando ? 'Entrando…' : 'Entrar'}
            </button>
          </div>
        </form>

        <p className="campo__ayuda mt-e4 sin-margen">
          ¿Prefieres no usar contraseña?{' '}
          <a className="boton boton--secundario boton--bloque mt-e2" href={urlApi('/oauth2/authorization/google-admin')}>
            Entrar con Google
          </a>
        </p>
      </section>
    </main>
  );
}