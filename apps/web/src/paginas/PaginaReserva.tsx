import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ErrorApi, api } from '../api/cliente';
import { useSesionHuesped } from '../api/useSesionHuesped';
import { useSesion } from '../api/useSesion';
import { BotonWhatsApp } from '../componentes/BotonWhatsApp';
import { CodigoReserva } from '../componentes/CodigoReserva';
import { fechaCorta, monto } from '../api/formato';
import { Aviso, HuecoImagen, MensajeError } from '../componentes/Estado';

type EnCurso = {
  habitacion: { id: number; codigo: string; nombre: string };
  tipo: { nombre: string };
  totalCents: number;
  moneda: string;
  noches: number;
  plan?: { id: number; codigo: string; nombre: string };
  descuentoPct?: number;
  totalSinDescuentoCents?: number;
  llegada: string;
  salida: string;
  huespedes: number;
  clave: string;
};

type RespuestaReserva = {
  codigo: string;
  email: string;
  nombre: string;
  llegada: string;
  salida: string;
  huespedes: number;
  estado: string;
  origen: string;
  mensaje: string;
  noches?: number;
  habitacion?: string;
  recuperada?: boolean;
};

type LocalizadorComprobante = { codigo: string; email: string };

const CLAVE_COMPROBANTE = 'reserva-comprobante';

/** Pasos 2 a 5 del flujo público: datos mínimos, envío y resultado con su estado real. */
export function PaginaReserva() {
  // Se lee UNA vez al montar. Si se relejera en cada render, al borrar la selección tras el
  // éxito volvería a ser null y la pantalla de confirmación no se llegaría a pintar.
  const [enCurso] = useState<EnCurso | null>(() => leerEnCurso());
  // Una selección vigente tiene prioridad sobre el comprobante anterior.
  const [localizador, setLocalizador] = useState<LocalizadorComprobante | null>(() =>
    enCurso ? null : leerLocalizadorComprobante());
  const [recuperando, setRecuperando] = useState(() => !enCurso && Boolean(localizador));
  const [errorRecuperacion, setErrorRecuperacion] = useState<string | null>(null);
  const sesion = useSesionHuesped();
  const panel = useSesion();

  // La confirmación también le sirve al personal que reserva para sí mismo: sin esta
  // comprobación, con sesión del panel vería "guarda el código" aunque la reserva ya es suya.
  useEffect(() => {
    void panel.comprobar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const conCuenta = sesion.haySesion === true || panel.haySesion === true;
  const [email, setEmail] = useState('');
  const [nombre, setNombre] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [erroresCampo, setErroresCampo] = useState<{ email?: string; nombre?: string }>({});
  const [reserva, setReserva] = useState<RespuestaReserva | null>(null);
  // Si la tarifa se movió entre la búsqueda y la confirmación, aquí queda el importe vigente
  // para que el huésped lo confirme de nuevo con conocimiento, no en silencio.
  const [precioNuevo, setPrecioNuevo] = useState<{ totalCents: number; moneda: string; ratePlanId: number } | null>(null);
  const [planNuevo, setPlanNuevo] = useState<string | null>(null);
  /**
   * Condiciones que el hotel fijó (horas y cancelación). El backend ya las guarda y las
   * publica en /api/hotel, pero el huésped no las veía hasta llegar a /terminos, después
   * de decidir. Se piden aquí, antes de confirmar. Si no hay nada configurado no se
   * inventa ninguna: el bloque no aparece. Un fallo temporal sí se comunica con una opción
   * de reintento, sin impedir que el huésped continúe.
   */
  const [condiciones, setCondiciones] = useState<{
    entrada?: string; salida?: string; cancelacion?: string;
  } | null>(null);
  const [errorCondiciones, setErrorCondiciones] = useState(false);
  const [cargandoCondiciones, setCargandoCondiciones] = useState(true);
  const [intentoCondiciones, setIntentoCondiciones] = useState(0);

  useEffect(() => {
    let vigente = true;
    setCargandoCondiciones(true);
    void api.get<Record<string, string>>('/api/hotel')
      .then((datos) => {
        if (!vigente) return;
        const entrada = datos.hora_entrada?.trim();
        const salida = datos.hora_salida?.trim();
        const cancelacion = datos.politica_cancelacion?.trim();
        setCondiciones(!entrada && !salida && !cancelacion ? null : {
          entrada: entrada || undefined,
          salida: salida || undefined,
          cancelacion: cancelacion || undefined,
        });
        setErrorCondiciones(false);
      })
      .catch(() => {
        if (!vigente) return;
        setCondiciones(null);
        setErrorCondiciones(true);
      })
      .finally(() => {
        if (vigente) setCargandoCondiciones(false);
      });
    return () => {
      vigente = false;
    };
  }, [intentoCondiciones]);

  async function recuperarComprobante(actual: LocalizadorComprobante) {
    setRecuperando(true);
    setErrorRecuperacion(null);
    try {
      const datos = await api.post<{
        reserva: Pick<RespuestaReserva,
          'codigo' | 'email' | 'nombre' | 'llegada' | 'salida' | 'huespedes' | 'estado' | 'origen'> & { noches: number };
        habitacion: { codigo: string; nombre: string; tipo: string } | null;
      }>('/api/reservas/consulta', {
        codigo: actual.codigo,
        email: actual.email,
      });
      const habitacion = datos.habitacion
        ? [datos.habitacion.codigo, datos.habitacion.tipo || datos.habitacion.nombre].filter(Boolean).join(' · ')
        : undefined;
      setReserva({
        ...datos.reserva,
        mensaje: 'Este es el estado vigente de tu reserva.',
        noches: datos.reserva.noches,
        habitacion,
        recuperada: true,
      });
    } catch (e) {
      if (e instanceof ErrorApi && e.estado === 404) {
        sessionStorage.removeItem(CLAVE_COMPROBANTE);
        setLocalizador(null);
      } else {
        setErrorRecuperacion('No pudimos recuperar tu reserva ahora. Inténtalo de nuevo.');
      }
    } finally {
      setRecuperando(false);
    }
  }

  useEffect(() => {
    if (!enCurso && localizador) void recuperarComprobante(localizador);
    // El localizador se lee una vez al entrar; los reintentos son explícitos.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (sesion.haySesion !== true) return;
    setEmail((actual) => actual || sesion.email);
    setNombre((actual) => actual || sesion.nombre);
  }, [sesion.haySesion, sesion.email, sesion.nombre]);

  if (reserva) return <VistaReserva reserva={reserva} eleccion={enCurso} conCuenta={conCuenta} />;

  if (!enCurso) {
    if (localizador) {
      return (
        <main id="contenido" className="centrado">
          <section className="seccion">
            <h1 className="seccion__titulo">Recuperar comprobante</h1>
            {errorRecuperacion ? <MensajeError texto={errorRecuperacion} /> : null}
            {recuperando ? <p role="status" aria-live="polite">Consultando el estado actual…</p> : null}
            <button
              className="boton boton--primario"
              type="button"
              onClick={() => void recuperarComprobante(localizador)}
              disabled={recuperando}
            >
              {recuperando ? 'Consultando…' : 'Reintentar'}
            </button>
          </section>
        </main>
      );
    }
    return (
      <main id="contenido" className="centrado">
        <section className="seccion">
          <div className="vacio">
            <p className="vacio__titulo">No hay una reserva en curso</p>
            <p>Busca disponibilidad primero para elegir habitación y fechas.</p>
            <Link className="boton boton--primario" to="/">
              Buscar disponibilidad
            </Link>
          </div>
        </section>
      </main>
    );
  }

  // Tras el guard, se fija el dato para que TypeScript lo conserve dentro de `enviar`.
  const eleccion = enCurso;
  // Lo que el huésped acepta pagar: lo visto en la búsqueda, o lo vigente tras un cambio que
  // ya se le mostró. Nunca se envía un importe ni un plan que no haya visto.
  const esperado = precioNuevo
    ? { totalCents: precioNuevo.totalCents, moneda: precioNuevo.moneda, ratePlanId: precioNuevo.ratePlanId }
    : {
        totalCents: eleccion.totalCents,
        moneda: eleccion.moneda,
        ratePlanId: eleccion.plan?.id ?? null,
      };

  async function enviar(evento: React.FormEvent) {
    evento.preventDefault();
    const emailLimpio = email.trim();
    const nombreLimpio = nombre.trim();
    const campoEmail = document.getElementById('email') as HTMLInputElement | null;
    const correoInvalido =
      emailLimpio.length === 0 ||
      (campoEmail ? !campoEmail.validity.valid : !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailLimpio));
    const nuevosErrores: { email?: string; nombre?: string } = {};
    if (correoInvalido) {
      nuevosErrores.email =
        emailLimpio.length === 0 ? 'Escribe tu correo electrónico.' : 'Escribe un correo electrónico válido.';
    }
    if (nombreLimpio.length === 0) nuevosErrores.nombre = 'Escribe tu nombre.';
    setErroresCampo(nuevosErrores);
    if (nuevosErrores.email || nuevosErrores.nombre) {
      setError(null);
      document.getElementById(nuevosErrores.email ? 'email' : 'nombre')?.focus();
      return;
    }

    setError(null);
    setEnviando(true);
    try {
      const r = await api.post<RespuestaReserva>('/api/reservas', {
        email: emailLimpio,
        nombre: nombreLimpio,
        llegada: eleccion.llegada,
        salida: eleccion.salida,
        huespedes: eleccion.huespedes,
        roomId: eleccion.habitacion.id,
        idempotencia: eleccion.clave,
        totalEsperadoCents: esperado.totalCents,
        monedaEsperada: esperado.moneda,
        ratePlanIdEsperado: esperado.ratePlanId,
      });
      setReserva(r);
      // Guarda solo los dos datos necesarios para volver a consultar el comprobante.
      try {
        sessionStorage.setItem(CLAVE_COMPROBANTE, JSON.stringify({
          codigo: r.codigo,
          email: emailLimpio,
        }));
      } catch {
        // La confirmación actual sigue disponible aunque el navegador bloquee el almacenamiento.
      }
      sessionStorage.removeItem('reserva-en-curso');
    } catch (e) {
      const cambio = cambioDePrecio(e);
      if (cambio) {
        setPrecioNuevo(cambio);
        // El resumen visible también se actualiza: el plan puede haber cambiado de nombre, y el
        // huésped reconfirma lo que ve, no un número oculto.
        try {
          const consulta = new URLSearchParams({
            roomId: String(eleccion.habitacion.id),
            llegada: eleccion.llegada,
            salida: eleccion.salida,
            huespedes: String(eleccion.huespedes),
          });
          const detalle = await api.get<{ plan: { nombre: string } }>(
            `/api/disponibilidad/detalle?${consulta}`);
          setPlanNuevo(detalle.plan.nombre);
        } catch {
          setPlanNuevo(null);
        }
      } else {
        setPrecioNuevo(null);
        setError(e instanceof Error ? e.message : 'No se pudo registrar la reserva');
      }
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main id="contenido" className="centrado">
      <div className="rejilla rejilla--dos pt-e6">
        <aside aria-labelledby="titulo-resumen">
          <h2 id="titulo-resumen" className="seccion__titulo t-xl">
            Tu selección
          </h2>
          <div className="tarjeta pila">
            <HuecoImagen alto="bajo" texto="Fotografía de la habitación" />
            <h3 className="sin-margen">{eleccion.tipo.nombre}</h3>
            <p className="campo__ayuda sin-margen">
              {fechaCorta(eleccion.llegada)} → {fechaCorta(eleccion.salida)}
            </p>
            <p className="campo__ayuda sin-margen">
              {eleccion.huespedes} {eleccion.huespedes === 1 ? 'huésped' : 'huéspedes'} ·{' '}
              {eleccion.noches} {eleccion.noches === 1 ? 'noche' : 'noches'}
            </p>
            {eleccion.plan || planNuevo ? (
              <p className="campo__ayuda sin-margen">Plan {planNuevo ?? eleccion.plan?.nombre}</p>
            ) : null}
            {(eleccion.descuentoPct ?? 0) > 0 ? (
              <p className="sin-margen">
                <span className="etiqueta etiqueta--exito">−{eleccion.descuentoPct} %</span>{' '}
                <span className="campo__ayuda">
                  antes {monto(eleccion.totalSinDescuentoCents ?? eleccion.totalCents, eleccion.moneda)}
                </span>
              </p>
            ) : null}
            <p className="precio">
              {monto(esperado.totalCents, esperado.moneda)}
              <span className="precio__detalle">total del periodo</span>
            </p>
            {condiciones ? (
              <section aria-label="Condiciones del hotel" className="condiciones">
                <h4 className="condiciones__titulo">Antes de confirmar</h4>
                <dl className="condiciones__lista">
                  {condiciones.entrada || condiciones.salida ? (
                    <div className="condiciones__fila">
                      <dt>Check-in y check-out</dt>
                      <dd>
                        {condiciones.entrada ? <>desde las {condiciones.entrada}</> : null}
                        {condiciones.entrada && condiciones.salida ? ' · ' : null}
                        {condiciones.salida ? <>hasta las {condiciones.salida}</> : null}
                      </dd>
                    </div>
                  ) : null}
                  {condiciones.cancelacion ? (
                    <div className="condiciones__fila">
                      <dt>Cancelación</dt>
                      <dd>{condiciones.cancelacion}</dd>
                    </div>
                  ) : null}
                </dl>
              </section>
            ) : null}
            {errorCondiciones ? (
              <Aviso tono="error" titulo="No pudimos cargar las condiciones del hotel">
                <p>Las condiciones no están disponibles temporalmente. Puedes volver a intentarlo antes de confirmar.</p>
                <button
                  className="boton boton--secundario boton--chico"
                  type="button"
                  disabled={cargandoCondiciones}
                  onClick={() => {
                    setCargandoCondiciones(true);
                    setIntentoCondiciones((anterior) => anterior + 1);
                  }}
                >
                  {cargandoCondiciones ? 'Reintentando…' : 'Reintentar condiciones'}
                </button>
              </Aviso>
            ) : null}
            <Link
              className="boton boton--fantasma boton--chico"
              to="/"
              state={{
                restaurarBusqueda: {
                  llegada: eleccion.llegada,
                  salida: eleccion.salida,
                  huespedes: eleccion.huespedes,
                },
              }}
            >
              Cambiar fechas
            </Link>
          </div>
        </aside>
        <section className="seccion">
          <h1 className="seccion__titulo">Confirma tu reserva</h1>
          <p className="seccion__intro">
            Solo pedimos lo necesario para atenderte. No pedimos datos de tarjeta: el hotel aún no
            ha configurado un medio de pago.
          </p>

          <form onSubmit={enviar} noValidate>
            <div className="pila">
              <div className="campo">
                <label className="campo__etiqueta" htmlFor="email">Correo electrónico *</label>
                <input
                  id="email"
                  type="email"
                  required
                  autoComplete="email"
                  value={email}
                  onChange={(e) => {
                    setEmail(e.target.value);
                    if (erroresCampo.email) setErroresCampo((actual) => ({ ...actual, email: undefined }));
                  }}
                  aria-invalid={erroresCampo.email ? 'true' : undefined}
                  aria-describedby={erroresCampo.email ? 'ayuda-email error-email' : 'ayuda-email'}
                />
                <p className="campo__ayuda" id="ayuda-email">
                  Lo usaremos para confirmarte y para que puedas consultar tu reserva.
                </p>
                {erroresCampo.email ? <p className="campo__error" id="error-email">{erroresCampo.email}</p> : null}
              </div>

              <div className="campo">
                <label className="campo__etiqueta" htmlFor="nombre">Nombre *</label>
                <input
                  id="nombre"
                  type="text"
                  required
                  autoComplete="name"
                  value={nombre}
                  onChange={(e) => {
                    setNombre(e.target.value);
                    if (erroresCampo.nombre) setErroresCampo((actual) => ({ ...actual, nombre: undefined }));
                  }}
                  aria-invalid={erroresCampo.nombre ? 'true' : undefined}
                  aria-describedby={erroresCampo.nombre ? 'error-nombre' : undefined}
                />
                {erroresCampo.nombre ? <p className="campo__error" id="error-nombre">{erroresCampo.nombre}</p> : null}
              </div>

              {error ? <MensajeError texto={error} /> : null}
            {precioNuevo ? (
              <Aviso tono="aviso" titulo="El precio cambió desde tu búsqueda">
                <p>
                  Viste {monto(eleccion.totalCents, eleccion.moneda)} y ahora el total es{' '}
                  {monto(precioNuevo.totalCents, precioNuevo.moneda)}
                  {planNuevo ? ` con el plan ${planNuevo}` : ''}. Si estás de acuerdo,
                  pulsa «Confirmar solicitud de reserva» de nuevo.
                </p>
              </Aviso>
            ) : null}

              <button className="boton boton--primario" type="submit" disabled={enviando}>
                {enviando ? 'Enviando…' : 'Confirmar solicitud de reserva'}
              </button>
              <p className="campo__ayuda">
                Tu solicitud quedará pendiente de confirmación. El hotel la revisará y te escribirá.
              </p>
            </div>
          </form>
        </section>

      </div>
    </main>
  );
}

function VistaReserva({
  reserva,
  eleccion,
  conCuenta,
}: {
  reserva: RespuestaReserva;
  eleccion: EnCurso | null;
  conCuenta: boolean;
}) {
  const noches = reserva.noches ?? eleccion?.noches;
  const habitacion = reserva.habitacion ?? eleccion?.tipo.nombre ?? 'Pendiente de asignación';

  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <h1 className="seccion__titulo">{reserva.recuperada ? 'Tu reserva' : 'Reserva registrada'}</h1>
        <BotonWhatsApp codigo={reserva.codigo} />
        <CodigoReserva codigo={reserva.codigo} />

        <Aviso
          tono={reserva.recuperada ? 'info' : 'exito'}
          titulo={reserva.recuperada ? 'Comprobante actualizado' : 'Solicitud recibida'}
        >
          <p>{reserva.mensaje}</p>
        </Aviso>

        <div className="tarjeta pila mt-e5">
          <dl className="pila">
            <div>
              <dt className="campo__etiqueta">Habitación</dt>
              <dd>{habitacion}</dd>
            </div>
            <div>
              <dt className="campo__etiqueta">Fechas</dt>
              <dd className="cifra">
                {fechaCorta(reserva.llegada)} → {fechaCorta(reserva.salida)}{noches ? (
                  <> ({noches} {noches === 1 ? 'noche' : 'noches'})</>
                ) : null}
              </dd>
            </div>
            <div>
              <dt className="campo__etiqueta">Huéspedes</dt>
              <dd className="cifra">{reserva.huespedes}</dd>
            </div>
            <div>
              <dt className="campo__etiqueta">Estado</dt>
              <dd>{reserva.estado === 'PENDIENTE' ? 'Pendiente de confirmación' : reserva.estado}</dd>
            </div>
          </dl>
        </div>

        <p className="mt-e5">
          {conCuenta ? (
            <>Esta reserva ya está en tu cuenta: puedes verla sin el código.</>
          ) : (
            <>Guarda el código: con él y tu correo puedes consultar la reserva cuando quieras.</>
          )}
        </p>
        <p className="pila gap-e2">
          <Link className="boton boton--secundario" to="/">
            Volver al inicio
          </Link>
          {conCuenta ? (
            <Link className="boton boton--primario" to="/mis-reservas">
              Ver mis reservas
            </Link>
          ) : (
            <Link className="boton boton--secundario" to="/consulta">
              Consultar una reserva
            </Link>
          )}
        </p>
      </section>
    </main>
  );
}

/** Extrae el importe y plan vigentes de un 409 por cambio de precio; null para otro error. */
function cambioDePrecio(e: unknown): { totalCents: number; moneda: string; ratePlanId: number } | null {
  if (!(e instanceof ErrorApi) || e.estado !== 409) return null;
  const d = e.datos as { nuevoTotalCents?: unknown; nuevaMoneda?: unknown; nuevoRatePlanId?: unknown } | null;
  if (!d || typeof d.nuevoTotalCents !== 'number' || typeof d.nuevaMoneda !== 'string'
    || typeof d.nuevoRatePlanId !== 'number') return null;
  return { totalCents: d.nuevoTotalCents, moneda: d.nuevaMoneda, ratePlanId: d.nuevoRatePlanId };
}

function leerLocalizadorComprobante(): LocalizadorComprobante | null {
  const bruto = sessionStorage.getItem(CLAVE_COMPROBANTE);
  if (!bruto) return null;
  try {
    const datos: unknown = JSON.parse(bruto);
    if (!datos || typeof datos !== 'object') return null;
    const o = datos as Record<string, unknown>;
    if (typeof o.codigo !== 'string' || !o.codigo.trim()
      || typeof o.email !== 'string' || !o.email.trim()) return null;
    return { codigo: o.codigo.trim(), email: o.email.trim() };
  } catch {
    return null;
  }
}

function leerEnCurso(): EnCurso | null {
  const bruto = sessionStorage.getItem('reserva-en-curso');
  if (!bruto) return null;
  try {
    // JSON.parse más un cast no verifica nada: si otro código (o una versión vieja del
    // panel) dejó otra forma, se descarta en vez de pintar una reserva a medias.
    const datos: unknown = JSON.parse(bruto);
    if (!datos || typeof datos !== 'object') return null;
    const o = datos as Record<string, unknown>;
    const habitacion = o.habitacion as { id?: unknown } | undefined;
    if (typeof o.llegada !== 'string' || typeof o.salida !== 'string'
      || typeof o.huespedes !== 'number' || typeof o.totalCents !== 'number'
      || typeof o.moneda !== 'string' || typeof o.noches !== 'number'
      || typeof o.clave !== 'string' || typeof habitacion?.id !== 'number') return null;
    return datos as EnCurso;
  } catch {
    return null;
  }
}
