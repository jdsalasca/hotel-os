import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api, nuevaClaveIdempotencia } from '../api/cliente';
import { fechaCorta, hoyIso, mananaIso, monto } from '../api/formato';
import { Aviso, HuecoImagen, MensajeError } from '../componentes/Estado';

type Oferta = {
  habitacion: { id: number; codigo: string; nombre: string };
  tipo: { id: number; codigo: string; nombre: string; capacidadMax: number };
  totalCents: number;
  moneda: string;
  noches: number;
  plan: { id: number; codigo: string; nombre: string };
  descuentoPct: number;
  totalSinDescuentoCents: number;
};

type RespuestaDisponibilidad = {
  llegada: string;
  salida: string;
  huespedes: number;
  ofertas: Oferta[];
  error?: string;
};

type DiaCalendario = {
  fecha: string;
  disponibles: number;
  /** Mínimo por moneda, ordenado: nunca se mezclan monedas en un mínimo. */
  precios: { moneda: string; desdeCents: number }[];
};

type RespuestaCalendario = {
  mes: string;
  huespedes: number;
  dias: DiaCalendario[];
  error?: string;
};

type BusquedaRestaurada = { llegada: string; salida: string; huespedes: number };

function leerBusquedaRestaurada(estado: unknown): BusquedaRestaurada | null {
  if (!estado || typeof estado !== 'object') return null;
  const valor = (estado as { restaurarBusqueda?: unknown }).restaurarBusqueda;
  if (!valor || typeof valor !== 'object') return null;
  const busqueda = valor as Partial<BusquedaRestaurada>;
  if (
    typeof busqueda.llegada !== 'string' ||
    typeof busqueda.salida !== 'string' ||
    !/^\d{4}-\d{2}-\d{2}$/.test(busqueda.llegada) ||
    !/^\d{4}-\d{2}-\d{2}$/.test(busqueda.salida) ||
    busqueda.salida <= busqueda.llegada ||
    !Number.isInteger(busqueda.huespedes) ||
    (busqueda.huespedes ?? 0) < 1 ||
    (busqueda.huespedes ?? 0) > 20
  ) {
    return null;
  }
  return {
    llegada: busqueda.llegada,
    salida: busqueda.salida,
    huespedes: busqueda.huespedes!,
  };
}

type DetalleOferta = {
  habitacion: { id: number; codigo: string; nombre: string };
  tipo: { id: number; codigo: string; nombre: string; capacidadMax: number };
  plan: { codigo: string; nombre: string };
  noches: { fecha: string; precioCents: number }[];
  totalCents: number;
  moneda: string;
  descuentoPct: number;
  totalSinDescuentoCents: number;
};

/** Suma días a un ISO YYYY-MM-DD sin pelear con la zona horaria. */
function sumarIso(iso: string, dias: number): string {
  const d = new Date(iso + 'T12:00:00');
  d.setDate(d.getDate() + dias);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Desplaza un mes YYYY-MM. Sin límite: el pasado se muestra apagado y sin clic. */
function desplazarMes(mesIso: string, delta: number): string {
  const [anio = 0, mes = 1] = mesIso.split('-').map(Number);
  const d = new Date(anio, mes - 1 + delta, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

/** Paso 1 del flujo público: fechas, huéspedes y habitaciones disponibles con su precio. */
export function PaginaInicio({ nombreHotel = 'Hotel Eridu' }: { nombreHotel?: string }) {
  const navegar = useNavigate();
  const ubicacion = useLocation();
  const busquedaRestaurada = leerBusquedaRestaurada(ubicacion.state);
  const [llegada, setLlegada] = useState(busquedaRestaurada?.llegada ?? '');
  const [salida, setSalida] = useState(busquedaRestaurada?.salida ?? '');
  const [huespedes, setHuespedes] = useState(busquedaRestaurada?.huespedes ?? 2);
  const [ofertas, setOfertas] = useState<Oferta[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(false);
  const [buscado, setBuscado] = useState(false);
  const resultadosRef = useRef<HTMLDivElement | null>(null);
  const [detalles, setDetalles] = useState<Record<string, DetalleOferta>>({});
  const [servicios, setServicios] = useState<Record<number, string[]>>({});
  const [mapa, setMapa] = useState<{
    hotel: { ubicado: boolean; latitud?: number; longitud?: number };
    lugares: { id: number; nombre: string; descripcion: string; latitud: number; longitud: number }[];
  } | null>(null);

  /** Mapa y distancias: una sola lectura; si el hotel no se ubicó, la sección no se muestra. */
  useEffect(() => {
    void api
      .get<{
        hotel: { ubicado: boolean; latitud?: number; longitud?: number };
        lugares: { id: number; nombre: string; descripcion: string; latitud: number; longitud: number }[];
      }>('/api/lugares')
      .then((datos) => setMapa(datos))
      .catch(() => setMapa(null));
  }, []);
  const [detalleCargando, setDetalleCargando] = useState<Record<string, boolean>>({});
  const [detalleAbierto, setDetalleAbierto] = useState<Record<string, boolean>>({});
  /** La búsqueda que produjo las ofertas en pantalla: elegir usa este snapshot, no el
   * formulario que el huésped pudo editar después sin volver a buscar. */
  const [busqueda, setBusqueda] = useState<{ llegada: string; salida: string; huespedes: number } | null>(null);
  const peticionBusqueda = useRef(0);
  const restauracionPendiente = useRef(busquedaRestaurada);
  const [mes, setMes] = useState(() => hoyIso().slice(0, 7));
  const [dias, setDias] = useState<DiaCalendario[]>([]);
  const [errorCal, setErrorCal] = useState<string | null>(null);
  const [cargandoCal, setCargandoCal] = useState(false);
  const peticionCal = useRef(0);
  /**
   * Sin nada que vender el calendario mentiría un mes de Llenos: una sola lectura al
   * abrir dice si el hotel publica. null es "aún no se sabe" y pinta como siempre;
   * si falla, tampoco se bloquea nada.
   */
  const [venta, setVenta] = useState<boolean | null>(null);
  const [ventaComprobada, setVentaComprobada] = useState(false);

  useEffect(() => {
    void api
      .get<{ a_la_venta: boolean }>('/api/hotel/venta')
      .then((r) => {
        const disponible = r.a_la_venta !== false;
        setVenta(disponible);
        setCargandoCal(disponible);
        if (!disponible) {
          peticionBusqueda.current++;
          setOfertas(null);
          setBusqueda(null);
          setBuscado(false);
          setError(null);
          setCargando(false);
        }
        setVentaComprobada(true);
      })
      .catch(() => {
        setVenta(null);
        setCargandoCal(true);
        setVentaComprobada(true);
      });
  }, []);

  /** Contacto público para el estado vacío: sin habitaciones, que al menos escriban. */
  const [contacto, setContacto] = useState<{ email: string; telefono: string } | null>(null);

  useEffect(() => {
    void api
      .get<Record<string, string>>('/api/hotel')
      .then((h) => {
        const email = (h.contacto_email ?? '').trim();
        const telefono = (h.contacto_telefono ?? '').trim();
        setContacto(email || telefono ? { email, telefono } : null);
      })
      .catch(() => setContacto(null));
  }, []);

  // El calendario es una sola petición por mes: si el huésped cambia de mes o de huéspedes antes
  // de que vuelva, la respuesta vieja se ignora en vez de pintar otro mes.
  useEffect(() => {
    if (!ventaComprobada || venta === false) {
      peticionCal.current++;
      setDias([]);
      setErrorCal(null);
      setCargandoCal(false);
      return;
    }
    if (!huespedes || huespedes < 1) {
      setDias([]);
      return;
    }
    const id = ++peticionCal.current;
    setCargandoCal(true);
    api
      .get<RespuestaCalendario>(`/api/disponibilidad/calendario?mes=${mes}&huespedes=${huespedes}`)
      .then((r) => {
        if (peticionCal.current !== id) return;
        setDias(r.dias ?? []);
        setErrorCal(r.error ?? null);
      })
      .catch(() => {
        if (peticionCal.current !== id) return;
        setDias([]);
        setErrorCal('No se pudo cargar el calendario de este mes');
      })
      .finally(() => {
        if (peticionCal.current === id) setCargandoCal(false);
      });
  }, [mes, huespedes, venta, ventaComprobada]);

  async function buscarCon(llegadaIso: string, salidaIso: string, huespedesN: number) {
    if (venta === false) return;
    // Como el calendario: si el huésped lanza otra búsqueda antes de que vuelva esta,
    // la tardía se ignora en vez de pintar el viaje anterior.
    const id = ++peticionBusqueda.current;
    setError(null);
    setCargando(true);
    try {
      const consulta = new URLSearchParams({ llegada: llegadaIso, salida: salidaIso, huespedes: String(huespedesN) });
      const r = await api.get<RespuestaDisponibilidad>(`/api/disponibilidad?${consulta}`);
      if (peticionBusqueda.current !== id) return;
      if (r.error) setError(r.error);
      setOfertas(r.ofertas ?? []);
      setBusqueda({ llegada: llegadaIso, salida: salidaIso, huespedes: huespedesN });
      // Resultados nuevos, detalles viejos fuera: lo abierto era de otra búsqueda.
      setDetalles({});
      setDetalleAbierto({});
      setDetalleCargando({});
      setBuscado(true);
    } catch (e) {
      if (peticionBusqueda.current !== id) return;
      setError(e instanceof Error ? e.message : 'No se pudo consultar la disponibilidad');
    } finally {
      if (peticionBusqueda.current === id) setCargando(false);
    }
  }

  useEffect(() => {
    if (!ventaComprobada) return;
    const anterior = restauracionPendiente.current;
    if (!anterior) return;
    restauracionPendiente.current = null;
    if (venta === false) {
      navegar('/', { replace: true, state: null });
      return;
    }
    void buscarCon(anterior.llegada, anterior.salida, anterior.huespedes);
    // Consumir el estado evita repetir la búsqueda si se refresca la página.
    navegar('/', { replace: true, state: null });
  }, [navegar, venta, ventaComprobada]);

  async function buscar(evento: React.FormEvent) {
    evento.preventDefault();
    if (venta === false) return;
    if (fechasInvalidas) {
      document.getElementById(llegadaInvalida ? 'llegada' : 'salida')?.focus();
      return;
    }
    if (huespedesInvalidos) {
      document.getElementById('huespedes')?.focus();
      return;
    }
    await buscarCon(llegada, salida, huespedes);
  }

  useEffect(() => {
    if (cargando || (!buscado && !error)) return;
    const resultados = resultadosRef.current;
    if (!resultados) return;

    const reducirMovimiento = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    resultados.focus({ preventScroll: true });
    if (typeof resultados.scrollIntoView === 'function') {
      resultados.scrollIntoView({ behavior: reducirMovimiento ? 'auto' : 'smooth', block: 'start' });
    }
  }, [buscado, cargando, error, ofertas]);

  function elegir(oferta: Oferta) {    // Clave de idempotencia por intento: el respaldo cubre entornos sin secure context (HTTP local).
    const clave = nuevaClaveIdempotencia();
    // Snapshot de la búsqueda que produjo la oferta: si el huésped editó el formulario
    // después sin volver a buscar, la tarjeta vieja no se mezcla con los valores nuevos.
    const base = busqueda ?? { llegada, salida, huespedes };
    sessionStorage.setItem('reserva-en-curso', JSON.stringify({ ...oferta, ...base, clave }));
    navegar('/reserva');
  }

  /** Servicios de los tipos en pantalla: una sola lectura para todas las ofertas. */
  useEffect(() => {
    const ids = [...new Set((ofertas ?? []).map((o) => o.tipo.id))];
    if (ids.length === 0) return;
    void api
      .get<{ porTipo: Record<string, { nombre: string }[]> }>(
        `/api/amenidades/por-tipo?ids=${ids.join(',')}`,
      )
      .then((datos) => {
        const mapa: Record<number, string[]> = {};
        for (const [tipoId, lista] of Object.entries(datos.porTipo ?? {})) {
          mapa[Number(tipoId)] = (lista ?? []).map((a) => a.nombre);
        }
        setServicios(mapa);
      })
      .catch(() => setServicios({}));
  }, [ofertas]);

/** Distancia en línea recta, redondeada al metro. El enlace abre la ruta real. */
function distanciaMetros(
  desdeLat: number,
  desdeLng: number,
  hastaLat: number,
  hastaLng: number,
): number {
  const rad = (g: number) => (g * Math.PI) / 180;
  const dLat = rad(hastaLat - desdeLat);
  const dLng = rad(hastaLng - desdeLng);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(rad(desdeLat)) * Math.cos(rad(hastaLat)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
  return Math.round(2 * 6371000 * Math.asin(Math.min(1, Math.sqrt(a))));
}

function formatearDistancia(metros: number): string {
  if (metros < 1000) return `${metros} m`;
  return `${(metros / 1000).toLocaleString('es-CO', { maximumFractionDigits: 1 })} km`;
}

  /** Parámetros de la búsqueda en pantalla: el detalle y el resumen pertenecen a lo
   * buscado, no al formulario que se pudo editar después sin volver a buscar. */
  const baseBusqueda = busqueda ?? { llegada, salida, huespedes };
  /** Clave del detalle de una oferta: habitación + parámetros + plan. Dos planes de la
   * misma habitación son dos ofertas distintas y abren por separado. */
  const claveDetalle = (oferta: Oferta) =>
    `${oferta.habitacion.id}|${baseBusqueda.llegada}|${baseBusqueda.salida}|${baseBusqueda.huespedes}|${oferta.plan.id}`;

  /** Desglose noche por noche de una oferta: el plan que la respalda y cada importe. */
  async function verDetalle(oferta: Oferta) {
    const id = oferta.habitacion.id;
    const clave = claveDetalle(oferta);
    if (detalleAbierto[clave]) {
      setDetalleAbierto({ ...detalleAbierto, [clave]: false });
      return;
    }
    setDetalleAbierto({ ...detalleAbierto, [clave]: true });
    if (detalles[clave]) return;
    setDetalleCargando({ ...detalleCargando, [clave]: true });
    try {
      const consulta = new URLSearchParams({
        roomId: String(id),
        llegada: baseBusqueda.llegada,
        salida: baseBusqueda.salida,
        huespedes: String(baseBusqueda.huespedes),
      });
      const r = await api.get<DetalleOferta>(`/api/disponibilidad/detalle?${consulta}`);
      setDetalles((previos) => ({ ...previos, [clave]: r }));
    } catch {
      setDetalleAbierto((previos) => ({ ...previos, [clave]: false }));
    } finally {
      setDetalleCargando((previos) => ({ ...previos, [clave]: false }));
    }
  }

  /** Elegir un día del calendario busca esa noche directamente: lo elegido queda en el formulario. */
  function elegirDia(dia: DiaCalendario) {
    const siguiente = sumarIso(dia.fecha, 1);
    setLlegada(dia.fecha);
    setSalida(siguiente);
    void buscarCon(dia.fecha, siguiente, huespedes);
  }

  const llegadaInvalida = Boolean(llegada && llegada < mananaIso());
  const salidaInvalida = Boolean(salida && (!llegada || salida <= llegada || salida < mananaIso()));
  const fechasInvalidas = llegadaInvalida || salidaInvalida;
  const huespedesInvalidos = !Number.isInteger(huespedes) || huespedes < 1 || huespedes > 20;
  const resultadosDesactualizados = Boolean(
    buscado &&
    busqueda &&
    (busqueda.llegada !== llegada || busqueda.salida !== salida || busqueda.huespedes !== huespedes),
  );
  const hoy = hoyIso();
  const [anioCal = 0, mesCal = 1] = mes.split('-').map(Number);
  const huecoInicial = (new Date(anioCal, mesCal - 1, 1).getDay() + 6) % 7;
  const nombreMes = new Date(`${mes}-01T12:00:00`).toLocaleDateString('es-CO', { month: 'long', year: 'numeric' });

  return (
    <main id="contenido">
      <section className="portada">
        <img
          className="portada__fondo"
          src="/img/portada-villa-de-leyva.jpg"
          alt="Panorámica de Villa de Leyva, Boyacá"
          fetchPriority="high"
        />
        <div className="portada__interna">
          <p className="portada__ojal">Sáchica · Villa de Leyva · Boyacá</p>
          <h1 className="portada__titulo">{nombreHotel}: reserva directa, sin intermediarios</h1>
          <p className="portada__texto">
            {venta === false
              ? 'El hotel no está recibiendo nuevas reservas en línea por ahora. Si ya tienes una, puedes gestionarla con tu código o tu cuenta de Google.'
              : 'Consulta la disponibilidad, reserva tus fechas y gestiona tu reserva con tu código o tu cuenta de Google. Sin comisiones ni apps de terceros: lo que ves es lo que el hotel configuró.'}
          </p>
          <p className="portada__acciones">
            {venta === false ? (
              <>
                <span className="portada__estado" role="status">Reservas en línea pausadas</span>
                <Link className="boton boton--secundario boton--claro" to="/mis-reservas">
                  Gestionar o consultar una reserva
                </Link>
              </>
            ) : (
              <>
                <a className="boton boton--primario" href="#titulo-buscar">
                  Ver disponibilidad
                </a>{' '}
                <Link className="boton boton--secundario boton--claro" to="/mis-reservas">
                  Gestionar una reserva
                </Link>
              </>
            )}
          </p>
        </div>
      </section>

      {venta === false ? (
        <div className="centrado">
          <section className="seccion reserva-pausada" aria-labelledby="titulo-buscar">
            <h2 id="titulo-buscar" className="seccion__titulo">
              Reservas en línea pausadas
            </h2>
            <Aviso tono="aviso" titulo="No estamos tomando nuevas reservas en este momento">
              <p>Vuelve a consultar más adelante; la búsqueda y el calendario no están disponibles mientras la venta esté pausada.</p>
              {contacto && (contacto.email || contacto.telefono) ? (
                <p className="reserva-pausada__contacto">
                  {contacto.email ? (
                    <>
                      Escríbenos a{' '}
                      <a href={`mailto:${contacto.email}`}>{contacto.email}</a>
                    </>
                  ) : null}
                  {contacto.email && contacto.telefono ? ' o ' : null}
                  {contacto.telefono ? (
                    <>
                      llámanos al{' '}
                      <a href={`tel:${contacto.telefono.replace(/[\s-]/g, '')}`}>
                        {contacto.telefono}
                      </a>
                    </>
                  ) : null}
                  .
                </p>
              ) : null}
              <p>Si ya tienes una reserva, puedes entrar con Google o consultarla con su código y correo.</p>
            </Aviso>
          </section>
        </div>
      ) : null}

      <div className="centrado">
        <section className="seccion" aria-labelledby="titulo-descubre">
          <h2 id="titulo-descubre" className="seccion__titulo">
            A minutos de lo mejor de Boyacá
          </h2>
          <div className="collage" role="list" aria-label="Fotos de Sáchica y Villa de Leyva">
            <figure className="collage__item collage__item--ancha" role="listitem">
              <img
                className="collage__foto"
                src="/img/casa-terracota.jpg"
                alt="Casa de Terracota, Villa de Leyva"
                loading="lazy"
              />
              <figcaption className="collage__leyenda">Casa de Terracota</figcaption>
            </figure>
            <figure className="collage__item collage__item--alta" role="listitem">
              <img
                className="collage__foto"
                src="/img/balcones.jpg"
                alt="Balcones coloniales, Villa de Leyva"
                loading="lazy"
              />
              <figcaption className="collage__leyenda">Balcones coloniales</figcaption>
            </figure>
            <figure className="collage__item" role="listitem">
              <img
                className="collage__foto"
                src="/img/desierto.jpg"
                alt="Atardecer en el desierto de Villa de Leyva"
                loading="lazy"
              />
              <figcaption className="collage__leyenda">Desierto de la Candelaria</figcaption>
            </figure>
            <figure className="collage__item collage__item--alta" role="listitem">
              <img
                className="collage__foto"
                src="/img/calles.jpg"
                alt="Calles empedradas, Villa de Leyva"
                loading="lazy"
              />
              <figcaption className="collage__leyenda">Calles empedradas</figcaption>
            </figure>
            <figure className="collage__item" role="listitem">
              <img
                className="collage__foto"
                src="/img/cruz-sachica.jpg"
                alt="Cruz atrial de Sáchica, Boyacá"
                loading="lazy"
              />
              <figcaption className="collage__leyenda">Sáchica colonial</figcaption>
            </figure>
            <figure className="collage__item collage__item--ancha" role="listitem">
              <img
                className="collage__foto"
                src="/img/cascada.jpg"
                alt="Cascada La Periquera, Villa de Leyva"
                loading="lazy"
              />
              <figcaption className="collage__leyenda">Cascada La Periquera</figcaption>
            </figure>
          </div>
          <p className="campo__ayuda">
            Fotografías:{' '}
            <a href="https://commons.wikimedia.org/wiki/File:Casa_de_Terracota.jpg">Terracota ©
            amontero89</a>
            {', '}
            <a href="https://commons.wikimedia.org/wiki/File:Balcones_de_Villa_de_Leyva.jpg">
            Balcones © Adrián Salazar Garelli</a>
            {', '}
            <a href="https://commons.wikimedia.org/wiki/File:Atardecer_desierto_Villa_de_Leyva.JPG">
            Desierto © Petruss</a>
            {', '}
            <a href="https://commons.wikimedia.org/wiki/File:Calles_Villa_Leyva.JPG">Calles ©
            MatthRomero</a>
            {', '}
            <a href="https://commons.wikimedia.org/wiki/File:Cruz_Atrial_de_S%C3%A1chica.jpg">
            Sáchica</a>
            {', '}
            <a href="https://commons.wikimedia.org/wiki/File:Cascada_La_Periquera_-_panoramio.jpg">
            Cascada © diego_cue</a>
            {' '}vía Wikimedia Commons (CC BY-SA).
          </p>
        </section>
      </div>

      {venta === false ? null : (
      <div className="centrado">
        <section className="seccion" aria-labelledby="titulo-pasos">
          <h2 id="titulo-pasos" className="seccion__titulo">
            Reservar es así de simple
          </h2>
          <ol className="pasos">
            <li className="paso">
              <span className="paso__numero" aria-hidden="true">1</span>
              <div>
                <strong>Elige tus fechas</strong>
                <span> Dinos tu llegada, tu salida y tus huéspedes: te mostramos precio final, sin letra pequeña.</span>
              </div>
            </li>
            <li className="paso">
              <span className="paso__numero" aria-hidden="true">2</span>
              <div>
                <strong>Confirma con tus datos</strong>
                <span> Nombre y correo: con eso nace tu reserva y tu código de consulta.</span>
              </div>
            </li>
            <li className="paso">
              <span className="paso__numero" aria-hidden="true">3</span>
              <div>
                <strong>Gestiona a tu manera</strong>
                <span> Consulta o cancela con tu código, o entra con Google y ve tus reservas.</span>
              </div>
            </li>
          </ol>
        </section>
      </div>
      )}

      {mapa !== null &&
      (mapa.hotel.ubicado || mapa.lugares.length > 0) ? (
        <div className="centrado">
          <section className="seccion" aria-labelledby="titulo-mapa">
            <h2 id="titulo-mapa" className="seccion__titulo">
              Encuéntranos y explora
            </h2>
            {mapa.hotel.ubicado &&
            mapa.hotel.latitud !== undefined &&
            mapa.hotel.longitud !== undefined ? (
              <div className="mapa">
                <p className="mapa__nota">
                  La distancia es en línea recta; «Cómo llegar» abre la ruta real.
                </p>
                <iframe
                  className="mapa__marco"
                  title={`Mapa de ${nombreHotel}`}
                  loading="lazy"
                  src={`https://www.openstreetmap.org/export/embed.html?bbox=${
                    mapa.hotel.longitud - 0.03
                  }%2C${mapa.hotel.latitud - 0.02}%2C${mapa.hotel.longitud + 0.03}%2C${
                    mapa.hotel.latitud + 0.02
                  }&layer=mapnik&marker=${mapa.hotel.latitud}%2C${mapa.hotel.longitud}`}
                />
                <ul className="mapa__lugares">
                  {mapa.lugares.map((lugar) => (
                    <li key={lugar.id} className="mapa__lugar">
                      <div>
                        <strong>{lugar.nombre}</strong>
                        {lugar.descripcion ? <span> — {lugar.descripcion}</span> : null}
                        <span className="mapa__distancia">
                          {' '}
                          a {formatearDistancia(
                            distanciaMetros(
                              mapa.hotel.latitud ?? 0,
                              mapa.hotel.longitud ?? 0,
                              lugar.latitud,
                              lugar.longitud,
                            ),
                          )}
                        </span>
                      </div>
                      <a
                        className="boton boton--fantasma boton--chico"
                        href={`https://www.google.com/maps/dir/?api=1&destination=${lugar.latitud},${lugar.longitud}`}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        Cómo llegar
                      </a>
                    </li>
                  ))}
                  {mapa.lugares.length === 0 ? (
                    <li className="campo__ayuda">
                      El hotel ya marcó su punto; los sitios cercanos aparecen aquí cuando los
                      agregue.
                    </li>
                  ) : null}
                </ul>
              </div>
            ) : (
              <ul className="mapa__lugares">
                {mapa.lugares.map((lugar) => (
                  <li key={lugar.id} className="mapa__lugar">
                    <div>
                      <strong>{lugar.nombre}</strong>
                      {lugar.descripcion ? <span> — {lugar.descripcion}</span> : null}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      ) : null}

      {venta === false ? null : (
      <div className="centrado">
        <section className="seccion" aria-labelledby="titulo-buscar">
          <h2 id="titulo-buscar" className="seccion__titulo">
            ¿Cuándo quieres venir?
          </h2>

          <form onSubmit={buscar} noValidate>
            <div className="campos campos--tres">
              <div className="campo">
                <label className="campo__etiqueta" htmlFor="llegada">Llegada</label>
                <input
                  id="llegada"
                  type="date"
                  required
                  min={mananaIso()}
                  value={llegada}
                  aria-invalid={llegadaInvalida ? 'true' : undefined}
                  aria-describedby={llegadaInvalida ? 'error-fechas' : undefined}
                  onChange={(e) => setLlegada(e.target.value)}
                />
              </div>
              <div className="campo">
                <label className="campo__etiqueta" htmlFor="salida">Salida</label>
                <input
                  id="salida"
                  type="date"
                  required
                  min={llegada || mananaIso()}
                  value={salida}
                  aria-invalid={salidaInvalida ? 'true' : undefined}
                  aria-describedby={salidaInvalida ? 'error-fechas' : undefined}
                  onChange={(e) => setSalida(e.target.value)}
                />
              </div>
              <div className="campo">
                <label className="campo__etiqueta" htmlFor="huespedes">Huéspedes</label>
                <input
                  id="huespedes"
                  type="number"
                  min={1}
                  max={20}
                  step={1}
                  value={huespedes}
                  aria-invalid={huespedesInvalidos ? 'true' : undefined}
                  aria-describedby={huespedesInvalidos ? 'error-huespedes' : undefined}
                  onChange={(e) => setHuespedes(Number(e.target.value))}
                />
              </div>
            </div>

            {fechasInvalidas ? (
              <p className="campo__error" id="error-fechas">
                {llegadaInvalida
                  ? 'La llegada debe ser una fecha futura.'
                  : 'La salida debe ser posterior a la llegada.'}
              </p>
            ) : null}
            {huespedesInvalidos ? (
              <p className="campo__error" id="error-huespedes">
                Elige entre 1 y 20 huéspedes.
              </p>
            ) : null}

            <div className="mt-e4">
              <button
                className="boton boton--primario"
                type="submit"
                disabled={cargando || !llegada || !salida}
              >
                {cargando ? 'Buscando…' : 'Buscar disponibilidad'}
              </button>
            </div>
          </form>
        </section>

        <section className="seccion" aria-labelledby="titulo-calendario">
          <h2 id="titulo-calendario" className="seccion__titulo">
            Calendario de disponibilidad
          </h2>
          <p className="seccion__intro">
            Qué noches tienen habitaciones libres para {huespedes}{' '}
            {huespedes === 1 ? 'huésped' : 'huéspedes'} y desde qué precio. Toca un día libre para
            buscar esa noche.
          </p>

          <div className="calendario-mes__navegacion">
            <button
              type="button"
              className="boton boton--secundario boton--chico"
              onClick={() => setMes(desplazarMes(mes, -1))}
              aria-label="Mes anterior"
            >
              ‹ Anterior
            </button>
            <h3 className="calendario-mes__titulo">{nombreMes}</h3>
            <button
              type="button"
              className="boton boton--secundario boton--chico"
              onClick={() => setMes(desplazarMes(mes, 1))}
              aria-label="Mes siguiente"
            >
              Siguiente ›
            </button>
          </div>

          {errorCal ? <MensajeError texto={errorCal} /> : null}
          {cargandoCal || !ventaComprobada ? (
            <p className="cargando" role="status">Comprobando disponibilidad…</p>
          ) : null}

          {ventaComprobada && !cargandoCal && !errorCal ? (
            <div className="calendario-mes__rejilla" role="group" aria-label={`Disponibilidad de ${nombreMes}`}>
              {['L', 'M', 'X', 'J', 'V', 'S', 'D'].map((d) => (
                <span key={d} className="calendario-mes__semana" aria-hidden="true">{d}</span>
              ))}
              {Array.from({ length: huecoInicial }).map((_, i) => (
                <span key={`hueco-${i}`} className="calendario-mes__hueco" aria-hidden="true" />
              ))}
              {dias.map((dia) => {
                const numero = Number(dia.fecha.slice(8, 10));
                const pasado = dia.fecha < hoy;
                const libre = !pasado && dia.disponibles > 0;
                const desde = dia.precios.map((p) => monto(p.desdeCents, p.moneda)).join(' · ');
                const etiqueta = pasado
                  ? `${numero}: fecha pasada`
                  : dia.disponibles > 0
                    ? `${fechaCorta(dia.fecha)}: ${dia.disponibles} ${dia.disponibles === 1 ? 'habitación libre' : 'habitaciones libres'}${desde ? ` desde ${desde}` : ''}`
                    : `${fechaCorta(dia.fecha)}: sin habitaciones`;
                return (
                  <button
                    key={dia.fecha}
                    type="button"
                    className={`calendario-mes__dia${libre ? ' calendario-mes__dia--libre' : ''}${pasado ? ' calendario-mes__dia--pasado' : ''}`}
                    aria-label={etiqueta}
                    disabled={!libre}
                    onClick={() => elegirDia(dia)}
                  >
                    <span className="calendario-mes__numero" aria-hidden="true">{numero}</span>
                    <span className="calendario-mes__detalle" aria-hidden="true">
                      {libre ? `${dia.disponibles} · ${desde}` : pasado ? '—' : 'Lleno'}
                    </span>
                  </button>
                );
              })}
            </div>
          ) : null}
          <p className="campo__ayuda">El precio del día es el de una noche; el total del viaje lo confirma la búsqueda.</p>
        </section>

        {cargando ? <p className="cargando" role="status">Consultando disponibilidad…</p> : null}

        {(error || (buscado && !cargando && ofertas)) ? (
          <div ref={resultadosRef} className="resultados-busqueda" tabIndex={-1}>
            {error ? <MensajeError texto={error} /> : null}
            {resultadosDesactualizados ? (
              <Aviso tono="aviso" titulo="Actualiza la búsqueda">
                <p>Cambiaste los criterios de búsqueda. Vuelve a buscar antes de elegir una habitación.</p>
              </Aviso>
            ) : null}
            {buscado && !cargando && ofertas?.length === 0 ? (
              <div className="vacio" role="status" aria-live="polite" aria-atomic="true">
                <p className="vacio__titulo">No hay habitaciones disponibles para esas fechas</p>
                <p>Prueba otras fechas. Si el hotel aún no ha publicado tarifas para este periodo, tampoco hay precios que mostrar.</p>
              </div>
            ) : null}
            {buscado && !cargando && ofertas && ofertas.length > 0 ? (
              <section className="seccion" aria-labelledby="titulo-habitaciones">
                <h2 id="titulo-habitaciones" className="seccion__titulo">
                  Habitaciones disponibles
                </h2>
                <p className="seccion__intro" role="status" aria-live="polite" aria-atomic="true">
                  Encontramos {ofertas.length}{' '}
                  {ofertas.length === 1 ? 'opción disponible' : 'opciones disponibles'}.
                </p>
                <p className="seccion__intro">
                  Del {fechaCorta(baseBusqueda.llegada)} al {fechaCorta(baseBusqueda.salida)} para{' '}
                  {baseBusqueda.huespedes}{' '}
                  {baseBusqueda.huespedes === 1 ? 'huésped' : 'huéspedes'}.
                </p>
                <div className="rejilla">
                  {ofertas.map((oferta) => {
                    const id = oferta.habitacion.id;
                    const clave = claveDetalle(oferta);
                    const abierto = !!detalleAbierto[clave];
                    const detalle = detalles[clave];

                    return (
                      <article className="tarjeta pila" key={`${id}|${oferta.plan.id}`}>
                        <HuecoImagen texto="Fotografía de la habitación" />
                        <h3>{oferta.tipo.nombre}</h3>
                        {oferta.descuentoPct > 0 ? (
                          <p className="sin-margen">
                            <span className="etiqueta etiqueta--exito">−{oferta.descuentoPct} %</span>
                          </p>
                        ) : null}
                        <p className="campo__ayuda">
                          {oferta.habitacion.nombre || oferta.habitacion.codigo} · Hasta{' '}
                          {oferta.tipo.capacidadMax} huéspedes · Plan {oferta.plan.nombre}
                        </p>
                        {(servicios[oferta.tipo.id] ?? []).length > 0 ? (
                          <ul className="servicios" aria-label={`Servicios de ${oferta.tipo.nombre}`}>
                            {(servicios[oferta.tipo.id] ?? []).map((servicio) => (
                              <li key={servicio} className="servicios__item">{servicio}</li>
                            ))}
                          </ul>
                        ) : null}
                        <p className="precio">
                          {monto(oferta.totalCents, oferta.moneda)}
                          {oferta.descuentoPct > 0 ? (
                            <span className="precio__detalle">
                              antes {monto(oferta.totalSinDescuentoCents, oferta.moneda)} · total por{' '}
                              {oferta.noches} {oferta.noches === 1 ? 'noche' : 'noches'}
                            </span>
                          ) : (
                            <span className="precio__detalle">
                              total por {oferta.noches} {oferta.noches === 1 ? 'noche' : 'noches'}
                            </span>
                          )}
                        </p>
                        <button
                          className="boton boton--fantasma boton--chico"
                          type="button"
                          onClick={() => void verDetalle(oferta)}
                          aria-expanded={abierto}
                          aria-label={`Ver el precio noche por noche de ${oferta.tipo.nombre}`}
                        >
                          {abierto ? 'Ocultar detalle' : 'Ver detalle por noche'}
                        </button>
                        {detalleCargando[clave] ? (
                          <p className="cargando" role="status">Cargando el desglose…</p>
                        ) : null}
                        {abierto && detalle ? (
                          <div className="desglose">
                            <p className="campo__ayuda sin-margen">
                              Plan {detalle.plan.nombre}
                              {detalle.descuentoPct > 0 ? (
                                <> · −{detalle.descuentoPct} % (antes {monto(detalle.totalSinDescuentoCents, detalle.moneda)})</>
                              ) : null}
                            </p>
                            <dl className="desglose__noches">
                              {detalle.noches.map((noche) => (
                                <div key={noche.fecha} className="desglose__noche">
                                  <dt>{fechaCorta(noche.fecha)}</dt>
                                  <dd className="cifra">{monto(noche.precioCents, detalle.moneda)}</dd>
                                </div>
                              ))}
                            </dl>
                          </div>
                        ) : null}
                        <button
                          className="boton boton--primario boton--bloque"
                          onClick={() => elegir(oferta)}
                          aria-label={`Elegir ${oferta.tipo.nombre}`}
                          disabled={resultadosDesactualizados}
                        >
                          Elegir esta habitación
                        </button>
                      </article>
                    );
                  })}
                </div>
              </section>
            ) : null}
          </div>
        ) : null}
      </div>
      )}
    </main>
  );
}
