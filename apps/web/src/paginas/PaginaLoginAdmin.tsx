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
  const [pideCambio, setPideCambio] = useState(false);
  const [nueva, setNueva] = useState('');
  const [nueva2, setNueva2] = useState('');

  const denegado = parametros.get('error') === 'denegado';

  async function entrar(evento: React.FormEvent) {
    evento.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      const estado = await sesion.entrar(email, clave);
      if (estado === 'cambio_requerido') setPideCambio(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo iniciar sesión');
    } finally {
      setEnviando(false);
    }
  }

  async function cambiar(evento: React.FormEvent) {
    evento.preventDefault();
    setError(null);
    if (nueva !== nueva2) {
      setError('La nueva contraseña y su confirmación no coinciden');
      return;
    }
    setEnviando(true);
    try {
      await sesion.cambiarClave(email, clave, nueva);
      setPideCambio(false);
      setNueva('');
      setNueva2('');
      const estado = await sesion.entrar(email, nueva);
      if (estado === 'cambio_requerido') setPideCambio(true);
      else setClave(nueva);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo cambiar la contraseña');
    } finally {
      setEnviando(false);
    }
  }

  if (pideCambio) {
    return (
      <main id="contenido" className="centrado">
        <section className="seccion ancho-acceso">
          <h1 className="seccion__titulo">Cambia tu contraseña</h1>
          <p className="seccion__intro">
            Entras con la clave inicial del servidor: pon la tuya (mínimo 12 caracteres) para
            seguir.
          </p>
          <form onSubmit={cambiar} noValidate>
            <div className="pila">
              <div className="campo">
                <label className="campo__etiqueta" htmlFor="admin-nueva">Nueva contraseña</label>
                <input
                  id="admin-nueva"
                  type="password"
                  required
                  minLength={12}
                  autoComplete="new-password"
                  value={nueva}
                  onChange={(e) => setNueva(e.target.value)}
                />
              </div>
              <div className="campo">
                <label className="campo__etiqueta" htmlFor="admin-nueva2">Confírmala</label>
                <input
                  id="admin-nueva2"
                  type="password"
                  required
                  minLength={12}
                  autoComplete="new-password"
                  value={nueva2}
                  onChange={(e) => setNueva2(e.target.value)}
                />
              </div>
              {error ? <Aviso tono="error">{error}</Aviso> : null}
              <button className="boton boton--primario boton--bloque" type="submit" disabled={enviando}>
                {enviando ? 'Guardando…' : 'Guardar y entrar'}
              </button>
            </div>
          </form>
        </section>
      </main>
    );
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