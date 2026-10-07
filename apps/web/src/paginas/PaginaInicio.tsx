import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, nuevaClaveIdempotencia } from '../api/cliente';
import { fechaCorta, hoyIso, mananaIso, monto } from '../api/formato';
import { HuecoImagen, MensajeError } from '../componentes/Estado';

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
  desdeCents?: number;
  moneda?: string;
};

type RespuestaCalendario = {
  mes: string;
  huespedes: number;
  dias: DiaCalendario[];
  error?: string;
};

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
  const [llegada, setLlegada] = useState('');
  const [salida, setSalida] = useState('');
  const [huespedes, setHuespedes] = useState(2);
  const [ofertas, setOfertas] = useState<Oferta[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(false);
  const [buscado, setBuscado] = useState(false);
  const [detalles, setDetalles] = useState<Record<number, DetalleOferta>>({});
  const [servicios, setServicios] = useState<Record<number, string[]>>({});
  const [detalleCargando, setDetalleCargando] = useState<Record<number, boolean>>({});
  const [detalleAbierto, setDetalleAbierto] = useState<Record<number, boolean>>({});
  const [mes, setMes] = useState(() => hoyIso().slice(0, 7));
  const [dias, setDias] = useState<DiaCalendario[]>([]);
  const [errorCal, setErrorCal] = useState<string | null>(null);
  const [cargandoCal, setCargandoCal] = useState(false);
  const peticionCal = useRef(0);

  // El calendario es una sola petición por mes: si el huésped cambia de mes o de huéspedes antes
  // de que vuelva, la respuesta vieja se ignora en vez de pintar otro mes.
  useEffect(() => {
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
  }, [mes, huespedes]);

  async function buscarCon(llegadaIso: string, salidaIso: string, huespedesN: number) {
    setError(null);
    setCargando(true);
    try {
      const consulta = new URLSearchParams({ llegada: llegadaIso, salida: salidaIso, huespedes: String(huespedesN) });
      const r = await api.get<RespuestaDisponibilidad>(`/api/disponibilidad?${consulta}`);
      if (r.error) setError(r.error);
      setOfertas(r.ofertas ?? []);
      setBuscado(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo consultar la disponibilidad');
    } finally {
      setCargando(false);
    }
  }

  async function buscar(evento: React.FormEvent) {
    evento.preventDefault();
    await buscarCon(llegada, salida, huespedes);
  }

  function elegir(oferta: Oferta) {    // Clave de idempotencia por intento: el respaldo cubre entornos sin secure context (HTTP local).
    const clave = nuevaClaveIdempotencia();
    sessionStorage.setItem('reserva-en-curso', JSON.stringify({ ...oferta, llegada, salida, huespedes, clave }));
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

  /** Desglose noche por noche de una oferta: el plan que la respalda y cada importe. */
  async function verDetalle(oferta: Oferta) {
    const id = oferta.habitacion.id;
    if (detalleAbierto[id]) {
      setDetalleAbierto({ ...detalleAbierto, [id]: false });
      return;
    }
    setDetalleAbierto({ ...detalleAbierto, [id]: true });
    if (detalles[id]) return;
    setDetalleCargando({ ...detalleCargando, [id]: true });
    try {
      const consulta = new URLSearchParams({
        roomId: String(id), llegada, salida, huespedes: String(huespedes),
      });
      const r = await api.get<DetalleOferta>(`/api/disponibilidad/detalle?${consulta}`);
      setDetalles({ ...detalles, [id]: r });
    } catch {
      setDetalleAbierto({ ...detalleAbierto, [id]: false });
    } finally {
      setDetalleCargando({ ...detalleCargando, [id]: false });
    }
  }

  /** Elegir un día del calendario busca esa noche directamente: lo elegido queda en el formulario. */
  function elegirDia(dia: DiaCalendario) {
    const siguiente = sumarIso(dia.fecha, 1);
    setLlegada(dia.fecha);
    setSalida(siguiente);
    void buscarCon(dia.fecha, siguiente, huespedes);
    document.getElementById('titulo-habitaciones')?.scrollIntoView({ block: 'start' });
  }

  const fechasInvalidas = llegada && salida && salida <= llegada;
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
            Consulta la disponibilidad, reserva tus fechas y gestiona tu reserva con tu código o
            tu cuenta de Google. Sin comisiones ni apps de terceros: lo que ves es lo que el
            hotel configuró.
          </p>
          <p className="portada__acciones">
            <a className="boton boton--primario" href="#titulo-buscar">
              Ver disponibilidad
            </a>{' '}
            <a className="boton boton--secundario boton--claro" href="/consulta">
              Consultar mi reserva
            </a>
          </p>
        </div>
      </section>

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
                <span> Dinos llegada, salida y huéspedes: te mostramos precio final, sin letra pequeña.</span>
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
                  aria-invalid={fechasInvalidas ? 'true' : undefined}
                  aria-describedby={fechasInvalidas ? 'error-fechas' : undefined}
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
                  aria-invalid={fechasInvalidas ? 'true' : undefined}
                  aria-describedby={fechasInvalidas ? 'error-fechas' : undefined}
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
                  value={huespedes}
                  onChange={(e) => setHuespedes(Number(e.target.value))}
                />
              </div>
            </div>

            {fechasInvalidas ? (
              <p className="campo__error" id="error-fechas">
                La salida debe ser posterior a la llegada.
              </p>
            ) : null}

            <div className="mt-e4">
              <button className="boton boton--primario" type="submit" disabled={cargando || !llegada || !salida}>
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
          {cargandoCal ? <p className="cargando" role="status">Cargando el mes…</p> : null}

          {!cargandoCal && !errorCal ? (
            <div className="calendario-mes__rejilla" role="list" aria-label={`Disponibilidad de ${nombreMes}`}>
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
                const etiqueta = pasado
                  ? `${numero}: fecha pasada`
                  : dia.disponibles > 0
                    ? `${fechaCorta(dia.fecha)}: ${dia.disponibles} ${dia.disponibles === 1 ? 'habitación libre' : 'habitaciones libres'} desde ${monto(dia.desdeCents ?? 0, dia.moneda ?? 'COP')}`
                    : `${fechaCorta(dia.fecha)}: sin habitaciones`;
                return libre ? (
                  <button
                    key={dia.fecha}
                    type="button"
                    role="listitem"
                    className="calendario-mes__dia calendario-mes__dia--libre"
                    aria-label={etiqueta}
                    onClick={() => elegirDia(dia)}
                  >
                    <span className="calendario-mes__numero" aria-hidden="true">{numero}</span>
                    <span className="calendario-mes__detalle" aria-hidden="true">
                      {dia.disponibles} · {monto(dia.desdeCents ?? 0, dia.moneda ?? 'COP')}
                    </span>
                  </button>
                ) : (
                  <span
                    key={dia.fecha}
                    role="listitem"
                    aria-label={etiqueta}
                    className={`calendario-mes__dia${pasado ? ' calendario-mes__dia--pasado' : ''}`}
                  >
                    <span className="calendario-mes__numero" aria-hidden="true">{numero}</span>
                    <span className="calendario-mes__detalle" aria-hidden="true">
                      {pasado ? '—' : 'Lleno'}
                    </span>
                  </span>
                );
              })}
            </div>
          ) : null}
          <p className="campo__ayuda">El precio del día es el de una noche; el total del viaje lo confirma la búsqueda.</p>
        </section>

        {error ? <MensajeError texto={error} /> : null}

        {cargando ? <p className="cargando" role="status">Consultando disponibilidad…</p> : null}

        {buscado && !cargando && ofertas && ofertas.length === 0 ? (
          <div className="vacio">
            <p className="vacio__titulo">No hay habitaciones disponibles para esas fechas</p>
            <p>Prueba otras fechas. Si el hotel aún no ha publicado tarifas para este periodo, tampoco hay precios que mostrar.</p>
          </div>
        ) : null}

        {buscado && !cargando && ofertas && ofertas.length > 0 ? (
          <section className="seccion" aria-labelledby="titulo-habitaciones">
            <h2 id="titulo-habitaciones" className="seccion__titulo">
              Habitaciones disponibles
            </h2>
            <p className="seccion__intro">
              Del {fechaCorta(llegada)} al {fechaCorta(salida)} para {huespedes}{' '}
              {huespedes === 1 ? 'huésped' : 'huéspedes'}.
            </p>
              <div className="rejilla">
                {ofertas.map((oferta) => {
                  const id = oferta.habitacion.id;
                  const abierto = !!detalleAbierto[id];
                  const detalle = detalles[id];
                  return (
                  <article className="tarjeta pila" key={id}>
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
                      {(servicios[oferta.tipo.id] ?? []).map((s) => (
                        <li key={s} className="servicios__item">{s}</li>
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
                  {detalleCargando[id] ? (
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
    </main>
  );
}