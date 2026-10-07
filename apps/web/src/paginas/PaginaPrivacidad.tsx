import { useContactoHotel } from '../api/useContactoHotel';

/**
 * Política de privacidad de la reserva directa.
 *
 * Describe lo que la aplicación hace de verdad: correo y nombre para gestionar la reserva,
 * cuenta de Google opcional para verla sin el código, cookies técnicas de sesión y CSRF, y
 * ninguna tarjeta ni rastreo publicitario. Sin letra pequeña inventada.
 */
export function PaginaPrivacidad() {
  const contacto = useContactoHotel();

  return (
    <main id="contenido" className="centrado">
      <section className="seccion">
        <h1 className="seccion__titulo">Política de privacidad</h1>
        <p className="seccion__intro">
          Qué datos recoge esta web, para qué y cómo ejercer tus derechos. Sin tecnicismos de más.
        </p>

        <h2 className="t-lg">Qué datos nos das</h2>
        <ul className="lista-marcada">
          <li>
            <strong>Correo electrónico y nombre</strong>, cuando haces una reserva: sirven para
            confirmártela y para que puedas consultarla después con tu código.
          </li>
          <li>
            <strong>Fechas y número de huéspedes</strong> de tu estancia: sin ellos no hay reserva
            que gestionar.
          </li>
          <li>
            <strong>Tu cuenta de Google, solo si entras con ella</strong>: guardamos su
            identificador, tu correo y tu nombre para mostrarte tus reservas sin pedirte el código
            cada vez. Entrar con Google es opcional; sin entrar también puedes reservar y
            consultar.
          </li>
        </ul>

        <h2 className="t-lg">Lo que nunca te pedimos</h2>
        <p>
          Ni números de tarjeta ni datos bancarios: el hotel aún no cobra en línea y esta web no
          tiene dónde guardarlos. Si alguien te los pide en nombre de esta reserva, no somos
          nosotros.
        </p>

        <h2 className="t-lg">Cookies</h2>
        <p>
          Solo las técnicas imprescindibles: una de sesión para saber quién eres mientras usas la
          web y una de seguridad contra falsificación de peticiones. No hay cookies de publicidad
          ni de seguimiento entre sitios.
        </p>

        <h2 className="t-lg">Dónde viven tus datos</h2>
        <p>
          En los sistemas del propio hotel, no en una red de terceros. No vendemos ni cedemos tus
          datos con fines comerciales.
        </p>

        <h2 className="t-lg">Tus derechos</h2>
        <p>
          Puedes pedir ver, corregir o borrar tus datos cuando quieras
          {contacto ? (
            <>
              {' '}escribiendo a <strong>{contacto}</strong>
            </>
          ) : (
            ' contactando con el hotel'
          )}
          . Borrar una reserva futura la cancela: avísanos con tiempo si ya no vas a venir.
        </p>
      </section>
    </main>
  );
}
