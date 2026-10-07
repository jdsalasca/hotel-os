import { NavLink, Route, Routes, useLocation } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { api } from './api/cliente';
import { useSesion } from './api/useSesion';
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
import { PaginaMisReservas } from './paginas/PaginaMisReservas';
import { PaginaPrivacidad } from './paginas/PaginaPrivacidad';
import { PaginaTerminos } from './paginas/PaginaTerminos';

function texto(valor?: string): string {
  return valor?.trim() ?? '';
}

/**
 * Salida del panel donde el personal la encuentra: junto a la navegación del panel y solo con
 * sesión. Se monta únicamente en las rutas del panel para no pedir la sesión en las públicas.
 * Tras cerrar, recarga a /admin/entrar para no dejar datos del panel en memoria.
 */
function CierreSesionPanel() {
  const sesion = useSesion();

  if (sesion.haySesion !== true) return null;

  async function salir() {
    await sesion.salir();
    window.location.assign('/admin/entrar');
  }

  return (
    <button type="button" className="nav__salir" onClick={() => void salir()}>
      Cerrar sesión
    </button>
  );
}

export function App() {
  const [hotel, setHotel] = useState<Record<string, string> | null>(null);
  const ubicacion = useLocation();
  const enPanel = ubicacion.pathname.startsWith('/admin/') && ubicacion.pathname !== '/admin/entrar';

  // La marca pública llega del hotel; si la API falla, la cabecera usa el nombre genérico.
  useEffect(() => {
    void api
      .get<Record<string, string>>('/api/hotel')
      .then((datos) => setHotel(datos))
      .catch(() => setHotel(null));
  }, []);

  const nombre = texto(hotel?.nombre) || 'Hotel';
  const contacto = [texto(hotel?.direccion), texto(hotel?.contacto_telefono), texto(hotel?.contacto_email)]
    .filter((dato) => dato.length > 0);

  return (
    <>
      <a className="solo-lectores" href="#contenido">
        Saltar al contenido principal
      </a>

      <header className="cabecera no-imprimir">
        <div className="cabecera__interna">
          <NavLink to="/" className="cabecera__marca">
            <span className="cabecera__marcador" aria-hidden="true" />
            {nombre}
          </NavLink>
          <nav className="nav" aria-label="Navegación principal">
            <NavLink to="/" end>Reservar</NavLink>
            <NavLink to="/consulta">Consultar reserva</NavLink>
            <NavLink to="/mis-reservas">Mis reservas</NavLink>
            <NavLink to="/admin/reservas">Panel</NavLink>
          </nav>
        </div>
      </header>

      <Routes>
        {/* Público */}
        <Route path="/" element={<PaginaInicio />} />
        <Route path="/reserva" element={<PaginaReserva />} />
        <Route path="/consulta" element={<PaginaConsulta />} />
        <Route path="/mis-reservas" element={<PaginaMisReservas />} />
        <Route path="/privacidad" element={<PaginaPrivacidad />} />
        <Route path="/terminos" element={<PaginaTerminos />} />

        {/* Panel administrativo: cada pantalla comprueba la sesión por su cuenta */}
        <Route path="/admin/entrar" element={<PaginaLoginAdmin />} />
        <Route path="/admin/reservas" element={<PaginaAdminReservas />} />
        <Route path="/admin/hoy" element={<PaginaAdminHoy />} />
        <Route path="/admin/inventario" element={<PaginaAdminInventario />} />
        <Route path="/admin/hotel" element={<PaginaAdminHotel />} />
        <Route path="/admin/integraciones" element={<PaginaAdminIntegraciones />} />
        <Route path="/admin/indicadores" element={<PaginaAdminIndicadores />} />
        <Route path="/admin/auditoria" element={<PaginaAdminAuditoria />} />
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
            {enPanel ? <CierreSesionPanel /> : null}
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