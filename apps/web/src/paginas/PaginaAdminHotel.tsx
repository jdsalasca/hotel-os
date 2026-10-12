import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { useSesion } from '../api/useSesion';
import { Aviso, Cargando, MensajeError, PuertaAdmin } from '../componentes/Estado';
import { MONEDAS } from '../dominio/monedas';
import { SelectorDePunto } from '../componentes/SelectorDePunto';

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

const CAMPOS_RESUMEN_FICHA: {
  etiqueta: string;
  claves: string[];
  ubicacion?: boolean;
}[] = [
  { etiqueta: 'Nombre del hotel', claves: ['nombre'] },
  { etiqueta: 'Correo de contacto', claves: ['contacto_email'] },
  { etiqueta: 'Teléfono de contacto', claves: ['contacto_telefono'] },
  { etiqueta: 'Dirección', claves: ['direccion'] },
  { etiqueta: 'Hora de entrada', claves: ['hora_entrada'] },
  { etiqueta: 'Hora de salida', claves: ['hora_salida'] },
  { etiqueta: 'Política de cancelación', claves: ['politica_cancelacion'] },
  { etiqueta: 'Zona horaria', claves: ['zona_horaria'] },
  { etiqueta: 'Moneda', claves: ['moneda'] },
  { etiqueta: 'Punto del hotel', claves: ['latitud', 'longitud'], ubicacion: true },
];

const VACIO: ConfigHotel = Object.fromEntries(CAMPOS.map((campo) => [campo.clave, '']));

/** Un cero es una coordenada válida; solo el campo vacío o no numérico usa el centro inicial. */
function coordenadaParaSelector(valor: string | undefined, respaldo: number): number {
  const texto = valor?.trim();
  if (!texto) return respaldo;
  const numero = Number(texto);
  return Number.isFinite(numero) ? numero : respaldo;
}

/** Centro de la vista cuando el hotel aún no está ubicado: Boyacá, donde está el hotel. */
const CENTRO_SIN_UBICAR = { latitud: 5.65, longitud: -73.52 };

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
        const datos = { ...VACIO, ...actual };
        setValores(datos);
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

  const resumenFicha = CAMPOS_RESUMEN_FICHA.map((campo) => ({
    ...campo,
    informado: campo.claves.every((clave) => Boolean(valores[clave]?.trim())),
  }));
  const datosPendientes = resumenFicha.filter((campo) => !campo.informado).length;
  const configuracionConocida = !cargando && !cargaFallida;
  const sinPunto =
    configuracionConocida &&
    !(Boolean(valores.latitud?.trim()) && Boolean(valores.longitud?.trim()));

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

        {configuracionConocida ? (
          <section className="tarjeta pila" aria-labelledby="hotel-resumen-publico">
            <h2 className="t-base mb-0" id="hotel-resumen-publico">
              Estado de la ficha del hotel
            </h2>
            <p className="campo__ayuda sin-margen">
              El resumen indica si hay un valor en cada campo; no determina si se puede vender ni
              valida el contenido. Revisa que los datos sean correctos y estén aprobados por el
              hotel antes de guardar.
            </p>
            <p className="sin-margen">
              {datosPendientes === 0
                ? 'Todos los datos de esta lista tienen un valor.'
                : `${datosPendientes} de ${resumenFicha.length} datos aún no tienen valor.`}
            </p>
            <ul className="lista-marcada rejilla">
              {resumenFicha.map((campo) => (
                <li className="campo" key={campo.etiqueta}>
                  <strong className="campo__etiqueta">{campo.etiqueta}</strong>
                  <span className="campo__ayuda">
                    {campo.informado ? 'Informado' : 'Pendiente'}
                  </span>
                  {campo.ubicacion ? (
                    <span className="campo__ayuda">
                      {campo.informado
                        ? 'La web puede mostrar el mapa y las distancias.'
                        : 'El mapa no aparece en tu web sin el punto del hotel.'}
                    </span>
                  ) : null}
                  {!campo.informado ? (
                    <button
                      className="boton boton--secundario boton--chico no-estirar"
                      type="button"
                      disabled={guardando}
                      aria-label={campo.ubicacion
                        ? 'Ubicar en el mapa: punto del hotel'
                        : `Completar ${campo.etiqueta.toLocaleLowerCase()}`}
                      onClick={() => {
                        if (campo.ubicacion) {
                          setEligiendoPunto(true);
                          return;
                        }
                        const pendiente = campo.claves.find((clave) => !valores[clave]?.trim());
                        if (pendiente) document.getElementById(`hotel-${pendiente}`)?.focus();
                      }}
                    >
                      {campo.ubicacion ? 'Ubicar en el mapa' : 'Ir al campo'}
                    </button>
                  ) : null}
                </li>
              ))}
            </ul>
          </section>
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
          {/* El botón de aquí sólo aparece cuando el aviso de arriba no está: si no hay
              punto, quien llega siguiendo el aviso del panel ya tiene el mapa a un clic y
              dos botones con el mismo nombre compiten por el mismo destino. */}
          {sinPunto ? null : (
            <div className="acciones-lugar">
              <button
                className="boton boton--secundario boton--chico"
                type="button"
                disabled={cargando}
                onClick={() => setEligiendoPunto(true)}
              >
                {eligiendoPunto ? 'Cerrar el mapa' : 'Ubicar en el mapa'}
              </button>
            </div>
          )}
          {eligiendoPunto ? (
            <SelectorDePunto
              titulo="Ubicación del hotel"
              ayuda="El punto verde es lo que se guarda. Muévelo con los botones hasta reconocer la entrada del hotel y pulsa Usar este punto."
              punto={{
                latitud: coordenadaParaSelector(valores.latitud, CENTRO_SIN_UBICAR.latitud),
                longitud: coordenadaParaSelector(valores.longitud, CENTRO_SIN_UBICAR.longitud),
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
