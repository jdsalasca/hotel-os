import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, urlApi } from '../api/cliente';
import { useSesionHuesped } from '../api/useSesionHuesped';
import { HiloMensajes, type Hilo } from '../componentes/HiloMensajes';
import { ComprobantePropio, type ComprobantePropioDatos } from '../componentes/ComprobantePropio';
import { Aviso, Cargando, Etiqueta, MensajeError, Vacio } from '../componentes/Estado';

type Reserva = {
  codigo: string;
  llegada: string;
  salida: string;
  huespedes: number;
  estado: string;
  creado_en: string;
  total_cents: number | null;
  moneda: string | null;
  abonado_cents: number | null;
  pendiente_cents: number | null;
};

function monto(cents: number | null, moneda: string | null): string | null {
  if (cents === null || moneda === null) return null;
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: moneda }).format(cents / 100);
}

/**
 * Las reservas del huésped que entró con Google. El backend las filtra por su usuario, así que
 * aquí no hay código ni correo: la sesión ya dice de quién son.
 */
export function PaginaMisReservas() {
  const sesion = useSesionHuesped();
  const [reservas, setReservas] = useState<Reserva[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirmando, setConfirmando] = useState<string | null>(null);
  const [cancelando, setCancelando] = useState<string | null>(null);
  const [hiloAbierto, setHiloAbierto] = useState<string | null>(null);
  const [comprobanteAbierto, setComprobanteAbierto] = useState<string | null>(null);
  const [nuevos, setNuevos] = useState<Record<string, number>>({});

  /** Cancelar en dos pasos por fila: el primero avisa, el segundo ejecuta con el correo de la sesión. */
  async function cancelar(codigo: string) {
    if (confirmando !== codigo) {
      setConfirmando(codigo);
      return;
    }
    setCancelando(codigo);
    setError(null);
    try {
      await api.post(`/api/reservas/${codigo}/cancelar`, { email: sesion.email });
      setReservas((previas) => previas?.map((r) => (r.codigo === codigo ? { ...r, estado: 'CANCELADA' } : r)) ?? null);
      setConfirmando(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo cancelar la reserva');
      setConfirmando(null);
    } finally {
      setCancelando(null);
    }
  }

  useEffect(() => {
    if (sesion.haySesion !== true) return;
    api
      .get<{ reservas: Reserva[] }>('/api/mis-reservas')
      .then((datos) => setReservas(datos.reservas))
      .catch((e: Error) => setError(e.message));
    api
      .get<{ nuevos: number; porReserva: Record<string, number> }>('/api/mis-reservas/mensajes/nuevos')
      .then((datos) => setNuevos(datos.porReserva ?? {}))
      .catch(() => setNuevos({}));
  }, [sesion.haySesion]);

  if (sesion.haySesion === null) return <Cargando texto="Comprobando sesión" />;

  if (sesion.haySesion === false) {
    return (
      <main id="contenido" className="centrado">
        <section className="seccion ancho-acceso">
          <h1 className="seccion__titulo">Mis reservas</h1>
          <Aviso tono="aviso" titulo="Entra con Google">
            Con tu cuenta puedes ver tus reservas sin acordarte del código. También puedes
            consultar cualquier reserva con el código y el correo con el que la hiciste.
          </Aviso>
          <a
            className="boton boton--primario boton--bloque mt-e3"
            href={urlApi('/oauth2/authorization/google-huesped')}
          >
            Entrar con Google
          </a>
          <p className="mt-e3">
            <Link to="/consulta">Consultar una reserva sin entrar</Link>
          </p>
        </section>
      </main>
    );
  }

  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <div className="pila mb-e4">
          <h1 className="seccion__titulo mb-0">Mis reservas</h1>
          <p className="seccion__intro mb-0">
            Hola, {sesion.nombre || sesion.email}. Aquí están las reservas de esta cuenta.
          </p>
        </div>

        <div className="pila gap-e2 mb-e4">
          <a className="boton boton--secundario boton--chico" href={urlApi('/oauth2/authorization/google-huesped')}>
            Vincular otra cuenta
          </a>
          <button
            className="boton boton--fantasma boton--chico"
            type="button"
            onClick={() => {
              void (async () => {
                const ok = await sesion.salir();
                if (!ok) {
                  setError(
                    'No se pudo cerrar la sesión: el servidor no confirmó la salida y sigue abierta.',
                  );
                }
              })();
            }}
          >
            Salir
          </button>
        </div>

        {error ? <MensajeError texto={error} /> : null}
        {reservas === null && !error ? <Cargando /> : null}

        {reservas && reservas.length === 0 ? (
          <Vacio
            titulo="Todavía no has reservado"
            detalle="Cuando reserves con esta cuenta, la reserva aparecerá aquí sin necesidad de guardar el código."
          />
        ) : null}

        {reservas && reservas.length > 0 ? (
          <table className="tabla">
            <caption>Reservas de {sesion.email}</caption>
            <thead>
              <tr>
                <th scope="col">Código</th>
                <th scope="col">Llegada</th>
                <th scope="col">Salida</th>
                <th scope="col">Huéspedes</th>
                <th scope="col">Total acordado</th>
                <th scope="col">Abonado</th>
                <th scope="col">Pendiente</th>
                <th scope="col">Estado</th>
                <th scope="col">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {reservas.map((r) => (
                <tr key={r.codigo}>
                  <td data-label="Código">
                    <code>{r.codigo}</code>
                  </td>
                  <td className="cifra" data-label="Llegada">{r.llegada}</td>
                  <td className="cifra" data-label="Salida">{r.salida}</td>
                  <td className="cifra" data-label="Huéspedes">{r.huespedes}</td>
                  <td className="cifra" data-label="Total">{monto(r.total_cents, r.moneda) ?? 'Sin precio'}</td>
                  <td className="cifra" data-label="Abonado">{monto(r.abonado_cents ?? 0, r.moneda) ?? '—'}</td>
                  <td data-label="Pendiente">
                    {r.total_cents === null || r.moneda === null ? (
                      <span className="campo__ayuda">Sin precio</span>
                    ) : (r.pendiente_cents ?? r.total_cents) <= 0 ? (
                      <Etiqueta tono="exito">PAGADA</Etiqueta>
                    ) : (
                      <span className="cifra">{monto(r.pendiente_cents, r.moneda)}</span>
                    )}
                  </td>
                  <td data-label="Estado">
                    <Etiqueta tono={r.estado === 'CONFIRMADA' ? 'exito' : r.estado === 'CANCELADA' ? 'error' : 'aviso'}>
                      {r.estado}
                    </Etiqueta>
                  </td>
                  <td data-label="Acciones">
                    <button
                      className="boton boton--fantasma boton--chico"
                      type="button"
                      onClick={() => setHiloAbierto((abierto) => (abierto === r.codigo ? null : r.codigo))}
                      aria-expanded={hiloAbierto === r.codigo}
                    >
                      Mensajes{(nuevos[r.codigo] ?? 0) > 0 ? ` (${nuevos[r.codigo]})` : ''}
                    </button>{' '}
                    <button
                      className="boton boton--fantasma boton--chico"
                      type="button"
                      onClick={() =>
                        setComprobanteAbierto((abierto) => (abierto === r.codigo ? null : r.codigo))
                      }
                      aria-expanded={comprobanteAbierto === r.codigo}
                    >
                      Comprobante
                    </button>{' '}{(r.estado === 'PENDIENTE' || r.estado === 'CONFIRMADA') ? (
                      <button
                        className={`boton boton--chico ${confirmando === r.codigo ? 'boton--peligro' : 'boton--fantasma'}`}
                        type="button"
                        onClick={() => void cancelar(r.codigo)}
                        disabled={cancelando !== null}
                        aria-label={confirmando === r.codigo ? `Confirma cancelar la reserva ${r.codigo}` : `Cancelar la reserva ${r.codigo}`}
                      >
                        {cancelando === r.codigo ? 'Cancelando…' : confirmando === r.codigo ? 'Sí, cancelar' : 'Cancelar'}
                      </button>
                    ) : (
                      <span className="campo__ayuda">Sin acciones</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : null}

        {hiloAbierto ? (
          <div className="tarjeta pila mt-e6">
            <HiloMensajes
              titulo={`Conversación de ${hiloAbierto}`}
              ladoPropio="HUESPED"
              cargar={(antesDe) =>
                api.get<Hilo>(
                  `/api/mis-reservas/${hiloAbierto}/mensajes${antesDe ? `?antes_de=${antesDe}` : ''}`,
                )
              }
              enviar={(texto) =>
                api
                  .post(`/api/mis-reservas/${hiloAbierto}/mensajes`, { texto })
                  .then(() =>
                    api
                      .get<{ nuevos: number; porReserva: Record<string, number> }>(
                        '/api/mis-reservas/mensajes/nuevos',
                      )
                      .then((datos) => setNuevos(datos.porReserva ?? {}))
                      .catch(() => undefined),
                  )
              }
            />
          </div>
        ) : null}

        {comprobanteAbierto ? (
          <div className="tarjeta pila mt-e6">
            <h2 className="t-lg mb-0">Comprobante de {comprobanteAbierto}</h2>
            <ComprobantePropio
              codigo={comprobanteAbierto}
              cargar={(codigo) =>
                api.get<ComprobantePropioDatos>(`/api/mis-reservas/${codigo}/comprobante`)
              }
            />
          </div>
        ) : null}

        <p className="mt-e6">
          <Link to="/">Reservar otra vez</Link>
        </p>
      </section>
    </main>
  );
}