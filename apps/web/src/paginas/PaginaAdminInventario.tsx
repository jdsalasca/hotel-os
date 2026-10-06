import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { useSesion } from '../api/useSesion';
import { Aviso, Cargando, Etiqueta, MensajeError } from '../componentes/Estado';
import { fechaCorta } from '../api/formato';

type Habitacion = { id: number; codigo: string; roomTypeId: number; nombre: string; estado: string };
type Oferta = {
  habitacion: { id: number; codigo: string };
  tipo: { capacidadMax: number };
  totalCents: number;
  moneda: string;
  noches: number;
};
type Tipo = { id: number; codigo: string; nombre: string; capacidadMax: number };
type Plan = { id: number; codigo: string; nombre: string; moneda: string };
type Noche = { fecha: string; precioCents: number; minEstancia: number | null; maxEstancia: number | null; cerrado: boolean };

/** Calendario de ocupación y gestión de inventario y bloqueos. */
export function PaginaAdminInventario() {
  const sesion = useSesion();
  const [mes, setMes] = useState(mesActual());
  const [habitaciones, setHabitaciones] = useState<Habitacion[] | null>(null);
  const [tipos, setTipos] = useState<Tipo[] | null>(null);
  const [ocupadas, setOcupadas] = useState<Set<number>>(new Set());
  const [planes, setPlanes] = useState<Plan[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);

  const [nuevoTipo, setNuevoTipo] = useState({ codigo: '', nombre: '', capacidadMax: 2 });
  const [nuevaHabitacion, setNuevaHabitacion] = useState({ codigo: '', roomTypeId: '', nombre: '' });
  const [bloqueo, setBloqueo] = useState({ roomId: '', desde: '', hasta: '', motivo: '' });
  const [nuevoPlan, setNuevoPlan] = useState({ codigo: '', nombre: '', moneda: 'COP' });

  // Precios por noche: qué hay guardado y qué está escribiendo el hotel ahora mismo.
  const [tarifas, setTarifas] = useState<{ planId: string; tipoId: string }>({ planId: '', tipoId: '' });
  const [mesTarifas, setMesTarifas] = useState(mesActual());
  const [noches, setNoches] = useState<Map<string, Noche>>(new Map());
  const [borrador, setBorrador] = useState<Record<string, string>>({});
  const [cerradas, setCerradas] = useState<Record<string, boolean>>({});

  async function cargar() {
    setCargando(true);
    setError(null);
    try {
      const [habs, tiposCargados, planesCargados] = await Promise.all([
        api.get<Habitacion[]>('/api/admin/habitaciones'),
        api.get<Tipo[]>('/api/admin/tipos'),
        api.get<Plan[]>('/api/admin/planes'),
      ]);
      setHabitaciones(habs);
      setTipos(tiposCargados);
      setPlanes(planesCargados);

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

  // Al cambiar de plan, tipo o mes se releen las noches ya tarifadas para no inventar un precio
  // que el hotel no ha fijado: lo guardado se muestra tal cual está en la base.
  useEffect(() => {
    const [desde, hasta] = rangoMes(mesTarifas);
    if (!tarifas.planId || !tarifas.tipoId) {
      setNoches(new Map());
      setBorrador({});
      setCerradas({});
      return;
    }
    void api
      .get<Noche[]>(`/api/admin/tarifas?planId=${tarifas.planId}&tipoId=${tarifas.tipoId}&desde=${desde}&hasta=${hasta}`)
      .then((lista) => {
        const mapa = new Map(lista.map((n) => [n.fecha, n]));
        setNoches(mapa);
        setBorrador(Object.fromEntries(lista.map((n) => [n.fecha, String(n.precioCents / 100)])));
        setCerradas(Object.fromEntries(lista.map((n) => [n.fecha, n.cerrado])));
      })
      .catch((e) => setError(e instanceof Error ? e.message : 'No se pudieron leer las tarifas'));
  }, [tarifas.planId, tarifas.tipoId, mesTarifas]);

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
          <form
            className="tarjeta pila"
            onSubmit={(e) => {
              e.preventDefault();
              void accion(() =>
                api.post('/api/admin/planes', {
                  codigo: nuevoPlan.codigo,
                  nombre: nuevoPlan.nombre,
                  moneda: nuevoPlan.moneda,
                }),
              );
            }}
          >
            <h2 className="t-lg mb-0">Nuevo plan tarifario</h2>
            <p className="campo__ayuda sin-margen">
              Un plan agrupa precios. Sin plan y sin precios, la web no muestra habitaciones.
            </p>
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="plan-codigo">Código</label>
              <input id="plan-codigo" required value={nuevoPlan.codigo} onChange={(e) => setNuevoPlan({ ...nuevoPlan, codigo: e.target.value.toUpperCase() })} />
            </div>
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="plan-nombre">Nombre</label>
              <input id="plan-nombre" required value={nuevoPlan.nombre} onChange={(e) => setNuevoPlan({ ...nuevoPlan, nombre: e.target.value })} />
            </div>
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="plan-moneda">Moneda</label>
              <input
                id="plan-moneda"
                required
                maxLength={3}
                pattern="[A-Za-z]{3}"
                title="Código ISO 4217 de tres letras, por ejemplo COP"
                value={nuevoPlan.moneda}
                onChange={(e) => setNuevoPlan({ ...nuevoPlan, moneda: e.target.value.toUpperCase() })}
              />
            </div>
            <button className="boton boton--primario" type="submit">Crear plan</button>
          </form>
        </div>

        <h2 className="t-xl mt-e6">Precios por noche</h2>
        <p className="seccion__intro mb-0">
          Elige plan, tipo de habitación y mes. Solo se envían las noches que hayas cambiado.
        </p>
        <div className="campos">
          <div className="campo">
            <label className="campo__etiqueta" htmlFor="tar-plan">Plan</label>
            <select id="tar-plan" value={tarifas.planId} onChange={(e) => setTarifas({ ...tarifas, planId: e.target.value })}>
              <option value="">Selecciona un plan</option>
              {planes?.map((p) => (
                <option key={p.id} value={p.id}>{p.nombre} ({p.moneda})</option>
              ))}
            </select>
          </div>
          <div className="campo">
            <label className="campo__etiqueta" htmlFor="tar-tipo">Tipo</label>
            <select id="tar-tipo" value={tarifas.tipoId} onChange={(e) => setTarifas({ ...tarifas, tipoId: e.target.value })}>
              <option value="">Selecciona un tipo</option>
              {tipos?.map((t) => (
                <option key={t.id} value={t.id}>{t.nombre}</option>
              ))}
            </select>
          </div>
          <div className="campo">
            <label className="campo__etiqueta" htmlFor="tar-mes">Mes</label>
            <input id="tar-mes" type="month" className="cifra" value={mesTarifas} onChange={(e) => setMesTarifas(e.target.value)} />
          </div>
        </div>

        {tarifas.planId && tarifas.tipoId ? (
          <form
            className="tarjeta pila"
            onSubmit={(e) => {
              e.preventDefault();
              const dias = diasDelMes(mesTarifas);
              const planId = Number(tarifas.planId);
              const tipoId = Number(tarifas.tipoId);
              const cambios = dias.filter((dia) => {
                const guardado = noches.get(dia);
                const texto = (borrador[dia] ?? '').trim();
                const centimos = texto === '' ? null : Math.round(Number(texto) * 100);
                const cerrado = cerradas[dia] ?? false;
                if (centimos === null) return guardado !== undefined;
                if (Number.isNaN(centimos)) return false;
                return guardado?.precioCents !== centimos || guardado?.cerrado !== cerrado;
              });
              if (cambios.length === 0) {
                setError('No hay cambios que guardar.');
                return;
              }
              void accion(async () => {
                for (const dia of cambios) {
                  const centimos = Math.round(Number((borrador[dia] ?? '').trim()) * 100);
                  await api.post('/api/admin/tarifas', {
                    ratePlanId: planId,
                    roomTypeId: tipoId,
                    fecha: dia,
                    precioCents: centimos,
                    cerrado: cerradas[dia] ?? false,
                  });
                }
              });
            }}
          >
            <div className="tabla-envoltura">
              <table className="tabla">
                <caption>Precios guardados y pendientes del mes</caption>
                <thead>
                  <tr>
                    <th scope="col">Noche</th>
                    <th scope="col">Precio</th>
                    <th scope="col">Estado</th>
                    <th scope="col">Cerrada</th>
                  </tr>
                </thead>
                <tbody>
                  {diasDelMes(mesTarifas).map((dia) => {
                    const guardada = noches.get(dia);
                    return (
                      <tr key={dia}>
                        <th scope="row" className="cifra">{fechaCorta(dia)}</th>
                        <td>
                          <label className="visually-hidden" htmlFor={`precio-${dia}`}>Precio de la noche {dia}</label>
                          <input
                            id={`precio-${dia}`}
                            className="cifra"
                            type="number"
                            min={0}
                            step="0.01"
                            inputMode="decimal"
                            value={borrador[dia] ?? ''}
                            placeholder="Sin fijar"
                            onChange={(e) => setBorrador({ ...borrador, [dia]: e.target.value })}
                          />
                        </td>
                        <td>
                          {guardada ? (
                            <Etiqueta tono={guardada.cerrado ? 'neutra' : 'exito'}>
                              {guardada.cerrado ? 'No vendible' : 'Fijada'}
                            </Etiqueta>
                          ) : (
                            <Etiqueta tono="aviso">Sin precio</Etiqueta>
                          )}
                        </td>
                        <td>
                          <label className="visually-hidden" htmlFor={`cerrada-${dia}`}>Cerrar la noche {dia}</label>
                          <input
                            id={`cerrada-${dia}`}
                            type="checkbox"
                            checked={cerradas[dia] ?? false}
                            onChange={(e) => setCerradas({ ...cerradas, [dia]: e.target.checked })}
                          />
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <button className="boton boton--primario no-estirar" type="submit">
              Guardar precios del mes
            </button>
          </form>
        ) : (
          <p className="campo__ayuda">Selecciona un plan y un tipo de habitación para ver el mes.</p>
        )}

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

/** Fecha en ISO desde la hora local. `toISOString` converts a UTC y en husos al este desplaza el
 *  día un día hacia atrás, que es justo el error que hace que falte una noche en un precio. */
function isoLocal(fecha: Date): string {
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${fecha.getFullYear()}-${mes}-${dia}`;
}

function rangoMes(mes: string): [string, string] {
  const inicio = new Date(`${mes}-01T12:00:00`);
  const fin = new Date(inicio);
  fin.setMonth(fin.getMonth() + 1);
  return [isoLocal(inicio), isoLocal(fin)];
}

/** Las noches del mes: la última es la que precede al día 1 del mes siguiente. */
function diasDelMes(mes: string): string[] {
  const [desde, hasta] = rangoMes(mes);
  const dias: string[] = [];
  for (let dia = new Date(`${desde}T12:00:00`), fin = new Date(`${hasta}T12:00:00`); dia < fin; dia.setDate(dia.getDate() + 1)) {
    dias.push(isoLocal(dia));
  }
  return dias;
}