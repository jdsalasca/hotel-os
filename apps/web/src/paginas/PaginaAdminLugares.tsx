import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { CATEGORIAS_LUGAR, etiquetaCategoriaLugar, type CategoriaLugar } from '../api/lugares';
import { useSesion } from '../api/useSesion';
import { Aviso, Cargando, MensajeError, PuertaAdmin, Vacio } from '../componentes/Estado';
import { SelectorDePunto } from '../componentes/SelectorDePunto';

type Lugar = {
  id: number;
  nombre: string;
  descripcion: string;
  categoria?: CategoriaLugar;
  latitud: number;
  longitud: number;
  activo: boolean;
  /** Distancia al hotel en metros. Viene del servidor; si el hotel aún no está ubicado, no viene. */
  metros?: number;
};

/**
 * MetrosExactos bajo el kilómetro y kilómetros con un decimal por encima: "240 m" y "3,5 km"
 * se leen de un vistazo, que es justo lo que el hotelero quiere saber de un vistazo.
 */
function formatearDistancia(metros: number): string {
  if (metros < 1000) return `${metros} m`;
  const km = (metros / 1000).toLocaleString('es-CO', { maximumFractionDigits: 1 });
  return `${km} km`;
}

const VACIO = {
  nombre: '',
  descripcion: '',
  categoria: 'VISITAR' as CategoriaLugar,
  latitud: '',
  longitud: '',
};

/** Sitios y experiencias del mapa: la web pública solo muestra los activos. */
export function PaginaAdminLugares() {
  const sesion = useSesion();
  const [lugares, setLugares] = useState<Lugar[] | null>(null);
  const [nuevo, setNuevo] = useState(VACIO);
  const [editando, setEditando] = useState<Lugar | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [errorCargaLugares, setErrorCargaLugares] = useState<string | null>(null);
  const [cargandoLugares, setCargandoLugares] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const nombreRef = useRef<HTMLInputElement>(null);
  /** El hotel sin GPS no publica el mapa: la pantalla lo dice en vez de quedarse callada. */
  const [sinPunto, setSinPunto] = useState<boolean | null>(null);
  /** Centro con el que se abre el mapa: el hotel, para partir de donde está el pueblo. */
  const [centro, setCentro] = useState({ latitud: 5.65, longitud: -73.52 });
  const [eligiendoPunto, setEligiendoPunto] = useState(false);

  async function cargar() {
    setCargandoLugares(true);
    setErrorCargaLugares(null);
    try {
      const datos = await api.get<{ lugares: Lugar[] }>('/api/admin/lugares');
      setLugares(datos.lugares);
    } catch (e) {
      setErrorCargaLugares(e instanceof Error ? e.message : 'No se pudieron cargar los lugares');
    } finally {
      setCargandoLugares(false);
    }
  }

  /** La config del hotel viene aparte: si su lectura falla, el mapa no se declara apagado. */
  async function cargarPuntoDelHotel() {
    try {
      const c = await api.get<{ latitud?: string; longitud?: string }>('/api/admin/hotel-config');
      const lat = Number(c.latitud);
      const lng = Number(c.longitud);
      // `Number('')` es 0, que es un número válido: hay que mirar el texto, no el número.
      const latOk = (c.latitud ?? '').trim().length > 0 && Number.isFinite(lat);
      const lngOk = (c.longitud ?? '').trim().length > 0 && Number.isFinite(lng);
      setSinPunto(!latOk || !lngOk);
      // El mapa arranca donde está el hotel: es el sitio que quien ubica está viendo.
      if (latOk && lngOk) setCentro({ latitud: lat, longitud: lng });
    } catch {
      setSinPunto(null);
    }
  }

  useEffect(() => {
    if (sesion.haySesion !== true) return;
    void cargar();
    void cargarPuntoDelHotel();
  }, [sesion.haySesion]);

  useEffect(() => {
    if (editando) nombreRef.current?.focus();
  }, [editando]);

  async function guardar(evento: FormEvent) {
    evento.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      const valores = {
        nombre: nuevo.nombre,
        descripcion: nuevo.descripcion,
        categoria: nuevo.categoria,
        latitud: Number(nuevo.latitud),
        longitud: Number(nuevo.longitud),
      };
      if (editando) {
        await api.put(`/api/admin/lugares/${editando.id}`, {
          ...valores,
          activo: editando.activo,
        });
      } else {
        await api.post('/api/admin/lugares', valores);
      }
      setNuevo(VACIO);
      setEditando(null);
      await cargar();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo guardar el lugar');
    } finally {
      setEnviando(false);
    }
  }

  function editar(lugar: Lugar) {
    setEditando(lugar);
    setNuevo({
      nombre: lugar.nombre,
      descripcion: lugar.descripcion,
      categoria: lugar.categoria ?? 'VISITAR',
      latitud: String(lugar.latitud),
      longitud: String(lugar.longitud),
    });
  }

  function cancelarEdicion() {
    setEditando(null);
    setNuevo(VACIO);
    setError(null);
  }

  async function alternar(lugar: Lugar) {
    setError(null);
    try {
      await api.put(`/api/admin/lugares/${lugar.id}`, {
        nombre: lugar.nombre,
        descripcion: lugar.descripcion,
        categoria: lugar.categoria ?? 'VISITAR',
        latitud: lugar.latitud,
        longitud: lugar.longitud,
        activo: !lugar.activo,
      });
      await cargar();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo cambiar el lugar');
    }
  }

  async function eliminar(lugar: Lugar) {
    if (!window.confirm(`¿Eliminar "${lugar.nombre}" del mapa?`)) return;
    setError(null);
    try {
      await api.del(`/api/admin/lugares/${lugar.id}`);
      await cargar();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo eliminar el lugar');
    }
  }

  if (sesion.haySesion === false)
    return <PuertaAdmin>Inicia sesión para gestionar el mapa.</PuertaAdmin>;
  if (sesion.haySesion === null) return <Cargando texto="Comprobando sesión" />;

  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <h1 className="seccion__titulo">Lugares en el mapa</h1>
        <p className="seccion__intro">
          Lo que el huésped ve alrededor del hotel, con su distancia. Solo los activos salen en
          la web.
        </p>

        {error ? <MensajeError texto={error} /> : null}
        {errorCargaLugares ? (
          <div className="pila">
            <MensajeError texto={errorCargaLugares} />
            <div className="acciones-lugar">
              <button
                className="boton boton--secundario boton--chico"
                type="button"
                onClick={() => void cargar()}
              >
                Reintentar carga de lugares
              </button>
            </div>
          </div>
        ) : null}

        {/* Sin el punto del hotel no hay distancias que mostrar: el hotelero crea lugares con
            entusiasmo, guarda, y la web sigue vacía sin que nadie le diga por qué. */}
        {sinPunto ? (
          <Aviso tono="aviso" titulo="El mapa no aparece todavía">
            Falta el punto del hotel en el GPS, así que la web no muestra ningún sitio. Ponlo en
            <Link to="/admin/hotel"> Datos del hotel</Link> y este mapa se publica solo.
          </Aviso>
        ) : null}

        <form className="tarjeta pila" onSubmit={(e) => void guardar(e)}>
          <h2 className="t-lg mb-0">
            {editando ? `Editar lugar: ${editando.nombre}` : 'Nuevo lugar'}
          </h2>
          <div className="campo">
            <label className="campo__etiqueta" htmlFor="lugar-nombre">Nombre</label>
            <input
              ref={nombreRef}
              id="lugar-nombre"
              required
              minLength={2}
              maxLength={120}
              placeholder="Plaza Mayor de Villa de Leyva"
              value={nuevo.nombre}
              onChange={(e) => setNuevo({ ...nuevo, nombre: e.target.value })}
            />
          </div>
          <div className="campo">
            <label className="campo__etiqueta" htmlFor="lugar-descripcion">Descripción</label>
            <input
              id="lugar-descripcion"
              maxLength={500}
              placeholder="La plaza empedrada más grande de Colombia"
              value={nuevo.descripcion}
              onChange={(e) => setNuevo({ ...nuevo, descripcion: e.target.value })}
            />
          </div>
          <div className="campo">
            <label className="campo__etiqueta" htmlFor="lugar-categoria">Categoría</label>
            <select
              id="lugar-categoria"
              required
              value={nuevo.categoria}
              onChange={(e) => setNuevo({ ...nuevo, categoria: e.target.value as CategoriaLugar })}
            >
              {CATEGORIAS_LUGAR.map((categoria) => (
                <option key={categoria.valor} value={categoria.valor}>{categoria.etiqueta}</option>
              ))}
            </select>
          </div>
          <div className="campos">
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="lugar-lat">Latitud</label>
              <input
                id="lugar-lat"
                type="number"
                step="any"
                required
                placeholder="5.635"
                value={nuevo.latitud}
                onChange={(e) => setNuevo({ ...nuevo, latitud: e.target.value })}
              />
            </div>
            <div className="campo">
              <label className="campo__etiqueta" htmlFor="lugar-lng">Longitud</label>
              <input
                id="lugar-lng"
                type="number"
                step="any"
                required
                placeholder="-73.525"
                value={nuevo.longitud}
                onChange={(e) => setNuevo({ ...nuevo, longitud: e.target.value })}
              />
            </div>
          </div>
          <p className="campo__ayuda sin-margen">
            Puedes teclearlas, o ubicar el sitio en el mapa de abajo.
          </p>
          <div className="acciones-lugar">
            {/* Sin salir a copiar coordenadas: el mapa se abre aquí y se mueve con botones. */}
            <button
              className="boton boton--secundario boton--chico"
              type="button"
              disabled={editando === null && lugares === null}
              onClick={() => setEligiendoPunto((v) => !v)}
            >
              {eligiendoPunto ? 'Cerrar el mapa' : 'Ubicar en el mapa'}
            </button>
          </div>
          {eligiendoPunto ? (
            <SelectorDePunto
              titulo="Ubicación del lugar"
              ayuda="El punto verde es lo que se guarda. Muévelo con los botones hasta reconocer el sitio y pulsa Usar este punto."
              punto={{
                latitud: Number(nuevo.latitud) || centro.latitud,
                longitud: Number(nuevo.longitud) || centro.longitud,
              }}
              onElegir={(lat, lng) =>
                setNuevo((actual) => ({ ...actual, latitud: String(lat), longitud: String(lng) }))
              }
            />
          ) : null}
          <div className="acciones-lugar">
            <button className="boton boton--primario" type="submit" disabled={enviando}>
              {enviando ? 'Guardando…' : editando ? 'Guardar cambios' : 'Agregar lugar'}
            </button>
            {editando ? (
              <button
                className="boton boton--fantasma"
                type="button"
                disabled={enviando}
                onClick={cancelarEdicion}
              >
                Cancelar edición
              </button>
            ) : null}
          </div>
        </form>

        {lugares === null && cargandoLugares && errorCargaLugares === null ? (
          <Cargando texto="Cargando lugares" />
        ) : null}
        {lugares !== null && lugares.length === 0 ? (
          <Vacio titulo="Sin lugares todavía" detalle="Agrega el primero: aparecerá en el mapa de la web." />
        ) : null}
        {lugares !== null && lugares.length > 0 ? (
          <table className="tabla mt-e6">
            <caption>Lugares del mapa</caption>
            <thead>
              <tr>
                <th scope="col">Nombre</th>
                <th scope="col">Categoría</th>
                <th scope="col">Punto</th>
                <th scope="col">Distancia</th>
                <th scope="col">Visible</th>
                <th scope="col">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {lugares.map((lugar) => (
                <tr key={lugar.id}>
                  <td data-label="Nombre">{lugar.nombre}</td>
                  <td data-label="Categoría">{etiquetaCategoriaLugar(lugar.categoria)}</td>
                  <td className="cifra" data-label="Punto">
                    {lugar.latitud}, {lugar.longitud}
                  </td>
                  <td className="cifra" data-label="Distancia">
                    {lugar.metros === undefined ? (
                      <span className="campo__ayuda">Ubica el hotel</span>
                    ) : (
                      formatearDistancia(lugar.metros)
                    )}
                  </td>
                  <td data-label="Visible">{lugar.activo ? 'Sí' : 'No'}</td>
                  <td data-label="Acciones">
                    <div className="acciones-lugar">
                      <button
                        className="boton boton--fantasma boton--chico"
                        type="button"
                        aria-label={`Editar ${lugar.nombre}`}
                        disabled={editando !== null || enviando}
                        onClick={() => editar(lugar)}
                      >
                        Editar
                      </button>
                      <button
                        className="boton boton--fantasma boton--chico"
                        type="button"
                        aria-label={`${lugar.activo ? 'Ocultar' : 'Mostrar'} ${lugar.nombre}`}
                        disabled={editando !== null || enviando}
                        onClick={() => void alternar(lugar)}
                      >
                        {lugar.activo ? 'Ocultar' : 'Mostrar'}
                      </button>
                      <button
                        className="boton boton--fantasma boton--chico"
                        type="button"
                        aria-label={`Eliminar ${lugar.nombre}`}
                        disabled={editando !== null || enviando}
                        onClick={() => void eliminar(lugar)}
                      >
                        Eliminar
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : null}

        <p className="mt-e6">
          <Link to="/admin/reservas">Volver a las reservas</Link>
        </p>
      </section>
    </main>
  );
}
