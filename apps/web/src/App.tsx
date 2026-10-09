import { NavLink, Route, Routes, useLocation } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { api } from './api/cliente';
import { useSesion } from './api/useSesion';
import { useSesionHuesped } from './api/useSesionHuesped';
import { nombreCorto } from './api/formato';
import { PaginaInicio } from './paginas/PaginaInicio';
import { PaginaReserva } from './paginas/PaginaReserva';
import { PaginaConsulta } from './paginas/PaginaConsulta';
import { PaginaLoginAdmin } from './paginas/PaginaLoginAdmin';
import { PaginaAdminReservas } from './paginas/PaginaAdminReservas';
import { PaginaAdminInventario } from './paginas/PaginaAdminInventario';
import { PaginaAdminIntegraciones } from './paginas/PaginaAdminIntegraciones';
import { PaginaAdminHotel } from './paginas/PaginaAdminHotel';
import { PaginaAdminHoy } from './paginas/PaginaAdminHoy';
import { PaginaAdminIndicadores } from './paginas/PaginaAdminIndicadores';
import { PaginaAdminAuditoria } from './paginas/PaginaAdminAuditoria';
import { PaginaAdminPanel } from './paginas/PaginaAdminPanel';
import { BadgeMensajes } from './componentes/BadgeMensajes';
import { Etiqueta } from './componentes/Estado';
import { PaginaAdminLugares } from './paginas/PaginaAdminLugares';
import { PaginaMisReservas } from './paginas/PaginaMisReservas';
import { PaginaNoEncontrada } from './paginas/PaginaNoEncontrada';
import { PaginaPrivacidad } from './paginas/PaginaPrivacidad';
import { PaginaTerminos } from './paginas/PaginaTerminos';

function texto(valor?: string): string {
  return valor?.trim() ?? '';
}

/**
 * Cada ruta empieza arriba: sin esto, el scroll se hereda de la página anterior y
 * se aterriza a mitad de la nueva (verificado en prod: 1500 → 492 al ir a /consulta).
 */
function VolverArribaAlNavegar() {
  const ruta = useLocation().pathname;
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [ruta]);
  return null;
}

/**
 * Quién está detrás de la pantalla, en la cabecera y en todas las páginas. El panel pregunta
 * "soy admin" y la web "soy huésped": si hay admin manda el correo, si no el nombre de Google,
 * y sin sesión no se muestra nada en vez de un saludo inventado.
 */
export function SaludoSesion() {
  const admin = useSesion();
  const huesped = useSesionHuesped();

  // Sin comprobar() explícito: cada hook pregunta al montar y la pregunta es compartida
  // (ver api/sesion.ts). Llamarlo aquí además duplicaba la petición por cada render.

  if (admin.haySesion === true && admin.email.length > 0) {
    const quien = nombreCorto(admin.nombre, admin.email);
    if (quien.length === 0) return null;
    return (
      <span className="cabecera__saludo" title={`Sesión del panel (${admin.email})`}>
        Bienvenido de vuelta, {quien} <Etiqueta tono="info">Administrador</Etiqueta>
      </span>
    );
  }
  if (admin.haySesion !== true && huesped.haySesion === true) {
    const quien = nombreCorto(huesped.nombre, huesped.email);
    if (quien.length === 0) return null;
    return (
      <span className="cabecera__saludo" title={huesped.email}>
        Bienvenido de vuelta, {quien} <Etiqueta tono="info">Huésped</Etiqueta>
      </span>
    );
  }
  return null;
}

/**
 * Salida del panel junto al saludo, donde el personal la ve sin bajar al pie: solo con
 * sesión. Tras cerrar, recarga a /admin/entrar para no dejar datos del panel en memoria.
 */
export function CierreSesionPanel() {
  const sesion = useSesion();
  const [fallo, setFallo] = useState(false);

  if (sesion.haySesion !== true) return null;

  async function salir() {
    setFallo(false);
    const ok = await sesion.salir();
    if (ok) window.location.assign('/admin/entrar');
    else setFallo(true);
  }

    return (
      <>
        <button type="button" className="cabecera__salir" onClick={() => void salir()}>
          Cerrar sesión
        </button>
        {fallo ? (
          <span className="cabecera__error" role="alert">
            No se pudo cerrar la sesión: sigue abierta.
          </span>
        ) : null}
      </>
    );
}

export function App() {
  const [hotel, setHotel] = useState<Record<string, string> | null>(null);

  // La marca pública llega del hotel; si la API falla, la cabecera usa el nombre genérico.
  useEffect(() => {
    void api
      .get<Record<string, string>>('/api/hotel')
      .then((datos) => setHotel(datos))
      .catch(() => setHotel(null));
  }, []);

  const nombre = texto(hotel?.nombre) || 'Hotel Eridu';
  const contacto = [texto(hotel?.direccion), texto(hotel?.contacto_telefono), texto(hotel?.contacto_email)]
    .filter((dato) => dato.length > 0);

  return (
    <>
      <a className="solo-lectores" href="#contenido">
        Saltar al contenido principal
      </a>
      <VolverArribaAlNavegar />

      <header className="cabecera no-imprimir">
        <div className="cabecera__interna">
          <NavLink to="/" className="cabecera__marca">
            <img src="/icono-hotel.svg" alt="" width={28} height={28} className="cabecera__logo" />
            {nombre}
          </NavLink>
          <nav className="nav" aria-label="Navegación principal">
            <NavLink to="/" end>Reservar</NavLink>
            <NavLink to="/consulta">Consultar reserva</NavLink>
            <NavLink to="/mis-reservas">Mis reservas <BadgeMensajes /></NavLink>
            <NavLink to="/admin/inventario">Habitaciones</NavLink>
            <NavLink to="/admin">Panel</NavLink>
          </nav>
          <div className="cabecera__sesion">
            <SaludoSesion />
            <CierreSesionPanel />
          </div>
        </div>
      </header>

      <Routes>
        {/* Público */}
        <Route path="/" element={<PaginaInicio nombreHotel={nombre} />} />
        <Route path="/reserva" element={<PaginaReserva />} />
        <Route path="/consulta" element={<PaginaConsulta />} />
        <Route path="/mis-reservas" element={<PaginaMisReservas />} />
        <Route path="/privacidad" element={<PaginaPrivacidad />} />
        <Route path="/terminos" element={<PaginaTerminos />} />

        {/* Panel administrativo: cada pantalla comprueba la sesión por su cuenta */}
        <Route path="/admin" element={<PaginaAdminPanel />} />
        <Route path="/admin/entrar" element={<PaginaLoginAdmin />} />
        <Route path="/admin/reservas" element={<PaginaAdminReservas />} />
        <Route path="/admin/hoy" element={<PaginaAdminHoy />} />
        <Route path="/admin/inventario" element={<PaginaAdminInventario />} />
        <Route path="/admin/hotel" element={<PaginaAdminHotel />} />
        <Route path="/admin/integraciones" element={<PaginaAdminIntegraciones />} />
        <Route path="/admin/indicadores" element={<PaginaAdminIndicadores />} />
        <Route path="/admin/auditoria" element={<PaginaAdminAuditoria />} />
        <Route path="/admin/lugares" element={<PaginaAdminLugares />} />
        <Route path="*" element={<PaginaNoEncontrada />} />
      </Routes>

      <footer className="pie no-imprimir">
        <div className="pie__interna">
          <p className="sin-margen">
            Reservas directas de {nombre}. Los precios mostrados son los que el hotel ha configurado.
          </p>
          {contacto.length > 0 ? (
            <p className="campo__ayuda sin-margen">{contacto.join(' · ')}</p>
          ) : null}
          <nav className="nav" aria-label="Navegación del panel">
            <NavLink to="/admin/inventario">Inventario</NavLink>
            <NavLink to="/admin/hoy">Hoy</NavLink>
            <NavLink to="/admin/hotel">Hotel</NavLink>
            <NavLink to="/admin/integraciones">Integraciones</NavLink>
            <NavLink to="/admin/indicadores">Indicadores</NavLink>
            <NavLink to="/admin/auditoria">Actividad</NavLink>
            <NavLink to="/admin/lugares">Mapa</NavLink>
          </nav>
          <nav className="nav" aria-label="Información legal">
            <NavLink to="/privacidad">Privacidad</NavLink>
            <NavLink to="/terminos">Términos</NavLink>
          </nav>
        </div>
      </footer>
    </>
  );
}