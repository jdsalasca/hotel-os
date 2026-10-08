import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/cliente';
import { useSesion } from '../api/useSesion';
import { Cargando, MensajeError, PuertaAdmin, Vacio } from '../componentes/Estado';

type Lugar = {
  id: number;
  nombre: string;
  descripcion: string;
  latitud: number;
  longitud: number;
  activo: boolean;
};

const VACIO = { nombre: '', descripcion: '', latitud: '', longitud: '' };

/** Sitios y experiencias del mapa: la web pública solo muestra los activos. */
export function PaginaAdminLugares() {
  const sesion = useSesion();
  const [lugares, setLugares] = useState<Lugar[] | null>(null);
  const [nuevo, setNuevo] = useState(VACIO);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function cargar() {
    try {
      const datos = await api.get<{ lugares: Lugar[] }>('/api/admin/lugares');
      setLugares(datos.lugares);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudieron cargar los lugares');
    }
  }

  useEffect(() => {
    if (sesion.haySesion === false) return;
    void cargar();
  }, [sesion.haySesion]);

  async function crear(evento: FormEvent) {
    evento.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      await api.post('/api/admin/lugares', {
        nombre: nuevo.nombre,
        descripcion: nuevo.descripcion,
        latitud: Number(nuevo.latitud),
        longitud: Number(nuevo.longitud),
      });
      setNuevo(VACIO);
      await cargar();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo crear el lugar');
    } finally {
      setEnviando(false);
    }
  }

  async function alternar(lugar: Lugar) {
    setError(null);
    try {
      await api.put(`/api/admin/lugares/${lugar.id}`, {
        nombre: lugar.nombre,
        descripcion: lugar.descripcion,
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

        <form className="tarjeta pila" onSubmit={(e) => void crear(e)}>
          <h2 className="t-lg mb-0">Nuevo lugar</h2>
          <div className="campo">
            <label className="campo__etiqueta" htmlFor="lugar-nombre">Nombre</label>
            <input
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
          <div className="campos campos--tres">
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
            El punto exacto sale de Google Maps: clic derecho → las coordenadas se copian.
          </p>
          <button className="boton boton--primario" type="submit" disabled={enviando}>
            {enviando ? 'Guardando…' : 'Agregar lugar'}
          </button>
        </form>

        {lugares === null ? <Cargando texto="Cargando lugares" /> : null}
        {lugares !== null && lugares.length === 0 ? (
          <Vacio titulo="Sin lugares todavía" detalle="Agrega el primero: aparecerá en el mapa de la web." />
        ) : null}
        {lugares !== null && lugares.length > 0 ? (
          <table className="tabla mt-e6">
            <caption>Lugares del mapa</caption>
            <thead>
              <tr>
                <th scope="col">Nombre</th>
                <th scope="col">Punto</th>
                <th scope="col">Visible</th>
                <th scope="col">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {lugares.map((lugar) => (
                <tr key={lugar.id}>
                  <td data-label="Nombre">{lugar.nombre}</td>
                  <td className="cifra" data-label="Punto">
                    {lugar.latitud}, {lugar.longitud}
                  </td>
                  <td data-label="Visible">{lugar.activo ? 'Sí' : 'No'}</td>
                  <td data-label="Acciones">
                    <button
                      className="boton boton--fantasma boton--chico"
                      type="button"
                      onClick={() => void alternar(lugar)}
                    >
                      {lugar.activo ? 'Ocultar' : 'Mostrar'}
                    </button>{' '}
                    <button
                      className="boton boton--fantasma boton--chico"
                      type="button"
                      onClick={() => void eliminar(lugar)}
                    >
                      Eliminar
                    </button>
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
