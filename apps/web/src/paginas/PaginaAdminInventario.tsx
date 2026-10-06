import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { useSesion } from '../api/useSesion';
import { Aviso, Cargando, Etiqueta, MensajeError } from '../componentes/Estado';

type Habitacion = { id: number; codigo: string; roomTypeId: number; nombre: string; estado: string };
type Oferta = {
  habitacion: { id: number; codigo: string };
  tipo: { capacidadMax: number };
  totalCents: number;
  moneda: string;
  noches: number;
};
type Tipo = { id: number; codigo: string; nombre: string; capacidadMax: number };

/** Calendario de ocupación y gestión de inventario y bloqueos. */
export function PaginaAdminInventario() {
  const sesion = useSesion();
  const [mes, setMes] = useState(mesActual());
  const [habitaciones, setHabitaciones] = useState<Habitacion[] | null>(null);
  const [tipos, setTipos] = useState<Tipo[] | null>(null);
  const [ocupadas, setOcupadas] = useState<Set<number>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);

  const [nuevoTipo, setNuevoTipo] = useState({ codigo: '', nombre: '', capacidadMax: 2 });
  const [nuevaHabitacion, setNuevaHabitacion] = useState({ codigo: '', roomTypeId: '', nombre: '' });
  const [bloqueo, setBloqueo] = useState({ roomId: '', desde: '', hasta: '', motivo: '' });

  async function cargar() {
    setCargando(true);
    setError(null);
    try {
      const [habs, tiposCargados] = await Promise.all([
        api.get<Habitacion[]>('/api/admin/habitaciones'),
        api.get<Tipo[]>('/api/admin/tipos'),
      ]);
      setHabitaciones(habs);
      setTipos(tiposCargados);

      // Disponibilidad real del mes: si una habitación no aparece en la oferta del mes completo,
      // tiene alguna noche ocupada o bloqueada. Es la misma consulta que ve el huésped.
      const [desde, hasta] = rangoMes(mes);
      const libres = await Promise.all(
        habs.map(async (h) => {
          const r = await api
            .get<{ ofertas: Oferta[] }>(`/api/disponibilidad?llegada=${desde}&salida=${hasta}&huespedes=1`)
            .catch(() => ({ ofertas: [] }));
          return r.ofertas.some((o) => o.habitacion.id === h.id);
        }),
      );
      setOcupadas(new Set(habs.filter((_h, i) => !libres[i]).map((h) => h.id)));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo cargar el inventario');
    } finally {
      setCargando(false);
    }
  }

  useEffect(() => {
    void cargar();
  }, [mes]);

  async function accion<T>(tarea: () => Promise<T>) {
    setError(null);
    try {
      await tarea();
      await cargar();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo completar la operación');
    }
  }

  if (sesion.haySesion === false) return <Aviso tono="aviso" titulo="Sesión requerida">Inicia sesión para gestionar el inventario.</Aviso>;
  if (sesion.haySesion === null) return <Cargando texto="Comprobando sesión" />;

  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <h1 className="seccion__titulo">Inventario y disponibilidad</h1>
        <p className="seccion__intro">
          Todo lo que registres aquí es lo que el hotel ofrece. Sin inventario cargado, la web pública
          no muestra habitaciones.
        </p>

        {error ? <MensajeError texto={error} /> : null}
        {cargando ? <Cargando /> : null}

        <div className="campo ancho-campo mb-e5">
          <label className="campo__etiqueta" htmlFor="mes-inv">Mes</label>
          <input id="mes-inv" type="month" className="cifra" value={mes} onChange={(e) => setMes(e.target.value)} />
        </div>

        <h2 className="t-xl">Habitaciones ({habitaciones?.length ?? 0})</h2>
        {habitaciones && habitaciones.length > 0 ? (
          <div className="tabla-envoltura">
            <table className="tabla">
              <caption>Habitaciones registradas</caption>
              <thead>
                <tr>
                  <th scope="col">Código</th>
                  <th scope="col">Nombre</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Mes completo</th>
                  <th scope="col"><span className="visually-hidden">Acciones</span></th>
                </tr>
              </thead>
              <tbody>
                {habitaciones.map((h) => (
                  <tr key={h.id}>
                    <td className="cifra">{h.codigo}</td>
                    <td>{h.nombre || '—'}</td>
                    <td><Etiqueta tono={h.estado === 'ACTIVA' ? 'exito' : 'neutra'}>{h.estado}</Etiqueta></td>
                    <td>
                      {ocupadas.has(h.id) ? <Etiqueta tono="aviso">Con ocupación</Etiqueta> : <Etiqueta tono="exito">Libre</Etiqueta>}
                    </td>
                    <td>
                      <div className="pila pila--fila gap-e1">
                        {h.estado === 'ACTIVA' ? (
                          <button
                            className="boton boton--chico boton--secundario"
                            onClick={() => accion(() => api.post(`/api/admin/habitaciones/${h.id}/estado`, { estado: 'FUERA_DE_SERVICIO' }))}
                          >
                            Retirar
                          </button>
                        ) : (
                          <button
                            className="boton boton--chico boton--primario"
                            onClick={() => accion(() => api.post(`/api/admin/habitaciones/${h.id}/estado`, { estado: 'ACTIVA' }))}
                          >
                            Reactivar
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="campo__ayuda">No hay habitaciones registradas todavía.</p>
        )}

        <div className="rejilla mt-e6">
          <form
            className="tarjeta pila"
            onSubmit={(e) => {
              e.preventDefault();
              void accion(() =>
                api.post('/api/admin/tipos', {
                  codigo: nuevoTipo.codigo,
                  nombre: nuevoTipo.nombre,
                  capacidadMax: Number(nuevoTipo.capacidadMax),
                }),
              );
            }}
          >
            <h2 className="t-lg mb-0">Nuevo tipo de habitación</h2>
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="tipo-codigo">Código</label>
              <input id="tipo-codigo" required value={nuevoTipo.codigo} onChange={(e) => setNuevoTipo({ ...nuevoTipo, codigo: e.target.value })} />
            </div>
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="tipo-nombre">Nombre</label>
              <input id="tipo-nombre" required value={nuevoTipo.nombre} onChange={(e) => setNuevoTipo({ ...nuevoTipo, nombre: e.target.value })} />
            </div>
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="tipo-capacidad">Capacidad máxima</label>
              <input
                id="tipo-capacidad"
                type="number"
                min={1}
                required
                value={nuevoTipo.capacidadMax}
                onChange={(e) => setNuevoTipo({ ...nuevoTipo, capacidadMax: Number(e.target.value) })}
              />
            </div>
            <button className="boton boton--primario" type="submit">Crear tipo</button>
          </form>

          <form
            className="tarjeta pila"
            onSubmit={(e) => {
              e.preventDefault();
              void accion(() =>
                api.post('/api/admin/habitaciones', {
                  codigo: nuevaHabitacion.codigo,
                  roomTypeId: Number(nuevaHabitacion.roomTypeId),
                  nombre: nuevaHabitacion.nombre,
                }),
              );
            }}
          >
            <h2 className="t-lg mb-0">Nueva habitación</h2>
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="hab-codigo">Código</label>
              <input id="hab-codigo" required className="cifra" value={nuevaHabitacion.codigo} onChange={(e) => setNuevaHabitacion({ ...nuevaHabitacion, codigo: e.target.value })} />
            </div>
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="hab-tipo">Tipo</label>
              <select
                id="hab-tipo"
                required
                value={nuevaHabitacion.roomTypeId}
                onChange={(e) => setNuevaHabitacion({ ...nuevaHabitacion, roomTypeId: e.target.value })}
              >
                <option value="">Selecciona un tipo</option>
                {tipos?.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.nombre} (máx. {t.capacidadMax})
                  </option>
                ))}
              </select>
            </div>
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="hab-nombre">Nombre</label>
              <input id="hab-nombre" value={nuevaHabitacion.nombre} onChange={(e) => setNuevaHabitacion({ ...nuevaHabitacion, nombre: e.target.value })} />
            </div>
            <button className="boton boton--primario" type="submit" disabled={!nuevaHabitacion.roomTypeId}>
              Crear habitación
            </button>
          </form>

          <form
            className="tarjeta pila"
            onSubmit={(e) => {
              e.preventDefault();
              void accion(() =>
                api.post('/api/admin/bloqueos', {
                  roomId: bloqueo.roomId ? Number(bloqueo.roomId) : null,
                  desde: bloqueo.desde,
                  hasta: bloqueo.hasta,
                  motivo: bloqueo.motivo,
                }),
              );
            }}
          >
            <h2 className="t-lg mb-0">Bloqueo de mantenimiento</h2>
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="bloqueo-hab">Habitación</label>
              <select id="bloqueo-hab" value={bloqueo.roomId} onChange={(e) => setBloqueo({ ...bloqueo, roomId: e.target.value })}>
                <option value="">Todo el hotel</option>
                {habitaciones?.map((h) => (
                  <option key={h.id} value={h.id}>{h.codigo}</option>
                ))}
              </select>
            </div>
            <div className="campos">
              <div className="campo">
                <label className="campo__etiqueta" htmlFor="bloqueo-desde">Desde</label>
                <input id="bloqueo-desde" type="date" required value={bloqueo.desde} onChange={(e) => setBloqueo({ ...bloqueo, desde: e.target.value })} />
              </div>
              <div className="campo">
                <label className="campo__etiqueta" htmlFor="bloqueo-hasta">Hasta</label>
                <input id="bloqueo-hasta" type="date" required value={bloqueo.hasta} onChange={(e) => setBloqueo({ ...bloqueo, hasta: e.target.value })} />
              </div>
            </div>
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="bloqueo-motivo">Motivo</label>
              <input id="bloqueo-motivo" value={bloqueo.motivo} onChange={(e) => setBloqueo({ ...bloqueo, motivo: e.target.value })} />
            </div>
            <button className="boton boton--primario" type="submit">Aplicar bloqueo</button>
          </form>
        </div>

        <p className="mt-e6">
          <Link to="/admin/reservas">Volver a las reservas</Link>
        </p>
      </section>
    </main>
  );
}

function mesActual(): string {
  return new Date().toISOString().slice(0, 7);
}

function rangoMes(mes: string): [string, string] {
  const inicio = new Date(`${mes}-01T12:00:00`);
  const fin = new Date(inicio);
  fin.setMonth(fin.getMonth() + 1);
  return [`${mes}-01`, fin.toISOString().slice(0, 10)];
}