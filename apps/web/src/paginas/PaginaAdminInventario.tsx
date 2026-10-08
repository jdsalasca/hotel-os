import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { useSesion } from '../api/useSesion';
import { Aviso, Cargando, Etiqueta, MensajeError } from '../componentes/Estado';
import { fechaCorta } from '../api/formato';

type Habitacion = { id: number; codigo: string; roomTypeId: number; nombre: string; estado: string };
type Tipo = { id: number; codigo: string; nombre: string; capacidadMax: number };
type Plan = { id: number; codigo: string; nombre: string; moneda: string; descuentoPct: number };
type Noche = { fecha: string; precioCents: number; minEstancia: number | null; maxEstancia: number | null; cerrado: boolean };
type FilaPrevia = { fecha: string; valida: boolean; motivo?: string; precioCents?: number; cerrado?: boolean; nueva?: boolean };
type PreviaLote = { lista: boolean; filas: FilaPrevia[] };

/** Monedas que el hotel puede usar en sus planes. El backend acepta cualquier ISO 4217;
 * esta lista cerrada es para no escribir COP como "cop", "Cop" o "COL" en un campo abierto. */
const MONEDAS = [
  { codigo: 'COP', nombre: 'Peso colombiano' },
  { codigo: 'USD', nombre: 'Dólar estadounidense' },
  { codigo: 'EUR', nombre: 'Euro' },
  { codigo: 'MXN', nombre: 'Peso mexicano' },
  { codigo: 'BRL', nombre: 'Real brasileño' },
  { codigo: 'PEN', nombre: 'Sol peruano' },
  { codigo: 'ARS', nombre: 'Peso argentino' },
  { codigo: 'CLP', nombre: 'Peso chileno' },
  { codigo: 'GBP', nombre: 'Libra esterlina' },
];

/** Días ISO para el rango: 1 lunes … 7 domingo, como los valida el backend. */
const DIAS_SEMANA = [
  { n: 1, nombre: 'Lunes' },
  { n: 2, nombre: 'Martes' },
  { n: 3, nombre: 'Miércoles' },
  { n: 4, nombre: 'Jueves' },
  { n: 5, nombre: 'Viernes' },
  { n: 6, nombre: 'Sábado' },
  { n: 7, nombre: 'Domingo' },
];

type Bloqueo = { id: number; habitacion: string; desde: string; hasta: string; motivo: string };
type Amenidad = { id: number; codigo: string; nombre: string };
/** Fila del calendario: una habitación con una entrada por noche del mes. */
type CalendarioDia = {
  id: number;
  codigo: string;
  noches: { fecha: string; estado: string; codigoReserva: string | null }[];
};
/** Estados del calendario. La forma visible cambia además del color para no depender solo del color. */
const ESTADOS_CALENDARIO: Record<string, { etiqueta: string; simbolo: string }> = {
  LIBRE: { etiqueta: 'Libre', simbolo: '○' },
  OCUPADA: { etiqueta: 'Ocupada', simbolo: '●' },
  BLOQUEADA: { etiqueta: 'Bloqueada', simbolo: '■' },
  MANTENIMIENTO: { etiqueta: 'En mantenimiento', simbolo: '✕' },
  FUERA_DE_SERVICIO: { etiqueta: 'Fuera de servicio', simbolo: '✕' },
};

function estadoCalendario(valor: string): { etiqueta: string; simbolo: string } {
  return ESTADOS_CALENDARIO[valor] ?? { etiqueta: `Estado desconocido (${valor})`, simbolo: '?' };
}

function claveEstadoCalendario(valor: string): string {
  return Object.hasOwn(ESTADOS_CALENDARIO, valor) ? valor.toLowerCase().replace(/_/g, '-') : 'desconocida';
}

function fechaDia(iso: string): Date {
  return new Date(`${iso}T12:00:00`);
}

function letraDiaSemana(iso: string): string {
  return ['D', 'L', 'M', 'X', 'J', 'V', 'S'][fechaDia(iso).getDay()] ?? '';
}

function esFinDeSemana(iso: string): boolean {
  const dia = fechaDia(iso).getDay();
  return dia === 0 || dia === 6;
}

/** Calendario de ocupación y gestión de inventario y bloqueos. */
export function PaginaAdminInventario() {
  const sesion = useSesion();
  const [mes, setMes] = useState(mesActual());
  const [habitaciones, setHabitaciones] = useState<Habitacion[] | null>(null);
  const [tipos, setTipos] = useState<Tipo[] | null>(null);
  const [calendario, setCalendario] = useState<CalendarioDia[] | null>(null);
  const [bloqueos, setBloqueos] = useState<Bloqueo[] | null>(null);
  const [planes, setPlanes] = useState<Plan[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);

  const [nuevoTipo, setNuevoTipo] = useState({ codigo: '', nombre: '', capacidadMax: 2 });
  const [nuevaHabitacion, setNuevaHabitacion] = useState({ codigo: '', roomTypeId: '', nombre: '' });
  const [bloqueo, setBloqueo] = useState({ roomId: '', desde: '', hasta: '', motivo: '' });
  const [nuevoPlan, setNuevoPlan] = useState({ codigo: '', nombre: '', moneda: 'COP', descuentoPct: '' });
  const [catalogoServicios, setCatalogoServicios] = useState<Amenidad[] | null>(null);
  const [tipoServicios, setTipoServicios] = useState('');
  const [marcados, setMarcados] = useState<number[]>([]);

  /** Alta guiada: el hotelero avanza 1 Tipo → 2 Habitación → 3 Servicios → 4 Plan. */
  const [paso, setPaso] = useState(1);
  const tituloPaso = useRef<HTMLHeadingElement>(null);
  const primerPaso = useRef(true);
  useEffect(() => {
    if (primerPaso.current) {
      primerPaso.current = false;
      return;
    }
    tituloPaso.current?.focus();
  }, [paso]);

  useEffect(() => {
    void api
      .get<{ amenidades: Amenidad[] }>('/api/amenidades')
      .then((datos) => setCatalogoServicios(datos.amenidades))
      .catch(() => setCatalogoServicios([]));
  }, []);

  /** Lecturas de servicios en vuelo: si el hotel cambia de tipo antes de que vuelvan,
   * las viejas se ignoran en vez de marcar lo de otro tipo. */
  const peticionServicios = useRef(0);

  async function elegirTipoServicios(id: string) {
    setTipoServicios(id);
    const n = ++peticionServicios.current;
    if (!id) {
      setMarcados([]);
      return;
    }
    try {
      const datos = await api.get<{ porTipo: Record<string, Amenidad[]> }>(
        `/api/amenidades/por-tipo?ids=${id}`,
      );
      if (peticionServicios.current !== n) return;
      setMarcados((datos.porTipo[id] ?? []).map((a) => a.id));
    } catch (e) {
      if (peticionServicios.current !== n) return;
      setError(e instanceof Error ? e.message : 'No se pudieron leer los servicios');
      setMarcados([]);
    }
  }

  function alternarServicio(id: number) {
    setMarcados((previos) =>
      previos.includes(id) ? previos.filter((m) => m !== id) : [...previos, id],
    );
  }

  // Precios por noche: qué hay guardado y qué está escribiendo el hotel ahora mismo.
  const [tarifas, setTarifas] = useState<{ planId: string; tipoId: string }>({ planId: '', tipoId: '' });
  const [mesTarifas, setMesTarifas] = useState(mesActual());
  const [noches, setNoches] = useState<Map<string, Noche>>(new Map());
  const [borrador, setBorrador] = useState<Record<string, string>>({});
  const [cerradas, setCerradas] = useState<Record<string, boolean>>({});
  /** Previa del lote: lo que se guardaría al confirmar, sin haber escrito nada todavía. */
  const [previa, setPrevia] = useState<PreviaLote | null>(null);
  /** De dónde salió la previa: el mes o un rango. Confirmar usa la misma fuente. */
  const [previaOrigen, setPreviaOrigen] = useState<'mes' | 'rango'>('mes');
  const [rangoEnviado, setRangoEnviado] = useState<{
    rango: { desde: string; hasta: string; diasSemana: number[] };
    precioCents: number;
    cerrado: boolean;
  } | null>(null);
  const [rango, setRango] = useState({ desde: '', hasta: '', dias: [1, 2, 3, 4, 5, 6, 7], precio: '', cerrado: false });
  /** Al confirmar, las noches se releen para mostrar lo guardado de verdad, no el borrador. */
  const [versionTarifas, setVersionTarifas] = useState(0);

  async function cargar() {
    setCargando(true);
    setError(null);
    try {
      const [desde, hasta] = rangoMes(mes);
      const [habs, tiposCargados, planesCargados, calendarioCargado, bloqueosCargados] = await Promise.all([
        api.get<Habitacion[]>('/api/admin/habitaciones'),
        api.get<Tipo[]>('/api/admin/tipos'),
        api.get<Plan[]>('/api/admin/planes'),
        api.get<CalendarioDia[]>(`/api/admin/calendario?desde=${desde}&hasta=${hasta}`),
        api.get<Bloqueo[]>('/api/admin/bloqueos'),
      ]);
      setHabitaciones(habs);
      setTipos(tiposCargados);
      setPlanes(planesCargados);
      setCalendario(calendarioCargado);
      setBloqueos(bloqueosCargados);
    } catch (e) {
      setCalendario(null);
      setBloqueos(null);
      setError(e instanceof Error ? e.message : 'No se pudo cargar el inventario');
    } finally {
      setCargando(false);
    }
  }

  useEffect(() => {
    void cargar();
  }, [mes]);

  /** Lecturas de noches en vuelo: como en la búsqueda pública, si el hotel cambia de
   * plan, tipo o mes antes de que vuelvan, las viejas se ignoran en vez de pintar (y
   * tarifar sobre) otro plan. */
  const peticionTarifas = useRef(0);

  // Al cambiar de plan, tipo o mes se releen las noches ya tarifadas para no inventar un precio
  // que el hotel no ha fijado: lo guardado se muestra tal cual está en la base.
  useEffect(() => {
    const [desde, hasta] = rangoMes(mesTarifas);
    // También invalida lo que venía en camino: limpiar el plan con una lectura en vuelo
    // no puede dejar las noches del plan viejo en pantalla.
    const id = ++peticionTarifas.current;
    if (!tarifas.planId || !tarifas.tipoId) {
      setNoches(new Map());
      setBorrador({});
      setCerradas({});
      setPrevia(null);
      setPreviaOrigen('mes');
      setRangoEnviado(null);
      return;
    }
    setPrevia(null);
    setPreviaOrigen('mes');
    setRangoEnviado(null);
    void api
      .get<Noche[]>(`/api/admin/tarifas?planId=${tarifas.planId}&tipoId=${tarifas.tipoId}&desde=${desde}&hasta=${hasta}`)
      .then((lista) => {
        if (peticionTarifas.current !== id) return;
        const mapa = new Map(lista.map((n) => [n.fecha, n]));
        setNoches(mapa);
        setBorrador(Object.fromEntries(lista.map((n) => [n.fecha, String(n.precioCents / 100)])));
        setCerradas(Object.fromEntries(lista.map((n) => [n.fecha, n.cerrado])));
      })
      .catch((e) => {
        if (peticionTarifas.current !== id) return;
        setError(e instanceof Error ? e.message : 'No se pudieron leer las tarifas');
      });
  }, [tarifas.planId, tarifas.tipoId, mesTarifas, versionTarifas]);

  /** Noches que cambiaron en el borrador, listas para la previa o el lote. */
  function cambiosDelMes() {    return diasDelMes(mesTarifas)
      .filter((dia) => {
        const guardado = noches.get(dia);
        const texto = (borrador[dia] ?? '').trim();
        const centimos = texto === '' ? null : Math.round(Number(texto) * 100);
        const cerrado = cerradas[dia] ?? false;
        if (centimos === null) return guardado !== undefined;
        if (Number.isNaN(centimos)) return false;
        return guardado?.precioCents !== centimos || guardado?.cerrado !== cerrado;
      })
      .map((dia) => {
        const texto = (borrador[dia] ?? '').trim();
        return {
          fecha: dia,
          precioCents: texto === '' ? 0 : Math.round(Number(texto) * 100),
          cerrado: cerradas[dia] ?? false,
        };
      });
  }

  /** Confirma lo previado, venga del mes o de un rango: lo mismo que se revisó. */
  function confirmarPrevia() {
    setError(null);
    const guardar =
      previaOrigen === 'rango' && rangoEnviado
        ? api.post('/api/admin/tarifas/lote/rango', {
            ratePlanId: Number(tarifas.planId),
            roomTypeId: Number(tarifas.tipoId),
            rango: rangoEnviado.rango,
            precioCents: rangoEnviado.precioCents,
            cerrado: rangoEnviado.cerrado,
          })
        : api.post('/api/admin/tarifas/lote', {
            ratePlanId: Number(tarifas.planId),
            roomTypeId: Number(tarifas.tipoId),
            noches: cambiosDelMes(),
          });
    void guardar
      .then(() => {
        setPrevia(null);
        setPreviaOrigen('mes');
        setRangoEnviado(null);
        setVersionTarifas((v) => v + 1);
        void cargar();
      })
      .catch((err) => setError(err instanceof Error ? err.message : 'No se pudo guardar el lote'));
  }

  /** Pide la previa de un rango (p. ej. fines de semana): la validación fina es por fila. */
  function revisarRango(evento: React.FormEvent) {
    evento.preventDefault();
    const precio = Math.round(Number(rango.precio) * 100);
    if (!rango.desde || !rango.hasta) {
      setError('El rango necesita desde y hasta.');
      return;
    }
    if (rango.precio.trim() === '' || Number.isNaN(precio)) {
      setError('El rango necesita un precio.');
      return;
    }
    if (rango.dias.length === 0) {
      setError('Marca al menos un día de la semana.');
      return;
    }
    setError(null);
    setPrevia(null);
    const enviado = {
      rango: { desde: rango.desde, hasta: rango.hasta, diasSemana: [...rango.dias].sort() },
      precioCents: precio,
      cerrado: rango.cerrado,
    };
    setPreviaOrigen('rango');
    setRangoEnviado(enviado);
    void api
      .post<PreviaLote>('/api/admin/tarifas/lote/rango/preview', {
        ratePlanId: Number(tarifas.planId),
        roomTypeId: Number(tarifas.tipoId),
        ...enviado,
      })
      .then(setPrevia)
      .catch((err) => setError(err instanceof Error ? err.message : 'No se pudo previsualizar'));
  }

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

  const dias = diasDelMes(mes);
  const filasCalendario = calendario ?? [];
  const calendarioPorHabitacion = new Map(
    filasCalendario.map((fila) => [fila.id, new Map(fila.noches.map((noche) => [noche.fecha, noche]))]),
  );
  const nochesCalendario = filasCalendario.flatMap((fila) => fila.noches);
  const hayTipos = (tipos?.length ?? 0) > 0;
  const PASOS = [
    { n: 1, titulo: 'Tipo de habitación', deshabilitado: false },
    { n: 2, titulo: 'Habitaciones', deshabilitado: !hayTipos },
    { n: 3, titulo: 'Servicios del tipo', deshabilitado: !hayTipos },
    { n: 4, titulo: 'Plan tarifario', deshabilitado: false },
  ];
  const puedeAvanzar = paso < 4 && !PASOS[paso]?.deshabilitado;
  const ocupadasMes = nochesCalendario.filter((noche) => noche.estado === 'OCUPADA').length;
  const bloqueadasMes = nochesCalendario.filter((noche) => noche.estado === 'BLOQUEADA').length;
  const noVendiblesMes = nochesCalendario.filter(
    (noche) => noche.estado === 'MANTENIMIENTO' || noche.estado === 'FUERA_DE_SERVICIO',
  ).length;

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

        <h2 id="titulo-calendario" className="t-xl">Calendario de ocupación</h2>
        <p className="seccion__intro mb-0">
          Una casilla por habitación y noche, con el código de la reserva cuando está ocupada.
          El día de salida cuenta como libre porque la habitación vuelve a estar disponible.
        </p>
        {filasCalendario.length > 0 ? (
          <>
            <p className="campo__ayuda">
              En {mes}: <strong>{ocupadasMes} noches ocupadas</strong>, {bloqueadasMes} bloqueadas y{' '}
              {noVendiblesMes} fuera de servicio.
            </p>
            <ul className="ocupacion__leyenda" aria-label="Leyenda del calendario">
              {Object.entries(ESTADOS_CALENDARIO).map(([estado, datos]) => (
                <li key={estado}>
                  <span
                    aria-hidden="true"
                    className={`ocupacion__marca ocupacion__marca--${claveEstadoCalendario(estado)}`}
                  >
                    {datos.simbolo}
                  </span>{' '}
                  {datos.etiqueta}
                </li>
              ))}
            </ul>
            <div className="ocupacion" role="region" aria-labelledby="titulo-calendario" tabIndex={0}>
              <table className="ocupacion__tabla">
                <caption>
                  Ocupación nocturna por habitación durante {mes}. Desplázate horizontalmente para ver
                  todo el mes.
                </caption>
                <thead>
                  <tr>
                    <th scope="col" className="ocupacion__habitacion">Habitación</th>
                    {dias.map((dia) => (
                      <th key={dia} scope="col" className={`ocupacion__dia${esFinDeSemana(dia) ? ' ocupacion__dia--fin-semana' : ''}`}>
                        <span aria-hidden="true" className="ocupacion__numero">
                          {new Date(`${dia}T12:00:00`).getDate()}
                        </span>
                        <span aria-hidden="true" className="ocupacion__semana">
                          {letraDiaSemana(dia)}
                        </span>
                        <span className="visually-hidden">{fechaCorta(dia)}</span>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {filasCalendario.map((fila) => (
                    <tr key={fila.id}>
                      <th scope="row" className="ocupacion__habitacion">
                        <span className="cifra">{fila.codigo}</span>
                      </th>
                      {dias.map((dia) => {
                        const noche = calendarioPorHabitacion.get(fila.id)?.get(dia);
                        if (!noche) {
                          return (
                            <td key={dia} className="ocupacion__celda">
                              <span className="visually-hidden">
                                {fila.codigo}, {fechaCorta(dia)}, sin dato
                              </span>
                              <span aria-hidden="true">—</span>
                            </td>
                          );
                        }
                        const estado = estadoCalendario(noche.estado);
                        const texto = `${fila.codigo}, ${fechaCorta(noche.fecha)}, ${estado.etiqueta}${
                          noche.codigoReserva ? `, reserva ${noche.codigoReserva}` : ''
                        }`;
                        return (
                          <td
                            key={dia}
                            className={`ocupacion__celda ocupacion__celda--${claveEstadoCalendario(noche.estado)}${esFinDeSemana(dia) ? ' ocupacion__celda--fin-semana' : ''}`}
                            title={texto}
                          >
                            <span
                              aria-hidden="true"
                              className={`ocupacion__marca ocupacion__marca--${claveEstadoCalendario(noche.estado)}`}
                            >
                              {estado.simbolo}
                            </span>
                            <span className="visually-hidden">{texto}</span>
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        ) : (
          <p className="campo__ayuda">Todavía no hay datos del calendario para este mes.</p>
        )}

        <h2 className="t-xl mt-e6">Habitaciones ({habitaciones?.length ?? 0})</h2>
        {habitaciones && habitaciones.length > 0 ? (
          <div className="tabla-envoltura">
            <table className="tabla">
              <caption>Habitaciones registradas</caption>
              <thead>
                <tr>
                    <th scope="col">Código</th>
                    <th scope="col">Nombre</th>
                    <th scope="col">Estado</th>
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
          <p className="campo__ayuda">
            No hay habitaciones registradas todavía.{' '}
            <a href="#alta-guiada">Créalas con el alta guiada de abajo</a>.
          </p>
        )}

        <section id="alta-guiada" className="asistente mt-e6" aria-label="Alta guiada del inventario">
          <h2 className="t-xl">Alta del hotel, paso a paso</h2>
          <p className="seccion__intro mb-0">
            Primero el tipo, luego sus habitaciones y servicios, y al final el plan con el que se
            venden. Sin plan y sin precios, la web no muestra habitaciones.
          </p>
          <ol className="asistente__progreso">
            {PASOS.map((p) => (
              <li key={p.n}>
                <button
                  type="button"
                  className={`asistente__paso${p.n === paso ? ' asistente__paso--actual' : ''}`}
                  aria-current={p.n === paso ? 'step' : undefined}
                  disabled={p.deshabilitado}
                  title={p.deshabilitado ? 'Primero crea un tipo de habitación' : undefined}
                  onClick={() => setPaso(p.n)}
                >
                  <span aria-hidden="true" className="asistente__numero">{p.n}</span> {p.titulo}
                </button>
              </li>
            ))}
          </ol>
          <h3 ref={tituloPaso} tabIndex={-1} className="t-lg">
            Paso {paso}: {PASOS[paso - 1]?.titulo}
          </h3>
          {paso === 1 ? (
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
          ) : null}
          {paso === 2 ? (
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
          ) : null}
          {paso === 3 ? (
          <form
            className="tarjeta pila"
            onSubmit={(e) => {
              e.preventDefault();
              if (!tipoServicios) return;
              void accion(() =>
                api.put(`/api/admin/tipos/${tipoServicios}/amenidades`, { ids: marcados }),
              );
            }}
          >
            <h2 className="t-lg mb-0">Servicios del tipo</h2>
            <p className="campo__ayuda sin-margen">
              Marca lo que tiene cada tipo: la web lo muestra en cada oferta.
            </p>
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="serv-tipo">Tipo</label>
              <select
                id="serv-tipo"
                value={tipoServicios}
                onChange={(e) => void elegirTipoServicios(e.target.value)}
              >
                <option value="">Selecciona un tipo</option>
                {tipos?.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.nombre}
                  </option>
                ))}
              </select>
            </div>
            {catalogoServicios === null ? <p className="cargando">Cargando servicios…</p> : null}
            {catalogoServicios !== null && tipoServicios ? (
              <fieldset className="grupo-chequeos">
                <legend className="campo__etiqueta">Servicios incluidos</legend>
                {catalogoServicios.map((a) => (
                  <label key={a.id} className="chequeo">
                    <input
                      type="checkbox"
                      checked={marcados.includes(a.id)}
                      onChange={() => alternarServicio(a.id)}
                    />
                    {a.nombre}
                  </label>
                ))}
              </fieldset>
            ) : null}
            <button className="boton boton--primario" type="submit" disabled={!tipoServicios}>
              Guardar servicios
            </button>
          </form>
          ) : null}
          {paso === 4 ? (
          <form
            className="tarjeta pila"
            onSubmit={(e) => {
              e.preventDefault();
              void accion(() =>
                api.post('/api/admin/planes', {
                  codigo: nuevoPlan.codigo,
                  nombre: nuevoPlan.nombre,
                  moneda: nuevoPlan.moneda,
                  descuentoPct: nuevoPlan.descuentoPct === '' ? null : Number(nuevoPlan.descuentoPct),
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
              <select
                id="plan-moneda"
                required
                value={nuevoPlan.moneda}
                onChange={(e) => setNuevoPlan({ ...nuevoPlan, moneda: e.target.value })}
              >
                {MONEDAS.map((m) => (
                  <option key={m.codigo} value={m.codigo}>
                    {m.codigo} — {m.nombre}
                  </option>
                ))}
              </select>
            </div>
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="plan-descuento">Descuento % (opcional)</label>
              <input
                id="plan-descuento"
                type="number"
                min={0}
                max={100}
                placeholder="0"
                value={nuevoPlan.descuentoPct}
                onChange={(e) => setNuevoPlan({ ...nuevoPlan, descuentoPct: e.target.value })}
              />
            </div>
            <button className="boton boton--primario" type="submit">Crear plan</button>
          </form>
          ) : null}
          <div className="asistente__navegacion">
            <button
              className="boton boton--secundario"
              type="button"
              disabled={paso === 1}
              onClick={() => setPaso(paso - 1)}
            >
              Anterior
            </button>
            {paso < 4 ? (
              <button
                className="boton boton--primario"
                type="button"
                disabled={!puedeAvanzar}
                title={!puedeAvanzar ? 'Primero crea un tipo de habitación' : undefined}
                onClick={() => setPaso(paso + 1)}
              >
                Siguiente
              </button>
            ) : (
              <span className="campo__ayuda sin-margen">
                Con el plan creado, fija sus precios por noche más abajo.
              </span>
            )}
          </div>
        </section>

        <div className="rejilla mt-e6">
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

          {planes && planes.length > 0 ? (
            <div className="tarjeta pila">
              <h2 className="t-lg mb-0">Planes y descuentos</h2>
              <p className="campo__ayuda sin-margen">
                El descuento se aplica al totalizar. Las reservas ya guardadas no cambian.
              </p>
              <ul className="lista-marcada">
                {planes.map((plan) => (
                  <FilaPlan key={plan.id} plan={plan} alGuardar={cargar} />
                ))}
              </ul>
            </div>
          ) : null}
        </div>

        <div className="tarjeta pila mt-e6">
          <h2 className="t-lg mb-0">Bloqueos vigentes</h2>
          {!bloqueos || bloqueos.length === 0 ? (
            <p className="campo__ayuda sin-margen">Sin bloqueos vigentes: todo el inventario está a la venta.</p>
          ) : (
            <div className="tabla-envoltura">
              <table className="tabla">
                <caption>Bloqueos que aún cubren noches futuras</caption>
                <thead>
                  <tr>
                    <th scope="col">Habitación</th>
                    <th scope="col">Desde</th>
                    <th scope="col">Hasta</th>
                    <th scope="col">Motivo</th>
                    <th scope="col">Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {bloqueos.map((b) => (
                    <tr key={b.id}>
                      <td data-label="Habitación">{b.habitacion}</td>
                      <td className="cifra" data-label="Desde">{fechaCorta(b.desde)}</td>
                      <td className="cifra" data-label="Hasta">{fechaCorta(b.hasta)}</td>
                      <td data-label="Motivo">{b.motivo || '—'}</td>
                      <td data-label="Acciones">
                        <button
                          className="boton boton--secundario boton--chico"
                          type="button"
                          onClick={() => void accion(() => api.post(`/api/admin/bloqueos/${b.id}/retirar`))}
                          aria-label={`Retirar el bloqueo de ${b.habitacion} (${b.motivo || 'sin motivo'})`}
                        >
                          Retirar
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
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
          <>
          <form
            className="tarjeta pila"
            onSubmit={(e) => {
              e.preventDefault();
              const cambios = cambiosDelMes();
              if (cambios.length === 0) {
                setError('No hay cambios que guardar.');
                return;
              }
              setError(null);
              setPrevia(null);
              setPreviaOrigen('mes');
              setRangoEnviado(null);
              void api
                .post<PreviaLote>('/api/admin/tarifas/lote/preview', {
                  ratePlanId: Number(tarifas.planId),
                  roomTypeId: Number(tarifas.tipoId),
                  noches: cambios,
                })
                .then(setPrevia)
                .catch((err) => setError(err instanceof Error ? err.message : 'No se pudo previsualizar'));
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
              Revisar cambios
            </button>
          </form>

          <details className="tarjeta pila mt-e6">
            <summary className="referencia__resumen">Tarifar por rango y días de la semana</summary>
            <p className="campo__ayuda sin-margen">
              Para subir los fines de semana de una vez, por ejemplo. Pasa por la misma
              previa de abajo antes de guardar.
            </p>
            <form className="pila" onSubmit={revisarRango}>
              <div className="campos">
                <div className="campo">
                  <label className="campo__etiqueta" htmlFor="rango-desde">Desde</label>
                  <input
                    id="rango-desde"
                    type="date"
                    required
                    className="cifra"
                    value={rango.desde}
                    onChange={(e) => setRango({ ...rango, desde: e.target.value })}
                  />
                </div>
                <div className="campo">
                  <label className="campo__etiqueta" htmlFor="rango-hasta">Hasta</label>
                  <input
                    id="rango-hasta"
                    type="date"
                    required
                    className="cifra"
                    value={rango.hasta}
                    onChange={(e) => setRango({ ...rango, hasta: e.target.value })}
                  />
                </div>
              </div>
              <fieldset className="grupo-chequeos">
                <legend className="campo__etiqueta">Días de la semana</legend>
                {DIAS_SEMANA.map((d) => (
                  <label key={d.n} className="chequeo">
                    <input
                      type="checkbox"
                      checked={rango.dias.includes(d.n)}
                      onChange={() => setRango({
                        ...rango,
                        dias: rango.dias.includes(d.n)
                          ? rango.dias.filter((m) => m !== d.n)
                          : [...rango.dias, d.n],
                      })}
                    />
                    {d.nombre}
                  </label>
                ))}
              </fieldset>
              <div className="campos">
                <div className="campo">
                  <label className="campo__etiqueta" htmlFor="rango-precio">Precio</label>
                  <input
                    id="rango-precio"
                    className="cifra"
                    type="number"
                    min={0}
                    step="0.01"
                    inputMode="decimal"
                    required
                    placeholder="Sin fijar"
                    value={rango.precio}
                    onChange={(e) => setRango({ ...rango, precio: e.target.value })}
                  />
                </div>
                <div className="campo">
                  <label className="chequeo" htmlFor="rango-cerrada">
                    <input
                      id="rango-cerrada"
                      type="checkbox"
                      checked={rango.cerrado}
                      onChange={(e) => setRango({ ...rango, cerrado: e.target.checked })}
                    />
                    No vendible
                  </label>
                </div>
              </div>
              <button className="boton boton--secundario no-estirar" type="submit">
                Revisar rango
              </button>
            </form>
          </details>

          {previa ? (
            <div className="tarjeta pila mt-e6" role="region" aria-label="Previa del lote">
              <h3 className="t-lg mb-0">Revisa antes de guardar</h3>
              <p className="campo__ayuda sin-margen">
                {previa.filas.length} noches. Si una falla, no se guarda ninguna.
              </p>
              <div className="tabla-envoltura">
                <table className="tabla">
                  <caption>Cambios que se guardarán al confirmar</caption>
                  <thead>
                    <tr>
                      <th scope="col">Noche</th>
                      <th scope="col">Antes</th>
                      <th scope="col">Después</th>
                      <th scope="col">Estado</th>
                    </tr>
                  </thead>
                  <tbody>
                    {previa.filas.map((f) => {
                      const guardada = noches.get(f.fecha);
                      return (
                        <tr key={f.fecha} className={f.valida ? undefined : 'previa-fila--invalida'}>
                          <th scope="row" className="cifra">{fechaCorta(f.fecha)}</th>
                          <td className="cifra">{guardada ? String(guardada.precioCents / 100) : '—'}</td>
                          <td className="cifra">
                            {f.valida && f.precioCents !== undefined ? String(f.precioCents / 100) : '—'}
                          </td>
                          <td>
                            {f.valida ? (
                              <Etiqueta tono={f.nueva ? 'info' : 'exito'}>
                                {f.nueva ? 'Nueva' : 'Cambia'}
                              </Etiqueta>
                            ) : (
                              <span className="campo__error">{f.motivo}</span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              <div className="asistente__navegacion">
                <button className="boton boton--secundario" type="button" onClick={() => setPrevia(null)}>
                  Corregir
                </button>
                <button
                  className="boton boton--primario no-estirar"
                  type="button"
                  disabled={!previa.lista}
                  title={!previa.lista ? 'Corrige las filas con error: si una falla, no se guarda ninguna' : undefined}
                  onClick={() => confirmarPrevia()}
                >
                  Confirmar y guardar {previa.filas.length} noches
                </button>
              </div>
            </div>
          ) : null}
          </>
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

/** Descuento de un plan: número, guardar y recargar la lista que manda el padre. */
function FilaPlan({ plan, alGuardar }: { plan: Plan; alGuardar: () => Promise<void> }) {
  const [pct, setPct] = useState(String(plan.descuentoPct ?? 0));
  const [aviso, setAviso] = useState<string | null>(null);

  async function guardar() {
    setAviso(null);
    try {
      await api.post(`/api/admin/planes/${plan.id}/descuento`, { descuentoPct: Number(pct) });
      await alGuardar();
    } catch (e) {
      setAviso(e instanceof Error ? e.message : 'No se pudo guardar el descuento');
    }
  }

  return (
    <li>
      <strong>{plan.codigo}</strong> · {plan.nombre} ({plan.moneda})
      {plan.descuentoPct > 0 ? <> · −{plan.descuentoPct} %</> : null}
      <div className="campos">
        <div className="campo">
          <label className="campo__etiqueta" htmlFor={`descuento-${plan.id}`}>
            Descuento %
          </label>
          <input
            id={`descuento-${plan.id}`}
            type="number"
            min={0}
            max={100}
            value={pct}
            onChange={(e) => setPct(e.target.value)}
          />
        </div>
        <button className="boton boton--secundario boton--chico" type="button" onClick={() => void guardar()}>
          Guardar descuento
        </button>
      </div>
      {aviso ? <p className="campo__error">{aviso}</p> : null}
    </li>
  );
}