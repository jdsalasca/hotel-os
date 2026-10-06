import { NavLink, Route, Routes } from 'react-router-dom';
import { PaginaInicio } from './paginas/PaginaInicio';
import { PaginaReserva } from './paginas/PaginaReserva';
import { PaginaConsulta } from './paginas/PaginaConsulta';
import { PaginaLoginAdmin } from './paginas/PaginaLoginAdmin';
import { PaginaAdminReservas } from './paginas/PaginaAdminReservas';
import { PaginaAdminInventario } from './paginas/PaginaAdminInventario';
import { PaginaAdminIntegraciones } from './paginas/PaginaAdminIntegraciones';
import { PaginaAdminIndicadores } from './paginas/PaginaAdminIndicadores';

export function App() {
  return (
    <>
      <a className="solo-lectores" href="#contenido">
        Saltar al contenido principal
      </a>

      <header className="cabecera no-imprimir">
        <div className="cabecera__interna">
          <NavLink to="/" className="cabecera__marca">
            <span className="cabecera__marcador" aria-hidden="true" />
            Hotel
          </NavLink>
          <nav className="nav" aria-label="Navegación principal">
            <NavLink to="/" end>Reservar</NavLink>
            <NavLink to="/consulta">Consultar reserva</NavLink>
            <NavLink to="/admin/reservas">Panel</NavLink>
          </nav>
        </div>
      </header>

      <Routes>
        {/* Público */}
        <Route path="/" element={<PaginaInicio />} />
        <Route path="/reserva" element={<PaginaReserva />} />
        <Route path="/consulta" element={<PaginaConsulta />} />

        {/* Panel administrativo: cada pantalla comprueba la sesión por su cuenta */}
        <Route path="/admin/entrar" element={<PaginaLoginAdmin />} />
        <Route path="/admin/reservas" element={<PaginaAdminReservas />} />
        <Route path="/admin/inventario" element={<PaginaAdminInventario />} />
        <Route path="/admin/integraciones" element={<PaginaAdminIntegraciones />} />
        <Route path="/admin/indicadores" element={<PaginaAdminIndicadores />} />
      </Routes>

      <footer className="pie no-imprimir">
        <div className="pie__interna">
          <p style={{ margin: 0 }}>
            Reservas directas del hotel. Los precios mostrados son los que el hotel ha configurado.
          </p>
          <nav className="nav" aria-label="Navegación del panel">
            <NavLink to="/admin/inventario">Inventario</NavLink>
            <NavLink to="/admin/integraciones">Integraciones</NavLink>
            <NavLink to="/admin/indicadores">Indicadores</NavLink>
          </nav>
        </div>
      </footer>
    </>
  );
}