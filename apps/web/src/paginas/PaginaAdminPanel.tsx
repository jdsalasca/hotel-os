import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { useSesion } from '../api/useSesion';
import { useActualizacionPeriodicaVisible } from '../api/useActualizacionPeriodicaVisible';
import { Aviso, Cargando, MensajeError, PuertaAdmin } from '../componentes/Estado';
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
  /**
   * Contacto del hotel. Sin correo ni teléfono la web pública es un callejón sin salida:
   * quien quiere reservar y no puede, tampoco tiene a quién escribir. Se comprueba aquí,
   * en la puerta del panel, porque es donde el hotelero se da cuenta.
   */
  const [sinContacto, setSinContacto] = useState<boolean | null>(null);
  /**
   * Tipos de habitación. Sin ninguno no hay nada que ofrecer: la portada muestra el estado
   * vacío y quien llega a reservar no puede. Se comprueba en la misma puerta que el
   * contacto, que es donde el hotelero se da cuenta de que su web está ciega.
   */
  const [sinVenta, setSinVenta] = useState<boolean | null>(null);

  async function actualizarMensajesNuevos() {
    if (sesion.haySesion !== true) return;
    try {
      const datos = await api.get<{ nuevos: number }>('/api/admin/mensajes/nuevos');
      setNuevos(datos.nuevos);
    } catch {
      // Conserva el último conteo si falla una consulta en segundo plano.
    }
  }

  useEffect(() => {
    void actualizarMensajesNuevos();
  }, [sesion.haySesion]);
  useActualizacionPeriodicaVisible(actualizarMensajesNuevos, sesion.haySesion === true);

  useEffect(() => {
    if (sesion.haySesion !== true) return;
    // Si no se puede leer, no se afirma nada: `sinContacto` se queda en null y no se avisa.
    try {
      api
        .get<Record<string, string>>('/api/hotel')
        .then((datos) => {
          const email = (datos.contacto_email ?? '').trim();
          const telefono = (datos.contacto_telefono ?? '').trim();
          setSinContacto(email.length === 0 && telefono.length === 0);
        })
        .catch(() => setSinContacto(null));
    } catch {
      setSinContacto(null);
    }
  }, [sesion.haySesion]);

  useEffect(() => {
    if (sesion.haySesion !== true) return;
    // Igual que el contacto: si no se puede leer, `sinVenta` se queda en null y no se avisa.
    // No se afirma que no hay nada que vender por un fallo de red.
    try {
      api
        .get<unknown[]>('/api/admin/tipos')
        .then((tipos) => setSinVenta(!Array.isArray(tipos) || tipos.length === 0))
        .catch(() => setSinVenta(null));
    } catch {
      setSinVenta(null);
    }
  }, [sesion.haySesion]);

  useEffect(() => {
    if (sesion.haySesion === true) {
      // Si el huésped empieza a escribir antes de este efecto, conserva su edición.
      setNombre((actual) => actual ?? sesion.nombre);
    }
  }, [sesion.haySesion, sesion.nombre]);

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

        {sinVenta ? (
          <Aviso tono="aviso" titulo="Tu web no puede vender todavía">
            <p>
              No hay ningún tipo de habitación creado, así que la portada no muestra
              disponibilidad y nadie puede reservar. Crea el primero y tu web empieza a
              vender.
            </p>
            <p>
              <Link to="/admin/inventario">Crear habitaciones</Link>
            </p>
          </Aviso>
        ) : null}

        {sinContacto ? (
          <Aviso tono="aviso" titulo="Los huéspedes no pueden contactarte">
            <p>
              Ni correo ni teléfono: quien quiera reservar y no pueda hacerlo en línea se queda
              sin a quién escribir. Pon tu contacto y la web dejará de ser un callejón sin salida.
            </p>
            <p>
              <Link to="/admin/hotel">Pon tu contacto</Link>
            </p>
          </Aviso>
        ) : null}

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
                <> Tienes <strong role="status">{nuevos} mensaje{nuevos === 1 ? '' : 's'} sin leer</strong>.</>
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
              Tipos, servicios, calendario, tarifas y bloqueos.
            </p>
            <p className="sin-margen">
              <Link className="boton boton--secundario" to="/admin/inventario">
                Gestionar habitaciones
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
