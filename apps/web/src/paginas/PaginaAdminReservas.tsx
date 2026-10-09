import { useEffect, useState } from 'react';
import { api, urlApi } from '../api/cliente';
import { fechaCorta, monto } from '../api/formato';
import { useSesion } from '../api/useSesion';
import { Aviso, Cargando, Etiqueta, MensajeError, PuertaAdmin, Vacio } from '../componentes/Estado';
import { HiloMensajes, type Hilo } from '../componentes/HiloMensajes';

type Estado = 'PENDIENTE' | 'CONFIRMADA' | 'CANCELADA' | 'RECHAZADA';

type Reserva = {
  codigo: string;
  email: string;
  nombre: string;
  llegada: string;
  salida: string;
  noches: number;
  huespedes: number;
  estado: Estado;
  /** Estados que el servidor acepta desde aquí. La lista la manda el dominio, no el panel. */
  siguientes: Estado[];
  origen: string;
  creadoEn: string;
};

type Detalle = {
  reserva: Reserva & { totalCents: number | null; moneda: string | null; plan: string | null };
  habitacion: { codigo: string; nombre: string; tipo: string } | null;
  historial: { estado_ant: string | null; estado_nuevo: string; actor: string; en: string; detalle?: string | null }[];
};

type MovimientoPago = {
  id: number; monto_cents: number; moneda: string; concepto: string; actor: string;
  creado_en: string; anulado_en: string | null; anulado_por: string | null;
};

type Saldo = {
  totalCents: number; moneda: string; abonadoCents: number; pendienteCents: number;
  movimientos: MovimientoPago[];
};

type Habitacion = { id: number; codigo: string; nombre: string };

const ESTADOS: { valor: Estado; texto: string }[] = [
  { valor: 'CONFIRMADA', texto: 'Confirmar' },
  { valor: 'CANCELADA', texto: 'Cancelar' },
  { valor: 'RECHAZADA', texto: 'Rechazar' },
];

/**
 * Los botones son los que el servidor dice que valen, no una copia local de las reglas.
 *
 * Este panel ya se salió una vez: el CHECK de la base y el enum no coincidían, y una lista de
 * transiciones escrita aquí habría_divergido en silencio. La lista viene en la respuesta, así que
 * lo que se ve es lo que el servidor acepta. Solo el texto de cada botón es de aquí.
 */
function accionesPosibles(reserva: Reserva) {
  return ESTADOS.filter((e) => reserva.siguientes.includes(e.valor));
}

/**
 * El color sigue a la misma regla que los botones: si no tiene salida, está muerta.
 *
 * RECHAZADA salía en el mismo amarillo que PENDIENTE y las dos son lo contrario: una espera una
 * respuesta del hotel, la otra ya la tiene y no va a volver.
 */
function tonoEstado(estado: Estado): 'exito' | 'error' | 'aviso' {
  if (estado === 'CONFIRMADA') return 'exito';
  if (estado === 'CANCELADA' || estado === 'RECHAZADA') return 'error';
  return 'aviso';
}

/**
 * Una reserva cerrada (CANCELADA o RECHAZADA) ya soltó la fecha: no admite dinero. El servidor lo
 * rechaza con 400, así que el panel tampoco ofrece el formulario, que solo produciría un error.
 */
function admiteDinero(estado: Estado): boolean {
  return estado === 'PENDIENTE' || estado === 'CONFIRMADA';
}

export function PaginaAdminReservas() {
  const sesion = useSesion();
  const [reservas, setReservas] = useState<Reserva[] | null>(null);
  const [detalle, setDetalle] = useState<Detalle | null>(null);
  const [habitaciones, setHabitaciones] = useState<Habitacion[] | null>(null);
  const [nuevaHabitacion, setNuevaHabitacion] = useState('');
  const [nuevasFechas, setNuevasFechas] = useState({ llegada: '', salida: '' });
  const [saldo, setSaldo] = useState<Saldo | null>(null);
  const [abono, setAbono] = useState({ monto: '', concepto: '' });
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);
  const [texto, setTexto] = useState('');
  const [estado, setEstado] = useState('');
  /** Alta de mostrador: la recepción registra sin pasar por la web. */
  const [mostrarNueva, setMostrarNueva] = useState(false);
  const [nueva, setNueva] = useState({ email: '', nombre: '', llegada: '', salida: '', huespedes: 2, roomId: '' });
  const [creada, setCreada] = useState<string | null>(null);

  async function alternarNueva() {
    const abrir = !mostrarNueva;
    setMostrarNueva(abrir);
    setCreada(null);
    if (abrir && habitaciones === null) {
      try {
        setHabitaciones(await api.get<Habitacion[]>('/api/admin/habitaciones'));
      } catch (e) {
        setError(e instanceof Error ? e.message : 'No se pudieron cargar las habitaciones');
      }
    }
  }

  async function crearManual(evento: React.FormEvent) {
    evento.preventDefault();
    setError(null);
    setCreada(null);
    try {
      const r = await api.post<{ codigo: string }>('/api/admin/reservas', {
        email: nueva.email,
        nombre: nueva.nombre,
        llegada: nueva.llegada,
        salida: nueva.salida,
        huespedes: nueva.huespedes,
        roomId: Number(nueva.roomId),
      });
      setCreada(r.codigo);
      setNueva({ email: '', nombre: '', llegada: '', salida: '', huespedes: 2, roomId: '' });
      await cargar();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo registrar la reserva');
    }
  }

  async function cargar(textoFiltro = texto, estadoFiltro = estado) {
    setCargando(true);
    setError(null);
    try {
      const consulta = new URLSearchParams({ limit: '100' });
      if (textoFiltro.trim() !== '') consulta.set('q', textoFiltro.trim());
      if (estadoFiltro !== '') consulta.set('estado', estadoFiltro);
      setReservas(await api.get<Reserva[]>(`/api/admin/reservas?${consulta}`));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudieron cargar las reservas');
    } finally {
      setCargando(false);
    }
  }

  useEffect(() => {
    void cargar();
  }, []);

  /** Los mismos filtros de la pantalla, para que el CSV traiga lo que se ve. */
  function filtrosUrl(): string {
    const consulta = new URLSearchParams({ limit: '200' });
    if (texto.trim() !== '') consulta.set('q', texto.trim());
    if (estado !== '') consulta.set('estado', estado);
    return consulta.toString();
  }

  async function abrir(codigo: string) {
    setError(null);
    try {
      // El comprobante trae lo mismo que el detalle más habitación y total acordado.
      const [detalleAbierto, habs] = await Promise.all([
        api.get<Detalle>(`/api/admin/reservas/${codigo}/comprobante`),
        habitaciones ?? api.get<Habitacion[]>('/api/admin/habitaciones'),
      ]);
      setDetalle(detalleAbierto);
      setHabitaciones(habs);
      setNuevaHabitacion('');
      setNuevasFechas({ llegada: '', salida: '' });
      setAbono({ monto: '', concepto: '' });
      try {
        setSaldo(await api.get<Saldo>(`/api/admin/reservas/${codigo}/saldo`));
      } catch {
        setSaldo(null);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo abrir la reserva');
    }
  }

  async function reasignar(codigo: string) {
    if (!nuevaHabitacion) return;
    setError(null);
    try {
      await api.post(`/api/admin/reservas/${codigo}/habitacion`, { roomId: Number(nuevaHabitacion) });
      await cargar();
      await abrir(codigo);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo reasignar la habitación');
    }
  }

  async function cambiarFechas(codigo: string) {
    if (!nuevasFechas.llegada || !nuevasFechas.salida) return;
    setError(null);
    try {
      await api.post(`/api/admin/reservas/${codigo}/fechas`, nuevasFechas);
      await cargar();
      await abrir(codigo);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudieron cambiar las fechas');
    }
  }

  async function abonar(codigo: string, moneda: string) {
    if (!abono.monto) return;
    setError(null);
    try {
      await api.post(`/api/admin/reservas/${codigo}/abonos`, {
        montoCents: Math.round(Number(abono.monto) * 100),
        moneda,
        concepto: abono.concepto,
      });
      await abrir(codigo);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo registrar el abono');
    }
  }

  async function anularAbono(abonoId: number, codigo: string) {
    setError(null);
    try {
      await api.post(`/api/admin/abonos/${abonoId}/anular`);
      await abrir(codigo);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo anular el abono');
    }
  }

  async function cambiar(codigo: string, estado: string) {
    setError(null);
    try {
      // Sin `actor`: el servidor usa el correo de la sesión. Mandarlo aquí solo permitiría firmarlo.
      await api.post(`/api/admin/reservas/${codigo}/estado`, { estado });
      await cargar();
      await abrir(codigo);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo cambiar el estado');
    }
  }

  if (sesion.haySesion === false) return <RequiereSesion />;
  if (sesion.haySesion === null) return <Cargando texto="Comprobando sesión" />;

  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <div className="pila mb-e4">
          <h1 className="seccion__titulo mb-0">Reservas</h1>
          <p className="seccion__intro mb-0">
            Cada reserva muestra su origen y su historial de cambios de estado.
          </p>
        </div>

        {error ? <MensajeError texto={error} /> : null}

        <button
          className={`boton boton--secundario boton--chico mb-e4${detalle ? ' no-imprimir' : ''}`}
          type="button"
          onClick={() => void alternarNueva()}
          aria-expanded={mostrarNueva}
        >
          {mostrarNueva ? 'Ocultar nueva reserva' : 'Nueva reserva'}
        </button>

        {mostrarNueva ? (
          <form
            className="tarjeta pila mb-e4"
            onSubmit={(e) => void crearManual(e)}
          >
            <h2 className="t-lg mb-0">Nueva reserva de mostrador</h2>
            {creada ? (
              <Aviso tono="exito" titulo="Reserva registrada">
                <p>
                  Código <span className="cifra">{creada}</span>. Ya aparece en la lista.
                </p>
              </Aviso>
            ) : null}
            <div className="campos">
              <div className="campo">
                <label className="campo__etiqueta" htmlFor="nr-email">Correo electrónico</label>
                <input
                  id="nr-email"
                  type="email"
                  required
                  autoComplete="email"
                  value={nueva.email}
                  onChange={(e) => setNueva({ ...nueva, email: e.target.value })}
                />
              </div>
              <div className="campo">
                <label className="campo__etiqueta" htmlFor="nr-nombre">Nombre</label>
                <input
                  id="nr-nombre"
                  type="text"
                  required
                  autoComplete="name"
                  value={nueva.nombre}
                  onChange={(e) => setNueva({ ...nueva, nombre: e.target.value })}
                />
              </div>
              <div className="campo">
                <label className="campo__etiqueta" htmlFor="nr-llegada">Llegada</label>
                <input
                  id="nr-llegada"
                  type="date"
                  required
                  value={nueva.llegada}
                  onChange={(e) => setNueva({ ...nueva, llegada: e.target.value })}
                />
              </div>
              <div className="campo">
                <label className="campo__etiqueta" htmlFor="nr-salida">Salida</label>
                <input
                  id="nr-salida"
                  type="date"
                  required
                  min={nueva.llegada || undefined}
                  value={nueva.salida}
                  onChange={(e) => setNueva({ ...nueva, salida: e.target.value })}
                />
              </div>
              <div className="campo">
                <label className="campo__etiqueta" htmlFor="nr-huespedes">Huéspedes</label>
                <input
                  id="nr-huespedes"
                  type="number"
                  required
                  min={1}
                  max={20}
                  value={nueva.huespedes}
                  onChange={(e) => setNueva({ ...nueva, huespedes: Number(e.target.value) })}
                />
              </div>
              <div className="campo">
                <label className="campo__etiqueta" htmlFor="nr-habitacion">Habitación</label>
                <select
                  id="nr-habitacion"
                  required
                  disabled={habitaciones === null}
                  value={nueva.roomId}
                  onChange={(e) => setNueva({ ...nueva, roomId: e.target.value })}
                >
                  <option value="">{habitaciones === null ? 'Cargando habitaciones…' : 'Selecciona…'}</option>
                  {(habitaciones ?? []).map((h) => (
                    <option key={h.id} value={h.id}>{h.codigo}{h.nombre ? ` · ${h.nombre}` : ''}</option>
                  ))}
                </select>
              </div>
            </div>
            <button
              className="boton boton--primario"
              type="submit"
              disabled={!nueva.email || !nueva.llegada || !nueva.salida || !nueva.roomId}
            >
              Registrar reserva
            </button>
          </form>
        ) : null}

        <form
          className={detalle ? 'filtros no-imprimir' : 'filtros'}
          onSubmit={(e) => {
            e.preventDefault();
            void cargar();
          }}
        >
          <div className="campo">
            <label className="campo__etiqueta" htmlFor="filtro-texto">Buscar por código, correo o nombre</label>
            <input
              id="filtro-texto"
              type="search"
              value={texto}
              onChange={(e) => setTexto(e.target.value)}
              placeholder="H-ABC, ana@…, nombre…"
            />
          </div>
          <div className="campo">
            <label className="campo__etiqueta" htmlFor="filtro-estado">Estado</label>
            <select id="filtro-estado" value={estado} onChange={(e) => setEstado(e.target.value)}>
              <option value="">Todos</option>
              <option value="PENDIENTE">Pendiente</option>
              <option value="CONFIRMADA">Confirmada</option>
              <option value="CANCELADA">Cancelada</option>
              <option value="RECHAZADA">Rechazada</option>
            </select>
          </div>
          <button className="boton boton--secundario" type="submit">Filtrar</button>
          <a
            className="boton boton--fantasma boton--chico"
            href={urlApi(`/api/admin/reservas.csv?${filtrosUrl()}`)}
            download="reservas.csv"
          >
            Descargar CSV
          </a>
        </form>

        {cargando ? <Cargando /> : null}

        {!cargando && reservas && reservas.length === 0 ? (
          <Vacio titulo="Sin resultados" detalle="Ninguna reserva coincide con ese filtro." />
        ) : null}

        {reservas && reservas.length > 0 ? (
          <div className={detalle ? 'tabla-envoltura no-imprimir' : 'tabla-envoltura'}>
            <table className="tabla">
              <caption>Reservas del hotel</caption>
              <thead>
                <tr>
                  <th scope="col">Código</th>
                  <th scope="col">Huésped</th>
                  <th scope="col">Fechas</th>
                  <th scope="col">Huéspedes</th>
                  <th scope="col">Origen</th>
                  <th scope="col">Estado</th>
                  <th scope="col"><span className="visually-hidden">Acciones</span></th>
                </tr>
              </thead>
              <tbody>
                {reservas.map((r) => (
                  <tr key={r.codigo}>
                    <td className="cifra" data-label="Código">{r.codigo}</td>
                    <td data-label="Huésped">{r.nombre || r.email}</td>
                    <td className="cifra" data-label="Fechas">
                      {fechaCorta(r.llegada)} → {fechaCorta(r.salida)}
                      <br />
                      <span className="campo__ayuda">{r.noches} noches</span>
                    </td>
                    <td className="cifra" data-label="Huéspedes">{r.huespedes}</td>
                    <td data-label="Origen"><Etiqueta tono="neutra">{r.origen}</Etiqueta></td>
                    <td data-label="Estado">
                      <Etiqueta tono={tonoEstado(r.estado)}>
                        {r.estado}
                      </Etiqueta>
                    </td>
                    <td data-label="Acciones">
                      <div className="pila gap-e1">
                        {accionesPosibles(r).map((e) => (
                          <button
                            key={e.valor}
                            className={`boton boton--chico ${e.valor === 'CONFIRMADA' ? 'boton--primario' : 'boton--secundario'}`}
                            onClick={() => cambiar(r.codigo, e.valor)}
                            aria-label={`${e.texto} la reserva ${r.codigo}`}
                          >
                            {e.texto}
                          </button>
                        ))}
                        {accionesPosibles(r).length === 0 ? (
                          <span className="t-sm tenue">Sin acciones</span>
                        ) : null}
                        <button className="boton boton--chico boton--fantasma" onClick={() => abrir(r.codigo)}>
                          Ver detalle
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}

        {detalle ? (
          <aside className="tarjeta pila mt-e6" aria-labelledby="titulo-detalle">
            <h2 id="titulo-detalle" className="mb-0">{detalle.reserva.codigo}</h2>
            <p className="sin-margen">
              {detalle.reserva.nombre || detalle.reserva.email} · {fechaCorta(detalle.reserva.llegada)} →{' '}
              {fechaCorta(detalle.reserva.salida)}
            </p>
            <p className="sin-margen">
              {detalle.habitacion ? (
                <>Habitación {detalle.habitacion.codigo}{detalle.habitacion.tipo ? ` · ${detalle.habitacion.tipo}` : ''} · </>
              ) : null}
              {detalle.reserva.totalCents !== null && detalle.reserva.moneda ? (
                <>Total acordado: {monto(detalle.reserva.totalCents, detalle.reserva.moneda)}</>
              ) : (
                'Sin precio acordado: se reservó sin tarifa configurada'
              )}
            </p>
            <h3 className="t-base mb-0">Cuenta</h3>
            {!saldo || !detalle.reserva.moneda ? (
              <p className="campo__ayuda sin-margen">Sin movimientos registrados.</p>
            ) : (
              <>
                <p className="sin-margen">
                  <span className="cifra">
                    Abonado: {monto(saldo.abonadoCents, detalle.reserva.moneda)} · Pendiente:{' '}
                    {monto(saldo.pendienteCents, detalle.reserva.moneda)}
                  </span>
                </p>
                {saldo.movimientos.length > 0 ? (
                  <ul className="lista-marcada">
                    {saldo.movimientos.map((m) => (
                      <li key={m.id} className="campo__ayuda">
                        {monto(m.monto_cents, m.moneda)}{m.concepto ? ` · ${m.concepto}` : ''} por{' '}
                        {m.actor}
                        {m.anulado_en ? (
                          <> (anulado por {m.anulado_por})</>
                        ) : (
                            <>
                              {' '}
                              <button
                                className="boton boton--fantasma boton--chico no-imprimir"
                                type="button"
                                onClick={() => void anularAbono(m.id, detalle.reserva.codigo)}
                                aria-label={`Anular el abono de ${monto(m.monto_cents, m.moneda)}`}
                              >
                                Anular
                              </button>
                            </>
                        )}
                      </li>
                    ))}
                  </ul>
                ) : null}
                {admiteDinero(detalle.reserva.estado) ? (
                  <form
                    className="campos no-imprimir"
                    onSubmit={(e) => {
                      e.preventDefault();
                      void abonar(detalle.reserva.codigo, detalle.reserva.moneda ?? '');
                    }}
                  >
                    <div className="campo">
                      <label className="campo__etiqueta" htmlFor="abono-monto">Abono ({detalle.reserva.moneda})</label>
                      <input
                        id="abono-monto"
                        type="number"
                        min={0.01}
                        step={0.01}
                        required
                        value={abono.monto}
                        onChange={(e) => setAbono({ ...abono, monto: e.target.value })}
                      />
                    </div>
                    <div className="campo">
                      <label className="campo__etiqueta" htmlFor="abono-concepto">Concepto</label>
                      <input
                        id="abono-concepto"
                        value={abono.concepto}
                        onChange={(e) => setAbono({ ...abono, concepto: e.target.value })}
                        placeholder="Anticipo"
                      />
                    </div>
                    <button className="boton boton--secundario boton--chico" type="submit" disabled={!abono.monto}>
                      Registrar abono
                    </button>
                  </form>
                ) : (
                  <p className="campo__ayuda sin-margen no-imprimir">
                    Esta reserva está {detalle.reserva.estado}: no admite abonos. Si llegó a cobrarse,
                    eso es una devolución, no un abono: créala como reserva nueva y anótalo en el
                    historial.
                  </p>
                )}
              </>
            )}
            <h3 className="t-base mb-0">Historial</h3>            <ol className="pila gap-e1 lista-marcada">
              {detalle.historial.map((h, i) => (
                <li key={i} className="campo__ayuda">
                  {h.estado_ant ? `${h.estado_ant} → ` : 'creada como '}
                  <strong>{h.estado_nuevo}</strong>
                  {h.detalle ? ` · ${h.detalle}` : null} por {h.actor} el {h.en.slice(0, 16).replace('T', ' ')}
                </li>
              ))}
            </ol>
            <div className="no-imprimir">
              <HiloMensajes
                titulo="Conversación con el huésped"
                ladoPropio="HOTEL"
                cargar={(antesDe) =>
                  api.get<Hilo>(
                    `/api/admin/reservas/${detalle.reserva.codigo}/mensajes${antesDe ? `?antes_de=${antesDe}` : ''}`,
                  )
                }
                enviar={(texto) =>
                  api.post(`/api/admin/reservas/${detalle.reserva.codigo}/mensajes`, { texto }).then(() => undefined)
                }
              />
            </div>
            {admiteDinero(detalle.reserva.estado) && habitaciones ? (
              <form
                className="campos no-imprimir"
                onSubmit={(e) => {
                  e.preventDefault();
                  void reasignar(detalle.reserva.codigo);
                }}
              >
                <div className="campo">
                  <label className="campo__etiqueta" htmlFor="reasignar-hab">Cambiar a la habitación</label>
                  <select
                    id="reasignar-hab"
                    value={nuevaHabitacion}
                    onChange={(e) => setNuevaHabitacion(e.target.value)}
                  >
                    <option value="">Selecciona…</option>
                    {habitaciones.map((h) => (
                      <option key={h.id} value={h.id}>{h.codigo}</option>
                    ))}
                  </select>
                </div>
                <button className="boton boton--secundario boton--chico" type="submit" disabled={!nuevaHabitacion}>
                  Reasignar
                </button>
              </form>
            ) : null}
            {admiteDinero(detalle.reserva.estado) ? (
              <form
                className="campos no-imprimir"
                onSubmit={(e) => {
                  e.preventDefault();
                  void cambiarFechas(detalle.reserva.codigo);
                }}
              >
                <div className="campo">
                  <label className="campo__etiqueta" htmlFor="mover-llegada">Nueva llegada</label>
                  <input
                    id="mover-llegada"
                    type="date"
                    required
                    value={nuevasFechas.llegada}
                    onChange={(e) => setNuevasFechas({ ...nuevasFechas, llegada: e.target.value })}
                  />
                </div>
                <div className="campo">
                  <label className="campo__etiqueta" htmlFor="mover-salida">Nueva salida</label>
                  <input
                    id="mover-salida"
                    type="date"
                    required
                    min={nuevasFechas.llegada || undefined}
                    value={nuevasFechas.salida}
                    onChange={(e) => setNuevasFechas({ ...nuevasFechas, salida: e.target.value })}
                  />
                </div>
                <button
                  className="boton boton--secundario boton--chico"
                  type="submit"
                  disabled={!nuevasFechas.llegada || !nuevasFechas.salida}
                >
                  Cambiar fechas
                </button>
              </form>
            ) : null}
            <button
              className="boton boton--secundario boton--chico no-imprimir"
              type="button"
              onClick={() => window.print()}
            >
              Imprimir comprobante
            </button>
            <button className="boton boton--secundario boton--chico no-imprimir" onClick={() => setDetalle(null)}>
              Cerrar detalle
            </button>
          </aside>
        ) : null}
      </section>
    </main>
  );
}

function RequiereSesion() {
  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <PuertaAdmin>Esta pantalla es para el personal del hotel.</PuertaAdmin>
      </section>
    </main>
  );
}