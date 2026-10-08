import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { useSesion } from '../api/useSesion';
import { Aviso, Cargando, MensajeError, PuertaAdmin } from '../componentes/Estado';

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
    maxLength: 3,
    placeholder: 'COP',
    ayuda: 'Código ISO 4217 de tres letras.',
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

/** Identidad operativa del hotel: lo que la web pública puede mostrar y nada más. */
export function PaginaAdminHotel() {
  const sesion = useSesion();
  const [valores, setValores] = useState<ConfigHotel>(VACIO);
  const [error, setError] = useState<string | null>(null);
  const [guardado, setGuardado] = useState(false);
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    async function cargar() {
      setCargando(true);
      setError(null);
      try {
        const actual = await api.get<ConfigHotel>('/api/admin/hotel-config');
        setValores({ ...VACIO, ...actual });
      } catch (e) {
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

        <form className="tarjeta pila" onSubmit={(e) => void guardar(e)}>
          <div className="rejilla rejilla--dos">
            {CAMPOS.map((campo) => (
              <div className="campo" key={campo.clave}>
                <label className="campo__etiqueta" htmlFor={`hotel-${campo.clave}`}>
                  {campo.etiqueta}
                </label>
                {campo.clave === 'politica_cancelacion' ? (
                  <textarea
                    id={`hotel-${campo.clave}`}
                    rows={4}
                    maxLength={campo.maxLength}
                    placeholder={campo.placeholder}
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
                    value={valores[campo.clave] ?? ''}
                    onChange={(e) => setValores({ ...valores, [campo.clave]: e.target.value })}
                  />
                )}
                {campo.ayuda ? <p className="campo__ayuda sin-margen">{campo.ayuda}</p> : null}
              </div>
            ))}
          </div>
          <button className="boton boton--primario no-estirar" type="submit" disabled={guardando}>
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
