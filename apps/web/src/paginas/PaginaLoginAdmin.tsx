import { useState } from 'react';
import { useSesion } from '../api/useSesion';
import { Aviso } from '../componentes/Estado';

/** Inicio de sesión del panel. El backend limita los intentos fallidos. */
export function PaginaLoginAdmin() {
  const sesion = useSesion();
  const [email, setEmail] = useState('');
  const [clave, setClave] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

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
            <button className="boton boton--primario boton--bloque" type="submit" disabled={enviando}>
              {enviando ? 'Entrando…' : 'Entrar'}
            </button>
          </div>
        </form>
      </section>
    </main>
  );
}