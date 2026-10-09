import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, urlApi } from '../api/cliente';
import { fechaCorta, hoyIso, monto as montoCompartido } from '../api/formato';
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
  return montoCompartido(cents, moneda);
}

function diaSiguienteIso(iso: string): string {
  const fecha = new Date(`${iso}T12:00:00`);
  fecha.setDate(fecha.getDate() + 1);
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
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
  const [moviendo, setMoviendo] = useState<string | null>(null);
  const [nuevas, setNuevas] = useState({ llegada: '', salida: '' });
  const [guardandoFechas, setGuardandoFechas] = useState(false);
  const [hiloAbierto, setHiloAbierto] = useState<string | null>(null);
  const [comprobanteAbierto, setComprobanteAbierto] = useState<string | null>(null);
  const [nuevos, setNuevos] = useState<Record<string, number>>({});
  /** Reserva cuyo formulario de huéspedes está abierto, con el valor tecleado. */
  const [abriendoGrupo, setAbriendoGrupo] = useState<{ codigo: string; valor: string } | null>(null);

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

  /** Mueve las fechas de una reserva propia: tras guardar se relee la lista para
   * mostrar lo guardado de verdad (el precio se recalcula en el servidor). */
  async function guardarFechas(codigo: string) {
    if (!nuevas.llegada || !nuevas.salida) return;
    setGuardandoFechas(true);
    setError(null);
    try {
      await api.post(`/api/mis-reservas/${codigo}/fechas`, {
        llegada: nuevas.llegada,
        salida: nuevas.salida,
      });
      setMoviendo(null);
      setNuevas({ llegada: '', salida: '' });
      const datos = await api.get<{ reservas: Reserva[] }>('/api/mis-reservas');
      setReservas(datos.reservas);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudieron cambiar las fechas');
    } finally {
      setGuardandoFechas(false);
    }
  }

  /** Avisa de quantos vienen. El servidor recalcula el precio y es el que dice si el grupo
   * cabe en la habitación; si no, no cambia nada y el motivo sale en el aviso. */
  async function guardarGrupo(codigo: string, huespedes: number) {
    setGuardandoFechas(true);
    setError(null);
    try {
      await api.post(`/api/mis-reservas/${codigo}/huespedes`, { huespedes });
      setAbriendoGrupo(null);
      const datos = await api.get<{ reservas: Reserva[] }>('/api/mis-reservas');
      setReservas(datos.reservas);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo cambiar el número de huéspedes');
    } finally {
      setGuardandoFechas(false);
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
                  <td className="cifra tabla__fecha" data-label="Llegada">{fechaCorta(r.llegada)}</td>
                  <td className="cifra tabla__fecha" data-label="Salida">{fechaCorta(r.salida)}</td>
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
                      <>
                        <button
                          className="boton boton--fantasma boton--chico"
                          type="button"
                          onClick={() => {
                            if (moviendo === r.codigo) {
                              setMoviendo(null);
                              setNuevas({ llegada: '', salida: '' });
                            } else {
                              setMoviendo(r.codigo);
                              setNuevas({ llegada: r.llegada, salida: r.salida });
                            }
                          }}
                          aria-expanded={moviendo === r.codigo}
                        >
                          Cambiar fechas
                        </button>{' '}
                        <button
                          className="boton boton--fantasma boton--chico"
                          type="button"
                          onClick={() =>
                            setAbriendoGrupo(
                              abriendoGrupo?.codigo === r.codigo
                                ? null
                                : { codigo: r.codigo, valor: String(r.huespedes) },
                            )
                          }
                          aria-expanded={abriendoGrupo?.codigo === r.codigo}
                        >
                          Cambiar huéspedes
                        </button>{' '}
                        <button
                          className={`boton boton--chico ${confirmando === r.codigo ? 'boton--peligro' : 'boton--fantasma'}`}
                          type="button"
                          onClick={() => void cancelar(r.codigo)}
                          disabled={cancelando !== null}
                          aria-label={confirmando === r.codigo ? `Confirma cancelar la reserva ${r.codigo}` : `Cancelar la reserva ${r.codigo}`}
                        >
                          {cancelando === r.codigo ? 'Cancelando…' : confirmando === r.codigo ? 'Sí, cancelar' : 'Cancelar'}
                        </button>
                      </>
                    ) : (
                      <span className="campo__ayuda">Sin acciones</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : null}

        {moviendo ? (
          <form
            className="tarjeta pila mt-e6"
            onSubmit={(e) => {
              e.preventDefault();
              void guardarFechas(moviendo);
            }}
          >
            <h2 className="t-lg mb-0">Cambiar fechas de {moviendo}</h2>
            <div className="campos">
              <div className="campo">
                <label className="campo__etiqueta" htmlFor="mov-llegada">Nueva llegada</label>
                <input
                  id="mov-llegada"
                  type="date"
                  required
                  min={hoyIso()}
                  value={nuevas.llegada}
                  onChange={(e) => setNuevas({ ...nuevas, llegada: e.target.value })}
                />
              </div>
              <div className="campo">
                <label className="campo__etiqueta" htmlFor="mov-salida">Nueva salida</label>
                <input
                  id="mov-salida"
                  type="date"
                  required
                  min={nuevas.llegada ? diaSiguienteIso(nuevas.llegada) : hoyIso()}
                  value={nuevas.salida}
                  onChange={(e) => setNuevas({ ...nuevas, salida: e.target.value })}
                />
              </div>
            </div>
            <div className="pila gap-e2">
              <button
                className="boton boton--primario boton--chico"
                type="submit"
                disabled={!nuevas.llegada || !nuevas.salida || guardandoFechas}
              >
                {guardandoFechas ? 'Guardando…' : 'Guardar fechas'}
              </button>
              <button
                className="boton boton--fantasma boton--chico"
                type="button"
                onClick={() => {
                  setMoviendo(null);
                  setNuevas({ llegada: '', salida: '' });
                }}
              >
                Volver sin guardar
              </button>
            </div>
          </form>
        ) : null}

        {abriendoGrupo ? (
          <form
            className="tarjeta pila mt-e6"
            onSubmit={(e) => {
              e.preventDefault();
              void guardarGrupo(abriendoGrupo.codigo, Number(abriendoGrupo.valor));
            }}
          >
            <h2 className="t-lg mb-0">Cambiar huéspedes de {abriendoGrupo.codigo}</h2>
            <div className="campos">
              <div className="campo">
                <label className="campo__etiqueta" htmlFor="grupo-huesped">Cuántos vienen</label>
                <input
                  id="grupo-huesped"
                  type="number"
                  min={1}
                  step={1}
                  required
                  inputMode="numeric"
                  value={abriendoGrupo.valor}
                  onChange={(e) =>
                    setAbriendoGrupo({ ...abriendoGrupo, valor: e.target.value })
                  }
                />
                <p className="campo__ayuda">
                  Si no caben en la habitación reservada no se cambia nada y te lo decimos.
                  El precio se recalcula con el grupo nuevo.
                </p>
              </div>
            </div>
            <div className="pila gap-e2">
              <button
                className="boton boton--primario boton--chico"
                type="submit"
                disabled={!abriendoGrupo.valor || guardandoFechas}
              >
                {guardandoFechas ? 'Guardando…' : 'Guardar huéspedes'}
              </button>
              <button
                className="boton boton--fantasma boton--chico"
                type="button"
                onClick={() => setAbriendoGrupo(null)}
              >
                Volver sin guardar
              </button>
            </div>
          </form>
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
