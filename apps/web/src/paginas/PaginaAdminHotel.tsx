import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { useSesion } from '../api/useSesion';
import { Aviso, Cargando, MensajeError, PuertaAdmin } from '../componentes/Estado';
import { MONEDAS } from '../dominio/monedas';

type ConfigHotel = Record<string, string>;

const CAMPOS: {
  clave: string;
  etiqueta: string;
  tipo?: string;
  paso?: string;
  maxLength?: number;
  placeholder?: string;
  ayuda?: string;
}[] = [
  { clave: 'nombre', etiqueta: 'Nombre del hotel', maxLength: 80, placeholder: 'Hotel Prueba Leyva' },
  {
    clave: 'contacto_email',
    etiqueta: 'Correo de contacto',
    tipo: 'email',
    maxLength: 254,
    placeholder: 'reservas@hotel.com',
  },
  {
    clave: 'contacto_telefono',
    etiqueta: 'Teléfono de contacto',
    tipo: 'tel',
    maxLength: 30,
    placeholder: '+57 320 000 0000',
  },
  {
    clave: 'direccion',
    etiqueta: 'Dirección',
    maxLength: 200,
    placeholder: 'Calle 1 # 2-3, Villa de Leyva',
  },
  { clave: 'hora_entrada', etiqueta: 'Hora de entrada', tipo: 'time' },
  { clave: 'hora_salida', etiqueta: 'Hora de salida', tipo: 'time' },
  {
    clave: 'politica_cancelacion',
    etiqueta: 'Política de cancelación',
    maxLength: 1000,
    placeholder: 'Cancelación gratuita hasta 24 horas antes.',
  },
  {
    clave: 'zona_horaria',
    etiqueta: 'Zona horaria',
    maxLength: 80,
    placeholder: 'America/Bogota',
    ayuda: 'Nombre IANA, por ejemplo America/Bogota.',
  },
  {
    clave: 'moneda',
    etiqueta: 'Moneda',
    ayuda: 'La moneda de cada plan tarifario se configura en Inventario.',
  },
  {
    clave: 'latitud',
    etiqueta: 'Latitud del hotel',
    tipo: 'number',
    paso: 'any',
    placeholder: '5.65',
    ayuda: 'Para el mapa: -90 a 90. Vacío = sin ubicar.',
  },
  {
    clave: 'longitud',
    etiqueta: 'Longitud del hotel',
    tipo: 'number',
    paso: 'any',
    placeholder: '-73.52',
    ayuda: 'Para el mapa: -180 a 180. Vacío = sin ubicar.',
  },
];

const VACIO: ConfigHotel = Object.fromEntries(CAMPOS.map((campo) => [campo.clave, '']));

/** Centro de la vista cuando el hotel aún no está ubicado: Boyacá, donde está el hotel. */
const CENTRO_SIN_UBICAR = { latitud: 5.65, longitud: -73.52 };

/**
 * Elegir el punto del hotel sobre el mapa en vez de teclear `5.635, -73.525`.
 *
 * El punto marcado ES la fuente de verdad: los botones lo mueven y el visor se recentra en él
 * en cada pulsación, así que lo que se guarda nunca se desincroniza de lo que se ve. El visor
 * trae sus propios controles de zoom y arrastre, pero si el hotelero los usa la vista se
 * desvía; la siguiente pulsación de un botón la vuelve a recentrar en el punto real.
 */
function SelectorDePunto({
  punto,
  onElegir,
}: {
  punto: { latitud: number; longitud: number };
  onElegir: (lat: number, lng: number) => void;
}) {
  const [paso, setPaso] = useState(0.02);
  const src = `https://www.openstreetmap.org/export/embed.html?bbox=${
    punto.longitud - paso
  }%2C${punto.latitud - paso * 0.66}%2C${punto.longitud + paso}%2C${
    punto.latitud + paso * 0.66
  }&layer=mapnik&marker=${encodeURIComponent(`${punto.latitud},${punto.longitud}`)}`;

  return (
    <div className="tarjeta pila">
      <h2 className="t-base mb-0">Ubicación del hotel</h2>
      <p className="campo__ayuda sin-margen">
        El punto verde es lo que se guarda. Muévelo con los botones hasta reconocer la entrada
        del hotel y pulsa <strong>Usar este punto</strong>.
      </p>
      <div className="pila gap-e1">
        <iframe
          className="mapa__marco"
          title="Mapa para elegir el punto del hotel"
          loading="lazy"
          src={src}
        />
        <div className="acciones-lugar">
          <button
            className="boton boton--fantasma boton--chico"
            type="button"
            onClick={() => onElegir(Number((punto.latitud - paso / 4).toFixed(5)), punto.longitud)}
          >
            Sur
          </button>
          <button
            className="boton boton--fantasma boton--chico"
            type="button"
            onClick={() => onElegir(Number((punto.latitud + paso / 4).toFixed(5)), punto.longitud)}
          >
            Norte
          </button>
          <button
            className="boton boton--fantasma boton--chico"
            type="button"
            onClick={() => onElegir(punto.latitud, Number((punto.longitud - paso / 4).toFixed(5)))}
          >
            Oeste
          </button>
          <button
            className="boton boton--fantasma boton--chico"
            type="button"
            onClick={() => onElegir(punto.latitud, Number((punto.longitud + paso / 4).toFixed(5)))}
          >
            Este
          </button>
          <button
            className="boton boton--fantasma boton--chico"
            type="button"
            onClick={() => setPaso((p) => Number((p / 2).toFixed(5)))}
          >
            Acercar
          </button>
          <button
            className="boton boton--secundario boton--chico"
            type="button"
            onClick={() => onElegir(punto.latitud, punto.longitud)}
          >
            Usar este punto
          </button>
        </div>
      </div>
    </div>
  );
}

/** Identidad operativa del hotel: lo que la web pública puede mostrar y nada más. */
export function PaginaAdminHotel() {
  const sesion = useSesion();
  const [valores, setValores] = useState<ConfigHotel>(VACIO);
  const [error, setError] = useState<string | null>(null);
  const [guardado, setGuardado] = useState(false);
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [cargaFallida, setCargaFallida] = useState(false);
  const [eligiendoPunto, setEligiendoPunto] = useState(false);

  useEffect(() => {
    async function cargar() {
      setCargando(true);
      setCargaFallida(false);
      setError(null);
      try {
        const actual = await api.get<ConfigHotel>('/api/admin/hotel-config');
        setValores({ ...VACIO, ...actual });
      } catch (e) {
        setCargaFallida(true);
        setError(e instanceof Error ? e.message : 'No se pudo cargar la configuración del hotel');
      } finally {
        setCargando(false);
      }
    }
    void cargar();
  }, []);

  async function guardar(evento: FormEvent) {
    evento.preventDefault();
    setError(null);
    setGuardado(false);
    setGuardando(true);
    try {
      const actual = await api.post<ConfigHotel>('/api/admin/hotel-config', valores);
      setValores({ ...VACIO, ...actual });
      setCargaFallida(false);
      setGuardado(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'La configuración no pudo guardarse');
    } finally {
      setGuardando(false);
    }
  }

  if (sesion.haySesion === false) return <PuertaAdmin>Inicia sesión para configurar el hotel.</PuertaAdmin>;
  if (sesion.haySesion === null) return <Cargando texto="Comprobando sesión" />;

  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <h1 className="seccion__titulo">Datos del hotel</h1>
        <p className="seccion__intro">
          Solo marca, contacto y horarios operativos. Nada de esta pantalla decide precios,
          inventario ni reservas.
        </p>

        {error ? <MensajeError texto={error} /> : null}
        {cargando ? <Cargando texto="Cargando datos del hotel" /> : null}
        {guardado ? (
          <Aviso tono="exito" titulo="Datos guardados">
            La web pública ya muestra la identidad actualizada.
          </Aviso>
        ) : null}

        <form className="tarjeta pila" aria-busy={cargando} onSubmit={(e) => void guardar(e)}>
          <div className="rejilla rejilla--dos">
            {CAMPOS.map((campo) => (
              <div className="campo" key={campo.clave}>
                <label className="campo__etiqueta" htmlFor={`hotel-${campo.clave}`}>
                  {campo.etiqueta}
                </label>
                {campo.clave === 'moneda' && cargaFallida ? (
                  <input
                    id={`hotel-${campo.clave}`}
                    type="text"
                    maxLength={3}
                    placeholder="COP"
                    disabled={cargando}
                    value={valores[campo.clave] ?? ''}
                    onChange={(e) => setValores({ ...valores, [campo.clave]: e.target.value })}
                  />
                ) : campo.clave === 'moneda' ? (
                  <select
                    id={`hotel-${campo.clave}`}
                    disabled={cargando}
                    value={valores[campo.clave] ?? ''}
                    onChange={(e) => setValores({ ...valores, [campo.clave]: e.target.value })}
                  >
                    <option value="">Selecciona una moneda</option>
                    {valores.moneda && !MONEDAS.some((moneda) => moneda.codigo === valores.moneda) ? (
                      <option value={valores.moneda}>Código actual ({valores.moneda})</option>
                    ) : null}
                    {MONEDAS.map((moneda) => (
                      <option key={moneda.codigo} value={moneda.codigo}>
                        {moneda.codigo} — {moneda.nombre}
                      </option>
                    ))}
                  </select>
                ) : campo.clave === 'politica_cancelacion' ? (
                  <textarea
                    id={`hotel-${campo.clave}`}
                    rows={4}
                    maxLength={campo.maxLength}
                    placeholder={campo.placeholder}
                    disabled={cargando}
                    value={valores[campo.clave] ?? ''}
                    onChange={(e) => setValores({ ...valores, [campo.clave]: e.target.value })}
                  />
                ) : (
                  <input
                    id={`hotel-${campo.clave}`}
                    type={campo.tipo ?? 'text'}
                    step={campo.paso}
                    required={campo.clave === 'nombre'}
                    maxLength={campo.maxLength}
                    placeholder={campo.placeholder}
                    disabled={cargando}
                    value={valores[campo.clave] ?? ''}
                    onChange={(e) => setValores({ ...valores, [campo.clave]: e.target.value })}
                  />
                )}
                {campo.ayuda ? (
                  <p className="campo__ayuda sin-margen">
                    {campo.clave === 'moneda' && cargaFallida
                      ? 'Código ISO 4217 de tres letras, por ejemplo CHF.'
                      : campo.ayuda}
                  </p>
                ) : null}
              </div>
            ))}
          </div>
          <div className="acciones-lugar">
            <button
              className="boton boton--secundario boton--chico"
              type="button"
              disabled={cargando}
              onClick={() => setEligiendoPunto((v) => !v)}
            >
              {eligiendoPunto ? 'Cerrar el mapa' : 'Ubicar en el mapa'}
            </button>
          </div>
          {eligiendoPunto ? (
            <SelectorDePunto
              punto={{
                latitud: Number(valores.latitud) || CENTRO_SIN_UBICAR.latitud,
                longitud: Number(valores.longitud) || CENTRO_SIN_UBICAR.longitud,
              }}
              onElegir={(lat, lng) => setValores({ ...valores, latitud: String(lat), longitud: String(lng) })}
            />
          ) : null}
          <button
            className="boton boton--primario no-estirar"
            type="submit"
            disabled={cargando || guardando}
          >
            {guardando ? 'Guardando…' : 'Guardar datos del hotel'}
          </button>
        </form>

        <p className="mt-e6">
          <Link to="/admin/reservas">Volver a las reservas</Link>
        </p>
      </section>
    </main>
  );
}
