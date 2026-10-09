import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { useSesion } from '../api/useSesion';
import { Cargando, MensajeError, PuertaAdmin } from '../componentes/Estado';
import { ListaPreparacion } from '../componentes/ListaPreparacion';

/**
 * Puerta de entrada del panel: a dónde va cada cosa, de una mirada. Entrar ya no te deja
 * en una lista sin contexto: aquí se ve el mapa del trabajo (reservas, hoy, habitaciones,
 * hotel, mapa, actividad) y los mensajes sin leer que esperan respuesta.
 */
export function PaginaAdminPanel() {
  const sesion = useSesion();
  const [nuevos, setNuevos] = useState<number | null>(null);
  const [nombre, setNombre] = useState<string | null>(null);
  const [guardandoNombre, setGuardandoNombre] = useState(false);
  const [nombreError, setNombreError] = useState<string | null>(null);

  useEffect(() => {
    if (sesion.haySesion !== true) return;
    void api
      .get<{ nuevos: number }>('/api/admin/mensajes/nuevos')
      .then((datos) => setNuevos(datos.nuevos))
      .catch(() => setNuevos(null));
  }, [sesion.haySesion]);

  useEffect(() => {
    if (sesion.haySesion === true && nombre === null) setNombre(sesion.nombre);
  }, [sesion.haySesion, sesion.nombre, nombre]);

  /** Nombre visible del saludo: con Google lo pone el login, con clave se pone aquí. */
  async function guardarNombre(evento: React.FormEvent) {
    evento.preventDefault();
    if (nombre === null || guardandoNombre) return;
    setGuardandoNombre(true);
    setNombreError(null);
    try {
      await api.post<{ nombre: string }>('/api/admin/perfil', { nombre });
      // Recarga completa a propósito: refrescar el hook no repinta la cabecera ya
      // montada (verificado en vivo), igual que al entrar y al salir.
      window.location.reload();
    } catch (e) {
      setNombreError(e instanceof Error ? e.message : 'No se pudo guardar el nombre');
    } finally {
      setGuardandoNombre(false);
    }
  }

  if (sesion.haySesion === false)
    return <PuertaAdmin>Inicia sesión para entrar al panel.</PuertaAdmin>;
  if (sesion.haySesion === null) return <Cargando texto="Comprobando sesión" />;

  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <h1 className="seccion__titulo">Panel del hotel</h1>
        <p className="seccion__intro">
          Todo el trabajo del día, por puertas: cada tarjeta lleva a su pantalla.
        </p>

        <form className="tarjeta pila mb-e4" onSubmit={(e) => void guardarNombre(e)}>
          <h2 className="t-lg mb-0">Mi cuenta</h2>
          <p className="campo__ayuda sin-margen">
            Así te saluda la cabecera. Con Google se pone solo; con contraseña lo pones aquí.
          </p>
          <div className="campo">
            <label className="campo__etiqueta" htmlFor="cuenta-nombre">Nombre visible</label>
            <input
              id="cuenta-nombre"
              type="text"
              maxLength={80}
              autoComplete="nickname"
              value={nombre ?? ''}
              onChange={(e) => setNombre(e.target.value)}
            />
          </div>
          {nombreError ? <MensajeError texto={nombreError} /> : null}
          <p className="sin-margen">
            <button className="boton boton--secundario boton--chico" type="submit" disabled={guardandoNombre}>
              {guardandoNombre ? 'Guardando…' : 'Guardar nombre'}
            </button>
          </p>
        </form>

        <ListaPreparacion />

        <div className="rejilla">
          <article className="tarjeta pila">
            <h2 className="t-lg mb-0">Reservas</h2>
            <p className="campo__ayuda sin-margen">
              Buscar, ver detalle, abonos, cambios y cancelaciones.
              {nuevos !== null && nuevos > 0 ? (
                <> Tienes <strong>{nuevos} mensaje{nuevos === 1 ? '' : 's'} sin leer</strong>.</>
              ) : null}
            </p>
            <p className="sin-margen">
              <Link className="boton boton--primario" to="/admin/reservas">
                Abrir reservas
              </Link>
            </p>
          </article>

          <article className="tarjeta pila">
            <h2 className="t-lg mb-0">Hoy en el hotel</h2>
            <p className="campo__ayuda sin-margen">
              Llegadas, salidas y quién duerme en casa, para la recepción.
            </p>
            <p className="sin-margen">
              <Link className="boton boton--secundario" to="/admin/hoy">
                Ver el día
              </Link>
            </p>
          </article>

          <article className="tarjeta pila">
            <h2 className="t-lg mb-0">Habitaciones</h2>
            <p className="campo__ayuda sin-margen">
              Tipos, servicios, calendario, tarifas y bloqueos del inventario.
            </p>
            <p className="sin-margen">
              <Link className="boton boton--secundario" to="/admin/inventario">
                Gestionar inventario
              </Link>
            </p>
          </article>

          <article className="tarjeta pila">
            <h2 className="t-lg mb-0">Hotel y mapa</h2>
            <p className="campo__ayuda sin-margen">
              Datos, ubicación GPS y sitios que ve el huésped en la web.
            </p>
            <p className="sin-margen">
              <Link className="boton boton--secundario" to="/admin/hotel">
                Datos del hotel
              </Link>{' '}
              <Link className="boton boton--secundario" to="/admin/lugares">
                Lugares
              </Link>
            </p>
          </article>

          <article className="tarjeta pila">
            <h2 className="t-lg mb-0">Actividad</h2>
            <p className="campo__ayuda sin-margen">
              Quién hizo qué en el panel, con rastro no falsificable.
            </p>
            <p className="sin-margen">
              <Link className="boton boton--secundario" to="/admin/auditoria">
                Ver actividad
              </Link>
            </p>
          </article>
        </div>
      </section>
    </main>
  );
}
