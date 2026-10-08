import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { useSesion } from '../api/useSesion';
import { Aviso, Cargando } from '../componentes/Estado';
import { ListaPreparacion } from '../componentes/ListaPreparacion';

/**
 * Puerta de entrada del panel: a dónde va cada cosa, de una mirada. Entrar ya no te deja
 * en una lista sin contexto: aquí se ve el mapa del trabajo (reservas, hoy, habitaciones,
 * hotel, mapa, actividad) y los mensajes sin leer que esperan respuesta.
 */
export function PaginaAdminPanel() {
  const sesion = useSesion();
  const [nuevos, setNuevos] = useState<number | null>(null);

  useEffect(() => {
    if (sesion.haySesion !== true) return;
    void api
      .get<{ nuevos: number }>('/api/admin/mensajes/nuevos')
      .then((datos) => setNuevos(datos.nuevos))
      .catch(() => setNuevos(null));
  }, [sesion.haySesion]);

  if (sesion.haySesion === false)
    return (
      <Aviso tono="aviso" titulo="Sesión requerida">
        Inicia sesión para entrar al panel.
      </Aviso>
    );
  if (sesion.haySesion === null) return <Cargando texto="Comprobando sesión" />;

  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <h1 className="seccion__titulo">Panel del hotel</h1>
        <p className="seccion__intro">
          Todo el trabajo del día, por puertas: cada tarjeta lleva a su pantalla.
        </p>

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
