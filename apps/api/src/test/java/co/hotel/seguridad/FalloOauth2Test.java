package co.hotel.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * Cuando Google no completa la vuelta, el fallo se registra con su motivo y cada
 * entrada vuelve a su puerta. Antes el lambda era mudo: el dueño veía un rebote
 * sin explicación y el log no decía nada.
 */
class FalloOauth2Test {

  private String destino(String uri) throws Exception {
    var fallo = new FalloOauth2();
    var req = new MockHttpServletRequest();
    req.setRequestURI(uri);
    var res = new MockHttpServletResponse();
    fallo.onAuthenticationFailure(req, res,
      new OAuth2AuthenticationException(new OAuth2Error("invalid_grant"), "nada que canjear"));
    return res.getRedirectedUrl();
  }

  @Test
  @DisplayName("el admin vuelve a entrar con el motivo en la URL")
  void adminVuelveAEntrar() throws Exception {
    assertEquals("/admin/entrar?error=oauth2", destino("/login/oauth2/code/google-admin"));
  }

  @Test
  @DisplayName("el huésped vuelve al inicio con el motivo en la URL")
  void huespedVuelveAlInicio() throws Exception {
    assertEquals("/?error=oauth2", destino("/login/oauth2/code/google-huesped"));
  }
}
