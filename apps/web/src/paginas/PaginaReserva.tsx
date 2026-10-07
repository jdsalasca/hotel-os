import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ErrorApi, api } from '../api/cliente';
import { useSesionHuesped } from '../api/useSesionHuesped';
import { fechaCorta, monto } from '../api/formato';
import { Aviso, HuecoImagen, MensajeError } from '../componentes/Estado';

type EnCurso = {
  habitacion: { id: number; codigo: string; nombre: string };
  tipo: { nombre: string };
  totalCents: number;
  moneda: string;
  noches: number;
  plan?: { id: number; codigo: string; nombre: string };
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
};

/** Pasos 2 a 5 del flujo público: datos mínimos, envío y resultado con su estado real. */
export function PaginaReserva() {
  // Se lee UNA vez al montar. Si se relejera en cada render, al borrar la selección tras el
  // éxito volvería a ser null y la pantalla de confirmación no se llegaría a pintar.
  const [enCurso] = useState<EnCurso | null>(() => leerEnCurso());
  const sesion = useSesionHuesped();
  const [email, setEmail] = useState('');
  const [nombre, setNombre] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reserva, setReserva] = useState<RespuestaReserva | null>(null);
  // Si la tarifa se movió entre la búsqueda y la confirmación, aquí queda el importe vigente
  // para que el huésped lo confirme de nuevo con conocimiento, no en silencio.
  const [precioNuevo, setPrecioNuevo] = useState<{ totalCents: number; moneda: string } | null>(null);

  if (!enCurso) {
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

  // Tras el guard, TypeScript no puede estrechar `enCurso` dentro de `enviar` (es un closure y
  // podría ejecutarse antes de que el estado cambie). Se fija aquí y se usa `eleccion` en todas
  // partes: además evita leer un estado que React ya pudo limpiar.
  const eleccion = enCurso;
  // Lo que el huésped acepta pagar: lo visto en la búsqueda, o el vigente tras un cambio de
  // tarifa que ya se le mostró. Nunca se envía un importe que no haya visto.
  const esperado = precioNuevo ?? { totalCents: eleccion.totalCents, moneda: eleccion.moneda };

  async function enviar(evento: React.FormEvent) {
    evento.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      const r = await api.post<RespuestaReserva>('/api/reservas', {
        email,
        nombre,
        llegada: eleccion.llegada,
        salida: eleccion.salida,
        huespedes: eleccion.huespedes,
        roomId: eleccion.habitacion.id,
        idempotencia: eleccion.clave,
        totalEsperadoCents: esperado.totalCents,
        monedaEsperada: esperado.moneda,
        ratePlanIdEsperado: eleccion.plan?.id ?? null,
      });
      setReserva(r);
      sessionStorage.removeItem('reserva-en-curso');
    } catch (e) {
      const cambio = cambioDePrecio(e);
      if (cambio) {
        setPrecioNuevo(cambio);
      } else {
        setPrecioNuevo(null);
        setError(e instanceof Error ? e.message : 'No se pudo registrar la reserva');
      }
    } finally {
      setEnviando(false);
    }
  }

  if (reserva) {
    return (
      <main id="contenido" className="centrado">
        <section className="seccion">
          <h1 className="seccion__titulo">Reserva registrada</h1>

          <p className="pila gap-e2">
            <span className="campo__etiqueta">Tu código de reserva</span>
            <br />
            <span className="codigo-reserva">{reserva.codigo}</span>
          </p>

          <Aviso tono="exito" titulo="Solicitud recibida">
            <p>{reserva.mensaje}</p>
          </Aviso>

          <div className="tarjeta pila mt-e5">
            <dl className="pila">
              <div>
                <dt className="campo__etiqueta">Habitación</dt>
                <dd>{eleccion.tipo.nombre}</dd>
              </div>
              <div>
                <dt className="campo__etiqueta">Fechas</dt>
                <dd className="cifra">
                  {fechaCorta(reserva.llegada)} → {fechaCorta(reserva.salida)} ({eleccion.noches}{' '}
                  {eleccion.noches === 1 ? 'noche' : 'noches'})
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
            {sesion.haySesion ? (
              <>Esta reserva ya está en tu cuenta: puedes verla sin el código.</>
            ) : (
              <>Guarda el código: con él y tu correo puedes consultar la reserva cuando quieras.</>
            )}
          </p>
          <p className="pila gap-e2">
            <Link className="boton boton--secundario" to="/">
              Volver al inicio
            </Link>
            {sesion.haySesion ? (
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

  return (
    <main id="contenido" className="centrado">
      <div className="rejilla rejilla--dos pt-e6">
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
                  onChange={(e) => setEmail(e.target.value)}
                  aria-describedby="ayuda-email"
                />
                <p className="campo__ayuda" id="ayuda-email">
                  Lo usaremos para confirmarte y para que puedas consultar tu reserva.
                </p>
              </div>

              <div className="campo">
                <label className="campo__etiqueta" htmlFor="nombre">Nombre *</label>
                <input
                  id="nombre"
                  type="text"
                  required
                  autoComplete="name"
                  value={nombre}
                  onChange={(e) => setNombre(e.target.value)}
                />
              </div>

              {error ? <MensajeError texto={error} /> : null}
              {precioNuevo ? (
                <Aviso tono="aviso" titulo="El precio cambió desde tu búsqueda">
                  <p>
                    Viste {monto(eleccion.totalCents, eleccion.moneda)} y ahora el total es{' '}
                    {monto(precioNuevo.totalCents, precioNuevo.moneda)}. Si estás de acuerdo,
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
            {eleccion.plan ? (
              <p className="campo__ayuda sin-margen">Plan {eleccion.plan.nombre}</p>
            ) : null}
            <p className="precio">
              {monto(esperado.totalCents, esperado.moneda)}
              <span className="precio__detalle">total del periodo</span>
            </p>
            <Link className="boton boton--fantasma boton--chico" to="/">
              Cambiar fechas
            </Link>
          </div>
        </aside>
      </div>
    </main>
  );
}

/** Extrae el importe vigente de un 409 por cambio de precio; null para cualquier otro error. */
function cambioDePrecio(e: unknown): { totalCents: number; moneda: string } | null {
  if (!(e instanceof ErrorApi) || e.estado !== 409) return null;
  const d = e.datos as { nuevoTotalCents?: unknown; nuevaMoneda?: unknown } | null;
  if (!d || typeof d.nuevoTotalCents !== 'number' || typeof d.nuevaMoneda !== 'string') return null;
  return { totalCents: d.nuevoTotalCents, moneda: d.nuevaMoneda };
}

function leerEnCurso(): EnCurso | null {
  const bruto = sessionStorage.getItem('reserva-en-curso');
  if (!bruto) return null;
  try {
    return JSON.parse(bruto) as EnCurso;
  } catch {
    return null;
  }
}