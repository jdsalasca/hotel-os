import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { fechaCorta, monto } from '../api/formato';
import { useSesion } from '../api/useSesion';
import { Aviso, Cargando, Etiqueta, MensajeError, Vacio } from '../componentes/Estado';

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

export function PaginaAdminReservas() {
  const sesion = useSesion();
  const [reservas, setReservas] = useState<Reserva[] | null>(null);
  const [detalle, setDetalle] = useState<Detalle | null>(null);
  const [habitaciones, setHabitaciones] = useState<Habitacion[] | null>(null);
  const [nuevaHabitacion, setNuevaHabitacion] = useState('');
  const [nuevasFechas, setNuevasFechas] = useState({ llegada: '', salida: '' });
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);
  const [texto, setTexto] = useState('');
  const [estado, setEstado] = useState('');

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

        <form
          className="filtros"
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
        </form>

        {cargando ? <Cargando /> : null}

        {!cargando && reservas && reservas.length === 0 ? (
          <Vacio titulo="Sin resultados" detalle="Ninguna reserva coincide con ese filtro." />
        ) : null}

        {reservas && reservas.length > 0 ? (
          <div className="tabla-envoltura">
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
            <h3 className="t-base mb-0">Historial</h3>
            <ol className="pila gap-e1 lista-marcada">
              {detalle.historial.map((h, i) => (
                <li key={i} className="campo__ayuda">
                  {h.estado_ant ? `${h.estado_ant} → ` : 'creada como '}
                  <strong>{h.estado_nuevo}</strong>
                  {h.detalle ? ` · ${h.detalle}` : null} por {h.actor} el {h.en.slice(0, 16).replace('T', ' ')}
                </li>
              ))}
            </ol>
            {(detalle.reserva.estado === 'PENDIENTE' || detalle.reserva.estado === 'CONFIRMADA') && habitaciones ? (
              <form
                className="campos"
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
            {(detalle.reserva.estado === 'PENDIENTE' || detalle.reserva.estado === 'CONFIRMADA') ? (
              <form
                className="campos"
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
            <button className="boton boton--secundario boton--chico" onClick={() => setDetalle(null)}>
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
        <Aviso tono="aviso" titulo="Sesión requerida">
          <p>Esta pantalla es para el personal del hotel.</p>
          <Link className="boton boton--primario" to="/admin/entrar">
            Iniciar sesión
          </Link>
        </Aviso>
      </section>
    </main>
  );
}