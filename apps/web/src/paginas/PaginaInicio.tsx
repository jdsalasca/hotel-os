import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, nuevaClaveIdempotencia } from '../api/cliente';
import { fechaCorta, mananaIso, monto } from '../api/formato';
import { HuecoImagen, MensajeError } from '../componentes/Estado';

type Oferta = {
  habitacion: { id: number; codigo: string; nombre: string };
  tipo: { id: number; codigo: string; nombre: string; capacidadMax: number };
  totalCents: number;
  moneda: string;
  noches: number;
};

type RespuestaDisponibilidad = {
  llegada: string;
  salida: string;
  huespedes: number;
  ofertas: Oferta[];
  error?: string;
};

/** Paso 1 del flujo público: fechas, huéspedes y habitaciones disponibles con su precio. */
export function PaginaInicio() {
  const navegar = useNavigate();
  const [llegada, setLlegada] = useState('');
  const [salida, setSalida] = useState('');
  const [huespedes, setHuespedes] = useState(2);
  const [ofertas, setOfertas] = useState<Oferta[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(false);
  const [buscado, setBuscado] = useState(false);

  async function buscar(evento: React.FormEvent) {
    evento.preventDefault();
    setError(null);
    setCargando(true);
    try {
      const consulta = new URLSearchParams({ llegada, salida, huespedes: String(huespedes) });
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

  function elegir(oferta: Oferta) {
    // Clave de idempotencia por intento: el respaldo cubre entornos sin secure context (HTTP local).
    const clave = nuevaClaveIdempotencia();
    sessionStorage.setItem('reserva-en-curso', JSON.stringify({ ...oferta, llegada, salida, huespedes, clave }));
    navegar('/reserva');
  }

  const fechasInvalidas = llegada && salida && salida <= llegada;

  return (
    <main id="contenido">
      <section className="hero">
        <div className="hero__interna">
          <h1 className="hero__titulo">Reserva directa en el hotel</h1>
          <p className="hero__texto">
            Consulta la disponibilidad y reserva sin intermediarios. Si el hotel no ha configurado
            precios para tus fechas, no te mostraremos un importe inventado.
          </p>
        </div>
      </section>

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
              {ofertas.map((oferta) => (
                <article className="tarjeta pila" key={oferta.habitacion.id}>
                  <HuecoImagen texto="Fotografía de la habitación" />
                  <h3>{oferta.tipo.nombre}</h3>
                  <p className="campo__ayuda">
                    {oferta.habitacion.nombre || oferta.habitacion.codigo} · Hasta{' '}
                    {oferta.tipo.capacidadMax} huéspedes
                  </p>
                  <p className="precio">
                    {monto(oferta.totalCents, oferta.moneda)}
                    <span className="precio__detalle">
                      total por {oferta.noches} {oferta.noches === 1 ? 'noche' : 'noches'}
                    </span>
                  </p>
                  <button
                    className="boton boton--primario boton--bloque"
                    onClick={() => elegir(oferta)}
                    aria-label={`Elegir ${oferta.tipo.nombre}`}
                  >
                    Elegir esta habitación
                  </button>
                </article>
              ))}
            </div>
          </section>
        ) : null}
      </div>
    </main>
  );
}